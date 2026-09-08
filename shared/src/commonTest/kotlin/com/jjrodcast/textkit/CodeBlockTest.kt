package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.export.MarkdownSerializer
import com.jjrodcast.textkit.editor.core.markdown.MarkdownParser
import com.jjrodcast.textkit.editor.core.markdown.markdownToJson
import com.jjrodcast.textkit.editor.core.parser.EmbedBlock
import com.jjrodcast.textkit.editor.core.parser.embedCodeTextOf
import com.jjrodcast.textkit.editor.core.parser.embedLanguageOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `codeBlock` node (#142): an opaque embed like tables and images, so it survives the editor
 * losslessly, plus fenced-block support on both Markdown sides. Inside a fence nothing is parsed —
 * the code is verbatim, blank lines and metacharacters included — and the exported fence always
 * outgrows the longest backtick run in the code so the content can never close it early.
 */
class CodeBlockTest {

    private fun parse(md: String) = MarkdownParser().parse(md)

    @Test
    fun a_fence_imports_verbatim_with_its_language() {
        val doc = parse("```kotlin\nval x = \"**not bold**\"\n\nprintln(x)\n```")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("codeBlock", embed.embedType)
        val payload = embed.raw.toString()
        assertEquals("kotlin", embedLanguageOf(payload))
        assertEquals("val x = \"**not bold**\"\n\nprintln(x)", embedCodeTextOf(payload))
    }

    @Test
    fun an_indented_fence_still_opens_a_code_block() {
        val doc = parse("  ```py\n  indented code\n  ```")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("py", embedLanguageOf(embed.raw.toString()))
        assertEquals("  indented code", embedCodeTextOf(embed.raw.toString()))
    }

    @Test
    fun an_unterminated_fence_runs_to_the_end() {
        val doc = parse("```\nno closer\nstill code")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("no closer\nstill code", embedCodeTextOf(embed.raw.toString()))
    }

    @Test
    fun a_longer_fence_carries_backticks_in_the_code() {
        val doc = parse("````\na ``` inside\n````")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("a ``` inside", embedCodeTextOf(embed.raw.toString()))
    }

    @Test
    fun canonical_fences_are_a_fixed_point_of_parse_then_export() {
        for (md in listOf(
            "```kotlin\nval x = 1\n```",
            "```\nplain\n```",
            "```\n```",
            "````\ncode with ``` run\n````",
            "before\n\n```js\nlet a = 1\n```\n\nafter",
        )) {
            assertEquals(md, MarkdownSerializer().serialize(parse(md)), "fixed point broken for:\n$md")
        }
    }

    @Test
    fun a_code_block_document_loads_as_a_chip_and_round_trips() {
        val e = editorFrom(markdownToJson("intro\n\n```kotlin\nval x = 1\n```"))
        assertTrue(e.text.contains("Código"), e.text)
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
        // the code survives the editor verbatim: export the loaded document back to markdown
        assertTrue(e.toMarkdown().contains("```kotlin\nval x = 1\n```"), e.toMarkdown())
    }

    @Test
    fun the_code_helpers_degrade_on_malformed_payloads() {
        assertNull(embedCodeTextOf("not json"))
        assertNull(embedLanguageOf("{}"))
    }
}
