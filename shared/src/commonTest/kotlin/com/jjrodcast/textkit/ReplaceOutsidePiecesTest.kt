package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A replace whose window sits where no piece is in range — an empty paragraph or the document end —
 * must degrade to a plain replacement instead of crashing. A replace with `removeLength = 0` is how
 * IME composition and autocorrect deliver text, and the document end is where composition usually
 * happens. Regression cover for issue #77.
 */
class ReplaceOutsidePiecesTest {

    private val EMPTY_PARA_MID = """{"type":"doc","content":[
        {"type":"paragraph","content":[{"type":"text","text":"a"}]},
        {"type":"paragraph"},
        {"type":"paragraph","content":[{"type":"text","text":"b"}]}
    ]}"""

    private fun assertConsistent(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        val json = editor.toJson()
        assertEquals(editorFrom(json).text, editor.text, "live text diverged from the model")
        assertEquals(json, editorFrom(json).toJson(), "export is not a fixed point")
    }

    @Test
    fun replace_on_an_empty_paragraph_inserts_there() {
        val editor = editorFrom(EMPTY_PARA_MID)

        editor.replaceText(offset = 2, removeLength = 0, textToAdd = "e")

        assertEquals("a\ne\nb", editor.text)
        assertConsistent(editor)
    }

    @Test
    fun replace_at_the_document_end_appends() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)

        editor.replaceText(offset = editor.text.length, removeLength = 0, textToAdd = "e")

        assertEquals("Hello worlde", editor.text)
        assertConsistent(editor)
    }

    @Test
    fun replace_on_a_trailing_blank_line_inserts_there() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.typeText(editor.text.length, "\n")

        editor.replaceText(offset = editor.text.length, removeLength = 0, textToAdd = "e")

        assertEquals("Hello world\ne", editor.text)
        assertConsistent(editor)
    }

    @Test
    fun replace_at_the_end_of_a_bold_run_continues_the_formatting() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        editor.applyStyle(editor.rangeOf("Hello world"), com.jjrodcast.textkit.editor.components.TextEditorStyleItem.Bold)

        editor.replaceText(offset = editor.text.length, removeLength = 0, textToAdd = "e")

        assertEquals("Hello worlde", editor.text)
        assertTrue(
            editor.marksAt(editor.rangeOf("worlde")).has<com.jjrodcast.textkit.editor.core.parser.BoldMark>(),
            "the appended character inherits the bold run: ${editor.toJson()}",
        )
        assertConsistent(editor)
    }

    @Test
    fun replace_on_a_blank_line_only_document_inserts_there() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x")
        editor.typeText(1, "\n")
        editor.deleteText(0, 1)

        editor.replaceText(offset = 1, removeLength = 0, textToAdd = "e")

        assertEquals("\ne", editor.text)
        assertConsistent(editor)
    }
}
