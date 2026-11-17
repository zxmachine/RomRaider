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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A link clicked in a documentation page, sorted by what the panel does with
 * it: show another page (or another heading), open a table, or hand it to the
 * system (web links, other files).
 */
public final class DocLink {
    public enum Kind {
        /** A Markdown page, or a heading on the current page: shown in the panel. */
        PAGE,
        /** A table: opened in the editor. */
        TABLE,
        /** A web or mail link: opened in the system browser. */
        EXTERNAL,
        /** Another local file (source, image, ...): opened by the system. */
        FILE,
        /** Nothing usable. */
        NONE
    }

    private final Kind kind;
    private final File page;
    private final String anchor;
    private final String table;
    private final URI uri;

    private DocLink(Kind kind, File page, String anchor, String table, URI uri) {
        this.kind = kind;
        this.page = page;
        this.anchor = anchor == null ? "" : anchor;
        this.table = table;
        this.uri = uri;
    }

    public Kind getKind() {
        return kind;
    }

    /** The Markdown page of a PAGE link, or the file of a FILE link. */
    public File getPage() {
        return page;
    }

    /** The heading id of a PAGE link, or "". */
    public String getAnchor() {
        return anchor;
    }

    /** The table name of a TABLE link. */
    public String getTable() {
        return table;
    }

    /** The URI to hand to the system for EXTERNAL and FILE links. */
    public URI getUri() {
        return uri;
    }

    public DocTarget toTarget() {
        return new DocTarget(page, anchor);
    }

    /**
     * Sorts a link.
     *
     * @param href the link as written in the page (or as resolved by a browser)
     * @param currentPage the Markdown file the link is on; relative links
     *            resolve against its folder
     */
    public static DocLink classify(String href, File currentPage) {
        if (href == null || href.trim().length() == 0) {
            return new DocLink(Kind.NONE, null, null, null, null);
        }
        String h = href.trim();
        String table = MarkdownPages.tableFromHref(h);
        if (table != null) {
            return new DocLink(Kind.TABLE, null, null, table, null);
        }
        if (h.startsWith("#")) {
            return new DocLink(currentPage == null ? Kind.NONE : Kind.PAGE, currentPage, decode(h.substring(1)), null,
                    null);
        }
        String lower = h.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        boolean hasScheme = colon > 1 && lower.substring(0, colon).matches("[a-z][a-z0-9+.-]*");
        if (hasScheme && !lower.startsWith("file:")) {
            try {
                return new DocLink(Kind.EXTERNAL, null, null, null, new URI(h));
            } catch (Exception e) {
                return new DocLink(Kind.NONE, null, null, null, null);
            }
        }

        // a local file: file: URI, absolute or relative path
        String path = h;
        String fragment = "";
        int hash = path.indexOf('#');
        if (hash >= 0) {
            fragment = decode(path.substring(hash + 1));
            path = path.substring(0, hash);
        }
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        File file;
        if (lower.startsWith("file:")) {
            try {
                file = new File(new URI(path));
            } catch (Exception e) {
                return new DocLink(Kind.NONE, null, null, null, null);
            }
        } else {
            String p = decode(path).replace('/', File.separatorChar);
            File f = new File(p);
            if (path.length() == 0) {
                f = currentPage;
            } else if (!f.isAbsolute() && currentPage != null) {
                f = new File(currentPage.getAbsoluteFile().getParentFile(), p);
            }
            file = f;
        }
        if (file == null) return new DocLink(Kind.NONE, null, null, null, null);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            file = file.getAbsoluteFile();
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return new DocLink(Kind.PAGE, file, fragment, null, null);
        }
        return new DocLink(Kind.FILE, file, fragment, null, file.toURI());
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    @Override
    public String toString() {
        switch (kind) {
            case TABLE: return "TABLE " + table;
            case PAGE: return "PAGE " + page + (anchor.length() > 0 ? "#" + anchor : "");
            case EXTERNAL:
            case FILE: return kind + " " + uri;
            default: return "NONE";
        }
    }
}
