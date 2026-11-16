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

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Puts the active view's menus and the window's common menus into one menu bar. */
public final class MenuLayout {
    private MenuLayout() {
        throw new UnsupportedOperationException();
    }

    /** The menus of a menu bar, in order. */
    public static List<JMenu> menusOf(JMenuBar bar) {
        List<JMenu> menus = new ArrayList<JMenu>();
        if (bar == null) return menus;
        for (int i = 0; i < bar.getMenuCount(); i++) {
            JMenu menu = bar.getMenu(i);
            if (menu != null) menus.add(menu);
        }
        return menus;
    }

    /**
     * The view's menus with the common menus after them; when the view's
     * last menu is its Help menu (mnemonic H), the common menus go before it.
     */
    public static List<JMenu> arrange(List<JMenu> viewMenus, List<JMenu> commonMenus) {
        List<JMenu> menus = new ArrayList<JMenu>(viewMenus);
        int at = menus.size();
        if (at > 0 && menus.get(at - 1).getMnemonic() == KeyEvent.VK_H) at--;
        menus.addAll(at, commonMenus);
        return menus;
    }
}
