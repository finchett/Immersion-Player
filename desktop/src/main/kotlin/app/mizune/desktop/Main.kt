package app.mizune.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.mizune.desktop.mpv.MpvPlayer
import app.mizune.subs.SubtitleEncoding
import app.mizune.ui.Motion
import kotlinx.coroutines.delay
import java.io.File
import kotlin.system.exitProcess

private sealed interface Screen {
    data class Library(val folder: File?) : Screen
    data class Player(val video: File, val from: File?) : Screen
    data class Settings(val from: Screen) : Screen
    /** The first launch's welcome and setup. */
    data object Welcome : Screen
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--bench") return bench(args[1], args.getOrNull(2)?.toInt() ?: 1920)
    val app = DesktopApp()
    MacTrafficLights.probe()
    if (isLinux) setLinuxWindowClass()
    Thread({ app.prepareDictionaries() }, "dictionary-setup").apply { isDaemon = true }.start()
    app.updater.start()
    app.startUsageStats()
    // without them old subtitle files come out as junk; CI fails on this line
    SubtitleEncoding.missing().takeIf { it.isNotEmpty() }?.let { System.err.println("subtitles: missing code pages ${it.joinToString()}") }

    application {
        val windowState = rememberWindowState(width = 1440.dp, height = 860.dp)
        // one mpv for the app's lifetime, created when the first video opens
        val player = remember { lazy { MpvPlayer() } }
        // a video passed on the command line ("Open with…") opens straight away
        val initial = remember { args.firstOrNull()?.let(::File)?.takeIf { it.isFile } }
        var screen by remember {
            mutableStateOf(
                when {
                    initial != null -> Screen.Player(initial, initial.parentFile)
                    !app.settings.onboarded -> Screen.Welcome
                    else -> Screen.Library(null)
                },
            )
        }
        var session by remember {
            mutableStateOf(initial?.let { PlayerSession(app.settings, player.value, it) })
        }
        val keys = remember { PlayerKeys() }

        // Windows' placement full screen keeps the title bar, so there WindowsFullscreen does it
        var windowsFullscreen by remember { mutableStateOf(false) }
        fun isFullscreen() = if (isWindows) windowsFullscreen else windowState.placement == WindowPlacement.Fullscreen
        fun setFullscreen(on: Boolean) {
            if (isWindows) {
                windowsFullscreen = on
            } else {
                windowState.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            }
        }
        fun toggleFullscreen() = setFullscreen(!isFullscreen())
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
            if (isFullscreen()) setFullscreen(false)
            screen = Screen.Library(current.from)
        }

