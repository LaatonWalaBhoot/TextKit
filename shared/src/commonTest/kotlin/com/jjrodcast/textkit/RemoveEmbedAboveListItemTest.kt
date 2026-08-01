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
        // Both placeholder lines become task items, so the first one's trailing break is the only
        // thing separating the two markers.
        editor.toListItem(TextRange(1, 3), TextEditorListItem.None, TextEditorListItem.CheckList)
        assertEquals("$TABS-[] T\n$TABS-[] T", editor.text)

        val embed = editor.embedAt(editor.text.indexOf('T', startIndex = 1) + 1)
            ?: editor.embedAt(editor.text.indexOf('T'))
        editor.removeEmbedAt(embed!!.range)

        editor.assertNoMidlineDecorator()
        assertEquals("$TABS-[] T", editor.text)
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
