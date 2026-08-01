package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.components.TextEditorListItem
import com.jjrodcast.textkit.editor.core.TextKitEditorManager
import com.jjrodcast.textkit.editor.utils.TABS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A decorator is presentation-only and atomic, so a token or an embed inserted while the caret sits
 * on a list item's marker must land at the item's content start. Splicing into the marker splits it
 * around the insertion, leaving the paragraph with a stranded decorator piece mid-line — the same
 * corruption the typing, replace and paste paths already clamp against (#58, #69, #89).
 */
class TokenInsertOnDecoratorTest {

    private val TABLE = """{"type":"table","content":[{"type":"tableRow","content":[]}]}"""

    /** "abc" as a single item of [type]; the caret offset 2 sits inside its decorator. */
    private fun listItemEditor(type: TextEditorListItem): TextKitEditorManager =
        editorFrom("{}").also {
            it.typeText(0, "abc")
            it.toListItem(TextRange(0, 3), TextEditorListItem.None, type)
        }

    private fun TextKitEditorManager.assertNoMidlineDecorator() {
        getParagraphs().forEachIndexed { i, p ->
            assertTrue(
                p.children.drop(1).none { it.decorator != null },
                "paragraph $i carries a mid-line decorator: ${text.replace("\n", "\\n").replace("\t", "\\t")}",
            )
        }
    }

    @Test
    fun a_hashtag_inserted_on_a_decorator_lands_at_the_content_start() {
        val editor = listItemEditor(TextEditorListItem.BulletedList)

        editor.insertToken(nodeType = "hashtag", id = "1", label = "kt", replaceRange = TextRange(2, 2))

        assertEquals("$TABS• @ktabc", editor.text)
        editor.assertNoMidlineDecorator()
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun a_mention_inserted_on_a_decorator_lands_at_the_content_start() {
        val editor = listItemEditor(TextEditorListItem.NumberedList)

        editor.insertMention(id = "1", label = "Jorge", replaceRange = TextRange(2, 2))

        assertEquals("${TABS}1. @Jorgeabc", editor.text)
        editor.assertNoMidlineDecorator()
    }

    @Test
    fun a_token_range_starting_in_the_decorator_only_replaces_the_content_it_covers() {
        val editor = listItemEditor(TextEditorListItem.BulletedList)
        val contentStart = editor.text.indexOf('a')

        // Starts inside the decorator and reaches one character into the content.
        editor.insertToken(
            nodeType = "hashtag",
            id = "1",
            label = "kt",
            replaceRange = TextRange(2, contentStart + 1),
        )

        // Only the covered content character is replaced; the marker survives intact.
        assertEquals("$TABS• @ktbc", editor.text)
        editor.assertNoMidlineDecorator()
    }

    @Test
    fun an_embed_inserted_on_a_decorator_keeps_the_marker_intact() {
        val editor = listItemEditor(TextEditorListItem.BulletedList)

        editor.insertEmbed("table", TABLE, label = "T", at = TextRange(2))

        editor.assertNoMidlineDecorator()
        // The marker stays whole and keeps its line; the embed opens its own paragraph below.
        assertEquals("$TABS• \nT\nabc", editor.text)
        val once = editor.toJson()
        assertEquals(once, editorFrom(once).toJson(), "export is not a fixed point")
    }

    @Test
    fun a_token_in_a_plain_paragraph_is_unaffected() {
        val editor = editorFrom("{}")
        editor.typeText(0, "abc")

        editor.insertToken(nodeType = "hashtag", id = "1", label = "kt", replaceRange = TextRange(1, 2))

        assertEquals("a@ktc", editor.text)
    }
}
