package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Edge cases for embedded blocks: editing the text around a placeholder, embeds at the document
 * start/end, and multiple mixed embeds. The block stays atomic and its verbatim JSON survives.
 *
 * Complements [EmbeddedBlockTest] (load/round-trip/insert/update/remove of a single embed).
 */
class EmbedEdgeCasesTest {

    private fun docWith(vararg nodes: String) =
        """{"type":"doc","content":[${nodes.joinToString(",")}]}"""

    private fun paragraph(text: String) =
        """{"type":"paragraph","content":[{"type":"text","text":"$text"}]}"""

    private val tableNode = """
        {"type":"table","content":[
          {"type":"tableRow","content":[
            {"type":"tableCell","attrs":{"colspan":1,"rowspan":1,"colwidth":null},"content":[{"type":"paragraph","content":[{"type":"text","text":"Juan"}]}]}
          ]}
        ]}
    """.trimIndent()

    private val imageNode = """{"type":"image","attrs":{"src":"photo.png","alt":"A photo"}}"""

    /** Number of atomic embed placeholders currently in the document. */
    private fun com.jjrodcast.textkit.editor.core.TextKitEditorManager.embedCount(): Int =
        getParagraphs().flatMap { it.children }.count { it.isEmbed }

    // ── Position in the document ───────────────────────────────────────────────

    @Test
    fun an_embed_at_the_document_start_loads_and_round_trips() {
        val editor = editorFrom(docWith(tableNode, paragraph("After")))

        assertEquals("📊 Tabla 1\nAfter", editor.text)
        assertEquals(1, editor.embedCount())
        assertTrue(editor.toJson().contains("\"text\":\"Juan\""), "table content survives")
    }

    @Test
    fun an_embed_at_the_document_end_loads_and_round_trips() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode))

        assertEquals("Before\n📊 Tabla 1", editor.text)
        assertEquals(1, editor.embedCount())
        assertTrue(editor.toJson().contains("\"text\":\"Juan\""))
    }

    // ── Editing around an embed ────────────────────────────────────────────────

    @Test
    fun typing_before_an_embed_keeps_it_intact() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))

        editor.typeText(offset = editor.offsetOf("Before") + "Before".length, textToAdd = "!")

        assertTrue(editor.text.contains("Before!"))
        assertEquals(1, editor.embedCount())
        assertTrue(editor.toJson().contains("\"text\":\"Juan\""))
        assertEquals(3, editor.getParagraphs().size)
    }

    @Test
    fun typing_after_an_embed_keeps_it_intact() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))

        editor.typeText(offset = editor.offsetOf("After"), textToAdd = ">>")

        assertTrue(editor.text.contains(">>After"))
        assertEquals(1, editor.embedCount())
        assertTrue(editor.toJson().contains("\"text\":\"Juan\""))
    }

    @Test
    fun deleting_an_adjacent_paragraph_keeps_the_embed() {
        val editor = editorFrom(docWith(paragraph("Before"), tableNode, paragraph("After")))

        editor.deleteText(offset = editor.offsetOf("After"), length = "After".length)

        assertEquals(1, editor.embedCount())
        assertTrue(editor.toJson().contains("\"text\":\"Juan\""))
    }

    // ── Multiple embeds ────────────────────────────────────────────────────────

    @Test
    fun multiple_mixed_embeds_load_and_round_trip_with_their_payloads() {
        val editor = editorFrom(docWith(imageNode, paragraph("mid"), tableNode))

        assertEquals(2, editor.embedCount())
        assertEquals("🖼 Imagen 1\nmid\n📊 Tabla 1", editor.text)

        val json = editor.toJson()
        assertTrue(json.contains("photo.png"), "image payload survives")
        assertTrue(json.contains("\"text\":\"Juan\""), "table payload survives")
    }

    @Test
    fun an_image_embed_exports_to_an_img_tag() {
        val editor = editorFrom(docWith(imageNode))

        assertEquals("<img src=\"photo.png\" alt=\"A photo\">", editor.toHtml())
    }
}
