package neontd.physics;

import neontd.math.Mathx;
import neontd.math.Vec2;

/**
 * Ein Pfad als dichte Polylinie mit Bogenlängen-Parametrisierung.
 *
 * <p>Gegner speichern nur "wie weit sie auf dem Pfad sind" (Bogenlänge {@code s}). Dadurch bewegen sie sich
 * unabhängig von der Kurvenform mit exakt ihrer Geschwindigkeit, und zukünftige Positionen lassen sich
 * exakt vorhersagen ({@code positionAt(s + v * t)}) – die Grundlage für Zielvorhersage der Türme.
 *
 * <p>Erzeugt wird der Pfad aus Kontrollpunkten über einen zentripetalen Catmull-Rom-Spline: Die Kurve geht
 * durch alle Punkte, bildet keine Schleifen oder Spitzen und fühlt sich im Editor intuitiv an.
 */
public final class Path {
    private final double[] xs;
    private final double[] ys;
    private final double[] cum;
    private final int n;
    private final double length;

    private Path(double[] xs, double[] ys, int n) {
        this.xs = xs;
        this.ys = ys;
        this.n = n;
        this.cum = new double[n];
        double acc = 0;
        for (int i = 1; i < n; i++) {
            acc += Mathx.dist(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            cum[i] = acc;
        }
        this.length = acc;
    }

    /** Minimaler Abstand zweier Kontrollpunkte; näher beieinander liegende Punkte werden zusammengefasst. */
    public static final double MIN_POINT_GAP = 1.0;

    /** Anzahl unterscheidbarer Kontrollpunkte (Duplikate in Folge zählen nicht). */
    public static int distinctCount(double[] pts, int count) {
        int c = 0;
        double lx = 0;
        double ly = 0;
        for (int i = 0; i < count; i++) {
            double x = pts[i * 2];
            double y = pts[i * 2 + 1];
            if (c == 0 || Mathx.dist(lx, ly, x, y) >= MIN_POINT_GAP) {
                c++;
                lx = x;
                ly = y;
            }
        }
        return c;
    }

    /**
     * Baut einen glatten Pfad durch die Kontrollpunkte.
     *
     * @param pts        x0,y0,x1,y1,...
     * @param count      Anzahl der Punkte (nicht der Zahlen)
     * @param sampleStep ungefährer Abstand der Stützpunkte der Polylinie in Welteinheiten
     */
    public static Path fromControlPoints(double[] pts, int count, double sampleStep) {
        double[] px = new double[count];
        double[] py = new double[count];
        int m = 0;
        for (int i = 0; i < count; i++) {
            double x = pts[i * 2];
            double y = pts[i * 2 + 1];
            if (m == 0 || Mathx.dist(px[m - 1], py[m - 1], x, y) >= MIN_POINT_GAP) {
                px[m] = x;
                py[m] = y;
                m++;
            }
        }
        if (m < 2) {
            throw new IllegalArgumentException("Ein Pfad braucht mindestens zwei verschiedene Punkte");
        }
        if (m == 2) {
            return new Path(new double[] {px[0], px[1]}, new double[] {py[0], py[1]}, 2);
        }

        DoubleBuf bx = new DoubleBuf(m * 16);
        DoubleBuf by = new DoubleBuf(m * 16);
        for (int i = 0; i < m - 1; i++) {
            double p0x = i > 0 ? px[i - 1] : 2 * px[0] - px[1];
            double p0y = i > 0 ? py[i - 1] : 2 * py[0] - py[1];
            double p3x = i + 2 < m ? px[i + 2] : 2 * px[m - 1] - px[m - 2];
            double p3y = i + 2 < m ? py[i + 2] : 2 * py[m - 1] - py[m - 2];
            sampleSegment(p0x, p0y, px[i], py[i], px[i + 1], py[i + 1], p3x, p3y, sampleStep, bx, by);
        }
        bx.add(px[m - 1]);
        by.add(py[m - 1]);
        return new Path(bx.toArray(), by.toArray(), bx.size());
    }

    /** Baut einen Pfad direkt aus einer Polylinie (ohne Glättung). */
    public static Path fromPolyline(double[] pts, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("Ein Pfad braucht mindestens zwei Punkte");
        }
        double[] x = new double[count];
        double[] y = new double[count];
        for (int i = 0; i < count; i++) {
            x[i] = pts[i * 2];
            y[i] = pts[i * 2 + 1];
        }
        return new Path(x, y, count);
    }

