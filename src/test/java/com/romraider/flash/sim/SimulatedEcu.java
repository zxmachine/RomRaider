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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;

import com.romraider.flash.Hc16Y5Layout;
import com.romraider.flash.KernelProfile;
import com.romraider.flash.Timing;
import com.romraider.io.j2534.api.ConfigItem;
import com.romraider.io.j2534.api.J2534;
import com.romraider.io.j2534.api.J2534Exception;
import com.romraider.io.j2534.api.J2534Impl.TxFlags;
import com.romraider.io.j2534.api.Version;

/**
 * A 16-bit Denso ECU behind a J2534 adapter, for tests: the boot loader's
 * entry conditions (test-mode connector, the reflash pin grounded at reset and
 * a 170-230 ms release), its handshake and scrambled kernel upload, a kernel
 * with the reply code and voltage window of a profile, and flash that erases
 * in 16 KB blocks to 0xFF and programs only by clearing bits, with the
 * programming voltage on pin 11 and the flash enable both needed.
 *
 * Erase and commit take simulated time; while one runs the kernel drops the
 * frames it gets, and a power loss in that time leaves the block half done.
 * Replies are queued with the time they arrive at the host. The pins keep
 * the state they were last given, also after the session's adapter object is
 * closed or forgotten (an adapter does not switch them off by itself).
 *
 * An operator turns the ignition off and on whenever the reflash pin is newly
 * grounded while the ECU runs. Faults can be switched on.
 */
public final class SimulatedEcu implements J2534 {
    /** Faults the simulator can show. */
    public enum Fault {
        /** The boot loader never answers the handshake. */
        LOADER_SILENT,
        /** Nobody cycles the ignition. */
        NO_OPERATOR,
        /** The kernel never answers. */
        KERNEL_SILENT,
        /** The kernel answers with the other profile's reply codes. */
        WRONG_REPLY_CODE,
        /** The CRC reply for the read chunk at {@link #faultAddress} is wrong once. */
        CRC_WRONG_ONCE,
        /** Every CRC reply for a read chunk is wrong. */
        CRC_WRONG_ALWAYS,
        /** The programming voltage reaches the ECU too low. */
        LOW_PROGRAMMING_VOLTAGE,
        /** The battery is low. */
        LOW_BATTERY,
        /** The battery drops below 12 V once {@link #batteryDropAfterErases} erases are done. */
        BATTERY_DROPS,
        /** The power fails half way through the commit number {@link #dropAfterCommits}. */
        DROP_MID_BLOCK,
        /** One reply frame (the first read of a 0x200-byte chunk) has a bad checksum byte. */
        BAD_REPLY_SUM_ONCE,
        /** The reply to the read of the chunk at {@link #faultAddress} comes {@link #lateMs} late, once. */
        LATE_REPLY_ONCE,
        /** The adapter hands back every kernel frame the host sends (an echo) before the reply. */
        ECHO,
        /** The kernel sets the flash enable but its reply is lost. */
        LOSE_ENABLE_REPLY,
        /** The adapter is lost (unplugged) after commit number {@link #dropAfterCommits}: every call fails. */
        ADAPTER_LOST,
        /**
         * The kernel arrives with one byte of its code changed (a damage the
         * boot loader's checksum missed), and runs all the same.
         */
        KERNEL_RAM_DAMAGED
    }

    /** What the ECU is doing. */
    public enum State { OFF, APPLICATION, WAITING, WATCHING, LOADER, KERNEL, HUNG, DEAD }

    /** Called with a command code when the kernel starts it (a test hook; on the session's thread). */
    public interface CommandHook {
        void started(int command);
    }

    private static final int LEN_ANY = -1;
    private static final Map<Integer, Integer> COMMAND_LENGTHS = new HashMap<Integer, Integer>();
    /** A made-up reading of the programming-voltage input with nothing on pin 11. */
    private static final int VPP_IDLE = 0x0040;

    static {
        int[][] t = {{0x01, 1}, {0x02, 9}, {0x03, 7}, {0x04, 1}, {0x05, 1}, {0x06, 1}, {0x20, 1}, {0x21, 1},
                     {0x22, LEN_ANY}, {0x23, 11}, {0x24, 11}, {0x25, 5}};
        for (int[] e : t) COMMAND_LENGTHS.put(e[0], e[1]);
    }

