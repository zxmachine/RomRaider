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

import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.io.File;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditor;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.maps.Rom;
import com.romraider.maps.Table;
import com.romraider.swing.CategoryTreeNode;
import com.romraider.swing.RomTree;
import com.romraider.swing.TableFrame;
import com.romraider.swing.TableTreeNode;
import com.romraider.util.SettingsManager;

/**
 * Puts the {@link TableDocsPanel} into the editor window, to the right of
 * the table desktop, and keeps it on the selected table. Also the "Table
 * documentation" menu. Without an index file in the settings, nothing but the
 * menu is added.
 */
public final class EditorDocs {
    private static final Logger LOGGER = Logger.getLogger(EditorDocs.class);
    private static EditorDocs instance;

    private final ECUEditor editor;
    private TableDocsPanel panel;
    private JSplitPane split;
    private boolean listening;
    private boolean settingDivider;
    private boolean dividerPlaced;

    private EditorDocs(ECUEditor editor) {
        this.editor = editor;
    }

    /**
     * Adds the "Table documentation" submenu to a menu of the editor and,
     * once the editor window is laid out, the panel (when an index is set).
     */
    public static void addMenu(JMenu menu) {
        final JMenu docs = new JMenu("Table documentation");
        final JCheckBoxMenuItem show = new JCheckBoxMenuItem("Show beside the tables");
        final JMenuItem choose = new JMenuItem("Choose index file...");
        final JMenuItem stop = new JMenuItem("Stop using the index");
        docs.add(show);
        docs.add(choose);
        docs.add(stop);
        menu.add(docs);
        docs.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                Settings s = SettingsManager.getSettings();
                boolean has = s.getTableDocsIndex().length() > 0;
                show.setEnabled(has);
                show.setSelected(has && s.isTableDocsVisible());
                stop.setEnabled(has);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        show.addActionListener(e -> {
            EditorDocs d = get();
            if (d != null) d.setVisible(show.isSelected());
        });
        choose.addActionListener(e -> {
            EditorDocs d = get();
            if (d != null) d.chooseIndex();
        });
        stop.addActionListener(e -> {
            EditorDocs d = get();
            if (d != null) d.stopIndex();
        });
        SwingUtilities.invokeLater(() -> {
            EditorDocs d = get();
            if (d != null) d.start();
        });
    }

    /** The binding for the open editor, or null when there is none. */
    public static synchronized EditorDocs get() {
        ECUEditor e = ECUEditorManager.getECUEditorWithoutCreation();
        if (e == null) return null;
        if (instance == null || instance.editor != e) instance = new EditorDocs(e);
        return instance;
    }

    /** The panel, or null when none is shown. */
    public TableDocsPanel getPanel() {
        return panel;
    }

    private void start() {
        listen();
        Settings s = SettingsManager.getSettings();
        if (s.getTableDocsIndex().length() > 0 && s.isTableDocsVisible()) {
            setVisible(true);
        }
    }

    // ------------------------------------------------------------- the panel

    /** Shows or hides the panel (remembered in the settings). */
    public void setVisible(boolean visible) {
        Settings s = SettingsManager.getSettings();
        s.setTableDocsVisible(visible);
        if (visible) {
            if (s.getTableDocsIndex().length() == 0) return;
            if (panel == null) {
                panel = new TableDocsPanel();
                panel.setTableOpener(name -> openTable(name));
                panel.setHideListener(e -> setVisible(false));
                if (!loadIndex(new File(s.getTableDocsIndex()))) return;
            }
            insert();
            showSelection();
        } else {
            remove();
        }
    }

    private boolean loadIndex(File f) {
        try {
            panel.setIndex(TableDocsIndex.load(f));
            return true;
        } catch (Exception ex) {
            LOGGER.warn("Cannot read the table documentation index " + f, ex);
            panel.setStatus("Cannot read " + f + ": " + ex.getMessage());
            return true; // the panel says what is wrong
        }
    }

