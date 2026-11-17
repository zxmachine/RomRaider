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
 * What a flash session reports while it works. Called on the session's
 * thread, not the event thread.
 */
public interface FlashListener {
    /** A line for the log. */
    void log(String line);

    /** Progress of the current task: done of total units. */
    void progress(String task, int done, int total);

    /** Something the user must do at the car now, such as cycling the ignition. */
    void instruction(String text);

    /** The battery voltage the adapter measured. */
    void battery(double volts);

    /** A listener that ignores everything. */
    FlashListener NONE = new FlashListener() {
        @Override public void log(String line) { }
        @Override public void progress(String task, int done, int total) { }
        @Override public void instruction(String text) { }
        @Override public void battery(double volts) { }
    };
}
