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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import com.romraider.flash.FlashException.FlashState;
import com.romraider.flash.KernelLink.LinkException;
import com.romraider.io.j2534.api.J2534;
import com.romraider.io.j2534.api.J2534RawKLine;

/**
 * A wrx02 session: the boot loader entry, the kernel upload, and the
 * kernel's read, compare and write, in the order and with the timing of a
 * host that has written these ECUs.
 *
 * <ol>
 * <li>Entry: the reflash pin (OBD pin 9) is grounded and the user cycles the
 * ignition. Until the boot loader answers, the pin is released for 200 ms and
 * grounded again, then the handshake 'M' frame is sent at 9600 baud.</li>
 * <li>Upload: the kernel goes in one scrambled 'S' frame; then the channel
 * reopens at the kernel's baud rate.</li>
 * <li>Read: 0x200 bytes at a time, each checked against the kernel's CRC-32 of
 * the same memory, three tries each.</li>
 * <li>Compare: the CRC-32 of each 16 KB block against the image.</li>
 * <li>Write: the blocks that differ and the marker block. Programming voltage
 * on pin 11, checked through the kernel; flash enable; the marker block erased
 * first; each block erased and programmed 4 KB at a time (8 buffer loads, then
 * a commit checked by CRC-32); the marker block programmed last; flash
 * disable, pin 11 off; compare again.</li>
 * </ol>
 *
 * Another thread may ask the session to stop ({@link #shutdownRequested()}):
 * it stops at the next safe point, and during a write after the command that
 * is running, with flash disable sent and the programming voltage off.
 */
final class Wrx02Session implements FlashSession {
    private static final byte[] HANDSHAKE = {0x4D, 0x00, (byte) 0xB3};
    /** Unanswered handshakes (about 1.5 s each) after which the user is asked to cycle the ignition again. */
    static final int RETRY_HINT_AFTER = 5;

    private static final String RETRY_UNCHANGED =
            "Turn the ignition off, check the adapter and the test-mode connector, and try again."
            + " Until the ignition is cycled the kernel may still be running; the next session tries it first.";

    private final Wrx02Method method;
    private final FlashContext ctx;
    private final FlashOptions options;
    private final KernelProfile profile;
    private final Timing timing;
    private final FlashListener out;
    private final J2534RawKLine line;
    private final KernelLink link;
    private EcuIdentity identity;
    /** The known kernel the kernel file is; null for an unknown kernel the user allowed. */
    private KnownKernel known;
    /** The running kernel's code has been checked against the kernel file. */
    private boolean kernelChecked;
    private volatile boolean writing;
    private volatile boolean flashEnableSent;
    private volatile boolean shutdown;
    private volatile boolean closed;
    private FlashBlock currentBlock;

    private Wrx02Session(Wrx02Method method, FlashContext ctx) {
        this.method = method;
        this.ctx = ctx;
        this.options = ctx.getOptions();
        this.profile = ctx.getKernel().getProfile();
        this.timing = ctx.getTiming();
        this.out = ctx.getListener();
        this.line = new J2534RawKLine(ctx.getAdapter());
        this.link = new KernelLink(line, timing);
    }

    static Wrx02Session open(Wrx02Method method, FlashContext ctx) throws FlashException {
        Wrx02Session s = new Wrx02Session(method, ctx);
        ActiveSessions.register(s);
        try {
            s.start();
            return s;
        } catch (FlashException e) {
            s.close();
            throw e;
        } catch (RuntimeException e) {
            s.close();
            throw new FlashException("The adapter failed: " + e.getMessage(), RETRY_UNCHANGED);
        }
    }

