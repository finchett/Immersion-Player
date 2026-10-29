package app.mizune.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A release's notes, from the bit of Markdown they're written in: ### headings, - bullets (their
 * lines carried on indented), paragraphs, **bold**, `code` and [links](…), shown as their text.
 * The Install section is left out; it's for downloading by hand.
 */
object ReleaseNotes {
    sealed interface Block {
        data class Heading(val text: String) : Block
        data class Bullet(val text: AnnotatedString) : Block
        data class Paragraph(val text: AnnotatedString) : Block
    }

    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        var skipping = false
        var bullet: StringBuilder? = null
        var paragraph: StringBuilder? = null
        fun flush() {
            bullet?.let { blocks += Block.Bullet(inline(it.toString())) }
            paragraph?.let { blocks += Block.Paragraph(inline(it.toString())) }
            bullet = null
            paragraph = null
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val heading = Regex("^#{1,4} +(.*)").find(line)?.groupValues?.get(1)
            when {
                heading != null -> {
                    flush()
                    skipping = heading.trim().equals("Install", ignoreCase = true)
                    if (!skipping) blocks += Block.Heading(heading.trim())
                }
                skipping -> {}
                line.isBlank() -> flush()
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    bullet = StringBuilder(line.drop(2).trim())
                }
                bullet != null && raw.startsWith(" ") -> bullet!!.append(' ').append(line.trim())
                paragraph != null -> paragraph!!.append(' ').append(line.trim())
                else -> {
                    flush()
                    paragraph = StringBuilder(line.trim())
                }
            }
        }
        flush()
        return blocks
    }

    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        val plain = text.replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
        val pattern = Regex("""\*\*(.+?)\*\*|`([^`]+)`""")
        var at = 0
        for (match in pattern.findAll(plain)) {
            append(plain.substring(at, match.range.first))
            val (bold, code) = match.destructured
            if (bold.isNotEmpty()) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            } else {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            }
            at = match.range.last + 1
        }
        append(plain.substring(at))
    }
}

/** [markdown] laid out as [ReleaseNotes] reads it. */
@Composable
fun ReleaseNotesText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { ReleaseNotes.parse(markdown) }
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is ReleaseNotes.Block.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp),
                )
                is ReleaseNotes.Block.Bullet -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = scheme.primary, modifier = Modifier.width(16.dp))
                    Text(block.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                }
                is ReleaseNotes.Block.Paragraph -> Text(
                    block.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface,
                )
            }
        }
    }
}
