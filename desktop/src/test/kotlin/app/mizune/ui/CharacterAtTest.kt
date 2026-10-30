package app.mizune.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/** A tap on a character of the line picks that character, whichever way the text runs. */
@OptIn(ExperimentalTestApi::class)
class CharacterAtTest {

    private fun layouts(vararg texts: String): List<TextLayoutResult> {
        val results = mutableListOf<TextLayoutResult>()
        runDesktopComposeUiTest {
            setContent {
                val measurer = rememberTextMeasurer()
                if (results.isEmpty()) texts.forEach { results += measurer.measure(it, TextStyle(fontSize = 30.sp)) }
            }
            waitForIdle()
        }
        return results
    }

    @Test
    fun tapsLandOnTheCharacterUnderThem() {
        val texts = arrayOf("今日は何を食べたい？", "What do you want?", "مرحبا، كيف حالك اليوم؟", "שלום, מה שלומך?")
        for (layout in layouts(*texts)) {
            val text = layout.layoutInput.text.text
            for (index in text.indices) {
                val box = layout.getBoundingBox(index)
                if (box.width < 4f) continue
                // the middle, and near either edge, where the nearest caret is the neighbour's
                for (x in listOf(box.center.x, box.left + box.width * 0.2f, box.right - box.width * 0.2f)) {
                    assertEquals("'${text[index]}' ($index) in $text at $x", index, characterAt(layout, Offset(x, box.center.y), text.length))
                }
            }
        }
    }
}
