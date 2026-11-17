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

import com.romraider.io.j2534.api.J2534;

/** Everything a flash session needs: the adapter, the kernel, the settings and where to report. */
public final class FlashContext {
    private final J2534 adapter;
    private final Kernel kernel;
    private final FlashOptions options;
    private final BackupStore backups;
    private final Timing timing;
    private final FlashListener listener;
    private final CancelToken cancel;

    public FlashContext(J2534 adapter, Kernel kernel, FlashOptions options, BackupStore backups,
            Timing timing, FlashListener listener, CancelToken cancel) {
        if (adapter == null || kernel == null || options == null || backups == null) {
            throw new IllegalArgumentException("adapter, kernel, options and backups are needed");
        }
        this.adapter = adapter;
        this.kernel = kernel;
        this.options = options.copy();
        this.backups = backups;
        this.timing = timing == null ? Timing.SYSTEM : timing;
        this.listener = listener == null ? FlashListener.NONE : listener;
        this.cancel = cancel == null ? new CancelToken() : cancel;
    }

    public J2534 getAdapter() { return adapter; }
    public Kernel getKernel() { return kernel; }
    public FlashOptions getOptions() { return options; }
    public BackupStore getBackups() { return backups; }
    public Timing getTiming() { return timing; }
    public FlashListener getListener() { return listener; }
    public CancelToken getCancel() { return cancel; }
}
