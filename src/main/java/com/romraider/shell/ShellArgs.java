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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the command line for the main window: whether to open one window,
 * which view to start with, and which image to open.
 */
public final class ShellArgs {
    /** Opens the editor and the logger in separate windows, as before the main window. */
    public static final String SEPARATE_ARG = "-separate";
    static final String LOGGER_ARG = "-logger";
    static final String LOGGER_FULLSCREEN_ARG = "-logger.fullscreen";
    static final String LOGGER_TOUCH_ARG = "-logger.touch";

    private ShellArgs() {
        throw new UnsupportedOperationException();
    }

    /**
     * True when the program opens one main window. The old separate windows
     * stay for -separate, and for the full-screen and touch loggers.
     */
    public static boolean wantsMainWindow(String[] args) {
        return !contains(args, SEPARATE_ARG)
                && !contains(args, LOGGER_FULLSCREEN_ARG)
                && !contains(args, LOGGER_TOUCH_ARG);
    }

    /** The arguments without the switches that only this class reads. */
    public static String[] withoutShellArgs(String[] args) {
        List<String> kept = new ArrayList<String>();
        if (args != null) {
            for (String arg : args) {
                if (!SEPARATE_ARG.equalsIgnoreCase(arg)) kept.add(arg);
            }
        }
        return kept.toArray(new String[0]);
    }

    /** The view to start with: the logger for any logger switch, else the editor. */
    public static String startView(String[] args) {
        return contains(args, LOGGER_ARG)
                || contains(args, LOGGER_FULLSCREEN_ARG)
                || contains(args, LOGGER_TOUCH_ARG)
                ? ShellView.LOGGER : ShellView.EDITOR;
    }

    /**
     * The image file named in the arguments, or null. A path handed over by
     * a later start arrives split at its spaces, so the words that are not
     * switches are joined again; when that names no file but the first word
     * does, the first word is the image.
     */
    public static String imagePath(String[] args) {
        List<String> words = new ArrayList<String>();
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.trim().length() > 0 && !arg.startsWith("-")) words.add(arg);
            }
        }
        if (words.isEmpty()) return null;
        String joined = String.join(" ", words);
        if (words.size() > 1 && !new File(joined).exists() && new File(words.get(0)).exists()) {
            return words.get(0);
        }
        return joined;
    }

    static boolean contains(String[] args, String wanted) {
        if (args == null) return false;
        for (String arg : args) {
            if (wanted.equalsIgnoreCase(arg)) return true;
        }
        return false;
    }
}
