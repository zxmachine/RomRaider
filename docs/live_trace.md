# The live trace on a table

With the logger running and "Overlay Log" ticked on a table's toolbar, a 2D or 3D table whose axes are linked to
logger parameters (`logparam` on the axes in the definition) shows where the ECU's lookup stands on it now and where
it has been:

* **The point** is placed where the ECU's lookup routine puts the logged values, not on the nearest cell: on each
  axis, the breakpoint at or below the value plus the fraction of the way to the next one, and the end breakpoint
  beyond either end (the lookup clamps there). Between the centres of the cells it lies in proportion. Its label is
  the value the lookup reads there, interpolated as the ECU does it: linear on a 2D table, bilinear on a 3D table.
* **The trail** is a smooth line through the points of the last seconds (the toolbar's "Trail" box, saved in the
  settings as `<livetrace seconds=.../>`; 0 shows the point only). It is a centripetal Catmull-Rom spline: one
  continuous curve through every point, without the loops a plain spline makes where the points come unevenly. The
  axes of one logger reply make one point. When the logger stops, the trail stays as it was.
* The cells the logger has visited keep their marks as before. "Clear Overlay" clears both.

The code is in `com.romraider.maps.trace` (`AxisPosition`, `LiveTrace`, `Spline`, `TraceOverlay`), with small hooks in
the table views. Display units give the same position and value as the ECU's raw numbers as long as the scalings are
linear, as table scalings are.
