package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.sim.EnemyType;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;

/**
 * Die eingebauten Level: 10 Kapitel mit je 15 Leveln (150 insgesamt). Jedes Kapitel hat ein Thema (Pfadform) und
 * wird von Level zu Level verwinkelter und schwerer; das erste Level jedes Kapitels ist von Hand gebaut, die übrigen
 * 14 entstehen deterministisch aus Formparametern. Jedes Level hat 20 handgebaute Wellen (Bronze) und läuft danach
 * im Endlosmodus weiter (Silber ab Welle 50, Gold ab 100, Platin ab 250 – siehe {@code progress.Medals}).
 */
public final class Levels {
    public static final String SERPENTINE = "serpentine";
    public static final int CHAPTERS = 10;
    public static final int PER_CHAPTER = 15;
    /** Pseudo-Kapitel für die Übersicht der eigenen Level. */
    public static final int CUSTOM = 11;
    /** So viele Level eines Kapitels brauchen Bronze, damit das nächste Kapitel aufgeht (die übrigen sind Zugabe). */
    public static final int OPEN_NEXT_AT = 10;

    private static final int CAMPAIGN_WAVES = 20;
    private static final String[] CHAPTER_NAMES = {"Serpentine", "Zickzack", "Spirale", "Zwillinge", "Kreuzung",
        "Labyrinth", "Drei Wege", "Zangen", "Festung", "Chaos"};
    private static final String[] CHAPTER_IDS = {SERPENTINE, "zickzack", "spirale", "zwillinge", "kreuzung",
        "labyrinth", "dreiwege", "zangen", "festung", "chaos"};

    private static List<LevelDef> cache;

    private Levels() {
    }

    /** Alle 150 eingebauten Level in Spielreihenfolge (Kapitel für Kapitel). Die Liste wird einmal erzeugt und geteilt. */
    public static synchronized List<LevelDef> builtins() {
        if (cache == null) {
            List<LevelDef> list = new ArrayList<>();
            for (int c = 1; c <= CHAPTERS; c++) {
                for (int n = 1; n <= PER_CHAPTER; n++) {
                    list.add(generate(c, n));
                }
            }
            cache = java.util.Collections.unmodifiableList(list);
        }
        return cache;
    }

    public static String chapterName(int chapter) {
        return chapter == CUSTOM ? "Eigene Level" : CHAPTER_NAMES[chapter - 1];
    }

    /** Die 15 Level eines Kapitels. */
    public static List<LevelDef> chapter(int chapter) {
        return builtins().subList((chapter - 1) * PER_CHAPTER, chapter * PER_CHAPTER);
    }

