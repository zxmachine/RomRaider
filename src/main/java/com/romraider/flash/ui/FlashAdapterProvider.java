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

package com.romraider.flash.ui;

import com.romraider.io.j2534.api.J2534;

/**
 * An adapter the Flash view can offer besides the J2534 libraries installed
 * on the computer. The view adds one when the system property
 * {@value #PROPERTY} names a class that implements this interface and is on
 * the class path; release builds contain none (the simulated ECU of the
 * tests is one, for trying the view without a car).
 */
public interface FlashAdapterProvider {
    /** The system property that names an extra adapter class. */
    String PROPERTY = "romraider.flash.testAdapter";

    /** The name shown in the adapter list. */
    String getName();

    /** An adapter for one session. */
    J2534 create();
}
