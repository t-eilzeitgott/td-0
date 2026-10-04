package neontd.ui;

import neontd.math.Mathx;

/** Easing-Kurven; Eingabe t in 0..1, Ausgabe meist ebenfalls 0..1 (Back/Elastic dürfen überschwingen). */
public final class Easing {
    private Easing() {
    }

    public static double inQuad(double t) {
        return t * t;
    }

    public static double outQuad(double t) {
        return 1 - (1 - t) * (1 - t);
    }

    public static double outCubic(double t) {
        double u = 1 - t;
        return 1 - u * u * u;
    }

    public static double inOutCubic(double t) {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    public static double inOutSine(double t) {
        return -(Math.cos(Math.PI * t) - 1) / 2;
    }

    /** Schwingt kurz über das Ziel hinaus und federt zurück – ideal für "Pop"-Animationen. */
    public static double outBack(double t) {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        double u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    public static double outElastic(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (Mathx.TAU / 3)) + 1;
    }
}
