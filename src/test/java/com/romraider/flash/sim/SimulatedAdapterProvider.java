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

package com.romraider.flash.sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.romraider.flash.KernelProfile;
import com.romraider.flash.Timing;
import com.romraider.flash.ui.FlashAdapterProvider;
import com.romraider.io.j2534.api.J2534;

/**
 * The simulated ECU as an adapter of the Flash view, for trying the view
 * without a car. Test code: it is never in a release build. Start RomRaider
 * with the test classes on the class path and
 *
 * <pre>
 * -Dromraider.flash.testAdapter=com.romraider.flash.sim.SimulatedAdapterProvider
 * -Dromraider.flash.sim.image=&lt;160 KB image the ECU holds&gt;
 * -Dromraider.flash.sim.kernel=&lt;the kernel file the upload must carry&gt;
 * -Dromraider.flash.sim.profile=reply80|reply40
 * </pre>
 *
 * One ECU serves every session, in real time.
 */
public final class SimulatedAdapterProvider implements FlashAdapterProvider {
    private static SimulatedEcu ecu;

    @Override
    public String getName() {
        return "Simulated ECU (test only)";
    }

    @Override
    public synchronized J2534 create() {
        synchronized (SimulatedAdapterProvider.class) {
            if (ecu == null) {
                try {
                    byte[] image = Files.readAllBytes(Paths.get(System.getProperty("romraider.flash.sim.image")));
                    byte[] kernel = Files.readAllBytes(Paths.get(System.getProperty("romraider.flash.sim.kernel")));
                    KernelProfile profile = KernelProfile.byId(System.getProperty("romraider.flash.sim.profile", "reply80"));
                    ecu = new SimulatedEcu(Timing.SYSTEM, image, profile, kernel);
                } catch (IOException e) {
                    throw new IllegalStateException("simulated ECU: " + e.getMessage(), e);
                }
            }
            return ecu;
        }
    }

    /** The one simulated ECU, or null before the first session. */
    public static synchronized SimulatedEcu ecu() {
        synchronized (SimulatedAdapterProvider.class) {
            return ecu;
        }
    }
}
