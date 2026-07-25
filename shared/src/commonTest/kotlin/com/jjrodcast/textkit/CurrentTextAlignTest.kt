package com.jjrodcast.textkit

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CurrentTextAlignTest {

    private fun stateWith(json: String): TextKitState =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    private fun TextKitState.caretAt(offset: Int) =
        onTextFieldChange(textFieldValue.copy(selection = TextRange(offset)))

    private fun TextKitState.selectAll() =
        onTextFieldChange(textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length)))

    private fun docWithAlign(align: String) = """
        {"type":"doc","content":[
          {"type":"paragraph","attrs":{"textAlign":"$align"},"content":[{"type":"text","text":"Hello world"}]}
        ]}
    """

    private val mixedAlignments = """
        {"type":"doc","content":[
          {"type":"paragraph","attrs":{"textAlign":"left"},"content":[{"type":"text","text":"left one"}]},
          {"type":"paragraph","attrs":{"textAlign":"center"},"content":[{"type":"text","text":"center two"}]}
        ]}
    """

    @Test
    fun reflectsCenterAlignmentAtCaret() {
        val state = stateWith(docWithAlign("center"))
        state.caretAt(3)
        assertEquals(TextAlign.Center, state.currentTextAlign)
    }

    @Test
    fun plainParagraphReadsAsLeft() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)
        assertEquals(TextAlign.Left, state.currentTextAlign)
    }

    @Test
    fun mixedSelectionReadsAsNull() {
        val state = stateWith(mixedAlignments)
        state.selectAll()
        assertNull(state.currentTextAlign, "a selection spanning differing alignments is mixed")
    }

    @Test
    fun updatesAfterApplyingAlignment() {
        val state = stateWith(SampleDocuments.SINGLE_PARAGRAPH)
        state.caretAt(3)
        assertEquals(TextAlign.Left, state.currentTextAlign)

        state.applyTextAlignment(TextAlign.Right)

        assertEquals(TextAlign.Right, state.currentTextAlign)
    }

    @Test
    fun displayRendersNewlineAsSpaceKeepingIdentityOffsets() {
        val state = stateWith(SampleDocuments.TWO_PARAGRAPHS)
        val fieldText = state.textFieldValue.text
        assertTrue(fieldText.contains("\n"), "field text keeps the paragraph separator: \"$fieldText\"")

        val transformed = state.visualTransformation.filter(AnnotatedString(fieldText))

        // The '\n' is shown as a (width-having, but visually invisible) space, so there is no gap line
        // and the caret can distinguish end-of-paragraph from start-of-next. Same length as the field,
        // so OffsetMapping stays Identity: every offset maps 1:1 to the piece table.
        assertEquals(fieldText.replace('\n', ' '), transformed.text.text)
        assertEquals(fieldText.length, transformed.text.length)
        val nl = fieldText.indexOf('\n')
        assertEquals(nl, transformed.offsetMapping.originalToTransformed(nl))
        assertEquals(nl + 1, transformed.offsetMapping.transformedToOriginal(nl + 1))
        assertEquals(fieldText.length, transformed.offsetMapping.originalToTransformed(fieldText.length))
    }

    @Test
    fun emptyParagraphGetsClickableLine() {
        val doc = """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"A"}]},
              {"type":"paragraph"},
              {"type":"paragraph","content":[{"type":"text","text":"B"}]}
            ]}
        """
        val state = stateWith(doc)
        val fieldText = state.textFieldValue.text // "A\n\nB"
        val display = state.visualTransformation.filter(AnnotatedString(fieldText)).text.text

        assertEquals(fieldText.length, display.length) // identity preserved
        // Every '\n' \u2014 the paragraph boundaries and the empty paragraph \u2014 becomes a clickable space.
        assertEquals("A  B", display)
    }

    @Test
    fun trailingEmptyParagraphKeepsRealNewline() {
        val doc = """
            {"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"A"}]},
              {"type":"paragraph"}
            ]}
        """
        val state = stateWith(doc)
        val fieldText = state.textFieldValue.text
        val display = state.visualTransformation.filter(AnnotatedString(fieldText)).text.text

        assertEquals(fieldText.length, display.length) // identity preserved
        // A '\n' at the very end of the document stays a real newline so the trailing empty paragraph
        // renders as a blank line the caret can land on (a space would not create that line).
        assertTrue(fieldText.endsWith('\n'), "field text: \"$fieldText\"")
        assertTrue(display.endsWith('\n'), "display: \"$display\"")
    }

    @Test
    fun viewerAppliesPerParagraphAlignment() {
        val state = stateWith(mixedAlignments)
        val (annotated, _) = state.viewerTextValue

        // Read-only view also shows '\n' as a space (no gap line), length preserved.
        assertEquals(state.textFieldValue.text.replace('\n', ' '), annotated.text)

        // Each paragraph gets its own ParagraphStyle; the second one is centered.
        val aligns = annotated.paragraphStyles.map { it.item.textAlign }
        assertTrue(aligns.contains(ComposeTextAlign.Center), "expected a centered paragraph: $aligns")
        assertTrue(aligns.contains(ComposeTextAlign.Left), "expected a left paragraph: $aligns")
    }
}
