package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The explicit import API (#142, phase 2): [TextKitState.importMarkdown] replaces the whole
 * document — the scope the proposal settled on. Like `load`, the swap resets the undo history:
 * the snapshots undo restores are tied to the replaced document.
 */
class ImportMarkdownApiTest {

    private fun state(json: String = "{}") =
        TextKitState(json, createTextKitConfiguration()).apply { setup() }

    @Test
    fun import_replaces_the_document() {
        val s = state("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"old content"}]}]}""")
        s.importMarkdown("# Title\n\n- item one\n- item two")
        assertTrue(!s.textFieldValue.text.contains("old content"))
        assertTrue(s.textFieldValue.text.contains("Title"))
        assertTrue(s.textFieldValue.text.contains("item two"))
        assertEquals(TextRange.Zero, s.textFieldValue.selection)
        assertEquals(s.toJson(), editorFrom(s.toJson()).toJson())
    }

    @Test
    fun import_resets_the_undo_history_like_load() {
        val s = state("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"keep me"}]}]}""")
        s.importMarkdown("replacement\n\n> quoted")
        // A document swap invalidates the piece-table snapshots undo restores, so the history is
        // reset — the same contract as loading a new document.
        assertTrue(!s.canUndo)
        assertTrue(!s.undo())
        assertTrue(s.textFieldValue.text.contains("replacement"))
    }

    @Test
    fun importing_empty_markdown_clears_the_document() {
        val s = state("""{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"gone"}]}]}""")
        s.importMarkdown("")
        assertEquals("", s.textFieldValue.text)
    }

    @Test
    fun the_manager_level_convenience_loads_markdown() {
        val e = com.jjrodcast.textkit.editor.core.TextKitEditorManager()
        e.loadMarkdown("**bold** text\n\n- [x] done")
        assertTrue(e.text.contains("bold"))
        assertTrue(e.toJson().contains("taskItem"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }
}
