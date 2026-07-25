package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextAlignTest {

    private fun docWithAlign(align: String) = """
        {"type":"doc","content":[
          {"type":"paragraph","attrs":{"textAlign":"$align"},"content":[{"type":"text","text":"Hello"}]}
        ]}
    """

    @Test
    fun centerAlignmentSurvivesRoundTrip() {
        val json = editorFrom(docWithAlign("center")).toJson()
        assertTrue(json.contains("\"textAlign\":\"center\""), "expected center in: $json")
    }

    @Test
    fun rightAlignmentSurvivesRoundTrip() {
        val json = editorFrom(docWithAlign("right")).toJson()
        assertTrue(json.contains("\"textAlign\":\"right\""), "expected right in: $json")
    }

    @Test
    fun justifyAlignmentSurvivesRoundTrip() {
        val json = editorFrom(docWithAlign("justify")).toJson()
        assertTrue(json.contains("\"textAlign\":\"justify\""), "expected justify in: $json")
    }

    @Test
    fun alignmentSurvivesEditing() {
        val editor = editorFrom(docWithAlign("center"))
        editor.typeText(editor.offsetOf("Hello") + "Hello".length, " world")
        val json = editor.toJson()
        assertTrue(editor.text.contains("Hello world"), "text: ${editor.text}")
        assertTrue(json.contains("\"textAlign\":\"center\""), "expected center after edit in: $json")
    }

    @Test
    fun unknownAlignmentCoercesToLeft() {
        val json = editorFrom(docWithAlign("wobble")).toJson()
        assertTrue(json.contains("\"textAlign\":\"left\""), "expected left fallback in: $json")
    }

    @Test
    fun updateTextAlignmentAppliesOverSelection() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        val (changed, _) = editor.setTextAlign(editor.rangeOf("Hello world"), TextAlign.Center)
        assertTrue(changed, "expected the alignment change to apply")
        assertTrue(editor.toJson().contains("\"textAlign\":\"center\""), "json: ${editor.toJson()}")
    }

    @Test
    fun updateTextAlignmentWorksWithCollapsedCaret() {
        val editor = editorFrom(SampleDocuments.SINGLE_PARAGRAPH)
        // Collapsed caret inside the paragraph — alignment is paragraph-level, so it still applies.
        val (changed, _) = editor.setTextAlign(TextRange(2), TextAlign.Right)
        assertTrue(changed, "expected collapsed-caret alignment to apply")
        assertTrue(editor.toJson().contains("\"textAlign\":\"right\""), "json: ${editor.toJson()}")
    }

    @Test
    fun updateTextAlignmentOnlyTargetsTouchedParagraph() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        editor.setTextAlign(editor.rangeOf("First paragraph"), TextAlign.Center)
        val json = editor.toJson()
        // Only the first paragraph is centered; the second stays left-aligned.
        assertTrue(json.contains("\"textAlign\":\"center\""), "json: $json")
        assertTrue(json.contains("\"textAlign\":\"left\""), "second paragraph should remain left: $json")
    }

    @Test
    fun reapplyingSameAlignmentIsNoOp() {
        val editor = editorFrom(docWithAlign("center"))
        val (changed, _) = editor.setTextAlign(editor.rangeOf("Hello"), TextAlign.Center)
        assertFalse(changed, "re-applying the existing alignment should report no change")
    }

    @Test
    fun getParagraphsExposesAlignmentForRendering() {
        val editor = editorFrom(docWithAlign("right"))
        val paragraph = editor.getParagraphs().first()
        // The renderer reads paragraph.textAlign to build the paragraph's ParagraphStyle.
        assertEquals(TextAlign.Right, paragraph.textAlign)
    }

    @Test
    fun getParagraphsReportsPerParagraphAlignment() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)
        editor.setTextAlign(editor.rangeOf("First paragraph"), TextAlign.Center)
        val paragraphs = editor.getParagraphs()
        assertEquals(TextAlign.Center, paragraphs[0].textAlign)
        assertEquals(TextAlign.Left, paragraphs[1].textAlign)
    }
}
