package io.github.immersionplayer.triggers

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import io.github.immersionplayer.BuildConfig
import io.github.immersionplayer.player.PlayerCommand
import io.github.immersionplayer.player.PlayerCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Phone shoulder triggers (RedMagic) as previous/next line, read through Shizuku.
 * Left trigger = previous line, right trigger = next line.
 */
object ShoulderTriggers {
    enum class Status { NotInstalled, NotRunning, NeedsPermission, Ready }

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST = 4201

    /** The capacitive sensors can flicker while a finger rests on them; a press needs a real release first. */
    private const val DEBOUNCE_MS = 150L

    private val main = Handler(Looper.getMainLooper())
    private var service: ITriggerService? = null
    private var wanted = false
    private val held = mutableSetOf<Int>()
    private val releasedAt = mutableMapOf<Int, Long>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Last press seen, for the settings test ("left"/"right"), or null. */
    private val _lastPress = MutableStateFlow<String?>(null)
    val lastPress: StateFlow<String?> = _lastPress.asStateFlow()

    /** When true, presses are only reported to [lastPress] (settings test), not sent to the player. */
    @Volatile var testMode = false

    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, TriggerUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("triggers")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val listener = object : ITriggerListener.Stub() {
        override fun onTrigger(trigger: Int, down: Boolean) {
            main.post { handle(trigger, down) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val connected = ITriggerService.Stub.asInterface(binder ?: return)
            service = connected
            if (wanted) {
                runCatching { connected.start(listener) }.onSuccess { _running.value = true }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _running.value = false
        }
    }

    fun status(context: Context): Status {
        val installed = runCatching { context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) }.isSuccess
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return if (installed) Status.NotRunning else Status.NotInstalled
        }
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) Status.Ready else Status.NeedsPermission
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        val callback = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != PERMISSION_REQUEST) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(callback)
        Shizuku.requestPermission(PERMISSION_REQUEST)
    }

    /** Turns the triggers on and starts listening, if Shizuku is ready. */
    fun start(context: Context) {
        if (status(context) != Status.Ready) return
        wanted = true
        val existing = service
        if (existing != null) {
            runCatching { existing.start(listener) }.onSuccess { _running.value = true }
        } else {
            runCatching { Shizuku.bindUserService(args, connection) }
        }
    }

    /** Stops listening and turns the triggers off (they'd otherwise stay on system-wide). */
    fun stop() {
        wanted = false
        held.clear()
        runCatching { service?.stop() }
        _running.value = false
    }

    private fun handle(trigger: Int, down: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!down) {
            held.remove(trigger)
            releasedAt[trigger] = now
            return
        }
        val flicker = now - (releasedAt[trigger] ?: 0L) < DEBOUNCE_MS
        val newPress = trigger !in held && !flicker
        held.add(trigger)
        if (!newPress) return
        if (testMode) {
            _lastPress.value = if (trigger == 0) "left" else "right"
        } else {
            PlayerCommands.send(if (trigger == 0) PlayerCommand.PreviousLine else PlayerCommand.NextLine)
        }
    }
}
