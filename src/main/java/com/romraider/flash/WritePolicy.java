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
import java.util.List;

/**
 * The rules a write must pass, each one a method of its own so that it can
 * be tested alone. A session calls them in this order: the kernel file, the
 * image and the settings before anything is sent; the battery; the ECU's ROM
 * ID once the kernel answers; the typed confirmation once the blocks are
 * known; and the address of every erase and program command.
 */
public final class WritePolicy {
    /** How to switch writing on, for messages and the user interface. */
    public static final String HOW_TO_ENABLE =
            "Writing is switched off. To switch it on, tick \"Allow writing to the ECU\" under Settings in the Flash view."
            + " Compare (a dry run) works without it.";

    private WritePolicy() {
    }

    /**
     * The checks that need no ECU: the settings switch, the definition and
     * the image. Called before a session opens, and again by the session.
     */
    public static void preflight(FlashMethod method, FlashOptions options, WriteRequest request) throws FlashException {
        checkWritingEnabled(options, request);
        checkDefinition(method, request);
        checkImage(method, request.getImage());
    }

    /** Writing must be switched on in the settings (a dry run needs nothing). */
    public static void checkWritingEnabled(FlashOptions options, WriteRequest request) throws FlashException {
        if (!request.isDryRun() && !options.writingEnabled) {
            throw new FlashException("Writing is switched off.", HOW_TO_ENABLE);
        }
    }

    /** The image's definition must name this method's flash method and memory model, when it names any. */
    public static void checkDefinition(FlashMethod method, WriteRequest request) throws FlashException {
        String fm = request.getFlashMethod();
        String mm = request.getMemoryModel();
        if ((fm != null || mm != null) && !method.supports(fm, mm)) {
            throw new FlashException(String.format(
                    "The image's definition names flash method \"%s\" and memory model \"%s\"; this is %s.",
                    fm, mm, method.getName()),
                    "Write this image with a tool for its own ECU type.");
        }
    }

    /** The image must have the right size, checksum, reset words and marker. */
    public static void checkImage(FlashMethod method, byte[] image) throws FlashException {
        List<String> problems = method.imageProblems(image);
        if (!problems.isEmpty()) {
            StringBuilder sb = new StringBuilder("The image can't be written: ");
            for (String p : problems) sb.append(p).append(' ');
            throw new FlashException(sb.toString().trim(),
                    "Fix the image first. A wrong checksum: press Fix checksum (or save the ROM in the editor"
                    + " with its checksum module). Anything else: the file is not a whole image for this ECU.");
        }
    }

    /**
     * The battery must hold at least the minimum. A voltage that can't be
     * read, or a minimum that is not a number, refuses the write.
     */
    public static void checkBattery(double volts, double minimum) throws FlashException {
        if (Double.isNaN(minimum) || Double.isInfinite(minimum)) {
            throw new FlashException("The battery minimum is not a number.",
                    "Enter the battery minimum in volts (for example 12.0) under Settings in the Flash view.");
        }
        if (Double.isNaN(volts)) {
            throw new FlashException("The adapter can't read the battery voltage, so a write can't be checked.",
                    "Use an adapter that reports the battery voltage.");
        }
        if (volts < minimum) {
            throw new FlashException(String.format(
                    "The battery is at %.2f V, below the %.2f V a write needs.", volts, minimum),
                    "Charge the battery or connect a charger or power supply, then try again.");
        }
    }

