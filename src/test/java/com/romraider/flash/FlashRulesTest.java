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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.romraider.flash.sim.SimulatedEcu;
import com.romraider.maps.checksum.ChecksumWORDSUM16;

/** The memory layout, the image checks, the kernel upload and the write rules, without an ECU. */
public class FlashRulesTest {

    @Test
    public void layoutMapsAddressesAndBlocks() {
        assertEquals(0x1FFFF, Hc16Y5Layout.fileOffset(0x1FFFF));
        assertEquals(0x20000, Hc16Y5Layout.fileOffset(0x28000));
        assertEquals(0x2FFFF, Hc16Y5Layout.address(0x27FFF));
        List<FlashBlock> blocks = Hc16Y5Layout.blocks();
        assertEquals(10, blocks.size());
        assertEquals(0x28000, blocks.get(8).getAddress());
        assertEquals(0x20000, blocks.get(8).getFileOffset());
        assertEquals("FB10", Hc16Y5Layout.markerBlock().getName());
        try {
            Hc16Y5Layout.fileOffset(0x24000);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void theBootBlockIsNeverWritable() {
        assertTrue(Hc16Y5Layout.isApplicationFlash(0x2C000, 0x4000));
        assertFalse(Hc16Y5Layout.isApplicationFlash(0x60000, 0x200));
        assertFalse(Hc16Y5Layout.isApplicationFlash(0x1FE00, 0x400));    // runs into the gap
        assertFalse(Hc16Y5Layout.isApplicationFlash(0x20000, 0x200));
        try {
            WritePolicy.checkWritable(0x60000, 0x4000);
            fail();
        } catch (IllegalStateException expected) {
        }
        WritePolicy.checkWritable(0x00000, 0x4000);
    }

    @Test
    public void imageChecks() {
        byte[] img = SimulatedEcu.makeImage(3, "ABCD1234");
        assertTrue(Hc16Y5Layout.problems(img).isEmpty());
        // the content key: ten block CRCs; any change in any block changes it
        String key = Hc16Y5Layout.contentKey(img);
        assertEquals(80, key.length());
        byte[] other = img.clone();
        other[Hc16Y5Layout.fileOffset(0x2A000)] ^= 1;
        assertFalse(key.equals(Hc16Y5Layout.contentKey(other)));
        assertEquals("ABCD1234", Hc16Y5Layout.romId(img));

        assertEquals(1, Hc16Y5Layout.problems(new byte[100]).size());

        byte[] sum = img.clone();
        sum[0x100] ^= 0x10;
        assertTrue(Hc16Y5Layout.problems(sum).get(0).contains("checksum"));
        assertTrue(Hc16Y5Layout.fixChecksum(sum));
        assertTrue(Hc16Y5Layout.problems(sum).isEmpty());
        assertFalse(Hc16Y5Layout.fixChecksum(sum));

        byte[] marker = img.clone();
        marker[0x27FFE] = (byte) 0xFF;
        marker[0x27FFF] = (byte) 0xFF;
        Hc16Y5Layout.fixChecksum(marker);
        assertTrue(Hc16Y5Layout.problems(marker).get(0).contains("marker"));

        byte[] reset = img.clone();
        reset[3] = 0x01;      // an odd start address
        Hc16Y5Layout.fixChecksum(reset);
        assertTrue(Hc16Y5Layout.problems(reset).get(0).contains("reset words"));
    }

    @Test
    public void checksumModuleForTheEditor() {
        byte[] img = SimulatedEcu.makeImage(4, "ABCD1234");
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("type", "wordsum16");
        attrs.put("start", "0");
        attrs.put("end", "27FF4");
        attrs.put("correction", "27FF2");
        attrs.put("target", "5AA5");
        ChecksumWORDSUM16 cs = new ChecksumWORDSUM16();
        cs.configure(attrs);
        assertEquals(1, cs.getNumberOfChecksums());
        assertEquals(1, cs.validate(img));
        img[0x1234] ^= 0x40;
        assertEquals(0, cs.validate(img));
        assertEquals(1, cs.update(img));
        assertEquals(1, cs.validate(img));
        assertEquals(0, cs.update(img));
        assertEquals(0x5AA5, Hc16Y5Layout.wordSum(img));
    }

    @Test
    public void kernelUploadIsScrambledWithThePlainSignature() throws FlashException {
        byte[] file = SimulatedEcu.makeKernel(KernelProfile.REPLY_80, 7);
        Kernel k = new Kernel(file, KernelProfile.REPLY_80, "test");
        byte[] f = k.uploadFrame();
        assertEquals(0x53, f[0] & 0xFF);
        assertEquals(0x02, f[1] & 0xFF);
        assertEquals(0x00, f[2] & 0xFF);
        assertEquals(file.length, ((f[3] & 0xFF) << 16) | ((f[4] & 0xFF) << 8) | (f[5] & 0xFF));
        assertEquals(0x39, f[8] & 0xFF);
        assertEquals(0x41, f[9] & 0xFF);
        for (int i = 0; i < file.length; i++) {
            if (i == 2 || i == 3) continue;
            assertEquals(file[i], Kernel.unscramble(f[6 + i]));
        }
        int sum = 0;
        for (byte b : f) sum += b & 0xFF;
        assertEquals(0, sum & 0xFF);
    }

    @Test
    public void kernelProfilesDifferInSignatureAndPadding() throws FlashException {
        byte[] plain = SimulatedEcu.makeKernel(KernelProfile.REPLY_40, 8);   // no signature in the file
        try {
            new Kernel(plain, KernelProfile.REPLY_80, "test");
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("0x3941"));
        }
        Kernel k = new Kernel(plain, KernelProfile.REPLY_40, "test");
        assertEquals(0, k.ramImage().length % 16);
        assertEquals(0x39, k.ramImage()[2] & 0xFF);
        try {
            new Kernel(new byte[0x1001], KernelProfile.REPLY_40, "test");
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("at most"));
        }
        assertSame(KernelProfile.REPLY_40, KernelProfile.byId("reply40"));
        assertSame(KernelProfile.REPLY_80, KernelProfile.byId("nonsense"));
        assertEquals(0x81, KernelProfile.REPLY_80.reply(0x01));
        assertEquals(0x65, KernelProfile.REPLY_40.reply(0x25));
    }

