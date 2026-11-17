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

import static org.apache.log4j.Logger.getLogger;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.log4j.Logger;

import com.fazecast.jSerialComm.SerialPort;
import com.romraider.Settings;
import com.romraider.io.j2534.api.J2534Library;
import com.romraider.io.j2534.api.J2534LibraryLocator;
import com.romraider.util.ResourceUtil;

/**
 * The device the logger uses: "any", which tries the J2534 adapters found and then the serial port as RomRaider
 * always did, or one J2534 adapter, J2534 DLL or serial port that the logger is locked to. A locked device is the
 * only one the logger opens: when it fails, the logger tries it again and nothing else
 * (Settings.isLoggerDeviceLocked, read by QueryManagerImpl and ConnectionManagerFactory). The lists are read again
 * each time the box opens.
 */
public final class DeviceChooser extends JComboBox<DeviceChooser.Choice> {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = getLogger(DeviceChooser.class);
    private static final ResourceBundle RB = new ResourceUtil().getBundle(DeviceChooser.class.getName());

    private final Settings settings;
    private final Runnable onChange;
    private boolean filling;

    /**
     * @param settings the settings the choice is kept in
     * @param onChange run after the user has chosen another device (to reconnect)
     */
    public DeviceChooser(Settings settings, Runnable onChange) {
        this.settings = settings;
        this.onChange = onChange;
        setToolTipText(text("TOOLTIP", "The device the logger uses"));
        fill();
        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                fill();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chosen();
            }
        });
    }

    /** The text of the label shown before the box. */
    public String getLabelText() {
        return text("LABEL", "Device:");
    }

    private void chosen() {
        if (filling) return;
        final Choice choice = (Choice) getSelectedItem();
        if (choice == null) return;
        String type = choice.type;
        String name = choice.name;
        if (choice.browse) {
            final JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter(text("DLL_FILES", "J2534 DLL (*.dll)"), "dll"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                fill();
                return;
            }
            type = Settings.DEVICE_J2534;
            name = chooser.getSelectedFile().getPath();
        }
        if (type.equals(settings.getLoggerDeviceType()) && name.equalsIgnoreCase(settings.getLoggerDeviceName())) {
            if (choice.browse) fill();
            return;
        }
        settings.setLoggerDevice(type, name);
        LOGGER.info("Logger device: " + settings.getLoggerDeviceType() + " " + settings.getLoggerDeviceName());
        fill();
        onChange.run();
    }

    private void fill() {
        filling = true;
        try {
            removeAllItems();
            final String lockedType = settings.getLoggerDeviceType();
            final String lockedName = settings.getLoggerDeviceName();
            final boolean locked = settings.isLoggerDeviceLocked();
            final Choice any = new Choice(Settings.DEVICE_AUTO, "",
                    text("ANY", "Any (J2534 adapters, then the COM port)"), false);
            addItem(any);
            Choice selected = locked ? null : any;

            final List<Choice> found = new ArrayList<Choice>();
            for (J2534Library library : j2534Libraries()) {
                found.add(new Choice(Settings.DEVICE_J2534, library.getLibrary(), "J2534: " + library.getVendor(), false));
            }
            if (locked && Settings.DEVICE_J2534.equals(lockedType) && !contains(found, lockedType, lockedName)) {
                found.add(new Choice(lockedType, lockedName, "J2534: " + new File(lockedName).getName(), false));
            }
            final String serial = text("SERIAL", "Serial port");
            for (String port : serialPorts()) {
                found.add(new Choice(Settings.DEVICE_SERIAL, port, serial + " " + port, false));
            }
            if (locked && Settings.DEVICE_SERIAL.equals(lockedType) && !contains(found, lockedType, lockedName)) {
                found.add(new Choice(lockedType, lockedName,
                        serial + " " + lockedName + " " + text("NOT_PRESENT", "(not present)"), false));
            }
            for (Choice choice : found) {
                addItem(choice);
                if (locked && choice.matches(lockedType, lockedName)) selected = choice;
            }
            addItem(new Choice(Settings.DEVICE_J2534, "", text("BROWSE", "J2534 DLL file..."), true));
            setSelectedItem(selected);
        } finally {
            filling = false;
        }
    }

    private Set<J2534Library> j2534Libraries() {
        try {
            return J2534LibraryLocator.getLibraries(settings.getTransportProtocol().toUpperCase());
        } catch (RuntimeException e) {
            LOGGER.warn("Cannot list the J2534 libraries", e);
            return new HashSet<J2534Library>();
        }
    }

    private static Set<String> serialPorts() {
        final Set<String> names = new TreeSet<String>();
        try {
            for (SerialPort port : SerialPort.getCommPorts()) {
                names.add(port.getSystemPortName());
            }
        } catch (Exception | LinkageError e) {
            LOGGER.warn("Cannot list the serial ports: " + e);
        }
        return names;
    }

    private static boolean contains(List<Choice> choices, String type, String name) {
        for (Choice choice : choices) {
            if (choice.matches(type, name)) return true;
        }
        return false;
    }

    private static String text(String key, String fallback) {
        try {
            return RB == null ? fallback : RB.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }

    /** One entry of the box: a device type and name, or the entry that browses for a J2534 DLL. */
    public static final class Choice {
        final String type;
        final String name;
        final String label;
        final boolean browse;

        Choice(String type, String name, String label, boolean browse) {
            this.type = type;
            this.name = name;
            this.label = label;
            this.browse = browse;
        }

        boolean matches(String otherType, String otherName) {
            return !browse && type.equals(otherType) && name.equalsIgnoreCase(otherName);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
