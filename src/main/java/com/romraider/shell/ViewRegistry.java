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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.KeyStroke;

/**
 * The views of the main window in tab order, which of them are started and
 * which is active. The first view is the base view: it is closed last.
 */
public final class ViewRegistry {
    private final List<ShellView> views = new ArrayList<ShellView>();
    private final Set<ShellView> started = new LinkedHashSet<ShellView>();
    private ShellView active;

    public void add(ShellView view) {
        if (view == null) throw new IllegalArgumentException("view is null");
        if (get(view.getId()) != null) {
            throw new IllegalArgumentException("a view with id " + view.getId() + " is already registered");
        }
        views.add(view);
    }

    public List<ShellView> getViews() {
        return Collections.unmodifiableList(views);
    }

    /** The view with this id, or null. */
    public ShellView get(String id) {
        for (ShellView view : views) {
            if (view.getId().equals(id)) return view;
        }
        return null;
    }

    /** The tab index of the view with this id, or -1. */
    public int indexOf(String id) {
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    /** The base view (the first one), or null when there is none. */
    public ShellView getBase() {
        return views.isEmpty() ? null : views.get(0);
    }

    public ShellView getActive() {
        return active;
    }

    public void setActive(ShellView view) {
        if (view != null && !views.contains(view)) {
            throw new IllegalArgumentException("view is not registered: " + view.getId());
        }
        active = view;
    }

    /** Marks a view started; true the first time, false when it already was. */
    public boolean markStarted(ShellView view) {
        return started.add(view);
    }

    public boolean isStarted(ShellView view) {
        return started.contains(view);
    }

    /**
     * The order in which the views stop when the window closes: the started
     * views other than the base, last registered first, then the base view,
     * started or not.
     */
    public List<ShellView> closeOrder() {
        List<ShellView> order = new ArrayList<ShellView>();
        ShellView base = getBase();
        for (int i = views.size() - 1; i > 0; i--) {
            if (started.contains(views.get(i))) order.add(views.get(i));
        }
        if (base != null) order.add(base);
        return order;
    }

    /** The shortcut to switch to the view at this index: Ctrl+1 to Ctrl+9, or null. */
    public static KeyStroke shortcut(int index) {
        if (index < 0 || index > 8) return null;
        return KeyStroke.getKeyStroke(KeyEvent.VK_1 + index, InputEvent.CTRL_DOWN_MASK);
    }
}
