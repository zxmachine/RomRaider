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

/**
 * How a flashing kernel for the 16-bit Denso boot loader is sent and spoken
 * to. Two kernels of the same design are in use: they share the commands and
 * the frame format, and differ in the reply code, the baud rate and the
 * programming-voltage window they accept. The kernel itself is the user's
 * own file; a profile only says how to handle it.
 */
public final class KernelProfile {
    /** How the two signature bytes at offset 2 of the upload get there. */
    public enum Signature {
        /** The file already holds them (a RAM image of the kernel); they are sent as they are. */
        IN_FILE,
        /** The host puts them in place of the file's bytes 2 and 3 after scrambling the rest. */
        SET_BY_HOST
    }

    /** Kernels whose replies are the request code + 0x80, at about 39.4 kbaud (ECUFlash's). */
    public static final KernelProfile REPLY_80 = new KernelProfile("reply80",
            "ECUFlash-style kernel (reply = request + 0x80, 39.4 kbaud)",
            39400, 0x80, 0x247, 0x29A, 1, Signature.IN_FILE);

    /** Kernels whose replies are the request code + 0x40, at 62.5 kbaud (FastECU's). */
    public static final KernelProfile REPLY_40 = new KernelProfile("reply40",
            "FastECU-style kernel (reply = request + 0x40, 62.5 kbaud)",
            62500, 0x40, 0x23A, 0x276, 16, Signature.SET_BY_HOST);

    private static final List<KernelProfile> ALL =
            Collections.unmodifiableList(Arrays.asList(REPLY_80, REPLY_40));

    /** The boot loader's own baud rate for the handshake and the upload. */
    public static final int LOADER_BAUD = 9600;
    /** The two bytes the boot loader checks at offset 2 of the upload (sent unscrambled). */
    public static final int SIGNATURE = 0x3941;
    /** Upload scrambling: each byte goes out as (b XOR 0x55) + 0x10. */
    public static final int SCRAMBLE_XOR = 0x55;
    public static final int SCRAMBLE_ADD = 0x10;
    /** The RAM address the boot loader loads to and starts. */
    public static final int LOAD_ADDRESS = 0x20000;
    /** The largest kernel that does not run into the boot loader's own variables. */
    public static final int MAX_KERNEL_SIZE = 0x1000;

    private final String id;
    private final String name;
    private final int baud;
    private final int replyOffset;
    private final int vppMin;
    private final int vppMax;
    private final int padTo;
    private final Signature signature;

    public KernelProfile(String id, String name, int baud, int replyOffset,
            int vppMin, int vppMax, int padTo, Signature signature) {
        this.id = id;
        this.name = name;
        this.baud = baud;
        this.replyOffset = replyOffset;
        this.vppMin = vppMin;
        this.vppMax = vppMax;
        this.padTo = padTo;
        this.signature = signature;
    }

    public static List<KernelProfile> all() {
        return ALL;
    }

    /** The profile with this id, or the first one. */
    public static KernelProfile byId(String id) {
        for (KernelProfile p : ALL) {
            if (p.id.equalsIgnoreCase(id == null ? "" : id)) return p;
        }
        return REPLY_80;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    /** The kernel's baud rate on the K-line. */
    public int getBaud() { return baud; }
    /** Added to a request code to make the reply code. */
    public int getReplyOffset() { return replyOffset; }
    /** Lowest raw programming-voltage reading the kernel accepts. */
    public int getVppMin() { return vppMin; }
    /** Highest raw programming-voltage reading the kernel accepts. */
    public int getVppMax() { return vppMax; }
    /** The upload is padded to a multiple of this many bytes. */
    public int getPadTo() { return padTo; }
    public Signature getSignature() { return signature; }

    /** The reply code for a request code. */
    public int reply(int request) {
        return (request + replyOffset) & 0xFF;
    }

    @Override
    public String toString() {
        return name;
    }
}
