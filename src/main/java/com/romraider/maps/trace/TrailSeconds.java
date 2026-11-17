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

package com.romraider.maps.trace;

import java.awt.FlowLayout;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.romraider.Settings;
import com.romraider.util.ResourceUtil;
import com.romraider.util.SettingsManager;

/** The table toolbar's "Trail" box: how many seconds of the live trace a table shows. */
public final class TrailSeconds extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final ResourceBundle RB = new ResourceUtil().getBundle(TrailSeconds.class.getName());

    /** @param onChange run after the length has changed (to repaint the table shown) */
    public TrailSeconds(final Runnable onChange) {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        final Settings settings = SettingsManager.getSettings();
        final JSpinner seconds = new JSpinner(new SpinnerNumberModel(settings.getLiveTraceSeconds(), 0,
                Settings.MAX_LIVE_TRACE_SECONDS, 1));
        ((JSpinner.DefaultEditor) seconds.getEditor()).getTextField().setColumns(3);
        String tip = text("TOOLTIP", "Seconds of the live trace shown behind the current point (0: the point only)");
        seconds.setToolTipText(tip);
        seconds.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                settings.setLiveTraceSeconds(((Number) seconds.getValue()).doubleValue());
                if (onChange != null) onChange.run();
            }
        });
        JLabel label = new JLabel(text("LABEL", "Trail"));
        label.setToolTipText(tip);
        add(label);
        add(seconds);
        add(new JLabel(text("UNIT", "s")));
    }

    private static String text(String key, String fallback) {
        try {
            return RB == null ? fallback : RB.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}
