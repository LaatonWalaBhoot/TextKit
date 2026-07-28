package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.utils.DocumentUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Characterization tests for **suspected bugs**. Each test pins down the *current* (possibly wrong)
 * behavior so the suite stays green, and documents what the behavior *should* be. When a bug is
 * fixed the matching test here will start failing — that is the signal to update it to assert the
 * corrected behavior.
 */
class PotentialBugsTest {

    // The V3/V6 reserialization-idempotency characterizations moved to
    // [LargeDocumentTest.toJson_is_idempotent_across_a_second_round_trip]: preserving trailing empty
    // paragraphs (issue #61) made every sample's serialization stable.

    /**
     * BUG: Removing an existing link duplicates text / merges the linked piece with its neighbor.
     *
     * Removing only the `link` mark must not change the character stream nor the number of pieces:
     * the linked word sits in its own paragraph in [DocumentUtils.complexJsonV2], so dropping its
     * link leaves a plain piece with no same-paragraph neighbor to merge with.
     */
    @Test
    fun removing_link_keeps_text_and_piece_count() {
        val editor = editorFrom(DocumentUtils.complexJsonV2)
        val range = editor.rangeOf("link")

        val textBefore = editor.text
        val piecesBefore = editor.pieceCount()
        assertTrue(editor.marksAt(range).has<LinkMark>(), "precondition: range must be a link")

        assertTrue(editor.setLink(range, ""), "removing the link should report a change")

        assertFalse(editor.marksAt(range).has<LinkMark>(), "link mark should be gone")
        assertEquals(textBefore, editor.text, "text must be identical after removing the link")
        assertEquals(
            piecesBefore,
            editor.pieceCount(),
            "piece count must be unchanged after removing the link"
        )
    }
}
