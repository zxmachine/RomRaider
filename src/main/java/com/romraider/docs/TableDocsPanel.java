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

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

/**
 * The documentation panel: shows the Markdown page that explains a table,
 * at the heading that covers it, with the table's name marked. Links between
 * pages stay in the panel, links to tables go to a {@link TableOpener}, other
 * links to the system browser. Back, forward and pin.
 *
 * A plain JComponent: give it an index with {@link #setIndex}, call
 * {@link #showTable} when a table is selected, and set a TableOpener.
 */
public class TableDocsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(TableDocsPanel.class);

    /** Opens a table named in a page. */
    public interface TableOpener {
        /** Opens (or brings forward) the table; returns a message when it cannot, else null. */
        String openTable(String tableName);
    }

    private static final class Place {
        final DocTarget target;
        final String table;

        Place(DocTarget target, String table) {
            this.target = target;
            this.table = table;
        }
    }

    private final PageStore store;
    private final DocsRenderer renderer;
    private final List<Place> history = new ArrayList<Place>();
    private int position = -1;
    private TableDocsIndex index;
    private TableOpener opener;
    private File shownPage;
    private Place shown;
    private List<DocTarget> choices = Collections.emptyList();
    private String choicesTable;
    private boolean updatingChoices;

    private final JButton back = new JButton("◀");
    private final JButton forward = new JButton("▶");
    private final JToggleButton pin = new JToggleButton("Pin");
    private final JButton browser = new JButton("Open in browser");
    private final JButton hide = new JButton("✕");
    private final JLabel title = new JLabel(" ");
    private final JComboBox<String> places = new JComboBox<String>();
    private final JLabel status = new JLabel(" ");
    private final JLabel notice = new JLabel(" ");
    private ActionListener hideListener;

    public TableDocsPanel() {
        super(new BorderLayout());
        this.store = new PageStore(new MarkdownPages(null));
        this.renderer = DocsRenderers.create(store);
        build();
    }

    private void build() {
        back.setToolTipText("Back");
        forward.setToolTipText("Forward");
        pin.setToolTipText("Keep this page while other tables are selected");
        browser.setToolTipText("Open this page in the system browser");
        hide.setToolTipText("Hide the documentation panel");
        places.setToolTipText("Other places that explain this table");
        for (JButton b : new JButton[] {back, forward, browser, hide}) {
            b.setMargin(new java.awt.Insets(1, 4, 1, 4));
            b.setFocusable(false);
        }
        pin.setMargin(new java.awt.Insets(1, 4, 1, 4));
        pin.setFocusable(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        buttons.add(back);
        buttons.add(forward);
        buttons.add(pin);
        buttons.add(browser);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 1));
        right.add(hide);
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(buttons, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        title.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 4));
        places.setVisible(false);
        JPanel top = new JPanel(new BorderLayout());
        top.add(bar, BorderLayout.NORTH);
        JPanel lines = new JPanel(new BorderLayout());
        lines.add(title, BorderLayout.NORTH);
        notice.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 4));
        notice.setForeground(new java.awt.Color(0x80, 0x50, 0x00));
        lines.add(notice, BorderLayout.SOUTH);
        top.add(lines, BorderLayout.CENTER);
        top.add(places, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(renderer.getComponent(), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        add(status, BorderLayout.SOUTH);
        setNotice();
        setMinimumSize(new Dimension(120, 100));

        back.addActionListener(e -> go(position - 1));
        forward.addActionListener(e -> go(position + 1));
        browser.addActionListener(e -> openInBrowser());
        hide.addActionListener(e -> {
            if (hideListener != null) hideListener.actionPerformed(e);
        });
        places.addActionListener(e -> {
            int i = places.getSelectedIndex();
            if (!updatingChoices && i >= 0 && i < choices.size()) {
                navigate(new Place(choices.get(i), choicesTable), true);
            }
        });
        renderer.setLinkListener(href -> followLink(href));
        updateButtons();
    }

    // ------------------------------------------------------------------ API

    /** Uses an index (null: none). Shows its home page, if it has one. */
    public void setIndex(TableDocsIndex index) {
        this.index = index;
        store.setMarkdown(new MarkdownPages(index == null ? null : index.getLinkNames()));
        history.clear();
        position = -1;
        shown = null;
        shownPage = null;
        if (index != null && index.getHome() != null) {
            navigate(new Place(index.getHome(), null), true);
        } else if (index != null) {
            navigate(new Place(null, null), true);
        }
        updateButtons();
    }

    public TableDocsIndex getIndex() {
        return index;
    }

    public void setTableOpener(TableOpener opener) {
        this.opener = opener;
    }

    /** Called when the hide button is pressed. */
    public void setHideListener(ActionListener l) {
        this.hideListener = l;
    }

    public boolean isPinned() {
        return pin.isSelected();
    }

    public void setPinned(boolean pinned) {
        pin.setSelected(pinned);
    }

    public DocsRenderer getRenderer() {
        return renderer;
    }

    /** The page shown, or null. */
    public File getShownPage() {
        return shownPage;
    }

    /**
     * Shows the page for a table (or its category) unless the panel is pinned.
     * When the page shown already explains the table, stays on it and goes to
     * the table's heading there.
     */
    public void showTable(String tableName, String category) {
        if (index == null || isPinned()) return;
        List<DocTarget> targets = index.lookup(tableName, category);
        if (targets.isEmpty()) {
            setChoices(Collections.<DocTarget>emptyList(), null);
            navigate(new Place(null, tableName), true);
            return;
        }
        boolean direct = !index.forTable(tableName).isEmpty();
        String mark = direct ? tableName : null;
        DocTarget target = targets.get(0);
        if (shownPage != null) {
            for (DocTarget t : targets) {
                if (t.getPage().equals(shownPage)) {
                    target = t;
                    break;
                }
            }
        }
        setChoices(targets, mark);
        navigate(new Place(target, mark), true);
        setStatus(direct ? " " : "No section for " + tableName + "; showing its category's page.");
    }

    /** Shows the page for a category unless the panel is pinned. */
    public void showCategory(String category) {
        if (index == null || isPinned()) return;
        DocTarget t = index.forCategory(category);
        if (t != null) {
            setChoices(Collections.<DocTarget>emptyList(), null);
            navigate(new Place(t, null), true);
        }
    }

    /** Shows a place (even when pinned). */
    public void showTarget(DocTarget target, String table) {
        navigate(new Place(target, table), true);
    }

    public void setStatus(String text) {
        status.setText(text == null || text.length() == 0 ? " " : text);
    }

    // ------------------------------------------------------------ internals

    /** Shows a place: a page (target set) or the list of pages (target null, table: the one without a page). */
    private void navigate(Place place, boolean record) {
        if (place.target == null) {
            showContents(place.table);
        } else {
            File page = place.target.getPage();
            try {
                if (page.equals(shownPage)) {
                    renderer.reveal(place.target.getAnchor(), place.table);
                } else {
                    PageStore.Page p = store.load(page);
                    renderer.showPage(p, place.target.getAnchor(), place.table);
                    title.setText(p.getTitle());
                    shownPage = page;
                }
            } catch (IOException e) {
                setStatus("Cannot read " + page + ": " + e.getMessage());
                LOGGER.warn("Cannot read documentation page " + page, e);
                return;
            }
        }
        shown = place;
        if (record && !(position >= 0 && same(history.get(position), place))) {
            while (history.size() > position + 1) history.remove(history.size() - 1);
            history.add(place);
            position = history.size() - 1;
        }
        updateButtons();
    }

    private static boolean same(Place a, Place b) {
        return (a.target == null ? b.target == null : a.target.equals(b.target))
                && (a.table == null ? b.table == null : a.table.equals(b.table));
    }

    private void go(int to) {
        if (to < 0 || to >= history.size()) return;
        position = to;
        navigate(history.get(to), false);
    }

    private void updateButtons() {
        back.setEnabled(position > 0);
        forward.setEnabled(position >= 0 && position < history.size() - 1);
        browser.setEnabled(shown != null && shown.target != null);
    }

    private void setChoices(List<DocTarget> targets, String table) {
        choices = targets;
        choicesTable = table;
        updatingChoices = true;
        try {
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<String>();
            for (DocTarget t : targets) m.addElement(t.getLabel());
            places.setModel(m);
            places.setVisible(targets.size() > 1);
            if (!targets.isEmpty()) places.setSelectedIndex(0);
        } finally {
            updatingChoices = false;
        }
        revalidate();
    }

    /** A made-up page listing the index's pages, with a line about the table that has none. */
    private void showContents(String missingTable) {
        if (index == null) return;
        StringBuilder sb = new StringBuilder();
        if (missingTable != null) {
            sb.append("<p class=\"rr-note\">No page explains <b>").append(MarkdownPages.escapeText(missingTable))
                    .append("</b>.</p>\n");
        }
        sb.append("<h2 id=\"pages\">Pages</h2>\n<ul>\n");
        for (File f : index.getPages()) {
            String t = index.getPageTitle(f);
            if (t.length() == 0) {
                try {
                    t = store.load(f).getTitle();
                } catch (IOException e) {
                    t = f.getName();
                }
            }
            sb.append("<li><a href=\"").append(MarkdownPages.escapeAttr(f.toURI().toString())).append("\">")
                    .append(MarkdownPages.escapeText(t)).append("</a></li>\n");
        }
        sb.append("</ul>\n");
        PageStore.Page p = store.synthetic(missingTable == null ? "Documentation" : missingTable,
                sb.toString(), index.getBaseDir());
        renderer.showPage(p, "", null);
        title.setText(missingTable == null ? "Documentation" : missingTable + ": no page");
        shownPage = null;
        setStatus(missingTable == null ? " " : "No page for " + missingTable + ".");
    }

    private void followLink(String href) {
        DocLink link = DocLink.classify(href, shownPage);
        switch (link.getKind()) {
            case PAGE:
                navigate(new Place(link.toTarget(), null), true);
                break;
            case TABLE:
                if (opener == null) {
                    setStatus("No table editor to open " + link.getTable() + ".");
                } else {
                    String msg = opener.openTable(link.getTable());
                    setStatus(msg == null ? " " : msg);
                }
                break;
            case EXTERNAL:
            case FILE:
                browse(link.getUri());
                break;
            default:
                break;
        }
    }

    private void openInBrowser() {
        if (shown == null || shown.target == null) return;
        try {
            PageStore.Page p = store.load(shown.target.getPage());
            File f = store.writeDocument(p, shown.target.getAnchor(), shown.table, true);
            String anchor = shown.target.getAnchor();
            URI uri = f.toURI();
            if (anchor.length() > 0) uri = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), anchor);
            browse(uri);
        } catch (Exception e) {
            setStatus("Cannot open the browser: " + e.getMessage());
        }
    }

    private void browse(final URI uri) {
        if (uri == null) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            } else {
                setStatus("No system browser: " + uri);
            }
        } catch (Exception e) {
            setStatus("Cannot open " + uri + ": " + e.getMessage());
        }
    }

    private void setNotice() {
        String n = renderer.getNotice();
        notice.setVisible(n != null);
        if (n != null) {
            notice.setText("<html>" + MarkdownPages.escapeText(n) + "</html>");
            notice.setToolTipText(n);
        }
    }
}
