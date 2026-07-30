package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorStyleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Marks serialize in the order a load produces them: other marks first, textStyle last. Live
 * formatting can leave a textStyle mid-set (clearing the color over a selection whose formatting
 * is mixed re-adds the default textStyle before the pieces' own marks), and the loader always
 * rebuilds the set textStyle-last — so an export that follows the live set order changes across a
 * reload and the fixed point breaks even though the marks themselves are identical.
 */
class MarkOrderRoundTripTest {

    @Test
    fun clearing_color_over_a_partially_underlined_range_round_trips() {
        val editor = editorFrom("{}")
        editor.typeText(0, "ab")
        editor.applyStyle(TextRange(1, 2), TextEditorStyleItem.Underline)
        editor.setColor(TextRange(0, 2), "#ff0000")
        editor.setColor(TextRange(0, 2), null)

        val once = editor.toJson()
        assertTrue(
            once.contains("""[{"type":"underline"},{"attrs":{"color":"#000000","fontSize":14},"type":"textStyle"}]"""),
            "textStyle is not serialized last: $once"
        )
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun a_document_with_textstyle_listed_first_loads_to_the_canonical_order() {
        val doc = """{"type":"doc","content":[
          {"type":"paragraph","content":[{"type":"text","text":"x","marks":[
            {"type":"textStyle","attrs":{"color":"#ff0000","fontSize":14}},{"type":"bold"}
          ]}]}
        ]}"""
        val once = editorFrom(doc).toJson()
        assertTrue(
            once.contains("""[{"type":"bold"},{"attrs":{"color":"#ff0000","fontSize":14},"type":"textStyle"}]"""),
            "textStyle is not serialized last: $once"
        )
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