        fun onKey(event: KeyEvent): Boolean {
            val current = session.takeIf { screen is Screen.Player } ?: return false
            keys.onTutorialKey?.let { tutorial ->
                if (event.type == KeyEventType.KeyDown) tutorial(event.key)
                return true
            }
            if (event.key == Key.I) {
                keys.peeking = event.type == KeyEventType.KeyDown
                return true
            }
            if (event.type != KeyEventType.KeyDown) return false
            // ⌘/Ctrl + 1–6: Anime4K's modes, on the keys its mpv instructions use (turning it on at
            // Auto if it's off); 0 turns it off
            val anime4k = ANIME4K_KEYS.indexOf(event.key)
            if (anime4k >= 0 && (event.isMetaPressed || event.isCtrlPressed)) {
                val settings = app.settings
                val mode = Anime4K.Mode.entries.getOrNull(anime4k - 1)
                if (mode == null) {
                    settings.updateAnime4kPreset(Anime4K.Preset.OFF)
                } else {
                    settings.updateAnime4kMode(mode)
                    if (settings.anime4kPreset == Anime4K.Preset.OFF) settings.updateAnime4kPreset(Anime4K.Preset.AUTO)
                }
                keys.notice = Notice(
                    when {
                        mode == null -> "Anime4K off"
                        !current.player.runsShaders -> "Anime4K needs OpenGL, which isn't available here"
                        else -> {
                            val auto = if (settings.anime4kPreset == Anime4K.Preset.AUTO) " (auto)" else ""
                            "Anime4K ${mode.label} · ${if (settings.anime4kFast) "Fast" else "Best"}$auto"
                        }
                    },
                )
                return true
            }
            when (event.key) {
                Key.Spacebar -> current.togglePause()
                Key.H -> current.previousLine()
                Key.L -> current.nextLine()
                Key.J -> current.previousLine(stopAtEnd = true)
                Key.K -> current.nextLine(stopAtEnd = true)
                Key.Semicolon, Key.DirectionDown -> current.replayLine(stopAtEnd = true)
                Key.Y -> current.seekBy(-5.0)
                Key.O -> current.seekBy(5.0)
                Key.N -> current.shiftOffset(-0.1)
                Key.M -> current.shiftOffset(0.1)
                Key.U -> {
                    val on = !app.settings.autoPause
                    current.setAutoPause(on)
                    keys.notice = Notice(if (on) "Stopping at the end of every line" else "Playing straight through")
                }
                Key.S -> app.settings.updatePanelHidden(!app.settings.panelHidden)
                Key.F, Key.F11 -> toggleFullscreen()
                Key.Z -> app.settings.updateVideoFill(!app.settings.videoFill)
                Key.A -> keys.onAddCard()
                Key.Enter -> keys.onPlayNext?.invoke() ?: return false
                Key.DirectionLeft -> if (event.isShiftPressed) current.seekBy(-5.0) else current.previousLine()
                Key.DirectionRight -> if (event.isShiftPressed) current.seekBy(5.0) else current.nextLine()
                Key.Escape -> when {
                    keys.onDismiss != null -> keys.onDismiss?.invoke()
                    isFullscreen() -> toggleFullscreen()
                    else -> closeVideo()
                }
                else -> return false
            }
            return true
        }

        // the Dock icon (the bundle's is the light one, so a dark choice is set while the app runs),
        // and the window's, which is the taskbar's on Windows and Linux
        val settings = app.settings
        val dark = when (settings.appIcon) {
            AppIcon.Theme -> settings.themeFamily.resolve(settings.themeMode, isSystemInDarkTheme()) == settings.themeFamily.dark
            AppIcon.Light -> false
            AppIcon.Dark -> true
        }
        val iconName = if (dark) "icon-dark.png" else "icon.png"
        val appIcon = painterResource(iconName)
        LaunchedEffect(iconName) { setDockIcon(iconName) }

        // a staged update goes in once this has quit (Updater's shutdown hook)
        fun quit() {
            session?.close()
            if (player.isInitialized()) player.value.destroy()
            exitApplication()
        }

