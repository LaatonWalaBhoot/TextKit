package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Converting an empty document to a list must work regardless of how the document became empty.
 * Clearing a multi-paragraph document left the piece table in a state where the next list toggle
 * crashed (`NoSuchElementException: ArrayDeque is empty`) — regression cover for issue #59.
 */
class ListToggleAfterClearTest {

    private fun clearAll(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        editor.deleteText(0, editor.text.length)
        assertEquals("", editor.text)
    }

    @Test
    fun list_toggle_works_after_clearing_a_multi_paragraph_document() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x")
        editor.typeText(1, "\n")
        editor.typeText(2, "y")

        clearAll(editor)
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.CheckList)

        assertTrue(editor.toJson().contains("taskList"))
    }

    @Test
    fun every_list_kind_works_after_clearing_a_multi_paragraph_document() {
        for (kind in listOf(
            TextEditorListItem.NumberedList,
            TextEditorListItem.BulletedList,
            TextEditorListItem.CheckList,
        )) {
            val editor = editorFrom("{}")
            editor.typeText(0, "a")
            editor.typeText(1, "\n")
            editor.typeText(2, "b")

            clearAll(editor)
            editor.toListItem(TextRange(0, 0), TextEditorListItem.None, kind)

            val json = editor.toJson()
            assertTrue(
                json.contains("orderedList") || json.contains("bulletList") || json.contains("taskList"),
                "kind=$kind json=$json",
            )
        }
    }

    @Test
    fun list_toggle_works_after_clearing_a_loaded_multi_paragraph_document() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        clearAll(editor)
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.NumberedList)

        assertTrue(editor.toJson().contains("orderedList"))
    }

    @Test
    fun typing_still_works_after_clearing_a_multi_paragraph_document() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        clearAll(editor)
        editor.typeText(0, "fresh")

        assertEquals("fresh", editor.text)
        assertEquals(editor.toJson(), editorFrom(editor.toJson()).toJson())
    }
}
