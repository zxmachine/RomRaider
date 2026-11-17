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

/** One erase block of the ECU's flash: its name, CPU address, offset in the image and size. */
public final class FlashBlock {
    private final String name;
    private final int address;
    private final int fileOffset;
    private final int size;

    public FlashBlock(String name, int address, int fileOffset, int size) {
        this.name = name;
        this.address = address;
        this.fileOffset = fileOffset;
        this.size = size;
    }

    public String getName() { return name; }
    public int getAddress() { return address; }
    public int getFileOffset() { return fileOffset; }
    public int getSize() { return size; }

    @Override
    public String toString() {
        return String.format("%s 0x%05X", name, address);
    }
}
