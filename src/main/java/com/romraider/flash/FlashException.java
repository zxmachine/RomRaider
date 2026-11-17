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

/**
 * A flash step that stopped: what happened, what the user should do next,
 * and whether the ECU's flash may have changed.
 */
public class FlashException extends Exception {
    private static final long serialVersionUID = 6113424611086315547L;

    /** How far a failed step got with the ECU's flash. */
    public enum FlashState {
        /** Nothing was erased or programmed. */
        UNCHANGED,
        /** Some blocks may be erased or partly programmed. */
        PARTLY_WRITTEN
    }

    private final String nextStep;
    private final FlashState flashState;

    public FlashException(String what, String nextStep, FlashState flashState) {
        super(what);
        this.nextStep = nextStep == null ? "" : nextStep;
        this.flashState = flashState;
    }

    public FlashException(String what, String nextStep) {
        this(what, nextStep, FlashState.UNCHANGED);
    }

    /** What the user should do next, in plain words. */
    public String getNextStep() {
        return nextStep;
    }

    public FlashState getFlashState() {
        return flashState;
    }

    /** The message and the next step together, as the log shows them. */
    public String describe() {
        StringBuilder sb = new StringBuilder(getMessage());
        if (flashState == FlashState.UNCHANGED) {
            sb.append(" Nothing was written to the ECU.");
        }
        if (nextStep.length() > 0) sb.append("\nWhat to do now: ").append(nextStep);
        return sb.toString();
    }
}
