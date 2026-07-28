package com.jjrodcast.textkit.editor.core.transactions.text

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.core.models.MultiPieceParagraph
import com.jjrodcast.textkit.editor.core.models.PieceParagraph
import com.jjrodcast.textkit.editor.core.models.TextEditorModel
import com.jjrodcast.textkit.editor.core.transactions.TextEditorTransaction
import com.jjrodcast.textkit.editor.core.transactions.lists.models.TextEditorListItemTransaction
import com.jjrodcast.textkit.editor.core.transactions.models.TextEditorAction
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.getOffsetAfterDecorator
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.reorderListItemsOnUpdate
import com.jjrodcast.textkit.editor.core.transactions.text.TextTransactionsUtils.updateTransaction

internal object TextUpdateTransaction {
    internal fun updateText(
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated,
        manager: TextKitEditorManager
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val selectedParagraphs = lines.paragraphsInSelectedRange.filter { it.piecesInSelectedRange.isNotEmpty() }
        return if (selectedParagraphs.size > 1) {
            val firstParagraphInRange = selectedParagraphs.first()
            val lastParagraphInRange = selectedParagraphs.last()
            manager.transaction.updateOnMultipleParagraphs(firstParagraphInRange, lastParagraphInRange, lines, actionModel)
        } else {
            manager.transaction.updateOnSingleParagraph(selectedParagraphs.first(), actionModel)
        }
    }

    private fun TextEditorTransaction.updateOnMultipleParagraphs(
        firstParagraph: PieceParagraph,
        lastParagraph: PieceParagraph,
        lines: MultiPieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val firstParagraphIncludesDecorator = firstParagraph.piecesInSelectedRange.first().piece.isDecorator
        val isLastDecoratorPartiallySelected =
            getOffsetAfterDecorator(lastParagraph, lastParagraph.piecesInSelectedRange.last().piece.offset) > 0
        val transactions = mutableListOf<TextEditorListItemTransaction>()

        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (firstParagraphIncludesDecorator) {
            // Same clamping as the single-paragraph path: the window start moves past the first
            // item's decorator and the length loses the covered part, never inverting below zero.
            val remainingDecoratorOffset = maxOf(getOffsetAfterDecorator(firstParagraph, actionModel.offset), 0)
            offset += remainingDecoratorOffset
            length = maxOf(length - remainingDecoratorOffset, 0)
        }

        if (isLastDecoratorPartiallySelected) {
            val remainingDecoratorOffset =
                getOffsetAfterDecorator(lastParagraph, actionModel.offset + actionModel.removeLength)
            length += remainingDecoratorOffset
        }

        val marks = marksAtOrEmpty(offset)
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = null)
        val deleteTransaction = updateTransaction(offset, model, length)
        transactions.add(deleteTransaction)

        // Update next items
        val nextParagraphsTransactions = reorderListItemsOnUpdate(lines)
        transactions.addAll(nextParagraphsTransactions)

        return Pair(TextRange(offset + actionModel.text.length), transactions)
    }

    /**
     * Marks of the piece at [offset], or none when [offset] sits at the document end (a clamped
     * window on an empty list item lands there — nothing to inherit marks from).
     */
    private fun TextEditorTransaction.marksAtOrEmpty(offset: Int) =
        if (offset < text.length) getTextAt(offset).piece.marks else emptySet()

    private fun TextEditorTransaction.updateOnSingleParagraph(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        return updateTextAfterDecorator(paragraph, actionModel)
    }

    private fun TextEditorTransaction.updateTextAfterDecorator(
        paragraph: PieceParagraph,
        actionModel: TextEditorAction.TextUpdated
    ): Pair<TextRange, List<TextEditorListItemTransaction>> {
        val transactions = mutableListOf<TextEditorListItemTransaction>()
        val selectionIncludesDecorator = paragraph.piecesInSelectedRange.first().piece.isDecorator
        var offset = actionModel.offset
        var length = actionModel.removeLength

        if (selectionIncludesDecorator) {
            // The decorator is presentation-only and atomic, so the replace window is clamped to the
            // item's content: the start moves past the decorator and the length loses the part that
            // lay inside it — never below zero, or the window inverts (a window fully inside the
            // decorator becomes a plain insert at the content start).
            val remainingDecoratorOffset = maxOf(getOffsetAfterDecorator(paragraph, actionModel.offset), 0)
            offset += remainingDecoratorOffset
            length = maxOf(length - remainingDecoratorOffset, 0)
        }

        val marks = this.marksAtOrEmpty(offset)
        val model = TextEditorModel.create(text = actionModel.text, marks = marks, decorator = null)
        val updateTransaction = updateTransaction(offset, model, length)

        val rangeOffset = offset + actionModel.text.length
        val range = TextRange(rangeOffset)

        transactions.add(updateTransaction)

        return Pair(range, transactions)
    }
}
