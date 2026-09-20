package io.github.immersionplayer.triggers

import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Shizuku user service: runs as the shell user, which can change Nubia's game settings and read
 * raw input devices. RedMagic's shoulder triggers are capacitive sensors (nubia_tgk_aw_sar0/1)
 * that report KEY_F7/KEY_F8, but they're only powered while Nubia's game settings are on, and
 * Nubia's game service swallows the key events before apps see them. So we switch the settings
 * on ourselves and read the device nodes directly with getevent.
 * (Approach from RedTrigger, https://github.com/zampierilucas/RedTrigger, MIT.)
 */
class TriggerUserService : ITriggerService.Stub() {
    @Volatile private var running = false
    private var reader: Process? = null

    override fun start(listener: ITriggerListener) {
        if (running) return
        running = true
        enableSensors()

        val devices = findDevices()
        val process = Runtime.getRuntime().exec(arrayOf("getevent", "-ql"))
        reader = process
        thread(name = "trigger-reader") {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (!running) return@forEachLine
                    val trigger = parse(line, devices) ?: return@forEachLine
                    runCatching { listener.onTrigger(trigger.first, trigger.second) }
                }
            }
        }
        // Nubia resets the game settings whenever an activity resumes; keep them on
        thread(name = "trigger-watchdog") {
            while (running) {
                if (shell("settings get global nubia_game_scene").trim() != "1") enableSensors()
                Thread.sleep(1000)
            }
        }
    }

    override fun stop() {
        if (!running) return
        running = false
        reader?.destroy()
        reader = null
        shell("settings put global nubia_game_scene 0; settings put global nubia_game_mode 0")
    }

    override fun devices(): String = findDevices().entries.joinToString { "${it.key} (${if (it.value == 0) "left" else "right"})" }

    override fun destroy() {
        stop()
        exitProcess(0)
    }

    private fun enableSensors() {
        shell("settings put global nubia_game_scene 1; settings put global nubia_game_mode 1; settings put global cc_game_mis_operate 0")
    }

    /** Device node → trigger (0 = left sar0, 1 = right sar1), from `getevent -pl`. */
    private fun findDevices(): Map<String, Int> {
        val devices = mutableMapOf<String, Int>()
        var path: String? = null
        for (line in shell("getevent -pl").lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("add device")) path = trimmed.substringAfter(": ").trim()
            if (trimmed.startsWith("name:") && path != null) {
                when {
                    "nubia_tgk" in trimmed && "sar0" in trimmed -> devices[path] = 0
                    "nubia_tgk" in trimmed && "sar1" in trimmed -> devices[path] = 1
                }
            }
        }
        return devices
    }

    /** "/dev/input/event5: EV_KEY KEY_F7 DOWN" → (0, true). */
    private fun parse(line: String, devices: Map<String, Int>): Pair<Int, Boolean>? {
        if ("EV_KEY" !in line) return null
        val path = line.substringBefore(':').trim()
        val trigger = devices[path] ?: when {
            devices.isNotEmpty() -> return null
            "KEY_F7" in line -> 0
            "KEY_F8" in line -> 1
            else -> return null
        }
        val down = when {
            line.trimEnd().endsWith("DOWN") -> true
            line.trimEnd().endsWith("UP") -> false
            else -> return null
        }
        return trigger to down
    }

    private fun shell(command: String): String = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    }.getOrDefault("")
}