    private void start() throws FlashException {
        known = WritePolicy.checkKernel(ctx.getKernel(), options.knownKernels, options.allowUnknownKernel);
        log(known != null ? "kernel file: " + known.getName()
                : "kernel file: not a known kernel, used because \"Allow an unknown kernel\" is ticked");
        try {
            line.open();
        } catch (RuntimeException e) {
            throw new FlashException("The adapter did not open: " + e.getMessage(),
                    "Check that the adapter is plugged in and its driver installed, and that no other program"
                    + " (the logger, another flashing tool) is using it.");
        }
        battery();
        if (options.kernelMayBeRunning && kernelAnswers()) {
            if (kernelCodeMatches()) {
                kernelChecked = true;
                log("the kernel from the last session still answers; not loading it again");
                return;
            }
            log("the kernel still answering is not the chosen kernel file; entering the boot loader to load it");
        }
        enterBootLoader();
        uploadKernel();
    }

    /**
     * Whether the running kernel's code in RAM is the known kernel's, by the
     * CRC-32 the kernel gives of it. An unknown kernel the user allowed is not
     * checked (which of its bytes change while it runs is not known).
     */
    private boolean kernelCodeMatches() throws FlashException {
        if (known == null) {
            log("the kernel is not a known one: its code in RAM is not checked");
            return true;
        }
        int n = known.getCodeLength();
        long c;
        try {
            byte[] r = link.request(cat(new byte[] {0x02}, addr(KernelProfile.LOAD_ADDRESS),
                    new byte[] {0, 0, (byte) (n >> 8), (byte) n}), 2000, profile.reply(0x02));
            c = r.length == 5 && (r[0] & 0xFF) == profile.reply(0x02) ? be32(r, 1) : -1;
        } catch (LinkException e) {
            c = -1;
        } catch (InterruptedException e) {
            throw cancelled();
        }
        boolean same = c == known.getCodeCrc();
        log(String.format("kernel code in RAM 0x%05X-0x%05X: CRC-32 %s, the file's %08X: %s",
                KernelProfile.LOAD_ADDRESS, KernelProfile.LOAD_ADDRESS + n - 1,
                c < 0 ? "not given" : String.format("%08X", c), known.getCodeCrc(), same ? "the same" : "DIFFERENT"));
        return same;
    }

    /** Reads the battery voltage and reports it; NaN when the adapter can't. */
    private double battery() {
        try {
            double v = line.batteryVolts();
            out.battery(v);
            log(String.format("battery %.2f V", v));
            return v;
        } catch (RuntimeException e) {
            log("the adapter can't read the battery voltage: " + e.getMessage());
            return Double.NaN;
        }
    }

    /** A kernel left running answers its version request at its own baud rate. */
    private boolean kernelAnswers() throws FlashException {
        line.connect(profile.getBaud());
        try {
            byte[] ver = link.request(new byte[] {0x01}, 1000, profile.reply(0x01));
            return ver.length > 0 && (ver[0] & 0xFF) == profile.reply(0x01);
        } catch (LinkException e) {
            return false;
        } catch (InterruptedException e) {
            throw cancelled();
        }
    }