    private final Timing timing;
    private final byte[] flash = new byte[0x30000];
    private final byte[] bootBlock = new byte[Hc16Y5Layout.BOOT_BLOCK_SIZE];
    private final byte[] bootBlockAtStart;
    private final KernelProfile kernelProfile;
    private final byte[] expectedKernel;
    private final Set<Fault> faults = EnumSet.noneOf(Fault.class);
    private final Map<Integer, Integer> pins = new HashMap<Integer, Integer>();
    private final List<Object[]> toHost = new ArrayList<Object[]>();   // {Long readyAt, byte[] bytes}

    /** Settings a test may change. */
    public boolean testModeConnector = true;
    public long operatorDelayMs = 2500;
    public double batteryVolts = 12.6;
    public int dropAfterCommits = 3;
    public int batteryDropAfterErases = 1;
    public int faultAddress = 0x01000;
    public long lateMs = 2500;
    public long eraseMs = 1500;
    public long commitMs = 200;
    public volatile CommandHook hook;

    private State state = State.APPLICATION;
    private boolean deviceOpen;
    private boolean channelOpen;
    private boolean adapterLost;
    private int baud;
    private int flags;
    private long ignitionOnAt = -1;
    private long resetAt;
    private long releasedAt = -1;
    private boolean handshakeSeen;
    private boolean flashEnabled;
    private final byte[] buffer = new byte[0x1000];
    private int commits;
    private boolean crcFaultUsed;
    private boolean sumFaultUsed;
    private boolean lateFaultUsed;

    // the erase or commit that is running
    private long opDoneAt = -1;
    private long opDropAt = -1;
    private Runnable opApply;
    private Runnable opPartial;
    private byte[] opReply;

    /** What happened, for the tests. */
    public final List<String> events = new ArrayList<String>();
    public final List<Integer> erased = new ArrayList<Integer>();
    public final List<Integer> programmed = new ArrayList<Integer>();
    public final List<long[]> pulses = new ArrayList<long[]>();
    public final List<ConfigItem> config = new ArrayList<ConfigItem>();
    public boolean bootBlockTouched;
    public boolean uploadMatched;
    /** The kernel as the boot loader left it in RAM at 0x20000 (read while the kernel runs). */
    private byte[] kernelRam;
    public int resets;
    public int vppOnWhileErasing;
    public int framesDroppedWhileBusy;

    /**
     * @param timing         the clock the session uses too
     * @param image          the flash content, a whole 160 KB image
     * @param kernelProfile  how the kernel the ECU will run behaves
     * @param expectedKernel the kernel file the upload must carry
     */
    public SimulatedEcu(Timing timing, byte[] image, KernelProfile kernelProfile, byte[] expectedKernel) {
        this.timing = timing;
        Arrays.fill(flash, (byte) 0xFF);
        for (int off = 0; off < Hc16Y5Layout.IMAGE_SIZE; off++) {
            flash[Hc16Y5Layout.address(off)] = image[off];
        }
        new Random(99).nextBytes(bootBlock);
        bootBlockAtStart = bootBlock.clone();
        this.kernelProfile = kernelProfile;
        this.expectedKernel = expectedKernel.clone();
    }

    public synchronized SimulatedEcu fault(Fault f) {
        faults.add(f);
        return this;
    }

    public synchronized void clearFaults() {
        faults.clear();
        adapterLost = false;
    }

    public synchronized State getState() {
        return state;
    }

    /** The flash as an image file. */
    public synchronized byte[] image() {
        byte[] img = new byte[Hc16Y5Layout.IMAGE_SIZE];
        for (int off = 0; off < img.length; off++) img[off] = flash[Hc16Y5Layout.address(off)];
        return img;
    }

    /** Changes one byte of the running kernel's code in RAM, as another kernel there would differ. */
    public synchronized void damageKernelRam() {
        if (kernelRam != null) kernelRam[0x100] ^= 0x01;
    }

    public synchronized boolean bootBlockIntact() {
        return Arrays.equals(bootBlock, bootBlockAtStart) && !bootBlockTouched;
    }

