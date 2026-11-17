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

/** What the ECU reports about itself: the kernel's version and the ROM ID in its flash. */
public final class EcuIdentity {
    private final String kernelVersion;
    private final String romId;
    private final boolean blank;

    public EcuIdentity(String kernelVersion, String romId, boolean blank) {
        this.kernelVersion = kernelVersion;
        this.romId = romId;
        this.blank = blank;
    }

    public String getKernelVersion() { return kernelVersion; }

    /** The ROM ID in the ECU's flash; empty when blank. */
    public String getRomId() { return romId; }

    /** True when the ROM ID area is erased: an earlier write stopped part way. */
    public boolean isBlank() { return blank; }

    @Override
    public String toString() {
        return blank ? "blank ROM ID (erased)" : "ROM ID " + romId;
    }
}
