/*
 * RomRaider Open-Source Tuning, Logging and Reflashing
 * Copyright (C) 2006-2026 RomRaider.com
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.romraider.docs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The table documentation panel's headless parts: index, Markdown to HTML, links. */
public class TableDocsTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String INDEX = "<?xml version=\"1.0\"?>\n"
            + "<tabledocs base=\"pages\" home=\"README.md\">\n"
            + "  <page file=\"a.md\" title=\"Page A\"/>\n"
            + "  <table name=\"Main Map\">\n"
            + "    <doc page=\"a.md\" anchor=\"the-main-map\"/>\n"
            + "    <doc page=\"b.md\" anchor=\"#where-it-is-used\"/>\n"
            + "    <doc page=\"a.md\" anchor=\"the-main-map\"/>\n"
            + "  </table>\n"
            + "  <table name=\"Short Form\" page=\"b.md\" anchor=\"short\"/>\n"
            + "  <category name=\"Timing\" page=\"a.md\"/>\n"
            + "  <alias source=\"tbl_Main_Map\" table=\"Main Map\"/>\n"
            + "</tabledocs>\n";

    private TableDocsIndex index() throws IOException {
        File f = new File(tmp.getRoot(), "defs/table_docs.xml");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), INDEX.getBytes(StandardCharsets.UTF_8));
        return TableDocsIndex.load(f);
    }

    private File pages() {
        return new File(tmp.getRoot(), "defs/pages");
    }

    // ---------------------------------------------------------------- index

    @Test
    public void indexResolvesPagesAgainstBase() throws IOException {
        TableDocsIndex idx = index();
        List<DocTarget> t = idx.forTable("Main Map");
        assertEquals("duplicate doc entries are dropped", 2, t.size());
        assertEquals(new File(pages(), "a.md").getCanonicalFile(), t.get(0).getPage());
        assertEquals("the-main-map", t.get(0).getAnchor());
        assertEquals(new File(pages(), "b.md").getCanonicalFile(), t.get(1).getPage());
        assertEquals("a leading # is dropped", "where-it-is-used", t.get(1).getAnchor());
        assertEquals(new File(pages(), "README.md").getCanonicalFile(), idx.getHome().getPage());
        assertEquals("Page A", idx.getPageTitle(new File(pages(), "a.md").getCanonicalFile()));
    }

    @Test
    public void indexShortFormAndCase() throws IOException {
        TableDocsIndex idx = index();
        assertEquals("short", idx.forTable("Short Form").get(0).getAnchor());
        assertEquals("names match without regard to case", 2, idx.forTable("main map").size());
        assertTrue(idx.forTable("No Such Table").isEmpty());
        assertTrue(idx.forTable(null).isEmpty());
    }

    @Test
    public void lookupFallsBackToCategory() throws IOException {
        TableDocsIndex idx = index();
        List<DocTarget> t = idx.lookup("Undocumented", "Timing");
        assertEquals(1, t.size());
        assertEquals("a.md", t.get(0).getPage().getName());
        assertFalse(t.get(0).hasAnchor());
        assertTrue(idx.lookup("Undocumented", "Other").isEmpty());
        assertEquals(2, idx.lookup("Main Map", "Timing").size());
        assertNotNull(idx.forCategory("timing"));
    }

    @Test
    public void aliases() throws IOException {
        TableDocsIndex idx = index();
        assertEquals("Main Map", idx.tableForName("tbl_Main_Map"));
        assertEquals("Main Map", idx.tableForName("Main Map"));
        assertNull(idx.tableForName("tbl_Other"));
        assertTrue(idx.namesForTable("Main Map").contains("tbl_Main_Map"));
        assertEquals("Main Map", idx.getLinkNames().get("tbl_Main_Map"));
        assertEquals(3, idx.getPages().size()); // a.md, README.md, b.md
    }

    @Test
    public void indexRejectsOtherFiles() {
        try {
            TableDocsIndex.parse(new ByteArrayInputStream("<roms/>".getBytes(StandardCharsets.UTF_8)),
                    new File("x.xml"));
            fail("expected an IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("tabledocs"));
        }
    }

    // --------------------------------------------------------- Markdown/HTML

    @Test
    public void headingIdsLikeGitHub() {
        String html = new MarkdownPages(null).toHtml("# Title\n\n## Steps of the lookup table\n\n"
                + "### Warm-up mode B\n\n### `FLAG_NAME`\n\n## Notes\n\n## Notes\n");
        assertTrue(html, html.contains("<h2 id=\"steps-of-the-lookup-table\">"));
        assertTrue(html, html.contains("<h3 id=\"warm-up-mode-b\">"));
        assertTrue(html, html.contains("<h3 id=\"flag_name\">"));
        assertTrue(html, html.contains("<h2 id=\"notes\">"));
        assertTrue(html, html.contains("<h2 id=\"notes-1\">"));
    }

    @Test
    public void tablesMermaidAndCode() {
        String html = new MarkdownPages(null).toHtml("| a | b |\n| --- | ---: |\n| 1 | 2 |\n\n"
                + "```mermaid\nflowchart TD\n  A --> B\n```\n\n```c\nx = a < b;\n```\n");
        assertTrue(html, html.contains("<table>"));
        assertTrue(html, html.contains("<td align=\"right\">2</td>"));
        assertTrue(html, html.contains("<pre class=\"mermaid\">flowchart TD\n  A --&gt; B\n</pre>"));
        assertTrue(html, html.contains("<pre><code class=\"language-c\">x = a &lt; b;\n</code></pre>"));
    }

    @Test
    public void codeSpansNamingTablesBecomeLinks() throws IOException {
        MarkdownPages md = new MarkdownPages(index().getLinkNames());
        String html = md.toHtml("Uses `tbl_Main_Map` and `Other_Name`; see [`tbl_Main_Map`](a.md).");
        assertTrue(html, html.contains("<a href=\"romraider-table:Main%20Map\" title=\"Main Map\" "
                + "class=\"rr-table\" data-table=\"Main Map\"><code>tbl_Main_Map</code></a>"));
        assertTrue(html, html.contains("<code>Other_Name</code>"));
        assertTrue("a code span already in a link stays as it is",
                html.contains("<a href=\"a.md\"><code>tbl_Main_Map</code></a>"));
    }

    @Test
    public void tableHrefRoundTrip() {
        String name = "A/C Idle (rpm) +5% \u00b0C";
        String href = MarkdownPages.tableHref(name);
        assertTrue(href, href.startsWith("romraider-table:A%2FC%20Idle%20%28rpm%29%20%2B5%25%20"));
        assertEquals(name, MarkdownPages.tableFromHref(href));
        assertNull(MarkdownPages.tableFromHref("other.md"));
    }

    @Test
    public void documentHasBaseScriptAndTarget() {
        File page = new File(tmp.getRoot(), "p/page.md");
        String doc = MarkdownPages.document("<p>x</p>", page, "T<1>", "file:/m/mermaid.min.js", "a\"b",
                "Main </script> Map");
        assertTrue(doc.contains("<base href=\"" + page.getParentFile().toURI()));
        assertTrue(doc.contains("<script src=\"file:/m/mermaid.min.js\"></script>"));
        assertTrue(doc.contains("<title>T&lt;1&gt;</title>"));
        assertTrue(doc, doc.contains("window.rrTarget = {anchor: \"a\\\"b\", table: \"Main \\u003c/script\\u003e Map\"}"));
        assertTrue(doc.contains("rrShow"));
        assertFalse(MarkdownPages.document("", page, "", null, "", null).contains("<script src="));
    }

    @Test
    public void simpleViewReplacesDiagramsAndSvg() {
        String body = "<h2 id=\"x\">X</h2>\n<pre class=\"mermaid\">flowchart TD\n</pre>\n"
                + "<p><img src=\"fig/a.svg\" alt=\"Figure A\" /> <img src=\"b.png\" alt=\"B\" /></p>\n"
                + "<a href=\"romraider-table:M\" title=\"M\" class=\"rr-table\" data-table=\"M\"><code>m</code></a>";
        String s = SwingDocsRenderer.simplify(body, "M");
        assertTrue(s, s.contains("<h2 id=\"x\"><a name=\"x\"></a>X</h2>"));
        assertTrue(s, s.contains("<pre>flowchart TD\n</pre>"));
        assertFalse(s, s.contains("class=\"mermaid\""));
        assertTrue(s, s.contains("<a href=\"fig/a.svg\">[Figure: Figure A]</a>"));
        assertTrue("PNG figures stay", s.contains("<img src=\"b.png\""));
        assertTrue(s, s.contains("<span style=\"background-color: #ffe066\"><code>m</code></span>"));
    }

    // ------------------------------------------------------------------ links

    @Test
    public void linkKinds() throws IOException {
        File page = new File(tmp.getRoot(), "docs/a.md");
        File dir = page.getParentFile().getCanonicalFile();

        DocLink l = DocLink.classify("#the-main-map", page);
        assertEquals(DocLink.Kind.PAGE, l.getKind());
        assertEquals(page, l.getPage());
        assertEquals("the-main-map", l.getAnchor());

        l = DocLink.classify("b.md#where-it-is-used", page);
        assertEquals(DocLink.Kind.PAGE, l.getKind());
        assertEquals(new File(dir, "b.md"), l.getPage());
        assertEquals("where-it-is-used", l.getAnchor());

        l = DocLink.classify("../src/code%20file.s", page);
        assertEquals(DocLink.Kind.FILE, l.getKind());
        assertEquals(new File(dir.getParentFile(), "src/code file.s"), l.getPage());

        l = DocLink.classify("https://example.com/x#y", page);
        assertEquals(DocLink.Kind.EXTERNAL, l.getKind());
        assertEquals("https://example.com/x#y", l.getUri().toString());
        assertEquals(DocLink.Kind.EXTERNAL, DocLink.classify("mailto:someone@example.com", page).getKind());

        l = DocLink.classify("romraider-table:Main%20Map", page);
        assertEquals(DocLink.Kind.TABLE, l.getKind());
        assertEquals("Main Map", l.getTable());

        l = DocLink.classify(new File(dir, "c.md").toURI() + "#top", page);
        assertEquals(DocLink.Kind.PAGE, l.getKind());
        assertEquals(new File(dir, "c.md"), l.getPage());
        assertEquals("top", l.getAnchor());

        assertEquals(DocLink.Kind.FILE, DocLink.classify("fig/a.svg", page).getKind());
        assertEquals(DocLink.Kind.NONE, DocLink.classify("", page).getKind());
        assertEquals(DocLink.Kind.NONE, DocLink.classify("#x", null).getKind());
    }

    // --------------------------------------------------------------- pages

    @Test
    public void pageStoreTitleAndDocuments() throws IOException {
        File md = tmp.newFile("page.md");
        Files.write(md.toPath(), "\uFEFF# The `Title`\n\nText `tbl_Main_Map`.\n".getBytes(StandardCharsets.UTF_8));
        PageStore store = new PageStore(new MarkdownPages(index().getLinkNames()));
        PageStore.Page p = store.load(md);
        assertEquals("The Title", p.getTitle());
        assertTrue(p.getBody().contains("data-table=\"Main Map\""));
        assertTrue("cached", p == store.load(md));
        File doc = store.writeDocument(p, "", null, false);
        String html = new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8);
        assertTrue(html.contains("<h1 id=\"the-title\">"));
    }

    /** The example shipped in docs/table_docs_example parses and every target page exists. */
    @Test
    public void shippedExample() throws IOException {
        File f = new File("docs/table_docs_example/table_docs.xml");
        Assume.assumeTrue(f.isFile());
        TableDocsIndex idx = TableDocsIndex.load(f);
        assertEquals(6, idx.getTableNames().size());
        assertEquals(2, idx.forTable("Base Fuel").size());
        MarkdownPages md = new MarkdownPages(idx.getLinkNames());
        for (File page : idx.getPages()) {
            assertTrue(page + " exists", page.isFile());
            String html = md.toHtml(new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8));
            for (String table : idx.getTableNames()) {
                for (DocTarget t : idx.forTable(table)) {
                    if (t.getPage().equals(page) && t.hasAnchor()) {
                        assertTrue(table + ": " + t.getAnchor() + " on " + page.getName(),
                                html.contains("id=\"" + t.getAnchor() + "\""));
                    }
                }
            }
        }
    }
}
