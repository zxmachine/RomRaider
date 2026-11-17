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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.romraider.flash.FlashException.FlashState;
import com.romraider.flash.sim.SimulatedEcu;
import com.romraider.flash.sim.SimulatedEcu.Fault;
import com.romraider.flash.sim.VirtualTiming;
import com.romraider.io.j2534.api.ConfigItem;
import com.romraider.io.j2534.api.J2534Impl.Config;

/** The wrx02 session against the simulated ECU: reads, compares, writes and every fault. */
public class Wrx02SessionTest {
    private static final String ROM_ID = "TESTID01";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final Wrx02Method method = new Wrx02Method();
    private final List<String> log = new ArrayList<String>();
    private final List<String> instructions = new ArrayList<String>();
    private VirtualTiming timing;
    private SimulatedEcu ecu;
    private byte[] original;
    private Kernel kernel;
    private BackupStore backups;
    private FlashOptions options;
    private CancelToken cancel;
    private boolean failOnWritingProgress;

    private void rig(KernelProfile profile) throws Exception {
        timing = new VirtualTiming();
        original = SimulatedEcu.makeImage(1, ROM_ID);
        byte[] kernelFile = SimulatedEcu.makeKernel(profile, 2);
        ecu = new SimulatedEcu(timing, original, profile, kernelFile);
        kernel = new Kernel(kernelFile, profile, "made-up kernel");
        backups = new BackupStore(folder.newFolder("backups"));
        options = new FlashOptions();
        options.writingEnabled = true;
        options.knownKernels = Arrays.asList(KnownKernel.of("made-up kernel", kernel, kernelFile.length));
        cancel = new CancelToken();
    }

    private FlashSession open() throws FlashException {
        FlashListener listener = new FlashListener() {
            @Override public void log(String line) { log.add(line); }
            @Override public void progress(String task, int done, int total) {
                if (failOnWritingProgress && "Writing".equals(task)) throw new IllegalStateException("listener failed");
            }
            @Override public void instruction(String text) { instructions.add(text); }
            @Override public void battery(double volts) { }
        };
        return method.open(new FlashContext(ecu, kernel, options, backups, timing, listener, cancel));
    }

    private static WriteRequest.Confirmer typing(final String text) {
        return new WriteRequest.Confirmer() {
            @Override
            public String confirm(List<FlashBlock> blocks, String romId, String image, String phrase) {
                return text == null ? phrase : text;
            }
        };
    }

    private static WriteRequest write(byte[] image) {
        return new WriteRequest(image, "wrx02", "68HC16Y5", false, false, typing(null));
    }

    /** An image with a few bytes changed in the blocks at these addresses, checksum fixed. */
    private byte[] changed(int... addresses) {
        byte[] img = original.clone();
        for (int a : addresses) {
            int o = Hc16Y5Layout.fileOffset(a);
            for (int i = 0; i < 16; i++) img[o + i] = (byte) ~img[o + i];
        }
        Hc16Y5Layout.fixChecksum(img);
        return img;
    }

    @After
    public void nothingOutsideApplicationFlash() {
        if (ecu != null) {
            assertTrue("boot block untouched", ecu.bootBlockIntact());
            assertTrue("boot loader enterable", ecu.bootLoaderEnterable());
        }
    }

    // ---- read and compare

    @Test
    public void fullReadEqualsTheImage() throws Exception {
        rig(KernelProfile.REPLY_80);
        FlashSession s = open();
        try {
            assertEquals(ROM_ID, s.identify().getRomId());
            assertArrayEquals(original, s.read());
        } finally {
            s.close();
        }
        assertTrue("the upload is the kernel file, scrambled", ecu.uploadMatched);
        assertEquals(1, instructions.size());
        assertEquals(0x200, ecu.getFlags());
        assertTrue(hasConfig(Config.LOOPBACK, 0) && hasConfig(Config.P1_MAX, 0)
                && hasConfig(Config.P3_MIN, 0) && hasConfig(Config.P4_MIN, 0));
        for (long[] p : ecu.pulses) {
            long d = p[1] - p[0];
            assertTrue("pulse " + d + " ms", d >= 170 && d <= 230);
        }
        assertTrue(ecu.erased.isEmpty());
        assertTrue(ecu.programmed.isEmpty());
    }