    private void enterBootLoader() throws FlashException {
        line.connect(KernelProfile.LOADER_BAUD);
        line.setPin(options.reflashPin, J2534.SHORT_TO_GROUND);
        String ask = String.format("Pin %d is grounded. Turn the ignition OFF, wait 3 seconds, then turn it ON."
                + " Waiting up to %d s for the boot loader...", options.reflashPin, options.waitSeconds);
        log(ask);
        out.instruction(ask);
        long start = timing.now();
        long deadline = start + options.waitSeconds * 1000L;
        boolean inLoader = false;
        int tries = 0;
        while (!inLoader && timing.now() < deadline) {
            checkStop("Turn the ignition off. Nothing was sent to the ECU but the handshake.");
            sleep(800);
            line.setPin(options.reflashPin, J2534.VOLTAGE_OFF);
            sleep(options.pulseMillis);
            line.setPin(options.reflashPin, J2534.SHORT_TO_GROUND);
            sleep(50);
            line.write(HANDSHAKE, 500);
            byte[] r = listen(400);
            if (r.length > 0) log("  'M' reply: " + hex(r, r.length));
            inLoader = indexOf(r, HANDSHAKE) >= 0;
            out.progress("Waiting for the boot loader", (int) (timing.now() - start), (int) (deadline - start));
            if (!inLoader && ++tries == RETRY_HINT_AFTER) {
                // An ECU that starts while the pin is released for a handshake starts its program, not the boot
                // loader, and then never answers: about one ignition-on in seven lands in such a release.
                String again = "No answer yet. Turn the ignition OFF again, wait 3 seconds, then turn it ON:"
                        + " an ignition turned on while pin " + options.reflashPin + " was released for the"
                        + " handshake starts the program instead of the boot loader. Still waiting...";
                log(again);
                out.instruction(again);
            }
        }
        if (!inLoader) {
            throw new FlashException("The boot loader never answered.",
                    "Check that the test-mode connector is connected, that the car's flash connector carries"
                    + " the adapter's pin 9 to the ECU, and that the ignition was turned off for 3 s and on"
                    + " again while RomRaider waited. Then try again: an ignition turned on during one of the"
                    + " short releases of pin 9 starts the program instead, and a second try usually works.");
        }
    }

    private void uploadKernel() throws FlashException {
        Kernel kernel = ctx.getKernel();
        log(String.format("boot loader answered; sending the kernel (%d bytes)", kernel.ramImage().length));
        line.write(kernel.uploadFrame(), 10000);
        byte[] r = listen(500);
        if (r.length > 0) {
            log("  after 'S': " + hex(r, r.length));
            int at = indexOf(r, new byte[] {0x66});
            if (at >= 0) {
                int code = at + 1 < r.length ? r[at + 1] & 0xFF : -1;
                throw new FlashException(String.format("The boot loader refused the kernel (error 0x%02X: %s).",
                        code, loaderError(code)),
                        "Check that the kernel file and the kernel profile belong together, then turn the"
                        + " ignition off and try again.");
            }
        }
        line.disconnect();
        line.connect(profile.getBaud());
        line.setPin(options.reflashPin, J2534.SHORT_TO_GROUND);
    }

    static String loaderError(int code) {
        switch (code) {
            case 0x01: return "bad signature";
            case 0x02: return "receive error";
            case 0x03: return "bad checksum";
            case 0x0F: return "a byte outside a frame";
            case 0x21: return "unknown command";
            case 0x22: return "length does not match";
            case 0x23: return "load address outside RAM";
            case 0x24: return "upload before the handshake";
            case 0x25: return "bad handshake frame";
            default: return "unknown error";
        }
    }

