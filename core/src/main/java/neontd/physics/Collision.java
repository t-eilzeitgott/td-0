package neontd.physics;

import neontd.math.Mathx;

/** Kollisions-Primitive. Alles ohne Allokation, alles in Welteinheiten. */
public final class Collision {
    private Collision() {
    }

    public static boolean circlesOverlap(double ax, double ay, double ar, double bx, double by, double br) {
        double r = ar + br;
        return Mathx.distSq(ax, ay, bx, by) <= r * r;
    }

    /**
     * Kontinuierliche Kollision zweier bewegter Kreise (verhindert "Tunneling" schneller Projektile).
     *
     * <p>Beide Kreise bewegen sich im betrachteten Zeitschritt linear: A von (ax, ay) um (adx, ady),
     * B von (bx, by) um (bdx, bdy). Gerechnet wird in Relativbewegung.
     *
     * @return Zeitpunkt des ersten Kontakts als Anteil 0..1 des Schritts, oder -1 ohne Kontakt
     */
    public static double sweptCircles(double ax, double ay, double adx, double ady, double ar,
                                      double bx, double by, double bdx, double bdy, double br) {
        double dx = ax - bx;
        double dy = ay - by;
        double vx = adx - bdx;
        double vy = ady - bdy;
        double rr = ar + br;
        double c = dx * dx + dy * dy - rr * rr;
        if (c <= 0) {
            return 0; // überlappen schon zu Beginn
        }
        double a = vx * vx + vy * vy;
        if (a < Mathx.EPS) {
            return -1;
        }
        double b = 2 * (dx * vx + dy * vy);
        double disc = b * b - 4 * a * c;
        if (disc < 0) {
            return -1;
        }
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return (t >= 0 && t <= 1) ? t : -1;
    }

    /** Quadrat des Abstands von Punkt (px, py) zur Strecke (x1,y1)-(x2,y2). */
    public static double pointSegmentDistSq(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double l2 = dx * dx + dy * dy;
        double t = l2 > 0 ? Mathx.clamp(((px - x1) * dx + (py - y1) * dy) / l2, 0, 1) : 0;
        return Mathx.distSq(px, py, x1 + dx * t, y1 + dy * t);
    }

    /**
     * Erster Schnittpunkt einer Strecke mit einem Kreis.
     *
     * @return Parameter t (0..1) entlang der Strecke oder -1 ohne Schnitt
     */
    public static double segmentCircle(double x1, double y1, double x2, double y2, double cx, double cy, double r) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double fx = x1 - cx;
        double fy = y1 - cy;
        double a = dx * dx + dy * dy;
        double c = fx * fx + fy * fy - r * r;
        if (c <= 0) {
            return 0;
        }
        if (a < Mathx.EPS) {
            return -1;
        }
        double b = 2 * (fx * dx + fy * dy);
        double disc = b * b - 4 * a * c;
        if (disc < 0) {
            return -1;
        }
        double t = (-b - Math.sqrt(disc)) / (2 * a);
        return (t >= 0 && t <= 1) ? t : -1;
    }
}
