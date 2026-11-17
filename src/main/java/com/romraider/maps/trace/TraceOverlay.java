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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import com.romraider.Settings;
import com.romraider.maps.TableView;
import com.romraider.util.SettingsManager;

/**
 * Paints a table's live trace over its cells while "Overlay Log" is on: the point where the ECU's
 * lookup now stands, labelled with the value it reads there, and a smooth line ({@link Spline})
 * through the points of the last seconds (Settings.getLiveTraceSeconds).
 */
public class TraceOverlay {
    private static final Color HALO = new Color(255, 255, 255, 170);
    private static final Color OUTLINE = new Color(0, 0, 0, 200);
    private static final Color LABEL_BACK = new Color(255, 255, 255, 225);

    private final TableView view;
    private final LiveTrace trace;
    private final TraceLayout layout;

    public TraceOverlay(TableView view, LiveTrace trace, TraceLayout layout) {
        this.view = view;
        this.trace = trace;
        this.layout = layout;
    }

    public void paint(Graphics2D g, JComponent on) {
        if (!view.getOverlayLog() || trace.isEmpty() || layout.columns() < 1 || layout.rows() < 1) return;
        Settings settings = SettingsManager.getSettings();
        List<LiveTrace.Point> points = trace.recent(settings.getLiveTraceSeconds());
        if (points.isEmpty()) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        List<Point2D> screen = new ArrayList<Point2D>(points.size());
        for (LiveTrace.Point p : points) screen.add(new Point2D.Double(x(p.x), y(p.y)));
        Path2D.Double line = Spline.through(screen);
        if (line != null) {
            // the current value's colour: the live value's colour marks the cells visited, in another meaning
            Color c = settings.getCurLiveValueColor();
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(HALO);
            g.draw(line);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 190));
            g.draw(line);
        }

        LiveTrace.Point last = points.get(points.size() - 1);
        Point2D at = screen.get(screen.size() - 1);
        double r = 5;
        Ellipse2D dot = new Ellipse2D.Double(at.getX() - r, at.getY() - r, 2 * r, 2 * r);
        g.setColor(settings.getCurLiveValueColor());
        g.fill(dot);
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(OUTLINE);
        g.draw(dot);

        String text = format(last.value);
        if (text != null) label(g, on, text, at);
    }

    /** The x of a fractional column position: between the centres of the columns around it. */
    double x(double position) {
        return place(position, layout.columns(), true);
    }

    /** The y of a fractional row position (a 2D table has one row). */
    double y(double position) {
        return place(position, layout.rows(), false);
    }

    private double place(double position, int n, boolean column) {
        double p = Math.max(0, Math.min(n - 1, position));
        int i = (int) Math.floor(p);
        double a = column ? layout.columnCentre(i) : layout.rowCentre(i);
        if (i >= n - 1) return a;
        double b = column ? layout.columnCentre(i + 1) : layout.rowCentre(i + 1);
        return a + (b - a) * (p - i);
    }

    private String format(double value) {
        if (Double.isNaN(value)) return null;
        try {
            String pattern = view.getTable().getCurrentScale().getFormat();
            return new DecimalFormat(pattern == null || pattern.isEmpty() ? "0.##" : pattern).format(value);
        } catch (RuntimeException e) {
            return new DecimalFormat("0.##").format(value);
        }
    }

    private static void label(Graphics2D g, JComponent on, String text, Point2D at) {
        Font font = on.getFont().deriveFont(Font.BOLD);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        double w = fm.stringWidth(text) + 8, h = fm.getHeight() + 2;
        double x = at.getX() + 9, y = at.getY() - h - 6;
        if (x + w > on.getWidth()) x = at.getX() - 9 - w;
        if (y < 0) y = at.getY() + 8;
        RoundRectangle2D box = new RoundRectangle2D.Double(x, y, w, h, 6, 6);
        g.setColor(LABEL_BACK);
        g.fill(box);
        g.setStroke(new BasicStroke(1f));
        g.setColor(OUTLINE);
        g.draw(box);
        g.drawString(text, (float) (x + 4), (float) (y + 1 + fm.getAscent()));
    }
}
