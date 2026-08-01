package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.utils.TABS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An embed placeholder carries the line break that ends its own paragraph. Removing it must go
 * through the text-removal path, which resolves decorators for the window it deletes: dropping the
 * break outright merges the following paragraph into this one, so a list item below the embed loses
 * its own line and its marker ends up stranded mid-line.
 */
class RemoveEmbedAboveListItemTest {

    private val TABLE = """{"type":"table","content":[{"type":"tableRow","content":[]}]}"""

    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}",
            )
        }
    }

    @Test
    fun removing_an_embed_above_a_list_item_keeps_the_item_on_its_own_line() {
        val editor = editorFrom("{}")
        editor.insertEmbed("table", TABLE, label = "T", at = TextRange(0))
        editor.insertEmbed("table", TABLE, label = "T", at = TextRange(0))
        // Both placeholder lines become list items, so the first one's trailing break is the only
        // thing separating the two markers. Marker text is platform-specific, so this asserts on
        // the paragraph structure rather than on the rendered decorator.
        editor.toListItem(TextRange(1, 3), TextEditorListItem.None, TextEditorListItem.BulletedList)
        assertEquals(2, editor.getParagraphs().size, editor.text.replace("\n", "\\n").replace("\t", "\\t"))
        assertTrue(editor.getParagraphs().all { it.children.first().decorator != null }, editor.text)

        val embed = (0 until editor.text.length).firstNotNullOfOrNull { editor.embedAt(it) }
        editor.removeEmbedAt(embed!!.range)

        editor.assertNoMidlineDecorator()
        // One item is left, still carrying exactly one marker of its own.
        val paragraphs = editor.getParagraphs()
        assertEquals(1, paragraphs.size, editor.text.replace("\n", "\\n").replace("\t", "\\t"))
        assertEquals(1, paragraphs.single().children.count { it.decorator != null }, editor.text)
        assertTrue(editor.text.endsWith("T"), editor.text.replace("\t", "\\t"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun removing_a_standalone_embed_still_leaves_the_neighbours_alone() {
        val editor = editorFrom("{}")
        editor.typeText(0, "x\nabc")
        val second = editor.text.indexOf('\n') + 1
        editor.toListItem(TextRange(second, second + 3), TextEditorListItem.None, TextEditorListItem.BulletedList)
        editor.insertEmbed("table", TABLE, label = "T", at = TextRange(1))

        val embed = (0 until editor.text.length).firstNotNullOfOrNull { editor.embedAt(it) }
        editor.removeEmbedAt(embed!!.range)

        editor.assertNoMidlineDecorator()
        assertTrue(editor.text.contains("$TABS• abc"), editor.text.replace("\t", "\\t"))
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }
}
