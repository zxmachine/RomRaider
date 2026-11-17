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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * A flashing kernel, the user's own file, and the profile that says how to
 * send it. It builds the boot loader's upload frame.
 */
public final class Kernel {
    private final byte[] image;
    private final KernelProfile profile;
    private final String source;

    public Kernel(byte[] image, KernelProfile profile, String source) throws FlashException {
        if (image == null || image.length < 4) {
            throw new FlashException("The kernel file is empty or too short.",
                    "Choose the kernel file in the Flash view.");
        }
        if (image.length > KernelProfile.MAX_KERNEL_SIZE) {
            throw new FlashException(String.format(
                    "The kernel file has %d bytes; the boot loader takes at most %d.",
                    image.length, KernelProfile.MAX_KERNEL_SIZE),
                    "Check that the kernel file is the kernel's RAM image and not something else.");
        }
        if (profile.getSignature() == KernelProfile.Signature.IN_FILE
                && word(image, 2) != KernelProfile.SIGNATURE) {
            throw new FlashException(String.format(
                    "The kernel file has 0x%04X at offset 2, not 0x%04X: it is not a RAM image for the profile \"%s\".",
                    word(image, 2), KernelProfile.SIGNATURE, profile.getName()),
                    "Choose the other kernel profile, or the right kernel file.");
        }
        this.image = image.clone();
        this.profile = profile;
        this.source = source == null ? "" : source;
    }

    /** Reads the kernel from a file. */
    public static Kernel load(File file, KernelProfile profile) throws FlashException {
        if (file == null || !file.isFile()) {
            throw new FlashException("No kernel file" + (file == null ? "" : " at " + file) + ".",
                    "Choose your kernel file in the Flash view (see docs/flashing.md, \"The kernel file\").");
        }
        try {
            return new Kernel(Files.readAllBytes(file.toPath()), profile, file.getPath());
        } catch (IOException e) {
            throw new FlashException("Cannot read the kernel file " + file + ": " + e.getMessage(),
                    "Check the file and try again.");
        }
    }

    public KernelProfile getProfile() {
        return profile;
    }

    public int size() {
        return image.length;
    }

    public String getSource() {
        return source;
    }

    /** The kernel as the boot loader leaves it in RAM: padded, with the signature in place. */
    public byte[] ramImage() {
        int pad = profile.getPadTo();
        int length = (image.length + pad - 1) / pad * pad;
        byte[] ram = new byte[length];
        System.arraycopy(image, 0, ram, 0, image.length);
        ram[2] = (byte) (KernelProfile.SIGNATURE >> 8);
        ram[3] = (byte) KernelProfile.SIGNATURE;
        return ram;
    }

    /**
     * The upload frame: 'S', bank, address high byte, a 24-bit length, the
     * scrambled data (offsets 2 and 3 plain) and a checksum byte that makes
     * the frame's byte sum zero.
     */
    public byte[] uploadFrame() {
        byte[] ram = ramImage();
        int n = ram.length;
        byte[] frame = new byte[6 + n + 1];
        frame[0] = 0x53;
        frame[1] = (byte) (KernelProfile.LOAD_ADDRESS >> 16);
        frame[2] = (byte) (KernelProfile.LOAD_ADDRESS >> 8);
        frame[3] = (byte) (n >> 16);
        frame[4] = (byte) (n >> 8);
        frame[5] = (byte) n;
        for (int i = 0; i < n; i++) {
            frame[6 + i] = (i == 2 || i == 3) ? ram[i] : scramble(ram[i]);
        }
        int sum = 0;
        for (int i = 0; i < frame.length - 1; i++) sum += frame[i] & 0xFF;
        frame[frame.length - 1] = (byte) (0x100 - (sum & 0xFF));
        return frame;
    }

    static byte scramble(byte b) {
        return (byte) (((b & 0xFF) ^ KernelProfile.SCRAMBLE_XOR) + KernelProfile.SCRAMBLE_ADD);
    }

    static byte unscramble(byte b) {
        return (byte) (((b & 0xFF) - KernelProfile.SCRAMBLE_ADD) ^ KernelProfile.SCRAMBLE_XOR);
    }

    private static int word(byte[] b, int at) {
        return ((b[at] & 0xFF) << 8) | (b[at + 1] & 0xFF);
    }
}
