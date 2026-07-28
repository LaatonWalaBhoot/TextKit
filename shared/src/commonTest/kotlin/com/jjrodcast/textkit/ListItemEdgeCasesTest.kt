package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Edge cases for editing and toggling list items: typing inside an item, continuing a list with a
 * line break, converting a multi-paragraph selection, and switching between list kinds. Assertions
 * are on the exported document ([TextKitEditorManager.toJson]) — the clean structural source of
 * truth — since the rendered text stream carries the decorator prefixes.
 *
 * Complements [ListsAndDecoratorsTest] and [ListToggleTest] (paragraph↔list conversion and toggles).
 */
class ListItemEdgeCasesTest {

    /**
     * Number of list items in the exported document. Ordered/bulleted items carry a
     * `"type":"listItem"` discriminator (they live in a polymorphic list); a task item instead
     * carries its own `checked` attribute, so it is counted by that.
     */
    private fun com.jjrodcast.textkit.editor.core.TextKitEditorManager.listItemCount(): Int {
        val json = toJson()
        return (json.split("\"type\":\"listItem\"").size - 1) + (json.split("\"checked\"").size - 1)
    }

    private val wholeDoc get() = TextRange(0, Int.MAX_VALUE)

    // ── Editing within a list item ─────────────────────────────────────────────

    @Test
    fun typing_inside_a_list_item_keeps_it_in_the_list() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.typeText(offset = editor.offsetOf("one") + "one".length, textToAdd = "X")

        assertTrue(editor.text.contains("oneX"))
        assertTrue(editor.toJson().contains("orderedList"))
        assertEquals(2, editor.listItemCount())
    }

    @Test
    fun typing_at_the_start_of_a_list_item_prepends_within_the_item() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.typeText(offset = editor.offsetOf("one"), textToAdd = "Z")

        assertTrue(editor.text.contains("Zone"))
        assertEquals(2, editor.listItemCount())
    }

    @Test
    fun a_line_break_at_the_end_of_an_item_creates_a_new_item() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.typeText(offset = editor.offsetOf("one") + "one".length, textToAdd = "\n")

        // A new (empty) list item is inserted between the two existing ones; the list continues.
        assertEquals(3, editor.listItemCount())
        assertTrue(editor.toJson().contains("orderedList"))
    }

    @Test
    fun typing_in_second_empty_list_item_after_two_enters_at_first_item_end() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        val endOfOne = editor.offsetOf("one") + "one".length
        repeat(2) { editor.typeText(endOfOne, "\n") }
        assertEquals(4, editor.listItemCount())
        val item2ContentStart = editor.getParagraphs()[1].children
            .first { it.decorator == null }
            .start
        editor.typeText(item2ContentStart, "Q")
        val text = editor.text
        val qIndex = text.indexOf('Q')
        val marker2 = text.indexOf("2.")
        val marker3 = text.indexOf("3.")
        assertTrue(qIndex > marker2 && qIndex < marker3, "Q must sit in the second list item")
    }

    @Test
    fun typing_on_empty_list_item_after_single_enter() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        val endOfOne = editor.offsetOf("one") + "one".length
        val caret = editor.typeText(endOfOne, "\n")
        editor.typeText(caret.min, "Q")
        val text = editor.text
        val qIndex = text.indexOf('Q')
        val marker2 = text.indexOf("2.")
        val marker3 = text.indexOf("3.") // "two" was renumbered to 3
        assertTrue(qIndex > marker2 && qIndex < marker3, "Q must sit in the new empty second item")
    }

    // ── Converting selections ──────────────────────────────────────────────────

    @Test
    fun converting_a_multi_paragraph_selection_makes_one_item_per_paragraph() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        val applied = editor.toListItem(wholeDoc, TextEditorListItem.None, TextEditorListItem.BulletedList)

        assertTrue(applied)
        assertTrue(editor.toJson().contains("bulletList"))
        assertEquals(2, editor.listItemCount())
    }

    @Test
    fun removing_the_list_returns_each_item_to_a_plain_paragraph() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.toListItem(wholeDoc, TextEditorListItem.NumberedList, TextEditorListItem.None)

        assertEquals(0, editor.listItemCount())
        assertFalse(editor.toJson().contains("orderedList"))
        assertTrue(editor.text.contains("one"))
        assertTrue(editor.text.contains("two"))
    }

    // ── Switching list kind ────────────────────────────────────────────────────

    @Test
    fun switching_an_ordered_list_to_a_bulleted_list_keeps_the_items() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.toListItem(wholeDoc, TextEditorListItem.NumberedList, TextEditorListItem.BulletedList)

        assertTrue(editor.toJson().contains("bulletList"))
        assertFalse(editor.toJson().contains("orderedList"))
        assertEquals(2, editor.listItemCount())
    }

    @Test
    fun switching_an_ordered_list_to_a_task_list_keeps_the_items() {
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)

        editor.toListItem(wholeDoc, TextEditorListItem.NumberedList, TextEditorListItem.CheckList)

        assertTrue(editor.toJson().contains("taskList"))
        assertEquals(2, editor.listItemCount())
    }
}
