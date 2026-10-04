package neontd.fx;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.math.Mathx;
import neontd.math.Rng;

/**
 * Alle rein optischen Effekte der Spielwelt: Feuerwerk, Explosionen, Strahlen, Blitze, Ringe, Text-Popups
 * und Bildschirmwackeln. Die Simulation weiß nichts davon – sie meldet nur Ereignisse (siehe GameFx).
 */
public final class Effects {
    private static final int MAX_BEAMS = 24;
    private static final int MAX_BOLTS = 16;
    private static final int MAX_BOLT_POINTS = 12;
    private static final int MAX_DELAYED = 48;

    public final Particles particles = new Particles(2600);
    private final Rng rng = particles.rng();

    // Strahlen (Sniper)
    private final double[] bx1 = new double[MAX_BEAMS];
    private final double[] by1 = new double[MAX_BEAMS];
    private final double[] bx2 = new double[MAX_BEAMS];
    private final double[] by2 = new double[MAX_BEAMS];
    private final double[] bLife = new double[MAX_BEAMS];
    private final int[] bColor = new int[MAX_BEAMS];
    private int beamCount;

    // Blitzketten (Blitz-Turm)
    private final double[][] boltPts = new double[MAX_BOLTS][MAX_BOLT_POINTS * 2];
    private final int[] boltN = new int[MAX_BOLTS];
    private final double[] boltLife = new double[MAX_BOLTS];
    private final int[] boltColor = new int[MAX_BOLTS];
    private int boltCount;

    // verzögerte Feuerwerke (z.B. Boss)
    private final double[] dDelay = new double[MAX_DELAYED];
    private final double[] dx = new double[MAX_DELAYED];
    private final double[] dy = new double[MAX_DELAYED];
    private final double[] dPower = new double[MAX_DELAYED];
    private final int[] dColor = new int[MAX_DELAYED];
    private int delayedCount;

    private double time;
    /** Stärke des Bildschirmwackelns in Welteinheiten. */
    public double shake;

    private static final String[] MONEY_TEXT = new String[400];

    private static String moneyText(int v) {
        if (v < 0 || v >= MONEY_TEXT.length) {
            return "+" + v;
        }
        if (MONEY_TEXT[v] == null) {
            MONEY_TEXT[v] = "+" + v;
        }
        return MONEY_TEXT[v];
    }

    public void clear() {
        particles.clear();
        beamCount = 0;
        boltCount = 0;
        delayedCount = 0;
        shake = 0;
    }

    // ---------------------------------------------------------------------------------------- Erzeuger

    /**
     * Kleines Feuerwerk beim Zerstören eines Gegners.
     *
     * @param power 1 = normaler Gegner, größer = größeres Feuerwerk
     */
    public void firework(double x, double y, int rgb, double power) {
        double sp = Math.sqrt(power);
        particles.add(Particles.FLASH, x, y, 0, 0, 0.3, 58 * sp, Colors.lighten(rgb, 0.35));
        particles.add(Particles.FLASH, x, y, 0, 0, 0.12, 22 * sp, 0xFFFFFF);
        particles.add(Particles.RING, x, y, 0, 0, 0.45, 40 * sp, rgb);
        particles.add(Particles.RING, x, y, 0, 0, 0.3, 24 * sp, Colors.lighten(rgb, 0.6));
        int sparks = (int) (18 + 14 * power);
        double a0 = rng.angle();
        for (int i = 0; i < sparks; i++) {
            double a = a0 + Mathx.TAU * i / sparks + rng.range(-0.16, 0.16);
            double speed = (110 + rng.nextDouble() * 200) * sp;
            int c = rng.chance(0.25) ? Colors.lighten(rgb, 0.65) : rgb;
            particles.add(Particles.SPARK, x, y, Math.cos(a) * speed, Math.sin(a) * speed, rng.range(0.5, 1.0),
                    2.8, c);
        }
        int pops = (int) (3 + power * 1.5);
        for (int i = 0; i < pops; i++) {
            double a = rng.angle();
            double speed = rng.range(60, 140) * sp;
            particles.add(Particles.POP, x, y, Math.cos(a) * speed, Math.sin(a) * speed, rng.range(0.45, 0.8),
                    2.4, Colors.lighten(rgb, 0.2));
        }
        shake = Math.max(shake, Math.min(4, power * 1.0));
    }

