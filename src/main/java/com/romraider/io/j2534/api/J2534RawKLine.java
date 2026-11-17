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

package com.romraider.io.j2534.api;

import java.util.LinkedHashSet;
import java.util.Set;

import com.romraider.io.j2534.api.J2534Impl.Config;
import com.romraider.io.j2534.api.J2534Impl.Flag;
import com.romraider.io.j2534.api.J2534Impl.TxFlags;

/**
 * A raw K-line (ISO 9141) channel for talking to a boot loader or a flashing
 * kernel: no init, no checksum added by the adapter, no echo, no inter-byte
 * or inter-message delays, and every received byte passed. It also drives
 * the connector pins the reflash needs and remembers them, so that
 * {@link #close()} switches every one of them off again. Every call holds
 * the channel's lock, so that another thread (a shutdown that must switch
 * the pins off) can use it between the calls of the thread that runs a
 * session.
 */
public final class J2534RawKLine implements AutoCloseable {
    private final J2534 api;
    private final Set<Integer> pinsUsed = new LinkedHashSet<Integer>();
    private int deviceId = -1;
    private int channelId = -1;
    private int baud;

    public J2534RawKLine(J2534 api) {
        if (api == null) throw new IllegalArgumentException("api is null");
        this.api = api;
    }

    /** Opens the adapter. */
    public synchronized void open() {
        deviceId = api.open();
    }

    public synchronized boolean isOpen() {
        return deviceId >= 0;
    }

    /** Opens the channel at this baud rate (disconnecting an open one first). */
    public synchronized void connect(int baud) {
        if (channelId >= 0) disconnect();
        channelId = api.connect(deviceId, Flag.ISO9141_NO_CHECKSUM.getValue(), baud);
        this.baud = baud;
        api.setConfig(channelId,
                new ConfigItem(Config.LOOPBACK.getValue(), 0),
                new ConfigItem(Config.P1_MAX.getValue(), 0),
                new ConfigItem(Config.P3_MIN.getValue(), 0),
                new ConfigItem(Config.P4_MIN.getValue(), 0));
        api.startPassMsgFilter(channelId, (byte) 0, (byte) 0);
    }

    /** Changes the baud rate of the open channel in place (DATA_RATE). */
    public synchronized void setBaud(int baud) {
        api.setConfig(channelId, new ConfigItem(Config.DATA_RATE.getValue(), baud));
        this.baud = baud;
    }

    public synchronized int getBaud() {
        return baud;
    }

    public synchronized void disconnect() {
        if (channelId < 0) return;
        int ch = channelId;
        channelId = -1;
        api.disconnect(ch);
    }

    /** Sends the bytes as one message. */
    public synchronized void write(byte[] data, long timeout) {
        api.writeMsg(channelId, data, timeout, TxFlags.NO_FLAGS);
    }

    /** Whatever arrived within the timeout; empty when nothing did. */
    public synchronized byte[] readAvailable(long timeout) {
        return api.readAvailable(channelId, timeout);
    }

    public synchronized void clearBuffers() {
        api.clearBuffers(channelId);
    }

    /** Drives a pin: millivolts, {@link J2534#VOLTAGE_OFF} or {@link J2534#SHORT_TO_GROUND}. */
    public synchronized void setPin(int pin, int voltage) {
        pinsUsed.add(pin);
        api.setProgrammingVoltage(deviceId, pin, voltage);
    }

    /** The battery voltage on the connector, in volts. */
    public synchronized double batteryVolts() {
        return api.getVbattery(deviceId);
    }

    /**
     * Switches off every pin used, closes the channel and the adapter. Each
     * step is tried even if an earlier one fails; the first failure is thrown.
     */
    @Override
    public synchronized void close() {
        RuntimeException first = null;
        if (deviceId >= 0) {
            for (int pin : pinsUsed) {
                try {
                    api.setProgrammingVoltage(deviceId, pin, J2534.VOLTAGE_OFF);
                } catch (RuntimeException e) {
                    if (first == null) first = e;
                }
            }
        }
        pinsUsed.clear();
        try {
            disconnect();
        } catch (RuntimeException e) {
            if (first == null) first = e;
        }
        if (deviceId >= 0) {
            int dev = deviceId;
            deviceId = -1;
            try {
                api.close(dev);
            } catch (RuntimeException e) {
                if (first == null) first = e;
            }
        }
        if (first != null) throw first;
    }
}
