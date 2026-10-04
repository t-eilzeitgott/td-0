package neontd.ui;

import neontd.math.Mathx;

/**
 * Maße des sichtbaren Bereichs in logischen Pixeln, samt Sicherheitsrand (iPhone-Notch, Home-Indikator) und
 * einer Skalierungseinheit {@link #u}, damit Bedienelemente auf Handy, Laptop und 4K ähnlich groß wirken.
 */
public final class Viewport {
    public double w = 1280;
    public double h = 720;
    public double insetL;
    public double insetT;
    public double insetR;
    public double insetB;
    /** UI-Einheit: 1 = Entwurfsgröße (Fensterhöhe ≈ 560 px). Mindestens 0,86 (Handy: 10 % größer als zuvor), damit Tippflächen groß genug bleiben. */
    public double u = 1;

    public void set(double w, double h, double l, double t, double r, double b) {
        this.w = w;
        this.h = h;
        this.insetL = l;
        this.insetT = t;
        this.insetR = r;
        // Der Home-Indikator ist nur eine dünne Leiste ganz unten: nur ein Drittel des Randes freihalten, den Rest nutzen.
        this.insetB = b * 0.3;
        this.u = Mathx.clamp(Math.min(w, h) / 560.0, 0.86, 2.0);
    }

    public double safeX() {
        return insetL;
    }

    public double safeY() {
        return insetT;
    }

    public double safeW() {
        return w - insetL - insetR;
    }

    public double safeH() {
        return h - insetT - insetB;
    }

    public boolean landscape() {
        return w >= h * 1.05;
    }
}
