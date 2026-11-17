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

/**
 * ECUFlash's flash method "wrx02" on memory model "68HC16Y5": the 16-bit
 * Subaru Denso ECUs whose boot loader takes a RAM kernel over the K-line.
 * See {@link Wrx02Session} for the steps.
 */
public final class Wrx02Method implements FlashMethod {
    public static final String FLASH_METHOD = "wrx02";

    @Override
    public String getName() {
        return "wrx02 / " + Hc16Y5Layout.MEMORY_MODEL;
    }

    @Override
    public boolean supports(String flashMethod, String memoryModel) {
        return FLASH_METHOD.equalsIgnoreCase(flashMethod == null ? "" : flashMethod.trim())
                && Hc16Y5Layout.MEMORY_MODEL.equalsIgnoreCase(memoryModel == null ? "" : memoryModel.trim());
    }

    @Override
    public int getImageSize() {
        return Hc16Y5Layout.IMAGE_SIZE;
    }

    @Override
    public List<FlashBlock> getBlocks() {
        return Hc16Y5Layout.blocks();
    }

    @Override
    public List<String> imageProblems(byte[] image) {
        return Hc16Y5Layout.problems(image);
    }

    @Override
    public boolean fixChecksum(byte[] image) {
        if (image == null || image.length != Hc16Y5Layout.IMAGE_SIZE) return false;
        return Hc16Y5Layout.fixChecksum(image);
    }

    @Override
    public String romId(byte[] image) {
        return Hc16Y5Layout.romId(image);
    }

    @Override
    public String contentKey(byte[] image) {
        return Hc16Y5Layout.contentKey(image);
    }

    @Override
    public FlashSession open(FlashContext context) throws FlashException {
        return Wrx02Session.open(this, context);
    }
}
