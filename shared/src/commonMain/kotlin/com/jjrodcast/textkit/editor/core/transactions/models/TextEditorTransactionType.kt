package com.jjrodcast.textkit.editor.core.transactions.models

import com.jjrodcast.textkit.editor.core.parser.LinkAttrs
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.TextAlign

sealed class TextEditorTransactionType {
    data object Format : TextEditorTransactionType() {
        override val marks: Set<Mark> = emptySet()
    }

    data class Link(val href: String) : TextEditorTransactionType() {
        override val marks: Set<Mark>
            get() = if (href.isNotEmpty()) setOf(LinkMark(LinkAttrs(href))) else emptySet()
    }

    data class Color(val color: String?) : TextEditorTransactionType() {
        override val marks: Set<Mark> = emptySet()
    }

    /**
     * Paragraph-level horizontal alignment change. Unlike [Format] this is not a mark: it retags the
     * pieces of every paragraph the selection touches (see the piece table's `updateTextAlign`) and
     * so applies to whole paragraphs even with a collapsed caret. Carries no [marks].
     */
    data class Alignment(val textAlign: TextAlign) : TextEditorTransactionType() {
        override val marks: Set<Mark> = emptySet()
    }

    abstract val marks: Set<Mark>
}
