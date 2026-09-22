package io.github.immersionplayer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.immersionplayer.desktop.mpv.MpvPlayer
import java.io.File
import kotlin.system.exitProcess

private sealed interface Screen {
    data class Library(val folder: File?) : Screen
    data class Player(val video: File, val from: File?) : Screen
    data class Settings(val from: Screen) : Screen
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--bench") return bench(args[1], args.getOrNull(2)?.toInt() ?: 1920)
    val app = DesktopApp()
    Thread({ app.prepareDictionaries() }, "dictionary-setup").apply { isDaemon = true }.start()

    application {
        val windowState = rememberWindowState(width = 1440.dp, height = 860.dp)
        // one mpv for the app's lifetime, created when the first video opens
        val player = remember { lazy { MpvPlayer() } }
        // a video passed on the command line ("Open with…") opens straight away
        val initial = remember { args.firstOrNull()?.let(::File)?.takeIf { it.isFile } }
        var screen by remember {
            mutableStateOf<Screen>(if (initial != null) Screen.Player(initial, initial.parentFile) else Screen.Library(null))
        }
        var session by remember {
            mutableStateOf(initial?.let { PlayerSession(app.settings, player.value, it) })
        }
        val keys = remember { PlayerKeys() }

        fun toggleFullscreen() {
            windowState.placement =
                if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
        }
        keys.onToggleFullscreen = ::toggleFullscreen

        fun openVideo(video: File, from: File?) {
            session?.close()
            session = PlayerSession(app.settings, player.value, video)
            screen = Screen.Player(video, from)
        }

        fun closeVideo() {
            val current = screen as? Screen.Player ?: return
            session?.close()
            session = null
            keys.peeking = false
            if (windowState.placement == WindowPlacement.Fullscreen) windowState.placement = WindowPlacement.Floating
            screen = Screen.Library(current.from)
        }

        fun onKey(event: KeyEvent): Boolean {
            val current = session.takeIf { screen is Screen.Player } ?: return false
            if (event.key == Key.E) {
                keys.peeking = event.type == KeyEventType.KeyDown
                return true
            }
            if (event.type != KeyEventType.KeyDown) return false
            when (event.key) {
                Key.Spacebar -> current.togglePause()
                Key.DirectionLeft -> if (event.isShiftPressed) current.seekBy(-5.0) else current.previousLine()
                Key.DirectionRight -> if (event.isShiftPressed) current.seekBy(5.0) else current.nextLine()
                Key.DirectionDown, Key.R -> current.replayLine()
                Key.S -> current.setAutoPause(!app.settings.autoPause)
                Key.LeftBracket -> current.shiftOffset(-0.1)
                Key.RightBracket -> current.shiftOffset(0.1)
                Key.F -> toggleFullscreen()
                Key.Escape -> if (windowState.placement == WindowPlacement.Fullscreen) toggleFullscreen() else closeVideo()
                else -> return false
            }
            return true
        }

        Window(
            onCloseRequest = {
                session?.close()
                if (player.isInitialized()) player.value.destroy()
                exitApplication()
            },
            state = windowState,
            title = (screen as? Screen.Player)?.video?.nameWithoutExtension ?: "Immersion Player",
            onPreviewKeyEvent = ::onKey,
        ) {
            MaterialTheme(colorScheme = app.settings.theme.scheme) {
                when (val s = screen) {
                    is Screen.Library -> LibraryScreen(
                        app = app,
                        folder = s.folder,
                        onOpenFolder = { screen = Screen.Library(it) },
                        onOpenVideo = { openVideo(it, s.folder ?: it.parentFile) },
                        onOpenSettings = { screen = Screen.Settings(s) },
                    )
                    is Screen.Player -> session?.let { PlayerScreen(app, it, keys, onBack = ::closeVideo) }
                    is Screen.Settings -> SettingsScreen(app, onBack = { screen = s.from })
                }
            }
        }
    }
}

/** Plays [path] for 10 s at a fixed target width and prints the frame rate. For tuning the renderer. */
private fun bench(path: String, width: Int) {
    val player = MpvPlayer()
    player.setTargetSize(width, width * 9 / 16)
    player.property("mute", "yes")
    player.load(path)
    player.setPaused(false)
    Thread.sleep(2000)
    val startSerial = player.frame.value?.serial ?: 0
    Thread.sleep(10_000)
    val frames = (player.frame.value?.serial ?: 0) - startSerial
    System.getProperty("immersion.dumpFrame")?.let { out ->
        val bitmap = player.frame.value!!.bitmap
        val png = org.jetbrains.skia.Image.makeFromBitmap(bitmap).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
        File(out).writeBytes(png.bytes)
    }
    println("target ${width}x${width * 9 / 16}: %.1f fps".format(frames / 10.0))
    player.destroy()
    exitProcess(0)
}