    /** True when the boot loader would start the application at the next reset. */
    public synchronized boolean applicationWouldStart() {
        return markerValid();
    }

    /** The boot loader is reached by a reset with pin 9 grounded, whatever the application flash holds. */
    public boolean bootLoaderEnterable() {
        return bootBlockIntact();
    }

    /** The flash enable bit in the ECU. */
    public synchronized boolean isFlashEnabled() {
        return flashEnabled;
    }

    /** What the adapter drives on a pin now: millivolts, VOLTAGE_OFF or SHORT_TO_GROUND. */
    public synchronized int pinValue(int pin) {
        return pin(pin);
    }

    /** True when the adapter puts a voltage (not off, not ground) on the pin. */
    public synchronized boolean pinPowered(int pin) {
        int v = pin(pin);
        return v != VOLTAGE_OFF && v != SHORT_TO_GROUND;
    }

    private boolean markerValid() {
        return flash[Hc16Y5Layout.MARKER_ADDRESS] == flash[Hc16Y5Layout.MARKER_ADDRESS + 1]
                && (flash[Hc16Y5Layout.MARKER_ADDRESS] & 0xFF) != 0xFF;
    }

    // ---- time and the operator

    private void advance() {
        long now = timing.now();
        if (state == State.OFF && ignitionOnAt >= 0 && now >= ignitionOnAt) {
            reset(ignitionOnAt);
        }
        if (state == State.WATCHING && releasedAt < 0 && now > resetAt + 10000) {
            event("boot loader: no pulse within 10 s, gives up");
            giveUp();
        }
        if (opApply != null) {
            if (opDropAt >= 0 && now >= opDropAt) {
                opPartial.run();
                event("power lost in the middle of an erase or commit");
                clearOp();
                state = State.DEAD;
            } else if (now >= opDoneAt) {
                long at = opDoneAt;
                opApply.run();
                byte[] reply = opReply;
                clearOp();
                if (state == State.KERNEL && reply != null) queue(at, frame(reply));
            }
        }
    }

    private void clearOp() {
        opApply = null;
        opPartial = null;
        opReply = null;
        opDoneAt = -1;
        opDropAt = -1;
    }

    private void reset(long at) {
        resets++;
        resetAt = at;
        ignitionOnAt = -1;
        releasedAt = -1;
        handshakeSeen = false;
        flashEnabled = false;
        clearOp();
        boolean pin9Grounded = pin(9) == SHORT_TO_GROUND;
        if (pin9Grounded && testModeConnector) {
            state = State.WATCHING;
        } else {
            state = markerValid() ? State.APPLICATION : State.WAITING;
        }
        event("reset -> " + state);
    }

    private void giveUp() {
        state = markerValid() ? State.APPLICATION : State.WAITING;
        releasedAt = -1;
    }

    private void event(String e) {
        events.add(String.format("%d %s", timing.now(), e));
    }

    private int pin(int p) {
        Integer v = pins.get(p);
        return v == null ? VOLTAGE_OFF : v;
    }

    private void checkAdapter() {
        if (adapterLost) throw new J2534Exception("adapter lost (simulated)");
    }

    @Override
    public synchronized void setProgrammingVoltage(int deviceId, int pin, int voltage) {
        checkAdapter();
        checkDevice(deviceId);
        advance();
        int before = pin(pin);
        pins.put(pin, voltage);
        if (pin != 9 || before == voltage) return;
        long now = timing.now();
        boolean grounded = voltage == SHORT_TO_GROUND;
        boolean wasGrounded = before == SHORT_TO_GROUND;
        if (grounded && !wasGrounded) {
            switch (state) {
                case APPLICATION: case WAITING: case KERNEL: case HUNG: case DEAD:
                    if (!faults.contains(Fault.NO_OPERATOR)) {
                        event("operator: ignition off, on again in " + operatorDelayMs + " ms");
                        state = State.OFF;
                        clearOp();
                        ignitionOnAt = now + operatorDelayMs;
                    }
                    return;
                case WATCHING:
                    if (releasedAt >= 0) {
                        long d = now - releasedAt;
                        pulses.add(new long[] {releasedAt, now});
                        if (d >= 170 && d <= 230) {
                            state = State.LOADER;
                            event("boot loader: pulse " + d + " ms, download mode");
                        } else {
                            event("boot loader: pulse " + d + " ms, gives up");
                            giveUp();
                        }
                    }
                    return;
                default:
                    return;
            }
        }
        if (!grounded && wasGrounded) {
            if (state == State.WATCHING) {
                releasedAt = now;
            } else if (state == State.LOADER) {
                event("boot loader: pin 9 released in download mode, hangs");
                state = State.HUNG;
            }
        }
    }

