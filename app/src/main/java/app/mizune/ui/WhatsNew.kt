package app.mizune.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mizune.App
import app.mizune.BuildConfig
import app.mizune.update.AppUpdateState
import kotlin.reflect.KClass

/**
 * At the foot of the settings from the moment an update is found: "1.2.0 on its way", filling
 * up as it downloads, then "What's new in 1.2.0". Either opens its notes, the second with a way to
 * install it (on Android 12 and newer it also goes in by itself once the app is put away).
 */
@Composable
fun UpdatePill(app: App, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val update by app.updater.state.collectAsState()
    var readingNotes by remember { mutableStateOf(false) }
    if (pillText(update) == null) return
    val progress = (update as? AppUpdateState.Downloading)?.progress
    // once it's downloaded the fill runs to the end and fades, as the words change over
    val filled by animateFloatAsState(progress ?: 1f, label = "filled")
    val fillAlpha by animateFloatAsState(
        if (progress != null) 0.22f else 0f,
        tween(durationMillis = 500, delayMillis = 300),
        label = "fill",
    )
    val fill = MaterialTheme.colorScheme.primary
    // keyed on the kind of message alone, with what each kind last said kept for the one fading out
    val text = pillText(update).orEmpty()
    val said = remember { HashMap<KClass<out AppUpdateState>, String>() }
    SideEffect { said[update::class] = text }
    Surface(
        onClick = {
            // once it's downloaded its notes are kept for its first launch, which needn't show them again
            if (progress == null) app.updater.whatsNew.seen()
            readingNotes = true
        },
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        // a new kind of message crossfades and the pill resizes to it; the percentage just ticks
        AnimatedContent(
            targetState = update::class,
            transitionSpec = {
                (fadeIn(tween(300, delayMillis = 300)) togetherWith fadeOut(tween(300, delayMillis = 300)))
                    .using(SizeTransform { _, _ -> tween(300, delayMillis = 300) })
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.drawBehind {
                drawRect(fill.copy(alpha = fillAlpha), size = Size(size.width * filled, size.height))
            },
            label = "update",
        ) { kind ->
            Text(
                if (kind == update::class) text else said[kind].orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    if (readingNotes) {
        when (val state = update) {
            is AppUpdateState.Downloading -> WhatsNewDialog(
                eyebrow = "On its way · ${(state.progress * 100).toInt()}%",
                version = state.version,
                notes = state.notes,
                confirm = "OK",
                onConfirm = { readingNotes = false },
                onDismiss = { readingNotes = false },
            )
            is AppUpdateState.Ready -> WhatsNewDialog(
                eyebrow = "Ready to install",
                version = state.version,
                notes = state.notes,
                confirm = "Update now",
                onConfirm = {
                    readingNotes = false
                    app.updater.install(context)
                },
                later = "Later",
                onDismiss = { readingNotes = false },
            )
            is AppUpdateState.Installing -> WhatsNewDialog(
                eyebrow = "Installing",
                version = state.version,
                notes = state.notes,
                confirm = "OK",
                onConfirm = { readingNotes = false },
                onDismiss = { readingNotes = false },
            )
            else -> {}
        }
    }
}

private fun pillText(update: AppUpdateState): String? = when (update) {
    is AppUpdateState.Downloading -> "✦  ${update.version} on its way · ${(update.progress * 100).toInt()}%"
    is AppUpdateState.Ready -> "✦  What's new in ${update.version}"
    is AppUpdateState.Installing -> "✦  Installing ${update.version}…"
    else -> null
}

/** The first launch after an update: what it brought, once, unless it was read before it went in. */
@Composable
fun JustUpdated(app: App) {
    var notes by remember { mutableStateOf(app.updater.whatsNew.pending(BuildConfig.VERSION_NAME)) }
    val shown = notes ?: return
    fun close() {
        app.updater.whatsNew.seen()
        notes = null
    }
    WhatsNewDialog(
        eyebrow = "Just updated",
        version = BuildConfig.VERSION_NAME,
        notes = shown,
        confirm = "Let's go",
        onConfirm = ::close,
        onDismiss = ::close,
    )
}

@Composable
fun WhatsNewDialog(
    eyebrow: String,
    version: String,
    notes: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    later: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    eyebrow.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("What's new in $version")
            }
        },
        text = {
            if (notes.isNotBlank()) {
                ReleaseNotesText(notes, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
            } else {
                Text("Fixes and polish.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = later?.let { { TextButton(onClick = onDismiss) { Text(it) } } },
    )
}
