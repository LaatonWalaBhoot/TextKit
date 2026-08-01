package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A format change spanning several pieces must not coalesce across a piece it leaves untouched.
 * Text typed before an existing line break lands after it in the ADDED buffer, so the piece ending
 * a line and the break itself can be buffer-adjacent while another piece sits between them in the
 * document — merging those two carries the break to the wrong position and reorders the document.
 */
class FormatAcrossUntouchedPieceTest {

    /** No decorator piece may sit anywhere but the start of its paragraph. */
    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}"
            )
        }
    }

    /** "abZ\n": Z is typed between "ab" and the break, so "b" and the break are buffer-adjacent. */
    private fun buildReorderShape(): TextKitEditorManager {
        val editor = editorFrom("{}")
        editor.typeText(0, "ab")
        editor.typeText(2, "\n")
        editor.typeText(2, "Z")
        return editor
    }

    @Test
    fun formatting_across_an_untouched_piece_keeps_the_document_order() {
        val editor = buildReorderShape()
        assertEquals("abZ\n", editor.text)

        // Spans "b", the untouched "Z", and the line break.
        editor.removeStyle(TextRange(1, 4), editor.marksAt(TextRange(1, 4)), TextEditorStyleItem.Italic)

        // "Z" must still end the first paragraph, not open the second one.
        val paragraphs = editor.getParagraphs()
        assertEquals("abZ\n", paragraphs.first().children.joinToString("") { it.text })
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun formatting_across_an_untouched_piece_does_not_pull_a_decorator_into_the_line() {
        val editor = editorFrom("{}")
        editor.typeText(0, "ab")
        editor.typeText(2, "\n")
        editor.typeText(3, "c")
        val line2 = editor.text.indexOf('\n') + 1
        editor.toListItem(TextRange(line2, line2 + 1), TextEditorListItem.None, TextEditorListItem.NumberedList)
        editor.typeText(2, "Z")

        editor.removeStyle(TextRange(1, 4), editor.marksAt(TextRange(1, 4)), TextEditorStyleItem.Italic)

        editor.assertNoMidlineDecorator()
        val once = editor.toJson()
        // A decorator dragged into the line would serialize its tabs as ordinary text.
        assertTrue(!once.contains("\\t"), "decorator text leaked into the export: $once")
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
