package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Empty paragraphs are preserved across save → reload, and the export never emits an empty text
 * node — the policy settled on issue #61: `toJson()` and load agree, so blank lines a user
 * deliberately left survive a round trip exactly. The one canonical normalization kept: a document
 * with no content at all exports as `{}`.
 */
class EmptyParagraphPreservationTest {

    private fun reloaded(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) =
        editorFrom(editor.toJson())

    // ── Trailing empty paragraph ───────────────────────────────────────────────

    @Test
    fun a_loaded_trailing_empty_paragraph_survives_the_export() {
        val json = """{"type":"doc","content":[
            {"type":"paragraph","content":[{"type":"text","text":"abc"}]},
            {"type":"paragraph"}
        ]}"""
        val editor = editorFrom(json)
        assertEquals("abc\n", editor.text)

        assertEquals("abc\n", reloaded(editor).text, "the trailing blank line was lost on export")
    }

    @Test
    fun a_typed_trailing_blank_line_survives_save_and_reload() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.typeText(editor.text.length, "\n")
        assertEquals("Hello world\n", editor.text)

        assertEquals("Hello world\n", reloaded(editor).text)
    }

    @Test
    fun a_blank_line_only_document_survives_save_and_reload() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x")
        editor.typeText(1, "\n")
        editor.deleteText(0, 1)
        assertEquals("\n", editor.text)

        assertEquals("\n", reloaded(editor).text)
    }

    // ── Empty text nodes ───────────────────────────────────────────────────────

    @Test
    fun removing_a_list_decorator_leaves_no_empty_text_node_in_the_export() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x")
        editor.toListItem(TextRange(0, 1), TextEditorListItem.None, TextEditorListItem.CheckList)
        editor.toListItem(TextRange(1, 2), TextEditorListItem.CheckList, TextEditorListItem.None)

        assertFalse(editor.toJson().contains("\"text\":\"\""), editor.toJson())
    }

    @Test
    fun deleting_a_paragraph_start_leaves_no_empty_text_node_in_the_export() {
        val editor = editorFrom("{}")
        editor.typeText(0, "ab")
        editor.typeText(2, "\n")
        editor.typeText(3, "cd")
        editor.deleteText(0, 2)

        assertFalse(editor.toJson().contains("\"text\":\"\""), editor.toJson())
    }

    // ── Stability & canonical empty form ───────────────────────────────────────

    @Test
    fun exports_stay_a_fixed_point_for_documents_with_empty_paragraphs() {
        val docs = listOf(
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"a"}]},{"type":"paragraph"},{"type":"paragraph","content":[{"type":"text","text":"b"}]}]}""",
            """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"abc"}]},{"type":"paragraph"}]}""",
        )
        for (doc in docs) {
            val once = editorFrom(doc).toJson()
            assertEquals(once, editorFrom(once).toJson(), "not a fixed point for $doc")
        }
    }

    @Test
    fun a_truly_empty_document_still_exports_as_empty_json() {
        assertEquals("{}", editorFrom("{}").toJson())

        val cleared = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        cleared.deleteText(0, cleared.text.length)
        assertEquals("", cleared.text)
        assertEquals("{}", cleared.toJson())
    }

    @Test
    fun a_list_item_does_not_gain_a_phantom_empty_paragraph() {
        // The trailing-paragraph preservation applies to the document, not inside list items.
        val editor = editorFrom(SampleDocuments.ORDERED_LIST)
        val json = editor.toJson()

        assertEquals(json, editorFrom(json).toJson())
        assertFalse(json.contains("\"content\":[]"), "no empty paragraph inside list items: $json")
    }
}
