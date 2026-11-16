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
import java.util.List;

import javax.swing.JMenu;

/**
 * One view of the main window: a content panel, the menus that belong to it
 * and a window title. The main window shows one view at a time and puts the
 * active view's menus into its menu bar beside its own common menus.
 *
 * A new view implements this interface and is added to the registry in
 * {@link Shell#start(String[])}.
 */
public interface ShellView {
    /** Identifier of the ROM editor view. */
    String EDITOR = "editor";
    /** Identifier of the logger view. */
    String LOGGER = "logger";

    /** A short identifier, used to switch to the view. */
    String getId();

    /** The name shown on the view's tab and in the Window menu. */
    String getName();

    /**
     * Starts the view, the first time it is shown. Called once, on the event
     * thread. The view may build itself on another thread; once its content
     * and menus are there it calls {@link MainWindow#viewReady(ShellView)}
     * on the event thread.
     */
    void start(MainWindow window, String[] args);

    /** The view's content, or null while it is not ready. */
    Container getContent();

    /** The view's own menus, in menu bar order. */
    List<JMenu> getMenus();

    /** The window title while this view is active. */
    String getTitle();

    /** The view became the active one, and it is ready. */
    void activated();

    /** Takes the arguments a later start of the program handed over. */
    void handOver(String[] args);

    /**
     * Stops the view when the main window closes. Called on the event thread,
     * for each started view, the registry's base view last. Saves of the
     * settings are held for all but the base view, which saves them once and
     * may end the program.
     */
    void stop(MainWindow window);
}