    /** Mehrere Feuerwerke nacheinander rund um einen Punkt (Boss, Sieg). */
    public void fireworkLater(double delay, double x, double y, int rgb, double power) {
        if (delayedCount >= MAX_DELAYED) {
            return;
        }
        dDelay[delayedCount] = delay;
        dx[delayedCount] = x;
        dy[delayedCount] = y;
        dColor[delayedCount] = rgb;
        dPower[delayedCount] = power;
        delayedCount++;
    }

    public void explosion(double x, double y, double radius, int rgb) {
        particles.add(Particles.FLASH, x, y, 0, 0, 0.25, radius * 1.1, Colors.lighten(rgb, 0.3));
        particles.add(Particles.RING, x, y, 0, 0, 0.38, radius, rgb);
        particles.add(Particles.RING, x, y, 0, 0, 0.5, radius * 0.6, Colors.lighten(rgb, 0.5));
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = rng.angle();
            double sp = rng.range(60, 220);
            particles.add(Particles.SPARK, x, y, Math.cos(a) * sp, Math.sin(a) * sp, rng.range(0.3, 0.6), 2.2,
                    rng.chance(0.3) ? Colors.lighten(rgb, 0.6) : rgb);
        }
        shake = Math.max(shake, 3);
    }

    public void ring(double x, double y, double radius, int rgb, double life) {
        particles.add(Particles.RING, x, y, 0, 0, life, radius, rgb);
    }

    public void flash(double x, double y, double radius, int rgb, double life) {
        particles.add(Particles.FLASH, x, y, 0, 0, life, radius, rgb);
    }

    /** Funkenstoß in eine Richtung (Mündungsfeuer, Treffer). */
    public void sparks(double x, double y, double angle, double spread, int count, double speed, int rgb) {
        for (int i = 0; i < count; i++) {
            double a = angle + rng.range(-spread, spread);
            double sp = speed * rng.range(0.4, 1.0);
            particles.add(Particles.SPARK, x, y, Math.cos(a) * sp, Math.sin(a) * sp, rng.range(0.15, 0.35), 1.8, rgb);
        }
    }

    public void beam(double x1, double y1, double x2, double y2, int rgb) {
        if (beamCount >= MAX_BEAMS) {
            return;
        }
        bx1[beamCount] = x1;
        by1[beamCount] = y1;
        bx2[beamCount] = x2;
        by2[beamCount] = y2;
        bColor[beamCount] = rgb;
        bLife[beamCount] = 0.2;
        beamCount++;
        flash(x2, y2, 22, rgb, 0.16);
        double ang = Math.atan2(y1 - y2, x1 - x2);
        sparks(x2, y2, ang, 0.9, 6, 170, Colors.lighten(rgb, 0.4));
    }

    public void bolt(double[] pts, int count, int rgb) {
        if (boltCount >= MAX_BOLTS) {
            return;
        }
        int n = Math.min(count, MAX_BOLT_POINTS);
        System.arraycopy(pts, 0, boltPts[boltCount], 0, n * 2);
        boltN[boltCount] = n;
        boltColor[boltCount] = rgb;
        boltLife[boltCount] = 0.24;
        boltCount++;
        for (int i = 1; i < n; i++) {
            flash(pts[i * 2], pts[i * 2 + 1], 18, rgb, 0.14);
        }
    }

    public void moneyPopup(double x, double y, int amount, int rgb, double fontSize) {
        particles.addText(x, y, moneyText(amount), rgb, fontSize);
    }

    public void text(double x, double y, String s, int rgb, double fontSize) {
        particles.addText(x, y, s, rgb, fontSize);
    }

    // ------------------------------------------------------------------------------ Ablauf und Zeichnen

    public void update(double dt) {
        time += dt;
        particles.update(dt);
        shake *= Math.exp(-9 * dt);
        if (shake < 0.05) {
            shake = 0;
        }
        for (int i = beamCount - 1; i >= 0; i--) {
            bLife[i] -= dt;
            if (bLife[i] <= 0) {
                int last = --beamCount;
                bx1[i] = bx1[last];
                by1[i] = by1[last];
                bx2[i] = bx2[last];
                by2[i] = by2[last];
                bLife[i] = bLife[last];
                bColor[i] = bColor[last];
            }
        }
        for (int i = boltCount - 1; i >= 0; i--) {
            boltLife[i] -= dt;
            if (boltLife[i] <= 0) {
                int last = --boltCount;
                if (i != last) {
                    System.arraycopy(boltPts[last], 0, boltPts[i], 0, boltN[last] * 2);
                    boltN[i] = boltN[last];
                    boltLife[i] = boltLife[last];
                    boltColor[i] = boltColor[last];
                }
            }
        }
        for (int i = delayedCount - 1; i >= 0; i--) {
            dDelay[i] -= dt;
            if (dDelay[i] <= 0) {
                firework(dx[i], dy[i], dColor[i], dPower[i]);
                int last = --delayedCount;
                dDelay[i] = dDelay[last];
                dx[i] = dx[last];
                dy[i] = dy[last];
                dColor[i] = dColor[last];
                dPower[i] = dPower[last];
            }
        }
    }

    public void render(Gfx g) {
        for (int i = 0; i < beamCount; i++) {
            double u = Mathx.clamp01(bLife[i] / 0.2);
            int c = bColor[i];
            g.save();
            g.additive(true);
            g.line(bx1[i], by1[i], bx2[i], by2[i], 2 + 9 * u, Colors.withAlpha(c, 0.16 * u));
            g.line(bx1[i], by1[i], bx2[i], by2[i], 1 + 4 * u, Colors.withAlpha(c, 0.45 * u));
            g.line(bx1[i], by1[i], bx2[i], by2[i], 0.8 + 1.6 * u, Colors.withAlpha(0xFFFFFF, u));
            g.restore();
        }
        for (int i = 0; i < boltCount; i++) {
            renderBolt(g, i);
        }
        particles.render(g);
    }

    private void renderBolt(Gfx g, int idx) {
        double u = Mathx.clamp01(boltLife[idx] / 0.24);
        int n = boltN[idx];
        double[] p = boltPts[idx];
        int c = boltColor[idx];
        // Jeder Frame zackt die Linie neu aus: sieht aus wie knisternde Entladung.
        int s = (int) (time * 60) * 7919 + idx * 104729;
        s ^= s >>> 13;
        g.save();
        g.additive(true);
        for (int pass = 0; pass < 2; pass++) {
            g.beginPath();
            int state = s;
            for (int i = 0; i < n - 1; i++) {
                double x1 = p[i * 2];
                double y1 = p[i * 2 + 1];
                double x2 = p[i * 2 + 2];
                double y2 = p[i * 2 + 3];
                double len = Math.max(1, Mathx.dist(x1, y1, x2, y2));
                double nx = -(y2 - y1) / len;
                double ny = (x2 - x1) / len;
                int pieces = Math.max(3, (int) (len / 14));
                if (i == 0) {
                    g.moveTo(x1, y1);
                }
                for (int k = 1; k <= pieces; k++) {
                    double t = (double) k / pieces;
                    state = state * 1664525 + 1013904223;
                    double jitter = k == pieces ? 0 : (((state >>> 8) & 0xFFFF) / 65535.0 - 0.5) * 16;
                    g.lineTo(x1 + (x2 - x1) * t + nx * jitter, y1 + (y2 - y1) * t + ny * jitter);
                }
            }
            if (pass == 0) {
                g.stroke(7, Colors.withAlpha(c, 0.22 * u));
            } else {
                g.stroke(2.2, Colors.withAlpha(0xFFFFFF, 0.9 * u));
                g.stroke(1.2, Colors.withAlpha(c, u));
            }
        }
        g.restore();
    }
}
