package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A list toggle whose `from` state claims "not in a list" while the range actually sits inside a
 * list item must behave exactly as if the state had been read correctly — the paragraph itself is
 * the source of truth. It was instead stacking a second decorator onto the item, leaving the live
 * text and the document model disagreeing. Regression cover for issue #60.
 *
 * The stale claim is reachable in practice: the caret list-state at list boundaries has been wrong
 * before (#31), and the UI drives `from` off that observed state.
 */
class ListToggleStaleStateTest {

    private fun toggled(
        startJson: String,
        from: TextEditorListItem,
        to: TextEditorListItem,
    ): Pair<String, String> {
        val editor = editorFrom(startJson)
        editor.toListItem(TextRange(1, 2), from, to)
        return editor.text to editor.toJson()
    }

    @Test
    fun stale_none_over_a_task_item_behaves_like_the_correct_from_state() {
        val stale = toggled(SampleDocuments.TASK_LIST, TextEditorListItem.None, TextEditorListItem.NumberedList)
        val correct = toggled(SampleDocuments.TASK_LIST, TextEditorListItem.CheckList, TextEditorListItem.NumberedList)

        assertEquals(correct, stale)
    }

    @Test
    fun stale_none_over_an_ordered_item_behaves_like_the_correct_from_state() {
        val stale = toggled(SampleDocuments.ORDERED_LIST, TextEditorListItem.None, TextEditorListItem.BulletedList)
        val correct = toggled(SampleDocuments.ORDERED_LIST, TextEditorListItem.NumberedList, TextEditorListItem.BulletedList)

        assertEquals(correct, stale)
    }

    @Test
    fun stale_none_never_stacks_a_second_decorator() {
        val editor = editorFrom(SampleDocuments.TASK_LIST)

        editor.toListItem(TextRange(1, 2), TextEditorListItem.None, TextEditorListItem.NumberedList)

        val lines = editor.text.split("\n")
        for (line in lines) {
            val markers = Regex("""-\[[ x]?\]|\d+\. """).findAll(line).count()
            assertFalse(markers > 1, "line carries more than one decorator: [$line]")
        }
        assertEquals(editor.toJson(), editorFrom(editor.toJson()).toJson(), "export is not a fixed point")
    }
}
