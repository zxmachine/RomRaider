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

package com.romraider.flash.ui;

/**
 * Where the image to compare and write comes from. An image from a file is
 * fixed; the editor's image is taken again each time, so that Compare and
 * Write always use what the editor holds at that moment.
 */
interface ImageSource {
    /** The image as it is now, a copy of its own; null when there is none. */
    Snapshot take();

    /** Repairs the checksum where the image lives (the editor's ROM or the file's copy); true when it changed. */
    boolean fixChecksum(com.romraider.flash.FlashMethod method);

    /** An image at one moment, with what its definition says about it. */
    final class Snapshot {
        final byte[] bytes;
        final String name;
        final String flashMethod;
        final String memoryModel;

        Snapshot(byte[] bytes, String name, String flashMethod, String memoryModel) {
            this.bytes = bytes;
            this.name = name;
            this.flashMethod = flashMethod;
            this.memoryModel = memoryModel;
        }
    }

    /** A fixed image, such as one read from a file. */
    static ImageSource fixed(final byte[] data, final String name, final String flashMethod, final String memoryModel) {
        final byte[] held = data.clone();
        return new ImageSource() {
            @Override
            public Snapshot take() {
                return new Snapshot(held.clone(), name, flashMethod, memoryModel);
            }

            @Override
            public boolean fixChecksum(com.romraider.flash.FlashMethod method) {
                return method.fixChecksum(held);
            }
        };
    }

    /** The live bytes of an image held elsewhere (the editor's ROM), taken again at each call. */
    interface Live {
        /** The live bytes, or null when there is no image now. */
        byte[] bytes();
        String name();
        String flashMethod();
        String memoryModel();
    }

    /** An image that follows a live one: each take copies it as it is then. */
    static ImageSource following(final Live live) {
        return new ImageSource() {
            @Override
            public Snapshot take() {
                byte[] b = live.bytes();
                if (b == null) return null;
                return new Snapshot(b.clone(), live.name(), live.flashMethod(), live.memoryModel());
            }

            @Override
            public boolean fixChecksum(com.romraider.flash.FlashMethod method) {
                byte[] b = live.bytes();
                return b != null && method.fixChecksum(b);
            }
        };
    }
}
