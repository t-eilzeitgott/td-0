package neontd.ui;

/**
 * Eine gedämpfte Feder: Der Wert schwingt organisch auf sein Ziel zu, mit leichtem Überschwingen (je nach Dämpfung)
 * und echter Geschwindigkeit – so fühlen sich Auftritte, Hüpfer und Verschiebungen „lebendig“ an statt linear.
 *
 * <p>Gerechnet wird halbimplizit in kleinen Teilschritten, damit die Feder auch bei 30 fps, 120 fps oder einem
 * Ruckler stabil bleibt (framerate-unabhängig).
 */
public final class Spring {
    public double value;
    public double velocity;
    public double target;
    /** Steifigkeit (höher = schneller). */
    public double stiffness;
    /** Dämpfung (Verhältnis zur kritischen Dämpfung: 1 = kein Überschwingen, kleiner = federnder). */
    public double damping;

    public Spring(double value, double stiffness, double damping) {
        this.value = value;
        this.target = value;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /** Weich und mit einem Hauch Überschwingen – der Standard für schwebende Bedienteile. */
    public static Spring soft(double value) {
        return new Spring(value, 260, 0.72);
    }

    /** Spritzig mit deutlichem Hüpfer – für kleine Pops (Kacheln, Zahlen). */
    public static Spring bouncy(double value) {
        return new Spring(value, 420, 0.48);
    }

    /** Ruhig, ohne Überschwingen – für große Flächen und Positionen. */
    public static Spring calm(double value) {
        return new Spring(value, 180, 1.0);
    }

    public void update(double dt) {
        if (dt <= 0) {
            return;
        }
        double c = 2 * damping * Math.sqrt(stiffness);
        double rest = dt;
        while (rest > 0) {
            double h = Math.min(rest, 1.0 / 240.0);
            double a = -stiffness * (value - target) - c * velocity;
            velocity += a * h;
            value += velocity * h;
            rest -= h;
        }
        if (Math.abs(value - target) < 0.0004 && Math.abs(velocity) < 0.004) {
            value = target;
            velocity = 0;
        }
    }

    public void snap(double v) {
        value = v;
        target = v;
        velocity = 0;
    }

    /** Ein Anstoß ohne Zielwechsel (z. B. kurzer Hüpfer beim Kauf). */
    public void kick(double v) {
        velocity += v;
    }

    public boolean settled() {
        return value == target && velocity == 0;
    }
}
