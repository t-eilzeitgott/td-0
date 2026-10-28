package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.math.Mathx;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.ui.Easing;

/** Die fünf Turm-Symbole: klare geometrische Formen, jede in ihrer eigenen Neonfarbe. */
public final class TowerArt {
    private TowerArt() {
    }

    /**
     * Ist die Karte gedreht gezeichnet (Handy im Hochformat), dreht dieser Winkel (Radiant) Symbole zurück, die
     * aufrecht stehen sollen – derzeit der Blitz. Wird von der Spielszene vor dem Zeichnen gesetzt.
     */
    public static double uprightAngle;

    /** Winkel der drei Upgrade-Bögen (Mitte): Reichweite oben, Schaden rechts unten, Tempo links unten. */
    private static final double[] TRACK_ANGLE = {-Math.PI / 2, Math.PI / 6, Math.PI * 5 / 6};
    private static final double TRACK_SPAN = Math.PI * 0.62;

    /** Ein platzierter Turm in der Spielwelt. */
    public static void drawTower(Gfx g, Tower t, double time) {
        double pop = Easing.outBack(Mathx.clamp01(t.age / 0.32));
        if (t.pulse > 0) {
            // Upgrade: kurz aufplustern und zurückfedern
            pop *= 1 + 0.16 * Math.sin(t.pulse * Math.PI) * t.pulse;
        }
        g.save();
        g.translate(t.x, t.y);
        g.scale(pop, pop);
        g.alpha(Math.min(1, t.age * 6));
        symbol(g, t.type, 28, t.aim, t.recoil, time);
        for (int k = 0; k < 3; k++) {
            if (t.level[k] > 0) {
                levelArc(g, k, t.level[k], 28 + 8);
            }
        }
        g.restore();
    }

    /** Ein Symbol ohne Upgrade-Anzeige, z. B. für den Shop (zeigt nach oben, leichte Eigenbewegung). */
    public static void drawIcon(Gfx g, TowerType type, double cx, double cy, double r, double time) {
        g.save();
        g.translate(cx, cy);
        double s = r / 28.0;
        g.scale(s, s);
        symbol(g, type, 28, -Math.PI / 2, 0, time);
        g.restore();
    }

    private static void levelArc(Gfx g, int track, int level, double radius) {
        UpgradeTrack ut = UpgradeTrack.values()[track];
        double a0 = TRACK_ANGLE[track] - TRACK_SPAN / 2;
        g.beginPath();
        g.arc(0, 0, radius, a0, a0 + TRACK_SPAN);
        g.stroke(2.6, Colors.withAlpha(ut.color, 0.16));
        double lit = TRACK_SPAN * level / UpgradeTrack.MAX_LEVEL;
        g.beginPath();
        g.arc(0, 0, radius, a0, a0 + lit);
        Neon.stroke(g, 2.8, ut.color, 5);
    }

