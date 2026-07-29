package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A delete or replace whose removal window reaches into a following list item's decorator — either
 * ending strictly inside it, or stopping at the item's start after consuming the preceding line
 * break — must swallow the decorator whole and merge the lines. Leaving a fragment puts decorator
 * text mid-line, which the export silently drops and later inserts crash on.
 *
 * The items are built by typing + toggling (not loading), so their decorators live in the ADDED
 * buffer: the broken predicate compared a buffer offset against document offsets and only
 * misfired once decorators sat deep in that buffer.
 */
class RemovalWindowIntoDecoratorTest {

    /** No decorator piece may sit anywhere but the start of its paragraph. */
    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}"
            )
        }
    }

    private fun TextKitEditorManager.assertExportStable() {
        val once = toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    /**
     * `\t\t• one` / `\t\t• two`, built after typing and deleting a long padding paragraph. The
     * padding grows the ADDED buffer while the document shrinks back, so the items' decorators sit
     * at buffer offsets far past any document offset — the states where the buffer/document mixup
     * flipped the predicate (in a fresh document the two coordinate spaces track too closely to
     * misfire).
     */
    private fun twoBulletItems(): TextKitEditorManager {
        val editor = editorFrom("{}")
        editor.typeText(0, "padding padding padding padding padding")
        editor.deleteText(0, editor.text.length)
        editor.typeText(0, "one\ntwo")
        editor.toListItem(TextRange(0, editor.text.length), TextEditorListItem.None, TextEditorListItem.BulletedList)
        return editor
    }

    @Test
    fun a_delete_ending_inside_the_next_items_decorator_swallows_it_whole() {
        val editor = twoBulletItems()
        val breakAt = editor.text.lastIndexOf('\n')

        // From inside "one", across the break, ending two characters into item two's decorator.
        editor.deleteText(breakAt - 1, 3)

        assertTrue(editor.text.contains("on"), editor.text)
        assertTrue(editor.text.contains("two"), editor.text)
        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
        assertEquals(1, editor.toJson().split("\"type\":\"listItem\"").size - 1, "items must merge into one")
    }

    @Test
    fun a_replace_ending_inside_the_next_items_decorator_swallows_it_whole() {
        val editor = twoBulletItems()
        val breakAt = editor.text.lastIndexOf('\n')

        editor.replaceText(breakAt - 1, 3, "X")

        assertTrue(editor.text.contains("onX"), editor.text)
        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
        assertEquals(1, editor.toJson().split("\"type\":\"listItem\"").size - 1, "items must merge into one")
    }

    @Test
    fun replacing_the_break_between_two_items_merges_them_without_orphaning_the_decorator() {
        val editor = twoBulletItems()
        val breakAt = editor.text.lastIndexOf('\n')

        // The removal window is exactly the terminating break; the replacement text becomes the
        // head of the merged line, so the second item's decorator must go with the merge.
        editor.replaceText(breakAt, 1, "X")

        assertTrue(editor.text.contains("oneXtwo"), editor.text)
        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
    }

    @Test
    fun replacing_the_break_between_two_task_items_merges_them() {
        val editor = editorFrom("{}")
        editor.typeText(0, "a\nb")
        editor.toListItem(TextRange(0, editor.text.length), TextEditorListItem.None, TextEditorListItem.CheckList)
        val breakAt = editor.text.indexOf('\n')

        editor.replaceText(breakAt, 1, "X")

        assertTrue(editor.text.contains("aXb"), editor.text)
        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
        assertEquals(1, editor.toJson().split("\"checked\"").size - 1, "task items must merge into one")
    }

    @Test
    fun typing_after_the_merge_does_not_crash() {
        // The downstream symptom of the fragment: a later insert near the leaked decorator text
        // routed into the task-decorator handler with an offset far outside the decorator.
        val editor = editorFrom("{}")
        editor.typeText(0, "a\nb")
        editor.toListItem(TextRange(0, editor.text.length), TextEditorListItem.None, TextEditorListItem.CheckList)
        editor.replaceText(editor.text.indexOf('\n'), 1, "cc")

        editor.typeText(editor.text.length, "d")

        assertTrue(editor.text.contains("ccbd") || editor.text.contains("ccb") && editor.text.endsWith("d"), editor.text)
        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
    }

    @Test
    fun a_delete_into_a_numbered_decorator_renumbers_the_remaining_items() {
        val editor = editorFrom("{}")
        editor.typeText(0, "one\ntwo\nthree")
        editor.toListItem(TextRange(0, editor.text.length), TextEditorListItem.None, TextEditorListItem.NumberedList)
        val breakAt = editor.text.indexOf('\n')

        // Swallow item two's decorator: items one and two merge, item three becomes number two.
        editor.deleteText(breakAt - 1, 3)

        editor.assertNoMidlineDecorator()
        editor.assertExportStable()
        assertTrue(editor.text.contains("2. three") || editor.text.contains("2."), editor.text)
    }
}
