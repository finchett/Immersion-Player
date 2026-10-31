package app.mizune.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.R
import app.mizune.subs.ImageSubtitles
import app.mizune.subs.SubtitleFiles
import app.mizune.update.AppUpdateState

/** A titled card; settings are grouped into a few of these. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            trailing?.invoke()
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
        }
    }
}

/** One row inside a section: title, optional description, optional control on the right. */
@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    control: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control?.invoke()
    }
}

/** Picks one of the subtitle languages (or none), as the desktop settings do. */
@Composable
fun LanguageRow(
    title: String,
    description: String? = null,
    /** Said under [description] for the language chosen, if anything. */
    note: (String?) -> String? = { null },
    selected: String?,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
) {
    var choice by remember { mutableStateOf(selected) }
    var open by remember { mutableStateOf(false) }
    val described = listOfNotNull(description, note(choice)).joinToString(" ").ifEmpty { null }
    SettingRow(title = title, description = described) {
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text((choice?.let { SubtitleFiles.language(it)?.name ?: it } ?: "Off") + "  ▾")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                if (allowNone) {
                    DropdownMenuItem(text = { Text("Off") }, onClick = { choice = null; onSelect(null); open = false })
                }
                SubtitleFiles.LANGUAGES.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.name) },
                        onClick = { choice = language.code; onSelect(language.code); open = false },
                    )
                }
            }
        }
    }
}

/** Said wherever a peek language is picked: the subtitles are the user's own, and which can't be read. */
fun peekLanguageNote(code: String): String {
    val name = SubtitleFiles.language(code)?.name ?: code
    val files = "Mizune doesn't translate or download subtitles, so $name ones need to be in the video or in a file beside it."
    if (ImageSubtitles.model(code) != null) return files
    return "$files Picture subtitles (PGS) can only be read in English and Japanese for now."
}

/** A theme as a tile of its own colours; [theme] null is the one that follows the phone. */
@Composable
fun ThemeSwatch(label: String, theme: AppTheme?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.width(96.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                .padding(2.dp)
                .aspectRatio(3f / 2f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (theme != null) Brush.linearGradient(0f to theme.scheme.background, 1f to theme.scheme.background)
                    // the system tile is split: the light theme on one side, the dark on the other
                    else Brush.horizontalGradient(
                        0f to SystemLight.scheme.background, 0.5f to SystemLight.scheme.background,
                        0.5f to SystemDark.scheme.background, 1f to SystemDark.scheme.background,
                    ),
                )
                .border(1.dp, scheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "あ Aa",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = theme?.scheme?.onBackground ?: scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The app's own icon, name and version, at the foot of the settings, and where its update stands:
 * the latest, waiting for a connection that isn't metered, or [updatePill] on its way in.
 */
@Composable
fun AppFooter(version: String, update: AppUpdateState, updatePill: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(colorResource(R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center,
        ) {
            // the launcher foreground draws inside a 72 of 108 dp safe zone, so it is scaled to fill
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(66.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Mizune", style = MaterialTheme.typography.titleSmall)
            Text(
                "Version $version" + if (update == AppUpdateState.UpToDate) " · the latest" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            (update as? AppUpdateState.Waiting)?.let {
                Text(
                    "${it.version} waits for an unmetered connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        updatePill()
    }
}

@Composable
fun SettingSwitchRow(title: String, description: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    SettingRow(title = title, description = description) {
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}
