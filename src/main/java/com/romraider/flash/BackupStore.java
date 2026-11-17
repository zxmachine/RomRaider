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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * The folder where whole reads of the ECU are saved, and which contents have
 * been read whole while the program runs. A read counts as the backup of an
 * ECU only for the same ROM ID and the same content (the CRCs of its erase
 * blocks), so a second ECU with the same ROM ID gets its own backup. A write
 * starts only after such a read.
 */
public final class BackupStore {
    private final File folder;
    private final Map<String, File> backups = new HashMap<String, File>();

    public BackupStore(File folder) {
        this.folder = folder;
    }

    public File getFolder() {
        return folder;
    }

    /**
     * Saves a whole read; it counts as the backup of the ECU with this ROM ID
     * and this content key.
     */
    public synchronized File save(byte[] image, String ecuRomId, String contentKey, String kind) throws FlashException {
        String id = ecuRomId == null || ecuRomId.length() == 0 ? "blank" : ecuRomId.replaceAll("[^A-Za-z0-9_-]", "_");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
        File file = new File(folder, kind + "-" + id + "-" + stamp + ".bin");
        for (int n = 2; file.exists(); n++) file = new File(folder, kind + "-" + id + "-" + stamp + "-" + n + ".bin");
        try {
            Files.createDirectories(folder.toPath());
            Files.write(file.toPath(), image);
        } catch (IOException e) {
            throw new FlashException("Cannot save the read of the ECU to " + file + ": " + e.getMessage(),
                    "Choose a backup folder you can write to, in the Flash view.");
        }
        backups.put(key(ecuRomId, contentKey), file);
        return file;
    }

    /** The backup of this ROM ID and content taken while the program runs, or null. */
    public synchronized File backupOf(String ecuRomId, String contentKey) {
        return backups.get(key(ecuRomId, contentKey));
    }

    private static String key(String romId, String contentKey) {
        return (romId == null ? "" : romId) + "|" + (contentKey == null ? "" : contentKey);
    }
}
