package io.github.immersionplayer.dictionary

import org.json.JSONArray
import org.json.JSONObject

/**
 * A Yomitan glossary as HTML for an Anki field. Structured content keeps its elements and
 * inline styles, and its data attributes become `data-sc-*` as in Yomitan, so note types
 * styled for Yomitan's cards style these the same way. Images are dropped.
 */
object GlossaryHtml {

    fun render(glossaryJson: String): String {
        val items = runCatching { JSONArray(glossaryJson) }.getOrNull() ?: return escape(glossaryJson)
        val plain = mutableListOf<String>()
        val structured = StringBuilder()
        for (i in 0 until items.length()) {
            val item = items.opt(i)
            when {
                item is String -> plain.add(escape(item))
                item is JSONObject && item.optString("type") == "text" -> plain.add(escape(item.optString("text")))
                item is JSONObject && item.optString("type") == "structured-content" ->
                    node(item.opt("content"), structured)
            }
        }
        // plain glosses are short synonyms ("first day", "opening day"): one line reads best
        return listOf(plain.joinToString("; "), structured.toString()).filter { it.isNotEmpty() }.joinToString("<br>")
    }

    /**
     * The glosses of one definition as plain text: its strings, or in structured content the
     * items of lists marked as glossaries (Jitendex and similar), else its first line.
     */
    fun glosses(glossaryJson: String): List<String> {
        val items = runCatching { JSONArray(glossaryJson) }.getOrNull() ?: return listOf(glossaryJson)
        val out = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val item = items.opt(i)
            when {
                item is String -> out.add(item)
                item is JSONObject && item.optString("type") == "text" -> out.add(item.optString("text"))
                item is JSONObject && item.optString("type") == "structured-content" -> {
                    val found = mutableListOf<String>()
                    glossaryItems(item.opt("content"), inside = false, found)
                    if (found.isEmpty()) plain(item.opt("content")).lines().firstOrNull { it.isNotBlank() }?.let(found::add)
                    out.addAll(found)
                }
            }
        }
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun glossaryItems(value: Any?, inside: Boolean, out: MutableList<String>) {
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) glossaryItems(value.opt(i), inside, out)
            is JSONObject -> {
                val marked = value.optJSONObject("data")?.optString("content") == "glossary"
                if ((inside || marked) && value.optString("tag") == "li") {
                    out.add(plain(value.opt("content")))
                } else if (marked && value.optString("tag") !in setOf("ul", "ol")) {
                    // a glossary that isn't a list is a single gloss
                    out.add(plain(value.opt("content")))
                } else {
                    glossaryItems(value.opt("content"), inside || marked, out)
                }
            }
        }
    }

    /** Text only, leaving out readings in ruby (<rt>). */
    private fun plain(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is String -> value
        is JSONArray -> (0 until value.length()).joinToString("") { plain(value.opt(it)) }
        is JSONObject -> when (value.optString("tag")) {
            "rt", "rp", "img", "image" -> ""
            "br", "div", "li", "p" -> plain(value.opt("content")) + "\n"
            else -> plain(value.opt("content"))
        }
        else -> value.toString()
    }

    private fun node(value: Any?, out: StringBuilder) {
        when (value) {
            null, JSONObject.NULL -> Unit
            is String -> out.append(escape(value))
            is JSONArray -> for (i in 0 until value.length()) node(value.opt(i), out)
            is JSONObject -> element(value, out)
            else -> out.append(escape(value.toString()))
        }
    }

    private fun element(element: JSONObject, out: StringBuilder) {
        val tag = element.optString("tag")
        when (tag) {
            "br" -> out.append("<br>")
            "img", "image" -> Unit
            in TAGS -> {
                // links in dictionaries point into Yomitan's own search, which Anki can't open
                val name = if (tag == "a") "span" else tag
                out.append('<').append(name)
                attributes(element, out)
                out.append('>')
                node(element.opt("content"), out)
                out.append("</").append(name).append('>')
            }
            else -> node(element.opt("content"), out)
        }
    }

    private fun attributes(element: JSONObject, out: StringBuilder) {
        element.optJSONObject("data")?.let { data ->
            for (key in data.keySet()) {
                out.append(" data-sc-").append(key.replace(Regex("[^A-Za-z0-9-]"), ""))
                    .append("=\"").append(escape(data.optString(key))).append('"')
            }
        }
        val style = element.optJSONObject("style")
        if (style != null) {
            val css = style.keySet().filter { it in STYLES }.joinToString(";") { key ->
                val property = key.replace(Regex("[A-Z]")) { "-" + it.value.lowercase() }
                "$property:${style.opt(key)}"
            }
            if (css.isNotEmpty()) out.append(" style=\"").append(escape(css)).append('"')
        }
        for (key in listOf("lang", "title", "colSpan", "rowSpan")) {
            if (element.has(key)) out.append(' ').append(key.lowercase()).append("=\"").append(escape(element.optString(key))).append('"')
        }
    }

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\n' -> append("<br>")
            else -> append(c)
        }
    }

    private val TAGS = setOf(
        "span", "div", "ol", "ul", "li", "ruby", "rt", "rp", "table", "thead", "tbody", "tfoot", "tr", "td", "th",
        "details", "summary", "a", "b", "i", "em", "strong", "sub", "sup",
    )

    private val STYLES = setOf(
        "fontStyle", "fontWeight", "fontSize", "color", "backgroundColor", "textDecorationLine", "textDecorationStyle",
        "textDecorationColor", "verticalAlign", "textAlign", "marginTop", "marginLeft", "marginRight", "marginBottom",
        "paddingTop", "paddingLeft", "paddingRight", "paddingBottom", "listStyleType", "borderStyle", "borderWidth",
        "borderColor", "borderRadius", "whiteSpace", "wordBreak",
    )
}
