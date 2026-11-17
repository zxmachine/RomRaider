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

package com.romraider.maps.checksum;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

public class ChecksumWORDSUM16Test {

    private static ChecksumWORDSUM16 module(String start, String end, String correction, String target) {
        Map<String, String> attrs = new HashMap<String, String>();
        attrs.put("start", start);
        attrs.put("end", end);
        attrs.put("correction", correction);
        attrs.put("target", target);
        ChecksumWORDSUM16 cs = new ChecksumWORDSUM16();
        cs.configure(attrs);
        return cs;
    }

    @Test
    public void updateMakesTheSumTheTarget() {
        byte[] data = new byte[0x1000];
        new Random(1).nextBytes(data);
        ChecksumWORDSUM16 cs = module("0", "0xFF4", "FF2", "5AA5");
        assertEquals(1, cs.update(data));
        assertEquals(1, cs.validate(data));
        assertEquals(0x5AA5, ChecksumWORDSUM16.sum(data, 0, 0xFF4));
        assertEquals(0, cs.update(data));
        data[0x10] ^= 1;
        assertEquals(0, cs.validate(data));
        data[0xFF8] ^= 1;     // outside the summed range
        cs.update(data);
        assertEquals(1, cs.validate(data));
    }

    @Test
    public void aDefinitionThatDoesNotFitTheFileChangesNothing() {
        byte[] data = new byte[0x100];
        ChecksumWORDSUM16 cs = module("0", "1000", "FF2", "5AA5");
        assertEquals(0, cs.validate(data));
        assertEquals(0, cs.update(data));
        assertEquals(0, ChecksumWORDSUM16.sum(data, 0, data.length));
    }
}
