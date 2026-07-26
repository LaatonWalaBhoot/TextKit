package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Edge cases for adding and removing text: the empty document, full clears, caret at paragraph
 * boundaries, empty paragraphs and multi-paragraph edits. Driven through the same
 * [com.jjrodcast.textkit.editor.core.transactions.text.TextTransaction] path the Compose text field
 * uses (see [TextKitTestSupport]).
 *
 * Complements [TextEditingTest] (the happy-path insert/delete/replace/split/merge cases).
 */
class TextEditingEdgeCasesTest {

    /** `a`, an empty paragraph, then `b`. */
    private val PARAGRAPH_EMPTY_PARAGRAPH = """
        {"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"a"}]},
          {"type":"paragraph"},
          {"type":"paragraph","content":[{"type":"text","text":"b"}]}
        ]}
    """

    /** An empty leading paragraph, then `body`. */
    private val LEADING_EMPTY_PARAGRAPH = """
        {"type":"doc","content":[
          {"type":"paragraph"},
          {"type":"paragraph","content":[{"type":"text","text":"body"}]}
        ]}
    """

    // ── Empty document ─────────────────────────────────────────────────────────

    @Test
    fun types_the_first_character_into_an_empty_document() {
        val editor = editorFrom("{}")

        editor.typeText(offset = 0, textToAdd = "Hi")

        assertEquals("Hi", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun deletes_all_text_back_to_an_empty_editor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.deleteText(offset = 0, length = editor.text.length)

        assertEquals("", editor.text)
    }

    @Test
    fun a_zero_length_delete_is_a_no_op() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.deleteText(offset = 0, length = 0)

        assertEquals("Hello world", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    // ── Caret at paragraph boundaries ──────────────────────────────────────────

    @Test
    fun types_at_the_start_of_a_non_first_paragraph() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        editor.typeText(offset = editor.offsetOf("Second"), textToAdd = ">> ")

        assertEquals("First paragraph\n>> Second paragraph", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun types_at_the_end_of_a_paragraph_before_its_line_break() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        // The offset of the first line break is the end of the first paragraph's content.
        editor.typeText(offset = editor.offsetOf("\n"), textToAdd = "!")

        assertEquals("First paragraph!\nSecond paragraph", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    // ── Cross-paragraph edits ──────────────────────────────────────────────────

    @Test
    fun deleting_a_range_spanning_two_paragraphs_merges_them() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        val from = editor.offsetOf(" paragraph")     // in the first paragraph
        val to = editor.offsetOf("Second")           // start of the second paragraph

        editor.deleteText(offset = from, length = to - from)

        assertEquals("FirstSecond paragraph", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    @Test
    fun replacing_across_a_paragraph_boundary_merges_them() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        val from = editor.offsetOf(" paragraph")
        val to = editor.offsetOf("Second")

        editor.replaceText(offset = from, removeLength = to - from, textToAdd = " ")

        assertEquals("First Second paragraph", editor.text)
        assertEquals(1, editor.getParagraphs().size)
    }

    // Multi-line paste (a single insert carrying line breaks) is covered by `MultilineInsertTest`;
    // it splits into paragraphs since the fix for issue #48.

    // ── Empty paragraphs ───────────────────────────────────────────────────────

    @Test
    fun loads_an_empty_paragraph_between_two_paragraphs() {
        val editor = editorFrom(PARAGRAPH_EMPTY_PARAGRAPH)

        assertEquals("a\n\nb", editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    @Test
    fun types_into_an_empty_paragraph_between_two_others() {
        val editor = editorFrom(PARAGRAPH_EMPTY_PARAGRAPH)
        // "a\n\nb" — the empty paragraph sits between the two line breaks (offset 2).
        editor.typeText(offset = 2, textToAdd = "X")

        assertEquals("a\nX\nb", editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    @Test
    fun deletes_an_empty_paragraph_by_removing_its_line_break() {
        val editor = editorFrom(PARAGRAPH_EMPTY_PARAGRAPH)

        // Remove the second line break, collapsing the empty paragraph.
        editor.deleteText(offset = 2, length = 1)

        assertEquals("a\nb", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun types_into_a_leading_empty_paragraph() {
        val editor = editorFrom(LEADING_EMPTY_PARAGRAPH)

        assertEquals("\nbody", editor.text)

        editor.typeText(offset = 0, textToAdd = "top")

        assertEquals("top\nbody", editor.text)
        assertEquals(2, editor.getParagraphs().size)
    }

    @Test
    fun pressing_enter_twice_mid_text_creates_a_blank_paragraph_between() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val at = editor.offsetOf("world")

        // Two discrete line-break inserts — the way pressing Enter twice reaches the engine.
        editor.typeText(offset = at, textToAdd = "\n")
        editor.typeText(offset = at + 1, textToAdd = "\n")

        assertEquals("Hello \n\nworld", editor.text)
        assertEquals(3, editor.getParagraphs().size)
    }

    // ── Invariants ─────────────────────────────────────────────────────────────

    @Test
    fun the_document_round_trips_through_json_after_an_edit() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        editor.typeText(offset = 0, textToAdd = "X ")

        val reloaded = editorFrom(editor.toJson())

        assertEquals(editor.text, reloaded.text)
        assertEquals(editor.getParagraphs().size, reloaded.getParagraphs().size)
    }

    @Test
    fun paragraph_count_tracks_line_breaks_after_edits() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        assertEquals(1, editor.getParagraphs().size)

        // A break then content, as two discrete inserts (the typed path, not a paste).
        editor.typeText(offset = editor.text.length, textToAdd = "\n")
        editor.typeText(offset = editor.text.length, textToAdd = "b")
        assertEquals(2, editor.getParagraphs().size)

        editor.typeText(offset = editor.text.length, textToAdd = "\n")
        editor.typeText(offset = editor.text.length, textToAdd = "c")
        assertEquals(3, editor.getParagraphs().size)

        assertTrue(editor.text.endsWith("\nc"))
    }
}
