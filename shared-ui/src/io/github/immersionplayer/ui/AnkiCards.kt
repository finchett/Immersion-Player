package io.github.immersionplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.immersionplayer.anki.AnkiBackend
import io.github.immersionplayer.anki.AnkiException
import io.github.immersionplayer.anki.CardFormats
import io.github.immersionplayer.anki.CardMedia
import io.github.immersionplayer.anki.CardTarget
import io.github.immersionplayer.anki.CardWriter
import io.github.immersionplayer.anki.MinedWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Adds and removes cards one at a time, off the UI thread, and says how it went. Each app
 * supplies where cards go ([connect], [target]); the media come with each request.
 */
class AnkiCards(
    private val connect: () -> AnkiBackend,
    /** Where cards go now, or throws an [AnkiException] saying what still has to be set up. */
    private val target: () -> CardTarget,
    private val mediaDir: File,
    private val logError: (String, Throwable) -> Unit,
) {
    sealed class Status(val message: String) {
        class Working(message: String) : Status(message)
        class Done(message: String) : Status(message)
        class Failed(message: String) : Status(message)
    }

    private val _status = MutableStateFlow<Status?>(null)
    /** What the last request is doing or did, shown for a few seconds after it finishes. */
    val status: StateFlow<Status?> = _status.asStateFlow()

    /** Notes of words (expression and sentence) added, or found already in Anki, since the app started. */
    private val notes = mutableStateMapOf<Pair<String, String>, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private var clearJob: Job? = null

    fun isAdded(word: MinedWord) = word.key in notes

    fun add(word: MinedWord, media: CardMedia) = run(word, "Adding ${word.entry.expression} to Anki…") {
        try {
            val added = CardWriter(connect(), mediaDir).add(word, target(), media)
            notes[word.key] = added.noteId
            val missing = added.missing.joinToString(" or ").ifEmpty { null }
            "Added ${word.entry.expression} to Anki" + (missing?.let { " (no $it)" } ?: "")
        } catch (e: CardWriter.Duplicate) {
            e.noteId?.let { notes[word.key] = it }
            throw e
        }
    }

    /** Deletes the note made of [word]. */
    fun remove(word: MinedWord) = run(word, "Removing ${word.entry.expression} from Anki…") {
        val id = notes[word.key] ?: throw AnkiException("That card isn't known to be in Anki")
        CardWriter(connect(), mediaDir).remove(id)
        notes.remove(word.key)
        "Removed ${word.entry.expression} from Anki"
    }

    /** Runs [work] after any earlier request, showing [working] meanwhile and then how it went. */
    private fun run(word: MinedWord, working: String, work: () -> String) {
        scope.launch {
            queue.withLock {
                clearJob?.cancel()
                _status.value = Status.Working(working)
                _status.value = try {
                    Status.Done(work())
                } catch (e: AnkiException) {
                    Status.Failed(e.message ?: "Anki refused the card")
                } catch (e: Exception) {
                    logError("anki card ${word.entry.expression}", e)
                    Status.Failed("Couldn't change the card: ${e.message}")
                }
                clearJob = scope.launch {
                    delay(4_000)
                    _status.value = null
                }
            }
        }
    }

    private val MinedWord.key get() = entry.expression to sentence
}

/** A small round + that makes a card of one dictionary entry, and becomes a − that takes it out again. */
@Composable
fun CardButton(
    added: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.35f
    // a soft tint to add; solid once the card is in Anki
    val fill = when {
        added -> scheme.primary
        hovered -> scheme.primary.copy(alpha = 0.26f)
        else -> scheme.onSurface.copy(alpha = 0.08f)
    }.let { it.copy(alpha = it.alpha * alpha) }
    val mark = (if (added) scheme.onPrimary else if (hovered) scheme.primary else scheme.onSurfaceVariant)
        .copy(alpha = alpha)
    Canvas(
        modifier.size(size).clip(CircleShape)
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = if (added) onRemove else onAdd)
            .semantics { contentDescription = if (added) "Remove from Anki" else "Add to Anki" },
    ) {
        drawCircle(fill)
        val stroke = 1.3.dp.toPx()
        val arm = this.size.minDimension * 0.22f
        drawLine(mark, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), stroke, StrokeCap.Round)
        if (!added) drawLine(mark, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), stroke, StrokeCap.Round)
    }
}

/** What adding a card is doing, or how it went; fades out a few seconds after. */
@Composable
fun AnkiStatusText(status: AnkiCards.Status?, modifier: Modifier = Modifier) {
    // the last message stays while it fades out
    val shown = remember { object { var status: AnkiCards.Status? = null } }
    status?.let { shown.status = it }
    AnimatedVisibility(status != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val last = shown.status
        Text(
            last?.message.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = if (last is AnkiCards.Status.Failed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * Where to get the note type the cards are set up for: the example deck on AnkiWeb (importing
 * it brings the note type) or its source. [intro] leads in, e.g. whether one was found.
 */
@Composable
fun NoteTypeSource(intro: String, modifier: Modifier = Modifier) {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    Text(
        buildAnnotatedString {
            append(intro)
            append(" Get it with the ")
            withLink(LinkAnnotation.Url(CardFormats.SENTENCES_DECK_URL, link)) { append("example deck on AnkiWeb") }
            append(" (importing the deck adds the note type), or from ")
            withLink(LinkAnnotation.Url(CardFormats.SENTENCES_SOURCE_URL, link)) { append("its source") }
            append(".")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
