package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.utils.TABS
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A multiline paste whose offset falls in a list item's decorator region must replay cleanly.
 * Blocking a segment on a numbered decorator used to report the caret one past the item's content
 * start; the replay then inserted the next segment at that caret, which overruns the document when
 * the item is empty and crashes the piece table.
 */
class MultilinePasteOnDecoratorTest {

    @Test
    fun pasting_multiline_at_the_start_of_an_empty_numbered_item_does_not_crash() {
        val editor = editorFrom("{}")
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.NumberedList)

        editor.typeText(0, "x\ny")

        // The "x" is blocked on the numbered decorator, the break dissolves the empty item, and the
        // "y" lands as plain text — the same result the sequence produces when typed.
        assertEquals("y", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun pasting_multiline_into_the_decorator_of_a_nonempty_numbered_item_splits_at_the_content_start() {
        val editor = editorFrom("{}")
        editor.typeText(0, "abc")
        editor.toListItem(TextRange(0, 3), TextEditorListItem.None, TextEditorListItem.NumberedList)

        editor.typeText(1, "x\ny")

        assertEquals("${TABS}1. \n${TABS}2. yabc", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun pasting_multiline_at_the_start_of_an_empty_bullet_item_lands_at_the_content_start() {
        val editor = editorFrom("{}")
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.BulletedList)

        editor.typeText(0, "x\ny")

        assertEquals("${TABS}• x\n${TABS}• y", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun typing_on_a_numbered_decorator_leaves_the_caret_at_the_content_start() {
        val editor = editorFrom("{}")
        editor.typeText(0, "abc")
        editor.toListItem(TextRange(0, 3), TextEditorListItem.None, TextEditorListItem.NumberedList)

        val caret = editor.typeText(2, "z")

        assertEquals("${TABS}1. abc", editor.text)
        // The content start: right after the decorator, whose tab prefix is platform-specific.
        assertEquals(TextRange(editor.text.indexOf("abc")), caret)
    }
}
