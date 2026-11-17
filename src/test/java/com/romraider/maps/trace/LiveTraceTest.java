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

package com.romraider.maps.trace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.romraider.Settings;
import com.romraider.swing.JProgressPane;
import com.romraider.xml.DOMSettingsBuilder;
import com.romraider.xml.DOMSettingsUnmarshaller;

/** The live trace: the ECU's position on an axis, the interpolated value, the points, the spline. */
public class LiveTraceTest {
    private static final double E = 1e-9;
    private static final double[] RPM = {400, 600, 800, 1000, 1200, 1600, 2400, 2800};

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ---- where the lookup stands on an axis

    @Test
    public void betweenTwoBreakpointsItIsTheFractionOfTheWay() {
        assertEquals(6.25, AxisPosition.of(RPM, 2500), E);
        assertEquals(5.5, AxisPosition.of(RPM, 2000), E);
        assertEquals(0.5, AxisPosition.of(RPM, 500), E);
    }

    @Test
    public void onABreakpointItIsThatBreakpoint() {
        assertEquals(0, AxisPosition.of(RPM, 400), E);
        assertEquals(4, AxisPosition.of(RPM, 1200), E);
    }

    @Test
    public void beyondTheEndsItIsTheEnd() {
        assertEquals(0, AxisPosition.of(RPM, 0), E);
        assertEquals(7, AxisPosition.of(RPM, 2800), E);
        assertEquals(7, AxisPosition.of(RPM, 9000), E);
    }

    @Test
    public void anEvenlySpacedAxisGivesTheSameAsTheEcusFormula() {
        // min + i x 65536 / step, and (value - min) x step / 65536
        double min = 10, step = 2048;
        double[] axis = new double[9];
        for (int i = 0; i < axis.length; i++) axis[i] = min + i * 65536.0 / step;
        for (double v = 10; v <= 266; v += 7.3) {
            assertEquals(Math.min(8, (v - min) * step / 65536), AxisPosition.of(axis, v), 1e-9);
        }
    }

    @Test
    public void aDescendingAxisIsReadReversed() {
        double[] down = {100, 80, 40};
        assertEquals(0.5, AxisPosition.of(down, 90), E);
        assertEquals(1.25, AxisPosition.of(down, 70), E);
        assertEquals(2, AxisPosition.of(down, 0), E);
    }

    @Test
    public void nothingToStandOn() {
        assertTrue(Double.isNaN(AxisPosition.of(new double[0], 3)));
        assertTrue(Double.isNaN(AxisPosition.of(RPM, Double.NaN)));
        assertTrue(Double.isNaN(AxisPosition.of(new double[] {1, Double.NaN, 3}, 2)));
        assertEquals(0, AxisPosition.of(new double[] {5}, 9), E);
    }

    // ---- the value the lookup reads

    @Test
    public void aRowIsInterpolatedLinearly() {
        double[] row = {10, 20, 40};
        assertEquals(15, AxisPosition.interpolate(row, 0.5), E);
        assertEquals(35, AxisPosition.interpolate(row, 1.75), E);
        assertEquals(40, AxisPosition.interpolate(row, 2), E);
        assertEquals(40, AxisPosition.interpolate(row, 7), E);
    }

    @Test
    public void aGridIsInterpolatedBilinearly() {
        double[][] grid = {{0, 10}, {20, 30}};      // [x][y]
        assertEquals(15, AxisPosition.interpolate(grid, 0.5, 0.5), E);
        assertEquals(5, AxisPosition.interpolate(grid, 0, 0.5), E);
        assertEquals(27.5, AxisPosition.interpolate(grid, 1, 0.75), E);
        assertEquals(30, AxisPosition.interpolate(grid, 3, 3), E);
    }

    // ---- the points

    @Test
    public void theAxesOfOneLoggerReplyMakeOnePoint() {
        LiveTrace t = new LiveTrace();
        t.add(1000, 1, 2, 5);
        t.add(1002, 1.5, 2, 6);        // the other axis of the same reply
        t.add(1033, 2, 3, 7);          // the next reply
        List<LiveTrace.Point> p = t.recent(10);
        assertEquals(2, p.size());
        assertEquals(1.5, p.get(0).x, E);
        assertEquals(1000, p.get(0).time);
        assertEquals(7, t.latest().value, E);
    }

