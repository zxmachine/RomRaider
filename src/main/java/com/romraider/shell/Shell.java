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

import javax.swing.SwingUtilities;

import org.apache.log4j.Logger;

import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.logger.ecu.EcuLogger;

/**
 * Starts the main window and holds the hooks the editor and the logger call:
 * while the main window hosts them, their frames stay hidden, their exits
 * close the main window, and "Launch Logger" switches to the logger view.
 * Without a main window (the separate windows) every hook does nothing.
 */
public final class Shell {
    private static final Logger LOGGER = Logger.getLogger(Shell.class);
    private static volatile boolean oneWindow = true;
    private static volatile boolean hosting;
    private static volatile MainWindow window;

    private Shell() {
        throw new UnsupportedOperationException();
    }

    /**
     * Reads the switches for the main window from the command line and
     * returns the arguments without them, for the rest of the program.
     */
    public static String[] readArgs(String[] args) {
        oneWindow = ShellArgs.wantsMainWindow(args);
        return ShellArgs.withoutShellArgs(args);
    }

    /**
     * Opens the main window, unless the command line asked for separate
     * windows; true when it did. Call from the main thread, not the event thread.
     */
    public static boolean start(final String[] args) {
        if (!oneWindow || !ShellArgs.wantsMainWindow(args)) return false;
        hosting = true;
        // the editor is built first, hidden, so that the logger always has it
        ECUEditorManager.getECUEditor();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ViewRegistry registry = new ViewRegistry();
                    registry.add(new EditorView());     // the base view, closed last
                    registry.add(new LoggerView());
                    registry.add(new com.romraider.flash.ui.FlashView());
                    // further views are added here
                    MainWindow created = new MainWindow(registry, args);
                    window = created;
                    created.setVisible(true);
                    created.showView(ShellArgs.startView(args));
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("Main window open");
        return true;
    }

    /** True while the main window hosts the editor and the logger. */
    public static boolean isHosting() {
        return hosting;
    }

    /** The main window, or null. */
    public static MainWindow getWindow() {
        return window;
    }

    /**
     * Called first in the editor's and the logger's exit: while the main
     * window hosts them, an exit closes the whole window (and returns true);
     * while the window is closing, the exit goes on as before (false).
     */
    public static boolean takeOverExit() {
        final MainWindow current = window;
        if (current == null || current.isClosing()) return false;
        if (SwingUtilities.isEventDispatchThread()) {
            current.close();
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    current.close();
                }
            });
        }
        return true;
    }

    /** Called by the editor's "Launch Logger": shows the logger view; false without a main window. */
    public static boolean showLogger() {
        return showView(ShellView.LOGGER);
    }

    /** Shows a view of the main window; false without a main window. */
    public static boolean showView(final String id) {
        final MainWindow current = window;
        if (current == null) return false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                current.showView(id);
            }
        });
        return true;
    }

    /** Called by the logger, on the event thread, once it is built: the logger view takes its content. */
    public static void hostLogger(EcuLogger logger) {
        MainWindow current = window;
        ShellView view = current == null ? null : current.getRegistry().get(ShellView.LOGGER);
        if (view instanceof LoggerView) {
            ((LoggerView) view).hosted(logger);
        } else {
            LOGGER.error("No logger view to host the logger");
        }
    }

    /**
     * Takes what a later start of the program handed over: switches to the
     * logger, or to the editor and opens the image named. False without a
     * main window, so that the separate windows go on as before.
     */
    public static boolean handOver(final boolean logger, final String[] args) {
        final MainWindow current = window;
        if (current == null) return false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                String busy = current.getRegistry().busyReason();
                if (busy != null) {     // a write to an ECU runs: leave its view as it is
                    current.bringToFront();
                    javax.swing.JOptionPane.showMessageDialog(current, busy);
                    return;
                }
                String id = logger ? ShellView.LOGGER : ShellView.EDITOR;
                current.showView(id);
                current.getRegistry().get(id).handOver(args);
                current.bringToFront();
            }
        });
        return true;
    }
}
