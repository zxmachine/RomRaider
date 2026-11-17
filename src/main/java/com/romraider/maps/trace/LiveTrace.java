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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.romraider.Settings;

/**
 * The live points of one table: where the ECU's lookup stands on the table (fractional indexes,
 * {@link AxisPosition}) and the value it reads there, with the time each came. The logger hands a
 * table's axes their values one after the other; a point that comes less than {@link #SAME_POINT_MS}
 * after the last one replaces it, so one logger reply makes one point. Thread safe: the logger adds,
 * the event thread paints.
 */
public final class LiveTrace {
    /** Points closer in time than this are one point (the axes of one logger reply). */
    public static final long SAME_POINT_MS = 15;

    /** One point of the trace. */
    public static final class Point {
        public final long time;
        public final double x;
        public final double y;
        public final double value;

        Point(long time, double x, double y, double value) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.value = value;
        }
    }

    private final ArrayDeque<Point> points = new ArrayDeque<Point>();

    /** Adds the point where the lookup now stands (y is 0 for a 2D table). */
    public synchronized void add(long time, double x, double y, double value) {
        if (Double.isNaN(x) || Double.isNaN(y)) return;
        Point last = points.peekLast();
        if (last != null && time - last.time < SAME_POINT_MS && time >= last.time) {
            points.pollLast();
            time = last.time;
        }
        points.addLast(new Point(time, x, y, value));
        long keep = time - (long) (Settings.MAX_LIVE_TRACE_SECONDS * 1000);
        while (!points.isEmpty() && points.peekFirst().time < keep) points.pollFirst();
    }

    /** The newest point, or null. */
    public synchronized Point latest() {
        return points.peekLast();
    }

    /**
     * The points of the last seconds before the newest one, oldest first. The newest point is the
     * reference, so a trace stays as it was when the logger stops.
     */
    public synchronized List<Point> recent(double seconds) {
        List<Point> out = new ArrayList<Point>();
        Point last = points.peekLast();
        if (last == null) return out;
        long from = last.time - (long) (seconds * 1000);
        for (Iterator<Point> it = points.iterator(); it.hasNext();) {
            Point p = it.next();
            if (p.time >= from) out.add(p);
        }
        return out;
    }

    public synchronized void clear() {
        points.clear();
    }

    public synchronized boolean isEmpty() {
        return points.isEmpty();
    }
}
