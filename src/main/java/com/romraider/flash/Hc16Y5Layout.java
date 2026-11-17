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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.romraider.maps.checksum.ChecksumWORDSUM16;

/**
 * The flash of the 68HC16Y5 Denso ECUs (memory model "68HC16Y5"): two arrays
 * of application flash, 0x00000-0x1FFFF and 0x28000-0x2FFFF, kept in a
 * 160 KB image file with the second array at file offset address - 0x8000.
 * Erase works on ten 16 KB blocks. A separate 4 KB boot block at 0x60000
 * holds the boot loader; it is outside the image and is never written.
 */
public final class Hc16Y5Layout {
    /** The memory model name in definitions. */
    public static final String MEMORY_MODEL = "68HC16Y5";
    /** The image size. */
    public static final int IMAGE_SIZE = 0x28000;
    /** The erase block size. */
    public static final int BLOCK_SIZE = 0x4000;
    /** Bytes programmed with one commit (the kernel's flash buffer). */
    public static final int PAGE_SIZE = 0x1000;
    /** Bytes per buffer load and per read request. */
    public static final int CHUNK_SIZE = 0x200;
    /** The block that holds the "programmed" marker: erased first and programmed last. */
    public static final int MARKER_BLOCK = 0x2C000;
    /** The two "programmed" marker bytes (equal and not 0xFF for the boot loader to start the application). */
    public static final int MARKER_ADDRESS = 0x2FFFE;
    /** The summed words end before this address; the correction word is just before it. */
    public static final int CHECKSUM_END = 0x2FFF4;
    public static final int CHECKSUM_WORD = 0x2FFF2;
    public static final int CHECKSUM_TARGET = 0x5AA5;
    /** Where the ROM ID is in the image, and its length. */
    public static final int ROM_ID_ADDRESS = 0x200;
    public static final int ROM_ID_LENGTH = 8;
    /** The boot block, which nothing here ever erases or programs. */
    public static final int BOOT_BLOCK = 0x60000;
    public static final int BOOT_BLOCK_SIZE = 0x1000;
    /** The address ranges of the image, inclusive. */
    public static final int[][] RANGES = {{0x00000, 0x1FFFF}, {0x28000, 0x2FFFF}};

    private static final List<FlashBlock> BLOCKS;

    static {
        List<FlashBlock> blocks = new ArrayList<FlashBlock>();
        for (int i = 0; i < 10; i++) {
            int address = i < 8 ? i * BLOCK_SIZE : 0x28000 + (i - 8) * BLOCK_SIZE;
            blocks.add(new FlashBlock(String.format("FB%02d", i + 1), address, fileOffset(address), BLOCK_SIZE));
        }
        BLOCKS = Collections.unmodifiableList(blocks);
    }

    private Hc16Y5Layout() {
    }

    /** The ten 16 KB erase blocks, in address order. */
    public static List<FlashBlock> blocks() {
        return BLOCKS;
    }

    public static FlashBlock markerBlock() {
        for (FlashBlock b : BLOCKS) {
            if (b.getAddress() == MARKER_BLOCK) return b;
        }
        throw new IllegalStateException();
    }

    /** True when the whole range lies in application flash (never in the boot block). */
    public static boolean isApplicationFlash(int address, int length) {
        if (length <= 0) return false;
        int last = address + length - 1;
        for (int[] r : RANGES) {
            if (address >= r[0] && last <= r[1]) return true;
        }
        return false;
    }

    /** The image file offset of a CPU address. */
    public static int fileOffset(int address) {
        if (address >= 0 && address <= 0x1FFFF) return address;
        if (address >= 0x28000 && address <= 0x2FFFF) return address - 0x8000;
        throw new IllegalArgumentException(String.format("0x%05X is not in the image", address));
    }

    /** The CPU address of an image file offset. */
    public static int address(int fileOffset) {
        if (fileOffset < 0 || fileOffset >= IMAGE_SIZE) {
            throw new IllegalArgumentException(String.format("offset 0x%05X is outside the image", fileOffset));
        }
        return fileOffset < 0x20000 ? fileOffset : fileOffset + 0x8000;
    }

