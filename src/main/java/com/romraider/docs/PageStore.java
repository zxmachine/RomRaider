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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Markdown pages to HTML (cached) and writes the HTML documents the
 * renderers and the system browser load, into a temporary folder together
 * with the bundled mermaid.min.js.
 */
public final class PageStore {
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$", Pattern.MULTILINE);
    private static final int KEEP_DOCUMENTS = 4;

    /** A converted page. */
    public static final class Page {
        private final File markdown;
        private final String title;
        private final String body;

        Page(File markdown, String title, String body) {
            this.markdown = markdown;
            this.title = title;
            this.body = body;
        }

        /** The Markdown file; for a made-up page, a file in the folder links resolve against. */
        public File getMarkdown() {
            return markdown;
        }

        public String getTitle() {
            return title;
        }

        /** The HTML body. */
        public String getBody() {
            return body;
        }
    }

    private MarkdownPages markdown;
    private final Map<String, Page> cache = new HashMap<String, Page>();
    private final Map<String, Long> cacheTimes = new HashMap<String, Long>();
    private final Deque<File> written = new ArrayDeque<File>();
    private File tempDir;
    private File mermaidFile;
    private int counter;

    public PageStore(MarkdownPages markdown) {
        this.markdown = markdown;
    }

    /** Changes the converter (for a new index: its table names) and forgets the converted pages. */
    public synchronized void setMarkdown(MarkdownPages markdown) {
        this.markdown = markdown;
        cache.clear();
        cacheTimes.clear();
    }

    /** Reads and converts a Markdown page, or returns the cached one if the file has not changed. */
    public synchronized Page load(File md) throws IOException {
        String key = md.getAbsolutePath();
        long time = md.lastModified();
        Page p = cache.get(key);
        if (p != null && cacheTimes.get(key) == time) return p;
        String text = new String(Files.readAllBytes(md.toPath()), StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        Matcher m = H1.matcher(text);
        String title = m.find() ? m.group(1).replace("`", "") : md.getName();
        p = new Page(md, title, markdown.toHtml(text));
        cache.put(key, p);
        cacheTimes.put(key, time);
        return p;
    }

    /** A page made up from HTML (a message or a list of pages); links resolve against {@code baseDir}. */
    public Page synthetic(String title, String bodyHtml, File baseDir) {
        return new Page(new File(baseDir, "index.md"), title, bodyHtml);
    }

    /**
     * Writes the whole HTML document for a page and returns it. Each call
     * writes a new file (a browser engine may keep an old one in its cache);
     * only the last few are kept.
     */
    public synchronized File writeDocument(Page page, String anchor, String table, boolean withMermaid)
            throws IOException {
        File dir = tempDir();
        String mermaid = withMermaid ? mermaidUrl() : null;
        String html = MarkdownPages.document(page.getBody(), page.getMarkdown(), page.getTitle(), mermaid, anchor,
                table);
        File f = new File(dir, "page-" + (++counter) + ".html");
        Files.write(f.toPath(), html.getBytes(StandardCharsets.UTF_8));
        f.deleteOnExit();
        written.addLast(f);
        while (written.size() > KEEP_DOCUMENTS) {
            File old = written.removeFirst();
            if (!old.delete()) old.deleteOnExit();
        }
        return f;
    }

    /** The file URL of the bundled mermaid.min.js, copied out of the jar once; null if it is not bundled. */
    public synchronized String mermaidUrl() throws IOException {
        if (mermaidFile == null) {
            InputStream in = MarkdownPages.class.getResourceAsStream("mermaid.min.js");
            if (in == null) return null;
            try {
                File f = new File(tempDir(), "mermaid.min.js");
                Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                f.deleteOnExit();
                mermaidFile = f;
            } finally {
                in.close();
            }
        }
        return mermaidFile.toURI().toString();
    }

    private File tempDir() throws IOException {
        if (tempDir == null) {
            tempDir = Files.createTempDirectory("romraider-docs").toFile();
            tempDir.deleteOnExit();
        }
        return tempDir;
    }
}
