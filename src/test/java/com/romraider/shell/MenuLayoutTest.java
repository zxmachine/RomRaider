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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuBar;

import org.junit.Test;

public class MenuLayoutTest {

    private static JMenu menu(String text, int mnemonic) {
        JMenu menu = new JMenu(text);
        menu.setMnemonic(mnemonic);
        return menu;
    }

    private final JMenu window = menu("Window", KeyEvent.VK_W);

    @Test
    public void commonMenusGoBeforeHelp() {
        JMenu file = menu("File", KeyEvent.VK_F);
        JMenu help = menu("Help", KeyEvent.VK_H);
        assertEquals(Arrays.asList(file, window, help),
                MenuLayout.arrange(Arrays.asList(file, help), Collections.singletonList(window)));
    }

    @Test
    public void commonMenusGoLastWithoutHelp() {
        JMenu file = menu("File", KeyEvent.VK_F);
        JMenu tools = menu("Tools", KeyEvent.VK_T);
        assertEquals(Arrays.asList(file, tools, window),
                MenuLayout.arrange(Arrays.asList(file, tools), Collections.singletonList(window)));
        assertEquals(Arrays.asList(window),
                MenuLayout.arrange(Collections.<JMenu>emptyList(), Collections.singletonList(window)));
    }

    @Test
    public void readsTheMenusOfABar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu("File", KeyEvent.VK_F);
        JMenu help = menu("Help", KeyEvent.VK_H);
        bar.add(file);
        bar.add(help);
        List<JMenu> menus = MenuLayout.menusOf(bar);
        assertEquals(Arrays.asList(file, help), menus);
        assertTrue(MenuLayout.menusOf(null).isEmpty());
    }
}
