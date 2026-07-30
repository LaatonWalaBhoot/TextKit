package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The export emits the loader's canonical nested-list form, so `toJson()` is a fixed point in
 * every nested state (issue #81):
 *
 * - `listItem` chains preserve relative depth on load, so their nesting is kept as-is.
 * - Anything nested below a `taskItem` re-enters at exactly one level under the item on load —
 *   deeper levels reached through live edits are exported as document-ordered sibling lists,
 *   because that is the only shape a reload can represent.
 * - A loaded empty nested item carries its line break as a standalone piece; that piece must not
 *   surface as an empty text node (`{"text":""}`) on re-export.
 */
class NestedListCanonicalFormTest {

    /** `listItem` chain, three levels: bullet > ordered > bullet. Depth survives load. */
    private val LIST_ITEM_CHAIN = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"paragraph","content":[{"type":"text","text":"a"}]},
          {"type":"orderedList","attrs":{"start":1},"content":[
            {"type":"listItem","content":[
              {"type":"paragraph","content":[{"type":"text","text":"b"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"c"}]}]}
              ]}
            ]}
          ]}
        ]}
      ]}
    ]}"""

    private val EMPTY_NESTED_ITEM = """{"type":"doc","content":[
      {"type":"taskList","content":[
        {"type":"taskItem","attrs":{"checked":false},"content":[
          {"type":"paragraph","content":[]},
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[]}]}
          ]},
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
          ]}
        ]}
      ]}
    ]}"""

    @Test
    fun a_loaded_empty_nested_item_does_not_export_an_empty_text_node() {
        val once = editorFrom(EMPTY_NESTED_ITEM).toJson()

        assertFalse(once.contains("\"text\":\"\""), "empty text node leaked: $once")
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun deep_nesting_under_a_task_item_exports_the_loadable_flattened_shape() {
        // Load the listItem chain (depth preserved: levels 1..3), then convert the top item to a
        // task item. The level-2 and level-3 lists now hang under a task item — a shape only live
        // edits can produce, which the loader flattens to sibling lists one level below the item.
        val editor = editorFrom(LIST_ITEM_CHAIN)
        val aAt = editor.text.indexOf('a')
        editor.toListItem(TextRange(aAt, aAt + 1), TextEditorListItem.BulletedList, TextEditorListItem.CheckList)

        val once = editor.toJson()
        val reloaded = editorFrom(once)

        assertEquals(once, reloaded.toJson(), "export is not a fixed point")
        assertTrue(once.contains("\"checked\""), once)
        // No content is lost across the round trip.
        for (t in listOf("a", "b", "c")) {
            assertTrue(reloaded.text.contains(t), "reload lost '$t': ${reloaded.text}")
        }
    }

    @Test
    fun a_list_item_chain_keeps_its_nesting_and_its_indentation() {
        val editor = editorFrom(LIST_ITEM_CHAIN)
        val once = editor.toJson()
        val reloaded = editorFrom(once)

        assertEquals(once, reloaded.toJson(), "export is not a fixed point")
        // Depth is preserved through listItem chains: the reloaded text renders identically.
        assertEquals(editor.text, reloaded.text)
    }
}
