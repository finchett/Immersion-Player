package app.mizune.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.mizune.ui.ReleaseNotesText
import kotlin.reflect.KClass

/**
 * Beside the cog from the moment an update is found: "1.2.0 is on its way", filling up as it
 * downloads, then "What's new in 0.5.0", which opens its notes with a way to restart into it (it
 * goes in at the next quit anyway). Where this copy can't update itself it links to the release
 * instead.
 */
@Composable
fun UpdatePill(app: DesktopApp, onRestartToUpdate: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val update = app.updater.state.collectAsState().value
    if (pillText(update) == null) return
    Pill(update, progress = (update as? UpdateState.Downloading)?.progress) {
        when (update) {
            is UpdateState.Available -> openInBrowser(update.page)
            is UpdateState.Ready -> {
                app.updater.whatsNew.seen()
                open = true
            }
            else -> open = true
        }
    }
    if (open) {
        when (update) {
            is UpdateState.Downloading -> WhatsNewDialog(
                eyebrow = "On its way · ${(update.progress * 100).toInt()}%",
                version = update.version,
                notes = update.notes,
                confirm = "OK",
                onConfirm = { open = false },
                onDismiss = { open = false },
            )
            is UpdateState.Ready -> WhatsNewDialog(
                eyebrow = "Ready to install",
                version = update.version,
                notes = update.notes,
                confirm = "Restart now",
                onConfirm = onRestartToUpdate,
                later = "Later",
                onDismiss = { open = false },
            )
            else -> {}
        }
    }
}

private fun pillText(update: UpdateState): String? = when (update) {
    is UpdateState.Downloading -> "✦  ${update.version} is on its way · ${(update.progress * 100).toInt()}%"
    is UpdateState.Ready -> "✦  What's new in ${update.version}"
    is UpdateState.Available -> "✦  ${update.version} is out"
    else -> null
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

/**
 * One pill for every state of the update, so it changes over in place: once the download is
 * done the fill runs to the end and fades as the words crossfade and the pill resizes to them.
 */
@Composable
private fun Pill(update: UpdateState, progress: Float?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val primary = MaterialTheme.colorScheme.primary
    val filled by animateFloatAsState(progress ?: 1f, label = "filled")
    val fillAlpha by animateFloatAsState(
        if (progress != null) 0.18f else 0f,
        tween(durationMillis = 500, delayMillis = 300),
        label = "fill",
    )
    // a new kind of message crossfades as the pill resizes to it; the percentage just ticks.
    // Keyed on the kind alone, with what each kind last said kept for the one fading out.
    val text = pillText(update).orEmpty()
    val said = remember { HashMap<KClass<out UpdateState>, String>() }
    SideEffect { said[update::class] = text }
    AnimatedContent(
        targetState = update::class,
        transitionSpec = {
            (fadeIn(tween(300, delayMillis = 300)) togetherWith fadeOut(tween(300, delayMillis = 300)))
                .using(SizeTransform { _, _ -> tween(300, delayMillis = 300) })
        },
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(primary.copy(alpha = if (hovered) 0.22f else 0.13f))
            .drawBehind { drawRect(primary.copy(alpha = fillAlpha), size = Size(size.width * filled, size.height)) }
            .hoverable(interaction)
            .clickable(interaction, indication = null, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        label = "update",
    ) { kind ->
        Text(
            if (kind == update::class) text else said[kind].orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
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