    /** Zentripetaler Catmull-Rom-Abschnitt zwischen p1 und p2 (Barry-Goldman-Auswertung). */
    private static void sampleSegment(double p0x, double p0y, double p1x, double p1y, double p2x, double p2y,
                                      double p3x, double p3y, double step, DoubleBuf bx, DoubleBuf by) {
        double t0 = 0;
        double t1 = t0 + Math.sqrt(Mathx.dist(p0x, p0y, p1x, p1y));
        double t2 = t1 + Math.sqrt(Mathx.dist(p1x, p1y, p2x, p2y));
        double t3 = t2 + Math.sqrt(Mathx.dist(p2x, p2y, p3x, p3y));
        double chord = Mathx.dist(p1x, p1y, p2x, p2y);
        int k = Math.max(2, (int) Math.ceil(chord / step));
        for (int s = 0; s < k; s++) {
            double t = t1 + (t2 - t1) * s / k;
            double a1x = lerpT(p0x, p1x, t0, t1, t);
            double a1y = lerpT(p0y, p1y, t0, t1, t);
            double a2x = lerpT(p1x, p2x, t1, t2, t);
            double a2y = lerpT(p1y, p2y, t1, t2, t);
            double a3x = lerpT(p2x, p3x, t2, t3, t);
            double a3y = lerpT(p2y, p3y, t2, t3, t);
            double b1x = lerpT(a1x, a2x, t0, t2, t);
            double b1y = lerpT(a1y, a2y, t0, t2, t);
            double b2x = lerpT(a2x, a3x, t1, t3, t);
            double b2y = lerpT(a2y, a3y, t1, t3, t);
            bx.add(lerpT(b1x, b2x, t1, t2, t));
            by.add(lerpT(b1y, b2y, t1, t2, t));
        }
    }

    private static double lerpT(double a, double b, double ta, double tb, double t) {
        double d = tb - ta;
        if (d < Mathx.EPS) {
            return a;
        }
        return a * (tb - t) / d + b * (t - ta) / d;
    }

    // ------------------------------------------------------------------------------------------------ Abfragen

    public double length() {
        return length;
    }

    public int pointCount() {
        return n;
    }

    public double x(int i) {
        return xs[i];
    }

    public double y(int i) {
        return ys[i];
    }

    /** Index des Segments, das die Bogenlänge s enthält (cum[i] <= s < cum[i+1]). */
    private int segmentIndex(double s) {
        int lo = 0;
        int hi = n - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cum[mid] <= s) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Position bei Bogenlänge s (außerhalb von [0, length] wird an den Enden begrenzt). */
    public void positionAt(double s, Vec2 out) {
        if (s <= 0) {
            out.set(xs[0], ys[0]);
            return;
        }
        if (s >= length) {
            out.set(xs[n - 1], ys[n - 1]);
            return;
        }
        int i = segmentIndex(s);
        double seg = cum[i + 1] - cum[i];
        double t = seg > 0 ? (s - cum[i]) / seg : 0;
        out.x = xs[i] + (xs[i + 1] - xs[i]) * t;
        out.y = ys[i] + (ys[i + 1] - ys[i]) * t;
    }

    /** Laufrichtung (Winkel in Radiant) bei Bogenlänge s. */
    public double headingAt(double s) {
        int i = s <= 0 ? 0 : (s >= length ? n - 2 : segmentIndex(s));
        return Math.atan2(ys[i + 1] - ys[i], xs[i + 1] - xs[i]);
    }

    /**
     * Nächster Punkt auf dem Pfad zu (x, y).
     *
     * @param closest optionaler Out-Parameter für den Punkt auf dem Pfad
     * @return Bogenlänge des nächsten Punktes
     */
    public double project(double x, double y, Vec2 closest) {
        double best = Double.MAX_VALUE;
        double bestS = 0;
        double bx = xs[0];
        double by = ys[0];
        for (int i = 0; i < n - 1; i++) {
            double ax = xs[i];
            double ay = ys[i];
            double dx = xs[i + 1] - ax;
            double dy = ys[i + 1] - ay;
            double l2 = dx * dx + dy * dy;
            double t = l2 > 0 ? Mathx.clamp(((x - ax) * dx + (y - ay) * dy) / l2, 0, 1) : 0;
            double cx = ax + dx * t;
            double cy = ay + dy * t;
            double d2 = Mathx.distSq(x, y, cx, cy);
            if (d2 < best) {
                best = d2;
                bestS = cum[i] + t * Math.sqrt(l2);
                bx = cx;
                by = cy;
            }
        }
        if (closest != null) {
            closest.set(bx, by);
        }
        return bestS;
    }

    /** Kürzester Abstand von (x, y) zum Pfad. */
    public double distanceTo(double x, double y) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n - 1; i++) {
            double ax = xs[i];
            double ay = ys[i];
            double dx = xs[i + 1] - ax;
            double dy = ys[i + 1] - ay;
            double l2 = dx * dx + dy * dy;
            double t = l2 > 0 ? Mathx.clamp(((x - ax) * dx + (y - ay) * dy) / l2, 0, 1) : 0;
            double d2 = Mathx.distSq(x, y, ax + dx * t, ay + dy * t);
            if (d2 < best) {
                best = d2;
            }
        }
        return Math.sqrt(best);
    }

    /** Minimales wachsendes double-Array (vermeidet Boxing von {@code List<Double>}). */
    private static final class DoubleBuf {
        private double[] a;
        private int size;

        DoubleBuf(int cap) {
            a = new double[Math.max(16, cap)];
        }

        void add(double v) {
            if (size == a.length) {
                double[] b = new double[a.length * 2];
                System.arraycopy(a, 0, b, 0, size);
                a = b;
            }
            a[size++] = v;
        }

        int size() {
            return size;
        }

        double[] toArray() {
            double[] r = new double[size];
            System.arraycopy(a, 0, r, 0, size);
            return r;
        }
    }
}
