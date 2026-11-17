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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

/**
 * The flash sessions that are open, and what happens to them when the
 * program ends. The user interface refuses to close while one is busy; for
 * every other way out (a shutdown of the computer, Ctrl+C, another part of
 * the program calling System.exit) a shutdown hook asks each open session to
 * stop at its next safe point (during a write: after the command that runs,
 * with flash disable sent and the programming voltage off), waits for it, and
 * if it does not end in time sends flash disable and switches every pin off
 * itself.
 */
public final class ActiveSessions {
    private static final Logger LOGGER = Logger.getLogger(ActiveSessions.class);
    /** How long the shutdown hook waits for a session to stop by itself. */
    public static final long SHUTDOWN_WAIT_MILLIS = 20000;
    private static final Set<FlashSession> OPEN = new LinkedHashSet<FlashSession>();
    private static boolean hookInstalled;

    private ActiveSessions() {
    }

    static synchronized void register(FlashSession s) {
        OPEN.add(s);
        if (!hookInstalled) {
            hookInstalled = true;
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                    @Override
                    public void run() {
                        shutdown(SHUTDOWN_WAIT_MILLIS);
                    }
                }, "flash shutdown"));
            } catch (IllegalStateException e) {
                // the program is already ending
            }
        }
    }

    static synchronized void unregister(FlashSession s) {
        OPEN.remove(s);
    }

    /** True while a session is open. */
    public static synchronized boolean isBusy() {
        return !OPEN.isEmpty();
    }

    private static synchronized List<FlashSession> open() {
        return new ArrayList<FlashSession>(OPEN);
    }

    /**
     * Stops every open session: asks it to stop, waits up to the given time
     * (real time) for it to close, and then stops it at once (flash disable,
     * every pin off). Returns the number of sessions stopped at once.
     */
    public static int shutdown(long waitMillis) {
        List<FlashSession> sessions = open();
        for (FlashSession s : sessions) s.shutdownRequested();
        long end = System.currentTimeMillis() + waitMillis;
        int forced = 0;
        for (FlashSession s : sessions) {
            while (!s.isClosed() && System.currentTimeMillis() < end) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
            }
            if (!s.isClosed()) {
                LOGGER.warn("Flash session did not stop in time: flash disable and pins off now");
                s.emergencyStop();
                forced++;
            }
        }
        return forced;
    }
}
