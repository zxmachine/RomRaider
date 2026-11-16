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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Test;

public class ShellArgsTest {

    @Test
    public void oneWindowUnlessSeparateOrFullScreenOrTouch() {
        assertTrue(ShellArgs.wantsMainWindow(new String[0]));
        assertTrue(ShellArgs.wantsMainWindow(null));
        assertTrue(ShellArgs.wantsMainWindow(new String[] {"-logger"}));
        assertTrue(ShellArgs.wantsMainWindow(new String[] {"image.bin"}));
        assertFalse(ShellArgs.wantsMainWindow(new String[] {"-separate"}));
        assertFalse(ShellArgs.wantsMainWindow(new String[] {"-SEPARATE", "image.bin"}));
        assertFalse(ShellArgs.wantsMainWindow(new String[] {"-logger.fullscreen"}));
        assertFalse(ShellArgs.wantsMainWindow(new String[] {"-logger.touch"}));
    }

    @Test
    public void separateSwitchIsRemoved() {
        assertArrayEquals(new String[] {"image.bin"},
                ShellArgs.withoutShellArgs(new String[] {"-separate", "image.bin"}));
        assertArrayEquals(new String[] {"-logger"},
                ShellArgs.withoutShellArgs(new String[] {"-logger", "-Separate"}));
        assertArrayEquals(new String[0], ShellArgs.withoutShellArgs(null));
    }

    @Test
    public void startViewFollowsLoggerSwitches() {
        assertEquals(ShellView.EDITOR, ShellArgs.startView(new String[0]));
        assertEquals(ShellView.EDITOR, ShellArgs.startView(new String[] {"image.bin"}));
        assertEquals(ShellView.LOGGER, ShellArgs.startView(new String[] {"-logger"}));
        assertEquals(ShellView.LOGGER, ShellArgs.startView(new String[] {"-Logger.Touch"}));
        assertEquals(ShellView.LOGGER, ShellArgs.startView(new String[] {"-logger.fullscreen"}));
    }

    @Test
    public void imagePathSkipsSwitchesAndEmptyWords() {
        assertNull(ShellArgs.imagePath(null));
        assertNull(ShellArgs.imagePath(new String[0]));
        // a later start with no arguments hands over one empty word
        assertNull(ShellArgs.imagePath(new String[] {""}));
        assertNull(ShellArgs.imagePath(new String[] {"-logger"}));
        assertEquals("image.bin", ShellArgs.imagePath(new String[] {"image.bin"}));
        assertEquals("image.bin", ShellArgs.imagePath(new String[] {"-logger", "image.bin"}));
    }

    @Test
    public void imagePathJoinsAPathSplitAtSpaces() throws IOException {
        File dir = Files.createTempDirectory("shell args").toFile();
        File image = new File(dir, "my image.bin");
        try {
            assertTrue(image.createNewFile());
            String[] words = image.getPath().split(" ");
            assertTrue(words.length > 1);
            assertEquals(image.getPath(), ShellArgs.imagePath(words));
        } finally {
            image.delete();
            dir.delete();
        }
    }

    @Test
    public void imagePathTakesTheFirstWordWhenOnlyItIsAFile() throws IOException {
        File image = File.createTempFile("shellargs", ".bin");
        try {
            assertEquals(image.getPath(),
                    ShellArgs.imagePath(new String[] {image.getPath(), "second.bin"}));
        } finally {
            image.delete();
        }
    }
}
