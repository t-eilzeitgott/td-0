package neontd.fx;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.math.Mathx;
import neontd.math.Rng;

/**
 * Partikel mit einfacher Physik (Geschwindigkeit, Schwerkraft, Luftwiderstand) als Struct-of-Arrays: keine
 * Allokation pro Partikel, damit auch Dutzende gleichzeitige Feuerwerke auf dem iPhone flüssig bleiben.
 */
public final class Particles {
    /** Funke mit Schweif. */
    public static final int SPARK = 0;
    /** Leuchtpunkt. */
    public static final int DOT = 1;
    /** Funke, der am Lebensende in viele kleine Funken zerplatzt (Knistern). */
    public static final int POP = 2;
    /** Wachsender Ring (size = Endradius). */
    public static final int RING = 3;
    /** Lichtblitz (size = Radius). */
    public static final int FLASH = 4;
    /** Aufsteigender Text. */
    public static final int TEXT = 5;

    private static final double GRAVITY = 150;
    private static final double DRAG = 2.4;

    private final int cap;
    private int n;
    private final double[] x;
    private final double[] y;
    private final double[] vx;
    private final double[] vy;
    private final double[] life;
    private final double[] maxLife;
    private final double[] size;
    private final int[] color;
    private final byte[] kind;
    private final String[] label;
    /** Nur für Optik; bewusst getrennt vom deterministischen Simulations-RNG. */
    private final Rng rng = new Rng(0x5EED);

    public Particles(int capacity) {
        this.cap = capacity;
        x = new double[cap];
        y = new double[cap];
        vx = new double[cap];
        vy = new double[cap];
        life = new double[cap];
        maxLife = new double[cap];
        size = new double[cap];
        color = new int[cap];
        kind = new byte[cap];
        label = new String[cap];
    }

    public int count() {
        return n;
    }

    public Rng rng() {
        return rng;
    }

    public void clear() {
        n = 0;
    }

    private int alloc() {
        if (n >= cap) {
            return -1;
        }
        return n++;
    }

    public void add(int k, double px, double py, double pvx, double pvy, double lifeSec, double sz, int col) {
        int i = alloc();
        if (i < 0) {
            return;
        }
        kind[i] = (byte) k;
        x[i] = px;
        y[i] = py;
        vx[i] = pvx;
        vy[i] = pvy;
        life[i] = lifeSec;
        maxLife[i] = lifeSec;
        size[i] = sz;
        color[i] = col;
        label[i] = null;
    }

    public void addText(double px, double py, String text, int col, double sz) {
        int i = alloc();
        if (i < 0) {
            return;
        }
        kind[i] = TEXT;
        x[i] = px;
        y[i] = py;
        vx[i] = 0;
        vy[i] = -46;
        life[i] = 0.9;
        maxLife[i] = 0.9;
        size[i] = sz;
        color[i] = col;
        label[i] = text;
    }

    public void update(double dt) {
        double drag = Math.exp(-DRAG * dt);
        for (int i = 0; i < n; i++) {
            life[i] -= dt;
            if (life[i] <= 0) {
                if (kind[i] == POP) {
                    crackle(x[i], y[i], color[i], size[i]);
                }
                // letzten Eintrag nachziehen und diesen Index erneut prüfen
                int last = --n;
                if (i != last) {
                    copy(last, i);
                }
                i--;
                continue;
            }
            int k = kind[i];
            if (k == SPARK || k == POP || k == DOT) {
                vy[i] += GRAVITY * dt;
                vx[i] *= drag;
                vy[i] *= drag;
                x[i] += vx[i] * dt;
                y[i] += vy[i] * dt;
            } else if (k == TEXT) {
                vy[i] *= Math.exp(-2.0 * dt);
                y[i] += vy[i] * dt;
            }
        }
    }

    private void copy(int from, int to) {
        x[to] = x[from];
        y[to] = y[from];
        vx[to] = vx[from];
        vy[to] = vy[from];
        life[to] = life[from];
        maxLife[to] = maxLife[from];
        size[to] = size[from];
        color[to] = color[from];
        kind[to] = kind[from];
        label[to] = label[from];
    }

    private void crackle(double px, double py, int col, double sz) {
        int count = 6;
        double a0 = rng.angle();
        for (int i = 0; i < count; i++) {
            double a = a0 + Mathx.TAU * i / count + rng.range(-0.2, 0.2);
            double sp = rng.range(35, 75);
            add(SPARK, px, py, Math.cos(a) * sp, Math.sin(a) * sp, rng.range(0.22, 0.38), sz * 0.7,
                    Colors.lighten(col, 0.35));
        }
        add(FLASH, px, py, 0, 0, 0.14, 12, Colors.lighten(col, 0.5));
    }

    public void render(Gfx g) {
        if (n == 0) {
            return;
        }
        g.save();
        g.additive(true);
        for (int i = 0; i < n; i++) {
            int k = kind[i];
            if (k == TEXT) {
                continue;
            }
            double u = Mathx.clamp01(life[i] / maxLife[i]); // 1 -> 0
            int c = color[i];
            switch (k) {
                case SPARK:
                case POP: {
                    // "Komet": weicher Schein, farbiger Schweif und ein heller Kopf.
                    double a = Math.min(1, u * 1.8);
                    double twinkle = u < 0.35 ? 0.65 + 0.35 * Math.sin(life[i] * 70 + i) : 1;
                    a *= twinkle;
                    double w = Math.max(1.0, size[i] * (0.45 + 0.55 * u));
                    double tx = x[i] - vx[i] * 0.085;
                    double ty = y[i] - vy[i] * 0.085;
                    if (Neon.quality >= 1) {
                        g.line(tx, ty, x[i], y[i], w * 3.0, Colors.withAlpha(c, a * 0.14));
                    }
                    g.line(tx, ty, x[i], y[i], w, Colors.withAlpha(c, a * 0.8));
                    g.fillCircle(x[i], y[i], w * (k == POP ? 1.1 : 0.8), Colors.withAlpha(Colors.lighten(c, 0.65), a));
                    break;
                }
                case DOT:
                    g.fillCircle(x[i], y[i], size[i] * (0.3 + 0.7 * u), Colors.withAlpha(c, Math.min(1, u * 1.6)));
                    break;
                case RING: {
                    double t = 1 - u;
                    double e = 1 - (1 - t) * (1 - t) * (1 - t);
                    double rr = Math.max(0.5, size[i] * e);
                    if (Neon.quality >= 1) {
                        g.strokeCircle(x[i], y[i], rr, Math.max(1.0, 9 * u), Colors.withAlpha(c, u * 0.18));
                    }
                    g.strokeCircle(x[i], y[i], rr, Math.max(0.8, 4.2 * u), Colors.withAlpha(c, u * 0.95));
                    break;
                }
                case FLASH:
                    if (Neon.quality >= 1) {
                        g.radialGlow(x[i], y[i], size[i] * (0.7 + 0.9 * (1 - u)), Colors.withAlpha(c, Math.min(1, u * 1.1)));
                    }
                    break;
                default:
                    break;
            }
        }
        g.restore();
        for (int i = 0; i < n; i++) {
            if (kind[i] == TEXT) {
                double u = Mathx.clamp01(life[i] / maxLife[i]);
                double pop = u > 0.85 ? 1 + (u - 0.85) * 2.5 : 1;
                g.text(label[i], x[i], y[i], size[i] * pop, Colors.withAlpha(color[i], Math.min(1, u * 2.2)),
                        Gfx.ALIGN_CENTER, true);
            }
        }
    }
}
