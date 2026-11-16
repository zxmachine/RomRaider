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

package com.romraider.shell;

import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditor;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.util.SettingsManager;
import com.romraider.util.ThreadUtil;

/**
 * The ROM editor as a view: the editor's frame is built but never shown; its
 * content pane and menus move into the main window.
 */
final class EditorView implements ShellView {
    private MainWindow window;
    private ECUEditor editor;
    private Container content;
    private List<JMenu> menus = Collections.emptyList();
    private String[] startArgs = new String[0];
    private boolean definitionsChecked;

    @Override
    public String getId() {
        return EDITOR;
    }

    @Override
    public String getName() {
        return MainWindow.rb.getString("EDITOR");
    }

    @Override
    public void start(MainWindow window, String[] args) {
        this.window = window;
        this.startArgs = args;
        ECUEditor built = ECUEditorManager.getECUEditorWithoutCreation();
        if (built != null) {
            install(built);
            return;
        }
        // ECUEditorManager builds the editor with invokeAndWait: not from the event thread
        ThreadUtil.runAsDaemon(new Runnable() {
            @Override
            public void run() {
                final ECUEditor created = ECUEditorManager.getECUEditor();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        EcuLogger logger = EcuLogger.getEcuLoggerWithoutCreation();
                        if (logger != null) logger.setEcuEditor(created);
                        install(created);
                    }
                });
            }
        });
    }

    private void install(ECUEditor built) {
        editor = built;
        editor.initializeEditorUI();
        content = editor.getContentPane();
        menus = MenuLayout.menusOf(editor.getJMenuBar());
        window.setTransferHandler(new DropToEditor(editor.getTransferHandler(), window));
        editor.addPropertyChangeListener("title", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                window.titleChanged(EditorView.this);
            }
        });
        window.viewReady(this);
        String image = ShellArgs.startView(startArgs).equals(EDITOR) ? ShellArgs.imagePath(startArgs) : null;
        if (image != null) editor.openImage(image);
    }

    @Override
    public Container getContent() {
        return content;
    }

    @Override
    public List<JMenu> getMenus() {
        return menus;
    }

    @Override
    public String getTitle() {
        return editor == null ? null : editor.getTitle();
    }

    @Override
    public void activated() {
        if (definitionsChecked) return;
        definitionsChecked = true;
        // the editor asks for definitions the first time it is shown, after the window is up
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                editor.checkDefinitions();
            }
        });
    }

    @Override
    public void handOver(String[] args) {
        String image = ShellArgs.imagePath(args);
        if (image != null && editor != null) editor.openImage(image);
    }

    @Override
    public void stop(MainWindow window) {
        ECUEditor closing = editor != null ? editor : ECUEditorManager.getECUEditorWithoutCreation();
        if (closing == null) {
            Settings settings = SettingsManager.getSettings();
            settings.setWindowMaximized(window.getExtendedState() == JFrame.MAXIMIZED_BOTH);
            settings.setWindowSize(window.getSize());
            settings.setWindowLocation(window.getLocation());
            SettingsManager.save(settings);
            return;
        }
        // the editor saves its own frame's size and place: give it the main window's.
        // A dialog may have made the hidden frame displayable; dispose it first so
        // that setting its state can never show it.
        closing.dispose();
        closing.setSize(window.getSize());
        closing.setLocation(window.getLocation());
        closing.setExtendedState(window.getExtendedState());
        closing.handleExit();
    }

    /** Drops files on the window into the editor, and shows the editor. */
    private static final class DropToEditor extends TransferHandler {
        private static final long serialVersionUID = -6064180566429371547L;
        private final TransferHandler editorHandler;
        private final MainWindow window;

        DropToEditor(TransferHandler editorHandler, MainWindow window) {
            this.editorHandler = editorHandler;
            this.window = window;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return editorHandler != null && editorHandler.canImport(support);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (editorHandler == null || !editorHandler.importData(support)) return false;
            window.showView(EDITOR);
            return true;
        }
    }
}
