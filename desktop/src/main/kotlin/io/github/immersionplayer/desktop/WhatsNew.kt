package io.github.immersionplayer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.immersionplayer.ui.ReleaseNotesText

/**
 * Beside the cog once an update is waiting: "What's new in 0.5.0", which opens its notes with a
 * way to restart into it (it goes in at the next quit anyway). Where this copy can't update itself
 * it links to the release instead.
 */
@Composable
fun UpdatePill(app: DesktopApp, onRestartToUpdate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    when (val update = app.updater.state.collectAsState().value) {
        is UpdateState.Ready -> {
            Pill("✦  What's new in ${update.version}") {
                app.updater.whatsNew.seen()
                open = true
            }
            if (open) {
                WhatsNewDialog(
                    eyebrow = "Ready to install",
                    version = update.version,
                    notes = update.notes,
                    confirm = "Restart now",
                    onConfirm = onRestartToUpdate,
                    later = "Later",
                    onDismiss = { open = false },
                )
            }
        }
        is UpdateState.Available -> Pill("✦  ${update.version} is out") { openInBrowser(update.page) }
        else -> {}
    }
}

/** The first launch after an update: what it brought, once, unless it was read before it went in. */
@Composable
fun JustUpdated(app: DesktopApp) {
    var notes by remember { mutableStateOf(app.updater.whatsNew.pending(APP_VERSION)) }
    val shown = notes ?: return
    fun close() {
        app.updater.whatsNew.seen()
        notes = null
    }
    WhatsNewDialog(
        eyebrow = "Just updated",
        version = APP_VERSION,
        notes = shown,
        confirm = "Let's go",
        onConfirm = ::close,
        onDismiss = ::close,
    )
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val primary = MaterialTheme.colorScheme.primary
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = primary,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = if (hovered) 0.22f else 0.13f))
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun WhatsNewDialog(
    eyebrow: String,
    version: String,
    notes: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    later: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(480.dp).clip(RoundedCornerShape(16.dp)).background(scheme.surface),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 24.dp, top = 22.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // the icon carries its own margin, so 64 dp draws a 52 dp tile
                Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(64.dp))
                Column {
                    Text(
                        eyebrow.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = scheme.primary,
                    )
                    Text("What's new in $version", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
                }
            }
            HorizontalDivider(color = scheme.hairline)
            if (notes.isNotBlank()) {
                ReleaseNotesText(
                    notes,
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
                )
            } else {
                Text(
                    "Fixes and polish.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                )
            }
            HorizontalDivider(color = scheme.hairline)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (later != null) {
                    Text(
                        "Installs when you quit, if you'd rather wait",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (later != null) TextAction(later, onClick = onDismiss, modifier = Modifier.padding(end = 6.dp))
                Button(onClick = onConfirm, shape = RoundedCornerShape(8.dp), elevation = null) {
                    Text(confirm, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
