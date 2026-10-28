package io.github.immersionplayer.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.immersionplayer.ui.AppTheme
import io.github.immersionplayer.ui.ThemeFamily
import io.github.immersionplayer.ui.ThemeMode

/**
 * The theme: a family, each shown light beside dark, and whether it's light, dark or follows the
 * system. Settings › Appearance and the welcome both pick with this.
 */
@Composable
fun ThemePicker(settings: Settings, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ThemeFamily.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { family ->
                    FamilySwatch(family, settings.themeFamily == family, Modifier.weight(1f)) {
                        settings.updateThemeFamily(family)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ModeSwitch(settings.themeFamily, settings.themeMode, settings::updateThemeMode, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** A family as a tile of its light side beside its dark one, each with its accent. */
@Composable
private fun FamilySwatch(family: ThemeFamily, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .border(2.dp, if (selected) scheme.primary else Color.Transparent, RoundedCornerShape(11.dp))
                .padding(2.dp)
                .aspectRatio(2.1f)
                .clip(RoundedCornerShape(7.dp))
                .border(1.dp, scheme.hairline, RoundedCornerShape(7.dp)),
        ) {
            listOfNotNull(family.light, family.dark).forEach { Side(it, Modifier.weight(1f)) }
        }
        Text(
            family.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun Side(theme: AppTheme, modifier: Modifier) {
    Box(modifier.fillMaxHeight().background(theme.scheme.background)) {
        Box(Modifier.align(Alignment.TopStart).padding(8.dp).size(14.dp, 5.dp).clip(CircleShape).background(theme.scheme.primary))
        Text(
            "あ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = theme.scheme.onBackground,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
        )
    }
}

/** Auto, Light and Dark as one segmented control; what the family lacks is greyed out. */
@Composable
private fun ModeSwitch(family: ThemeFamily, mode: ThemeMode, onMode: (ThemeMode) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(9.dp)).background(scheme.onSurface.copy(alpha = 0.06f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // a family with one side is always that side, whatever was picked before
        val shown = if (family.supports(mode)) mode else if (family.dark != null) ThemeMode.Dark else ThemeMode.Light
        ThemeMode.entries.forEach { option ->
            val enabled = family.supports(option)
            val selected = option == shown
            val fill by animateColorAsState(if (selected) scheme.background else Color.Transparent, label = "mode")
            Text(
                when (option) {
                    ThemeMode.Auto -> "Auto"
                    ThemeMode.Light -> "Light"
                    ThemeMode.Dark -> "Dark"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(fill)
                    .then(
                        if (enabled) {
                            Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { onMode(option) }
                                .pointerHoverIcon(PointerIcon.Hand)
                        } else {
                            Modifier.alpha(0.35f)
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
        }
    }
}
