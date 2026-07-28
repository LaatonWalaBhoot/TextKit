package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A replace (`TextUpdated` — IME composition, autocorrect, paste-over-selection) whose window starts
 * inside a list item's decorator region must not touch the decorator: the window is clamped to the
 * item's content, so the decorator survives, the text lands at the content start, and nothing
 * presentation-only leaks into the exported document. Regression cover for issues #57 and #58.
 */
class ReplaceInListItemTest {

    private val BULLETED_LIST = """
        {"type":"doc","content":[
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"one"}]}]}
          ]}
        ]}
    """

    /** The export must never contain decorator artifacts (tabs, "1. ", "-[ ]" markers). */
    private fun assertNoDecoratorLeak(json: String) {
        assertFalse(json.contains("\\t"), "decorator text leaked into the export: $json")
    }

    private fun assertIdempotent(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    // ── #57: the empty-item crash ─────────────────────────────────────────────

    @Test
    fun replace_inside_an_empty_task_items_decorator_does_not_crash() {
        val editor = editorFrom("{}")
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.CheckList)

        editor.replaceText(offset = 1, removeLength = 1, textToAdd = "be")

        assertTrue(editor.text.contains("be"))
        assertTrue(editor.toJson().contains("taskList"))
        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }

    @Test
    fun replace_inside_an_empty_ordered_items_decorator_does_not_crash() {
        val editor = editorFrom("{}")
        editor.toListItem(TextRange(0, 0), TextEditorListItem.None, TextEditorListItem.NumberedList)

        editor.replaceText(offset = 1, removeLength = 1, textToAdd = "be")

        assertTrue(editor.text.contains("be"))
        assertTrue(editor.toJson().contains("orderedList"))
        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }

    // ── #58: silent corruption on non-empty items ─────────────────────────────

    @Test
    fun replace_in_an_ordered_items_decorator_keeps_both_items_in_the_list() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.replaceText(offset = 1, removeLength = 0, textToAdd = "zz")

        val json = editor.toJson()
        assertNoDecoratorLeak(json)
        // Both items are still list items; the second was falling out of the list as a paragraph.
        assertEquals(2, json.split("\"type\":\"listItem\"").size - 1, json)
        assertTrue(editor.text.contains("zzone"), "text lands at the item's content start: [${editor.text}]")
        assertIdempotent(editor)
    }

    @Test
    fun replace_in_a_task_items_decorator_lands_at_the_content_start() {
        val editor = editorFrom(SampleDocuments.TASK_LIST)

        editor.replaceText(offset = 1, removeLength = 0, textToAdd = "zz")

        // The insert was landing mid-word ("buy m|zz|ilk").
        assertTrue(editor.text.contains("zzbuy milk"), "text: [${editor.text}]")
        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }

    @Test
    fun replace_in_a_bulleted_items_decorator_lands_at_the_content_start() {
        val editor = editorFrom(BULLETED_LIST)

        editor.replaceText(offset = 1, removeLength = 0, textToAdd = "zz")

        assertTrue(editor.text.contains("zzone"), "text: [${editor.text}]")
        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }

    @Test
    fun replace_spanning_decorator_and_content_removes_only_the_content_part() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        val contentStart = editor.offsetOf("one")

        // Window starts inside the decorator and covers "on" of the content.
        editor.replaceText(offset = 1, removeLength = contentStart - 1 + 2, textToAdd = "X")

        assertTrue(editor.text.contains("Xe"), "only the covered content is replaced: [${editor.text}]")
        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }

    // ── #58 repro 3: crash after a merge ──────────────────────────────────────

    @Test
    fun replace_in_a_decorator_after_an_item_merge_does_not_crash() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        editor.deleteText(offset = 5, length = 4)

        editor.replaceText(offset = 1, removeLength = 0, textToAdd = "zz")

        assertNoDecoratorLeak(editor.toJson())
        assertIdempotent(editor)
    }
}
