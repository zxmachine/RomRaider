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

package com.romraider.shell;

import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.WindowConstants;

import org.apache.log4j.Logger;

import com.romraider.Settings;
import com.romraider.editor.ecu.ECUEditorManager;
import com.romraider.logger.ecu.EcuLogger;
import com.romraider.util.SettingsManager;
import com.romraider.util.ThreadUtil;

/**
 * The logger as a view. The logger is built on another thread the first time
 * the view is shown, with the editor for its live table cursor; its frame is
 * never shown: its content pane and menus move into the main window.
 */
final class LoggerView implements ShellView {
    private static final Logger LOG = Logger.getLogger(LoggerView.class);
    private MainWindow window;
    private EcuLogger logger;
    private Container content;
    private List<JMenu> menus = Collections.emptyList();

    @Override
    public String getId() {
        return LOGGER;
    }

    @Override
    public String getName() {
        return MainWindow.rb.getString("LOGGER");
    }

    @Override
    public void start(MainWindow window, String[] args) {
        this.window = window;
        final String[] loggerArgs = ShellArgs.startView(args).equals(LOGGER)
                ? args : new String[] {ShellArgs.LOGGER_ARG};
        ThreadUtil.runAsDaemon(new Runnable() {
            @Override
            public void run() {
                try {
                    // the logger hands itself back through Shell.hostLogger
                    EcuLogger.startLogger(WindowConstants.DISPOSE_ON_CLOSE,
                            ECUEditorManager.getECUEditorWithoutCreation(), loggerArgs);
                } catch (RuntimeException e) {
                    LOG.error("Logger start failed", e);
                }
            }
        });
    }

    /** The logger is built: take its content and menus. On the event thread. */
    void hosted(EcuLogger built) {
        logger = built;
        content = logger.getContentPane();
        menus = MenuLayout.menusOf(logger.getJMenuBar());
        logger.addPropertyChangeListener("title", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                window.titleChanged(LoggerView.this);
            }
        });
        window.viewReady(this);

        // pack() made the logger's frame displayable; it is never shown. It
        // saves its size and place when it stops: keep the values it had.
        logger.dispose();
        Settings settings = SettingsManager.getSettings();
        logger.setSize(settings.getLoggerWindowSize());
        logger.setLocation(settings.getLoggerWindowLocation());
        if (settings.isLoggerWindowMaximized()) logger.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    @Override
    public Container getContent() {
        return content;
    }

    @Override
    public List<JMenu> getMenus() {
        return menus;
    }

    @Override
    public String getTitle() {
        return logger == null ? null : logger.getTitle();
    }

    @Override
    public void activated() {
    }

    @Override
    public void handOver(String[] args) {
    }

    @Override
    public void stop(MainWindow window) {
        // stops logging and the plugins, and saves the logger's settings
        if (logger != null) logger.handleExit();
    }
}
