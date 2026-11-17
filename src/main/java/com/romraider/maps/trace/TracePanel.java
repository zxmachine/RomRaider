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

package com.romraider.maps.trace;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * The panel that holds a table view's cells, with the live trace painted over them. It reports that
 * its children may be painted over, so that a cell that repaints itself is painted through this
 * panel and the trace stays on top.
 */
public class TracePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private transient TraceOverlay overlay;

    public TracePanel(LayoutManager layout) {
        super(layout);
    }

    public void setOverlay(TraceOverlay overlay) {
        this.overlay = overlay;
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return overlay == null;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        TraceOverlay o = overlay;
        if (o != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                o.paint(g2, this);
            } finally {
                g2.dispose();
            }
        }
    }
}
