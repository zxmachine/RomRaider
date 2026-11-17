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

import com.romraider.ECUExec;

/**
 * The start class of the packaged program (package.xml). RomRaider finds its
 * i18n, plugins, customize and lib folders relative to the working folder, and
 * a packaged launcher is started from wherever the user is: from a shortcut,
 * from the command line, by opening an image file. When the working folder is
 * not the program's own folder, this starts the launcher again in that folder
 * and ends; otherwise it starts RomRaider.
 */
public final class PackagedStart {
    /** Set by the jpackage launcher: the path of the launcher. */
    static final String LAUNCHER_PROPERTY = "jpackage.app-path";
    /** Marks the second start, so that a start never repeats itself. */
    static final String RESTARTED_VARIABLE = "ROMRAIDER_STARTED_IN_FOLDER";

    private PackagedStart() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) throws Exception {
        final String launcher = System.getProperty(LAUNCHER_PROPERTY);
        final File workingFolder = new File("").getAbsoluteFile();
        if (needsRestart(launcher, workingFolder, System.getenv(RESTARTED_VARIABLE))) {
            final ProcessBuilder builder = new ProcessBuilder(command(launcher, args, workingFolder));
            builder.directory(new File(launcher).getAbsoluteFile().getParentFile());
            builder.environment().put(RESTARTED_VARIABLE, "1");
            builder.inheritIO();
            builder.start();
            return;
        }
        ECUExec.main(args);
    }

    /**
     * Whether the program has to be started again in its own folder: it was
     * started by a launcher, not already started again, and the working folder
     * is not the launcher's.
     */
    static boolean needsRestart(String launcher, File workingFolder, String restarted) {
        if (launcher == null || launcher.isEmpty() || restarted != null) {
            return false;
        }
        final File own = new File(launcher).getAbsoluteFile().getParentFile();
        return own != null && !own.equals(workingFolder.getAbsoluteFile());
    }

    /**
     * The command for the second start: the launcher and the arguments, with
     * each argument that names an existing file relative to the working folder
     * made absolute, since the working folder changes.
     */
    static List<String> command(String launcher, String[] args, File workingFolder) {
        final List<String> command = new ArrayList<String>();
        command.add(launcher);
        for (String arg : args) {
            final File file = new File(arg);
            if (!arg.startsWith("-") && !file.isAbsolute() && new File(workingFolder, arg).exists()) {
                command.add(new File(workingFolder, arg).getAbsolutePath());
            } else {
                command.add(arg);
            }
        }
        return command;
    }
}
