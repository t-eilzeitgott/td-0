package neontd.ui;

import neontd.gfx.Gfx;
import neontd.math.Mathx;

/**
 * Szenenwechsel als weiche Überblendung: Die alte Szene blendet aus, die neue blendet ein und hebt sich dabei ein
 * paar Pixel an – ruhig und schnell, wie ein Seitenwechsel in einer guten App. Der Wechsel selbst passiert sofort
 * (die neue Szene ist ab dem ersten Bild aktiv), nur die Darstellung mischt noch kurz.
 */
public final class Transition {
    private static final double DURATION = 0.28;

    private double t = -1;

    public boolean active() {
        return t >= 0;
    }

    public void start(Runnable swapAction) {
        if (swapAction != null) {
            swapAction.run();
        }
        t = 0;
    }

    public void update(double dt) {
        if (t < 0) {
            return;
        }
        t += dt / DURATION;
        if (t >= 1) {
            t = -1;
        }
    }

    /** Fortschritt 0..1 der Einblendung (1 = fertig). */
    public double progress() {
        return t < 0 ? 1 : Mathx.clamp01(t);
    }

    /** Deckkraft der alten Szene. */
    public double oldAlpha() {
        return t < 0 ? 0 : 1 - Easing.outCubic(Mathx.clamp01(t * 1.6));
    }

    /** Deckkraft der neuen Szene. */
    public double newAlpha() {
        return t < 0 ? 1 : Easing.outCubic(Mathx.clamp01(t));
    }

    /** Versatz (Pixel nach oben), mit dem die neue Szene hereinkommt. */
    public double lift(double u) {
        return t < 0 ? 0 : (1 - Easing.outCubic(Mathx.clamp01(t))) * 14 * u;
    }

    public void render(Gfx g, double w, double h) {
        // nichts mehr zu zeichnen – die App mischt die Szenen selbst
    }
}
