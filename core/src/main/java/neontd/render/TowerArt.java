package neontd.render;

import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.math.Mathx;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.UpgradeTrack;
import neontd.ui.Easing;

/**
 * Die Turm-Symbole: klare geometrische Formen in ihrer Neonfarbe, auf einem gestalteten Körper. Je weiter ein Turm
 * ausgebaut ist, desto aufwendiger wird er (Stufen 0–5, siehe {@link #tierOf}).
 */
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
        int tier = tierOf(t.totalLevels());
        int c = t.type.color;
        g.save();
        g.translate(t.x, t.y);
        g.scale(pop, pop);
        g.alpha(Math.min(1, t.age * 6));
        if (t.pad != null) {
            padAura(g, t.pad, time);
        }
        // Stufe 5: pulsierende Aura (einziger radialer Schein pro Turm, nur für voll ausgebaute Türme)
        if (tier >= 5) {
            double p = 0.5 + 0.5 * Math.sin(time * 2.4 + t.x * 0.01);
            Neon.halo(g, 0, 0, 70 + 6 * p, c, 0.16 + 0.1 * p);
        }
        if (tier >= 3) {
            outerHalo(g, c, tier, time);
        }
        if (tier >= 4) {
            crown(g, c, time);
        }
        body(g, t.type, 28, tier, time);
        inner(g, t.type, 28, t.aim, t.recoil, time);
        if (tier >= 3) {
            // hellerer Kern
            g.fillCircle(0, 0, tier >= 5 ? 5.2 : 3.6, tier >= 5 ? 0xFFFFFF : Colors.lighten(c, 0.6));
        }
        trackTicks(g, t);
        if (tier >= 2) {
            satellites(g, c, tier, time);
        }
        if (tier >= 5) {
            sparks(g, c, time);
        }
        marks(g, t, tier);
        if (t.pad != null) {
            padBadge(g, t.pad, time);
        }
        g.restore();
    }

    /** Sichtbare Ausbaustufe 0–5 aus der Summe aller Upgrade-Stufen (0–15, im Endlosmodus mehr). */
    public static int tierOf(int totalLevels) {
        if (totalLevels >= 15) {
            return 5;
        }
        return Math.max(0, totalLevels / 3);
    }

    /** Stufe 3+: segmentierter Außenring, der sich langsam dreht. */
    private static void outerHalo(Gfx g, int c, int tier, double time) {
        int n = 12;
        double r = 41;
        double rot = time * 0.35;
        double seg = Math.PI * 2 / n;
        for (int i = 0; i < n; i++) {
            double a = rot + i * seg;
            g.beginPath();
            g.arc(0, 0, r, a + seg * 0.18, a + seg * 0.82);
            if (i % 3 == 0) {
                Neon.stroke(g, 2.2, Colors.withAlpha(c, 0.95), 4);
            } else {
                g.stroke(1.6, Colors.withAlpha(c, 0.5));
            }
        }
    }

    /** Stufe 4+: gegenläufiger Ring mit Kerben und sechs Kronenzacken. */
    private static void crown(Gfx g, int c, double time) {
        double rot = -time * 0.5;
        g.beginPath();
        g.circle(0, 0, 46);
        g.strokeDashed(1.2, Colors.withAlpha(c, 0.45), 3, 5, rot * 20);
        g.save();
        g.rotate(rot * 0.6);
        g.beginPath();
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            double w = 0.13;
            g.moveTo(Math.cos(a - w) * 33, Math.sin(a - w) * 33);
            g.lineTo(ca * 39, sa * 39);
            g.lineTo(Math.cos(a + w) * 33, Math.sin(a + w) * 33);
            g.closePath();
        }
        g.fill(Colors.withAlpha(Colors.lighten(c, 0.35), 0.9));
        g.restore();
    }

    /** Stufe 2+: kreisende Knoten (2, ab Stufe 3 drei) in Turmfarbe. */
    private static void satellites(Gfx g, int c, int tier, double time) {
        int n = tier >= 3 ? 3 : 2;
        double r = 35;
        double rot = time * 1.1;
        int lc = Colors.lighten(c, 0.45);
        for (int i = 0; i < n; i++) {
            double a = rot + i * Math.PI * 2 / n;
            double x = Math.cos(a) * r;
            double y = Math.sin(a) * r;
            g.save();
            g.translate(x, y);
            g.rotate(a);
            g.beginPath();
            g.moveTo(4.2, 0);
            g.lineTo(0, -3.2);
            g.lineTo(-4.2, 0);
            g.lineTo(0, 3.2);
            g.closePath();
            g.fill(lc);
            if (Neon.quality >= 2) {
                g.stroke(3.5, Colors.withAlpha(c, 0.25));
            }
            g.restore();
        }
    }

    /** Stufe 5: winzige, schnell kreisende Funken. */
    private static void sparks(Gfx g, int c, double time) {
        for (int i = 0; i < 5; i++) {
            double a = time * (2.2 + i * 0.13) + i * 1.257;
            double r = 50 + 3 * Math.sin(time * 3 + i);
            g.fillCircle(Math.cos(a) * r, Math.sin(a) * r, 1.6, i % 2 == 0 ? 0xFFFFFF : Colors.lighten(c, 0.6));
        }
    }

    /** Ausbaupfad-Ränge und Spezialausbau als kleine, aufrecht stehende Marken unter dem Turm. */
    private static void marks(Gfx g, Tower t, int tier) {
        boolean sp = t.special != null;
        int ranks = t.branch > 0 ? t.rank : 0;
        if (!sp && ranks <= 0) {
            return;
        }
        g.save();
        g.rotate(uprightAngle);
        double y = tier >= 3 ? 53 : 42;
        double x0 = -(ranks * 9 + (sp ? 12 : 0)) / 2.0 + 4.5;
        for (int i = 0; i < ranks; i++) {
            double x = x0 + i * 9;
            g.beginPath();
            g.moveTo(x - 3.5, y + 2);
            g.lineTo(x, y - 2);
            g.lineTo(x + 3.5, y + 2);
            g.stroke(2, 0xFFD60A);
        }
        if (sp) {
            double x = x0 + ranks * 9 + 3;
            g.beginPath();
            g.moveTo(x, y - 4.5);
            g.lineTo(x + 4.5, y);
            g.lineTo(x, y + 4.5);
            g.lineTo(x - 4.5, y);
            g.closePath();
            g.fill(t.special.color);
            g.stroke(1.2, 0x0B0B12);
        }
        g.restore();
    }

    /**
     * Aura eines Turms auf einem Bonus-Pad: leuchtender Ring außerhalb der Upgrade-Bögen und darüber kreisende
     * Lichtsegmente in Pad-Farbe – damit der Bonus sichtbar bleibt, obwohl der Turm das Pad verdeckt.
     */
    private static void padAura(Gfx g, neontd.level.Zone.Kind pad, double time) {
        int c = pad.color;
        double pulse = 0.5 + 0.5 * Math.sin(time * 3.2);
        Neon.halo(g, 0, 0, 62, c, 0.2 + 0.12 * pulse);
        g.fillCircle(0, 0, 44, Colors.withAlpha(c, 0.12 + 0.06 * pulse));
        Neon.circle(g, 0, 0, 44, 2.2, Colors.withAlpha(c, 0.85), 8);
        for (int i = 0; i < 3; i++) {
            double a = time * 1.6 + i * Math.PI * 2 / 3;
            g.beginPath();
            g.arc(0, 0, 49, a, a + 0.75);
            Neon.stroke(g, 2.6, c, 7);
        }
    }

    /** Abzeichen oben rechts am Turm (bildschirmbezogen aufrecht) mit dem Symbol des Pad-Bonus. */
    private static void padBadge(Gfx g, neontd.level.Zone.Kind pad, double time) {
        int c = pad.color;
        double s = 1 + 0.08 * Math.sin(time * 3.2);
        g.save();
        g.rotate(uprightAngle);
        g.translate(24, -24);
        g.scale(s, s);
        Neon.halo(g, 0, 0, 20, c, 0.45);
        g.fillCircle(0, 0, 12.5, c);
        g.strokeCircle(0, 0, 12.5, 2, 0x0B0B12);
        neontd.gfx.Icons.draw(g, PathArt.padIcon(pad), 0, 0, 6.4, 0x0B0B12, 0);
        g.restore();
    }

    /** Ein Symbol ohne Upgrade-Anzeige, z. B. für den Shop (zeigt nach oben, leichte Eigenbewegung). */
    public static void drawIcon(Gfx g, TowerType type, double cx, double cy, double r, double time) {
        g.save();
        g.translate(cx, cy);
        double s = r / 28.0;
        g.scale(s, s);
        body(g, type, 28, 0, time);
        inner(g, type, 28, -Math.PI / 2, 0, time);
        g.restore();
    }

    /**
     * Upgrade-Stände der drei Pfade als segmentierte Bögen knapp außerhalb des Körpers: je Stufe ein Segment in der
     * Pfadfarbe, offene Stufen als feine Andeutung. Reichweite oben, Schaden rechts unten, Tempo links unten.
     */
    private static void trackTicks(Gfx g, Tower t) {
        double r = 33;
        int max = UpgradeTrack.MAX_LEVEL;
        double seg = TRACK_SPAN / max;
        for (int k = 0; k < 3; k++) {
            int lvl = t.level[k];
            if (lvl <= 0) {
                continue;
            }
            int col = UpgradeTrack.values()[k].color;
            double a0 = TRACK_ANGLE[k] - TRACK_SPAN / 2;
            for (int i = 0; i < max; i++) {
                double a = a0 + i * seg;
                g.beginPath();
                g.arc(0, 0, r, a + seg * 0.12, a + seg * 0.88);
                if (i < lvl) {
                    if (Neon.quality >= 2) {
                        g.stroke(5, Colors.withAlpha(col, 0.18));
                    }
                    g.stroke(2.6, col);
                } else {
                    g.stroke(1.2, Colors.withAlpha(col, 0.2));
                }
            }
            if (lvl > max) {
                // Meisterstufen (Endlos): kleiner Punkt am Bogenende
                double a = a0 + TRACK_SPAN + 0.12;
                g.fillCircle(Math.cos(a) * r, Math.sin(a) * r, 2.2, 0xFFD60A);
            }
        }
    }

    /**
     * Turmkörper: dunkler Kern mit leichtem Verlauf zur Mitte, Fase-Ring, klare Neon-Kontur und vier Kerben; ab
     * Stufe 1 ein langsam drehender Zierring im Inneren.
     */
    private static void body(Gfx g, TowerType type, double r, int tier, double time) {
        int c = type.color;
        int dark = Colors.darken(c, 0.86);
        g.fillCircle(0, 0, r, Colors.withAlpha(dark, 0.97));
        g.fillCircle(0, 0, r * 0.8, Colors.withAlpha(Colors.darken(c, 0.74), 0.55));
        g.fillCircle(0, 0, r * 0.52, Colors.withAlpha(Colors.darken(c, 0.62), 0.45));
        g.strokeCircle(0, 0, r * 0.86, 1, Colors.withAlpha(c, 0.3));
        Neon.circle(g, 0, 0, r, 2.4, c, 8);
        // vier feine Kerben auf der Kontur
        g.beginPath();
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            g.moveTo(Math.cos(a) * (r - 4.5), Math.sin(a) * (r - 4.5));
            g.lineTo(Math.cos(a) * (r - 1), Math.sin(a) * (r - 1));
        }
        g.stroke(1.6, Colors.withAlpha(Colors.lighten(c, 0.4), 0.8));
        if (tier >= 1) {
            g.beginPath();
            g.circle(0, 0, r * 0.68);
            g.strokeDashed(1.4, Colors.withAlpha(Colors.lighten(c, 0.2), 0.75), 4, 3.2, time * 6);
        }
    }

    /** Zeichnet das typische Innenleben (Lauf, Kristall, Spule …) im Ursprung; r = Radius des Turmkörpers. */
    private static void inner(Gfx g, TowerType type, double r, double aim, double recoil, double time) {
        int c = type.color;

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
