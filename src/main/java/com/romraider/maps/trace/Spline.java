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

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A smooth curve through points: a centripetal Catmull-Rom spline (alpha 0.5), as one path of cubic
 * segments. It passes through every point, has no corners between segments, and, unlike the uniform
 * Catmull-Rom spline, makes no loops or overshoots where the points are unevenly spaced (a fast change
 * between slow ones). Points closer than {@link #MIN_STEP} to the one before are left out.
 */
public final class Spline {
    /** Points closer than this (in the units of the points, pixels when drawing) are one point. */
    public static final double MIN_STEP = 0.5;
    private static final double ALPHA = 0.5;
    private static final double EPS = 1e-9;

    private Spline() {
    }

    /** The points without those closer than {@link #MIN_STEP} to the one kept before. */
    public static List<Point2D> distinct(List<? extends Point2D> points) {
        List<Point2D> out = new ArrayList<Point2D>();
        for (Point2D p : points) {
            if (out.isEmpty() || out.get(out.size() - 1).distance(p) >= MIN_STEP) out.add(p);
        }
        return out;
    }

    /** The curve through the points, or null for fewer than two distinct points. */
    public static Path2D.Double through(List<? extends Point2D> points) {
        List<Point2D> p = distinct(points);
        int n = p.size();
        if (n < 2) return null;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p.get(0).getX(), p.get(0).getY());
        for (int i = 0; i < n - 1; i++) {
            Point2D p1 = p.get(i);
            Point2D p2 = p.get(i + 1);
            // the ends are extended by mirroring the next point
            Point2D p0 = i > 0 ? p.get(i - 1) : mirror(p2, p1);
            Point2D p3 = i + 2 < n ? p.get(i + 2) : mirror(p1, p2);
            double d1 = Math.pow(p0.distance(p1), ALPHA);
            double d2 = Math.pow(p1.distance(p2), ALPHA);
            double d3 = Math.pow(p2.distance(p3), ALPHA);
            double[] b1 = control(p0, p1, p2, d1, d2);
            double[] b2 = control(p3, p2, p1, d3, d2);
            path.curveTo(b1[0], b1[1], b2[0], b2[1], p2.getX(), p2.getY());
        }
        return path;
    }

    /** a reflected through b. */
    private static Point2D mirror(Point2D a, Point2D b) {
        return new Point2D.Double(2 * b.getX() - a.getX(), 2 * b.getY() - a.getY());
    }

    /**
     * The Bezier control point next to `at` of the segment from `at` towards `to`, with `from` the
     * point before `at`: (da^2 to - db^2 from + (2 da^2 + 3 da db + db^2) at) / (3 da (da + db)),
     * da and db the centripetal knot intervals of from-at and at-to.
     */
    private static double[] control(Point2D from, Point2D at, Point2D to, double da, double db) {
        if (da < EPS || db < EPS) return new double[] {at.getX(), at.getY()};
        double a2 = da * da, b2 = db * db;
        double k = 2 * a2 + 3 * da * db + b2;
        double m = 3 * da * (da + db);
        return new double[] {
            (a2 * to.getX() - b2 * from.getX() + k * at.getX()) / m,
            (a2 * to.getY() - b2 * from.getY() + k * at.getY()) / m};
    }
}
