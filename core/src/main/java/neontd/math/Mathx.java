package neontd.math;

/** Kleine Sammlung mathematischer Helfer (alles in double, damit JVM und JS identisch rechnen). */
public final class Mathx {
    public static final double PI = Math.PI;
    public static final double TAU = Math.PI * 2.0;
    public static final double EPS = 1e-9;

    private Mathx() {
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Position von v zwischen a und b als 0..1 (nicht begrenzt). */
    public static double invLerp(double a, double b, double v) {
        return Math.abs(b - a) < EPS ? 0 : (v - a) / (b - a);
    }

    public static double smoothstep(double a, double b, double v) {
        double t = clamp01(invLerp(a, b, v));
        return t * t * (3 - 2 * t);
    }

    public static double sqr(double v) {
        return v * v;
    }

    public static double dist(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static double distSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /** Winkel in den Bereich [-PI, PI) bringen. */
    public static double wrapAngle(double a) {
        a = a % TAU;
        if (a >= PI) {
            a -= TAU;
        } else if (a < -PI) {
            a += TAU;
        }
        return a;
    }

    /** Kürzeste vorzeichenbehaftete Winkeldifferenz von {@code from} nach {@code to}. */
    public static double angleDiff(double from, double to) {
        return wrapAngle(to - from);
    }

    /** Bewegt cur um höchstens maxDelta auf target zu. */
    public static double approach(double cur, double target, double maxDelta) {
        if (cur < target) {
            return Math.min(cur + maxDelta, target);
        }
        return Math.max(cur - maxDelta, target);
    }

    /** Framerate-unabhängiges, exponentielles Annähern ("Smoothing"). rate in 1/s. */
    public static double expDecay(double cur, double target, double rate, double dt) {
        return target + (cur - target) * Math.exp(-rate * dt);
    }

    public static int roundToInt(double v) {
        return (int) Math.floor(v + 0.5);
    }
}
