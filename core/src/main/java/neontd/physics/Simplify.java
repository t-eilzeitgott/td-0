package neontd.physics;

import neontd.math.Mathx;

/** Vereinfachung von Freihand-Strichen zu wenigen Kontrollpunkten (Ramer-Douglas-Peucker + Mindestabstand). */
public final class Simplify {
    private Simplify() {
    }

    /**
     * Ramer-Douglas-Peucker (iterativ, keine Rekursion).
     *
     * @param pts x0,y0,x1,y1,...
     * @param n   Anzahl der Punkte
     * @param eps maximal erlaubte Abweichung
     * @param out Ausgabe, mindestens so groß wie pts
     * @return Anzahl der Ausgabepunkte
     */
    public static int rdp(double[] pts, int n, double eps, double[] out) {
        if (n <= 2) {
            System.arraycopy(pts, 0, out, 0, n * 2);
            return n;
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        int[] stack = new int[n * 2 + 2];
        int sp = 0;
        stack[sp++] = 0;
        stack[sp++] = n - 1;
        while (sp > 0) {
            int hi = stack[--sp];
            int lo = stack[--sp];
            double maxD = -1;
            int idx = -1;
            for (int i = lo + 1; i < hi; i++) {
                double d = Math.sqrt(Collision.pointSegmentDistSq(pts[i * 2], pts[i * 2 + 1],
                        pts[lo * 2], pts[lo * 2 + 1], pts[hi * 2], pts[hi * 2 + 1]));
                if (d > maxD) {
                    maxD = d;
                    idx = i;
                }
            }
            if (idx >= 0 && maxD > eps) {
                keep[idx] = true;
                stack[sp++] = lo;
                stack[sp++] = idx;
                stack[sp++] = idx;
                stack[sp++] = hi;
            }
        }
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out[c * 2] = pts[i * 2];
                out[c * 2 + 1] = pts[i * 2 + 1];
                c++;
            }
        }
        return c;
    }

    /**
     * Entfernt Punkte, die näher als {@code minGap} am vorherigen behaltenen Punkt liegen (der letzte Punkt bleibt
     * immer erhalten). Arbeitet in place.
     */
    public static int enforceMinGap(double[] pts, int n, double minGap) {
        if (n <= 2) {
            return n;
        }
        int c = 1;
        for (int i = 1; i < n - 1; i++) {
            if (Mathx.dist(pts[(c - 1) * 2], pts[(c - 1) * 2 + 1], pts[i * 2], pts[i * 2 + 1]) >= minGap) {
                pts[c * 2] = pts[i * 2];
                pts[c * 2 + 1] = pts[i * 2 + 1];
                c++;
            }
        }
        // Letzten Punkt anhängen; liegt er zu nah am vorletzten, ersetzt er diesen.
        if (Mathx.dist(pts[(c - 1) * 2], pts[(c - 1) * 2 + 1], pts[(n - 1) * 2], pts[(n - 1) * 2 + 1]) < minGap && c > 1) {
            c--;
        }
        pts[c * 2] = pts[(n - 1) * 2];
        pts[c * 2 + 1] = pts[(n - 1) * 2 + 1];
        return c + 1;
    }
}
