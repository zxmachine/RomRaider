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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.KeyStroke;

import org.junit.Test;

public class ViewRegistryTest {

    /** A view that is only a name. */
    private static final class NamedView implements ShellView {
        private final String id;

        NamedView(String id) {
            this.id = id;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return id; }
        @Override public void start(MainWindow window, String[] args) { }
        @Override public Container getContent() { return null; }
        @Override public List<JMenu> getMenus() { return Collections.emptyList(); }
        @Override public String getTitle() { return id; }
        @Override public void activated() { }
        @Override public void handOver(String[] args) { }
        @Override public void stop(MainWindow window) { }
    }

    private final ShellView editor = new NamedView(ShellView.EDITOR);
    private final ShellView logger = new NamedView(ShellView.LOGGER);
    private final ShellView docs = new NamedView("third");

    private ViewRegistry registry() {
        ViewRegistry registry = new ViewRegistry();
        registry.add(editor);
        registry.add(logger);
        registry.add(docs);
        return registry;
    }

    @Test
    public void findsViewsByIdAndIndex() {
        ViewRegistry registry = registry();
        assertSame(logger, registry.get(ShellView.LOGGER));
        assertNull(registry.get("none"));
        assertEquals(0, registry.indexOf(ShellView.EDITOR));
        assertEquals(2, registry.indexOf("third"));
        assertEquals(-1, registry.indexOf("none"));
        assertSame(editor, registry.getBase());
        assertEquals(Arrays.asList(editor, logger, docs), registry.getViews());
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesTwoViewsWithOneId() {
        ViewRegistry registry = registry();
        registry.add(new NamedView(ShellView.LOGGER));
    }

    @Test(expected = IllegalArgumentException.class)
    public void activeViewMustBeRegistered() {
        registry().setActive(new NamedView("stranger"));
    }

    @Test
    public void startsEachViewOnce() {
        ViewRegistry registry = registry();
        assertFalse(registry.isStarted(logger));
        assertTrue(registry.markStarted(logger));
        assertFalse(registry.markStarted(logger));
        assertTrue(registry.isStarted(logger));
    }

    @Test
    public void closesStartedViewsLastFirstAndTheBaseLast() {
        ViewRegistry registry = registry();
        assertEquals(Arrays.asList(editor), registry.closeOrder());

        registry.markStarted(logger);
        assertEquals(Arrays.asList(logger, editor), registry.closeOrder());

        registry.markStarted(editor);
        registry.markStarted(docs);
        assertEquals(Arrays.asList(docs, logger, editor), registry.closeOrder());
    }

    @Test
    public void shortcutsAreCtrlAndTheTabNumber() {
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK), ViewRegistry.shortcut(0));
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.CTRL_DOWN_MASK), ViewRegistry.shortcut(1));
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK), ViewRegistry.shortcut(8));
        assertNull(ViewRegistry.shortcut(9));
        assertNull(ViewRegistry.shortcut(-1));
    }

    @Test
    public void aStartedBusyViewKeepsTheWindowOpen() {
        final String[] reason = {null};
        ShellView busy = new ShellView() {
            @Override public String getId() { return "flash"; }
            @Override public String getName() { return "flash"; }
            @Override public void start(MainWindow window, String[] args) { }
            @Override public Container getContent() { return null; }
            @Override public List<JMenu> getMenus() { return Collections.emptyList(); }
            @Override public String getTitle() { return "flash"; }
            @Override public void activated() { }
            @Override public void handOver(String[] args) { }
            @Override public void stop(MainWindow window) { }
            @Override public String busyReason() { return reason[0]; }
        };
        ViewRegistry registry = new ViewRegistry();
        registry.add(new NamedView(ShellView.EDITOR));
        registry.add(busy);
        reason[0] = "writing";
        assertNull("a view that is not started is not asked", registry.busyReason());
        registry.markStarted(busy);
        assertEquals("writing", registry.busyReason());
        reason[0] = null;
        assertNull(registry.busyReason());
    }
}
