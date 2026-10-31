package app.mizune.desktop

import app.mizune.update.UpdateRelease
import app.mizune.update.Updates
import app.mizune.update.WhatsNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Where an update stands, for the library header and the settings sidebar. */
sealed interface UpdateState {
    /** Not checked yet, or the last check failed (it's tried again later). */
    data object Unknown : UpdateState
    data object UpToDate : UpdateState
    /** On its way; [notes] are its release notes. */
    data class Downloading(val version: String, val notes: String, val progress: Float) : UpdateState
    /** Downloaded and checked; it replaces this app when the app quits. [notes] are its release notes. */
    data class Ready(val version: String, val notes: String) : UpdateState
    /** Out, but this copy can't replace itself (a dev run, a read-only folder, not macOS): a link to it. */
    data class Available(val version: String, val page: String) : UpdateState
}

/**
 * Keeps the app on the latest release. It checks the feed ([Updates]) at launch and every few
 * hours, downloads a newer .dmg in the background and copies the app out of it, then swaps that
 * copy in for the running one whenever the app quits, however it quits. "Restart to update" just
 * quits and opens it again.
 *
 * Only a packaged macOS app in a folder it may write to replaces itself; anywhere else a newer
 * release is offered as a link instead.
 */
class Updater(cacheDir: File, dataDir: File, private val logError: (String, Throwable) -> Unit) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Unknown)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The notes of the update on its way, for its first launch. */
    val whatsNew = WhatsNew(File(dataDir, "whats-new.json"))
    private val notesDir = File(dataDir, "release-notes")

    /**
     * What's new in the running version, for Settings › About: the notes that came with its update
     * if they're still waiting, else a copy kept from before, else its GitHub release's (kept for
     * next time). Null if none can be had. Slow the first time; call it off the UI thread.
     */
    fun currentNotes(): String? {
        whatsNew.pending(APP_VERSION)?.let { return it }
        val kept = File(notesDir, "$APP_VERSION.md")
        if (kept.isFile) return kept.readText()
        return Updates.releaseNotes(APP_VERSION)?.also { notes ->
            runCatching {
                notesDir.mkdirs()
                kept.writeText(notes)
            }
        }
    }

    private val dir = File(cacheDir, "updates")
    private val stagedDir = File(dir, "staged")
    // -Dmizune.updateFeed=http://localhost:8000/update.json to try a release before publishing it
    private val feed = System.getProperty("mizune.updateFeed") ?: Updates.FEED
    private val bundle: File? = runningBundle()
    @Volatile private var relaunch = false

    fun start() {
        Runtime.getRuntime().addShutdownHook(Thread(::installStaged, "update-install"))
        Thread({
            while (true) {
                try {
                    check()
                } catch (e: Exception) {
                    logError("update check", e)
                    if (_state.value is UpdateState.Downloading) _state.value = UpdateState.Unknown
                }
                Thread.sleep(CHECK_EVERY_MS)
            }
        }, "updates").apply { isDaemon = true }.start()
    }

    /** Quits so the update goes in, and opens the new version once it has. */
    fun restartAfterQuit() {
        relaunch = true
    }

    private fun check() {
        val release = Updates.latest(feed)
        if (!Updates.isNewer(release.version, APP_VERSION)) {
            if (stagedDir.exists()) delete(stagedDir)
            _state.value = UpdateState.UpToDate
            return
        }
        if ((_state.value as? UpdateState.Ready)?.version == release.version) return
        val asset = release.assets[PLATFORM]
        if (bundle == null || asset == null) {
            _state.value = UpdateState.Available(release.version, release.page)
            return
        }
        // staged by an earlier run whose install didn't go through: try again at quit
        if (stagedApp()?.let(::bundleVersion) == release.version) {
            _state.value = UpdateState.Ready(release.version, release.notes)
            return
        }
        _state.value = UpdateState.Downloading(release.version, release.notes, 0f)
        val dmg = Updates.download(asset, File(dir, asset.name)) { progress ->
            // a step per percent, not per block read
            val shown = (progress * 100).toInt() / 100f
            if ((_state.value as? UpdateState.Downloading)?.progress != shown) {
                _state.value = UpdateState.Downloading(release.version, release.notes, shown)
            }
        }
        stage(dmg, release)
        dmg.delete()
        whatsNew.expect(release.version, release.notes)
        _state.value = UpdateState.Ready(release.version, release.notes)
    }

    /** Copies the app out of [dmg] into [stagedDir], and checks it's whole and the version it should be. */
    private fun stage(dmg: File, release: UpdateRelease) {
        if (stagedDir.exists()) delete(stagedDir)
        stagedDir.mkdirs()
        val mount = Files.createTempDirectory(dir.toPath(), "mount").toFile()
        try {
            // -noverify: the download's SHA-256 has been checked already
            run("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-noverify", "-mountpoint", mount.path, dmg.path)
            try {
                val app = mount.listFiles().orEmpty().firstOrNull { it.name.endsWith(".app") && it.isDirectory }
                    ?: throw IOException("no app in ${dmg.name}")
                run("ditto", app.path, File(stagedDir, app.name).path)
            } finally {
                run("hdiutil", "detach", "-force", mount.path)
            }
        } finally {
            mount.delete()
        }
        val staged = stagedApp() ?: throw IOException("nothing staged from ${dmg.name}")
        val version = bundleVersion(staged)
        if (version != release.version) throw IOException("${dmg.name} holds version $version, not ${release.version}")
        run("codesign", "--verify", "--deep", staged.path)
    }

    /**
     * At quit, hands the staged app to a small script that waits for this process to end and
     * then puts it where the running app is, putting the old one back if that fails.
     */
    private fun installStaged() {
        if (_state.value !is UpdateState.Ready) return
        val target = bundle ?: return
        val staged = stagedApp() ?: return
        runCatching {
            val script = File(dir, "install-update.sh").apply { writeText(INSTALL_SCRIPT) }
            ProcessBuilder(
                "/bin/sh", script.path,
                ProcessHandle.current().pid().toString(), staged.path, target.path, if (relaunch) "1" else "0",
            )
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(File(dir, "install.log")))
                .start()
        }.onFailure { logError("update install", it) }
    }

    private fun stagedApp(): File? = stagedDir.listFiles().orEmpty().firstOrNull { it.name.endsWith(".app") }

    private fun bundleVersion(app: File): String? = runCatching {
        run("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleShortVersionString", File(app, "Contents/Info.plist").path).trim()
    }.getOrNull()

    // rm rather than deleteRecursively: an app bundle is full of symlinks, which this mustn't follow
    private fun delete(file: File) {
        run("rm", "-rf", file.path)
    }

    private fun run(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (process.waitFor() != 0) throw IOException("${command.first()} failed: ${output.trim()}")
        return output
    }

    private companion object {
        const val PLATFORM = "macos-arm64"
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

        /** The .app this runs from, when it's a packaged macOS build that may replace itself. */
        fun runningBundle(): File? {
            if (!isMac) return null
            val launcher = System.getProperty("jpackage.app-path")
                ?: ProcessHandle.current().info().command().orElse(null)
                ?: return null
            val app = generateSequence(File(launcher)) { it.parentFile }.firstOrNull { it.name.endsWith(".app") } ?: return null
            // not from the disk image, nor from the read-only copy macOS runs a quarantined download from
            return app.takeIf { it.canWrite() && it.parentFile?.canWrite() == true }
        }

        // install-update.sh <pid> <new app> <installed app> <relaunch: 1 or 0>
        val INSTALL_SCRIPT = """
            #!/bin/sh
            pid="${'$'}1"; new="${'$'}2"; old="${'$'}3"; relaunch="${'$'}4"
            while kill -0 "${'$'}pid" 2>/dev/null; do sleep 0.2; done
            echo "${'$'}(date) installing ${'$'}new over ${'$'}old"
            # the new app keeps its own name, in case a release renames it
            target="${'$'}(dirname "${'$'}old")/${'$'}(basename "${'$'}new")"
            if [ "${'$'}target" != "${'$'}old" ] && [ -e "${'$'}target" ]; then target="${'$'}old"; fi
            aside="${'$'}old.replaced"
            rm -rf "${'$'}aside"
            mv "${'$'}old" "${'$'}aside" || exit 1
            if mv "${'$'}new" "${'$'}target"; then
                rm -rf "${'$'}aside"
            else
                echo "couldn't move the new app in; putting the old one back"
                rm -rf "${'$'}target"
                mv "${'$'}aside" "${'$'}old"
                target="${'$'}old"
            fi
            if [ "${'$'}relaunch" = 1 ]; then open "${'$'}target"; fi
        """.trimIndent() + "\n"
    }
}

/** A release's page, in the default browser. */
fun openInBrowser(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}