    @Test
    public void theTrailIsTheSecondsBeforeTheNewestPoint() {
        LiveTrace t = new LiveTrace();
        for (int i = 0; i < 100; i++) t.add(i * 100L, i, 0, i);
        assertEquals(21, t.recent(2).size());        // 7.9 s ... 9.9 s
        assertEquals(99, t.recent(2).get(20).x, E);
        assertEquals(1, t.recent(0).size());
        t.clear();
        assertTrue(t.isEmpty());
        assertNull(t.latest());
    }

    @Test
    public void aPointWithoutAPositionIsNotAdded() {
        LiveTrace t = new LiveTrace();
        t.add(0, Double.NaN, 1, 1);
        assertTrue(t.isEmpty());
    }

    // ---- the line

    @Test
    public void theSplinePassesThroughEveryPointInOnePath() {
        List<Point2D> pts = points(0, 0, 10, 5, 30, 5, 31, 40, 80, 42);
        Path2D.Double path = Spline.through(pts);
        List<double[]> ends = segmentEnds(path);
        assertEquals(pts.size(), ends.size());
        for (int i = 0; i < pts.size(); i++) {
            assertEquals(pts.get(i).getX(), ends.get(i)[0], E);
            assertEquals(pts.get(i).getY(), ends.get(i)[1], E);
        }
        assertEquals(1, countMoves(path));
    }

    @Test
    public void theSplineDoesNotOvershootUnevenSpacing() {
        // a slow start and a sudden jump along a line: the curve must stay on the line, between the ends
        Path2D.Double path = Spline.through(points(0, 0, 1, 0, 2, 0, 200, 0));
        for (double[] c : allCoordinates(path)) {
            assertEquals(0, c[1], E);
            assertTrue("x " + c[0], c[0] >= -E && c[0] <= 200 + E);
        }
    }

    @Test
    public void repeatedPointsAreOne() {
        assertNull(Spline.through(points(5, 5, 5, 5, 5.1, 5)));
        assertEquals(2, Spline.distinct(points(0, 0, 0, 0, 3, 0)).size());
    }

    // ---- the setting

    @Test
    public void theTrailLengthIsSavedAndReadBack() throws Exception {
        Settings s = new Settings();
        assertEquals(5, s.getLiveTraceSeconds(), E);
        s.setLiveTraceSeconds(12.5);
        File f = folder.newFile();
        new DOMSettingsBuilder().buildSettings(s, f, new JProgressPane(), "test");
        Settings read = new DOMSettingsUnmarshaller().unmarshallSettings(
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getDocumentElement());
        assertEquals(12.5, read.getLiveTraceSeconds(), E);
        s.setLiveTraceSeconds(-3);
        assertEquals(0, s.getLiveTraceSeconds(), E);
        s.setLiveTraceSeconds(1e6);
        assertEquals(Settings.MAX_LIVE_TRACE_SECONDS, s.getLiveTraceSeconds(), E);
    }

    // ---- helpers

    private static List<Point2D> points(double... xy) {
        List<Point2D> out = new ArrayList<Point2D>();
        for (int i = 0; i < xy.length; i += 2) out.add(new Point2D.Double(xy[i], xy[i + 1]));
        return out;
    }

    /** The point each moveTo and curveTo ends on. */
    private static List<double[]> segmentEnds(Path2D path) {
        List<double[]> out = new ArrayList<double[]>();
        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_MOVETO || type == PathIterator.SEG_LINETO) out.add(new double[] {c[0], c[1]});
            else if (type == PathIterator.SEG_CUBICTO) out.add(new double[] {c[4], c[5]});
        }
        return out;
    }

    private static List<double[]> allCoordinates(Path2D path) {
        List<double[]> out = new ArrayList<double[]>();
        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            int n = type == PathIterator.SEG_CUBICTO ? 3 : type == PathIterator.SEG_QUADTO ? 2 : 1;
            for (int k = 0; k < n; k++) out.add(Arrays.copyOfRange(c, 2 * k, 2 * k + 2));
        }
        return out;
    }

    private static int countMoves(Path2D path) {
        int n = 0;
        double[] c = new double[6];
        for (PathIterator it = path.getPathIterator(null); !it.isDone(); it.next()) {
            if (it.currentSegment(c) == PathIterator.SEG_MOVETO) n++;
        }
        return n;
    }
}