    @Override
    public EcuIdentity identify() throws FlashException {
        if (identity != null) return identity;
        int[] versionReplies = new int[KernelProfile.all().size()];
        for (int i = 0; i < versionReplies.length; i++) versionReplies[i] = KernelProfile.all().get(i).reply(0x01);
        byte[] ver;
        try {
            ver = link.request(new byte[] {0x01}, 1000, versionReplies);
        } catch (LinkException e) {
            throw new FlashException("The kernel is not answering: " + e.getMessage() + ".",
                    "Check that the kernel profile fits the kernel file (its baud rate), then turn the ignition"
                    + " off and try again.");
        } catch (InterruptedException e) {
            throw cancelled();
        }
        int code = ver.length == 0 ? -1 : ver[0] & 0xFF;
        if (code != profile.reply(0x01)) {
            for (KernelProfile other : KernelProfile.all()) {
                if (other != profile && code == other.reply(0x01)) {
                    throw new FlashException(String.format(
                            "The kernel answers with reply code 0x%02X, not 0x%02X: it is a kernel for the profile \"%s\".",
                            code, profile.reply(0x01), other.getName()),
                            "Choose that kernel profile, or the kernel file that fits this one; then turn the"
                            + " ignition off and try again.");
                }
            }
            throw new FlashException(String.format("The kernel is not answering: version reply %s.", hex(ver, 8)),
                    RETRY_UNCHANGED);
        }
        String version = new String(ver, 1, ver.length - 1, StandardCharsets.US_ASCII).replace("\0", "").trim();
        log("kernel: " + version);
        if (!kernelChecked) {
            if (!kernelCodeMatches()) {
                throw new FlashException("The kernel running in the ECU is not the kernel file: the CRC-32 of its code"
                        + " in RAM differs, or it gives none. The upload was damaged on the way.",
                        "Nothing was written. Turn the ignition off (that clears the ECU's RAM) and try again."
                        + " If it happens again, check the adapter, its cable and the OBD connector.");
            }
            kernelChecked = true;
        }
        byte[] id = kreq(cat(new byte[] {0x03}, addr(Hc16Y5Layout.ROM_ID_ADDRESS),
                new byte[] {0, (byte) Hc16Y5Layout.ROM_ID_LENGTH}), 2000, 0x03);
        if (id.length != Hc16Y5Layout.ROM_ID_LENGTH + 1) {
            throw failure(String.format("ROM ID read: reply %s", hex(id, 8)));
        }
        boolean blank = Hc16Y5Layout.isBlank(id, 1, Hc16Y5Layout.ROM_ID_LENGTH);
        identity = new EcuIdentity(version,
                blank ? "" : Hc16Y5Layout.romIdText(id, 1, Hc16Y5Layout.ROM_ID_LENGTH), blank);
        log("ECU: " + identity);
        return identity;
    }

    @Override
    public byte[] read() throws FlashException {
        identify();
        ByteArrayOutputStream img = new ByteArrayOutputStream(Hc16Y5Layout.IMAGE_SIZE);
        long started = timing.now();
        int retries = 0;
        int total = Hc16Y5Layout.IMAGE_SIZE / Hc16Y5Layout.CHUNK_SIZE;
        int done = 0;
        for (int[] range : Hc16Y5Layout.RANGES) {
            int lo = range[0];
            int hi = range[1];
            for (int a = lo; a <= hi; a += Hc16Y5Layout.CHUNK_SIZE) {
                checkStop("Nothing was written. The ECU is in the kernel: turn the ignition off"
                        + " (test-mode connector out) to start it normally.");
                int n = Math.min(Hc16Y5Layout.CHUNK_SIZE, hi - a + 1);
                byte[] data = null;
                for (int attempt = 1; ; attempt++) {
                    String why = null;
                    try {
                        byte[] d = link.request(cat(new byte[] {0x03}, addr(a),
                                new byte[] {(byte) (n >> 8), (byte) n}), 2000, profile.reply(0x03));
                        if (d.length != n + 1 || (d[0] & 0xFF) != profile.reply(0x03)) {
                            why = "read reply " + hex(d, 8);
                        } else {
                            data = new byte[n];
                            System.arraycopy(d, 1, data, 0, n);
                            byte[] c = null;
                            try {
                                c = link.request(cat(new byte[] {0x02}, addr(a),
                                        new byte[] {0, 0, (byte) (n >> 8), (byte) n}), 2000, profile.reply(0x02));
                            } catch (LinkException e) {
                                why = "crc: " + e.getMessage();
                            }
                            if (why == null) {
                                if (c.length != 5 || (c[0] & 0xFF) != profile.reply(0x02)) {
                                    why = "crc: " + hex(c, c.length);
                                } else {
                                    long kc = be32(c, 1);
                                    long hc = crc32(data, 0, n);
                                    if (kc != hc) why = String.format("CRC mismatch: kernel %08X, data %08X", kc, hc);
                                }
                            }
                        }
                    } catch (LinkException e) {
                        why = "read: " + e.getMessage();
                    } catch (InterruptedException e) {
                        throw cancelled();
                    }
                    if (why == null) break;
                    retries++;
                    log(String.format("  0x%05X: %s (try %d)", a, why, attempt));
                    if (attempt >= 3) {
                        throw new FlashException(String.format("The read gave up at 0x%05X after 3 tries (%s).", a, why),
                                RETRY_UNCHANGED);
                    }
                }
                img.write(data, 0, data.length);
                done++;
                out.progress("Reading", done, total);
                if (((a - lo) / Hc16Y5Layout.CHUNK_SIZE) % 32 == 0) log(String.format("  0x%05X", a));
            }
        }
        byte[] bytes = img.toByteArray();
        log(String.format("read %d bytes in %d s (%d retries)", bytes.length, (timing.now() - started) / 1000, retries));
        return bytes;
    }

