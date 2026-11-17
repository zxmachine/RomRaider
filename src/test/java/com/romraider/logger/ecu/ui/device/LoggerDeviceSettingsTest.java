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

package com.romraider.logger.ecu.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;

import com.romraider.Settings;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;

/** The logger's device lock in the settings: its rules, and that it is saved and read back. */
public class LoggerDeviceSettingsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anyByDefault() {
        final Settings settings = new Settings();
        assertEquals(Settings.DEVICE_AUTO, settings.getLoggerDeviceType());
        assertEquals("", settings.getLoggerDeviceName());
        assertFalse(settings.isLoggerDeviceLocked());
    }

    @Test
    public void lockedToJ2534OrSerial() {
        final Settings settings = new Settings();
        settings.setLoggerDevice(Settings.DEVICE_J2534, "C:\\drivers\\adapter_64.dll");
        assertTrue(settings.isLoggerDeviceLocked());
        assertEquals(Settings.DEVICE_J2534, settings.getLoggerDeviceType());
        assertEquals("C:\\drivers\\adapter_64.dll", settings.getLoggerDeviceName());
        settings.setLoggerDevice(Settings.DEVICE_SERIAL, "COM7");
        assertTrue(settings.isLoggerDeviceLocked());
        assertEquals(Settings.DEVICE_SERIAL, settings.getLoggerDeviceType());
        assertEquals("COM7", settings.getLoggerDeviceName());
    }

    @Test
    public void anUnknownTypeOrNoNameIsAny() {
        final Settings settings = new Settings();
        settings.setLoggerDevice("usb", "X");
        assertFalse(settings.isLoggerDeviceLocked());
        settings.setLoggerDevice(Settings.DEVICE_SERIAL, "");
        assertFalse(settings.isLoggerDeviceLocked());
        settings.setLoggerDevice(Settings.DEVICE_J2534, null);
        assertFalse(settings.isLoggerDeviceLocked());
        settings.setLoggerDevice(Settings.DEVICE_AUTO, "COM7");
        assertFalse(settings.isLoggerDeviceLocked());
        assertEquals("", settings.getLoggerDeviceName());
    }

    @Test
    public void savedAndReadBack() throws Exception {
        final Settings settings = new Settings();
        settings.setLoggerDevice(Settings.DEVICE_J2534, "C:\\drivers\\adapter_64.dll");
        final Settings read = roundTrip(settings);
        assertEquals(Settings.DEVICE_J2534, read.getLoggerDeviceType());
        assertEquals("C:\\drivers\\adapter_64.dll", read.getLoggerDeviceName());

        settings.setLoggerDevice(Settings.DEVICE_AUTO, "");
        assertFalse(roundTrip(settings).isLoggerDeviceLocked());
    }

    @Test
    public void settingsWithoutTheElementAreAny() throws Exception {
        final File file = folder.newFile("old.xml");
        java.nio.file.Files.write(file.toPath(),
                "<settings><logger><serial port=\"COM3\" refresh=\"false\"/></logger></settings>".getBytes("UTF-8"));
        assertFalse(read(file).isLoggerDeviceLocked());
    }

    private Settings roundTrip(Settings settings) throws IOException, Exception {
        final File file = folder.newFile();
        new DOMSettingsBuilder().buildSettings(settings, file, new JProgressPane(), "test");
        return read(file);
    }

    private static Settings read(File file) throws Exception {
        final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        return new DOMSettingsUnmarshaller().unmarshallSettings(doc.getDocumentElement());
    }
}
