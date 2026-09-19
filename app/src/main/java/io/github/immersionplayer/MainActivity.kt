package io.github.immersionplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import io.github.immersionplayer.ui.LibraryScreen
import io.github.immersionplayer.ui.PlayerScreen
import io.github.immersionplayer.ui.SettingsScreen

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            MaterialTheme(colorScheme = colors) {
                var screen by remember { mutableStateOf<Screen>(Screen.Library) }
                BackHandler(enabled = screen != Screen.Library) { screen = Screen.Library }
                when (val s = screen) {
                    Screen.Library -> LibraryScreen(
                        app = app,
                        onOpenVideo = { video, siblings -> screen = Screen.Player(video, siblings) },
                        onOpenSettings = { screen = Screen.Settings },
                    )
                    Screen.Settings -> SettingsScreen(app = app, onBack = { screen = Screen.Library })
                    is Screen.Player -> PlayerScreen(
                        app = app,
                        video = s.video,
                        siblings = s.siblings,
                        onBack = { screen = Screen.Library },
                    )
                }
            }
        }
    }
}
