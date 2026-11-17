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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.romraider.flash.Hc16Y5Layout;
import com.romraider.flash.Wrx02Method;
import com.romraider.flash.sim.SimulatedEcu;

/** The Flash view's rules that need no window: the image source, the battery minimum, closing while busy. */
public class FlashUiRulesTest {

    /** A ROM held elsewhere (as the editor holds one) that changes between takes. */
    private static final class LiveRom implements ImageSource.Live {
        byte[] bytes;
        @Override public byte[] bytes() { return bytes; }
        @Override public String name() { return "the editor (test.bin)"; }
        @Override public String flashMethod() { return "wrx02"; }
        @Override public String memoryModel() { return "68HC16Y5"; }
    }

    @Test
    public void theEditorsImageIsTakenAgainAtEachAction() {
        LiveRom rom = new LiveRom();
        rom.bytes = SimulatedEcu.makeImage(1, "TESTID01");
        ImageSource source = ImageSource.following(rom);
        byte[] first = source.take().bytes;
        rom.bytes[0x5000] ^= 0x55;                       // an edit in the editor after the first take
        byte[] second = source.take().bytes;
        assertFalse("the edit is in the next take", first[0x5000] == second[0x5000]);
        second[0x6000] ^= 1;                            // a take is a copy: changing it leaves the ROM alone
        assertFalse(rom.bytes[0x6000] == second[0x6000]);
        rom.bytes = null;                               // the ROM was closed
        assertNull(source.take());
    }

    @Test
    public void fixChecksumFixesTheImageWhereItLives() {
        LiveRom rom = new LiveRom();
        rom.bytes = SimulatedEcu.makeImage(2, "TESTID01");
        rom.bytes[0x100] ^= 1;
        ImageSource editor = ImageSource.following(rom);
        assertTrue(editor.fixChecksum(new Wrx02Method()));
        assertEquals(Hc16Y5Layout.CHECKSUM_TARGET, Hc16Y5Layout.wordSum(rom.bytes));

        byte[] file = SimulatedEcu.makeImage(3, "TESTID01");
        file[0x100] ^= 1;
        ImageSource fixed = ImageSource.fixed(file, "a.bin", null, null);
        assertTrue(fixed.fixChecksum(new Wrx02Method()));
        assertEquals(Hc16Y5Layout.CHECKSUM_TARGET, Hc16Y5Layout.wordSum(fixed.take().bytes));
        byte[] a = fixed.take().bytes;
        a[0] ^= 1;
        assertArrayEquals("a fixed image does not change with its takes", fixed.take().bytes, fixed.take().bytes);
        assertFalse(a[0] == fixed.take().bytes[0]);
    }

    @Test
    public void theBatteryMinimumMustBeANumber() {
        assertEquals(12.0, FlashPanel.parseMinBattery("12.0"), 0);
        assertEquals(11.0, FlashPanel.parseMinBattery("9"), 0);
        assertTrue(Double.isNaN(FlashPanel.parseMinBattery("NaN")));
        assertTrue(Double.isNaN(FlashPanel.parseMinBattery("Infinity")));
        assertTrue(Double.isNaN(FlashPanel.parseMinBattery("twelve")));
        assertTrue(Double.isNaN(FlashPanel.parseMinBattery(null)));
    }

    @Test
    public void theWindowDoesNotCloseWhileTheEcuIsReadOrWritten() {
        assertNull(FlashView.busy(false, false));
        assertNotNull(FlashView.busy(true, false));
        assertNotNull(FlashView.busy(false, true));
        assertTrue(FlashView.BUSY.contains("partly written"));
        assertNull("a view that is not started says nothing", new FlashView().busyReason());
    }
}