        val title = (screen as? Screen.Player)?.video?.nameWithoutExtension ?: "Mizune"
        Window(
            onCloseRequest = ::quit,
            state = windowState,
            title = title,
            icon = appIcon,
            // Windows and Linux: the app's own frame, its header the title bar (WindowFrame)
            undecorated = ownsFrame,
            onPreviewKeyEvent = ::onKey,
        ) {
            if (isMac) {
                // content runs under a transparent title bar, as native Mac apps do
                LaunchedEffect(Unit) {
                    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
                // traffic lights centred in the header and faded with the player controls; AppKit
                // resets their layout on resize and on a new title, so this runs again whenever the
                // size, state or title changes
                val lightsVisible = screen !is Screen.Player || keys.chromeVisible
                val lightCenter = HeaderHeight.value / 2.0
                LaunchedEffect(windowState.size, windowState.placement, lightsVisible, screen::class, title) {
                    MacTrafficLights.apply(lightsVisible, lightCenter, title)
                    delay(150) // after AppKit's own relayout following a resize
                    MacTrafficLights.apply(lightsVisible, lightCenter, title)
                }
            }
            val fullscreen = isFullscreen()
            SideEffect { keys.fullscreen = fullscreen }
            val frame = remember { if (ownsFrame) WindowFrame(window, windowState) else null }
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(720, 480) }
            if (isWindows) LaunchedEffect(windowsFullscreen) { WindowsFullscreen.set(window, windowsFullscreen) }
            DesktopTheme(app.settings.themeFamily, app.settings.themeMode) {
                CompositionLocalProvider(
                    LocalWindowFrame provides frame?.takeIf { !fullscreen },
                    LocalTrafficLights provides (isMac && !fullscreen),
                    LocalCorners provides Corners(if (fullscreen) 0.0 else MacTrafficLights.cornerRadius),
                ) {
                    // keep the session of each player screen, so a closing player still shows its last frame
                    val sessions = remember { HashMap<Screen.Player, PlayerSession>() }
                    (screen as? Screen.Player)?.let { current -> session?.let { sessions[current] = it } }
                    // the native window too, so a resize never flashes white before Compose repaints
                    val background = MaterialTheme.colorScheme.background
                    LaunchedEffect(background) { window.background = java.awt.Color(background.toArgb()) }
                    // and Windows draws its border in the same colours, rounding the corners while it floats
                    if (isWindows) {
                        val onBackground = MaterialTheme.colorScheme.onBackground
                        val rounded = !fullscreen && windowState.placement == WindowPlacement.Floating
                        LaunchedEffect(background, onBackground, rounded) { WindowsTitleBar.apply(window, background, onBackground, rounded) }
                    }
                    // the theme background behind the screens, so a crossfade never shows the window's white.
                    // Library and settings share one key: moving between them doesn't animate here, only
                    // their bodies do, under a header bar that stays put.
                    Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                        targetState = screen,
                        contentKey = { if (it is Screen.Player || it is Screen.Welcome) it else Screen.Library::class },
                        transitionSpec = {
                            if (targetState is Screen.Player) Motion.intoPlayer() else Motion.outOfPlayer()
                        },
                        label = "player",
                    ) { s ->
                        if (s is Screen.Player) {
                            sessions[s]?.let {
                                PlayerScreen(app, it, keys, onBack = ::closeVideo, onOpenVideo = { next -> openVideo(next, s.from) })
                            }
                            DisposableEffect(s) { onDispose { if (screen != s) sessions.remove(s) } }
                        } else if (s is Screen.Welcome) {
                            Onboarding(app, onFinish = {
                                app.settings.finishOnboarding()
                                screen = Screen.Library(null)
                            })
                        } else {
                            JustUpdated(app)
                            Browse(
                                app = app,
                                screen = s,
                                navigate = { screen = it },
                                onOpenVideo = { video, from -> openVideo(video, from) },
                                onRestartToUpdate = {
                                    app.updater.restartAfterQuit()
                                    quit()
                                },
                            )
                        }
                    }
                    if (frame != null && !fullscreen) {
                        // the window's buttons, faded with the player's controls as the traffic lights are
                        val inPlayer = screen is Screen.Player
                        AnimatedVisibility(
                            visible = !inPlayer || keys.chromeVisible,
                            enter = fadeIn(tween(160)),
                            exit = fadeOut(tween(400)),
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            WindowButtons(frame, onClose = ::quit, onDark = inPlayer && app.settings.panelHidden)
                        }
                        // Linux draws no edge round a frameless window, so it gets a hairline
                        if (isLinux && windowState.placement == WindowPlacement.Floating) {
                            Box(Modifier.fillMaxSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant))
                        }
                        ResizeEdges(frame)
                    }
                    }
                }
            }
        }
    }
}

