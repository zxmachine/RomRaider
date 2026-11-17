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

package com.romraider.maps.checksum;

import java.util.Map;

import com.romraider.util.HexUtil;

/**
 * A 16-bit sum of big-endian words that must come to a fixed value, kept by
 * a correction word inside the summed range. The 16-bit Subaru Denso ROMs
 * (68HC16Y5) use it. In a definition:
 *
 * <pre>
 * &lt;checksum type="wordsum16" start="0" end="27FF4" correction="27FF2" target="5AA5"/&gt;
 * </pre>
 *
 * start and end (exclusive) are file offsets of the summed words, correction
 * is the file offset of the correction word, target the sum; all hex.
 */
public final class ChecksumWORDSUM16 implements ChecksumManager {
    private static final String START = "start";
    private static final String END = "end";
    private static final String CORRECTION = "correction";
    private static final String TARGET = "target";
    private int start;
    private int end;
    private int correction;
    private int target;

    @Override
    public void configure(Map<String, String> vars) {
        start = HexUtil.hexToInt(vars.get(START));
        end = HexUtil.hexToInt(vars.get(END));
        correction = HexUtil.hexToInt(vars.get(CORRECTION));
        target = HexUtil.hexToInt(vars.get(TARGET)) & 0xFFFF;
    }

    @Override
    public int getNumberOfChecksums() {
        return 1;
    }

    @Override
    public int validate(byte[] data) {
        return fits(data) && sum(data, start, end) == target ? 1 : 0;
    }

    @Override
    public int update(byte[] data) {
        if (!fits(data)) return 0;
        return fix(data, start, end, correction, target) ? 1 : 0;
    }

    private boolean fits(byte[] data) {
        return data != null && start >= 0 && start <= end && end <= data.length
                && correction >= start && correction + 2 <= end;
    }

    /** The 16-bit sum of the big-endian words from start to end (exclusive). */
    public static int sum(byte[] data, int start, int end) {
        int sum = 0;
        for (int i = start; i + 1 < end; i += 2) {
            sum += ((data[i] & 0xFF) << 8) | (data[i + 1] & 0xFF);
        }
        return sum & 0xFFFF;
    }

    /**
     * Sets the correction word so that the sum is the target; true when it
     * had to change.
     */
    public static boolean fix(byte[] data, int start, int end, int correction, int target) {
        int old = ((data[correction] & 0xFF) << 8) | (data[correction + 1] & 0xFF);
        int rest = (sum(data, start, end) - old) & 0xFFFF;
        int word = (target - rest) & 0xFFFF;
        data[correction] = (byte) (word >> 8);
        data[correction + 1] = (byte) word;
        return word != old;
    }
}
