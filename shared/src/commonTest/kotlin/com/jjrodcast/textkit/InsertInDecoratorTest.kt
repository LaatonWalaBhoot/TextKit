package com.jjrodcast.textkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An insert (plain text or a line break) whose offset falls inside a list item's decorator region
 * must behave as if the offset were at the item's content start — the decorator is presentation-only
 * and atomic. It was instead splicing the typed text (or the break) into the decorator itself,
 * leaving the live text stream and the document model disagreeing. Regression cover for issue #69,
 * completing for the insert path what #58 fixed for replace.
 */
class InsertInDecoratorTest {

    private val ONE_BULLET = """{"type":"doc","content":[{"type":"bulletList","content":[
        {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"bb"}]}]}
    ]}]}"""

    private fun assertStreamMatchesModel(editor: com.jjrodcast.textkit.editor.core.TextKitEditorManager) {
        assertEquals(editorFrom(editor.toJson()).text, editor.text, "live text diverged from the model")
    }

    // ── Plain inserts at decorator-interior offsets ────────────────────────────

    @Test
    fun typing_at_a_decorator_interior_offset_lands_at_the_content_start() {
        for (at in 1..3) {
            val editor = editorFrom(ONE_BULLET)

            editor.typeText(at, "x")

            assertTrue(editor.text.contains("xbb"), "@$at: text lands at content start: [${editor.text}]")
            assertStreamMatchesModel(editor)
            assertFalse(editor.toJson().contains("\\t"), "@$at: no decorator leak: ${editor.toJson()}")
        }
    }

    @Test
    fun typing_in_a_task_items_decorator_lands_at_the_content_start() {
        val editor = editorFrom(SampleDocuments.TASK_LIST)

        editor.typeText(2, "x")

        assertTrue(editor.text.contains("xbuy milk"), "text: [${editor.text}]")
        assertStreamMatchesModel(editor)
        assertTrue(editor.toJson().contains("taskList"), "the item stays a task item: ${editor.toJson()}")
        assertFalse(editor.toJson().contains("\\t"), "no decorator leak: ${editor.toJson()}")
    }

    // ── Line breaks at decorator-interior offsets ──────────────────────────────

    @Test
    fun a_break_at_a_decorator_interior_offset_splits_at_the_item_boundary() {
        for (at in 1..3) {
            val editor = editorFrom(ONE_BULLET)

            editor.typeText(at, "\n")

            assertStreamMatchesModel(editor)
            assertFalse(editor.toJson().contains("\\t"), "@$at: no decorator leak: ${editor.toJson()}")
            assertTrue(editor.text.contains("bb"), "@$at: content survives: [${editor.text}]")
        }
    }

    @Test
    fun a_break_at_the_item_start_inserts_a_line_above_without_touching_the_item() {
        val editor = editorFrom(ONE_BULLET)

        editor.typeText(0, "\n")

        assertStreamMatchesModel(editor)
        assertTrue(editor.toJson().contains("bulletList"), "the item stays a list item: ${editor.toJson()}")
        assertFalse(editor.toJson().contains("\\t"), "no decorator leak: ${editor.toJson()}")
    }

    // ── Guard: the working boundary stays working ──────────────────────────────

    @Test
    fun a_break_at_the_content_start_still_splits_cleanly() {
        val editor = editorFrom(ONE_BULLET)
        val contentStart = editor.offsetOf("bb")

        editor.typeText(contentStart, "\n")

        assertStreamMatchesModel(editor)
    }
}
