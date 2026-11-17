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
 * A way of reading and writing an ECU, chosen by a ROM definition's
 * flashmethod and memmodel (as ECUFlash names them). A method opens a
 * session, which talks to one ECU through one adapter.
 */
public interface FlashMethod {
    /** A name for the user. */
    String getName();

    /** True when this method reads and writes ROMs of this flash method and memory model. */
    boolean supports(String flashMethod, String memoryModel);

    /** The size of a whole image. */
    int getImageSize();

    /** The erase blocks, in address order. */
    List<FlashBlock> getBlocks();

    /**
     * What keeps an image from being written by this method, as sentences;
     * empty when nothing does.
     */
    List<String> imageProblems(byte[] image);

    /** Repairs the image's checksum in place; true when it changed. */
    boolean fixChecksum(byte[] image);

    /** The ROM ID in an image. */
    String romId(byte[] image);

    /**
     * A key of the image's content, the same that a compare of an ECU with
     * this content gives: under it a whole read counts as that ECU's backup.
     */
    String contentKey(byte[] image);

    /** Connects to the ECU: enters its boot loader and starts the kernel. */
    FlashSession open(FlashContext context) throws FlashException;
}