    private void insert() {
        if (split != null) return;
        JScrollPane desktop = editor.getRightScrollPane();
        Container parent = desktop.getParent();
        if (!(parent instanceof JSplitPane)) {
            LOGGER.warn("Table documentation: the table desktop is not in a split pane; panel not added");
            return;
        }
        final JSplitPane outer = (JSplitPane) parent;
        int outerLocation = outer.getDividerLocation();
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, desktop, panel);
        split.setResizeWeight(1.0);
        split.setDividerSize(5);
        split.setContinuousLayout(true);
        split.setBorder(null);
        outer.setRightComponent(split);
        outer.setDividerLocation(outerLocation);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (dividerPlaced && !settingDivider && split != null && split.getWidth() > 0) {
                int width = split.getWidth() - split.getDividerLocation() - split.getDividerSize();
                if (width > 40) SettingsManager.getSettings().setTableDocsWidth(width);
            }
        });
        outer.revalidate();
        SwingUtilities.invokeLater(() -> placeDivider());
    }

    private void placeDivider() {
        if (split == null) return;
        if (split.getWidth() <= 0) {
            SwingUtilities.invokeLater(() -> placeDivider());
            return;
        }
        int width = Math.max(150, SettingsManager.getSettings().getTableDocsWidth());
        settingDivider = true;
        try {
            split.setDividerLocation(Math.max(100, split.getWidth() - width - split.getDividerSize()));
            dividerPlaced = true;
        } finally {
            settingDivider = false;
        }
    }

    private void remove() {
        if (split == null) return;
        JScrollPane desktop = editor.getRightScrollPane();
        Container parent = split.getParent();
        if (parent instanceof JSplitPane) {
            JSplitPane outer = (JSplitPane) parent;
            int outerLocation = outer.getDividerLocation();
            split.remove(desktop);
            outer.setRightComponent(desktop);
            outer.setDividerLocation(outerLocation);
            outer.revalidate();
        }
        split = null;
        dividerPlaced = false;
    }

    private void chooseIndex() {
        Settings s = SettingsManager.getSettings();
        String current = s.getTableDocsIndex();
        JFileChooser fc = new JFileChooser(current.length() > 0 ? new File(current).getParentFile()
                : s.getLastDefinitionDir());
        fc.setDialogTitle("Choose the table documentation index");
        fc.setFileFilter(new FileNameExtensionFilter("Table documentation index (.xml)", "xml"));
        if (fc.showOpenDialog(editor) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            TableDocsIndex index = TableDocsIndex.load(f);
            s.setTableDocsIndex(f.getAbsolutePath());
            if (panel == null) {
                setVisible(true);
            } else {
                panel.setIndex(index);
                setVisible(true);
            }
        } catch (Exception ex) {
            showMessageDialog(editor, ex.getMessage(), "Table documentation", ERROR_MESSAGE);
        }
    }

    private void stopIndex() {
        SettingsManager.getSettings().setTableDocsIndex("");
        remove();
        panel = null;
    }

    // -------------------------------------------------------- the selection

    private void listen() {
        if (listening) return;
        listening = true;
        editor.getImageList().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                TreePath p = e.getNewLeadSelectionPath();
                if (p != null) showNode(p.getLastPathComponent());
            }
        });
        Container desktop = editor.getRightPanel();
        for (JInternalFrame f : editor.getRightPanel().getAllFrames()) watch(f);
        desktop.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent e) {
                Component c = e.getChild();
                if (c instanceof JInternalFrame) watch((JInternalFrame) c);
            }
        });
    }

    private void watch(final JInternalFrame f) {
        if (!(f instanceof TableFrame)) return;
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                Table t = ((TableFrame) f).getTable();
                if (panel != null && split != null && t != null) panel.showTable(t.getName(), t.getCategory());
            }
        });
    }

    private void showNode(Object node) {
        if (panel == null || split == null) return;
        if (node instanceof TableTreeNode) {
            Table t = ((TableTreeNode) node).getTable();
            if (t != null) panel.showTable(t.getName(), t.getCategory());
        } else if (node instanceof CategoryTreeNode) {
            panel.showCategory(String.valueOf(((CategoryTreeNode) node).getUserObject()));
        }
    }

    private void showSelection() {
        if (panel == null) return;
        TreePath p = editor.getImageList().getSelectionPath();
        if (p != null) showNode(p.getLastPathComponent());
    }

    // ------------------------------------------------------- opening tables

    /** Opens or brings forward a table of the ROM in use; a message when it cannot. */
    String openTable(String name) {
        Rom rom = currentRom();
        if (rom == null) return "Open an image to see " + name + ".";
        TableTreeNode node = rom.getTableNodeByName(name);
        if (node == null) return name + " is not in " + rom.getFileName() + ".";
        if (RomTree.getRomNode(node) == null) {
            return name + " is hidden at the current user level.";
        }
        TableFrame frame = node.getFrame();
        if (frame != null && frame.getParent() != null) {
            try {
                if (frame.isIcon()) frame.setIcon(false);
                frame.toFront();
                frame.setSelected(true);
            } catch (Exception e) {
                // stays where it is
            }
        } else {
            editor.displayTable(node);
        }
        selectInTree(node);
        return null;
    }

    private Rom currentRom() {
        JInternalFrame f = editor.getRightPanel().getSelectedFrame();
        if (f instanceof TableFrame && ((TableFrame) f).getTable() != null) {
            Rom r = ((TableFrame) f).getTable().getRom();
            if (r != null) return r;
        }
        return editor.getLastSelectedRom();
    }

    private void selectInTree(TableTreeNode node) {
        TreeNode[] path = node.getPath();
        if (path == null || path.length == 0) return;
        TreePath tp = new TreePath(path);
        editor.getImageList().setSelectionPath(tp);
        editor.getImageList().scrollPathToVisible(tp);
    }
}