    private boolean hasConfig(Config c, int value) {
        for (ConfigItem i : ecu.config) {
            if (i.parameter == c.getValue() && i.value == value) return true;
        }
        return false;
    }

    @Test
    public void readRetriesABadCrcAndABadFrame() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.CRC_WRONG_ONCE).fault(Fault.BAD_REPLY_SUM_ONCE);
        FlashSession s = open();
        try {
            assertArrayEquals(original, s.read());
        } finally {
            s.close();
        }
        assertTrue(contains("CRC mismatch"));
        assertTrue(contains("bad reply checksum"));
    }

    @Test
    public void compareFindsTheDifferingBlocks() throws Exception {
        rig(KernelProfile.REPLY_80);
        FlashSession s = open();
        try {
            assertTrue(s.compare(original).isSame());
            List<FlashBlock> diff = s.compare(changed(0x05000)).differing();
            assertEquals(Arrays.asList("FB02", "FB10"), names(diff));
        } finally {
            s.close();
        }
    }

    @Test
    public void dryRunIsTheDefaultAndNeedsNoSwitch() throws Exception {
        rig(KernelProfile.REPLY_80);
        options.writingEnabled = false;
        FlashSession s = open();
        try {
            WriteResult r = s.write(WriteRequest.dryRun(changed(0x05000)));
            assertEquals(WriteResult.Outcome.DRY_RUN, r.getOutcome());
            assertEquals(Arrays.asList("FB02", "FB10"), names(r.getBlocks()));
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
        assertArrayEquals(original, ecu.image());
    }

    // ---- writes

    @Test
    public void writingTheSameImageWritesNothing() throws Exception {
        rig(KernelProfile.REPLY_80);
        FlashSession s = open();
        WriteResult r;
        try {
            r = s.write(write(original));
        } finally {
            s.close();
        }
        assertEquals(WriteResult.Outcome.NOTHING_TO_WRITE, r.getOutcome());
        assertTrue(ecu.erased.isEmpty());
        assertTrue(ecu.programmed.isEmpty());
        assertNull("no write, so no backup read", r.getBackup());
    }

    @Test
    public void writeOnlyChangedBlocksMarkerLastThenVerified() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = changed(0x05000, 0x12000);
        FlashSession s = open();
        WriteResult r;
        try {
            r = s.write(write(image));
        } finally {
            s.close();
        }
        assertEquals(WriteResult.Outcome.WRITTEN, r.getOutcome());
        assertEquals(Arrays.asList("FB02", "FB05", "FB10"), names(r.getBlocks()));
        // the marker block is erased first, then each block erased and programmed, the marker block last
        assertEquals(Arrays.asList(0x2C000, 0x04000, 0x10000), ecu.erased);
        assertEquals(Arrays.asList(0x04000, 0x05000, 0x06000, 0x07000, 0x10000, 0x11000, 0x12000, 0x13000,
                0x2C000, 0x2D000, 0x2E000, 0x2F000), ecu.programmed);
        assertArrayEquals(image, ecu.image());
        assertTrue(contains("flash correct"));
        assertTrue(ecu.applicationWouldStart());
        assertEquals("programming voltage was on for every erase", 3, ecu.vppOnWhileErasing);
        assertFalse("pin 11 off at the end", ecu.pinPowered(11));
        assertFalse(ecu.isFlashEnabled());
    }

    /** The image with two words swapped in the block at this address: the word sum, so FB10, stays the same. */
    private byte[] sumNeutral(int address) {
        byte[] image = original.clone();
        int o = Hc16Y5Layout.fileOffset(address);
        for (int k = 0; k < 64; k += 4) {
            byte a0 = image[o + k], a1 = image[o + k + 1];
            image[o + k] = image[o + k + 2];
            image[o + k + 1] = image[o + k + 3];
            image[o + k + 2] = a0;
            image[o + k + 3] = a1;
        }
        assertFalse(Arrays.equals(image, original));
        assertEquals(Hc16Y5Layout.CHECKSUM_TARGET, Hc16Y5Layout.wordSum(image));
        return image;
    }

    @Test
    public void theMarkerBlockIsWrittenLastEvenWhenItDoesNotDiffer() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = sumNeutral(0x0C000);
        FlashSession s = open();
        try {
            assertEquals(Arrays.asList("FB04"), names(s.compare(image).differing()));
            WriteResult r = s.write(write(image));
            assertEquals(Arrays.asList("FB04", "FB10"), names(r.getBlocks()));
        } finally {
            s.close();
        }
        assertEquals(Arrays.asList(0x2C000, 0x0C000), ecu.erased);
        assertEquals(Arrays.asList(0x0C000, 0x0D000, 0x0E000, 0x0F000, 0x2C000, 0x2D000, 0x2E000, 0x2F000),
                ecu.programmed);
        assertArrayEquals(image, ecu.image());
        assertTrue(ecu.applicationWouldStart());
    }

    @Test
    public void aStopMidBlockWithTheMarkerBlockUnchangedKeepsTheEcuInItsBootLoader() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.DROP_MID_BLOCK);
        ecu.dropAfterCommits = 2;
        byte[] image = sumNeutral(0x0C000);
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().startsWith("WRITE STOPPED at FB04"));
        } finally {
            s.close();
        }
        assertFalse("marker erased first: the half-written program is not started", ecu.applicationWouldStart());
        assertFalse(ecu.pinPowered(11));

        ecu.clearFaults();
        options.kernelMayBeRunning = true;
        FlashSession again = open();
        try {
            assertEquals(WriteResult.Outcome.WRITTEN, again.write(write(image)).getOutcome());
        } finally {
            again.close();
        }
        assertArrayEquals(image, ecu.image());
        assertTrue(ecu.applicationWouldStart());
    }

    @Test
    public void theOtherKernelProfileReadsAndWrites() throws Exception {
        rig(KernelProfile.REPLY_40);
        byte[] image = changed(0x2A000);
        FlashSession s = open();
        try {
            assertArrayEquals(original, s.read());
            WriteResult r = s.write(write(image));
            assertEquals(WriteResult.Outcome.WRITTEN, r.getOutcome());
            assertEquals(Arrays.asList("FB09", "FB10"), names(r.getBlocks()));
        } finally {
            s.close();
        }
        assertTrue(ecu.uploadMatched);
        assertArrayEquals(image, ecu.image());
    }

    @Test
    public void everyContentIsBackedUpOnceBeforeItIsOverwritten() throws Exception {
        rig(KernelProfile.REPLY_80);
        FlashSession s = open();
        try {
            WriteResult first = s.write(write(changed(0x05000)));
            assertNotNull(first.getBackup());
            assertArrayEquals(original, Files.readAllBytes(first.getBackup().toPath()));
            // the ECU now holds other content: it is read again before it is overwritten
            byte[] held = ecu.image();
            WriteResult second = s.write(write(changed(0x08000)));
            assertNotNull(second.getBackup());
            assertArrayEquals(held, Files.readAllBytes(second.getBackup().toPath()));
        } finally {
            s.close();
        }
        assertEquals(2, backups.getFolder().listFiles().length);
    }

    @Test
    public void aReadOfTheSameContentCountsAsTheBackup() throws Exception {
        rig(KernelProfile.REPLY_80);
        backups.save(original, ROM_ID, Hc16Y5Layout.contentKey(original), "read");
        FlashSession s = open();
        try {
            assertNull(s.write(write(changed(0x05000))).getBackup());
        } finally {
            s.close();
        }
        assertTrue(contains("saved earlier in this run"));
    }

    @Test
    public void aSecondEcuWithTheSameRomIdGetsItsOwnBackup() throws Exception {
        rig(KernelProfile.REPLY_80);
        backups.save(original, ROM_ID, Hc16Y5Layout.contentKey(original), "read");   // car A, read earlier
        byte[] carB = changed(0x09000);                                              // car B: same ID, own tune
        ecu = new SimulatedEcu(timing, carB, KernelProfile.REPLY_80, SimulatedEcu.makeKernel(KernelProfile.REPLY_80, 2));
        FlashSession s = open();
        WriteResult r;
        try {
            r = s.write(write(original));
        } finally {
            s.close();
        }
        assertNotNull("car B's own content is read before it is overwritten", r.getBackup());
        assertArrayEquals(carB, Files.readAllBytes(r.getBackup().toPath()));
    }

    // ---- refusals before anything is written

    @Test
    public void writingSwitchedOffRefusesBeforeTheEcuIsTouched() throws Exception {
        rig(KernelProfile.REPLY_80);
        options.writingEnabled = false;
        try {
            WritePolicy.preflight(method, options, write(changed(0x05000)));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getNextStep().contains("Allow writing"));
        }
        assertEquals(0, ecu.resets);
    }

    @Test
    public void aWrongConfirmationWritesNothing() throws Exception {
        rig(KernelProfile.REPLY_80);
        FlashSession s = open();
        try {
            s.write(new WriteRequest(changed(0x05000), "wrx02", "68HC16Y5", false, false, typing("write " + ROM_ID)));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
            assertTrue(e.getNextStep().contains("WRITE " + ROM_ID));
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
    }

    @Test
    public void aWrongChecksumIsRefusedUntilFixed() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = original.clone();
        image[0x5000] ^= 1;
        try {
            WritePolicy.preflight(method, options, write(image));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("checksum"));
        }
        assertTrue(method.fixChecksum(image));
        WritePolicy.preflight(method, options, write(image));
    }

    @Test
    public void anotherRomIdIsRefusedUnlessAllowed() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] other = SimulatedEcu.makeImage(5, "OTHERID2");
        FlashSession s = open();
        try {
            try {
                s.write(write(other));
                fail();
            } catch (FlashException e) {
                assertTrue(e.getMessage().contains("OTHERID2"));
            }
            assertTrue(ecu.erased.isEmpty());
            WriteResult r = s.write(new WriteRequest(other, "wrx02", "68HC16Y5", false, true, typing("WRITE OTHERID2")));
            assertEquals(WriteResult.Outcome.WRITTEN, r.getOutcome());
        } finally {
            s.close();
        }
        assertArrayEquals(other, ecu.image());
    }

    @Test
    public void anotherMemoryModelIsRefused() throws Exception {
        rig(KernelProfile.REPLY_80);
        try {
            WritePolicy.preflight(method, options,
                    new WriteRequest(original, "wrx04", "68HC16Y5", false, false, typing(null)));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("wrx04"));
        }
    }

    @Test
    public void aLowBatteryRefusesTheWrite() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LOW_BATTERY);
        FlashSession s = open();
        try {
            s.write(write(changed(0x05000)));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("battery"));
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
        assertArrayEquals(original, ecu.image());
    }

    @Test
    public void aLowProgrammingVoltageWritesNothing() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LOW_PROGRAMMING_VOLTAGE);
        FlashSession s = open();
        try {
            s.write(write(changed(0x05000)));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("programming voltage"));
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
            assertTrue(e.getNextStep().contains("pin 11"));
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
        assertTrue(ecu.applicationWouldStart());
        assertFalse(ecu.pinPowered(11));
    }

    // ---- faults while connecting or reading

    @Test
    public void aSilentBootLoaderIsReported() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LOADER_SILENT);
        expectOpenFailure("boot loader never answered");
    }

    @Test
    public void anUnansweredHandshakeAsksForTheIgnitionAgainOnce() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LOADER_SILENT);
        expectOpenFailure("boot loader never answered");
        // the first instruction, then once the hint to cycle the ignition again
        assertEquals(2, instructions.size());
        assertTrue(instructions.get(1), instructions.get(1).contains("Turn the ignition OFF again"));
    }

    @Test
    public void noIgnitionCycleIsReported() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.NO_OPERATOR);
        expectOpenFailure("boot loader never answered");
        assertEquals(0, ecu.resets);
    }

    @Test
    public void aSilentKernelIsReported() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.KERNEL_SILENT);
        FlashSession s = open();
        try {
            s.identify();
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("not answering"));
        } finally {
            s.close();
        }
    }

    @Test
    public void aKernelOfTheOtherProfileIsNamed() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.WRONG_REPLY_CODE);
        FlashSession s = open();
        try {
            s.identify();
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("0xC1") || e.getMessage().contains("0x41"));
            assertTrue(e.getMessage().contains(KernelProfile.REPLY_40.getName()));
        } finally {
            s.close();
        }
    }

    @Test
    public void aReadGivesUpAfterThreeBadCrcs() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.CRC_WRONG_ALWAYS);
        FlashSession s = open();
        try {
            s.read();
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("after 3 tries"));
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
        } finally {
            s.close();
        }
    }

    @Test
    public void aStopWhileWaitingForTheBootLoaderWritesNothing() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.NO_OPERATOR);
        cancel.cancel();
        try {
            open();
            fail();
        } catch (FlashCancelledException e) {
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
        }
        assertTrue(ecu.erased.isEmpty());
    }

    private void expectOpenFailure(String text) throws Exception {
        try {
            open().close();
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(text));
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
            assertTrue(e.getNextStep().contains("test-mode connector"));
        }
        assertTrue(ecu.erased.isEmpty());
        assertArrayEquals(original, ecu.image());
    }

    // ---- a power loss in the middle of a block, and the recovery

    @Test
    public void aDropMidBlockLeavesTheEcuWritableAgain() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.DROP_MID_BLOCK);
        ecu.dropAfterCommits = 3;
        byte[] image = changed(0x05000, 0x12000);
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().startsWith("WRITE STOPPED at FB02"));
            assertTrue(e.getNextStep().contains("Write again"));
        } finally {
            s.close();
        }
        assertEquals(SimulatedEcu.State.DEAD, ecu.getState());
        assertFalse("the marker block was erased first: no half-written application starts",
                ecu.applicationWouldStart());
        assertFalse("pin 11 off after the failure", ecu.pinPowered(11));

        // the recovery: write again; the dead kernel does not answer, so the boot loader is entered again
        ecu.clearFaults();
        options.kernelMayBeRunning = true;
        FlashSession again = open();
        try {
            WriteResult r = again.write(write(image));
            assertEquals(WriteResult.Outcome.WRITTEN, r.getOutcome());
        } finally {
            again.close();
        }
        assertArrayEquals(image, ecu.image());
        assertTrue(ecu.applicationWouldStart());
    }

    @Test
    public void aKernelLeftRunningIsUsedAgain() throws Exception {
        rig(KernelProfile.REPLY_80);
        open().close();
        int resets = ecu.resets;
        options.kernelMayBeRunning = true;
        FlashSession s = open();
        try {
            assertTrue(s.compare(original).isSame());
        } finally {
            s.close();
        }
        assertEquals("no new reset", resets, ecu.resets);
        assertTrue(contains("still answers"));
        assertTrue(contains("the same"));
    }

    // ---- the kernel: only known kernels, checked in RAM

    @Test
    public void anUnknownKernelIsRefusedBeforeAnythingIsSent() throws Exception {
        rig(KernelProfile.REPLY_80);
        options.knownKernels = KnownKernel.all();
        try {
            open().close();
            fail("an unknown kernel was used");
        } catch (FlashException e) {
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().contains("not a kernel RomRaider knows"));
            assertTrue(e.getNextStep().contains("Allow an unknown kernel"));
        }
        assertEquals("the ECU never saw a handshake", 0, ecu.resets);
        assertFalse(ecu.uploadMatched);
        assertTrue(ecu.events.isEmpty());
    }

    @Test
    public void anUnknownKernelIsUsedWhenAllowedAndNotChecked() throws Exception {
        rig(KernelProfile.REPLY_80);
        options.knownKernels = KnownKernel.all();
        options.allowUnknownKernel = true;
        FlashSession s = open();
        try {
            assertArrayEquals(original, s.read());
        } finally {
            s.close();
        }
        assertTrue(contains("not a known kernel"));
        assertTrue(contains("not checked"));
    }

    @Test
    public void aKernelDamagedOnTheWayIsRefusedBeforeAnythingIsWritten() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.KERNEL_RAM_DAMAGED);
        byte[] image = changed(0x2A000);
        FlashSession s = open();
        try {
            s.write(write(image));
            fail("a damaged kernel was used");
        } catch (FlashException e) {
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().contains("not the kernel file"));
        } finally {
            s.close();
        }
        assertTrue(contains("DIFFERENT"));
        assertTrue(ecu.erased.isEmpty());
        assertTrue(ecu.programmed.isEmpty());
        assertFalse(ecu.pinPowered(11));
        assertArrayEquals(original, ecu.image());
    }

    @Test
    public void aKernelLeftRunningThatIsNotTheFileIsLoadedAgain() throws Exception {
        rig(KernelProfile.REPLY_80);
        open().close();
        ecu.damageKernelRam();
        int resets = ecu.resets;
        options.kernelMayBeRunning = true;
        FlashSession s = open();
        try {
            assertTrue(s.compare(original).isSame());
        } finally {
            s.close();
        }
        assertTrue("the boot loader was entered again", ecu.resets > resets);
        assertTrue(contains("not the chosen kernel file"));
    }

    // ---- the program ends while a write runs (review A1)

    @Test
    public void aShutdownDuringAnEraseStopsTheWriteCleanly() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = changed(0x05000, 0x12000);
        final int[] forced = {-1};
        final Thread[] stopper = new Thread[1];
        ecu.hook = new SimulatedEcu.CommandHook() {
            @Override
            public void started(int command) {
                if (command != 0x25 || stopper[0] != null) return;
                stopper[0] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        forced[0] = ActiveSessions.shutdown(10000);
                    }
                });
                stopper[0].start();
                sleepReal(300);     // the shutdown request arrives while the erase runs
            }
        };
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().contains("program is closing"));
            assertFalse("pin 11 off before the session is closed", ecu.pinPowered(11));
            assertFalse("flash disable sent", ecu.isFlashEnabled());
        } finally {
            s.close();
        }
        stopper[0].join(10000);
        assertEquals("the session stopped by itself", 0, forced[0]);
        assertEquals("the running erase ended; nothing more was erased", Arrays.asList(0x2C000), ecu.erased);
        assertFalse("the marker is erased: the boot loader stays in charge", ecu.applicationWouldStart());
        assertFalse(ActiveSessions.isBusy());
        recoverWith(image);
    }

    @Test
    public void aSessionThatDoesNotStopInTimeHasItsPinsSwitchedOff() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = changed(0x05000);
        final int[] forced = {-1};
        final Thread[] stopper = new Thread[1];
        ecu.hook = new SimulatedEcu.CommandHook() {
            @Override
            public void started(int command) {
                if (command != 0x25 || stopper[0] != null) return;
                stopper[0] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        forced[0] = ActiveSessions.shutdown(200);
                    }
                });
                stopper[0].start();
                sleepReal(800);     // the session's thread hangs past the shutdown's wait
            }
        };
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
        } finally {
            s.close();
        }
        stopper[0].join(10000);
        assertEquals("stopped from the shutdown thread", 1, forced[0]);
        assertFalse("pin 11 off", ecu.pinPowered(11));
        assertTrue(s.isClosed());
        assertFalse(ActiveSessions.isBusy());
    }

    private static void sleepReal(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recoverWith(byte[] image) throws Exception {
        ecu.clearFaults();
        ecu.hook = null;
        options.kernelMayBeRunning = true;
        FlashSession again = open();
        try {
            assertEquals(WriteResult.Outcome.WRITTEN, again.write(write(image)).getOutcome());
        } finally {
            again.close();
        }
        assertArrayEquals(image, ecu.image());
        assertTrue(ecu.applicationWouldStart());
    }

    // ---- review B1, B3, B4, B6, C1

    @Test
    public void theConfirmationNamesTheImage() throws Exception {
        rig(KernelProfile.REPLY_80);
        byte[] image = changed(0x05000);
        final String[] shown = new String[1];
        WriteRequest request = new WriteRequest(image, "wrx02", "68HC16Y5", false, false, new WriteRequest.Confirmer() {
            @Override
            public String confirm(List<FlashBlock> blocks, String romId, String description, String phrase) {
                shown[0] = description;
                return null;     // the user says no
            }
        });
        FlashSession s = open();
        try {
            s.write(request);
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
        } finally {
            s.close();
        }
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(image);
        assertTrue(shown[0], shown[0].contains(String.format("%08X", crc.getValue())));
        assertTrue(shown[0], shown[0].contains(ROM_ID) && shown[0].contains("checksum right"));
        assertTrue(ecu.erased.isEmpty());
    }

    @Test
    public void aLostFlashEnableReplyStillEndsWithFlashDisableAndPinOff() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LOSE_ENABLE_REPLY);
        FlashSession s = open();
        try {
            s.write(write(changed(0x05000)));
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("0x20"));
            assertFalse("flash disable sent before the session closes", ecu.isFlashEnabled());
            assertFalse("pin 11 off before the session closes", ecu.pinPowered(11));
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
    }

    @Test
    public void aLateReplyIsNotTakenForTheNextRequest() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.LATE_REPLY_ONCE);
        ecu.faultAddress = 0x01000;
        FlashSession s = open();
        try {
            assertArrayEquals(original, s.read());
            assertTrue(s.compare(original).isSame());
        } finally {
            s.close();
        }
        assertTrue(contains("0x01000: read: no reply"));
        boolean late = false;
        for (String e : ecu.events) late |= e.contains("comes late");
        assertTrue(late);
    }

    @Test
    public void anEchoOfEachRequestIsSkipped() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.ECHO);
        byte[] image = changed(0x05000);
        FlashSession s = open();
        try {
            assertArrayEquals(original, s.read());
            assertEquals(WriteResult.Outcome.WRITTEN, s.write(write(image)).getOutcome());
        } finally {
            s.close();
        }
        assertArrayEquals(image, ecu.image());
    }

    @Test
    public void aBatteryDropAfterTheConfirmationRefusesBeforeTheFirstErase() throws Exception {
        rig(KernelProfile.REPLY_80);
        WriteRequest request = new WriteRequest(changed(0x05000), "wrx02", "68HC16Y5", false, false,
                new WriteRequest.Confirmer() {
                    @Override
                    public String confirm(List<FlashBlock> blocks, String romId, String d, String phrase) {
                        ecu.batteryVolts = 11.5;    // lights on while the user types
                        return phrase;
                    }
                });
        FlashSession s = open();
        try {
            s.write(request);
            fail();
        } catch (FlashException e) {
            assertTrue(e.getMessage().contains("battery"));
            assertEquals(FlashState.UNCHANGED, e.getFlashState());
        } finally {
            s.close();
        }
        assertTrue(ecu.erased.isEmpty());
        assertFalse(ecu.pinPowered(11));
    }

    @Test
    public void aBatteryDropBetweenBlocksStopsTheWriteCleanly() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.BATTERY_DROPS);
        ecu.batteryDropAfterErases = 1;
        byte[] image = changed(0x05000);
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().contains("battery dropped"));
            assertFalse(ecu.isFlashEnabled());
            assertFalse(ecu.pinPowered(11));
        } finally {
            s.close();
        }
        assertEquals(Arrays.asList(0x2C000), ecu.erased);
        assertFalse(ecu.applicationWouldStart());
        recoverWith(image);
    }

    @Test
    public void anErrorInsideTheWriteIsReportedAsAStoppedWrite() throws Exception {
        rig(KernelProfile.REPLY_80);
        failOnWritingProgress = true;
        byte[] image = changed(0x05000);
        FlashSession s = open();
        try {
            s.write(write(image));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
            assertTrue(e.getMessage(), e.getMessage().startsWith("WRITE STOPPED"));
            assertTrue(e.getNextStep().contains("Write again"));
            assertFalse(ecu.pinPowered(11));
        } finally {
            s.close();
        }
        failOnWritingProgress = false;
        recoverWith(image);
    }

    @Test
    public void aLostAdapterInTheMiddleOfAWriteIsReportedAsAStoppedWrite() throws Exception {
        rig(KernelProfile.REPLY_80);
        ecu.fault(Fault.ADAPTER_LOST);
        ecu.dropAfterCommits = 2;
        FlashSession s = open();
        try {
            s.write(write(changed(0x05000)));
            fail();
        } catch (FlashException e) {
            assertEquals(FlashState.PARTLY_WRITTEN, e.getFlashState());
        } finally {
            s.close();
        }
        assertFalse(ActiveSessions.isBusy());
        assertFalse(ecu.applicationWouldStart());
    }

    // ---- helpers

    private boolean contains(String text) {
        for (String l : log) {
            if (l.contains(text)) return true;
        }
        return false;
    }

    private static List<String> names(List<FlashBlock> blocks) {
        List<String> out = new ArrayList<String>();
        for (FlashBlock b : blocks) out.add(b.getName());
        return out;
    }
}
