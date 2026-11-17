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

/**
 * Where an ECU's table lookup puts a value on an axis, and the value it then reads from the table.
 *
 * The lookup routines of the Subaru ECUs search an ascending axis from its top for the breakpoint at
 * or below the input and take the fraction of the way to the next one; at or beyond either end they
 * use the end breakpoint with no fraction. A 3D table is interpolated along one axis in the two rows
 * around the input, and those two results along the other axis (bilinear). An evenly spaced axis
 * gives the same position. The position is a fractional index: 2.25 is a quarter of the way from
 * breakpoint 2 to breakpoint 3. With the linear scalings of these tables, display units give the same
 * position and value as the ECU's raw numbers.
 */
public final class AxisPosition {

    private AxisPosition() {
    }

    /**
     * The fractional index of value on the breakpoints, clamped to the axis; NaN without breakpoints or
     * for a value that is not a number. A descending axis is read as the same breakpoints reversed.
     */
    public static double of(double[] breakpoints, double value) {
        int n = breakpoints.length;
        if (n == 0 || Double.isNaN(value)) return Double.NaN;
        for (double b : breakpoints) if (Double.isNaN(b)) return Double.NaN;
        if (n == 1) return 0;
        if (breakpoints[n - 1] < breakpoints[0]) {
            double[] ascending = new double[n];
            for (int i = 0; i < n; i++) ascending[i] = breakpoints[n - 1 - i];
            return n - 1 - of(ascending, value);
        }
        if (value >= breakpoints[n - 1]) return n - 1;
        for (int i = n - 2; i >= 0; i--) {          // from the top, as the ECU searches
            if (value >= breakpoints[i]) {
                double span = breakpoints[i + 1] - breakpoints[i];
                return span > 0 ? i + (value - breakpoints[i]) / span : i;
            }
        }
        return 0;                                   // below the first breakpoint
    }

    /** The value at a fractional index of a row of values, linear between the two around it. */
    public static double interpolate(double[] values, double position) {
        int n = values.length;
        if (n == 0 || Double.isNaN(position)) return Double.NaN;
        double p = Math.max(0, Math.min(n - 1, position));
        int i = (int) Math.floor(p);
        if (i >= n - 1) return values[n - 1];
        double f = p - i;
        return values[i] + (values[i + 1] - values[i]) * f;
    }

    /** The value at fractional indexes of a grid of values indexed [x][y], bilinear. */
    public static double interpolate(double[][] values, double x, double y) {
        int nx = values.length;
        if (nx == 0 || Double.isNaN(x) || Double.isNaN(y)) return Double.NaN;
        double px = Math.max(0, Math.min(nx - 1, x));
        int i = (int) Math.floor(px);
        double fx = px - i;
        double[] column = new double[values[0].length];
        for (int j = 0; j < column.length; j++) {
            double a = values[i][j];
            column[j] = i >= nx - 1 ? a : a + (values[i + 1][j] - a) * fx;
        }
        return interpolate(column, y);
    }
}
