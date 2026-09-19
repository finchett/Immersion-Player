package io.github.immersionplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.ui.LibraryScreen
import io.github.immersionplayer.ui.NaturalOrder
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

private val colors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFF2B8B5),
    tertiary = Color(0xFFA8DAB5),
    background = Color(0xFF111316),
    surface = Color(0xFF111316),
    surfaceVariant = Color(0xFF1E2126),
)

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf<Screen>(Screen.Library)

    /** Next screen to show after one frame on the library (lets the old player release mpv first). */
    private var pendingScreen by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent {
            MaterialTheme(colorScheme = colors) {
                BackHandler(enabled = screen != Screen.Library) { screen = Screen.Library }
                LaunchedEffect(pendingScreen) {
                    val next = pendingScreen ?: return@LaunchedEffect
                    awaitFrame()
                    screen = next
                    pendingScreen = null
                }
                when (val s = screen) {
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
