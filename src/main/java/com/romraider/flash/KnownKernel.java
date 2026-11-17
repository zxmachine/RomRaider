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
import java.util.zip.CRC32;

/**
 * A flashing kernel whose code has been read and checked: it erases and
 * programs only the application flash, never the boot loader's own block.
 * <p>
 * The kernel runs from RAM with the whole flash in reach. Nothing in the chip
 * protects the boot loader's block, and a kernel can erase it (FastECU's
 * v0.02 does, on purpose). An ECU whose boot loader is erased no longer starts
 * and can't be written over the K-line again. So a session uploads only a
 * kernel it knows, unless "Allow an unknown kernel" is ticked, and checks the
 * kernel's code in the ECU's RAM before using it: that also catches an upload
 * damaged on the way, which the boot loader's one-byte checksum can miss.
 * <p>
 * A kernel is known by its file's length and the CRC-32 of its code as the
 * boot loader leaves it in RAM (from 0x20000, with the signature in place).
 * The code is the part that does not change while the kernel runs; bytes after
 * it may be the kernel's own variables.
 */
public final class KnownKernel {
    /** ECUFlash's wrx02 kernel, the RAM image (0x20000-0x20663; the last 4 bytes are variables). */
    public static final KnownKernel ECUFLASH_V106 = new KnownKernel(
            "OpenECU Subaru HC16 Kernel V1.06 (ECUFlash)", KernelProfile.REPLY_80, 1636, 0x660, 0x34D2A661L);
    /** FastECU's kernel v0.01, the binary FastECU ships (the same code as ECUFlash's). */
    public static final KnownKernel FASTECU_V001 = new KnownKernel(
            "FastECU Subaru HC16 Kernel v0.01", KernelProfile.REPLY_40, 1632, 0x660, 0x98D28618L);

    private static final List<KnownKernel> ALL =
            Collections.unmodifiableList(Arrays.asList(ECUFLASH_V106, FASTECU_V001));

    private final String name;
    private final KernelProfile profile;
    private final int fileLength;
    private final int codeLength;
    private final long codeCrc;

    /**
     * @param name       what the kernel is called in messages
     * @param profile    the profile it is sent and spoken to with
     * @param fileLength the length of its file
     * @param codeLength how many bytes from the load address are code that does not change
     * @param codeCrc    the CRC-32 of those bytes as they are in RAM
     */
    public KnownKernel(String name, KernelProfile profile, int fileLength, int codeLength, long codeCrc) {
        this.name = name;
        this.profile = profile;
        this.fileLength = fileLength;
        this.codeLength = codeLength;
        this.codeCrc = codeCrc;
    }

    /** A kernel file as a known kernel (tests, and a kernel checked by other means). */
    public static KnownKernel of(String name, Kernel kernel, int codeLength) {
        return new KnownKernel(name, kernel.getProfile(), kernel.size(), codeLength,
                crc(kernel.ramImage(), codeLength));
    }

    /** The kernels RomRaider knows. */
    public static List<KnownKernel> all() {
        return ALL;
    }

    /** The known kernel this file is, whatever profile was chosen for it; null if none. */
    public static KnownKernel find(Kernel kernel, List<KnownKernel> known) {
        byte[] ram = kernel.ramImage();
        for (KnownKernel k : known) {
            if (kernel.size() == k.fileLength && ram.length >= k.codeLength && crc(ram, k.codeLength) == k.codeCrc) {
                return k;
            }
        }
        return null;
    }

    /** The CRC-32 of the first bytes of a RAM image. */
    static long crc(byte[] ram, int length) {
        CRC32 c = new CRC32();
        c.update(ram, 0, Math.min(length, ram.length));
        return c.getValue();
    }

    public String getName() { return name; }
    public KernelProfile getProfile() { return profile; }
    public int getFileLength() { return fileLength; }
    /** How many bytes from the load address are code that does not change while the kernel runs. */
    public int getCodeLength() { return codeLength; }
    /** The CRC-32 of the code in RAM. */
    public long getCodeCrc() { return codeCrc; }

    @Override
    public String toString() {
        return name;
    }
}