    public static LevelDef find(String id) {
        int i = indexOf(id);
        return i < 0 ? null : builtins().get(i);
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

    /**
     * Welches Level muss Bronze haben, damit dieses spielbar ist? Innerhalb eines Kapitel das vorherige, beim ersten
     * Level eines Kapitels das {@link #OPEN_NEXT_AT}. des vorigen Kapitels; {@code null} für das allererste Level.
     */
    public static LevelDef prerequisite(LevelDef l) {
        if (!l.builtin || l.chapter < 1) {
            return null;
        }
        if (l.number > 1) {
            return chapter(l.chapter).get(l.number - 2);
        }
        return l.chapter > 1 ? chapter(l.chapter - 1).get(OPEN_NEXT_AT - 1) : null;
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

    // ------------------------------------------------------------------------------------- Kapitel-Generator

    private static LevelDef handmade(int chapter) {
        switch (chapter) {
            case 1: return serpentine();
            case 2: return zickzack();
            case 3: return spirale();
            case 4: return zwillinge();
            case 5: return kreuzung();
            case 6: return labyrinth();
            case 7: return dreiWege();
            case 8: return zangen();
            case 9: return festung();
            default: return chaos();
        }
    }

    /** Level {@code n} (1–15) von Kapitel {@code c} (1–10). */
    private static LevelDef generate(int c, int n) {
        LevelDef h = handmade(c);
        LevelDef l = h;
        if (n > 1) {
            double t = (n - 1) / (double) (PER_CHAPTER - 1);
            double hp = LIMIT[(c - 1) * (PER_CHAPTER - 1) + n - 2] * (0.40 + 0.015 * (c - 1) + 0.40 * t);
            l = base(CHAPTER_IDS[c - 1] + "-" + (n < 10 ? "0" : "") + n, "", c, hp, h.startMoney, h.startLives);
            int variant = n - 1;
            switch (c) {
                case 1: genSerpentine(l, t, variant); break;
                case 2: genZickzack(l, t, variant); break;
                case 3: genSpirale(l, t, variant); break;
                case 4: genZwillinge(l, n); break;
                case 5: genKreuzung(l, t, variant, n); break;
                case 6: genLabyrinth(l, t, variant); break;
                case 7: genDreiWege(l, t, variant); break;
                case 8: genZangen(l, n); break;
                case 9: genFestung(l, t, variant); break;
                default: genChaos(l, variant); break;
            }
            snap(l);
        }
        l.chapter = c;
        l.number = n;
        l.difficulty = c;
        l.name = CHAPTER_NAMES[c - 1] + " " + n;
        return l;
    }

    /**
     * Pro generiertem Level (Kapitel für Kapitel, Nummer 2–15) der größte Lebenspunkte-Faktor, den der Test-Bot
     * gerade noch mit mindestens 10 Leben besteht (erzeugt mit {@code Calibrate}). Der echte Faktor liegt darunter
     * und steigt im Kapitel um 40 Prozentpunkte (Kapitel 1: 40–80 %, Kapitel 10: 54–94 %).
     */
    private static final double[] LIMIT = {
        0.85, 0.81, 1.01, 0.72, 0.94, 0.96, 1.14, 1.05, 1.15, 1.10, 1.02, 1.16, 1.10, 1.17,
        1.12, 1.12, 0.99, 1.36, 1.38, 1.31, 1.20, 1.28, 1.46, 1.41, 1.40, 1.63, 1.47, 1.44,
        1.56, 1.57, 1.45, 1.75, 1.63, 1.61, 1.92, 1.81, 1.93, 1.81, 1.72, 1.79, 1.76, 1.84,
        0.71, 0.68, 0.73, 0.68, 1.00, 1.04, 0.92, 0.95, 0.96, 0.95, 1.00, 1.00, 1.04, 0.92,
        0.85, 0.63, 0.77, 0.89, 0.81, 0.85, 0.88, 0.99, 1.09, 1.07, 1.28, 1.24, 1.12, 1.21,
        0.86, 0.75, 0.90, 0.86, 0.73, 0.94, 1.04, 1.11, 1.12, 1.31, 1.33, 1.02, 1.24, 1.13,
        0.72, 0.83, 0.76, 0.89, 0.86, 0.83, 0.89, 0.88, 0.86, 0.73, 0.76, 0.81, 0.53, 0.78,
        1.23, 0.94, 1.05, 1.06, 1.21, 1.06, 1.03, 1.12, 0.99, 1.10, 1.26, 0.96, 1.05, 0.99,
        0.94, 0.83, 0.89, 0.89, 1.07, 0.89, 0.90, 0.88, 0.88, 0.81, 0.85, 0.73, 0.71, 0.83,
        1.05, 1.17, 1.04, 0.89, 1.03, 1.00, 1.10, 1.15, 0.93, 1.02, 1.11, 0.99, 0.88, 1.05
    };

    /** Rundet alle Punkte auf ganze Zahlen – damit sind die Pfade auf jeder Plattform identisch. */
    private static void snap(LevelDef l) {
        for (double[] p : l.paths) {
            for (int i = 0; i < p.length; i++) {
                p[i] = Math.round(p[i]);
            }
        }
    }

    /** Spiegelt x (und/oder y) am Spielfeld. */
    private static double[] flip(double[] p, boolean fx, boolean fy) {
        double[] r = p.clone();
        for (int i = 0; i < r.length; i += 2) {
            if (fx) {
                r[i] = 1280 - r[i];
            }
            if (fy) {
                r[i + 1] = 720 - r[i + 1];
            }
        }
        return r;
    }

    /** Rechter Wendepunkt der Gänge (1070–1190), je Variante anders. */
    private static double turnRight(int v) {
        return 1190 - ((v * 53) % 7) * 20;
    }

    /** Linker Wendepunkt der Gänge (90–210), je Variante anders. */
    private static double turnLeft(int v) {
        return 90 + ((v * 29) % 7) * 20;
    }

    private static void genSerpentine(LevelDef l, double t, int v) {
        double periods = 2.5 + 2.5 * t;
        double amp = 290 - 80 * t;
        int samples = (int) Math.ceil(periods * 16);
        l.paths.add(wave(30, 1250, 360, amp, periods, (v % 2) * Math.PI, samples));
    }

    private static void genZickzack(LevelDef l, double t, int v) {
        int n = 4 + (int) Math.round(t * 2);
        double sp = 580.0 / (n - 1);
        double[] c = new double[(n * 2 + 2) * 2];
        int k = 0;
        c[k++] = 30;
        c[k++] = 70;
        for (int i = 0; i < n; i++) {
            double y = 70 + i * sp;
            c[k++] = i % 2 == 0 ? turnRight(v + i) : turnLeft(v + i);
            c[k++] = y;
            if (i < n - 1) {
                c[k++] = i % 2 == 0 ? turnRight(v + i) : turnLeft(v + i);
                c[k++] = y + sp;
            }
        }
        c[k++] = (n - 1) % 2 == 0 ? 1250 : 30;
        c[k++] = 70 + (n - 1) * sp;
        double[] pts = rounded(Math.min(70, sp / 2 - 2), java.util.Arrays.copyOf(c, k));
        l.paths.add(flip(pts, v % 2 == 1, v % 4 >= 2));
    }

    private static void genSpirale(LevelDef l, double t, int v) {
        double s = 150 - 45 * t;
        double left = 40;
        double top = 60;
        double right = 1240;
        double bottom = 660;
        List<Double> q = new ArrayList<>();
        double[] first = {left, top, right, top, right, bottom, left, bottom};
        for (double d : first) {
            q.add(d);
        }
        int k = 1;
        double lastX = left;
        while (bottom - k * s - (top + k * s) >= 90) {
            double y1 = top + k * s;
            double y2 = bottom - k * s;
            double[] ring = {left + (k - 1) * s, y1, right - k * s, y1, right - k * s, y2, left + k * s, y2};
            for (double d : ring) {
                q.add(d);
            }
            lastX = left + k * s;
            k++;
        }
        q.add(lastX);
        q.add(360.0);
        q.add(640.0);
        q.add(360.0);
        double[] c = new double[q.size()];
        for (int i = 0; i < c.length; i++) {
            c[i] = q.get(i);
        }
        double[] pts = rounded(Math.min(70, s / 2), c);
        l.paths.add(flip(pts, v % 2 == 1, v % 4 >= 2));
    }

    /** Eine Schlangenbahn aus {@code n} Gängen ab Höhe {@code y0}, Abstand {@code sp}, Richtung nach unten oder oben. */
    private static double[] lanes(int n, double y0, double sp, double dir, int v) {
        double[] c = new double[(n * 2 + 1) * 2];
        int k = 0;
        c[k++] = 30;
        c[k++] = y0;
        for (int i = 0; i < n; i++) {
            double y = y0 + dir * i * sp;
            c[k++] = i % 2 == 0 ? turnRight(v + i) : turnLeft(v + i);
            c[k++] = y;
            if (i < n - 1) {
                c[k++] = i % 2 == 0 ? turnRight(v + i) : turnLeft(v + i);
                c[k++] = y + dir * sp;
            }
        }
        c[k++] = (n - 1) % 2 == 0 ? 1250 : 30;
        c[k++] = y0 + dir * (n - 1) * sp;
        return rounded(Math.min(55, sp / 2 - 2), java.util.Arrays.copyOf(c, k));
    }

    private static void genZwillinge(LevelDef l, int number) {
        int n = number <= 5 ? 2 : 3;
        double sp = Math.min(110, 240.0 / (n - 1));
        l.paths.add(lanes(n, 60, sp, 1, number));
        l.paths.add(lanes(n, 660, sp, -1, number + 3));
    }

    private static void genKreuzung(LevelDef l, double t, int v, int number) {
        double periods = 1.5 + 2.5 * t;
        double amp = 290 - 80 * t;
        int samples = (int) Math.ceil(periods * 12);
        l.paths.add(wave(30, 1250, 360, amp, periods, 0, samples));
        l.paths.add(wave(30, 1250, 360, amp, periods, Math.PI, samples));
        if (number >= 11) {
            l.paths.add(wave(30, 1250, 360, amp * 0.55, periods * 0.5 + 0.5, Math.PI / 2, samples));
        }
    }

    private static void genLabyrinth(LevelDef l, double t, int v) {
        int n = 5 + (int) Math.round(t * 4);
        double sp = 1050.0 / (n - 1);
        double[] c = new double[(n * 2 + 2) * 2];
        int k = 0;
        c[k++] = 30;
        c[k++] = 60;
        for (int i = 0; i < n; i++) {
            double x = 110 + i * sp;
            double top = 60 + ((v * 31 + i * 17) % 5) * 12;
            double bot = 660 - ((v * 17 + i * 29) % 5) * 12;
            c[k++] = x;
            c[k++] = i % 2 == 0 ? top : bot;
            c[k++] = x;
            c[k++] = i % 2 == 0 ? bot : top;
        }
        // Der letzte Gang endet unten oder oben; der Ausgang liegt auf derselben Höhe wie sein Ende.
        double lastY = c[k - 1];
        c[k++] = 1250;
        c[k++] = lastY;
        double[] pts = rounded(Math.min(60, sp / 2 - 2), java.util.Arrays.copyOf(c, k));
        l.paths.add(flip(pts, false, v % 2 == 1));
    }

    private static void genDreiWege(LevelDef l, double t, int v) {
        double gap = 110 - 40 * t;
        double tx = 1190 - 90 * t;
        double shift = ((v * 37) % 61) - 30; // ±30, je Level anders
        double[] mid = {70, 300, 530};
        for (int i = 0; i < 3; i++) {
            double y = mid[i] + shift * (i == 1 ? -1 : 1) * 0.6;
            l.paths.add(rounded(Math.min(55, gap / 2), 30, y, tx, y, tx, y + gap, 30, y + gap));
        }
    }

    private static void genZangen(LevelDef l, int number) {
        int n = number >= 11 ? 4 : 3;
        double sp = 240.0 / (n - 1);
        double[] c = new double[(n * 2 + 2) * 2 + 4];
        int k = 0;
        c[k++] = 30;
        c[k++] = 60;
        for (int i = 0; i < n; i++) {
            double y = 60 + i * sp;
            boolean right = i % 2 == 0;
            double x = i == n - 1 ? (right ? 900 - (number % 4) * 30 : 380 + (number % 4) * 30)
                    : (right ? turnRight(number + i) : turnLeft(number + i));
            c[k++] = x;
            c[k++] = y;
            if (i < n - 1) {
                c[k++] = x;
                c[k++] = y + sp;
            } else {
                c[k++] = x;
                c[k++] = 360;
            }
        }
        c[k++] = 640;
        c[k++] = 360;
        double[] a = rounded(Math.min(60, sp / 2 - 2), java.util.Arrays.copyOf(c, k));
        l.paths.add(a);
        l.paths.add(flip(a, false, true));
    }

    private static void genFestung(LevelDef l, double t, int v) {
        double s1 = 100 - 8 * t;
        double[] top = rounded(55, 30, 60, 1050, 60, 1050, 60 + s1, 150, 60 + s1, 150, 250, 1150, 250, 1150, 360);
        l.paths.add(top);
        double amp = 40 + 25 * t;
        l.paths.add(wave(30, 1150, 360, amp, 5 + 3 * t, (v % 2) * Math.PI, 28));
        l.paths.add(flip(top, false, true));
    }

    /** Kleiner deterministischer Zufallsgenerator für die Chaos-Level (nur ganzzahlig, überall identisch). */
    private static final class Lcg {
        private int state;

        Lcg(int seed) {
            state = seed * 7919 + 12345;
        }

        double range(double lo, double hi) {
            state = state * 1664525 + 1013904223;
            return lo + ((state >>> 8) & 0xFFFF) / 65535.0 * (hi - lo);
        }
    }

    private static void genChaos(LevelDef l, int v) {
        Lcg r = new Lcg(v);
        int legs = 5 + (v >= 8 ? 1 : 0);
        double[] a = new double[legs * 4 + 4];
        double[] b = new double[legs * 4 + 4];
        for (int i = 0; i <= legs; i++) {
            double x = 30 + i * 1220.0 / legs;
            boolean high = i % 2 == 0;
            double ya = high ? r.range(60, 260) : r.range(440, 660);
            double yb = high ? r.range(440, 660) : r.range(60, 260);
            double xj = i == 0 || i == legs ? 0 : r.range(-70, 70);
            a[i * 2] = x + xj;
            a[i * 2 + 1] = ya;
            b[i * 2] = 1250 - (x + xj);
            b[i * 2 + 1] = yb;
        }
        l.paths.add(rounded(60, java.util.Arrays.copyOf(a, (legs + 1) * 2)));
        l.paths.add(rounded(60, java.util.Arrays.copyOf(b, (legs + 1) * 2)));
        if (v >= 4) {
            double x0 = r.range(500, 800);
            l.paths.add(rounded(60, x0, 30, x0 + r.range(-300, 300), 240, 1250 - x0, 420, 640, 690));
        }
    }
}
