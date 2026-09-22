package io.github.immersionplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import io.github.immersionplayer.library.NaturalOrder
import io.github.immersionplayer.triggers.ShoulderTriggers
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SizeTransform
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.ui.LibraryScreen
import io.github.immersionplayer.ui.PlayerScreen
import io.github.immersionplayer.ui.SettingsScreen
import io.github.immersionplayer.ui.isVideo
import kotlinx.coroutines.android.awaitFrame
import java.io.File

sealed interface Screen {
    data object Library : Screen
    data object Settings : Screen
    data class Player(val video: DocumentFile, val siblings: List<DocumentFile>) : Screen
}

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Library)

    /** Next screen to show after one frame on the library (lets the old player release mpv first). */
    private var pendingScreen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        val app = application as App
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent {
            MaterialTheme(colorScheme = app.appearance.theme.scheme) {
                BackHandler(enabled = screen != Screen.Library) { screen = Screen.Library }
                LaunchedEffect(pendingScreen) {
                    val next = pendingScreen ?: return@LaunchedEffect
                    awaitFrame()
                    screen = next
                    pendingScreen = null
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val toPlayer = targetState is Screen.Player
                        val fromPlayer = initialState is Screen.Player
                        val toSettings = targetState is Screen.Settings
                        when {
                            // opening a video: settle into it
                            toPlayer -> (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.93f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(260), targetScale = 1.04f))
                            // leaving a video: pull back out to the grid
                            fromPlayer -> (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 1.05f)) togetherWith
                                (fadeOut(tween(160)) + scaleOut(tween(260), targetScale = 0.95f))
                            // settings slides in from the right, and back out the same way
                            toSettings -> (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(260)) { -it / 6 } + fadeOut(tween(200)))
                            else -> (slideInHorizontally(tween(260)) { -it / 6 } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                        } using SizeTransform(clip = false)
                    },
                    label = "screen",
                ) { animatedScreen ->
                when (val s = animatedScreen) {
                    Screen.Library -> LibraryScreen(
                        app = app,
                        onOpenVideo = { video, siblings -> screen = Screen.Player(video, siblings) },
                        onOpenSettings = { screen = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(app = app, onBack = { screen = Screen.Library })
                    is Screen.Player -> {
                        val next = nextEpisode(s.video, s.siblings)
                        PlayerScreen(
                            app = app,
                            video = s.video,
                            siblings = s.siblings,
                            nextEpisodeName = next?.name,
                            onBack = { screen = Screen.Library },
                            onNextEpisode = next?.let { { switchTo(Screen.Player(it, s.siblings)) } },
                        )
                    }
                }
                }
            }
        }
    }

    /**
     * While the shoulder triggers are on, Nubia's game service also injects screen taps at the
     * points it has mapped for games. Those come from a virtual device rather than the
     * touchscreen, so they can be dropped; the trigger reader already handles the presses.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (ShoulderTriggers.running.value && event.isInjected()) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                (application as App).logError(
                    "ignored injected tap at (${event.x.toInt()}, ${event.y.toInt()}) device=${event.deviceId}",
                    Throwable("trace"),
                )
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun MotionEvent.isInjected(): Boolean =
        deviceId <= 0 || device == null || device.isVirtual

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Fullscreen everywhere; the bars come back with a swipe from the edge. */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleViewIntent(intent)
    }

    /** "Open with" from a file manager or another app. */
    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val video = when (uri.scheme) {
            "content" -> DocumentFile.fromSingleUri(this, uri)
            "file" -> uri.path?.let { DocumentFile.fromFile(File(it)) }
            else -> null
        } ?: return
        switchTo(Screen.Player(video, siblingsOf(uri)))
    }

    /** Sidecar subtitles need the folder listing, which only file:// paths allow here. */
    private fun siblingsOf(uri: Uri): List<DocumentFile> {
        if (uri.scheme != "file") return emptyList()
        val parent = uri.path?.let { File(it).parentFile } ?: return emptyList()
        return parent.listFiles()?.map { DocumentFile.fromFile(it) }.orEmpty()
    }

    private fun switchTo(target: Screen) {
        if (screen is Screen.Player) {
            screen = Screen.Library
            pendingScreen = target
        } else {
            screen = target
        }
    }

    private fun nextEpisode(video: DocumentFile, siblings: List<DocumentFile>): DocumentFile? {
        val videos = siblings.filter { it.isVideo() }.sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
        val index = videos.indexOfFirst { it.uri == video.uri }
        return if (index >= 0) videos.getOrNull(index + 1) else null
    }
}
