package io.github.immersionplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.immersionplayer.player.TriggerSetup
import io.github.immersionplayer.player.PlayerCommand
import io.github.immersionplayer.player.PlayerCommands
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.ui.LibraryScreen
import io.github.immersionplayer.ui.NaturalOrder
import io.github.immersionplayer.ui.PlayerScreen
import io.github.immersionplayer.ui.SettingsScreen
import io.github.immersionplayer.ui.isVideo
import kotlinx.coroutines.android.awaitFrame
import java.io.File

private const val TRIGGER_DEBOUNCE_MS = 150L

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

    private val triggerHeld = mutableSetOf<Int>()
    private val triggerReleasedAt = mutableMapOf<Int, Long>()

    /**
     * Shoulder triggers (RedMagic and similar) arrive as F7/F8 key presses. They're capacitive
     * and can flicker while a finger rests on them, so a press only counts after a real release.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val app = application as App
        if (TriggerSetup.active && event.keyCode != KeyEvent.KEYCODE_BACK) {
            TriggerSetup.onKey(event)
            return true
        }
        val previousKey = app.prefs.previousLineKey
        val nextKey = app.prefs.nextLineKey
        val isTrigger = event.keyCode == previousKey || event.keyCode == nextKey
        if (!isTrigger || screen !is Screen.Player || !app.prefs.shoulderTriggers) {
            return super.dispatchKeyEvent(event)
        }
        val key = event.keyCode
        val now = event.eventTime
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val recentlyReleased = now - (triggerReleasedAt[key] ?: 0L) < TRIGGER_DEBOUNCE_MS
                if (event.repeatCount == 0 && key !in triggerHeld && !recentlyReleased) {
                    PlayerCommands.send(if (key == previousKey) PlayerCommand.PreviousLine else PlayerCommand.NextLine)
                }
                triggerHeld.add(key)
            }
            KeyEvent.ACTION_UP -> {
                triggerHeld.remove(key)
                triggerReleasedAt[key] = now
            }
        }
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (TriggerSetup.active) TriggerSetup.onTouch(event)
        return super.dispatchTouchEvent(event)
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
