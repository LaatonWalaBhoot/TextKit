package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A range toggle whose selection spans a plain paragraph and reaches into a following nested list
 * item must convert cleanly: the nested item's existing decorator is replaced, never stacked
 * behind a second one, and the line break between the paragraphs survives.
 */
class RangeToggleOverNestedItemTest {

    /** No decorator piece may sit anywhere but the start of its paragraph. */
    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}"
            )
        }
    }

    /** Parent bullet item with two nested (level-2) items. */
    private val NESTED_TWO = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"paragraph","content":[{"type":"text","text":"a"}]},
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"b"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"c"}]}]}
          ]}
        ]}
      ]}
    ]}"""

    @Test
    fun a_toggle_spanning_a_plain_line_and_a_nested_items_decorator_does_not_stack_decorators() {
        val editor = editorFrom(NESTED_TWO)
        // Open an empty plain line between the two nested items, then toggle from inside the first
        // nested item's text, across the empty line, ending inside the second item's decorator.
        val cLine = editor.text.lastIndexOf('\n') + 1
        editor.typeText(cLine, "\n")
        val bAt = editor.text.indexOf('b')
        val cLineStart = editor.text.lastIndexOf('\n') + 1

        editor.toListItem(TextRange(bAt, cLineStart + 2), TextEditorListItem.None, TextEditorListItem.BulletedList)

        editor.assertNoMidlineDecorator()
        assertTrue(editor.text.contains("c"), editor.text)
        // The break between the converted empty line and the following item survives.
        assertEquals(4, editor.text.count { it == '\n' }, editor.text.replace("\n", "\\n").replace("\t", "\\t"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun typing_after_the_toggle_does_not_crash() {
        val editor = editorFrom(NESTED_TWO)
        val cLine = editor.text.lastIndexOf('\n') + 1
        editor.typeText(cLine, "\n")
        val bAt = editor.text.indexOf('b')
        val cLineStart = editor.text.lastIndexOf('\n') + 1
        editor.toListItem(TextRange(bAt, cLineStart + 2), TextEditorListItem.None, TextEditorListItem.BulletedList)

        editor.typeText(editor.text.length, "d")

        assertTrue(editor.text.endsWith("d"), editor.text)
        editor.assertNoMidlineDecorator()
    }
}
