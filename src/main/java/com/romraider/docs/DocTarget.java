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
import java.util.Objects;

/**
 * A place in the documentation: a Markdown page and, optionally, the id of a
 * heading on it.
 */
public final class DocTarget {
    private final File page;
    private final String anchor;

    public DocTarget(File page, String anchor) {
        this.page = page;
        this.anchor = anchor == null ? "" : anchor;
    }

    /** The Markdown file. */
    public File getPage() {
        return page;
    }

    /** The heading id on the page, or "" for the top of the page. */
    public String getAnchor() {
        return anchor;
    }

    public boolean hasAnchor() {
        return anchor.length() > 0;
    }

    /** A short label such as "ignition.md # base-timing". */
    public String getLabel() {
        return page.getName() + (hasAnchor() ? " # " + anchor : "");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocTarget)) return false;
        DocTarget t = (DocTarget) o;
        return page.getAbsoluteFile().equals(t.page.getAbsoluteFile()) && anchor.equals(t.anchor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page.getAbsoluteFile(), anchor);
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
