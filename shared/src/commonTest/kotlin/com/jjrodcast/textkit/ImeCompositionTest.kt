package com.jjrodcast.textkit

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jjrodcast.textkit.editor.models.createTextKitConfiguration
import com.jjrodcast.textkit.ui.state.TextKitState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A composing IME (Chinese pinyin, Japanese romaji, …) marks its in-progress text with
 * [TextFieldValue.composition] and expects that region to survive the app's round trip — a value
 * handed back without it cancels the composition, so every keystroke committed immediately and a
 * CJK word could never be composed (#144). The state must keep the region whenever the engine
 * settles on exactly the text the IME produced, and drop it only when an engine rewrite (a marker
 * conversion, a clamp) makes the region meaningless.
 */
class ImeCompositionTest {

    private fun state() = TextKitState("{}", createTextKitConfiguration()).apply { setup() }

    private fun TextKitState.ime(text: String, caret: Int, composition: TextRange?) =
        onTextFieldChange(TextFieldValue(text, TextRange(caret), composition))

    @Test
    fun the_composing_region_survives_each_keystroke() {
        val s = state()
        s.ime("n", 1, TextRange(0, 1))
        assertEquals(TextRange(0, 1), s.textFieldValue.composition)
        s.ime("ni", 2, TextRange(0, 2))
        assertEquals(TextRange(0, 2), s.textFieldValue.composition)
    }

    @Test
    fun committing_the_composition_clears_the_region_and_keeps_the_text() {
        val s = state()
        s.ime("nihao", 5, TextRange(0, 5))
        s.ime("你好", 2, null)
        assertEquals("你好", s.textFieldValue.text)
        assertNull(s.textFieldValue.composition)
    }

    @Test
    fun composing_after_existing_text_keeps_the_region_in_place() {
        val s = state()
        s.ime("hello ", 6, null)
        s.ime("hello w", 7, TextRange(6, 7))
        assertEquals(TextRange(6, 7), s.textFieldValue.composition)
        s.ime("hello wo", 8, TextRange(6, 8))
        assertEquals(TextRange(6, 8), s.textFieldValue.composition)
    }

    @Test
    fun an_engine_rewrite_drops_the_stale_region() {
        val s = state()
        // "1. " converts to a list marker: the engine's settled text is no longer what the IME
        // sent, so the composing region no longer describes the document and must not survive.
        s.ime("1", 1, TextRange(0, 1))
        s.ime("1.", 2, TextRange(0, 2))
        s.ime("1. ", 3, TextRange(0, 3))
        assertNull(s.textFieldValue.composition)
    }

    @Test
    fun a_selection_only_change_keeps_the_region() {
        val s = state()
        s.ime("nihao", 5, TextRange(0, 5))
        s.onTextFieldChange(TextFieldValue("nihao", TextRange(3), TextRange(0, 5)))
        assertEquals(TextRange(0, 5), s.textFieldValue.composition)
    }

    @Test
    fun a_mid_document_commit_replaces_only_the_composed_run() {
        val s = state()
        s.ime("hello ", 6, null)
        s.ime("hello nihao", 11, TextRange(6, 11))
        s.ime("hello 你好", 8, null)
        assertEquals("hello 你好", s.textFieldValue.text)
        assertNull(s.textFieldValue.composition)
        assertEquals(s.toJson(), editorFrom(s.toJson()).toJson())
    }

    @Test
    fun a_composition_only_update_is_not_swallowed() {
        val s = state()
        s.ime("nihao", 5, TextRange(0, 5))
        // same text, same selection — only the composing region changes
        s.ime("nihao", 5, TextRange(2, 5))
        assertEquals(TextRange(2, 5), s.textFieldValue.composition)
    }
}
