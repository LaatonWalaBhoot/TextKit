package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A delete whose range falls inside a list item's decorator removes the decorator **whole** — the
 * item becomes a plain paragraph, per the existing backspace-on-decorator convention — instead of
 * truncating the marker into literal text fragments that persist in the export. Regression cover
 * for issue #74.
 */
class MidDecoratorDeleteTest {

    private val ONE_BULLET = """{"type":"doc","content":[{"type":"bulletList","content":[
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bb"}]}]}
    ]}]}"""

    private fun assertNoCorruption(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        val json = editor.toJson()
        assertFalse(json.contains("\\t"), "no decorator leak: $json")
        assertEquals(editorFrom(json).text, editor.text, "live text diverged from the model")
        assertEquals(json, editorFrom(json).toJson(), "export is not a fixed point")
    }

    @Test
    fun mid_decorator_delete_on_an_empty_task_item_removes_the_decorator_whole() {
        val editor = editorFrom("{}")
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.CheckList)

        editor.deleteText(2, 3)

        assertNoCorruption(editor)
    }

    @Test
    fun mid_decorator_delete_on_a_bulleted_item_keeps_the_content() {
        val editor = editorFrom(ONE_BULLET)

        editor.deleteText(1, 2)

        assertTrue(editor.text.contains("bb"), "content survives: [${editor.text}]")
        assertNoCorruption(editor)
    }

    @Test
    fun mid_decorator_delete_on_an_ordered_item_keeps_the_content() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.deleteText(1, 2)

        assertTrue(editor.text.contains("one"), "content survives: [${editor.text}]")
        assertNoCorruption(editor)
    }

    @Test
    fun a_delete_starting_in_the_decorator_and_ending_in_content_keeps_the_rest() {
        val editor = editorFrom(ONE_BULLET)
        val contentStart = editor.offsetOf("bb")

        // From inside the decorator through the first content char.
        editor.deleteText(2, contentStart - 2 + 1)

        assertTrue(editor.text.contains("b"), "remaining content survives: [${editor.text}]")
        assertNoCorruption(editor)
    }
}
