package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Alignment on an empty list item survives the load/export round trip. An empty item has no text
 * piece, and its line-break piece is dropped when the paragraph converts back — so the alignment
 * must ride on the decorator piece, which the loader previously stamped before the decorator was
 * prepended.
 */
class AlignedEmptyItemTest {

    private val LEADING_EMPTY_ALIGNED = """{"type":"doc","content":[
      {"type":"orderedList","attrs":{"start":1},"content":[
        {"type":"listItem","content":[{"type":"paragraph","attrs":{"textAlign":"right"},"content":[]}]},
        {"type":"listItem","content":[{"type":"paragraph","attrs":{"textAlign":"right"},"content":[{"type":"text","text":"x"}]}]}
      ]}
    ]}"""

    @Test
    fun a_loaded_empty_aligned_item_keeps_its_alignment() {
        val once = editorFrom(LEADING_EMPTY_ALIGNED).toJson()

        assertEquals(2, once.split("\"textAlign\":\"right\"").size - 1, "alignment dropped: $once")
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun emptying_an_aligned_item_keeps_its_alignment_across_reload() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x\ny")
        editor.toListItem(TextRange(0, editor.text.length), TextEditorListItem.None, TextEditorListItem.NumberedList)
        editor.setTextAlign(TextRange(0, editor.text.length), TextAlign.Right)
        // Empty the first item; its alignment must survive export -> load -> export.
        val xAt = editor.text.indexOf('x')
        editor.deleteText(xAt, 1)

        val once = editor.toJson()
        assertTrue(once.contains("\"textAlign\":\"right\""), once)
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun an_aligned_heading_line_round_trips() {
        // The Heading branch got the same stamping-order change; pin a heading document.
        val doc = """{"type":"doc","content":[
          {"type":"heading","attrs":{"level":2,"textAlign":"center"},"content":[{"type":"text","text":"t"}]}
        ]}"""
        val once = editorFrom(doc).toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
