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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlNodeRendererFactory;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;

/**
 * Turns a Markdown page into HTML for the documentation panel: GitHub-style
 * tables and heading ids, Mermaid code blocks as {@code <pre class="mermaid">},
 * and code spans that name a table as links that open the table.
 */
public final class MarkdownPages {
    /** Link scheme for a table: romraider-table:Table%20Name */
    public static final String TABLE_SCHEME = "romraider-table:";
    /** Class of a link to a table. */
    public static final String TABLE_LINK_CLASS = "rr-table";

    private static final List<Extension> EXTENSIONS = Arrays.asList(
            TablesExtension.create(), HeadingAnchorExtension.create(), StrikethroughExtension.create());

    private final Map<String, String> linkNames;
    private final Parser parser;
    private final HtmlRenderer renderer;

    /**
     * @param linkNames names in the page text (exact code span text) and the
     *            table display name each stands for; may be empty
     */
    public MarkdownPages(Map<String, String> linkNames) {
        this.linkNames = linkNames == null ? Collections.<String, String>emptyMap() : linkNames;
        this.parser = Parser.builder().extensions(EXTENSIONS).build();
        this.renderer = HtmlRenderer.builder().extensions(EXTENSIONS)
                .nodeRendererFactory(new HtmlNodeRendererFactory() {
                    @Override
                    public NodeRenderer create(HtmlNodeRendererContext context) {
                        return new FencedBlockRenderer(context);
                    }
                })
                .attributeProviderFactory(c -> new TableLinkAttributes())
                .build();
    }

    /** The HTML body for a Markdown text. */
    public String toHtml(String markdown) {
        Node doc = parser.parse(markdown);
        linkTableNames(doc);
        return renderer.render(doc);
    }

    /** The link to a table. */
    public static String tableHref(String tableName) {
        StringBuilder sb = new StringBuilder(TABLE_SCHEME);
        for (byte b : tableName.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.') {
                sb.append((char) c);
            } else {
                sb.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
            }
        }
        return sb.toString();
    }

    /** The table name in a table link, or null when it is not one. */
    public static String tableFromHref(String href) {
        if (href == null || !href.regionMatches(true, 0, TABLE_SCHEME, 0, TABLE_SCHEME.length())) {
            return null;
        }
        String enc = href.substring(TABLE_SCHEME.length()).replace("+", "%2B");
        return URLDecoder.decode(enc, StandardCharsets.UTF_8);
    }

    /** Wraps code spans that name a table in a link to it. */
    private void linkTableNames(Node doc) {
        if (linkNames.isEmpty()) return;
        final List<Code> found = new ArrayList<Code>();
        doc.accept(new AbstractVisitor() {
            @Override
            public void visit(Code code) {
                if (linkNames.containsKey(code.getLiteral().trim()) && !insideLink(code)) {
                    found.add(code);
                }
            }
        });
        for (Code code : found) {
            String table = linkNames.get(code.getLiteral().trim());
            Link link = new Link(tableHref(table), table);
            code.insertBefore(link);
            code.unlink();
            link.appendChild(code);
        }
    }

    private static boolean insideLink(Node n) {
        for (Node p = n.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Link) return true;
        }
        return false;
    }

    /** Adds the table link class and the table name to links to tables. */
    private static final class TableLinkAttributes implements AttributeProvider {
        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            if (node instanceof Link) {
                String table = tableFromHref(((Link) node).getDestination());
                if (table != null) {
                    attributes.put("class", TABLE_LINK_CLASS);
                    attributes.put("data-table", table);
                }
            }
        }
    }

    /** Mermaid blocks become {@code <pre class="mermaid">}; other blocks as usual. */
    private static final class FencedBlockRenderer implements NodeRenderer {
        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        FencedBlockRenderer(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return new HashSet<Class<? extends Node>>(
                    Collections.<Class<? extends Node>>singletonList(FencedCodeBlock.class));
        }

        @Override
        public void render(Node node) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            String info = block.getInfo() == null ? "" : block.getInfo().trim();
            String language = info.split("\\s+", 2)[0];
            html.line();
            if ("mermaid".equalsIgnoreCase(language)) {
                Map<String, String> attrs = new HashMap<String, String>();
                attrs.put("class", "mermaid");
                html.tag("pre", context.extendAttributes(block, "pre", attrs));
                html.text(block.getLiteral());
                html.tag("/pre");
            } else {
                html.tag("pre", context.extendAttributes(block, "pre", Collections.<String, String>emptyMap()));
                Map<String, String> attrs = new HashMap<String, String>();
                if (language.length() > 0) attrs.put("class", "language-" + language);
                html.tag("code", context.extendAttributes(block, "code", attrs));
                html.text(block.getLiteral());
                html.tag("/code");
                html.tag("/pre");
            }
            html.line();
        }
    }

    // ---------------------------------------------------------------- pages

    /**
     * A whole HTML document for a page.
     *
     * @param body the HTML body from {@link #toHtml}
     * @param pageFile the Markdown file; relative links and images resolve
     *            against its folder
     * @param title the document title
     * @param mermaidUrl URL of mermaid.min.js, or null for no diagrams
     * @param anchor the heading id to show first, or ""
     * @param table the table to mark, or null
     */
    public static String document(String body, File pageFile, String title, String mermaidUrl,
            String anchor, String table) {
        StringBuilder sb = new StringBuilder(body.length() + 8192);
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">\n");
        if (pageFile != null) {
            sb.append("<base href=\"").append(escapeAttr(pageFile.getAbsoluteFile().getParentFile().toURI().toString()))
                    .append("\">\n");
        }
        sb.append("<title>").append(escapeText(title == null ? "" : title)).append("</title>\n");
        sb.append("<style>\n").append(resource("page.css")).append("</style>\n");
        if (mermaidUrl != null) {
            sb.append("<script src=\"").append(escapeAttr(mermaidUrl)).append("\"></script>\n");
        }
        sb.append("</head>\n<body>\n").append(body);
        sb.append("\n<script>\nwindow.rrTarget = {anchor: ").append(jsString(anchor == null ? "" : anchor))
                .append(", table: ").append(table == null ? "null" : jsString(table)).append("};\n");
        sb.append(resource("page.js")).append("</script>\n</body></html>\n");
        return sb.toString();
    }

    /** A JavaScript string literal. */
    public static String jsString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                case '&': sb.append("\\u0026"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    public static String escapeText(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String escapeAttr(String s) {
        return escapeText(s).replace("\"", "&quot;");
    }

    /** A text resource of this package. */
    public static String resource(String name) {
        InputStream in = MarkdownPages.class.getResourceAsStream(name);
        if (in == null) return "";
        try {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "";
        }
    }
}