    // ---- the adapter

    @Override
    public synchronized int open() {
        checkAdapter();
        deviceOpen = true;
        return 1;
    }

    private void checkDevice(int deviceId) {
        if (!deviceOpen || deviceId != 1) throw new J2534Exception("device not open");
    }

    @Override
    public synchronized int connect(int deviceId, int flags, int baud) {
        checkAdapter();
        checkDevice(deviceId);
        if (channelOpen) throw new J2534Exception("channel already open");
        channelOpen = true;
        this.baud = baud;
        this.flags = flags;
        toHost.clear();
        return 7;
    }

    public int getFlags() {
        return flags;
    }

    @Override
    public synchronized void setConfig(int channelId, ConfigItem... items) {
        checkAdapter();
        config.addAll(Arrays.asList(items));
    }

    @Override
    public synchronized int startPassMsgFilter(int channelId, byte mask, byte pattern) {
        checkAdapter();
        return 1;
    }

    @Override
    public synchronized void clearBuffers(int channelId) {
        checkAdapter();
        if (!channelOpen) throw new J2534Exception("channel not open");
        advance();
        long now = timing.now();
        for (Iterator<Object[]> it = toHost.iterator(); it.hasNext();) {
            if ((Long) it.next()[0] <= now) it.remove();      // what has arrived is thrown away
        }
    }

    @Override
    public synchronized void disconnect(int channelId) {
        checkAdapter();
        channelOpen = false;
    }

    @Override
    public synchronized void close(int deviceId) {
        checkAdapter();
        checkDevice(deviceId);
        deviceOpen = false;
        channelOpen = false;
    }

    @Override
    public synchronized double getVbattery(int deviceId) {
        checkAdapter();
        checkDevice(deviceId);
        if (faults.contains(Fault.LOW_BATTERY)) return 11.2;
        if (faults.contains(Fault.BATTERY_DROPS) && erased.size() >= batteryDropAfterErases) return 11.4;
        return batteryVolts;
    }

    @Override
    public void writeMsg(int channelId, byte[] data, long timeout, TxFlags flag) {
        CommandHook h = null;
        int cmd = -1;
        synchronized (this) {
            checkAdapter();
            if (!channelOpen) throw new J2534Exception("channel not open");
            advance();
            if (faults.contains(Fault.ECHO) && state == State.KERNEL) queue(timing.now(), data.clone());
            cmd = receive(data.clone());
            h = hook;
        }
        if (h != null && cmd >= 0) h.started(cmd);    // outside the lock, so that a hook may wait
    }

    @Override
    public byte[] readAvailable(int channelId, long timeout) {
        synchronized (this) {
            checkAdapter();
            if (!channelOpen) throw new J2534Exception("channel not open");
            advance();
            byte[] ready = drain();
            if (ready.length > 0) return ready;
        }
        try {
            timing.sleep(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            checkAdapter();
            advance();
            return drain();
        }
    }

    private void queue(long readyAt, byte[] bytes) {
        toHost.add(new Object[] {readyAt, bytes});
    }

    private byte[] drain() {
        long now = timing.now();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Iterator<Object[]> it = toHost.iterator(); it.hasNext();) {
            Object[] e = it.next();
            if ((Long) e[0] <= now) {
                byte[] b = (byte[]) e[1];
                out.write(b, 0, b.length);
                it.remove();
            }
        }
        return out.toByteArray();
    }

    private void send(byte[] b) {
        queue(timing.now(), b);
    }

    // ---- what the ECU does with a frame

