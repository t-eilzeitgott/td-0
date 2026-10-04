package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.sim.EnemyType;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;

/**
 * Die zehn eingebauten Level, von einfach bis komplex. Jedes hat 20 handgebaute Wellen (Bronze) und läuft danach im
 * Endlosmodus weiter (Silber ab Welle 50, Gold ab 100, Platin ab 250 – siehe {@code progress.Medals}). Schwerere
 * Level haben verwinkeltere oder mehrere Pfade und einen höheren Lebenspunkte-Faktor ({@link LevelDef#hpMul}).
 */
public final class Levels {
    public static final String SERPENTINE = "serpentine";

    private static final int CAMPAIGN_WAVES = 20;

    private Levels() {
    }

    public static List<LevelDef> builtins() {
        List<LevelDef> list = new ArrayList<>();
        list.add(serpentine());
        list.add(zickzack());
        list.add(spirale());
        list.add(zwillinge());
        list.add(kreuzung());
        list.add(labyrinth());
        list.add(dreiWege());
        list.add(zangen());
        list.add(festung());
        list.add(chaos());
        return list;
    }

    public static LevelDef find(String id) {
        for (LevelDef l : builtins()) {
            if (l.id.equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** Position (0-basiert) eines eingebauten Levels in der Reihenfolge oder -1. */
    public static int indexOf(String id) {
        List<LevelDef> all = builtins();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Die handgebauten Wellen eines eingebauten Levels: die Standardkurve mit dem Faktor des Levels, Finale mit zwei Titanen. */
    public static List<WaveDef> campaignWaves(LevelDef level) {
        List<WaveDef> waves = WaveFactory.generate(CAMPAIGN_WAVES, level.hpMul);
        WaveDef last = new WaveDef().bonus(WaveFactory.bonus(CAMPAIGN_WAVES));
        for (WaveDef.Group g : waves.get(CAMPAIGN_WAVES - 1).groups) {
            int count = g.type == EnemyType.BOSS ? 2 : g.count;
            last.groups.add(new WaveDef.Group(g.type, count, g.interval, g.delay, g.hp, g.path, g.reward));
        }
        waves.set(CAMPAIGN_WAVES - 1, last);
        return waves;
    }

    // ---------------------------------------------------------------------------------------- Hilfen

    private static LevelDef base(String id, String name, int difficulty, double hpMul, int money, int lives) {
        LevelDef l = new LevelDef(id, name);
        l.builtin = true;
        l.startMoney = money;
        l.startLives = lives;
        l.waveCount = CAMPAIGN_WAVES;
        l.difficulty = difficulty;
        l.hpMul = hpMul;
        return l;
    }

    /**
     * Polylinie mit abgerundeten Ecken: Jede innere Ecke wird durch zwei Punkte im Abstand {@code r} (oder der halben
     * Segmentlänge) ersetzt, damit der Spline weiche Kurven statt Überschwinger an scharfen Knicken bildet.
     */
    private static double[] rounded(double r, double... c) {
        int n = c.length / 2;
        List<Double> out = new ArrayList<>();
        out.add(c[0]);
        out.add(c[1]);
        for (int i = 1; i < n - 1; i++) {
            double px = c[i * 2 - 2];
            double py = c[i * 2 - 1];
            double x = c[i * 2];
            double y = c[i * 2 + 1];
            double nx = c[i * 2 + 2];
            double ny = c[i * 2 + 3];
            double d0 = Math.hypot(x - px, y - py);
            double d1 = Math.hypot(nx - x, ny - y);
            double r0 = Math.min(r, d0 / 2);
            double r1 = Math.min(r, d1 / 2);
            out.add(x + (px - x) / d0 * r0);
            out.add(y + (py - y) / d0 * r0);
            out.add(x + (nx - x) / d1 * r1);
            out.add(y + (ny - y) / d1 * r1);
        }
        out.add(c[n * 2 - 2]);
        out.add(c[n * 2 - 1]);
        double[] a = new double[out.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }
        return a;
    }

    // ---------------------------------------------------------------------------------------- Level

    /** 1 – Serpentine: ein ruhig geschwungener Pfad mit vielen Plätzen zwischen den Bahnen. */
    public static LevelDef serpentine() {
        LevelDef l = base(SERPENTINE, "Serpentine", 1, 1.0, 500, 20);
        l.paths.add(new double[] {
            30, 120, 250, 120, 430, 170, 620, 120, 840, 100, 1010, 140, 1120, 250, 1050, 370, 860, 410, 650, 375,
            450, 320, 270, 360, 170, 480, 250, 590, 450, 625, 680, 575, 900, 560, 1070, 610, 1250, 590
        });
        return l;
    }

    /** 2 – Zickzack: vier lange Bahnen im Wechsel, enge Wenden an den Rändern. */
    public static LevelDef zickzack() {
        LevelDef l = base("zickzack", "Zickzack", 2, 1.1, 500, 20);
        l.paths.add(rounded(70, 30, 90, 1190, 90, 1190, 250, 90, 250, 90, 410, 1190, 410, 1190, 570, 90, 570, 30, 570));
        return l;
    }

    /** 3 – Spirale: eine lange Spirale nach innen; Türme stehen zwischen den Windungen und in der Mitte. */
    public static LevelDef spirale() {
        LevelDef l = base("spirale", "Spirale", 3, 1.2, 520, 20);
        l.paths.add(rounded(70, 40, 60, 1240, 60, 1240, 660, 40, 660, 40, 170, 1130, 170, 1130, 550, 150, 550, 150, 280,
                1020, 280, 1020, 440, 260, 440, 260, 360, 640, 360));
        return l;
    }

    /**
     * Eine Wellenlinie von links nach rechts (für Kreuzungen): {@code periods} volle Schwingungen mit Amplitude
     * {@code amp} um die Höhe {@code cy}, {@code phase} im Bogenmaß.
     */
    private static double[] wave(double x0, double x1, double cy, double amp, double periods, double phase, int samples) {
        double[] a = new double[(samples + 1) * 2];
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            a[i * 2] = x0 + (x1 - x0) * t;
            a[i * 2 + 1] = cy + amp * Math.sin(Math.PI * 2 * periods * t + phase);
        }
        return a;
    }

    /** 4 – Zwillinge: zwei lange Schlangenbahnen oben und unten, Gegner wechseln sich ab. */
    public static LevelDef zwillinge() {
        LevelDef l = base("zwillinge", "Zwillinge", 4, 0.85, 900, 20);
        l.paths.add(rounded(55, 30, 60, 1190, 60, 1190, 170, 90, 170, 90, 280, 1190, 280, 1250, 280));
        l.paths.add(rounded(55, 30, 660, 1190, 660, 1190, 550, 90, 550, 90, 440, 1190, 440, 1250, 440));
        return l;
    }

    /** 5 – Kreuzung: zwei Wellenbahnen kreuzen sich viermal. */
    public static LevelDef kreuzung() {
        LevelDef l = base("kreuzung", "Kreuzung", 5, 0.85, 900, 20);
        l.paths.add(wave(30, 1250, 360, 290, 2.5, 0, 25));
        l.paths.add(wave(30, 1250, 360, 290, 2.5, Math.PI, 25));
        return l;
    }

    /** 6 – Labyrinth: acht senkrechte Gänge nebeneinander, sehr lang. */
    public static LevelDef labyrinth() {
        LevelDef l = base("labyrinth", "Labyrinth", 6, 1.15, 700, 20);
        l.paths.add(rounded(60, 30, 60, 110, 60, 110, 660, 260, 660, 260, 60, 410, 60, 410, 660, 560, 660, 560, 60,
                710, 60, 710, 660, 860, 660, 860, 60, 1010, 60, 1010, 660, 1160, 660, 1160, 60, 1250, 60));
        return l;
    }

    /** 7 – Drei Wege: drei Bahnen mit je zwei Gängen übereinander. */
    public static LevelDef dreiWege() {
        LevelDef l = base("dreiwege", "Drei Wege", 7, 0.8, 1300, 20);
        l.paths.add(rounded(55, 30, 70, 1190, 70, 1190, 180, 30, 180));
        l.paths.add(rounded(55, 30, 300, 1190, 300, 1190, 410, 30, 410));
        l.paths.add(rounded(55, 30, 530, 1190, 530, 1190, 640, 30, 640));
        return l;
    }

    /** 8 – Zangen: zwei lange Bahnen von gegenüber, die sich in der Mitte treffen. */
    public static LevelDef zangen() {
        LevelDef l = base("zangen", "Zangen", 8, 1.05, 1100, 20);
        l.paths.add(rounded(60, 30, 60, 1190, 60, 1190, 170, 90, 170, 90, 280, 900, 280, 900, 360, 640, 360));
        l.paths.add(rounded(60, 1250, 660, 90, 660, 90, 550, 1190, 550, 1190, 440, 380, 440, 380, 360, 640, 360));
        return l;
    }

    /** 9 – Festung: drei Bahnen aus drei Richtungen auf eine Basis am rechten Rand zu. */
    public static LevelDef festung() {
        LevelDef l = base("festung", "Festung", 9, 0.85, 1500, 20);
        l.paths.add(rounded(55, 30, 60, 1050, 60, 1050, 170, 150, 170, 150, 250, 1150, 250, 1150, 360));
        double[] mid = wave(30, 1150, 360, 40, 6, 0, 24);
        l.paths.add(mid);
        l.paths.add(rounded(55, 30, 660, 1050, 660, 1050, 550, 150, 550, 150, 470, 1150, 470, 1150, 360));
        return l;
    }

    /** 10 – Chaos: drei lange, sich kreuzende Bahnen mit engen Kurven. */
    public static LevelDef chaos() {
        LevelDef l = base("chaos", "Chaos", 10, 1.1, 1500, 20);
        l.paths.add(rounded(60, 30, 60, 1000, 60, 1000, 300, 250, 300, 250, 500, 1100, 500, 1100, 640, 700, 640));
        l.paths.add(rounded(60, 30, 640, 600, 640, 600, 420, 1250, 420, 1250, 200, 400, 200, 400, 130, 30, 130));
        l.paths.add(rounded(60, 1250, 60, 1130, 60, 1130, 360, 820, 360, 820, 600, 120, 600, 120, 420));
        return l;
    }
}
