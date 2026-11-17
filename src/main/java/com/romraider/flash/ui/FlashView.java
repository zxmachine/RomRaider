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

package com.romraider.flash.ui;

import java.awt.Container;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;

import com.romraider.flash.ActiveSessions;
import com.romraider.shell.MainWindow;
import com.romraider.shell.ShellView;

/** The Flash view of the main window: reading and writing an ECU. */
public final class FlashView implements ShellView {
    /** Identifier of the flash view. */
    public static final String FLASH = "flash";

    private FlashPanel panel;

    @Override
    public String getId() {
        return FLASH;
    }

    @Override
    public String getName() {
        return "Flash";
    }

    @Override
    public void start(MainWindow window, String[] args) {
        panel = new FlashPanel();
        window.viewReady(this);
    }

    @Override
    public Container getContent() {
        return panel;
    }

    @Override
    public List<JMenu> getMenus() {
        return Collections.emptyList();
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public void activated() {
        if (panel != null) panel.activated();
    }

    /** The text shown when the window is asked to close while the ECU is being read or written. */
    public static final String BUSY =
            "RomRaider is reading or writing the ECU (Flash view). It can't close until that ends:"
            + " closing in the middle of a write could leave the ECU partly written with the programming"
            + " voltage on. Wait for it to end, or press Stop in the Flash view (a write stops only between"
            + " commands, then switches the programming voltage off).";

    @Override
    public String busyReason() {
        return busy(panel != null && panel.isBusy(), ActiveSessions.isBusy());
    }

    /** The reason not to close: while the panel or any flash session is busy. */
    static String busy(boolean panelBusy, boolean sessionOpen) {
        return panelBusy || sessionOpen ? BUSY : null;
    }

    @Override
    public void handOver(String[] args) {
    }

    @Override
    public void stop(MainWindow window) {
        if (panel != null) panel.stop();
    }

    /** The panel, for a test driver; null before the view is started. */
    public FlashPanel getPanel() {
        return panel;
    }
}
