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

package com.romraider.flash;

import java.util.List;

/** The settings a flash session runs with. Writing is off unless switched on. */
public final class FlashOptions {
    /** Writing is allowed at all (the settings switch; off by default). */
    public boolean writingEnabled = false;
    /** No write starts below this battery voltage. */
    public double minBatteryVolts = 12.0;
    /** The programming voltage put on the programming pin for erase and program, in millivolts. */
    public int programmingMillivolts = 12000;
    /** The pin that asks the ECU for its boot loader. */
    public int reflashPin = 9;
    /** The pin that carries the programming voltage. */
    public int programmingPin = 11;
    /** How long the reflash pin is released to ask for the boot loader. */
    public int pulseMillis = 200;
    /** How long to wait for the boot loader after asking the user to cycle the ignition. */
    public int waitSeconds = 60;
    /**
     * The kernel may still be running from an earlier session (after an
     * error, before the ignition was cycled): try it first, and enter the
     * boot loader only when it does not answer.
     */
    public boolean kernelMayBeRunning = false;
    /** A kernel file that is not one of {@link #knownKernels} may be uploaded (off at every start, never saved). */
    public boolean allowUnknownKernel = false;
    /** The kernels a session uploads without {@link #allowUnknownKernel}. */
    public List<KnownKernel> knownKernels = KnownKernel.all();

    public FlashOptions copy() {
        FlashOptions o = new FlashOptions();
        o.writingEnabled = writingEnabled;
        o.minBatteryVolts = minBatteryVolts;
        o.programmingMillivolts = programmingMillivolts;
        o.reflashPin = reflashPin;
        o.programmingPin = programmingPin;
        o.pulseMillis = pulseMillis;
        o.waitSeconds = waitSeconds;
        o.kernelMayBeRunning = kernelMayBeRunning;
        o.allowUnknownKernel = allowUnknownKernel;
        o.knownKernels = knownKernels;
        return o;
    }
}
