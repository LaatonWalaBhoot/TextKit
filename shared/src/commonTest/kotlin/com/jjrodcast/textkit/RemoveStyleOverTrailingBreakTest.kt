package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Toggling a style off over a paragraph's trailing line break must not crash when the piece before
 * the break is not buffer-adjacent to it (text typed before an existing break lands later in the
 * ADDED buffer). The format path used to hand the left neighbor's start offset to the fallback that
 * marks the range inside the break's own piece, computing a negative buffer offset.
 */
class RemoveStyleOverTrailingBreakTest {

    @Test
    fun removing_a_style_over_the_trailing_break_does_not_crash() {
        val editor = editorFrom("{}")
        editor.typeText(0, "a\n")
        // Typed before the existing break: this piece sits after the break in the ADDED buffer, so
        // the two are not buffer-adjacent even though they are document-adjacent.
        editor.typeText(1, "bc")
        assertEquals("abc\n", editor.text)

        val range = TextRange(3, 4)
        editor.removeStyle(range, editor.marksAt(range), TextEditorStyleItem.Italic)

        assertEquals("abc\n", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun removing_a_style_over_the_trailing_break_keeps_neighbor_marks() {
        val editor = editorFrom("{}")
        editor.typeText(0, "a\n")
        editor.typeText(1, "bc")
        editor.applyStyle(TextRange(1, 3), TextEditorStyleItem.Bold)

        val range = TextRange(3, 4)
        editor.removeStyle(range, editor.marksAt(range), TextEditorStyleItem.Italic)

        assertEquals("abc\n", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
