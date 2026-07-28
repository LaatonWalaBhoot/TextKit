package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deleting an entire line that precedes a list item must leave the item untouched — it keeps its
 * decorator and its list identity in the export. The delete was leaving a zero-length remnant piece
 * at the line start, which hid the following decorator from paragraph-type detection: the item
 * exported as a plain paragraph with the decorator string as literal text. Regression cover for
 * issue #72.
 */
class WholeLineDeleteBeforeListTest {

    private fun docWithLeadingParagraph(listNode: String) =
        """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"intro"}]},$listNode]}"""

    private val BULLET = """{"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}]}"""
    private val ORDERED = """{"type":"orderedList","attrs":{"start":1},"content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}]}"""
    private val TASK = """{"type":"taskList","content":[{"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}]}"""

    private fun assertKeepsIdentity(listNode: String, marker: String) {
        val editor = editorFrom(docWithLeadingParagraph(listNode))
        val brk = editor.offsetOf("\n")

        editor.deleteText(0, brk + 1)

        val json = editor.toJson()
        assertTrue(json.contains(marker), "the item must keep its list identity: $json")
        assertFalse(json.contains("\\t"), "no decorator leak: $json")
        assertEquals(editorFrom(json).text, editor.text, "live text diverged from the model")
    }

    @Test
    fun whole_line_delete_keeps_a_bulleted_item() = assertKeepsIdentity(BULLET, "bulletList")

    @Test
    fun whole_line_delete_keeps_an_ordered_item() = assertKeepsIdentity(ORDERED, "orderedList")

    @Test
    fun whole_line_delete_keeps_a_task_item() = assertKeepsIdentity(TASK, "\"checked\":true")

    @Test
    fun whole_line_delete_between_list_items_keeps_the_following_item() {
        val json = """{"type":"doc","content":[
            {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"aa"}]}]}]},
            {"type":"paragraph","content":[{"type":"text","text":"mid"}]},
            {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bb"}]}]}]}
        ]}"""
        val editor = editorFrom(json)
        val start = editor.offsetOf("mid")

        editor.deleteText(start, "mid\n".length)

        val out = editor.toJson()
        assertFalse(out.contains("\\t"), "no decorator leak: $out")
        assertEquals(editorFrom(out).text, editor.text)
    }
}
