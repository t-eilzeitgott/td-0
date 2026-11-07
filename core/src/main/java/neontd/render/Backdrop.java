package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;

/**
 * Der Untergrund des Spielfelds – pro Welt ein eigenes, sehr dezentes Motiv statt eines Rasters. Alles bleibt
 * dunkel und ruhig (Deckkraft im einstelligen Prozentbereich), damit Bahn, Türme und Gegner klar vorne stehen.
 * Die Elemente werden je Welt einmal deterministisch erzeugt und nur noch gezeichnet (günstig, keine Verläufe bis
 * auf wenige Lichtflecken).
 */
public final class Backdrop {
    private Backdrop() {
    }

    private static int cachedWorld = -1;
    private static double cachedW;
    private static double cachedH;
    /** Elemente: x, y, Größe, Phase, Variante. */
    private static double[][] items = new double[0][];

    private static long seed;

    private static double rnd() {
        seed = (seed * 6364136223846793005L + 1442695040888963407L);
        return ((seed >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    private static void build(int world, double w, double h) {
        seed = 0x9E3779B97F4A7C15L * (world + 7);
        int n;
        switch (world) {
            case 1: n = 70; break;   // Neonstadt: Häuserblöcke
            case 2: n = 46; break;   // Tiefsee: Blasen
            case 3: n = 18; break;   // Magmakern: Risse
            case 4: n = 34; break;   // Gewächshaus: Ranken
            case 5: n = 160; break;  // Raumhafen: Sterne
            case 6: n = 24; break;   // Kathedrale: Fenster-Speichen
            case 7: n = 46; break;   // Datenstrom: Spalten
            case 8: n = 140; break;  // Ereignishorizont: Teilchen
            default: n = 90; break;  // eigene Level: Punktfeld
        }
        items = new double[n][];
        for (int i = 0; i < n; i++) {
            items[i] = new double[] {rnd() * w, rnd() * h, rnd(), rnd() * Math.PI * 2, rnd()};
        }
        cachedWorld = world;
        cachedW = w;
        cachedH = h;
    }

    /** Zeichnet den Untergrund für Welt {@code world} (1–8, sonst neutral) in einer Fläche w × h. */
    public static void draw(Gfx g, int world, int accent, double w, double h, double time) {
        if (world != cachedWorld || w != cachedW || h != cachedH) {
            build(world, w, h);
        }
        g.fillRect(0, 0, w, h, 0x000000);
        // eine große, kaum sichtbare Aufhellung in der Weltfarbe gibt Tiefe
        if (Neon.quality >= 1) {
            g.radialGlow(w * 0.5, h * 0.45, Math.max(w, h) * 0.62, Colors.withAlpha(accent, 0.05));
        }
        switch (world) {
            case 1: city(g, accent, time); break;
            case 2: sea(g, accent, w, h, time); break;
            case 3: magma(g, accent, w, h, time); break;
            case 4: garden(g, accent, time); break;
            case 5: space(g, accent, w, h, time); break;
            case 6: cathedral(g, accent, w, h, time); break;
            case 7: data(g, accent, h, time); break;
            case 8: horizon(g, accent, w, h, time); break;
            default: dots(g, accent, time); break;
        }
        vignette(g, w, h);
    }

    /** Weicher dunkler Rand, damit das Feld ohne Rahmen ins Schwarz ausläuft. */
    private static void vignette(Gfx g, double w, double h) {
        int steps = 8;
        double d = 26;
        for (int i = 0; i < steps; i++) {
            double t = 1 - i / (double) steps;
            int c = Colors.withAlpha(0x000000, 0.55 * t * t);
            double o = i * d / steps;
            g.fillRect(0, o, w, d / steps, c);
            g.fillRect(0, h - o - d / steps, w, d / steps, c);
            g.fillRect(o, 0, d / steps, h, c);
            g.fillRect(w - o - d / steps, 0, d / steps, h, c);
        }
    }

    // ------------------------------------------------------------------------------------------ Motive

    /** Neutral: ein feines, ruhiges Punktfeld. */
    private static void dots(Gfx g, int accent, double time) {
        for (double[] it : items) {
            double a = 0.10 + 0.08 * Math.sin(time * 0.6 + it[3]);
            g.fillCircle(it[0], it[1], 1.1 + it[2] * 0.9, Colors.withAlpha(0xFFFFFF, a * 0.5));
        }
    }

    /** Neonstadt: Grundrisse von Häuserblöcken mit vereinzelt blinkenden Fenstern. */
    private static void city(Gfx g, int accent, double time) {
        for (double[] it : items) {
            double bw = 30 + it[2] * 70;
            double bh = 24 + it[4] * 60;
            g.strokeRect(it[0] - bw / 2, it[1] - bh / 2, bw, bh, 1, Colors.withAlpha(accent, 0.07));
            int lights = (int) (it[2] * 4);
            for (int k = 0; k < lights; k++) {
                double on = Math.sin(time * (0.4 + k * 0.3) + it[3] + k * 1.7);
                if (on > 0.2) {
                    double lx = it[0] - bw / 2 + 6 + ((k * 13 + (int) (it[4] * 40)) % Math.max(8, (int) bw - 12));
                    double ly = it[1] - bh / 2 + 6 + ((k * 9) % Math.max(6, (int) bh - 12));
                    g.fillRect(lx, ly, 3, 3, Colors.withAlpha(k % 2 == 0 ? accent : 0xFFD60A, 0.18 * on));
                }
            }
        }
    }

    /** Tiefsee: wandernde Lichtwellen (Kaustik) und aufsteigende Blasen. */
    private static void sea(Gfx g, int accent, double w, double h, double time) {
        for (int k = 0; k < 7; k++) {
            double y0 = h * (k + 0.5) / 7;
            g.beginPath();
            for (double x = 0; x <= w; x += 24) {
                double y = y0 + Math.sin(x * 0.012 + time * 0.5 + k * 1.3) * 18 + Math.sin(x * 0.031 - time * 0.3 + k) * 7;
                if (x == 0) {
                    g.moveTo(x, y);
                } else {
                    g.lineTo(x, y);
                }
            }
            g.stroke(1.4, Colors.withAlpha(accent, 0.06));
        }
        for (double[] it : items) {
            double speed = 8 + it[2] * 14;
            double y = ((it[1] - time * speed) % h + h) % h;
            double x = it[0] + Math.sin(time * 0.8 + it[3]) * 6;
            g.strokeCircle(x, y, 2 + it[2] * 4, 1, Colors.withAlpha(accent, 0.14));
        }
    }

    /** Magmakern: glimmende Risse im Boden und aufsteigende Glut. */
    private static void magma(Gfx g, int accent, double w, double h, double time) {
        for (int i = 0; i < items.length; i++) {
            double[] it = items[i];
            double pulse = 0.5 + 0.5 * Math.sin(time * 0.9 + it[3]);
            long s = (long) (it[4] * 1e6) + i;
            double x = it[0];
            double y = it[1];
            double ang = it[3];
            g.beginPath();
            g.moveTo(x, y);
            for (int k = 0; k < 6; k++) {
                s = s * 1103515245L + 12345L;
                ang += ((s >>> 16) % 1000 / 1000.0 - 0.5) * 1.2;
                double len = 18 + it[2] * 30;
                x += Math.cos(ang) * len;
                y += Math.sin(ang) * len;
                g.lineTo(x, y);
            }
            g.stroke(3.2, Colors.withAlpha(accent, 0.03 + 0.03 * pulse));
            g.stroke(1.1, Colors.withAlpha(0xFFB060, 0.07 + 0.08 * pulse));
        }
        for (int i = 0; i < items.length * 2; i++) {
            double[] it = items[i % items.length];
            double ph = it[3] + i;
            double y = ((it[1] + i * 37 - time * (10 + (i % 5) * 4)) % h + h) % h;
            double x = (it[0] + i * 53) % w + Math.sin(time + ph) * 5;
            g.fillCircle(x, y, 1.2, Colors.withAlpha(0xFFB060, 0.18 + 0.12 * Math.sin(time * 2 + ph)));
        }
    }

    /** Gewächshaus: Glasscheiben (Sechsecke) und schwebende Sporen. */
    private static void garden(Gfx g, int accent, double time) {
        for (double[] it : items) {
            double r = 26 + it[2] * 34;
            g.beginPath();
            g.ngon(it[0], it[1], r, 6, Math.PI / 6);
            g.stroke(1, Colors.withAlpha(accent, 0.06));
            double sx = it[0] + Math.sin(time * 0.35 + it[3]) * 22;
            double sy = it[1] + Math.cos(time * 0.27 + it[3]) * 16;
            g.fillCircle(sx, sy, 1.6, Colors.withAlpha(accent, 0.22));
        }
    }

    /** Raumhafen: funkelnde Sterne und Landeplatz-Ringe. */
    private static void space(Gfx g, int accent, double w, double h, double time) {
        for (double[] it : items) {
            double tw = 0.5 + 0.5 * Math.sin(time * (0.8 + it[2] * 2) + it[3]);
            double r = 0.6 + it[2] * 1.2;
            g.fillCircle(it[0], it[1], r, Colors.withAlpha(it[4] > 0.85 ? accent : 0xFFFFFF, 0.08 + 0.22 * tw * it[2]));
        }
        for (int k = 0; k < 3; k++) {
            double[] it = items[k];
            g.strokeCircle(it[0], it[1], 70 + k * 20, 1, Colors.withAlpha(accent, 0.05));
            g.beginPath();
            g.circle(it[0], it[1], 52 + k * 20);
            g.strokeDashed(1, Colors.withAlpha(accent, 0.07), 6, 10, time * 8 * (k % 2 == 0 ? 1 : -1));
        }
    }

    /** Kathedrale: eine große Rosette in der Mitte und Lichtstrahlen. */
    private static void cathedral(Gfx g, int accent, double w, double h, double time) {
        double cx = w / 2;
        double cy = h / 2;
        double R = Math.min(w, h) * 0.46;
        for (int ring = 1; ring <= 4; ring++) {
            g.strokeCircle(cx, cy, R * ring / 4, 1, Colors.withAlpha(accent, 0.05));
        }
        int spokes = items.length;
        for (int i = 0; i < spokes; i++) {
            double a = i * Math.PI * 2 / spokes + time * 0.01;
            g.beginPath();
            g.moveTo(cx + Math.cos(a) * R * 0.25, cy + Math.sin(a) * R * 0.25);
            g.lineTo(cx + Math.cos(a) * R, cy + Math.sin(a) * R);
            g.stroke(1, Colors.withAlpha(accent, 0.045));
            double pa = a + Math.PI / spokes;
            g.strokeCircle(cx + Math.cos(pa) * R * 0.78, cy + Math.sin(pa) * R * 0.78, R * 0.07, 1, Colors.withAlpha(accent, 0.05));
        }
        for (int k = 0; k < 4; k++) {
            double x = w * (0.15 + 0.23 * k) + Math.sin(time * 0.2 + k) * 20;
            double a = 0.025 + 0.015 * Math.sin(time * 0.5 + k * 2);
            g.beginPath();
            g.moveTo(x - 30, 0);
            g.lineTo(x + 30, 0);
            g.lineTo(x + 110, h);
            g.lineTo(x + 40, h);
            g.closePath();
            g.fill(Colors.withAlpha(accent, a));
        }
    }

    /** Datenstrom: fallende Spalten aus kurzen Zeichen-Strichen. */
    private static void data(Gfx g, int accent, double h, double time) {
        for (double[] it : items) {
            double speed = 30 + it[2] * 60;
            double head = ((it[1] + time * speed) % (h + 160)) - 80;
            for (int k = 0; k < 9; k++) {
                double y = head - k * 16;
                if (y < -10 || y > h + 10) {
                    continue;
                }
                double a = (k == 0 ? 0.28 : 0.13) * (1 - k / 10.0);
                double len = 4 + ((int) (it[4] * 100 + k * 7) % 3) * 3;
                g.fillRect(it[0], y, 2, len, Colors.withAlpha(k == 0 ? 0xFFFFFF : accent, a));
            }
        }
    }

    /** Ereignishorizont: eine langsam drehende Akkretionsscheibe aus Teilchen um die Mitte. */
    private static void horizon(Gfx g, int accent, double w, double h, double time) {
        double cx = w / 2;
        double cy = h / 2;
        double R = Math.min(w, h) * 0.5;
        for (int ring = 0; ring < 3; ring++) {
            g.beginPath();
            g.circle(cx, cy, R * (0.2 + ring * 0.13));
            g.strokeDashed(1, Colors.withAlpha(accent, 0.07), 14, 18, time * (12 - ring * 4));
        }
        for (double[] it : items) {
            double rr = R * (0.18 + it[2] * 0.95);
            double a = it[3] + time * (0.5 / (0.3 + it[2] * 2));
            double x = cx + Math.cos(a) * rr * 1.25;
            double y = cy + Math.sin(a) * rr * 0.75;
            g.fillCircle(x, y, 0.8 + (1 - it[2]) * 1.2, Colors.withAlpha(it[4] > 0.7 ? 0xFFFFFF : accent, 0.10 + 0.18 * (1 - it[2])));
        }
        g.fillCircle(cx, cy, R * 0.12, Colors.withAlpha(0x000000, 0.9));
        g.strokeCircle(cx, cy, R * 0.12, 1.5, Colors.withAlpha(accent, 0.12));
    }
}
