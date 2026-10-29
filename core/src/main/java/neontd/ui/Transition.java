package neontd.ui;

import neontd.gfx.Gfx;
import neontd.math.Mathx;

/**
 * Szenenwechsel als echte Überblendung: Die alte Szene bleibt voll stehen, die neue (sie zeichnet immer zuerst
 * ihren schwarzen Hintergrund) legt sich mit wachsender Deckkraft darüber. Was in beiden Szenen gleich aussieht –
 * die Wortmarke, Kopfzeilen, Zurück-Knopf –, bleibt dadurch völlig ruhig: keine Bewegung, kein Flackern, kein
 * kurzes Abdunkeln. Der Wechsel selbst passiert sofort (die neue Szene ist ab dem ersten Bild aktiv).
 */
public final class Transition {
    private static final double DURATION = 0.3;

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

    /** Deckkraft der alten Szene: bleibt stehen, bis die neue sie ganz verdeckt. */
    public double oldAlpha() {
        return t < 0 ? 0 : 1;
    }

    /** Deckkraft der neuen Szene (weich ein- und auslaufend). */
    public double newAlpha() {
        return t < 0 ? 1 : Easing.inOutCubic(Mathx.clamp01(t));
    }

    /** Kein Versatz mehr: Bewegung beim Seitenwechsel ließe gemeinsame Kopfzeilen springen. */
    public double lift(double u) {
        return 0;
    }

    public void render(Gfx g, double w, double h) {
        // nichts mehr zu zeichnen – die App mischt die Szenen selbst
    }
}