    /** Handles a frame; returns the kernel command started, or -1. */
    private int receive(byte[] frame) {
        switch (state) {
            case LOADER:
                if (baud == KernelProfile.LOADER_BAUD) loaderFrame(frame);
                return -1;
            case KERNEL:
                if (baud == kernelProfile.getBaud()) return kernelFrame(frame);
                return -1;
            default:
                return -1;
        }
    }

    private void loaderFrame(byte[] f) {
        if (faults.contains(Fault.LOADER_SILENT)) return;
        int sum = 0;
        for (byte b : f) sum += b & 0xFF;
        int cmd = f[0] & 0xFF;
        if ((sum & 0xFF) != 0) {
            loaderError(0x03);
            return;
        }
        if (cmd == 0x4D) {
            if (f.length != 3) {
                loaderError(0x25);
                return;
            }
            handshakeSeen = true;
            send(new byte[] {0x4D, 0x00, (byte) 0xB3});
            return;
        }
        if (cmd != 0x53) {
            loaderError(0x21);
            return;
        }
        if (!handshakeSeen) {
            loaderError(0x24);
            return;
        }
        int address = ((f[1] & 0xFF) << 16) | ((f[2] & 0xFF) << 8);
        int len = ((f[3] & 0xFF) << 16) | ((f[4] & 0xFF) << 8) | (f[5] & 0xFF);
        if (f.length != 6 + len + 1) {
            loaderError(0x22);
            return;
        }
        if (address < 0x20000 || address + len > 0x21800) {
            loaderError(0x23);
            return;
        }
        byte[] ram = new byte[len];
        for (int i = 0; i < len; i++) {
            int a = address + i;
            byte b = f[6 + i];
            ram[i] = (a == 0x20002 || a == 0x20003) ? b : (byte) (((b & 0xFF) - 0x10) ^ 0x55);
        }
        if (((ram[2] & 0xFF) << 8 | (ram[3] & 0xFF)) != 0x3941) {
            loaderError(0x01);
            return;
        }
        byte[] expected = new byte[len];
        System.arraycopy(expectedKernel, 0, expected, 0, Math.min(len, expectedKernel.length));
        expected[2] = 0x39;
        expected[3] = 0x41;
        uploadMatched = len >= expectedKernel.length && Arrays.equals(ram, expected);
        if (!uploadMatched) {
            event("boot loader: the uploaded program is not the kernel; it crashes");
            state = State.HUNG;
            return;
        }
        kernelRam = ram;
        if (faults.contains(Fault.KERNEL_RAM_DAMAGED)) {
            kernelRam[0x100] ^= 0x01;
            event("boot loader: one byte of the kernel's code arrived changed");
        }
        event("boot loader: kernel loaded and started");
        state = State.KERNEL;
        flashEnabled = false;
    }

    private void loaderError(int code) {
        send(new byte[] {0x66, (byte) code, (byte) (0x100 - ((0x66 + code) & 0xFF))});
    }

    private int replyOffset() {
        if (!faults.contains(Fault.WRONG_REPLY_CODE)) return kernelProfile.getReplyOffset();
        return kernelProfile.getReplyOffset() == 0x80 ? 0x40 : 0x80;
    }

    private int kernelFrame(byte[] f) {
        if (faults.contains(Fault.KERNEL_SILENT)) return -1;
        if (opApply != null) {               // busy erasing or programming: the frame is lost
            framesDroppedWhileBusy++;
            return -1;
        }
        if (f.length < 5 || (f[0] & 0xFF) != 0xBE || (f[1] & 0xFF) != 0xEF) return -1;
        int len = ((f[2] & 0xFF) << 8) | (f[3] & 0xFF);
        if (f.length != len + 5) return -1;
        int s = 0;
        for (int i = 0; i < f.length - 1; i++) s += f[i] & 0xFF;
        if ((byte) s != f[f.length - 1]) return -1;        // dropped silently
        byte[] p = Arrays.copyOfRange(f, 4, 4 + len);
        int cmd = p[0] & 0xFF;
        byte[] reply = command(p);
        if (reply == null || state != State.KERNEL) return cmd;
        byte[] frame = frame(reply);
        if (faults.contains(Fault.BAD_REPLY_SUM_ONCE) && !sumFaultUsed && cmd == 0x03 && reply.length == 0x201) {
            sumFaultUsed = true;
            frame[frame.length - 1] ^= 0x5A;
        }
        long at = timing.now() + transferMs(frame.length);
        if (faults.contains(Fault.LATE_REPLY_ONCE) && !lateFaultUsed && cmd == 0x03 && reply.length == 0x201
                && addr(p, 1) == faultAddress) {
            lateFaultUsed = true;
            at += lateMs;
            event("reply to the read of " + Integer.toHexString(faultAddress) + " comes late");
        }
        queue(at, frame);
        return cmd;
    }

