package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge cases for hyperlinks: removing a link, editing inside a linked run, coexisting with other
 * marks, changing the href, round-tripping through JSON, and exporting. Driven through the real
 * `updateDocument`/`onTextUpdated` path (see [TextKitTestSupport]).
 *
 * Complements [FormattingTest] (adds/reads a link) and [LinkPopupSelectionTest] (the link popup).
 */
class HyperlinkEdgeCasesTest {

    @Test
    fun removes_a_link_but_keeps_the_text() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK)
        val range = editor.rangeOf("test")

        assertTrue(editor.setLink(range, ""))

        assertNull(editor.getLink(range.start, range.end).first)
        assertTrue(editor.text.contains("test"))
    }

    @Test
    fun editing_inside_a_linked_run_keeps_the_link() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK)
        val link = editor.rangeOf("test")

        editor.typeText(offset = link.start + 2, textToAdd = "XY")

        assertTrue(editor.text.contains("teXYst"))
        assertTrue(editor.marksAt(TextRange(link.start, link.start + 2)).has<LinkMark>())
    }

    @Test
    fun a_link_coexists_with_a_bold_mark() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.setLink(editor.rangeOf("world"), "https://example.com")

        editor.applyStyle(editor.rangeOf("world"), TextEditorStyleItem.Bold)

        val marks = editor.marksAt(editor.rangeOf("world"))
        assertTrue(marks.has<LinkMark>())
        assertTrue(marks.has<BoldMark>())
    }

    @Test
    fun changing_the_href_over_the_same_range_updates_the_link() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val range = editor.rangeOf("world")

        editor.setLink(range, "https://one.com")
        editor.setLink(range, "https://two.com")

        assertEquals("https://two.com", editor.getLink(range.start, range.end).first)
    }

    @Test
    fun reads_a_link_from_a_collapsed_caret_inside_it() {
        val editor = editorFrom(SampleDocuments.PARAGRAPH_WITH_LINK)
        val caret = editor.rangeOf("test").start + 1

        val (href, range) = editor.getLink(caret, caret)

        assertEquals("https://test.com", href)
        assertEquals(editor.rangeOf("test"), range)
    }

    @Test
    fun applies_a_link_over_a_multi_word_selection() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val whole = TextRange(0, editor.text.length)

        assertTrue(editor.setLink(whole, "https://example.com"))

        assertEquals("https://example.com", editor.getLink(0, editor.text.length).first)
    }

    @Test
    fun an_applied_link_round_trips_through_json() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.setLink(editor.rangeOf("world"), "https://example.com")

        val reloaded = editorFrom(editor.toJson())

        val range = reloaded.rangeOf("world")
        assertEquals("https://example.com", reloaded.getLink(range.start, range.end).first)
    }

    @Test
    fun an_applied_link_exports_to_an_anchor() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.setLink(editor.rangeOf("world"), "https://example.com")

        assertTrue(editor.toHtml().contains("<a href=\"https://example.com\">world</a>"))
    }
}
