package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