    /** The time a reply of this many bytes takes on the K-line (10 bits a byte). */
    private long transferMs(int bytes) {
        return bytes * 10L * 1000L / Math.max(1, baud);
    }

    private static byte[] frame(byte[] payload) {
        byte[] r = new byte[payload.length + 5];
        r[0] = (byte) 0xBE;
        r[1] = (byte) 0xEF;
        r[2] = (byte) (payload.length >> 8);
        r[3] = (byte) payload.length;
        System.arraycopy(payload, 0, r, 4, payload.length);
        int s = 0;
        for (int i = 0; i < r.length - 1; i++) s += r[i] & 0xFF;
        r[r.length - 1] = (byte) s;
        return r;
    }

    /**
     * The raw programming-voltage reading: nominal 12 V on pin 11 reads the
     * middle of the kernel's window (a made-up scale); nothing on the pin reads
     * a low made-up value.
     */
    private int vpp() {
        int v = pin(11);
        if (v == VOLTAGE_OFF || v == SHORT_TO_GROUND) return VPP_IDLE;
        double mv = faults.contains(Fault.LOW_PROGRAMMING_VOLTAGE) ? v * 0.8 : v;
        double middle = (kernelProfile.getVppMin() + kernelProfile.getVppMax()) / 2.0;
        return (int) Math.round(mv * middle / 12000.0);
    }

