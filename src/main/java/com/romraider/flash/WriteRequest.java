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

import java.util.List;

/** An image to write, where it comes from, and how the user confirms the write. */
public final class WriteRequest {
    /**
     * Asks the user to type the confirmation phrase once the blocks to write
     * are known; returns what was typed, or null when the user said no. The
     * image is described by its ROM ID, CRC-32 and checksum, so that the user
     * sees which image is about to be written.
     */
    public interface Confirmer {
        String confirm(List<FlashBlock> blocks, String romId, String imageDescription, String phrase);
    }

    private final byte[] image;
    private final String flashMethod;
    private final String memoryModel;
    private final boolean dryRun;
    private final boolean allowOtherRomId;
    private final Confirmer confirmer;

    /**
     * @param image           the whole image
     * @param flashMethod     the definition's flashmethod, or null for an image without one
     * @param memoryModel     the definition's memmodel, or null
     * @param dryRun          only compare
     * @param allowOtherRomId write even when the ECU holds another ROM ID
     * @param confirmer       asks for the typed confirmation
     */
    public WriteRequest(byte[] image, String flashMethod, String memoryModel, boolean dryRun,
            boolean allowOtherRomId, Confirmer confirmer) {
        this.image = image == null ? null : image.clone();
        this.flashMethod = flashMethod;
        this.memoryModel = memoryModel;
        this.dryRun = dryRun;
        this.allowOtherRomId = allowOtherRomId;
        this.confirmer = confirmer;
    }

    /** A compare only. */
    public static WriteRequest dryRun(byte[] image) {
        return new WriteRequest(image, null, null, true, true, null);
    }

    public byte[] getImage() { return image; }
    public String getFlashMethod() { return flashMethod; }
    public String getMemoryModel() { return memoryModel; }
    public boolean isDryRun() { return dryRun; }
    public boolean isAllowOtherRomId() { return allowOtherRomId; }
    public Confirmer getConfirmer() { return confirmer; }
}
