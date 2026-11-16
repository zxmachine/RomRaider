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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Shows pages in a Swing JEditorPane: text, tables, links and PNG/JPEG
 * figures. Mermaid diagrams are shown as their source and SVG figures as
 * links; "Open in browser" shows them in full.
 */
public class SwingDocsRenderer implements DocsRenderer {
    private static final Pattern MERMAID = Pattern.compile("<pre class=\"mermaid\">(.*?)</pre>", Pattern.DOTALL);
    private static final Pattern SVG_IMG = Pattern.compile(
            "<img src=\"([^\"]+\\.svg)\"(?: alt=\"([^\"]*)\")?[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("<h([1-6]) id=\"([^\"]*)\">");
    private static final String CSS = "body { font-family: SansSerif; font-size: 11pt; margin: 6px; }"
            + " code, pre { font-family: Monospaced; font-size: 10pt; }"
            + " pre { background-color: #f6f8fa; }"
            + " table { border-collapse: collapse; } th, td { border: 1px solid #d0d7de; padding: 2px; }"
            + " th { background-color: #f6f8fa; } a { color: #0969da; }"
            + " .note { color: #57606a; font-style: italic; }";

    private final JEditorPane pane = new JEditorPane();
    private final JScrollPane scroll = new JScrollPane(pane);
    private final String notice;
    private LinkListener listener;
    private PageStore.Page page;

    public SwingDocsRenderer(String notice) {
        this.notice = notice;
        pane.setEditable(false);
        pane.setEditorKit(new HTMLEditorKit());
        pane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && listener != null) {
                    listener.linkClicked(e.getDescription());
                }
            }
        });
    }

    @Override
    public JComponent getComponent() {
        return scroll;
    }

    @Override
    public void showPage(PageStore.Page page, String anchor, String table) {
        this.page = page;
        render(anchor, table);
    }

    @Override
    public void reveal(String anchor, String table) {
        if (page != null) render(anchor, table);
    }

    private void render(final String anchor, String table) {
        String base = page.getMarkdown().getAbsoluteFile().getParentFile().toURI().toString();
        String html = "<html><head><base href=\"" + MarkdownPages.escapeAttr(base) + "\"><style>" + CSS
                + "</style></head><body>" + simplify(page.getBody(), table) + "</body></html>";
        pane.setText(html);
        pane.setCaretPosition(0);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (anchor != null && anchor.length() > 0) {
                    pane.scrollToReference(anchor);
                } else {
                    pane.scrollRectToVisible(new java.awt.Rectangle(0, 0, 1, 1));
                }
            }
        });
    }

    /** The page body as HTML a JEditorPane shows: no diagrams, named anchors, marked table links. */
    static String simplify(String body, String table) {
        Matcher m = MERMAID.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<p class=\"note\">Diagram (drawn with JavaFX or in the browser):</p><pre>" + m.group(1)
                            + "</pre>"));
        }
        m.appendTail(sb);
        String s = sb.toString();

        m = SVG_IMG.matcher(s);
        sb = new StringBuffer();
        while (m.find()) {
            String alt = m.group(2) == null || m.group(2).length() == 0 ? m.group(1) : m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<a href=\"" + m.group(1) + "\">[Figure: " + alt + "]</a>"));
        }
        m.appendTail(sb);
        s = sb.toString();

        s = HEADING.matcher(s).replaceAll("<h$1 id=\"$2\"><a name=\"$2\"></a>");

        if (table != null) {
            String attr = "data-table=\"" + MarkdownPages.escapeAttr(table) + "\"";
            Pattern link = Pattern.compile("(<a [^>]*" + Pattern.quote(attr) + "[^>]*>)(.*?)(</a>)", Pattern.DOTALL);
            m = link.matcher(s);
            sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1)
                        + "<span style=\"background-color: #ffe066\">" + m.group(2) + "</span>" + m.group(3)));
            }
            m.appendTail(sb);
            s = sb.toString();
        }
        return s;
    }

    @Override
    public void setLinkListener(LinkListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean drawsDiagrams() {
        return false;
    }

    @Override
    public String getNotice() {
        return notice;
    }
}
