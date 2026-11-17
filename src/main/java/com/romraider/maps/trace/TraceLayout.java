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
 * Where a table view draws its cells, in the coordinates of the panel the trace is painted on: the
 * centre of each data column and row. A fractional position lies between the centres of the cells
 * around it, in proportion.
 */
public interface TraceLayout {
    int columns();

    int rows();

    /** The x of the centre of data column i. */
    double columnCentre(int i);

    /** The y of the centre of data row j. */
    double rowCentre(int j);
}
