package neontd.ui;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Theme;
import neontd.math.Mathx;

/**
 * Szenenwechsel als "Jalousie": Neon-Streifen fahren von der Mitte aus zu und nach dem Wechsel wieder auf.
 * Der eigentliche Wechsel passiert, wenn der Bildschirm komplett bedeckt ist.
 */
public final class Transition {
    private static final int BARS = 10;
    private static final double DURATION = 0.62;

    private double t = -1;
    private boolean swapped;
    private Runnable swap;

    public boolean active() {
        return t >= 0;
    }

    public void start(Runnable swapAction) {
        if (active()) {
            return;
        }
        t = 0;
        swapped = false;
        swap = swapAction;
    }

    public void update(double dt) {
        if (t < 0) {
            return;
        }
        t += dt / DURATION;
        if (!swapped && t >= 0.5) {
            swapped = true;
            if (swap != null) {
                swap.run();
            }
        }
        if (t >= 1) {
            t = -1;
        }
    }

    public void render(Gfx g, double w, double h) {
        if (t < 0) {
            return;
        }
        boolean closing = t < 0.5;
        double phase = closing ? t * 2 : (t - 0.5) * 2;
        double bw = w / BARS;
        double cy = h / 2;
        for (int i = 0; i < BARS; i++) {
            int order = closing ? i : BARS - 1 - i;
            double local = Mathx.clamp01((phase - order * 0.05) / 0.55);
            double cover = closing ? Easing.outCubic(local) : 1 - Easing.inQuad(local);
            if (cover <= 0.001) {
                continue;
            }
            double hh = h * cover;
            double x = i * bw;
            g.fillRect(x - 0.5, cy - hh / 2, bw + 1, hh, Theme.BLACK);
            if (cover < 0.999) {
                g.line(x, cy - hh / 2, x + bw, cy - hh / 2, 2, Colors.withAlpha(Theme.CYAN, 0.9));
                g.line(x, cy + hh / 2, x + bw, cy + hh / 2, 2, Colors.withAlpha(Theme.MAGENTA, 0.9));
            }
        }
    }
}