    /** Zeichnet das Symbol im Ursprung; r = Radius des Turmkörpers. */
    private static void symbol(Gfx g, TowerType type, double r, double aim, double recoil, double time) {
        int c = type.color;
        g.fillCircle(0, 0, r, Colors.withAlpha(Colors.darken(c, 0.82), 0.96));
        Neon.circle(g, 0, 0, r, 2.6, c, 9);
        g.strokeCircle(0, 0, r * 0.74, 1.1, Colors.withAlpha(c, 0.28));

        g.save();
        switch (type) {
            case PULSE: {
                g.rotate(aim);
                double k = recoil * r * 0.14;
                g.beginPath();
                g.moveTo(r * 0.58 - k, 0);
                g.lineTo(-r * 0.38 - k, -r * 0.42);
                g.lineTo(-r * 0.38 - k, r * 0.42);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.32));
                Neon.stroke(g, 2.4, Colors.lighten(c, 0.25), 6);
                break;
            }
            case SNIPER: {
                g.rotate(aim);
                double k = recoil * r * 0.28;
                g.beginPath();
                g.moveTo(-r * 0.46, 0);
                g.lineTo(-r * 0.02, -r * 0.34);
                g.lineTo(r * 0.3, 0);
                g.lineTo(-r * 0.02, r * 0.34);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.25), 6);
                Neon.line(g, r * 0.1 - k, 0, r * 1.08 - k, 0, 2.4, Colors.lighten(c, 0.3), 6);
                g.fillCircle(r * 1.08 - k, 0, 2.4, Colors.lighten(c, 0.7));
                break;
            }
            case MORTAR: {
                g.rotate(aim);
                double k = recoil * r * 0.2;
                g.beginPath();
                g.ngon(0, 0, r * 0.6, 6, Math.PI / 6);
                g.fill(Colors.withAlpha(c, 0.28));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.2), 6);
                g.beginPath();
                g.rect(r * 0.08 - k, -r * 0.2, r * 0.82, r * 0.4);
                g.fill(Colors.withAlpha(c, 0.4));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.3), 5);
                g.fillCircle(0, 0, r * 0.15, Colors.lighten(c, 0.5));
                break;
            }
            case FROST: {
                double spin = time * 0.35 + recoil * 0.6;
                g.beginPath();
                for (int i = 0; i < 3; i++) {
                    double a = spin + Math.PI * i / 3;
                    double cx = Math.cos(a);
                    double sy = Math.sin(a);
                    g.moveTo(-cx * r * 0.7, -sy * r * 0.7);
                    g.lineTo(cx * r * 0.7, sy * r * 0.7);
                    for (int s = -1; s <= 1; s += 2) {
                        double bx = cx * r * 0.42 * s;
                        double by = sy * r * 0.42 * s;
                        g.moveTo(bx - sy * r * 0.18, by + cx * r * 0.18);
                        g.lineTo(bx + sy * r * 0.18, by - cx * r * 0.18);
                    }
                }
                Neon.stroke(g, 2.0, Colors.lighten(c, 0.3), 6);
                g.beginPath();
                g.circle(0, 0, r * 0.88);
                g.strokeDashed(1.6, Colors.withAlpha(c, 0.6), 6, 9, time * 14);
                break;
            }
            case SALVE: {
                // Zwei kurze Läufe nebeneinander, dahinter ein Sechseck
                g.rotate(aim);
                double k = recoil * r * 0.16;
                g.beginPath();
                g.ngon(0, 0, r * 0.5, 6, 0);
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.2), 6);
                for (int s = -1; s <= 1; s += 2) {
                    g.beginPath();
                    g.rect(r * 0.12 - k, s * r * 0.22 - r * 0.1, r * 0.8, r * 0.2);
                    g.fill(Colors.withAlpha(c, 0.45));
                    Neon.stroke(g, 2.0, Colors.lighten(c, 0.3), 5);
                }
                break;
            }
            case TESLA: {
                // Spule: drehender Ring mit Zacken und leuchtendem Kern
                double spin = time * 0.9;
                g.beginPath();
                g.ngon(0, 0, r * 0.52, 6, spin);
                g.fill(Colors.withAlpha(c, 0.25));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.25), 6);
                g.beginPath();
                for (int i = 0; i < 6; i++) {
                    double a = spin + Math.PI * i / 3;
                    g.moveTo(Math.cos(a) * r * 0.52, Math.sin(a) * r * 0.52);
                    g.lineTo(Math.cos(a) * r * 0.86, Math.sin(a) * r * 0.86);
                }
                Neon.stroke(g, 2.0, Colors.lighten(c, 0.3), 5);
                for (int i = 0; i < 6; i++) {
                    double a = spin + Math.PI * i / 3;
                    g.fillCircle(Math.cos(a) * r * 0.86, Math.sin(a) * r * 0.86, 2.2 + recoil * 1.5, Colors.lighten(c, 0.6));
                }
                g.fillCircle(0, 0, r * (0.2 + 0.1 * recoil), Colors.lighten(c, 0.6));
                break;
            }
            case RAILGUN: {
                // Zwei lange Schienen mit Raute als Kammer
                g.rotate(aim);
                double k = recoil * r * 0.3;
                g.beginPath();
                g.moveTo(-r * 0.5, 0);
                g.lineTo(-r * 0.1, -r * 0.4);
                g.lineTo(r * 0.3, 0);
                g.lineTo(-r * 0.1, r * 0.4);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.2), 6);
                Neon.line(g, r * 0.0 - k, -r * 0.14, r * 1.1 - k, -r * 0.14, 2.4, Colors.lighten(c, 0.3), 6);
                Neon.line(g, r * 0.0 - k, r * 0.14, r * 1.1 - k, r * 0.14, 2.4, Colors.lighten(c, 0.3), 6);
                g.fillCircle(r * 1.1 - k, 0, 2.6, Colors.lighten(c, 0.7));
                break;
            }
            case ARC: {
                double pulse = 1 + recoil * 0.25;
                g.rotate(uprightAngle);
                g.scale(pulse, pulse);
                g.beginPath();
                g.moveTo(r * 0.12, -r * 0.66);
                g.lineTo(-r * 0.34, r * 0.08);
                g.lineTo(-r * 0.04, r * 0.08);
                g.lineTo(-r * 0.14, r * 0.66);
                g.lineTo(r * 0.36, -r * 0.14);
                g.lineTo(r * 0.06, -r * 0.14);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.35));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.3), 6);
                for (int i = 0; i < 4; i++) {
                    double a = time * 1.6 + Math.PI * i / 2;
                    g.fillCircle(Math.cos(a) * r * 0.84, Math.sin(a) * r * 0.84, 1.9, Colors.withAlpha(c, 0.9));
                }
                break;
            }
            case INFERNO: {
                // Flamme mit flackerndem Kern
                double fl = 1 + 0.07 * Math.sin(time * 13) + recoil * 0.12;
                g.scale(1, fl);
                g.beginPath();
                flame(g, r * 0.82);
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.25), 6);
                g.beginPath();
                flame(g, r * 0.42);
                g.fill(Colors.withAlpha(Colors.lighten(c, 0.5), 0.55));
                Neon.stroke(g, 1.6, Colors.lighten(c, 0.6), 4);
                break;
            }
            case VENOM: {
                // Giftstropfen mit aufsteigenden Blasen
                g.beginPath();
                g.moveTo(0, -r * 0.78);
                g.lineTo(r * 0.44, -r * 0.02);
                g.lineTo(r * 0.5, r * 0.3);
                g.lineTo(r * 0.3, r * 0.58);
                g.lineTo(0, r * 0.68);
                g.lineTo(-r * 0.3, r * 0.58);
                g.lineTo(-r * 0.5, r * 0.3);
                g.lineTo(-r * 0.44, -r * 0.02);
                g.closePath();
                g.fill(Colors.withAlpha(c, 0.28));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.25), 6);
                for (int i = 0; i < 3; i++) {
                    double ph = (time * 0.7 + i / 3.0) % 1.0;
                    g.strokeCircle((i - 1) * r * 0.22, r * 0.4 - ph * r * 0.9, r * (0.07 + 0.05 * (i % 2)), 1.4,
                            Colors.withAlpha(Colors.lighten(c, 0.5), 1 - ph));
                }
                g.fillCircle(-r * 0.12, r * 0.12, r * 0.09, Colors.lighten(c, 0.6));
                break;
            }
            case SHREDDER: {
                // Fächer aus drei kurzen Läufen
                g.rotate(aim);
                double k = recoil * r * 0.22;
                g.beginPath();
                g.ngon(0, 0, r * 0.5, 6, 0);
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.2), 6);
                for (int s2 = -1; s2 <= 1; s2++) {
                    double ang = s2 * 0.34;
                    double ca = Math.cos(ang);
                    double sa = Math.sin(ang);
                    Neon.line(g, ca * r * 0.25 - k, sa * r * 0.25, ca * r * 0.98 - k, sa * r * 0.98, 3.2,
                            Colors.lighten(c, 0.3), 5);
                }
                break;
            }
            case GRAVITON: {
                // Strudel: gegenläufige Bögen um einen dunklen Kern
                for (int i = 0; i < 3; i++) {
                    double rr = r * (0.28 + 0.2 * i);
                    double spin = time * (i % 2 == 0 ? 1.4 : -1.1) * (1 + 0.4 * recoil) + i * 2.1;
                    g.beginPath();
                    g.arc(0, 0, rr, spin, spin + Math.PI * 1.25);
                    Neon.stroke(g, 2.1, Colors.lighten(c, 0.15 + 0.1 * i), 5);
                }
                g.fillCircle(0, 0, r * 0.12, Colors.lighten(c, 0.6));
                break;
            }
            case MINT: {
                // Münze mit Raute; pulsiert beim Prägen
                double pulse = 1 + recoil * 0.2;
                g.scale(pulse, pulse);
                g.beginPath();
                g.circle(0, 0, r * 0.56);
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.4, Colors.lighten(c, 0.25), 6);
                g.strokeCircle(0, 0, r * 0.42, 1.2, Colors.withAlpha(c, 0.55));
                g.beginPath();
                g.ngon(0, 0, r * 0.26, 4, 0);
                g.fill(Colors.withAlpha(Colors.lighten(c, 0.5), 0.55));
                Neon.stroke(g, 1.6, Colors.lighten(c, 0.6), 3);
                for (int i = 0; i < 3; i++) {
                    double ph = (time * 0.5 + i / 3.0) % 1.0;
                    g.fillCircle(Math.cos(i * 2.1 + 0.6) * r * (0.7 + 0.15 * ph), Math.sin(i * 2.1 + 0.6) * r * (0.7 + 0.15 * ph),
                            1.6, Colors.withAlpha(Colors.lighten(c, 0.6), 1 - ph));
                }
                break;
            }
            case PRISMA: {
                // Dreieck mit Fächer aus Lichtstrahlen
                g.rotate(aim);
                double k = recoil * r * 0.12;
                g.beginPath();
                g.ngon(0, 0, r * 0.52, 3, 0);
                g.fill(Colors.withAlpha(c, 0.3));
                Neon.stroke(g, 2.2, Colors.lighten(c, 0.3), 6);
                int[] ray = {0xFF5470, 0xFFD60A, 0x00E5FF};
                for (int i = 0; i < 3; i++) {
                    double ang = (i - 1) * 0.16;
                    Neon.line(g, r * 0.4 - k, 0, Math.cos(ang) * r * 1.0 - k, Math.sin(ang) * r * 1.0, 2.0,
                            Colors.lerp(c, ray[i], 0.6), 5);
                }
                break;
            }
            case BEACON: {
                // Sendemast mit pulsierenden Wellen
                Neon.line(g, 0, r * 0.62, 0, -r * 0.18, 2.4, Colors.lighten(c, 0.3), 5);
                g.beginPath();
                g.rect(-r * 0.28, r * 0.5, r * 0.56, r * 0.14);
                g.fill(Colors.withAlpha(c, 0.4));
                Neon.stroke(g, 1.8, Colors.lighten(c, 0.3), 4);
                g.fillCircle(0, -r * 0.3, r * 0.12, Colors.lighten(c, 0.7));
                for (int i = 0; i < 2; i++) {
                    double ph = (time * 0.9 + i * 0.5) % 1.0;
                    double rr = r * (0.3 + 0.45 * ph);
                    g.beginPath();
                    g.arc(0, -r * 0.3, rr, -Math.PI * 0.85, -Math.PI * 0.15);
                    g.stroke(1.8, Colors.withAlpha(Colors.lighten(c, 0.3), 0.9 * (1 - ph)));
                    g.beginPath();
                    g.arc(0, -r * 0.3, rr, Math.PI * 0.15, Math.PI * 0.85);
                    g.stroke(1.8, Colors.withAlpha(Colors.lighten(c, 0.3), 0.9 * (1 - ph)));
                }
                break;
            }
            case ORBITAL: {
                // Fadenkreuz mit kreisenden Satelliten
                double rr = r * (0.55 + 0.1 * recoil);
                g.beginPath();
                g.circle(0, 0, rr);
                Neon.stroke(g, 2.0, Colors.lighten(c, 0.2), 6);
                g.beginPath();
                g.moveTo(-r * 0.85, 0);
                g.lineTo(-r * 0.3, 0);
                g.moveTo(r * 0.3, 0);
                g.lineTo(r * 0.85, 0);
                g.moveTo(0, -r * 0.85);
                g.lineTo(0, -r * 0.3);
                g.moveTo(0, r * 0.3);
                g.lineTo(0, r * 0.85);
                Neon.stroke(g, 1.8, Colors.lighten(c, 0.3), 4);
                for (int i = 0; i < 3; i++) {
                    double ang = time * 0.8 + i * 2.094;
                    g.fillCircle(Math.cos(ang) * rr, Math.sin(ang) * rr, 2.6, Colors.lighten(c, 0.6));
                }
                g.fillCircle(0, 0, r * 0.1, Colors.lighten(c, 0.6));
                break;
            }
            default:
                break;
        }
        g.restore();
    }

    /** Flammenumriss im Ursprung (Höhe etwa 1,5 r). */
    private static void flame(Gfx g, double r) {
        g.moveTo(0, -r * 0.95);
        g.lineTo(r * 0.3, -r * 0.35);
        g.lineTo(r * 0.62, r * 0.05);
        g.lineTo(r * 0.48, r * 0.6);
        g.lineTo(0, r * 0.88);
        g.lineTo(-r * 0.48, r * 0.6);
        g.lineTo(-r * 0.62, r * 0.05);
        g.lineTo(-r * 0.26, -r * 0.3);
        g.closePath();
    }
}
