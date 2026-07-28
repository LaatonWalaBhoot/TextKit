package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A delete that swallows the line break between two list items (or a paragraph and a following
 * item) merges them — and the live text stream must agree with the document model about it. The
 * exact-boundary case (the range ending at the next item's decorator start) left the decorator
 * orphaned mid-line in the stream while the export dropped it. Regression cover for issue #67.
 */
class BoundaryDeleteIntoListTest {

    private val TWO_BULLETS = """{"type":"doc","content":[{"type":"bulletList","content":[
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"aa"}]}]},
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bb"}]}]}
    ]}]}"""

    private val TWO_TASKS = SampleDocuments.TASK_LIST

    /** The live stream and a reload of the export must describe the same text. */
    private fun assertStreamMatchesModel(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        assertEquals(editorFrom(editor.toJson()).text, editor.text, "live text diverged from the model")
    }

    @Test
    fun deleting_up_to_the_next_bullets_decorator_merges_without_an_orphan() {
        val editor = editorFrom(TWO_BULLETS)
        val brk = editor.offsetOf("\n")

        // Deletes the last content char + the line break; range ends exactly at item 2's decorator.
        editor.deleteText(brk - 1, 2)

        assertStreamMatchesModel(editor)
    }

    @Test
    fun deleting_only_the_line_break_between_items_merges_without_an_orphan() {
        val editor = editorFrom(TWO_BULLETS)
        val brk = editor.offsetOf("\n")

        editor.deleteText(brk, 1)

        assertStreamMatchesModel(editor)
    }

    @Test
    fun deleting_up_to_a_task_items_decorator_merges_without_an_orphan() {
        val editor = editorFrom(TWO_TASKS)
        val brk = editor.offsetOf("\n")

        editor.deleteText(brk - 1, 2)

        assertStreamMatchesModel(editor)
    }

    @Test
    fun deleting_from_a_plain_paragraph_up_to_a_list_decorator_merges_without_an_orphan() {
        val json = """{"type":"doc","content":[
            {"type":"paragraph","content":[{"type":"text","text":"intro"}]},
            {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"item"}]}]}]}
        ]}"""
        val editor = editorFrom(json)
        val brk = editor.offsetOf("\n")

        editor.deleteText(brk - 1, 2)

        assertStreamMatchesModel(editor)
    }

    @Test
    fun deleting_into_the_decorator_still_merges_cleanly() {
        // The already-working neighbour case, pinned so the fix cannot regress it.
        val editor = editorFrom(TWO_BULLETS)
        val brk = editor.offsetOf("\n")

        editor.deleteText(brk - 1, 4)

        assertStreamMatchesModel(editor)
    }
}