    @Test
    public void writeRules() throws FlashException {
        List<FlashBlock> b = Hc16Y5Layout.blocks();
        List<FlashBlock> order = WritePolicy.writeOrder(Arrays.asList(b.get(9), b.get(1), b.get(4)));
        assertEquals(Arrays.asList(b.get(1), b.get(4), b.get(9)), order);
        // the marker block is written with any other block, last, even when it does not differ
        assertEquals(Arrays.asList(b.get(3), b.get(9)), WritePolicy.writeOrder(Arrays.asList(b.get(3))));
        assertTrue(WritePolicy.writeOrder(Arrays.<FlashBlock>asList()).isEmpty());

        WritePolicy.checkConfirmation(" WRITE ABCD1234 ", "ABCD1234");
        for (String wrong : new String[] {null, "", "write ABCD1234", "WRITE", "WRITE ABCD1235"}) {
            try {
                WritePolicy.checkConfirmation(wrong, "ABCD1234");
                fail(wrong);
            } catch (FlashException expected) {
            }
        }
        WritePolicy.checkBattery(12.0, 12.0);
        double[][] refused = {{11.9, 12.0}, {Double.NaN, 12.0}, {12.5, Double.NaN}, {12.5, Double.POSITIVE_INFINITY}};
        for (double[] r : refused) {
            try {
                WritePolicy.checkBattery(r[0], r[1]);
                fail(r[0] + " / " + r[1]);
            } catch (FlashException expected) {
            }
        }
        WritePolicy.checkProgrammingVoltage(0x270, KernelProfile.REPLY_80);
        try {
            WritePolicy.checkProgrammingVoltage(0x040, KernelProfile.REPLY_80);
            fail();
        } catch (FlashException expected) {
        }
        WritePolicy.checkRomId(new EcuIdentity("k", "", true), "ABCD1234", false);   // blank: recovery
        try {
            WritePolicy.checkRomId(new EcuIdentity("k", "OTHER123", false), "ABCD1234", false);
            fail();
        } catch (FlashException expected) {
        }
        assertTrue(new FlashOptions().writingEnabled == false);
    }

    @Test
    public void methodsAreChosenByFlashMethodAndMemoryModel() {
        assertTrue(FlashMethods.forRom("wrx02", "68HC16Y5") instanceof Wrx02Method);
        assertTrue(FlashMethods.forRom("WRX02", "68hc16y5") instanceof Wrx02Method);
        assertNull(FlashMethods.forRom("wrx02", "SH7055"));
        assertNull(FlashMethods.forRom("wrx04", "68HC16Y5"));
        assertNull(FlashMethods.forRom(null, null));
    }
}
