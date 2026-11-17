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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class PackagedStartTest {

    @Test
    public void noLauncherMeansNoRestart() {
        assertFalse(PackagedStart.needsRestart(null, new File("."), null));
        assertFalse(PackagedStart.needsRestart("", new File("."), null));
    }

    @Test
    public void restartsOnlyFromAnotherFolderAndOnlyOnce() throws IOException {
        final File own = Files.createTempDirectory("own").toFile();
        final File other = Files.createTempDirectory("other").toFile();
        final String launcher = new File(own, "RomRaider.exe").getPath();
        try {
            assertTrue(PackagedStart.needsRestart(launcher, other, null));
            assertFalse(PackagedStart.needsRestart(launcher, own, null));
            assertFalse(PackagedStart.needsRestart(launcher, other, "1"));
        } finally {
            own.delete();
            other.delete();
        }
    }

    @Test
    public void relativeFileArgumentsBecomeAbsolute() throws IOException {
        final File folder = Files.createTempDirectory("images").toFile();
        final File image = new File(folder, "image.bin");
        assertTrue(image.createNewFile());
        try {
            final List<String> command = PackagedStart.command("launcher.exe",
                    new String[] {"image.bin", "-logger", "missing.bin", image.getAbsolutePath()}, folder);
            assertEquals("launcher.exe", command.get(0));
            assertEquals(image.getAbsolutePath(), command.get(1));
            assertEquals("-logger", command.get(2));
            assertEquals("missing.bin", command.get(3));
            assertEquals(image.getAbsolutePath(), command.get(4));
            assertEquals(5, command.size());
        } finally {
            image.delete();
            folder.delete();
        }
    }
}
