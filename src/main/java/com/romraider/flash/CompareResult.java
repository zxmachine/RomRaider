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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The CRC of every erase block, in the ECU and in the image. */
public final class CompareResult {
    /** One block's CRCs. */
    public static final class Entry {
        private final FlashBlock block;
        private final long ecuCrc;
        private final long imageCrc;

        public Entry(FlashBlock block, long ecuCrc, long imageCrc) {
            this.block = block;
            this.ecuCrc = ecuCrc;
            this.imageCrc = imageCrc;
        }

        public FlashBlock getBlock() { return block; }
        public long getEcuCrc() { return ecuCrc; }
        public long getImageCrc() { return imageCrc; }
        public boolean isSame() { return ecuCrc == imageCrc; }

        @Override
        public String toString() {
            return String.format("%s 0x%05X  ecu %08X  image %08X  %s", block.getName(), block.getAddress(),
                    ecuCrc, imageCrc, isSame() ? "same" : "DIFFERS");
        }
    }

    private final List<Entry> entries;

    public CompareResult(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** The blocks that differ, in address order. */
    public List<FlashBlock> differing() {
        List<FlashBlock> out = new ArrayList<FlashBlock>();
        for (Entry e : entries) {
            if (!e.isSame()) out.add(e.getBlock());
        }
        return out;
    }

    public boolean isSame() {
        return differing().isEmpty();
    }
}