private val ANIME4K_KEYS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)

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
    System.getProperty("mizune.dumpFrame")?.let { out ->
        val bitmap = player.frame.value!!.bitmap
        val png = org.jetbrains.skia.Image.makeFromBitmap(bitmap).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!
        File(out).writeBytes(png.bytes)
    }
    val renderer = if (player.runsShaders) "OpenGL" else "software"
    val auto = Anime4K.hardware?.let { "${it.gpu}, Auto picks ${if (it.best) "Best" else "Fast"}" } ?: "no GPU name"
    println("target ${width}x${width * 9 / 16}: %.1f fps, $renderer renderer ($auto)".format(frames / 10.0))
    player.destroy()
    exitProcess(0)
}

// the transitions themselves are shared with the Android app, in Motion
private val fade = Motion.fade
private val slide = Motion.slide

/**
 * Library and settings. Each is a whole screen of its own, and one crossfades into the other:
 * settings brings a sidebar, so the two don't share a layout to animate within. Inside the
 * library, moving between folders slides the body under a header that stays put.
 */
@Composable
private fun Browse(
    app: DesktopApp,
    screen: Screen,
    navigate: (Screen) -> Unit,
    onOpenVideo: (File, File?) -> Unit,
    onRestartToUpdate: () -> Unit,
) {
    // which settings pane is open: kept out of Screen so switching pane doesn't animate the screen
    var pane by remember { mutableStateOf(SettingsPane.General) }
    // the folder the library is in, kept so it still shows it while it fades out under settings
    var library by remember { mutableStateOf(screen as? Screen.Library ?: Screen.Library(null)) }
    (screen as? Screen.Library)?.let { library = it }

    Crossfade(targetState = screen is Screen.Settings, animationSpec = fade, label = "settings") { settings ->
        if (settings) {
            Chrome(
                header = { SettingsHeader(pane) },
                body = { SettingsBody(app, pane, onRestartToUpdate) },
                // the panes live in a sidebar that runs the whole height, under the lights
                leading = {
                    SettingsSidebar(app, pane, onPane = { pane = it }, onBack = { navigate(library) }, onRestartToUpdate)
                },
            )
        } else {
            Chrome(
                header = {
                    LibraryHeader(
                        app, library.folder,
                        onOpenFolder = { navigate(Screen.Library(it)) },
                        onOpenSettings = { navigate(Screen.Settings(library)) },
                        onRestartToUpdate = onRestartToUpdate,
                    )
                },
                body = {
                    AnimatedContent(
                        modifier = Modifier.fillMaxSize(),
                        targetState = library,
                        transitionSpec = { folderTransition(initialState, targetState) },
                        label = "folder",
                    ) { s ->
                        LibraryBody(
                            app, s.folder,
                            onOpenFolder = { navigate(Screen.Library(it)) },
                            onOpenVideo = { onOpenVideo(it, s.folder ?: it.parentFile) },
                        )
                    }
                },
            )
        }
    }
}

/** Deeper folders come in from the right, the way back from the left. */
private fun folderTransition(from: Screen.Library, to: Screen.Library): ContentTransform =
    Motion.intoFolder(
        deeper = to.folder != null &&
            (from.folder == null || to.folder.path.startsWith(from.folder.path + File.separator)),
    )

/**
 * The Dock icon while the app runs. macOS otherwise shows the bundle's, which is the light one (and
 * nothing at all for an unpackaged run); once the app quits, the Dock goes back to the bundle's.
 */
private fun setDockIcon(name: String) {
    runCatching {
        val url = object {}.javaClass.getResource("/$name") ?: return
        java.awt.Taskbar.getTaskbar().iconImage = javax.imageio.ImageIO.read(url)
    }
}

/**
 * Linux docks (GNOME's among them) find a window's launcher by its WM_CLASS, which AWT names after
 * the main class unless told otherwise. This is the launcher the .deb installs, named by jpackage
 * from the package name. Needs java.desktop's sun.awt.X11 opened (desktop/build.gradle.kts).
 */
private fun setLinuxWindowClass() {
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(toolkit, "mizune-Mizune")
    }
}