    @Override
    public CompareResult compare(byte[] image) throws FlashException {
        return compare(image, true);
    }

    @Override
    public CompareResult verify(byte[] image) throws FlashException {
        return compare(image, false);
    }

    private CompareResult compare(byte[] image, boolean stoppable) throws FlashException {
        if (image == null || image.length != Hc16Y5Layout.IMAGE_SIZE) {
            throw new FlashException(String.format("The image must be %d bytes.", Hc16Y5Layout.IMAGE_SIZE),
                    "Open a whole image for this ECU.");
        }
        identify();
        List<CompareResult.Entry> entries = new ArrayList<CompareResult.Entry>();
        int i = 0;
        for (FlashBlock b : Hc16Y5Layout.blocks()) {
            if (stoppable) checkStop("Nothing was written.");
            byte[] r = kreq(cat(new byte[] {0x02}, addr(b.getAddress()), new byte[] {0, 0, 0x40, 0}), 3000, 0x02);
            if (r.length != 5) throw failure("CRC reply " + hex(r, r.length));
            CompareResult.Entry e = new CompareResult.Entry(b, be32(r, 1), crc32(image, b.getFileOffset(), b.getSize()));
            log("  " + e);
            entries.add(e);
            out.progress("Comparing", ++i, Hc16Y5Layout.blocks().size());
        }
        return new CompareResult(entries);
    }

