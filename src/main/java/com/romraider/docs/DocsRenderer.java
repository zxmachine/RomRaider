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

import javax.swing.JComponent;

/**
 * Shows documentation pages. Two implementations: a JavaFX WebView
 * (com.romraider.docs.fx, with Mermaid diagrams and SVG figures) and a plain
 * Swing one used when JavaFX is not there. All methods are called on the
 * Swing event thread.
 */
public interface DocsRenderer {

    /** Receives the links clicked in a page, as written there. */
    interface LinkListener {
        void linkClicked(String href);
    }

    JComponent getComponent();

    /** Shows a page, at the heading {@code anchor} (or the top), marking the links to {@code table}. */
    void showPage(PageStore.Page page, String anchor, String table);

    /** On the page already shown, goes to another heading and marks another table. */
    void reveal(String anchor, String table);

    void setLinkListener(LinkListener listener);

    /** True when Mermaid diagrams and SVG figures are drawn. */
    boolean drawsDiagrams();

    /** A line for the user about this renderer (what is missing), or null. */
    String getNotice();
}
