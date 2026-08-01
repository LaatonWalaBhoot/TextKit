package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The merge helpers that coalesce a formatted piece with a neighbor measure their range against the
 * piece's DOCUMENT offset. Handing them a BUFFER offset (and, for the merge branches, a length that
 * already includes the neighbor) rebuilds the piece over a span of the buffer that is not its own,
 * so the rope starts yielding text the document never contained.
 */
class FormatMergeCoordinatesTest {

    /** What the piece rope actually holds, independent of the cached plain-text snapshot. */
    private fun TextKitEditorManager.ropeText(): String =
        buildString { transaction.getLineContentModels(0, text.length).forEach { append(it.text) } }

    @Test
    fun merging_a_formatted_piece_with_its_right_neighbor_keeps_the_document_text() {
        val editor = editorFrom("{}")
        // Typing out of order interleaves the ADDED buffer, so document-adjacent pieces need not be
        // buffer-adjacent — and vice versa.
        editor.typeText(0, "c")
        editor.typeText(1, "b")
        editor.typeText(0, "a")
        editor.removeStyle(TextRange(2, 3), editor.marksAt(TextRange(2, 3)), TextEditorStyleItem.Bold)
        editor.typeText(3, "b")
        assertEquals("acbb", editor.text)

        editor.removeStyle(TextRange(2, 3), editor.marksAt(TextRange(2, 3)), TextEditorStyleItem.Italic)

        assertEquals("acbb", editor.ropeText(), "the rope no longer holds the document's text")
        assertEquals("acbb", editorFrom(editor.toJson()).text, "the export does not round trip")
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun re_marking_a_line_ending_piece_beside_a_loaded_piece_keeps_the_document_text() {
        val doc = """{"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"one"}]},
          {"type":"paragraph","content":[{"type":"text","text":"two"}]}
        ]}"""
        val editor = editorFrom(doc)
        // Typed pieces (ADDED) surrounding a loaded piece (ORIGINAL) that ends its paragraph.
        editor.typeText(2, "a")
        editor.typeText(2, "c")
        assertEquals("oncae\ntwo", editor.text)

        editor.removeStyle(TextRange(4, 6), editor.marksAt(TextRange(4, 6)), TextEditorStyleItem.Italic)

        assertEquals("oncae\ntwo", editor.ropeText(), "the rope no longer holds the document's text")
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