    private byte[] command(byte[] p) {
        int cmd = p[0] & 0xFF;
        Integer expectedLength = COMMAND_LENGTHS.get(cmd);
        if (expectedLength == null) return new byte[] {(byte) 0xFF};
        if (expectedLength != LEN_ANY && p.length != expectedLength) return new byte[] {(byte) 0xF0};
        int code = (cmd + replyOffset()) & 0xFF;
        switch (cmd) {
            case 0x01: {
                byte[] v = "SIM HC16 test kernel\0".getBytes(StandardCharsets.US_ASCII);
                byte[] r = new byte[v.length + 1];
                r[0] = (byte) code;
                System.arraycopy(v, 0, r, 1, v.length);
                return r;
            }
            case 0x02: {
                int a = addr(p, 1);
                int n = ((p[7] & 0xFF) << 8) | (p[8] & 0xFF);       // the kernel uses the low 16 bits
                CRC32 crc = new CRC32();
                for (int i = 0; i < n; i++) crc.update(mem(a + i));
                long c = crc.getValue();
                if (n == 0x200 && (faults.contains(Fault.CRC_WRONG_ALWAYS)
                        || (faults.contains(Fault.CRC_WRONG_ONCE) && !crcFaultUsed && a == faultAddress))) {
                    crcFaultUsed = true;
                    c ^= 0x1;
                }
                return new byte[] {(byte) code, (byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c};
            }
            case 0x03: {
                int a = addr(p, 1);
                int n = ((p[5] & 0xFF) << 8) | (p[6] & 0xFF);
                byte[] r = new byte[n + 1];
                r[0] = (byte) code;
                for (int i = 0; i < n; i++) r[1 + i] = (byte) mem(a + i);
                return r;
            }
            case 0x04: {
                int v = vpp();
                return new byte[] {(byte) code, (byte) (v >> 8), (byte) v};
            }
            case 0x05: return new byte[] {(byte) code, 0, 0, 0x02, 0x08};
            case 0x06: return new byte[] {(byte) code, 0, 0, 0x10, 0x00};
            case 0x20:
                flashEnabled = true;
                if (faults.contains(Fault.LOSE_ENABLE_REPLY)) return null;
                return new byte[] {(byte) code};
            case 0x21: flashEnabled = false; return new byte[] {(byte) code};
            case 0x22: {
                if (p.length < 5) return new byte[] {(byte) 0xF0};
                int a = addr(p, 1);
                for (int i = 5; i < p.length; i++) buffer[((a & 0xFFF) + i - 5) & 0xFFF] = p[i];
                return new byte[] {(byte) code};
            }
            case 0x23: {
                int n = ((p[5] & 0xFF) << 8) | (p[6] & 0xFF);
                return crcOk(n, p) ? new byte[] {(byte) code} : new byte[] {(byte) 0xF5};
            }
            case 0x24: return program(p, code);
            case 0x25: return erase(p, code);
            default: return new byte[] {(byte) 0xFF};
        }
    }

    private boolean crcOk(int n, byte[] p) {
        CRC32 crc = new CRC32();
        crc.update(buffer, 0, Math.min(n, buffer.length));
        long want = ((long) (p[7] & 0xFF) << 24) | ((p[8] & 0xFF) << 16) | ((p[9] & 0xFF) << 8) | (p[10] & 0xFF);
        return crc.getValue() == want;
    }

    private byte[] voltageError() {
        int v = vpp();
        if (v < kernelProfile.getVppMin()) return new byte[] {(byte) 0xF3};
        if (v > kernelProfile.getVppMax()) return new byte[] {(byte) 0xF4};
        return null;
    }

    private boolean applicationFlash(int a, int n) {
        boolean ok = Hc16Y5Layout.isApplicationFlash(a, n);
        if (!ok) {
            bootBlockTouched = true;    // any erase or program outside the arrays counts
            event(String.format("ERASE/PROGRAM OUTSIDE APPLICATION FLASH: 0x%05X", a));
        }
        return ok;
    }

    /** Starts a commit; it takes {@link #commitMs} and replies when done (null: no reply now). */
    private byte[] program(byte[] p, final int code) {
        final int a = addr(p, 1);
        final int n = ((p[5] & 0xFF) << 8) | (p[6] & 0xFF);
        if (!crcOk(n, p)) return new byte[] {(byte) 0xF5};
        byte[] ve = voltageError();
        if (ve != null) return ve;
        if (!applicationFlash(a, n)) return new byte[] {(byte) 0xF2};
        if (!flashEnabled) return new byte[] {(byte) 0xF2};    // no programming voltage reaches the array
        for (int i = 0; i < n; i++) {
            int want = buffer[i] & 0xFF;
            if (((flash[a + i] & 0xFF) & want) != want) return new byte[] {(byte) 0xF2};   // a bit 0 -> 1
        }
        commits++;
        final byte[] data = Arrays.copyOf(buffer, n);
        long now = timing.now();
        opDoneAt = now + commitMs;
        opApply = new Runnable() {
            @Override public void run() {
                System.arraycopy(data, 0, flash, a, n);
                programmed.add(a);
            }
        };
        opPartial = new Runnable() {
            @Override public void run() {
                System.arraycopy(data, 0, flash, a, n / 2);
            }
        };
        opReply = new byte[] {(byte) code, 0, 1};
        if (faults.contains(Fault.DROP_MID_BLOCK) && commits == dropAfterCommits) opDropAt = now + commitMs / 2;
        if (faults.contains(Fault.ADAPTER_LOST) && commits == dropAfterCommits) adapterLost = true;
        return null;
    }

    /** Starts an erase; it takes {@link #eraseMs} and replies when done. */
    private byte[] erase(byte[] p, final int code) {
        int a = addr(p, 1);
        byte[] ve = voltageError();
        if (ve != null) return ve;
        final int block = a & ~0x3FFF;
        if (!applicationFlash(block, 0x4000)) return new byte[] {(byte) 0xF2};
        if (!flashEnabled) return new byte[] {(byte) 0xF2};
        if (pinPowered(11)) vppOnWhileErasing++;
        long now = timing.now();
        opDoneAt = now + eraseMs;
        opApply = new Runnable() {
            @Override public void run() {
                Arrays.fill(flash, block, block + 0x4000, (byte) 0xFF);
                erased.add(block);
            }
        };
        opPartial = new Runnable() {
            @Override public void run() {
                Arrays.fill(flash, block, block + 0x2000, (byte) 0xFF);
            }
        };
        opReply = new byte[] {(byte) code, 0, 1};
        return null;
    }

    private static int addr(byte[] p, int at) {
        return ((p[at] & 0xFF) << 24 | (p[at + 1] & 0xFF) << 16 | (p[at + 2] & 0xFF) << 8 | (p[at + 3] & 0xFF)) & 0xFFFFFF;
    }

    private int mem(int a) {
        if (a >= 0 && a < flash.length && (a < 0x20000 || a >= 0x28000)) return flash[a] & 0xFF;
        if (a >= Hc16Y5Layout.BOOT_BLOCK && a < Hc16Y5Layout.BOOT_BLOCK + bootBlock.length) {
            return bootBlock[a - Hc16Y5Layout.BOOT_BLOCK] & 0xFF;
        }
        if (state == State.KERNEL && kernelRam != null && a >= 0x20000 && a < 0x20000 + kernelRam.length) {
            return kernelRam[a - 0x20000] & 0xFF;
        }
        return 0;
    }

    // ---- made-up test data

    /** A made-up whole image: random content, reset words, a ROM ID, the marker and a right checksum. */
    public static byte[] makeImage(long seed, String romId) {
        byte[] img = new byte[Hc16Y5Layout.IMAGE_SIZE];
        new Random(seed).nextBytes(img);
        byte[] reset = {0x02, 0x20, 0x04, 0x00, 0x08, (byte) 0xF6, 0x00, 0x00};
        System.arraycopy(reset, 0, img, 0, reset.length);
        img[0x400] = 0x27;   // code at the start address
        byte[] id = romId.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, img, Hc16Y5Layout.ROM_ID_ADDRESS, Math.min(id.length, Hc16Y5Layout.ROM_ID_LENGTH));
        int m = Hc16Y5Layout.fileOffset(Hc16Y5Layout.MARKER_ADDRESS);
        img[m] = 0;
        img[m + 1] = 0;
        Hc16Y5Layout.fixChecksum(img);
        return img;
    }

