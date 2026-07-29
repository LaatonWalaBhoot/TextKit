package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The export never emits a list node with zero items — a list must hold at least one item, and a
 * reload drops an empty one, breaking the export's fixed point. Regression cover for issue #79.
 */
class EmptyNestedListExportTest {

    private val NESTED = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"paragraph","content":[{"type":"text","text":"e"}]},
          {"type":"taskList","content":[
            {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[]}]},
            {"type":"taskItem","attrs":{"checked":false},"content":[{"type":"paragraph","content":[{"type":"text","text":"b"}]}]}
          ]}
        ]}
      ]}
    ]}"""

    private val emptyListNode = Regex("\"content\":\\[\\],\"type\":\"(taskList|bulletList|orderedList)\"")

    @Test
    fun removing_a_nested_task_item_does_not_export_an_empty_list_node() {
        val editor = editorFrom(NESTED)

        // Collapsed unList at the first nested task item's line start.
        editor.toListItem(TextRange(6, 6), TextEditorListItem.CheckList, TextEditorListItem.None)

        val json = editor.toJson()
        assertFalse(emptyListNode.containsMatchIn(json), "empty list node in export: $json")
        assertEquals(json, editorFrom(json).toJson(), "export is not a fixed point")
    }

    @Test
    fun the_nested_document_round_trips_before_any_edit() {
        val once = editorFrom(NESTED).toJson()
        assertFalse(emptyListNode.containsMatchIn(once), once)
        assertEquals(once, editorFrom(once).toJson())
    }

    /**
     * A task group rebuilt as a nested sibling (mixed nested lists under one item) must emit
     * `taskItem` children — not `listItem` — so the checked state survives and a reload keeps the
     * branch. The item's own nested list makes the shared list-item builder the wrong shape here.
     */
    private val MIXED_NESTED_SIBLINGS = """{"type":"doc","content":[
      {"type":"bulletList","content":[
        {"type":"listItem","content":[
          {"type":"paragraph","content":[{"type":"text","text":"a"}]},
          {"type":"bulletList","content":[
            {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"x"}]}]}
          ]},
          {"type":"taskList","content":[
            {"type":"taskItem","attrs":{"checked":true},"content":[
              {"type":"paragraph","content":[{"type":"text","text":"b"}]},
              {"type":"bulletList","content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"c"}]}]}
              ]}
            ]}
          ]}
        ]}
      ]}
    ]}"""

    @Test
    fun a_nested_task_group_keeps_taskItem_shape_and_checked_state() {
        val once = editorFrom(MIXED_NESTED_SIBLINGS).toJson()

        assertTrue(once.contains("\"checked\":true"), "checked state lost: $once")

        val reloaded = editorFrom(once)
        assertTrue(reloaded.text.contains("b") && reloaded.text.contains("c"), "reload dropped the task branch")
        assertEquals(once, reloaded.toJson(), "export is not a fixed point")
    }
}
