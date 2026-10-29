package app.mizune.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mizune.ui.ReleaseNotesText
import app.mizune.update.Updates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings › About, opened from the app's name at the foot of the sidebar: which version this is
 * and whether a newer one is waiting, what this version brought, and where the project lives.
 */
@Composable
fun AboutPane(app: DesktopApp, onRestartToUpdate: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val update by app.updater.state.collectAsState()
    // null while loading; "" when there are none to be had
    var notes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { notes = withContext(Dispatchers.IO) { app.updater.currentNotes() } ?: "" }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        // the icon carries its own margin, so 104 dp draws an 84 dp tile
        Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(104.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mizune", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Version $APP_VERSION" + when (val state = update) {
                    UpdateState.UpToDate -> " · the latest"
                    is UpdateState.Downloading -> " · getting ${state.version}…"
                    else -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                "A Japanese video player with a dictionary built into the subtitles.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (update is UpdateState.Ready || update is UpdateState.Available) {
                Row(Modifier.padding(top = 6.dp)) { UpdatePill(app, onRestartToUpdate) }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LinkButton("GitHub", Updates.REPO_PAGE)
        LinkButton("Report an issue", Updates.ISSUES_PAGE)
        LinkButton("All releases", "${Updates.REPO_PAGE}/releases")
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("What's new in $APP_VERSION")
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(scheme.surfaceVariant)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            when (val shown = notes) {
                null -> Text("Fetching the notes…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                "" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The notes couldn't be fetched just now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    TextAction(
                        "Read them on GitHub ↗",
                        onClick = { openInBrowser(Updates.releasePage(APP_VERSION)) },
                        color = scheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else -> ReleaseNotesText(shown)
            }
        }
    }

    Text(
        "Free and open source under the Mozilla Public License 2.0. Video by mpv and FFmpeg; " +
            "dictionary data from JMdict, by the Electronic Dictionary Research and Development Group, " +
            "and the dictionaries you add. Thank you for using it.",
        style = MaterialTheme.typography.bodySmall,
        color = scheme.onSurfaceVariant,
    )
}

/** A link out, as a quiet outlined button with an arrow. */
@Composable
private fun LinkButton(text: String, url: String) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Text(
        "$text ↗",
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.primary.copy(alpha = if (hovered) 0.12f else 0.06f))
            .border(1.dp, scheme.primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(interaction, indication = null) { openInBrowser(url) }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