    /** A made-up kernel file for a profile: random bytes, with the signature where the profile wants it. */
    public static byte[] makeKernel(KernelProfile profile, long seed) {
        byte[] k = new byte[1500];
        new Random(seed).nextBytes(k);
        if (profile.getSignature() == KernelProfile.Signature.IN_FILE) {
            k[2] = 0x39;
            k[3] = 0x41;
        } else {
            k[2] = 0x12;
            k[3] = 0x34;
        }
        return k;
    }

    // ---- not used by a flash session

    @Override public Version readVersion(int deviceId) { return new Version("sim", "sim", "04.04"); }
    @Override public ConfigItem[] getConfig(int channelId, int... parameters) { throw new UnsupportedOperationException(); }
    @Override public int startPassMsgFilter(int channelId, byte[] mask, byte[] pattern) { return 1; }
    @Override public int startPassMsgFilter(int channelId, byte[] mask, byte[] pattern, TxFlags flag) { return 1; }
    @Override public int startBlockMsgFilter(int channelId, byte[] mask, byte[] pattern) { throw new UnsupportedOperationException(); }
    @Override public int startBlockMsgFilter(int channelId, byte[] mask, byte[] pattern, TxFlags flag) { throw new UnsupportedOperationException(); }
    @Override public int startFlowCntrlFilter(int channelId, byte[] mask, byte[] pattern, byte[] flowCntrl, TxFlags flag) { throw new UnsupportedOperationException(); }
    @Override public byte[] fiveBaudInit(int channelId, byte[] input) { throw new UnsupportedOperationException(); }
    @Override public byte[] fastInit(int channelId, byte[] input) { throw new UnsupportedOperationException(); }
    @Override public byte[] readMsg(int channelId, int numMsg, long timeout) { throw new UnsupportedOperationException(); }
    @Override public byte[] readMsg(int channelId, long maxWait) { throw new UnsupportedOperationException(); }
    @Override public void readMsg(int channelId, byte[] response, long timeout) { throw new UnsupportedOperationException(); }
    @Override public void stopMsgFilter(int channelId, int msgId) { }
}
