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

/**
 * A connection to one ECU running a flashing kernel. Closing it switches
 * off every pin the session used and closes the adapter; the ECU stays in
 * the kernel until its ignition is cycled.
 */
public interface FlashSession extends AutoCloseable {
    /** The kernel's version and the ROM ID the ECU holds. */
    EcuIdentity identify() throws FlashException;

    /** Reads the whole image, every chunk checked against the kernel's CRC. */
    byte[] read() throws FlashException;

    /** Compares every erase block of the ECU with the image, by CRC. */
    CompareResult compare(byte[] image) throws FlashException;

    /**
     * Writes the blocks that differ, after every safety check; with a dry
     * run it only compares.
     */
    WriteResult write(WriteRequest request) throws FlashException;

    /** Compares again after a write; the same as {@link #compare}. */
    CompareResult verify(byte[] image) throws FlashException;

    /**
     * From another thread: the program is ending. The session stops at its
     * next safe point; a write stops after the command that is running, with
     * flash disable sent and the programming voltage off.
     */
    void shutdownRequested();

    /**
     * From another thread, when the session did not stop in time: flash
     * disable if it was enabled, every pin off, adapter closed.
     */
    void emergencyStop();

    /** True once the session is closed. */
    boolean isClosed();

    @Override
    void close();
}
