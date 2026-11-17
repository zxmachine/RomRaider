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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** How a write ended. */
public final class WriteResult {
    public enum Outcome {
        /** The ECU already held the image. */
        NOTHING_TO_WRITE,
        /** A dry run: compared only. */
        DRY_RUN,
        /** Blocks were written and the compare after the write found them all equal. */
        WRITTEN
    }

    private final Outcome outcome;
    private final List<FlashBlock> blocks;
    private final File backup;

    public WriteResult(Outcome outcome, List<FlashBlock> blocks, File backup) {
        this.outcome = outcome;
        this.blocks = Collections.unmodifiableList(new ArrayList<FlashBlock>(blocks));
        this.backup = backup;
    }

    public Outcome getOutcome() { return outcome; }

    /** The blocks written (or, for a dry run, that would be), in the order of writing. */
    public List<FlashBlock> getBlocks() { return blocks; }

    /** The backup read taken before this write, or null when an earlier one counted. */
    public File getBackup() { return backup; }
}
