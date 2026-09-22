package io.github.immersionplayer.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
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
    MacTrafficLights.probe()
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
            if (event.key == Key.I) {
                keys.peeking = event.type == KeyEventType.KeyDown
                return true
            }
            if (event.type != KeyEventType.KeyDown) return false
            when (event.key) {
                Key.Spacebar -> current.togglePause()
                Key.J -> current.previousLine()
                Key.K -> current.nextLine()
                Key.H -> current.previousLine(stopAtEnd = true)
                Key.L -> current.nextLine(stopAtEnd = true)
                Key.Semicolon, Key.DirectionDown -> current.replayLine(stopAtEnd = true)
                Key.Y -> current.seekBy(-5.0)
                Key.O -> current.seekBy(5.0)
                Key.N -> current.shiftOffset(-0.1)
                Key.M -> current.shiftOffset(0.1)
                Key.U -> current.setAutoPause(!app.settings.autoPause)
                Key.F -> toggleFullscreen()
                Key.Z -> app.settings.updateVideoFill(!app.settings.videoFill)
                Key.DirectionLeft -> if (event.isShiftPressed) current.seekBy(-5.0) else current.previousLine()
                Key.DirectionRight -> if (event.isShiftPressed) current.seekBy(5.0) else current.nextLine()
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
            if (isMac) {
                // content runs under a transparent title bar, as native Mac apps do
                LaunchedEffect(Unit) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
                // traffic lights pushed in and faded with the player controls; AppKit resets their
                // layout on resize, so this runs again whenever the size or state changes
                val lightsVisible = screen !is Screen.Player || keys.chromeVisible
                LaunchedEffect(windowState.size, windowState.placement, lightsVisible, screen::class) {
                    MacTrafficLights.apply(lightsVisible)
                    delay(150) // after AppKit's own relayout following a resize
                    MacTrafficLights.apply(lightsVisible)
                }
            }
            val fullscreen = windowState.placement == WindowPlacement.Fullscreen
            DesktopTheme(app.settings.theme) {
                CompositionLocalProvider(
                    LocalTrafficLights provides (isMac && !fullscreen),
                    LocalCorners provides Corners(if (fullscreen) 0.0 else MacTrafficLights.cornerRadius),
                ) {
                    // keep the session of each player screen, so a closing player still shows its last frame
                    val sessions = remember { HashMap<Screen.Player, PlayerSession>() }
                    (screen as? Screen.Player)?.let { current -> session?.let { sessions[current] = it } }
                    // the native window too, so a resize never flashes white before Compose repaints
                    val background = MaterialTheme.colorScheme.background
                    LaunchedEffect(background) { window.background = java.awt.Color(background.toArgb()) }
                    // the theme background behind the screens, so a crossfade never shows the window's white
                    AnimatedContent(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        targetState = screen,
                        transitionSpec = { screenTransition(initialState, targetState) },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            is Screen.Library -> LibraryScreen(
                                app = app,
                                folder = s.folder,
                                onOpenFolder = { screen = Screen.Library(it) },
                                onOpenVideo = { openVideo(it, s.folder ?: it.parentFile) },
                                onOpenSettings = { screen = Screen.Settings(s) },
                            )
                            is Screen.Player -> sessions[s]?.let { PlayerScreen(app, it, keys, onBack = ::closeVideo) }
                            is Screen.Settings -> SettingsScreen(app, onBack = { screen = s.from })
                        }
                        DisposableEffect(s) { onDispose { if (s is Screen.Player && screen != s) sessions.remove(s) } }
                    }
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

/**
 * Deeper folders slide in from the right and back out to the right; videos open with a slight
 * zoom; settings slide over the library. Only transforms and fades, so the video's size (which
 * mpv renders at) never changes mid-animation.
 */
private fun screenTransition(from: Screen, to: Screen): ContentTransform {
    val spec = tween<Float>(220, easing = FastOutSlowInEasing)
    val slide = tween<IntOffset>(260, easing = FastOutSlowInEasing)
    fun horizontal(forward: Boolean) =
        (slideInHorizontally(slide) { if (forward) it / 6 else -it / 6 } + fadeIn(spec)) togetherWith
            (slideOutHorizontally(slide) { if (forward) -it / 6 else it / 6 } + fadeOut(spec))
    return when {
        from is Screen.Library && to is Screen.Library -> {
            val deeper = to.folder != null && (from.folder == null || to.folder.path.startsWith(from.folder.path + File.separator))
            horizontal(forward = deeper)
        }
        to is Screen.Player -> (fadeIn(spec) + scaleIn(spec, initialScale = 0.97f)) togetherWith fadeOut(spec)
        from is Screen.Player -> fadeIn(spec) togetherWith (fadeOut(spec) + scaleOut(spec, targetScale = 0.97f))
        to is Screen.Settings -> horizontal(forward = true)
        from is Screen.Settings -> horizontal(forward = false)
        else -> fadeIn(spec) togetherWith fadeOut(spec)
    }
}
