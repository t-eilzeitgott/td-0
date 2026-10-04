package neontd.gfx;

import neontd.math.Mathx;

/** Farbhelfer. Farben sind 0xAARRGGBB; Alpha 0 bedeutet "voll deckend" (siehe {@link Gfx}). */
public final class Colors {
    private Colors() {
    }

    public static int alphaOf(int c) {
        int a = c >>> 24;
        return a == 0 ? 255 : a;
    }

    /** Alpha als Anteil 0..1. */
    public static double alpha01(int c) {
        return alphaOf(c) / 255.0;
    }

    public static int rgbOf(int c) {
        return c & 0xFFFFFF;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    /** Farbe mit Deckkraft a (0..1) – die Farbkomponenten bleiben erhalten. */
    public static int withAlpha(int c, double a) {
        int ia = Mathx.clamp((int) Math.round(a * 255), 1, 255);
        return (ia << 24) | (c & 0xFFFFFF);
    }

    /** Wie {@link #withAlpha}, multipliziert aber mit einer schon vorhandenen Deckkraft. */
    public static int mulAlpha(int c, double a) {
        return withAlpha(c, alpha01(c) * a);
    }

    public static int rgb(int r, int g, int b) {
        return (Mathx.clamp(r, 0, 255) << 16) | (Mathx.clamp(g, 0, 255) << 8) | Mathx.clamp(b, 0, 255);
    }

    /** Linear zwischen zwei Farben mischen (nur RGB; das Ergebnis ist voll deckend). */
    public static int lerp(int a, int b, double t) {
        t = Mathx.clamp01(t);
        int r = (int) Math.round(r(a) + (r(b) - r(a)) * t);
        int g = (int) Math.round(g(a) + (g(b) - g(a)) * t);
        int bl = (int) Math.round(b(a) + (b(b) - b(a)) * t);
        return rgb(r, g, bl);
    }

    public static int lighten(int c, double t) {
        return lerp(c, 0xFFFFFF, t);
    }

    public static int darken(int c, double t) {
        return lerp(c, 0x000000, t);
    }

    /** HSV (h in 0..1, s, v in 0..1) nach RGB. */
    public static int hsv(double h, double s, double v) {
        h = h - Math.floor(h);
        double hh = h * 6;
        int i = (int) Math.floor(hh);
        double f = hh - i;
        double p = v * (1 - s);
        double q = v * (1 - s * f);
        double t = v * (1 - s * (1 - f));
        double r;
        double g;
        double b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return rgb((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }
}
