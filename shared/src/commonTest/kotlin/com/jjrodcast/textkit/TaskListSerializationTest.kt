package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task list items must export with their `type: "taskItem"` tag, the same as `listItem`/`paragraph`
 * carry theirs — so `toJson()` stays a well-formed ProseMirror document. Regression cover for the bug
 * where a task item serialized with only its `attrs`/`content` and no node type (issue #51).
 */
class TaskListSerializationTest {

    private fun String.count(sub: String) = split(sub).size - 1

    @Test
    fun loaded_task_items_export_with_their_type() {
        val json = editorFrom(SampleDocuments.TASK_LIST).toJson()

        assertEquals(2, json.count("\"type\":\"taskItem\""))
        assertTrue(json.contains("\"type\":\"taskList\""))
    }

    @Test
    fun task_item_checked_state_survives_the_export() {
        val json = editorFrom(SampleDocuments.TASK_LIST).toJson()

        assertEquals(1, json.count("\"checked\":true"))
        assertEquals(1, json.count("\"checked\":false"))
    }

    @Test
    fun a_task_list_round_trips_through_json() {
        val once = editorFrom(SampleDocuments.TASK_LIST).toJson()
        val twice = editorFrom(once).toJson()

        assertEquals(once, twice)
        assertEquals(2, twice.count("\"type\":\"taskItem\""))
    }

    @Test
    fun a_task_list_created_by_toggle_also_tags_its_items() {
        val editor = editorFrom(SampleDocuments.TWO_PARAGRAPHS)

        editor.toListItem(TextRange(0, Int.MAX_VALUE), TextEditorListItem.None, TextEditorListItem.CheckList)

        assertEquals(2, editor.toJson().count("\"type\":\"taskItem\""))
    }
}