    @Override
    public WriteResult write(WriteRequest request) throws FlashException {
        WritePolicy.preflight(method, options, request);
        byte[] image = request.getImage();
        String imageId = method.romId(image);
        if (!request.isDryRun()) {
            WritePolicy.checkBattery(battery(), options.minBatteryVolts);
        }
        EcuIdentity ecu = identify();
        if (!ecu.isBlank() && !ecu.getRomId().equals(imageId)) {
            log(String.format("the ECU holds ROM ID %s, the image has %s", ecu.getRomId(), imageId));
        }
        if (!request.isDryRun()) WritePolicy.checkRomId(ecu, imageId, request.isAllowOtherRomId());

        log("comparing:");
        CompareResult before = compare(image, true);
        List<FlashBlock> diff = before.differing();
        if (diff.isEmpty()) {
            log("nothing to write: the ECU already holds this image");
            return new WriteResult(WriteResult.Outcome.NOTHING_TO_WRITE, diff, null);
        }
        List<FlashBlock> order = WritePolicy.writeOrder(diff);
        if (order.size() > diff.size()) {
            log("the marker block is written too, although it does not differ: erased first and programmed last,"
                    + " so that a stopped write never leaves a valid marker over half-written blocks");
        }
        if (request.isDryRun()) {
            log(String.format("dry run: %d block(s) would be written: %s", order.size(), names(order)));
            return new WriteResult(WriteResult.Outcome.DRY_RUN, order, null);
        }

        File backup = backup(ecu, before);

        String typed = request.getConfirmer() == null ? null
                : request.getConfirmer().confirm(order, imageId, describe(image),
                        WritePolicy.confirmationPhrase(imageId));
        WritePolicy.checkConfirmation(typed, imageId);
        checkStop("Nothing was written.");
        WritePolicy.checkBattery(battery(), options.minBatteryVolts);

        // from here on only an error or a program shutdown stops the write, and only between commands
        log(String.format("writing %d block(s): %s", order.size(), names(order)));
        int pages = order.size() * (Hc16Y5Layout.BLOCK_SIZE / Hc16Y5Layout.PAGE_SIZE);
        int pagesDone = 0;
        line.setPin(options.programmingPin, options.programmingMillivolts);
        try {
            sleepThrough(300);
            byte[] v = kreq(new byte[] {0x04}, 2000, 0x04);
            if (v.length < 3) throw failure("programming voltage reply " + hex(v, v.length));
            int an = ((v[1] & 0xFF) << 8) | (v[2] & 0xFF);
            log(String.format("programming voltage: 0x%04X (window 0x%04X-0x%04X)", an, profile.getVppMin(),
                    profile.getVppMax()));
            WritePolicy.checkProgrammingVoltage(an, profile);
            flashEnableSent = true;
            kreq(new byte[] {0x20}, 1000, 0x20);
            log("flash enable");
            writing = true;
            FlashBlock marker = order.get(order.size() - 1);
            currentBlock = marker;
            byte[] r = erase(marker);
            log(String.format("  erased %s 0x%05X first (marker block) in %d pulse(s)",
                    marker.getName(), marker.getAddress(), r.length > 2 ? r[2] & 0xFF : 0));
            for (FlashBlock b : order) {
                currentBlock = b;
                if (b != marker) {
                    checkBatteryBetweenBlocks();
                    stopIfShutdown();
                    r = erase(b);
                    log(String.format("  erased %s 0x%05X in %d pulse(s)", b.getName(), b.getAddress(),
                            r.length > 2 ? r[2] & 0xFF : 0));
                }
                for (int page = 0; page < Hc16Y5Layout.BLOCK_SIZE; page += Hc16Y5Layout.PAGE_SIZE) {
                    stopIfShutdown();
                    int a = b.getAddress() + page;
                    int o = b.getFileOffset() + page;
                    for (int k = 0; k < Hc16Y5Layout.PAGE_SIZE; k += Hc16Y5Layout.CHUNK_SIZE) {
                        WritePolicy.checkWritable(a + k, Hc16Y5Layout.CHUNK_SIZE);
                        byte[] chunk = new byte[Hc16Y5Layout.CHUNK_SIZE];
                        System.arraycopy(image, o + k, chunk, 0, chunk.length);
                        kreq(cat(new byte[] {0x22}, addr(a + k), chunk), 3000, 0x22);
                    }
                    WritePolicy.checkWritable(a, Hc16Y5Layout.PAGE_SIZE);
                    long crc = crc32(image, o, Hc16Y5Layout.PAGE_SIZE);
                    r = kreq(cat(new byte[] {0x24}, addr(a), new byte[] {0x10, 0x00}, be32(crc)), 15000, 0x24);
                    log(String.format("  programmed 0x%05X-0x%05X (crc32 %08X) in %d pulse(s)",
                            a, a + Hc16Y5Layout.PAGE_SIZE - 1, crc, r.length > 2 ? r[2] & 0xFF : 0));
                    out.progress("Writing", ++pagesDone, pages);
                }
            }
        } catch (RuntimeException e) {
            // an adapter or program error while erasing or programming: report it as a stopped write
            if (writing) throw failure("error: " + e);
            throw e;
        } finally {
            endWrite();
        }
        currentBlock = null;
        log("verifying:");
        CompareResult after = compare(image, false);
        if (!after.isSame()) {
            throw new FlashException(String.format("VERIFY FAILED: %d block(s) differ: %s.",
                    after.differing().size(), names(after.differing())), recovery(), FlashState.PARTLY_WRITTEN);
        }
        writing = false;
        log("flash correct");
        return new WriteResult(WriteResult.Outcome.WRITTEN, order, backup);
    }

