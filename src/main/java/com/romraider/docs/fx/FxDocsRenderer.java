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

package com.romraider.docs.fx;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.romraider.docs.DocsRenderer;
import com.romraider.docs.MarkdownPages;
import com.romraider.docs.PageStore;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Shows pages in a JavaFX WebView inside Swing: Markdown, tables, SVG figures
 * and Mermaid diagrams (drawn by the bundled mermaid.min.js, offline).
 * This class is compiled only when the build is given a JavaFX SDK, and is
 * loaded by reflection (see DocsRenderers).
 */
public class FxDocsRenderer implements DocsRenderer {
    private static final String LINK_PREFIX = "rr-link:";
    private final PageStore store;
    private final JFXPanel panel;
    private volatile WebView view;
    private volatile WebEngine engine;
    private volatile LinkListener listener;
    private volatile String pendingUrl;
    private volatile String lastError;

    private void link(final String href) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                LinkListener l = listener;
                if (l != null) l.linkClicked(href);
            }
        });
    }

    public FxDocsRenderer(PageStore store) {
        this.store = store;
        this.panel = new JFXPanel(); // starts the JavaFX runtime
        Platform.setImplicitExit(false);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                WebView v = new WebView();
                WebEngine e = v.getEngine();
                e.setJavaScriptEnabled(true);
                // the page reports clicked links as alerts "rr-link:<href>" (page.js)
                e.setOnAlert(ev -> {
                    String data = ev.getData();
                    if (data != null && data.startsWith(LINK_PREFIX)) {
                        link(data.substring(LINK_PREFIX.length()));
                    }
                });
                e.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                    if (state == Worker.State.SUCCEEDED) {
                        try {
                            e.executeScript("window.rrHost = true;");
                        } catch (RuntimeException ex) {
                            lastError = String.valueOf(ex);
                        }
                    } else if (state == Worker.State.FAILED) {
                        lastError = String.valueOf(e.getLoadWorker().getException());
                    }
                });
                panel.setScene(new Scene(v));
                view = v;
                engine = e;
                String url = pendingUrl;
                pendingUrl = null;
                if (url != null) e.load(url);
            }
        });
    }

    @Override
    public JComponent getComponent() {
        return panel;
    }

    @Override
    public void showPage(PageStore.Page page, String anchor, String table) {
        final String url;
        try {
            File f = store.writeDocument(page, anchor, table, true);
            url = f.toURI().toString();
        } catch (Exception e) {
            lastError = String.valueOf(e);
            return;
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (engine == null) {
                    pendingUrl = url;
                } else {
                    engine.load(url);
                }
            }
        });
    }

    @Override
    public void reveal(String anchor, String table) {
        final String js = "rrShow(" + MarkdownPages.jsString(anchor == null ? "" : anchor) + ", "
                + (table == null ? "null" : MarkdownPages.jsString(table)) + ")";
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (engine != null) {
                    try {
                        engine.executeScript(js);
                    } catch (RuntimeException e) {
                        lastError = String.valueOf(e);
                    }
                }
            }
        });
    }

    @Override
    public void setLinkListener(LinkListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean drawsDiagrams() {
        return true;
    }

    @Override
    public String getNotice() {
        return null;
    }

    /**
     * Runs a script in the page and returns its result as text (for checks and
     * diagnostics). Must not be called on the JavaFX thread.
     */
    public String evaluate(final String script) {
        final String[] result = {null};
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = engine == null ? null : String.valueOf(engine.executeScript(script));
                } catch (RuntimeException e) {
                    result[0] = "error: " + e;
                } finally {
                    done.countDown();
                }
            }
        });
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /** A picture of the page as shown (for checks); null when there is none yet. */
    public java.awt.image.BufferedImage snapshot() {
        final java.awt.image.BufferedImage[] result = {null};
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    if (view != null) {
                        result[0] = javafx.embed.swing.SwingFXUtils.fromFXImage(view.snapshot(null, null), null);
                    }
                } finally {
                    done.countDown();
                }
            }
        });
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    /** The last load or script error, or null. */
    public String getLastError() {
        return lastError;
    }
}
