package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.export.MarkdownSerializer
import com.jjrodcast.textkit.editor.core.markdown.MarkdownParser
import com.jjrodcast.textkit.editor.core.markdown.markdownToJson
import com.jjrodcast.textkit.editor.core.parser.Blockquote
import com.jjrodcast.textkit.editor.core.parser.BoldMark
import com.jjrodcast.textkit.editor.core.parser.BulletedList
import com.jjrodcast.textkit.editor.core.parser.EmbedBlock
import com.jjrodcast.textkit.editor.core.parser.HardBreak
import com.jjrodcast.textkit.editor.core.parser.Heading
import com.jjrodcast.textkit.editor.core.parser.HighlightMark
import com.jjrodcast.textkit.editor.core.parser.ItalicMark
import com.jjrodcast.textkit.editor.core.parser.LinkMark
import com.jjrodcast.textkit.editor.core.parser.ListItem
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Markdown importer (#142, phase 1): GFM subset → document model, the inverse of
 * [MarkdownSerializer]. The per-construct tests pin the mapping; the fixed-point tests assert
 * that for exporter-canonical Markdown, `export(parse(M)) == M` — every structure the two sides
 * share survives a full round trip.
 */
class MarkdownImportTest {

    private fun parse(md: String) = MarkdownParser().parse(md)

    private fun firstParagraph(md: String): Paragraph {
        val doc = parse(md)
        return assertIs<Paragraph>(doc.content.first())
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun headings_map_their_level() {
        val doc = parse("# One\n\n###### Six")
        assertEquals(1, assertIs<Heading>(doc.content[0]).attrs.level)
        assertEquals(6, assertIs<Heading>(doc.content[1]).attrs.level)
        assertEquals("One", (assertIs<Heading>(doc.content[0]).content.single() as Text).text)
    }

    @Test
    fun paragraph_lines_join_with_a_space_and_br_hard_breaks() {
        val p = firstParagraph("first line\nsecond line")
        assertEquals("first line second line", (p.content.single() as Text).text)

        val withBreak = firstParagraph("above<br>below")
        assertEquals(3, withBreak.content.size)
        assertIs<HardBreak>(withBreak.content[1])
    }

    @Test
    fun blockquote_groups_consecutive_lines_and_keeps_inner_blocks() {
        val doc = parse("> quoted one\n>\n> # Quoted heading")
        val quote = assertIs<Blockquote>(doc.content.single())
        assertIs<Paragraph>(quote.content[0])
        assertEquals(2, quote.content.size)
        assertIs<Heading>(quote.content[1])
    }

    @Test
    fun the_three_list_kinds_parse_with_their_attributes() {
        val doc = parse("- a\n- b\n\n3. c\n4. d\n\n- [x] done\n- [ ] open")
        val bullets = assertIs<BulletedList>(doc.content[0])
        assertEquals(2, bullets.content.size)
        val ordered = assertIs<OrderedList>(doc.content[1])
        assertEquals(3, ordered.attrs.start)
        val tasks = assertIs<TaskList>(doc.content[2])
        assertTrue(tasks.items[0].attrs.checked)
        assertTrue(!tasks.items[1].attrs.checked)
    }

    @Test
    fun a_tight_sublist_nests_inside_its_item() {
        val doc = parse("- parent\n    - child")
        val list = assertIs<BulletedList>(doc.content.single())
        val item = assertIs<ListItem>(list.content.single())
        assertIs<Paragraph>(item.content[0])
        val nested = assertIs<BulletedList>(item.content[1])
        assertEquals("child", (assertIs<Paragraph>(assertIs<ListItem>(nested.content.single()).content[0]).content.single() as Text).text)
    }

    @Test
    fun a_kind_switch_starts_a_new_list() {
        val doc = parse("- bullet\n1. number")
        assertIs<BulletedList>(doc.content[0])
        assertIs<OrderedList>(doc.content[1])
    }

    @Test
    fun a_table_becomes_the_editor_table_embed() {
        val doc = parse("| Name | Age |\n| --- | --- |\n| Juan | 5 |")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("table", embed.embedType)
        val raw = embed.raw.toString()
        assertTrue(raw.contains("tableHeader") && raw.contains("tableCell"))
        assertTrue(raw.contains("Name") && raw.contains("Juan"))
    }

    @Test
    fun a_lone_image_line_becomes_the_image_embed() {
        val doc = parse("![the alt](https://example.com/a.png)")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("image", embed.embedType)
        assertTrue(embed.raw.toString().contains("https://example.com/a.png"))
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    @Test
    fun emphasis_marks_nest_and_combine() {
        val p = firstParagraph("**bold _both_** and ~~gone~~")
        assertEquals(setOf<Any>(BoldMark()), (p.content[0] as Text).marks)
        assertEquals(setOf(BoldMark(), ItalicMark()), (p.content[1] as Text).marks)
        assertEquals(setOf<Any>(StrikeMark()), (p.content[3] as Text).marks)
    }

    @Test
    fun snake_case_stays_literal() {
        val p = firstParagraph("value of snake_case_name here")
        assertEquals("value of snake_case_name here", (p.content.single() as Text).text)
    }

    @Test
    fun links_carry_their_destination() {
        val p = firstParagraph("see [the docs](https://example.com/x) now")
        val linked = p.content[1] as Text
        assertEquals("the docs", linked.text)
        assertEquals("https://example.com/x", (linked.marks.single() as LinkMark).attrs.href)
    }

    @Test
    fun the_html_fallbacks_restore_their_marks() {
        val p = firstParagraph("<u>under</u> and <mark>lit</mark>")
        assertEquals(setOf<Any>(UnderlineMark()), (p.content[0] as Text).marks)
        assertEquals(setOf<Any>(HighlightMark()), (p.content[2] as Text).marks)
    }

    @Test
    fun escapes_entities_and_degrades_keep_the_text() {
        assertEquals("*not bold* & <kept>", (firstParagraph("""\*not bold\* &amp; &lt;kept&gt;""").content.single() as Text).text)
        // a code span keeps its inner text; an unknown tag is stripped around its content
        assertEquals("code here", (firstParagraph("`code` <span style=\"x\">here</span>").content.single() as Text).text)
        // an inline image degrades to its alt text
        assertEquals("before alt after", (firstParagraph("before ![alt](u) after").content.joinToString("") { (it as Text).text }))
    }

    // ── Integration ──────────────────────────────────────────────────────────

    @Test
    fun the_imported_json_loads_into_the_editor() {
        val e = editorFrom(markdownToJson("# Title\n\nplain **bold**\n\n- item\n\n> quoted"))
        assertTrue(e.text.contains("Title"))
        assertTrue(e.text.contains("bold"))
        assertTrue(e.text.contains("item"))
        assertTrue(e.text.contains("quoted"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    // ── Round trip ───────────────────────────────────────────────────────────

    /** For exporter-canonical Markdown, parse → export must be the identity. */
    private fun assertFixedPoint(markdown: String) {
        assertEquals(markdown, MarkdownSerializer().serialize(parse(markdown)), "fixed point broken for:\n$markdown")
    }

    @Test
    fun canonical_markdown_is_a_fixed_point_of_parse_then_export() {
        assertFixedPoint("plain paragraph")
        assertFixedPoint("# Heading")
        assertFixedPoint("**bold** and _italic_ and ~~strike~~")
        assertFixedPoint("a [link](https://example.com/x) here")
        assertFixedPoint("<u>under</u> and <mark>lit</mark>")
        assertFixedPoint("- one\n- two")
        assertFixedPoint("1. one\n2. two")
        assertFixedPoint("- [ ] open\n- [x] done")
        assertFixedPoint("- parent\n    - child")
        assertFixedPoint("> quoted line")
        assertFixedPoint("first\n\nsecond")
        assertFixedPoint("![alt](https://example.com/a.png)")
        assertFixedPoint("| a | b |\n| --- | --- |\n| c | d |")
    }

    @Test
    fun an_exported_document_survives_import_and_re_export() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"intro "},{"type":"text","text":"bold","marks":[{"type":"bold"}]}]},
              {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quoted"}]}]},
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"first"}]}]},
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"second"}]}]}
              ]},
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"do"}]}]}
              ]}
            ]}"""
        )
        val markdown = e.toMarkdown()
        assertEquals(markdown, MarkdownSerializer().serialize(parse(markdown)))
    }

    /**
     * The importer is a boundary: it must accept anything without throwing, and its output must
     * always load. Seeded, so a failure names its seed.
     */
    @Test
    fun hostile_input_never_throws_and_always_loads() {
        val alphabet = "ab \n\t*_~`[]()|>#-!<>&\\\"'x1."
        for (seed in 0 until 500) {
            val rng = kotlin.random.Random(seed)
            val garbage = buildString { repeat(rng.nextInt(80)) { append(alphabet[rng.nextInt(alphabet.length)]) } }
            val json = try {
                markdownToJson(garbage)
            } catch (t: Throwable) {
                throw AssertionError("parser threw at seed=$seed input='${garbage.replace("\n", "\\n")}'", t)
            }
            try {
                editorFrom(json)
            } catch (t: Throwable) {
                throw AssertionError("import output failed to load at seed=$seed input='${garbage.replace("\n", "\\n")}'", t)
            }
        }
    }
}
