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
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.immersionplayer.ui.AppTheme
import io.github.immersionplayer.ui.Motion
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
            val theme = app.appearance.theme
                ?: if (isSystemInDarkTheme()) AppTheme.Midnight else AppTheme.Paper
            MaterialTheme(colorScheme = theme.scheme) {
                BackHandler(enabled = screen != Screen.Library) { screen = Screen.Library }
                LaunchedEffect(pendingScreen) {
                    val next = pendingScreen ?: return@LaunchedEffect
                    awaitFrame()
                    screen = next
                    pendingScreen = null
                }
                AnimatedContent(
                    targetState = screen,
                    // the same transitions as the desktop app, from Motion
                    transitionSpec = {
                        when {
                            targetState is Screen.Player -> Motion.intoPlayer()
                            initialState is Screen.Player -> Motion.outOfPlayer()
                            // library and settings: neither slides, one dissolves into the other
                            else -> Motion.crossfade()
                        }
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
