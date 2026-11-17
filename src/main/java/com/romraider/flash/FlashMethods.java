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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The flash methods there are, chosen by a ROM's flashmethod and memmodel. */
public final class FlashMethods {
    private static final List<FlashMethod> METHODS =
            Collections.unmodifiableList(Arrays.<FlashMethod>asList(new Wrx02Method()));

    private FlashMethods() {
    }

    public static List<FlashMethod> all() {
        return METHODS;
    }

    /** The method for a ROM's flashmethod and memmodel, or null when there is none. */
    public static FlashMethod forRom(String flashMethod, String memoryModel) {
        for (FlashMethod m : METHODS) {
            if (m.supports(flashMethod, memoryModel)) return m;
        }
        return null;
    }
}
