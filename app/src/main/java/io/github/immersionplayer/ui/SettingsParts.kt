package io.github.immersionplayer.ui

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.R
import io.github.immersionplayer.subs.SubtitleFiles

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
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
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
    selected: String?,
    allowNone: Boolean,
    onSelect: (String?) -> Unit,
) {
    var choice by remember { mutableStateOf(selected) }
    var open by remember { mutableStateOf(false) }
    SettingRow(title = title, description = description) {
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
                        0f to AppTheme.Paper.scheme.background, 0.5f to AppTheme.Paper.scheme.background,
                        0.5f to AppTheme.Midnight.scheme.background, 1f to AppTheme.Midnight.scheme.background,
                    ),
                )
                .border(1.dp, scheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                "あ Aa",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = theme?.scheme?.onBackground ?: scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The app's own icon, name and version, at the foot of the settings. */
@Composable
fun AppFooter(version: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF111316)),
            contentAlignment = Alignment.Center,
        ) {
            // the launcher foreground draws inside a 72 of 108 dp safe zone, so it is scaled to fill
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(66.dp))
        }
        Column {
            Text("Immersion Player", style = MaterialTheme.typography.titleSmall)
            Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingSwitchRow(title: String, description: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(initial) }
    SettingRow(title = title, description = description) {
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}
