package com.jjrodcast.textkit

import com.jjrodcast.textkit.editor.core.export.HtmlSerializer
import com.jjrodcast.textkit.editor.core.html.HtmlParser
import com.jjrodcast.textkit.editor.core.html.htmlToJson
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
import com.jjrodcast.textkit.editor.core.parser.Mark
import com.jjrodcast.textkit.editor.core.parser.Mention
import com.jjrodcast.textkit.editor.core.parser.OrderedList
import com.jjrodcast.textkit.editor.core.parser.Paragraph
import com.jjrodcast.textkit.editor.core.parser.StrikeMark
import com.jjrodcast.textkit.editor.core.parser.TaskList
import com.jjrodcast.textkit.editor.core.parser.Text
import com.jjrodcast.textkit.editor.core.parser.TextAlign
import com.jjrodcast.textkit.editor.core.parser.TextStyleMark
import com.jjrodcast.textkit.editor.core.parser.UnderlineMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The HTML importer (#44): the exporter's subset inverted, plus real-world synonyms and tag-soup
 * recovery, sanitizing on the way in. The fixed-point tests assert that for exporter-canonical
 * HTML, `export(parse(H)) == H` — every structure the two sides share survives a full round trip.
 */
class HtmlImportTest {

    private fun parse(html: String) = HtmlParser().parse(html)

    private fun firstParagraph(html: String): Paragraph =
        assertIs<Paragraph>(parse(html).content.first())

    // ── Blocks ───────────────────────────────────────────────────────────────

    @Test
    fun block_tags_map_to_their_nodes() {
        val doc = parse("<h2>Title</h2><p>body</p><blockquote><p>quoted</p></blockquote>")
        assertEquals(2, assertIs<Heading>(doc.content[0]).attrs.level)
        assertIs<Paragraph>(doc.content[1])
        assertIs<Paragraph>(assertIs<Blockquote>(doc.content[2]).content.single())
    }

    @Test
    fun lists_map_with_start_and_task_state() {
        val doc = parse(
            "<ul><li>a</li><li>b</li></ul>" +
                "<ol start=\"3\"><li>c</li></ol>" +
                "<ul data-type=\"taskList\">" +
                "<li data-type=\"taskItem\" data-checked=\"true\"><input type=\"checkbox\" checked><p>done</p></li>" +
                "<li data-type=\"taskItem\" data-checked=\"false\"><input type=\"checkbox\"><p>open</p></li>" +
                "</ul>"
        )
        assertEquals(2, assertIs<BulletedList>(doc.content[0]).content.size)
        assertEquals(3, assertIs<OrderedList>(doc.content[1]).attrs.start)
        val tasks = assertIs<TaskList>(doc.content[2])
        assertTrue(tasks.items[0].attrs.checked)
        assertTrue(!tasks.items[1].attrs.checked)
    }

    @Test
    fun unclosed_p_and_li_recover_like_a_browser() {
        val doc = parse("<p>one<p>two<ul><li>a<li>b</ul>")
        assertEquals("one", (assertIs<Paragraph>(doc.content[0]).content.single() as Text).text)
        assertEquals("two", (assertIs<Paragraph>(doc.content[1]).content.single() as Text).text)
        assertEquals(2, assertIs<BulletedList>(doc.content[2]).content.size)
    }

    @Test
    fun loose_inline_content_becomes_a_paragraph() {
        val doc = parse("just <b>text</b> at the root")
        val p = assertIs<Paragraph>(doc.content.single())
        assertEquals(setOf<Mark>(BoldMark()), (p.content[1] as Text).marks)
    }

    @Test
    fun a_table_becomes_the_editor_table_embed() {
        val doc = parse("<table><thead><tr><th>Name</th></tr></thead><tbody><tr><td colspan=\"2\">Juan</td></tr></tbody></table>")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("table", embed.embedType)
        val raw = embed.raw.toString()
        assertTrue(raw.contains("tableHeader") && raw.contains("tableCell"))
        assertTrue(raw.contains("\"colspan\":2"))
    }

    @Test
    fun pre_code_becomes_the_code_embed_with_language() {
        val doc = parse("<pre><code class=\"language-kotlin\">val x = 1\nval y = 2</code></pre>")
        val embed = assertIs<EmbedBlock>(doc.content.single())
        assertEquals("codeBlock", embed.embedType)
        assertTrue(embed.raw.toString().contains("language-kotlin").not())
        assertTrue(embed.raw.toString().contains("\"language\":\"kotlin\""))
        assertTrue(embed.raw.toString().contains("val x = 1\\nval y = 2"))
    }

    @Test
    fun alignment_round_trips_from_the_style_attribute() {
        val p = firstParagraph("<p style=\"text-align:center\">centered</p>")
        assertEquals(TextAlign.Center, p.attrs.textAlign)
    }

    // ── Inline ───────────────────────────────────────────────────────────────

    @Test
    fun mark_tags_and_their_synonyms_map_and_adjacent_runs_merge() {
        // the synonym pairs produce identical marks, so their runs merge seamlessly
        val p = firstParagraph("<p><strong>s</strong><b>b</b><em>e</em><i>i</i><u>u</u><s>st</s><del>d</del><mark>m</mark></p>")
        assertEquals(
            listOf("sb" to BoldMark() as Mark, "ei" to ItalicMark(), "u" to UnderlineMark(), "std" to StrikeMark(), "m" to HighlightMark()),
            p.content.map { (it as Text).text to it.marks.single() },
        )
    }

    @Test
    fun links_spans_and_breaks_map() {
        val p = firstParagraph("<p><a href=\"https://example.com\">go</a><br><span style=\"color:#ff0000;font-size:18px\">red</span></p>")
        assertEquals("https://example.com", ((p.content[0] as Text).marks.single() as LinkMark).attrs.href)
        assertIs<HardBreak>(p.content[1])
        val style = (p.content[2] as Text).marks.single() as TextStyleMark
        assertEquals("#ff0000", style.attrs.color)
        assertEquals(18, style.attrs.fontSize)
    }

    @Test
    fun tokens_keep_their_identity() {
        val p = firstParagraph("<p><span data-type=\"mention\" data-id=\"u1\">@ana</span></p>")
        val mention = assertIs<Mention>(p.content.single())
        assertEquals("u1", mention.attrs.id)
        assertEquals("ana", mention.attrs.label)
    }

    @Test
    fun entities_decode_and_adjacent_runs_merge() {
        val p = firstParagraph("<p>a &amp; b &lt;c&gt; &#233; &#x41;</p>")
        assertEquals("a & b <c> é A", (p.content.single() as Text).text)
    }

    // ── Sanitization ─────────────────────────────────────────────────────────

    @Test
    fun scripts_styles_and_unsafe_schemes_are_neutralized() {
        val doc = parse(
            "<script>alert('x')</script><style>p{}</style>" +
                "<p onclick=\"evil()\"><a href=\"javascript:alert(1)\">still text</a></p>" +
                "<img src=\"javascript:bad()\" alt=\"the alt\">"
        )
        assertEquals(2, doc.content.size)
        val linkless = assertIs<Paragraph>(doc.content[0]).content.single() as Text
        assertEquals("still text", linkless.text)
        assertTrue(linkless.marks.isEmpty(), "unsafe link must drop, keeping the text")
        assertEquals("the alt", (assertIs<Paragraph>(doc.content[1]).content.single() as Text).text)
        assertTrue(!htmlToJson("<script>alert('x')</script>").contains("alert"))
    }

    // ── Integration ──────────────────────────────────────────────────────────

    @Test
    fun the_imported_json_loads_into_the_editor() {
        val e = editorFrom(htmlToJson("<h1>Title</h1><p><b>bold</b> body</p><ul><li>item</li></ul>"))
        assertTrue(e.text.contains("Title"))
        assertTrue(e.text.contains("bold"))
        assertTrue(e.text.contains("item"))
        assertEquals(e.toJson(), editorFrom(e.toJson()).toJson())
    }

    // ── Round trip ───────────────────────────────────────────────────────────

    /** For exporter-canonical HTML, parse → export must be the identity. */
    private fun assertFixedPoint(html: String) {
        assertEquals(html, HtmlSerializer().serialize(parse(html)), "fixed point broken for:\n$html")
    }

    @Test
    fun canonical_html_is_a_fixed_point_of_parse_then_export() {
        assertFixedPoint("<p>plain</p>")
        assertFixedPoint("<h3>Heading</h3>")
        assertFixedPoint("<p style=\"text-align:center\">centered</p>")
        assertFixedPoint("<p><strong>b</strong><em>i</em><u>u</u><s>s</s><mark>m</mark></p>")
        assertFixedPoint("<p><a href=\"https://example.com\">link</a></p>")
        assertFixedPoint("<p><span style=\"color:#ff0000;font-size:18px\">styled</span></p>")
        assertFixedPoint("<ul><li><p>one</p></li><li><p>two</p></li></ul>")
        assertFixedPoint("<ol start=\"3\"><li><p>c</p></li></ol>")
        assertFixedPoint("<ul data-type=\"taskList\"><li data-type=\"taskItem\" data-checked=\"true\"><input type=\"checkbox\" checked><p>do</p></li></ul>")
        assertFixedPoint("<blockquote><p>quoted</p></blockquote>")
        assertFixedPoint("<p>a<br>b</p>")
        assertFixedPoint("<img src=\"https://example.com/a.png\" alt=\"alt\">")
        assertFixedPoint("<p><span data-type=\"mention\" data-id=\"u1\">@ana</span></p>")
        assertFixedPoint("<div data-type=\"document\" data-id=\"doc-1\"></div>")
    }

    @Test
    fun an_exported_document_survives_import_and_re_export() {
        val e = editorFrom(
            """{"type":"doc","content":[
              {"type":"paragraph","content":[{"type":"text","text":"intro "},{"type":"text","text":"bold","marks":[{"type":"bold"}]}]},
              {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","text":"quoted"}]}]},
              {"type":"orderedList","attrs":{"start":1},"content":[
                {"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"first"}]}]}
              ]},
              {"type":"taskList","content":[
                {"type":"taskItem","attrs":{"checked":true},"content":[{"type":"paragraph","content":[{"type":"text","text":"do"}]}]}
              ]}
            ]}"""
        )
        val html = e.toHtml()
        assertEquals(html, HtmlSerializer().serialize(parse(html)))
    }

    /** The importer is a boundary: anything in, no throw, output always loads. */
    @Test
    fun hostile_input_never_throws_and_always_loads() {
        val alphabet = "ab <>/=\"'&;#pulih123!-\n\t"
        for (seed in 0 until 500) {
            val rng = kotlin.random.Random(seed)
            val garbage = buildString { repeat(rng.nextInt(100)) { append(alphabet[rng.nextInt(alphabet.length)]) } }
            val json = try {
                htmlToJson(garbage)
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