    /** Flash disable (when enable was sent) and the programming voltage off, whatever happened. */
    private void endWrite() {
        if (flashEnableSent) {
            try {
                link.request(new byte[] {0x21}, 1000, profile.reply(0x21));
                log("flash disable");
            } catch (LinkException e) {
                log("flash disable: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log("flash disable: " + e);
            }
            flashEnableSent = false;
        }
        pinOff();
    }

    /**
     * Takes a whole read of the ECU as its backup, unless one of this very
     * content (same ROM ID, same block CRCs) was saved while the program runs.
     */
    private File backup(EcuIdentity ecu, CompareResult before) throws FlashException {
        String key = contentKey(before);
        File earlier = ctx.getBackups().backupOf(ecu.getRomId(), key);
        if (earlier != null) {
            log("a whole read of this ECU's present content was saved earlier in this run: " + earlier);
            return null;
        }
        log("reading the whole ECU as a backup before writing");
        byte[] data = read();
        if (!Hc16Y5Layout.contentKey(data).equals(key)) {
            throw new FlashException("The backup read does not match the ECU's block CRCs.",
                    "Turn the ignition off and try again; if it happens again, check the adapter's connection.");
        }
        File f = ctx.getBackups().save(data, ecu.getRomId(), key, "backup");
        log("backup saved: " + f);
        return f;
    }

    private static String contentKey(CompareResult r) {
        StringBuilder sb = new StringBuilder();
        for (CompareResult.Entry e : r.getEntries()) sb.append(String.format("%08X", e.getEcuCrc()));
        return sb.toString();
    }

    /** What the confirmation shows of the image: ROM ID, CRC-32 and word sum. */
    static String describe(byte[] image) {
        int sum = Hc16Y5Layout.wordSum(image);
        return String.format("ROM ID %s, CRC-32 %08X, word sum 0x%04X (%s)", Hc16Y5Layout.romId(image),
                crc32(image, 0, image.length), sum, sum == Hc16Y5Layout.CHECKSUM_TARGET ? "checksum right" : "checksum WRONG");
    }

    private void checkBatteryBetweenBlocks() throws FlashException {
        double v = battery();
        if (Double.isNaN(v)) return;    // read at the start; one failed reading does not stop a write
        if (!(v >= options.minBatteryVolts)) {
            throw failure(String.format("the battery dropped to %.2f V, below the %.2f V minimum",
                    v, options.minBatteryVolts));
        }
    }

    private void stopIfShutdown() throws FlashException {
        if (shutdown) throw failure("stopped because the program is closing");
    }

    private byte[] erase(FlashBlock b) throws FlashException {
        WritePolicy.checkWritable(b.getAddress(), b.getSize());
        return kreq(cat(new byte[] {0x25}, addr(b.getAddress())), 15000, 0x25);
    }

    private void pinOff() {
        try {
            line.setPin(options.programmingPin, J2534.VOLTAGE_OFF);
            log(String.format("pin %d off", options.programmingPin));
        } catch (RuntimeException e) {
            log(String.format("pin %d off failed: %s", options.programmingPin, e.getMessage()));
        }
    }

    /** One kernel command whose reply must carry the right reply code. */
    private byte[] kreq(byte[] payload, long timeoutMs, int request) throws FlashException {
        byte[] r;
        try {
            r = link.request(payload, timeoutMs, profile.reply(request));
        } catch (LinkException e) {
            throw failure(String.format("command 0x%02X: %s", payload[0], e.getMessage()));
        } catch (InterruptedException e) {
            if (writing) {
                Thread.currentThread().interrupt();
                throw failure(String.format("command 0x%02X: interrupted", payload[0]));
            }
            throw cancelled();
        }
        int code = r.length == 0 ? -1 : r[0] & 0xFF;
        if (code != profile.reply(request)) {
            String why;
            switch (code) {
                case 0xF0: why = "bad length"; break;
                case 0xF2: why = "program/erase failed"; break;
                case 0xF3: why = "programming voltage low"; break;
                case 0xF4: why = "programming voltage high"; break;
                case 0xF5: why = "CRC mismatch"; break;
                case 0xFF: why = "unknown command"; break;
                default: why = "unexpected reply"; break;
            }
            throw failure(String.format("command 0x%02X: reply %s (%s)", payload[0], hex(r, 8), why));
        }
        return r;
    }

    /** A failed command, with the next step that fits how far the session got. */
    private FlashException failure(String what) {
        if (writing) {
            String where = currentBlock == null ? "" : String.format(" at %s 0x%05X", currentBlock.getName(),
                    currentBlock.getAddress());
            return new FlashException("WRITE STOPPED" + where + ": " + what + ".", recovery(), FlashState.PARTLY_WRITTEN);
        }
        return new FlashException(what + ".", RETRY_UNCHANGED);
    }

    private String recovery() {
        File folder = ctx.getBackups().getFolder();
        return "The ECU's flash is partly written. The marker block was erased first, so the boot loader will not"
                + " start the partly written program, and the boot loader itself is untouched: the ECU can be"
                + " written again. Leave the test-mode connector in and press Write again (the kernel may still be"
                + " running and is tried first; if it does not answer, turn the ignition off and on when asked)."
                + " Don't try to start the engine until a write ends with \"flash correct\". The read taken before"
                + " the write is in " + folder + ".";
    }

    private FlashCancelledException cancelled() {
        return new FlashCancelledException("Nothing was written. The ECU may be in the kernel: turn the ignition off"
                + " (test-mode connector out) to start it normally.");
    }

    private void checkStop(String nextStep) throws FlashException {
        if (shutdown) throw new FlashCancelledException(nextStep);
        ctx.getCancel().check(nextStep);
    }

    @Override
    public void shutdownRequested() {
        shutdown = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void emergencyStop() {
        if (closed) return;
        if (flashEnableSent) {
            try {
                link.request(new byte[] {0x21}, 500, profile.reply(0x21));
            } catch (Exception e) {
                // nothing more can be done; the pins go off below
            }
        }
        close();
    }

    @Override
    public void close() {
        try {
            line.close();
        } catch (RuntimeException e) {
            log("closing the adapter: " + e.getMessage());
        }
        if (!closed) {
            closed = true;
            ActiveSessions.unregister(this);
            log("session closed: pins off. The ECU stays in the kernel until the ignition is turned off"
                    + " (with the test-mode connector out, it then starts normally).");
        }
    }

    private byte[] listen(long ms) throws FlashException {
        ByteArrayOutputStream rx = new ByteArrayOutputStream();
        long deadline = timing.now() + ms;
        while (timing.now() < deadline) {
            byte[] b = line.readAvailable(50);
            rx.write(b, 0, b.length);
        }
        return rx.toByteArray();
    }

    private void sleep(long ms) throws FlashException {
        try {
            timing.sleep(ms);
        } catch (InterruptedException e) {
            throw cancelled();
        }
    }

    /** A wait that an interrupt does not cut short (while writing). */
    private void sleepThrough(long ms) {
        long end = timing.now() + ms;
        boolean interrupted = false;
        while (timing.now() < end) {
            try {
                timing.sleep(end - timing.now());
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private void log(String line) {
        out.log(line);
    }

    static byte[] addr(int a) {
        return new byte[] {0, (byte) (a >> 16), (byte) (a >> 8), (byte) a};
    }

    static byte[] be32(long v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }

    static long be32(byte[] b, int at) {
        return ((long) (b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    static long crc32(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    static byte[] cat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, at, p.length);
            at += p.length;
        }
        return out;
    }

    static int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i + pattern.length <= data.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length && match; j++) match = data[i + j] == pattern[j];
            if (match) return i;
        }
        return -1;
    }

    static String hex(byte[] b, int max) {
        if (b == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(max, b.length); i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String names(List<FlashBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (FlashBlock b : blocks) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(b.getName());
        }
        return sb.toString();
    }
}
