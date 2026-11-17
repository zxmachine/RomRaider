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

import com.romraider.io.j2534.api.J2534RawKLine;

/**
 * The kernel's frames, both ways: BE EF, a 16-bit length, the payload and
 * an 8-bit sum of every byte before it. One request, one reply.
 */
final class KernelLink {
    /** Why an exchange failed. */
    static final class LinkException extends Exception {
        private static final long serialVersionUID = 4419872371551101785L;

        LinkException(String message) {
            super(message);
        }
    }

    private final J2534RawKLine line;
    private final Timing timing;

    KernelLink(J2534RawKLine line, Timing timing) {
        this.line = line;
        this.timing = timing;
    }

    static byte[] frame(byte[] payload) {
        byte[] req = new byte[payload.length + 5];
        req[0] = (byte) 0xBE;
        req[1] = (byte) 0xEF;
        req[2] = (byte) (payload.length >> 8);
        req[3] = (byte) payload.length;
        System.arraycopy(payload, 0, req, 4, payload.length);
        int s = 0;
        for (int i = 0; i < req.length - 1; i++) s += req[i] & 0xFF;
        req[req.length - 1] = (byte) s;
        return req;
    }

    /** True for the kernel's error replies (0xF0-0xFF), which answer any request. */
    static boolean isError(int code) {
        return code >= 0xF0;
    }

    /**
     * Sends a request and waits for a whole reply frame with a good sum
     * whose code is one of the accepted ones or an error code; returns its
     * payload. The receive buffer is emptied before the request goes out,
     * so that a reply that came too late for an earlier request is not taken
     * for this one; frames with another code (a late reply, an echo of the
     * request) are skipped.
     */
    byte[] request(byte[] payload, long timeoutMs, int... accepted) throws LinkException, InterruptedException {
        try {
            line.clearBuffers();
            line.write(frame(payload), 2000);
        } catch (RuntimeException e) {
            throw new LinkException("sending failed: " + e.getMessage());
        }
        ByteArrayOutputStream rx = new ByteArrayOutputStream();
        int skipped = 0;
        long end = timing.now() + timeoutMs;
        while (timing.now() < end) {
            if (Thread.interrupted()) throw new InterruptedException();
            byte[] got;
            try {
                got = line.readAvailable(20);
            } catch (RuntimeException e) {
                throw new LinkException("receiving failed: " + e.getMessage());
            }
            rx.write(got, 0, got.length);
            while (true) {
                byte[] b = rx.toByteArray();
                int k = -1;
                for (int i = 0; i + 1 < b.length; i++) {
                    if ((b[i] & 0xFF) == 0xBE && (b[i + 1] & 0xFF) == 0xEF) {
                        k = i;
                        break;
                    }
                }
                if (k < 0 || k + 4 > b.length) break;
                int len = ((b[k + 2] & 0xFF) << 8) | (b[k + 3] & 0xFF);
                if (k + 5 + len > b.length) break;
                int t = 0;
                for (int i = k; i < k + 4 + len; i++) t += b[i] & 0xFF;
                if ((byte) t != b[k + 4 + len]) throw new LinkException("bad reply checksum");
                int code = len == 0 ? -1 : b[k + 4] & 0xFF;
                if (code >= 0 && (isError(code) || contains(accepted, code))) {
                    byte[] out = new byte[len];
                    System.arraycopy(b, k + 4, out, 0, len);
                    return out;
                }
                // not the reply to this request: drop it and look further
                skipped++;
                rx.reset();
                rx.write(b, k + 5 + len, b.length - (k + 5 + len));
            }
        }
        int n = rx.size();
        String why = n == 0 ? "no reply" : "incomplete reply (" + n + " bytes)";
        throw new LinkException(skipped == 0 ? why : why + " (" + skipped + " other frame(s) skipped)");
    }

    private static boolean contains(int[] codes, int code) {
        for (int c : codes) {
            if (c == code) return true;
        }
        return false;
    }
}
