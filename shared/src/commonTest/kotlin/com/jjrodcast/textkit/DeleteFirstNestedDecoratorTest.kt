package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deleting inside the decorator of a nested item that is the document's FIRST paragraph must not
 * crash: the delete path decides to remove the previous line break when the item's level beats the
 * previous item's, but a first paragraph has no previous line break — its level merely beats the
 * defaulted level of a paragraph that does not exist, and the offset went negative.
 */
class DeleteFirstNestedDecoratorTest {

    private val NESTED_FIRST = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
          ]}
        ]}
      ]}
    ]}"""

    @Test
    fun deleting_inside_the_first_nested_items_decorator_removes_it_whole() {
        val editor = editorFrom(NESTED_FIRST)
        editor.deleteText(1, 2)

        // The decorator is presentation-only and atomic: a removal window inside it dissolves the
        // item into a plain paragraph, same as for a top-level item.
        assertEquals("x", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    private val NESTED_TWO = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"a"}]}]},
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"b"}]}]}
          ]}
        ]}
      ]}
    ]}"""

    @Test
    fun deleting_inside_a_nested_items_decorator_below_another_item_still_merges_up() {
        val editor = editorFrom(NESTED_TWO)
        // Inside the SECOND item's decorator: the previous item is the same type, so the item
        // dissolves and its text merges into the previous line, decorator and break removed.
        val second = editor.text.indexOf('\n') + 2
        editor.deleteText(second, 2)

        assertTrue(editor.text.endsWith("ab"), editor.text.replace("\t", "\\t"))
        assertEquals(0, editor.text.count { it == '\n' }, editor.text.replace("\n", "\\n").replace("\t", "\\t"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
