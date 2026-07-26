package com.jjrodcast.textkit.ui.listlayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.jjrodcast.textkit.editor.core.parser.TextAlign as TextKitTextAlign
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel
import com.jjrodcast.textkit.editor.core.piecetable.models.TextDecoratorModel.Companion.createDecoratorString
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorItem
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorParagraph
import kotlin.math.roundToInt

internal object ListItemEditorTransform {

    fun buildDisplayAnnotatedString(
        paragraphs: List<TextEditorParagraph>,
        fieldLength: Int,
        defaultStyle: ParagraphStyle,
        toComposeAlign: (TextKitTextAlign) -> TextAlign,
        displayTextOf: (TextEditorItem, Int) -> String,
        spanStyleOf: (TextEditorItem) -> SpanStyle,
    ): AnnotatedString = buildAnnotatedString {
        paragraphs.forEach { paragraph ->
            val splitLayout = paragraph.usesSplitListLayout()
            withStyle(paragraphStyle(paragraph, defaultStyle, toComposeAlign, splitLayout)) {
                paragraph.children.forEach { child ->
                    if (splitLayout && child.decorator != null) return@forEach
                    withStyle(spanStyleOf(child)) {
                        append(displayTextOf(child, fieldLength))
                    }
                }
            }
        }
    }

    fun offsetMapping(
        segments: List<EditorParagraphSegment>,
        totalDisplayLength: Int,
    ): OffsetMapping = if (editorSegmentsNeedOffsetMapping(segments)) {
        ListItemOffsetMapping(segments, totalDisplayLength)
    } else {
        OffsetMapping.Identity
    }

    private fun paragraphStyle(
        paragraph: TextEditorParagraph,
        defaultStyle: ParagraphStyle,
        toComposeAlign: (TextKitTextAlign) -> TextAlign,
        splitLayout: Boolean,
    ): ParagraphStyle {
        val base = defaultStyle.copy(textAlign = toComposeAlign(paragraph.textAlign))
        if (!splitLayout) return base
        val gutter = paragraph.listDecoratorChild()?.decorator ?: return base
        val indent = gutterIndent(gutter)
        return base.copy(textIndent = TextIndent(firstLine = indent, restLine = indent))
    }

    private fun gutterIndent(decorator: TextDecoratorModel): TextUnit =
        (decorator.createDecoratorString().length * GUTTER_EM_FACTOR).em

    private const val GUTTER_EM_FACTOR = 0.55f
}

private class ListItemOffsetMapping(
    private val segments: List<EditorParagraphSegment>,
    private val totalDisplayLength: Int,
) : OffsetMapping {

    override fun originalToTransformed(offset: Int): Int {
        var skippedGutters = 0
        for (segment in segments) {
            if (offset <= segment.fieldStart) break
            if (segment.gutterLength == 0) continue
            when {
                offset >= segment.fieldEnd -> skippedGutters += segment.gutterLength
                offset < segment.fieldStart + segment.gutterLength -> return segment.displayStart
                else -> return segment.displayStart + (offset - segment.fieldStart - segment.gutterLength)
            }
        }
        return (offset - skippedGutters).coerceIn(0, totalDisplayLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        for (segment in segments) {
            if (offset < segment.displayStart) break
            if (offset <= segment.displayEnd) {
                val local = offset - segment.displayStart
                return if (segment.gutterLength == 0) {
                    segment.fieldStart + local
                } else {
                    segment.fieldStart + segment.gutterLength + local
                }
            }
        }
        return segments.lastOrNull()?.fieldEnd ?: offset
    }
}

/**
 * Draws list gutters at a fixed horizontal origin (container start), independent of content alignment.
 * Vertical position follows the laid-out display line for each split segment.
 *
 * The overlay should fill the text field parent ([Modifier.fillMaxSize]) so gutter Y tracks
 * layout without resizing its own box (avoids blink when textAlign changes).
 */
@Composable
internal fun ListItemEditorGutterOverlay(
    layoutResult: TextLayoutResult?,
    segments: List<EditorParagraphSegment>,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    val overlaySegments = editorOverlaySegments(segments)
    if (layoutResult == null || overlaySegments.isEmpty()) return

    val displayLength = layoutResult.layoutInput.text.length
    if (displayLength == 0) return

    Box(modifier = modifier) {
        val markerStyle = textStyle.copy(color = textColor)
        overlaySegments.forEach { segment ->
            val gutter = segment.gutter ?: return@forEach
            val label = gutter.createDecoratorString()
            if (label.isEmpty()) return@forEach

            val displayOffset = segment.displayStart.coerceIn(0, displayLength - 1)
            val lineIndex = layoutResult.getLineForOffset(displayOffset)
            val lineTop = layoutResult.getLineTop(lineIndex)

            Text(
                text = label,
                style = markerStyle,
                modifier = Modifier.offset {
                    IntOffset(x = 0, y = lineTop.roundToInt())
                }
            )
        }
    }
}
