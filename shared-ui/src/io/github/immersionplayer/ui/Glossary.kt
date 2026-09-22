package io.github.immersionplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import org.json.JSONArray
import org.json.JSONObject

data class GlossaryColors(
    val tag: Color,
    val muted: Color,
    val link: Color,
)

/** Renders a Yomitan glossary (strings, text objects and structured content) to styled text. */
object Glossary {

    fun render(glossaryJson: String, colors: GlossaryColors): AnnotatedString {
        val items = runCatching { JSONArray(glossaryJson) }.getOrNull() ?: return AnnotatedString(glossaryJson)
        val out = Out()
        val plainItems = (0 until items.length()).count { items.opt(it).isPlainItem() }
        var number = 0
        for (i in 0 until items.length()) {
            val item = items.opt(i)
            out.ensureNewline()
            when {
                item is String -> {
                    if (plainItems > 1) out.append("${++number}. ")
                    out.append(item)
                }
                item is JSONObject && item.optString("type") == "text" -> {
                    if (plainItems > 1) out.append("${++number}. ")
                    out.append(item.optString("text"))
                }
                item is JSONObject && item.optString("type") == "structured-content" ->
                    renderNode(item.opt("content"), out, colors)
                // images and deinflection hints have no text to show
            }
        }
        return out.build()
    }

    /** Plain text of a glossary, for mined cards and the clipboard. */
    fun plainText(glossaryJson: String): String =
        render(glossaryJson, GlossaryColors(Color.Unspecified, Color.Unspecified, Color.Unspecified)).text

    private fun Any?.isPlainItem() =
        this is String || (this is JSONObject && optString("type") == "text")

    private fun renderNode(node: Any?, out: Out, colors: GlossaryColors) {
        when (node) {
            null, JSONObject.NULL -> Unit
            is String -> out.append(node)
            is JSONArray -> for (i in 0 until node.length()) renderNode(node.opt(i), out, colors)
            is JSONObject -> renderElement(node, out, colors)
            else -> out.append(node.toString())
        }
    }

    private fun renderElement(element: JSONObject, out: Out, colors: GlossaryColors) {
        val content = element.opt("content")
        val dataContent = element.optJSONObject("data")?.optString("content").orEmpty()
        when (element.optString("tag")) {
            "br" -> out.append("\n")
            "rp", "img", "image" -> Unit
            "rt" -> out.styled(SpanStyle(fontSize = 0.7.em, color = colors.muted)) {
                out.append("(")
                renderNode(content, out, colors)
                out.append(")")
            }
            "a" -> out.styled(SpanStyle(color = colors.link)) { renderNode(content, out, colors) }
            "ul", "ol" -> {
                out.ensureNewline()
                out.listStack.add(if (element.optString("tag") == "ol") 0 else -1)
                renderNode(content, out, colors)
                out.listStack.removeAt(out.listStack.lastIndex)
                out.ensureNewline()
            }
            "li" -> {
                out.ensureNewline()
                val depth = out.listStack.size
                out.append("  ".repeat((depth - 1).coerceAtLeast(0)))
                val last = out.listStack.lastIndex
                if (last >= 0 && out.listStack[last] >= 0) {
                    out.listStack[last] = out.listStack[last] + 1
                    out.append("${out.listStack[last]}. ")
                } else {
                    out.append("• ")
                }
                renderNode(content, out, colors)
                out.ensureNewline()
            }
            "div", "p", "details", "summary", "table", "thead", "tbody", "tfoot", "tr" -> {
                out.ensureNewline()
                renderBlockContent(dataContent, content, out, colors)
                out.ensureNewline()
            }
            "td", "th" -> {
                renderNode(content, out, colors)
                out.append("  ")
            }
            else -> renderBlockContent(dataContent, content, out, colors)
        }
    }

    private fun renderBlockContent(dataContent: String, content: Any?, out: Out, colors: GlossaryColors) {
        when {
            dataContent in TAG_CONTENT -> {
                out.styled(SpanStyle(fontSize = 0.8.em, fontWeight = FontWeight.Bold, color = colors.tag)) {
                    renderNode(content, out, colors)
                }
                out.append(" ")
            }
            dataContent.contains("example") -> out.styled(SpanStyle(color = colors.muted, fontStyle = FontStyle.Italic)) {
                renderNode(content, out, colors)
            }
            dataContent == "attribution" || dataContent == "forms" -> out.styled(
                SpanStyle(fontSize = 0.85.em, color = colors.muted)
            ) { renderNode(content, out, colors) }
            else -> renderNode(content, out, colors)
        }
    }

    private val TAG_CONTENT = setOf(
        "part-of-speech-info", "misc-info", "field-info", "dialect-info", "tag", "tags", "sense-number",
    )

    /** AnnotatedString builder that knows its last character. */
    private class Out {
        private val builder = AnnotatedString.Builder()
        private var lastChar: Char? = null
        val listStack = mutableListOf<Int>()

        fun append(text: String) {
            if (text.isEmpty()) return
            builder.append(text)
            lastChar = text.last()
        }

        fun ensureNewline() {
            if (lastChar != null && lastChar != '\n') append("\n")
        }

        inline fun styled(style: SpanStyle, block: () -> Unit) {
            val index = builder.pushStyle(style)
            block()
            builder.pop(index)
        }

        fun build(): AnnotatedString {
            val result = builder.toAnnotatedString()
            val end = result.text.trimEnd().length
            return if (end == result.length) result else result.subSequence(0, end)
        }
    }
}
