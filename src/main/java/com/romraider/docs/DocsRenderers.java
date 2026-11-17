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

import org.apache.log4j.Logger;

/**
 * Picks the renderer: the JavaFX WebView when the program was built with
 * JavaFX and JavaFX is there at run time, else the Swing one with a notice
 * that says how to get diagrams. The system property
 * {@code romraider.docs.renderer=swing} forces the Swing renderer.
 */
public final class DocsRenderers {
    private static final Logger LOGGER = Logger.getLogger(DocsRenderers.class);
    static final String FX_CLASS = "com.romraider.docs.fx.FxDocsRenderer";

    private DocsRenderers() {
    }

    /** True when JavaFX's Swing bridge and web view are on the class or module path. */
    public static boolean javaFxPresent() {
        try {
            Class.forName("javafx.embed.swing.JFXPanel", false, DocsRenderers.class.getClassLoader());
            Class.forName("javafx.scene.web.WebView", false, DocsRenderers.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when this build has the JavaFX renderer in it. */
    public static boolean builtWithJavaFx() {
        return DocsRenderers.class.getResource("/" + FX_CLASS.replace('.', '/') + ".class") != null;
    }

    /** Makes a renderer; never fails. Call on the Swing event thread. */
    public static DocsRenderer create(PageStore store) {
        if ("swing".equalsIgnoreCase(System.getProperty("romraider.docs.renderer"))) {
            return new SwingDocsRenderer("Simple view chosen (romraider.docs.renderer=swing): diagrams and SVG "
                    + "figures show with \"Open in browser\".");
        }
        if (!builtWithJavaFx()) {
            return new SwingDocsRenderer("This build has no JavaFX view: diagrams and SVG figures show with "
                    + "\"Open in browser\". To draw them here, build with a JavaFX SDK (docs/table_docs.md).");
        }
        if (!javaFxPresent()) {
            return new SwingDocsRenderer("JavaFX is not loaded: diagrams and SVG figures show with \"Open in "
                    + "browser\". To draw them here, start with --module-path <javafx-sdk>/lib "
                    + "--add-modules javafx.swing,javafx.web (docs/table_docs.md).");
        }
        try {
            Class<?> c = Class.forName(FX_CLASS);
            return (DocsRenderer) c.getConstructor(PageStore.class).newInstance(store);
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            LOGGER.warn("JavaFX documentation view failed, using the simple view", cause);
            return new SwingDocsRenderer("JavaFX did not start (" + cause + "): diagrams and SVG figures show "
                    + "with \"Open in browser\".");
        }
    }
}
