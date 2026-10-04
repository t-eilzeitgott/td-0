package neontd.ui;

import neontd.math.Mathx;

/** Ein Wert, der sich framerate-unabhängig weich auf ein Ziel zubewegt (für Hover, Druck, Einblenden …). */
public final class Smooth {
    public double value;
    public double target;
    public double rate;

    public Smooth(double value, double rate) {
        this.value = value;
        this.target = value;
        this.rate = rate;
    }

    public void update(double dt) {
        value = Mathx.expDecay(value, target, rate, dt);
        if (Math.abs(value - target) < 0.0005) {
            value = target;
        }
    }

    public void snap(double v) {
        value = v;
        target = v;
    }
}