    /**
     * The kernel file must be a kernel RomRaider knows, sent with its own
     * profile, unless the user allowed an unknown one. Checked before anything
     * is sent, for every session (a read uploads the kernel too).
     *
     * @return the known kernel, or null for an unknown kernel the user allowed
     */
    public static KnownKernel checkKernel(Kernel kernel, List<KnownKernel> known, boolean allowUnknown)
            throws FlashException {
        KnownKernel k = KnownKernel.find(kernel, known);
        if (k != null) {
            if (k.getProfile() != kernel.getProfile()) {
                throw new FlashException(String.format(
                        "The kernel file is the %s, a kernel for the profile \"%s\", not \"%s\".",
                        k.getName(), k.getProfile().getName(), kernel.getProfile().getName()),
                        "Choose that kernel profile and try again.");
            }
            return k;
        }
        if (allowUnknown) return null;
        StringBuilder names = new StringBuilder();
        for (KnownKernel n : known) names.append(names.length() == 0 ? "" : "; ").append(n.getName());
        throw new FlashException(String.format(
                "The kernel file %s is not a kernel RomRaider knows (%d bytes, CRC-32 %08X as loaded, profile \"%s\").",
                kernel.getSource(), kernel.size(), KnownKernel.crc(kernel.ramImage(), Integer.MAX_VALUE),
                kernel.getProfile().getName()),
                "Nothing was sent. The kernel runs with the whole flash in reach, the boot loader's own block"
                + " included, and an ECU whose boot loader is erased can't be written again over the K-line."
                + " Choose a known kernel (" + (names.length() == 0 ? "none" : names) + ") or check the kernel"
                + " profile. To use this kernel all the same, tick \"Allow an unknown kernel\" (off at every start).");
    }

    /**
     * The ECU must hold the image's ROM ID, unless the user allowed another
     * one, or its ROM ID is blank (an earlier write stopped part way).
     */
    public static void checkRomId(EcuIdentity ecu, String imageRomId, boolean allowOther) throws FlashException {
        if (ecu.isBlank() || allowOther) return;
        if (!ecu.getRomId().equals(imageRomId)) {
            throw new FlashException(String.format(
                    "The ECU holds ROM ID %s, the image has %s.", ecu.getRomId(), imageRomId),
                    "Check that this image is meant for this ECU. To write it all the same,"
                    + " tick \"Allow another ROM ID\" and write again.");
        }
    }

    /** The phrase the user types to confirm a write. */
    public static String confirmationPhrase(String romId) {
        return "WRITE " + romId;
    }

    /** The typed text must be the phrase exactly (spaces around it aside). */
    public static void checkConfirmation(String typed, String romId) throws FlashException {
        if (typed == null || !typed.trim().equals(confirmationPhrase(romId))) {
            throw new FlashException("The write was not confirmed.",
                    "To write, type " + confirmationPhrase(romId) + " exactly when asked.");
        }
    }

    /** The programming voltage reading must be inside the kernel's window. */
    public static void checkProgrammingVoltage(int raw, KernelProfile profile) throws FlashException {
        if (raw < profile.getVppMin() || raw > profile.getVppMax()) {
            throw new FlashException(String.format(
                    "The programming voltage reads 0x%04X, outside the kernel's window 0x%04X-0x%04X.",
                    raw, profile.getVppMin(), profile.getVppMax()),
                    "Check that the adapter can put 12 V on the programming pin (pin 11) and that the car's"
                    + " flash connector carries it to the ECU; then cycle the ignition and write again.");
        }
    }

    /** Erase and program may only touch application flash: never the boot block. */
    public static void checkWritable(int address, int length) {
        if (!Hc16Y5Layout.isApplicationFlash(address, length)) {
            throw new IllegalStateException(String.format(
                    "refusing to erase or program 0x%05X-0x%05X: not application flash", address, address + length - 1));
        }
    }

    /**
     * The order of writing: the blocks that differ in address order, then
     * the marker block, which is written whenever any block is, even when it
     * does not differ. It is erased first and programmed last, so that while
     * any block is half written the "programmed" marker is erased and the
     * boot loader does not start the program. (The reference host writes the
     * marker block only when it differs; this costs one more erase of that
     * block per write.) Empty when nothing differs.
     */
    public static List<FlashBlock> writeOrder(List<FlashBlock> differing) {
        List<FlashBlock> order = new ArrayList<FlashBlock>();
        if (differing.isEmpty()) return order;
        for (FlashBlock b : differing) {
            if (b.getAddress() != Hc16Y5Layout.MARKER_BLOCK) order.add(b);
        }
        order.add(Hc16Y5Layout.markerBlock());
        return order;
    }
}