    /** The image's 16-bit word sum. */
    public static int wordSum(byte[] image) {
        return ChecksumWORDSUM16.sum(image, 0, fileOffset(CHECKSUM_END - 1) + 1);
    }

    /**
     * The CRC-32 of each erase block, as one string: the key under which a
     * read counts as the backup of the same content (the kernel's compare
     * gives the same CRCs for the ECU).
     */
    public static String contentKey(byte[] image) {
        StringBuilder sb = new StringBuilder();
        for (FlashBlock b : BLOCKS) {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(image, b.getFileOffset(), b.getSize());
            sb.append(String.format("%08X", crc.getValue()));
        }
        return sb.toString();
    }

    /** Sets the correction word so the word sum is right; true when it changed. */
    public static boolean fixChecksum(byte[] image) {
        return ChecksumWORDSUM16.fix(image, 0, fileOffset(CHECKSUM_END - 1) + 1,
                fileOffset(CHECKSUM_WORD), CHECKSUM_TARGET);
    }

    /** The ROM ID text in the image (printable ASCII only). */
    public static String romId(byte[] image) {
        int at = fileOffset(ROM_ID_ADDRESS);
        if (image == null || image.length < at + ROM_ID_LENGTH) return "";
        return romIdText(image, at, ROM_ID_LENGTH);
    }

    /** ROM ID bytes as text; non-printable bytes as '?'. */
    public static String romIdText(byte[] data, int at, int length) {
        byte[] b = new byte[length];
        for (int i = 0; i < length; i++) {
            int c = data[at + i] & 0xFF;
            b[i] = (byte) (c >= 0x20 && c < 0x7F ? c : '?');
        }
        return new String(b, StandardCharsets.US_ASCII).trim();
    }

    /** True when the ROM ID bytes are all 0xFF: erased flash. */
    public static boolean isBlank(byte[] data, int at, int length) {
        for (int i = 0; i < length; i++) {
            if ((data[at + i] & 0xFF) != 0xFF) return false;
        }
        return true;
    }

    /**
     * What keeps this image from being written, as sentences; empty when
     * nothing does. Checked: the size, the word sum, the reset words the
     * boot loader starts, and the "programmed" marker.
     */
    public static List<String> problems(byte[] image) {
        List<String> problems = new ArrayList<String>();
        if (image == null || image.length != IMAGE_SIZE) {
            problems.add(String.format("The image must be %d bytes, not %d.",
                    IMAGE_SIZE, image == null ? 0 : image.length));
            return problems;
        }
        int sum = wordSum(image);
        if (sum != CHECKSUM_TARGET) {
            problems.add(String.format("The checksum is wrong: the word sum is 0x%04X, not 0x%04X (use Fix checksum).",
                    sum, CHECKSUM_TARGET));
        }
        // the reset words: banks 0x0220, the start address, stack 0x08F6, IZ 0
        int pc = ((image[2] & 0xFF) << 8) | (image[3] & 0xFF);
        boolean resetOk = (image[0] & 0xFF) == 0x02 && (image[1] & 0xFF) == 0x20
                && (image[4] & 0xFF) == 0x08 && (image[5] & 0xFF) == 0xF6
                && image[6] == 0 && image[7] == 0
                && pc % 2 == 0 && pc >= 0x220
                && !((image[pc] & 0xFF) == 0xFF && (image[pc + 1] & 0xFF) == 0xFF);
        if (!resetOk) {
            problems.add(String.format("The reset words are %02X%02X %02X%02X %02X%02X %02X%02X, not 0220 <start> 08F6 0000"
                    + " with an even start address from 0x0220 that holds code.",
                    image[0], image[1], image[2], image[3], image[4], image[5], image[6], image[7]));
        }
        int m = fileOffset(MARKER_ADDRESS);
        if (image[m] != image[m + 1] || (image[m] & 0xFF) == 0xFF) {
            problems.add(String.format("The \"programmed\" marker at 0x%05X is %02X %02X, not two equal bytes other than FF:"
                    + " the boot loader would not start the application.", MARKER_ADDRESS, image[m], image[m + 1]));
        }
        return problems;
    }
}
