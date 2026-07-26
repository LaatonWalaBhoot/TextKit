package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * A single multi-character insert that carries line breaks — i.e. a paste of multi-line text — must
 * split into a paragraph per break, the same as typing those breaks one at a time. Regression cover
 * for the bug where the breaks stayed embedded inside one text node (issue #48).
 *
 * Driven through the same [com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction] path
 * the Compose text field uses (see [TextKitTestSupport]).
 */
class MultilineInsertTest {

    @Test
    fun pasting_multi_line_text_splits_into_paragraphs() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.text.length, textToAdd = "\nline2\nline3")

        assertEquals("Hello world\nline2\nline3", editor.text)
        assertEquals(3, editor.getParagraphs().size)
        // No line break is left embedded inside a text node.
        assertFalse(editor.toJson().contains("line2\\nline3"), "newlines must not stay inside a text node")
    }

    @Test
    fun pasting_in_the_middle_of_a_paragraph_splits_it() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.offsetOf("world"), textToAdd = "A\nB\n")

        assertEquals("Hello A\nB\nworld", editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    @Test
    fun pasted_paragraphs_survive_a_json_round_trip() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.typeText(offset = editor.text.length, textToAdd = "\nline2\nline3")

        val reloaded = editorFrom(editor.toJson())

        assertEquals(editor.text, reloaded.text)
        assertEquals(3, reloaded.getParagraphs().size)
    }

    @Test
    fun pasting_multi_line_text_over_a_selection_replaces_and_splits() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("world")

        // Replace the selection with multi-line text (a paste over a selection → TextUpdated).
        editor.replaceText(offset = range.start, removeLength = range.length, textToAdd = "A\nB")

        assertEquals("Hello A\nB", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun a_lone_line_break_still_splits_one_paragraph_into_two() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.offsetOf("world"), textToAdd = "\n")

        assertEquals("Hello \nworld", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun plain_text_without_breaks_is_unchanged() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.typeText(offset = editor.text.length, textToAdd = " again")

        assertEquals("Hello world again", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }
}
