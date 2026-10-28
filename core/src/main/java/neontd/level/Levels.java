package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.level.Zone.Kind;
import neontd.sim.WaveDef;

/**
 * Die eingebauten Level: 8 Welten mit je 6 von Hand gebauten Strecken (48 insgesamt). Jede Strecke ist anders
 * gebaut – Schleifen, Kreuzungen, Zusammenflüsse, Festungen, Spiralen – und hat Hindernisse und Bonus-Pads, die
 * die Platzierung zur Entscheidung machen. Jedes Level hat 20 Wellen im Stil seiner Welt (3 Sterne) und läuft
 * danach im Endlosmodus weiter (Medaillen ab Welle 50/100/250).
 */
public final class Levels {
    public static final int CHAPTERS = Worlds.COUNT;
    public static final int PER_CHAPTER = Worlds.LEVELS;
    /** Pseudo-Kapitel für die Übersicht der eigenen Level. */
    public static final int CUSTOM = CHAPTERS + 1;
    /** Kennung des allerersten Levels. */
    public static final String FIRST = "stadt-1";
    private static final int CAMPAIGN_WAVES = 20;
    private static final double W = LevelDef.DEFAULT_WIDTH;
    private static final double H = LevelDef.DEFAULT_HEIGHT;

    private static List<LevelDef> cache;

    private Levels() {
    }

    /** Alle 48 eingebauten Level in Spielreihenfolge (Welt für Welt). Die Liste wird einmal erzeugt und geteilt. */
    public static synchronized List<LevelDef> builtins() {
        if (cache == null) {
            List<LevelDef> list = new ArrayList<>();
            for (int w = 1; w <= CHAPTERS; w++) {
                for (int n = 1; n <= PER_CHAPTER; n++) {
                    list.add(build(w, n));
                }
            }
            cache = java.util.Collections.unmodifiableList(list);
        }
        return cache;
    }

    public static String chapterName(int chapter) {
        return chapter == CUSTOM ? "Eigene Level" : Worlds.NAMES[chapter - 1];
    }

    /** Die sechs Level einer Welt. */
    public static List<LevelDef> chapter(int chapter) {
        return builtins().subList((chapter - 1) * PER_CHAPTER, chapter * PER_CHAPTER);
    }

    public static LevelDef find(String id) {
        int i = indexOf(id);
        return i < 0 ? null : builtins().get(i);
    }

    /**
     * Die klassische Serpentine aus der ersten Fassung – nicht Teil der Kampagne, aber als feste Referenz für Tests,
     * Werkzeuge und die Demo erhalten (viele Bauplätze, ein einziger ruhiger Pfad).
     */
    public static LevelDef serpentine() {
        LevelDef l = new LevelDef("serpentine", "Serpentine");
        l.builtin = true;
        l.startMoney = 500;
        l.startLives = 20;
        l.waveCount = CAMPAIGN_WAVES;
        l.difficulty = 1;
        l.hpMul = 1.0;
        l.style = WaveStyle.KLASSIK;
        l.paths.add(new double[] {
            30, 120, 250, 120, 430, 170, 620, 120, 840, 100, 1010, 140, 1120, 250, 1050, 370, 860, 410, 650, 375,
            450, 320, 270, 360, 170, 480, 250, 590, 450, 625, 680, 575, 900, 560, 1070, 610, 1250, 590
        });
        return l;
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
     * Welches Level muss einen Stern haben, damit dieses spielbar ist? Innerhalb einer Welt das vorherige; beim
     * ersten Level einer Welt entscheidet die Sternen-Hürde der Welt ({@link Worlds#REQUIRED_STARS}), daher
     * {@code null}.
     */
    public static LevelDef prerequisite(LevelDef l) {
        if (!l.builtin || l.chapter < 1 || l.number <= 1) {
            return null;
        }
        return chapter(l.chapter).get(l.number - 2);
    }

    /** Die 20 Wellen eines eingebauten Levels im Stil des Levels. */
    public static List<WaveDef> campaignWaves(LevelDef level) {
        if (level.style == null) {
            level.style = WaveStyle.KLASSIK;
        }
        return CampaignWaves.build(level, CAMPAIGN_WAVES);
    }

    // ------------------------------------------------------------------------------------ Geometrie-Hilfen

    /**
     * Polylinie mit abgerundeten Ecken: Jede innere Ecke wird durch zwei Punkte im Abstand {@code r} (oder der halben
     * Segmentlänge) ersetzt, damit der Spline weiche Kurven statt Überschwinger an scharfen Knicken bildet.
     */
    static double[] rounded(double r, double... c) {
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
        // Lange Geraden bekommen Zwischenpunkte, damit der Spline sie nicht durchbiegt.
        List<Double> dense = new ArrayList<>();
        for (int i = 0; i < out.size(); i += 2) {
            if (i > 0) {
                double px = out.get(i - 2);
                double py = out.get(i - 1);
                double x = out.get(i);
                double y = out.get(i + 1);
                double d = Math.hypot(x - px, y - py);
                int parts = (int) Math.floor(d / 220);
                for (int k = 1; k <= parts; k++) {
                    dense.add(px + (x - px) * k / (parts + 1));
                    dense.add(py + (y - py) * k / (parts + 1));
                }
            }
            dense.add(out.get(i));
            dense.add(out.get(i + 1));
        }
        return toArray(dense);
    }

    private static double[] toArray(List<Double> l) {
        double[] a = new double[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    /** Wellenlinie von (x0, cy) nach (x1, cy): {@code periods} Schwingungen mit Amplitude {@code amp}. */
    static double[] wave(double x0, double x1, double cy, double amp, double periods, double phase, int samples) {
        double[] a = new double[(samples + 1) * 2];
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            a[i * 2] = x0 + (x1 - x0) * t;
            a[i * 2 + 1] = cy + amp * Math.sin(Math.PI * 2 * periods * t + phase);
        }
        return a;
    }

    /** Senkrechte Wellenlinie von (cx, y0) nach (cx, y1). */
    static double[] vwave(double cx, double y0, double y1, double amp, double periods, double phase, int samples) {
        double[] a = new double[(samples + 1) * 2];
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            a[i * 2] = cx + amp * Math.sin(Math.PI * 2 * periods * t + phase);
            a[i * 2 + 1] = y0 + (y1 - y0) * t;
        }
        return a;
    }

    /** Kreisbogen um (cx, cy) von Winkel a0 nach a1 (Grad, im Uhrzeigersinn bei steigendem Winkel), ohne Endpunkt-Duplikate. */
    static double[] arc(double cx, double cy, double r, double a0, double a1) {
        int n = Math.max(4, (int) Math.ceil(Math.abs(a1 - a0) / 15.0));
        double[] a = new double[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double ang = Math.toRadians(a0 + (a1 - a0) * i / n);
            a[i * 2] = cx + Math.cos(ang) * r;
            a[i * 2 + 1] = cy + Math.sin(ang) * r;
        }
        return a;
    }

    /** Hängt mehrere Punktfolgen aneinander. */
    static double[] join(double[]... parts) {
        int n = 0;
        for (double[] p : parts) {
            n += p.length;
        }
        double[] a = new double[n];
        int k = 0;
        for (double[] p : parts) {
            System.arraycopy(p, 0, a, k, p.length);
            k += p.length;
        }
        return a;
    }

    static double[] pts(double... c) {
        return c;
    }

    /** Spiegelt x (und/oder y) am Spielfeld. */
    static double[] flip(double[] p, boolean fx, boolean fy) {
        double[] r = p.clone();
        for (int i = 0; i < r.length; i += 2) {
            if (fx) {
                r[i] = W - r[i];
            }
            if (fy) {
                r[i + 1] = H - r[i + 1];
            }
        }
        return r;
    }

    /** Kehrt die Richtung einer Punktfolge um. */
    static double[] reverse(double[] p) {
        int n = p.length / 2;
        double[] r = new double[p.length];
        for (int i = 0; i < n; i++) {
            r[i * 2] = p[(n - 1 - i) * 2];
            r[i * 2 + 1] = p[(n - 1 - i) * 2 + 1];
        }
        return r;
    }

    /**
     * Eine Schleife: Der Pfad kommt von links unten an, läuft dreiviertel um (cx, cy) herum (unten → links → oben →
     * rechts) und verlässt den Kreis an der rechten Seite nach oben. Türme im Inneren sehen fast den ganzen Ring.
     */
    static double[] loop(double cx, double cy, double r) {
        return arc(cx, cy, r, 90, -270);
    }

    /** Spirale um (cx, cy): der Radius läuft von r0 nach r1, während der Winkel von a0 nach a1 (Grad) läuft. */
    static double[] spiral(double cx, double cy, double r0, double r1, double a0, double a1) {
        int n = Math.max(6, (int) Math.ceil(Math.abs(a1 - a0) / 12.0));
        double[] a = new double[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            double ang = Math.toRadians(a0 + (a1 - a0) * t);
            double r = r0 + (r1 - r0) * t;
            a[i * 2] = cx + Math.cos(ang) * r;
            a[i * 2 + 1] = cy + Math.sin(ang) * r;
        }
        return a;
    }

    // --------------------------------------------------------------------------------------- Bauen

    private static LevelDef base(int world, int n, String name, String blurb, int money) {
        LevelDef l = new LevelDef(Worlds.IDS[world - 1] + "-" + n, name);
        l.builtin = true;
        l.chapter = world;
        l.number = n;
        l.difficulty = world;
        l.waveCount = CAMPAIGN_WAVES;
        l.startMoney = money;
        l.startLives = Worlds.LIVES[world - 1];
        l.blurb = blurb;
        WaveStyle[] st = Worlds.STYLES[world - 1];
        switch (n) {
            case 1:
                l.style = st[0];
                break;
            case 2:
                l.style = st[0];
                l.altStyle = st[1];
                break;
            case 3:
                l.style = st[1];
                break;
            case 4:
                l.style = st[2];
                break;
            case 5:
                l.style = st[1];
                l.altStyle = st[2];
                break;
            default:
                l.style = WaveStyle.TITANEN;
                l.bossLevel = true;
                break;
        }
        return l;
    }

    private static void pad(LevelDef l, Kind k, double x, double y) {
        l.zones.add(Zone.pad(k, x, y));
    }

    private static void rock(LevelDef l, double x, double y, double r) {
        l.zones.add(Zone.rock(x, y, r));
    }

    private static void block(LevelDef l, double x, double y, double w, double h) {
        l.zones.add(Zone.block(x, y, w, h));
    }

    /** Rundet alle Punkte auf ganze Zahlen – damit sind die Pfade auf jeder Plattform identisch. */
    private static void snap(LevelDef l) {
        for (double[] p : l.paths) {
            for (int i = 0; i < p.length; i++) {
                p[i] = Math.round(p[i]);
            }
        }
    }

    /**
     * Lebenspunkte-Faktor je Level: der mit dem Test-Bot gemessene Grenzwert ({@link #LIMIT}, höchster Faktor, den
     * er noch mit halben Leben besteht) mal einem Anteil, der innerhalb der Welt und über die Welten wächst.
     */
    private static double hpMul(int world, int n) {
        double limit = LIMIT[(world - 1) * PER_CHAPTER + n - 1];
        double share = 0.42 + 0.02 * (world - 1) + 0.07 * (n - 1);
        return Math.round(limit * share * 100) / 100.0;
    }

    /**
     * Grenzwerte des Test-Bots je Level (erzeugt mit {@code Calibrate}; Reihenfolge: Welt für Welt, Level 1–6).
     * Ein Wert von 1,0 bedeutet: Mit der Standardkurve kommt der Bot gerade so mit halben Leben durch.
     */
    static final double[] LIMIT = {
        0.45, 1.05, 1.64, 0.68, 1.43, 1.17,
        0.96, 0.89, 1.99, 1.06, 0.55, 1.31,
        0.45, 0.51, 0.84, 0.96, 0.48, 1.38,
        0.78, 0.91, 0.63, 1.01, 1.26, 0.66,
        1.34, 1.56, 0.61, 0.60, 0.58, 0.52,
        0.94, 0.44, 0.76, 0.63, 0.78, 0.70,
        0.93, 1.28, 1.13, 0.83, 0.65, 1.26,
        0.45, 0.93, 1.31, 0.82, 0.81, 0.78,
    };

    private static LevelDef build(int world, int n) {
        LevelDef l;
        switch (world) {
            case 1: l = stadt(n); break;
            case 2: l = tiefsee(n); break;
            case 3: l = magma(n); break;
            case 4: l = garten(n); break;
            case 5: l = hafen(n); break;
            case 6: l = kathedrale(n); break;
            case 7: l = strom(n); break;
            default: l = horizont(n); break;
        }
        l.hpMul = hpMul(world, n);
        snap(l);
        return l;
    }

    // ------------------------------------------------------------------------------------ Welt 1: Neonstadt

    private static LevelDef stadt(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(1, 1, "Erste Runde", "Ein ruhiger Bogen durch die Stadt – lerne die Türme kennen", 500);
                l.paths.add(pts(30, 150, 240, 120, 470, 190, 640, 350, 820, 500, 1010, 580, 1250, 560));
                pad(l, Kind.PAD_DAMAGE, 480, 320);
                return l;
            }
            case 2: {
                LevelDef l = base(1, 2, "Haarnadel", "Die Bahn kommt zurück – Türme in der Mitte treffen zweimal", 520);
                l.paths.add(rounded(120, 30, 190, 1090, 190, 1090, 530, 30, 530));
                block(l, 560, 360, 170, 90);
                pad(l, Kind.PAD_RANGE, 1180, 360);
                return l;
            }
            case 3: {
                LevelDef l = base(1, 3, "Schleife", "Eine Schleife mitten im Feld – wer innen steht, sieht alles", 540);
                l.paths.add(join(pts(30, 560, 380, 640), loop(640, 360, 230), pts(900, 640, 1250, 560)));
                rock(l, 640, 360, 40);
                pad(l, Kind.PAD_SPEED, 560, 300);
                return l;
            }
            case 4: {
                LevelDef l = base(1, 4, "Zwei Tore", "Zwei Zufahrten münden in eine Straße", 560);
                l.paths.add(rounded(100, 30, 120, 430, 120, 640, 330, 900, 360, 1250, 360));
                l.paths.add(rounded(100, 30, 600, 430, 600, 640, 390, 900, 360, 1250, 360));
                pad(l, Kind.PAD_SPEED, 760, 200);
                rock(l, 1040, 520, 50);
                return l;
            }
            case 5: {
                LevelDef l = base(1, 5, "Kreuzung", "Die Bahn kreuzt sich selbst – der Knoten ist der beste Platz", 580);
                l.paths.add(rounded(90, 30, 190, 420, 190, 760, 540, 1030, 540, 1170, 360, 1030, 190, 760, 190,
                        420, 540, 30, 540));
                rock(l, 200, 360, 45);
                pad(l, Kind.PAD_DAMAGE, 1000, 360);
                return l;
            }
            default: {
                LevelDef l = base(1, 6, "Boulevard", "Der lange Weg zum Rathaus – alle fünf Wellen ein Titan", 700);
                l.paths.add(rounded(100, 30, 100, 1010, 100, 1010, 300, 270, 300, 270, 500, 1010, 500, 1010, 650,
                        30, 650));
                block(l, 640, 200, 240, 70);
                block(l, 640, 400, 240, 70);
                pad(l, Kind.PAD_DAMAGE, 1150, 200);
                pad(l, Kind.PAD_SPEED, 1150, 400);
                pad(l, Kind.PAD_RANGE, 130, 400);
                return l;
            }
        }
    }

    // ------------------------------------------------------------------------------------ Welt 2: Tiefsee

    private static LevelDef tiefsee(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(2, 1, "Strömung", "Zwei lange Wellen – Riffe versperren die besten Plätze", 540);
                l.paths.add(wave(30, 1250, 360, 230, 2, 0, 40));
                rock(l, 335, 230, 55);
                rock(l, 945, 490, 55);
                rock(l, 640, 140, 45);
                pad(l, Kind.PAD_RANGE, 640, 580);
                return l;
            }
            case 2: {
                LevelDef l = base(2, 2, "Doppelhelix", "Zwei Bahnen verflechten sich dreimal", 820);
                l.paths.add(wave(30, 1250, 360, 210, 1.5, 0, 36));
                l.paths.add(wave(30, 1250, 360, 210, 1.5, Math.PI, 36));
                pad(l, Kind.PAD_DAMAGE, 540, 360);
                pad(l, Kind.PAD_DAMAGE, 740, 360);
                return l;
            }
            case 3: {
                LevelDef l = base(2, 3, "Grotte", "Der Pfad windet sich zur Höhle in der Mitte", 560);
                l.paths.add(rounded(90, 30, 60, 1220, 60, 1220, 660, 60, 660, 60, 220, 1060, 220, 1060, 500,
                        220, 500, 220, 360, 640, 360));
                pad(l, Kind.PAD_RANGE, 640, 580);
                rock(l, 400, 140, 40);
                return l;
            }
            case 4: {
                LevelDef l = base(2, 4, "Riff", "Enge Kanäle zwischen Felsen", 500);
                l.paths.add(rounded(85, 30, 360, 300, 360, 300, 120, 620, 120, 620, 600, 940, 600, 940, 200,
                        1250, 200));
                rock(l, 460, 300, 60);
                rock(l, 780, 420, 60);
                rock(l, 1100, 420, 50);
                rock(l, 150, 200, 45);
                rock(l, 150, 520, 45);
                return l;
            }
            case 5: {
                LevelDef l = base(2, 5, "Gezeiten", "Von beiden Seiten zur Mitte – zwei Haken, ein Ziel", 900);
                l.paths.add(rounded(90, 30, 160, 360, 160, 360, 560, 520, 560, 520, 380, 640, 380));
                l.paths.add(rounded(90, 1250, 560, 920, 560, 920, 160, 760, 160, 760, 340, 640, 380));
                pad(l, Kind.PAD_SPEED, 640, 200);
                pad(l, Kind.PAD_SPEED, 640, 580);
                return l;
            }
            default: {
                LevelDef l = base(2, 6, "Abgrund", "Eine Spirale in die Tiefe – Titanen alle fünf Wellen", 700);
                l.paths.add(rounded(90, 40, 60, 1240, 60, 1240, 660, 40, 660, 40, 210, 1090, 210, 1090, 510,
                        190, 510, 190, 360, 640, 360));
                pad(l, Kind.PAD_DAMAGE, 640, 435);
                rock(l, 400, 135, 36);
                rock(l, 880, 585, 36);
                return l;
            }
        }
    }

    // ----------------------------------------------------------------------------------- Welt 3: Magmakern

    private static LevelDef magma(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(3, 1, "Lavastrom", "Ein breiter Strom in Schlangenlinien", 560);
                l.paths.add(rounded(120, 30, 200, 400, 200, 400, 520, 760, 520, 760, 200, 1120, 200, 1120, 520,
                        1250, 520));
                pad(l, Kind.PAD_DAMAGE, 580, 360);
                pad(l, Kind.PAD_SPEED, 940, 360);
                block(l, 220, 420, 120, 120);
                return l;
            }
            case 2: {
                LevelDef l = base(3, 2, "Zange", "Aus zwei Ecken auf die Schmiede zu", 900);
                l.paths.add(rounded(90, 30, 80, 900, 80, 900, 300, 640, 360));
                l.paths.add(rounded(90, 1250, 640, 380, 640, 380, 420, 640, 360));
                rock(l, 300, 300, 55);
                rock(l, 980, 420, 55);
                return l;
            }
            case 3: {
                LevelDef l = base(3, 3, "Kessel", "Ein Ring um den Kessel – innen stehen die besten Türme", 600);
                l.paths.add(join(pts(30, 670, 340, 670), spiral(640, 360, 300, 110, 90, -360), pts(640, 360)));
                rock(l, 480, 250, 40);
                rock(l, 800, 470, 40);
                pad(l, Kind.PAD_DAMAGE, 560, 360);
                return l;
            }
            case 4: {
                LevelDef l = base(3, 4, "Schmiede", "Enge Gänge – zwischen den Bahnen passt genau ein Turm", 520);
                l.paths.add(rounded(60, 30, 60, 1100, 60, 1100, 210, 180, 210, 180, 360, 1100, 360, 1100, 510,
                        180, 510, 180, 660, 1250, 660));
                pad(l, Kind.PAD_RANGE, 640, 135);
                pad(l, Kind.PAD_RANGE, 640, 585);
                block(l, 1190, 285, 100, 70);
                return l;
            }
            case 5: {
                LevelDef l = base(3, 5, "Dreizack", "Drei Ströme fließen zusammen", 1200);
                l.paths.add(rounded(100, 30, 100, 800, 100, 1000, 340, 1250, 360));
                l.paths.add(rounded(100, 30, 360, 1000, 360, 1250, 360));
                l.paths.add(rounded(100, 30, 620, 800, 620, 1000, 380, 1250, 360));
                rock(l, 420, 230, 50);
                rock(l, 420, 490, 50);
                pad(l, Kind.PAD_DAMAGE, 1150, 220);
                pad(l, Kind.PAD_DAMAGE, 1150, 500);
                return l;
            }
            default: {
                LevelDef l = base(3, 6, "Vulkan", "Spirale in den Krater – Titanen alle fünf Wellen", 760);
                l.paths.add(rounded(90, 1250, 60, 40, 60, 40, 660, 1240, 660, 1240, 210, 190, 210, 190, 510,
                        1090, 510, 1090, 360, 640, 360));
                rock(l, 640, 470, 60);
                pad(l, Kind.PAD_DAMAGE, 115, 135);
                pad(l, Kind.PAD_DAMAGE, 1165, 585);
                return l;
            }
        }
    }

    // --------------------------------------------------------------------------------- Welt 4: Gewächshaus

    private static LevelDef garten(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(4, 1, "Ranke", "Eine geschwungene Ranke quer durchs Haus", 560);
                l.paths.add(pts(30, 560, 200, 420, 380, 200, 560, 140, 700, 260, 760, 460, 900, 600, 1080, 520,
                        1180, 320, 1250, 200));
                pad(l, Kind.PAD_RANGE, 480, 420);
                rock(l, 560, 580, 45);
                rock(l, 1000, 260, 45);
                return l;
            }
            case 2: {
                LevelDef l = base(4, 2, "Beete", "Sechs Beete, lange Gänge", 600);
                l.paths.add(rounded(70, 30, 60, 110, 60, 110, 660, 330, 660, 330, 60, 550, 60, 550, 660, 770,
                        660, 770, 60, 990, 60, 990, 660, 1210, 660, 1210, 60, 1250, 60));
                block(l, 440, 360, 70, 220);
                block(l, 880, 360, 70, 220);
                pad(l, Kind.PAD_SPEED, 220, 360);
                pad(l, Kind.PAD_SPEED, 1100, 360);
                return l;
            }
            case 3: {
                LevelDef l = base(4, 3, "Kreuzgang", "Zwei Wege kreuzen sich viermal", 880);
                l.paths.add(wave(30, 1250, 360, 240, 2, 0, 40));
                l.paths.add(wave(30, 1250, 360, 240, 2, Math.PI, 40));
                pad(l, Kind.PAD_SPEED, 335, 200);
                pad(l, Kind.PAD_SPEED, 945, 520);
                rock(l, 640, 200, 45);
                rock(l, 640, 520, 45);
                return l;
            }
            case 4: {
                LevelDef l = base(4, 4, "Spalier", "Lange Geraden – aber die Beete stehen im Weg", 560);
                l.paths.add(rounded(80, 30, 140, 1150, 140, 1150, 360, 130, 360, 130, 580, 1250, 580));
                block(l, 400, 250, 180, 60);
                block(l, 880, 250, 180, 60);
                block(l, 400, 470, 180, 60);
                block(l, 880, 470, 180, 60);
                pad(l, Kind.PAD_DAMAGE, 640, 250);
                pad(l, Kind.PAD_SPEED, 640, 470);
                return l;
            }
            case 5: {
                LevelDef l = base(4, 5, "Knospe", "Zwei Schleifen hintereinander", 620);
                l.paths.add(join(pts(30, 560, 170, 590), loop(360, 360, 170), pts(640, 600), loop(920, 360, 170),
                        pts(1110, 590, 1250, 560)));
                rock(l, 360, 360, 36);
                rock(l, 920, 360, 36);
                pad(l, Kind.PAD_SPEED, 640, 120);
                return l;
            }
            default: {
                LevelDef l = base(4, 6, "Dschungel", "Drei Wege durchs Dickicht – Titanen alle fünf Wellen", 1300);
                l.paths.add(rounded(90, 30, 80, 600, 80, 600, 300, 1250, 300));
                l.paths.add(wave(30, 1250, 360, 50, 3, 0, 40));
                l.paths.add(rounded(90, 30, 640, 600, 640, 600, 420, 1250, 420));
                rock(l, 300, 200, 40);
                rock(l, 300, 520, 40);
                rock(l, 980, 200, 40);
                rock(l, 980, 520, 40);
                pad(l, Kind.PAD_RANGE, 760, 200);
                pad(l, Kind.PAD_RANGE, 760, 520);
                return l;
            }
        }
    }

    // ------------------------------------------------------------------------------------ Welt 5: Raumhafen

    private static LevelDef hafen(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(5, 1, "Docking", "Lange Pisten, enge Wenden", 580);
                l.paths.add(rounded(70, 30, 100, 1100, 100, 1100, 260, 180, 260, 180, 420, 1100, 420, 1100, 580,
                        30, 580));
                pad(l, Kind.PAD_SPEED, 1200, 180);
                pad(l, Kind.PAD_DAMAGE, 80, 340);
                pad(l, Kind.PAD_SPEED, 1200, 500);
                block(l, 640, 180, 200, 50);
                block(l, 640, 500, 200, 50);
                return l;
            }
            case 2: {
                LevelDef l = base(5, 2, "Orbit", "Zwei Umlaufbahnen bis zum Kern", 640);
                l.paths.add(join(pts(30, 660, 340, 680), loop(640, 360, 290), pts(880, 640, 760, 580),
                        arc(640, 360, 150, 90, 360), pts(640, 360)));
                rock(l, 920, 130, 40);
                rock(l, 360, 590, 40);
                pad(l, Kind.PAD_RANGE, 1130, 600);
                pad(l, Kind.PAD_DAMAGE, 200, 300);
                return l;
            }
            case 3: {
                LevelDef l = base(5, 3, "Schleuse", "Zwei Schleusen, ein Kern", 920);
                l.paths.add(rounded(90, 30, 120, 300, 120, 300, 560, 520, 560, 520, 360, 640, 360));
                l.paths.add(rounded(90, 1250, 120, 980, 120, 980, 560, 760, 560, 760, 360, 640, 360));
                pad(l, Kind.PAD_RANGE, 640, 180);
                rock(l, 640, 540, 45);
                return l;
            }
            case 4: {
                LevelDef l = base(5, 4, "Asteroiden", "Brocken überall – finde die Lücken", 540);
                l.paths.add(rounded(110, 30, 560, 360, 560, 360, 160, 700, 160, 700, 560, 1040, 560, 1040, 160,
                        1250, 160));
                rock(l, 190, 300, 52);
                rock(l, 530, 360, 56);
                rock(l, 870, 360, 56);
                rock(l, 1150, 400, 46);
                rock(l, 530, 640, 40);
                rock(l, 870, 80, 40);
                return l;
            }
            case 5: {
                LevelDef l = base(5, 5, "Warp", "Zwei Bahnen von oben und rechts – ein Knoten unten", 900);
                l.paths.add(rounded(90, 240, 30, 240, 250, 1040, 250, 1040, 690));
                l.paths.add(rounded(90, 1250, 470, 460, 470, 460, 690));
                pad(l, Kind.PAD_DAMAGE, 1150, 360);
                pad(l, Kind.PAD_SPEED, 640, 360);
                rock(l, 120, 400, 50);
                return l;
            }
            default: {
                LevelDef l = base(5, 6, "Mutterschiff", "Drei Landebahnen zum Kern – Titanen alle fünf Wellen", 1300);
                l.paths.add(rounded(90, 640, 30, 640, 120, 200, 120, 200, 260, 520, 300, 640, 360));
                l.paths.add(rounded(90, 30, 600, 300, 600, 300, 420, 640, 360));
                l.paths.add(rounded(90, 1250, 600, 980, 600, 980, 420, 640, 360));
                rock(l, 640, 520, 40);
                pad(l, Kind.PAD_DAMAGE, 1000, 200);
                pad(l, Kind.PAD_RANGE, 100, 400);
                return l;
            }
        }
    }

    // ---------------------------------------------------------------------------------- Welt 6: Kathedrale

    private static LevelDef kathedrale(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(6, 1, "Schiff", "Zwei Seitenschiffe, ein Altar", 880);
                l.paths.add(rounded(90, 30, 200, 1100, 200, 1100, 320, 640, 360));
                l.paths.add(rounded(90, 30, 520, 1100, 520, 1100, 400, 640, 360));
                rock(l, 300, 360, 40);
                rock(l, 980, 360, 40);
                pad(l, Kind.PAD_RANGE, 640, 100);
                pad(l, Kind.PAD_RANGE, 640, 620);
                return l;
            }
            case 2: {
                LevelDef l = base(6, 2, "Rosette", "Drei Blütenblätter laufen in der Mitte zusammen", 1200);
                l.paths.add(pts(640, 30, 610, 130, 430, 150, 360, 300, 480, 390, 640, 360));
                l.paths.add(pts(30, 420, 230, 440, 370, 560, 540, 470, 640, 360));
                l.paths.add(pts(1250, 420, 1050, 440, 910, 560, 740, 470, 640, 360));
                rock(l, 640, 560, 40);
                pad(l, Kind.PAD_DAMAGE, 900, 120);
                pad(l, Kind.PAD_DAMAGE, 200, 240);
                return l;
            }
            case 3: {
                LevelDef l = base(6, 3, "Klosterhof", "Einmal um den Hof – die Säulen stehen im Weg", 640);
                l.paths.add(rounded(100, 30, 160, 1100, 160, 1100, 560, 180, 560, 180, 300, 30, 300));
                rock(l, 450, 360, 40);
                rock(l, 830, 360, 40);
                rock(l, 640, 260, 40);
                rock(l, 640, 460, 40);
                pad(l, Kind.PAD_RANGE, 640, 360);
                return l;
            }
            case 4: {
                LevelDef l = base(6, 4, "Chor", "Von oben und unten in den Chorraum", 900);
                l.paths.add(rounded(90, 640, 30, 640, 140, 200, 140, 200, 360, 640, 360));
                l.paths.add(rounded(90, 640, 690, 640, 580, 1080, 580, 1080, 360, 640, 360));
                block(l, 420, 250, 120, 60);
                block(l, 860, 470, 120, 60);
                pad(l, Kind.PAD_DAMAGE, 1000, 200);
                pad(l, Kind.PAD_DAMAGE, 280, 520);
                return l;
            }
            case 5: {
                LevelDef l = base(6, 5, "Glockenturm", "Senkrechte Gänge, steile Wenden", 620);
                l.paths.add(rounded(70, 160, 30, 160, 600, 380, 600, 380, 120, 600, 120, 600, 600, 820, 600,
                        820, 120, 1040, 120, 1040, 600, 1250, 600));
                block(l, 270, 300, 60, 180);
                block(l, 710, 420, 60, 180);
                pad(l, Kind.PAD_SPEED, 490, 360);
                pad(l, Kind.PAD_SPEED, 930, 360);
                pad(l, Kind.PAD_DAMAGE, 1150, 300);
                return l;
            }
            default: {
                LevelDef l = base(6, 6, "Prozession", "Drei Züge kreuzen sich – Titanen alle fünf Wellen", 1400);
                l.paths.add(wave(30, 1250, 220, 110, 2, 0, 36));
                l.paths.add(wave(30, 1250, 360, 110, 2, Math.PI, 36));
                l.paths.add(wave(30, 1250, 500, 110, 2, 0, 36));
                pad(l, Kind.PAD_DAMAGE, 335, 60);
                pad(l, Kind.PAD_DAMAGE, 945, 660);
                rock(l, 640, 60, 40);
                rock(l, 640, 660, 40);
                return l;
            }
        }
    }

    // ---------------------------------------------------------------------------------- Welt 7: Datenstrom

    private static LevelDef strom(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(7, 1, "Bus", "Zwei Leitungen teilen sich die Mitte", 900);
                l.paths.add(rounded(90, 30, 200, 400, 200, 500, 360, 800, 360, 900, 200, 1250, 200));
                l.paths.add(rounded(90, 30, 520, 400, 520, 500, 360, 800, 360, 900, 520, 1250, 520));
                pad(l, Kind.PAD_SPEED, 650, 200);
                pad(l, Kind.PAD_SPEED, 650, 520);
                rock(l, 200, 360, 40);
                rock(l, 1080, 360, 40);
                return l;
            }
            case 2: {
                LevelDef l = base(7, 2, "Doppelspirale", "Zwei Spiralen ineinander", 1000);
                l.paths.add(rounded(90, 30, 60, 1100, 60, 1100, 500, 380, 500, 380, 260, 640, 360));
                l.paths.add(rounded(90, 1250, 660, 180, 660, 180, 220, 900, 220, 900, 460, 640, 360));
                pad(l, Kind.PAD_RANGE, 1180, 360);
                pad(l, Kind.PAD_RANGE, 100, 400);
                return l;
            }
            case 3: {
                LevelDef l = base(7, 3, "Netz", "Eine Leitung quer, eine längs – sechs Kreuzungen", 1000);
                l.paths.add(wave(30, 1250, 360, 170, 1.5, 0, 40));
                l.paths.add(vwave(640, 30, 690, 230, 1.5, 0, 48));
                rock(l, 640, 360, 40);
                pad(l, Kind.PAD_DAMAGE, 200, 600);
                pad(l, Kind.PAD_DAMAGE, 1080, 120);
                return l;
            }
            case 4: {
                LevelDef l = base(7, 4, "Puffer", "Drei Spuren, Pads und Blockaden im Wechsel", 620);
                l.paths.add(rounded(90, 30, 120, 1150, 120, 1150, 340, 130, 340, 130, 580, 1250, 580));
                block(l, 300, 230, 160, 56);
                block(l, 980, 230, 160, 56);
                block(l, 640, 460, 160, 56);
                pad(l, Kind.PAD_DAMAGE, 640, 230);
                pad(l, Kind.PAD_SPEED, 300, 460);
                pad(l, Kind.PAD_SPEED, 980, 460);
                return l;
            }
            case 5: {
                LevelDef l = base(7, 5, "Rekursion", "Viele kleine Buchten – in jeder zweiten liegt ein Fels", 620);
                l.paths.add(wave(30, 1250, 360, 130, 3.5, 0, 70));
                rock(l, 204, 150, 44);
                rock(l, 553, 150, 44);
                rock(l, 901, 150, 44);
                rock(l, 378, 570, 44);
                rock(l, 727, 570, 44);
                rock(l, 1076, 570, 44);
                pad(l, Kind.PAD_RANGE, 640, 620);
                return l;
            }
            default: {
                LevelDef l = base(7, 6, "Kernschmelze", "Drei Leitungen, ein Chaos – Titanen alle fünf Wellen", 1500);
                l.paths.add(rounded(80, 30, 80, 900, 80, 900, 300, 250, 300, 250, 520, 1000, 520, 1000, 660,
                        1250, 660));
                l.paths.add(rounded(80, 1250, 80, 1100, 80, 1100, 440, 500, 440, 500, 200, 120, 200, 120, 640,
                        30, 640));
                l.paths.add(rounded(80, 640, 30, 640, 180, 780, 360, 640, 600, 640, 690));
                pad(l, Kind.PAD_DAMAGE, 1180, 300);
                return l;
            }
        }
    }

    // ----------------------------------------------------------------------------- Welt 8: Ereignishorizont

    private static LevelDef horizont(int n) {
        switch (n) {
            case 1: {
                LevelDef l = base(8, 1, "Singularität", "Drei Zufahrten aus drei Richtungen", 1300);
                l.paths.add(rounded(90, 30, 360, 200, 360, 200, 160, 460, 160, 460, 300, 640, 360));
                l.paths.add(rounded(90, 640, 30, 640, 110, 960, 110, 960, 330, 640, 360));
                l.paths.add(pts(1250, 560, 1040, 560, 1000, 420, 820, 480, 640, 360));
                rock(l, 640, 560, 44);
                rock(l, 320, 560, 44);
                pad(l, Kind.PAD_DAMAGE, 1120, 160);
                return l;
            }
            case 2: {
                LevelDef l = base(8, 2, "Sanduhr", "Zwei Bahnen, ein Knoten in der Mitte", 1100);
                l.paths.add(rounded(90, 200, 30, 200, 200, 500, 200, 640, 360, 780, 520, 1080, 520, 1080, 690));
                l.paths.add(rounded(90, 1080, 30, 1080, 200, 780, 200, 640, 360, 500, 520, 200, 520, 200, 690));
                rock(l, 640, 110, 50);
                rock(l, 640, 610, 50);
                pad(l, Kind.PAD_DAMAGE, 360, 360);
                pad(l, Kind.PAD_DAMAGE, 920, 360);
                return l;
            }
            case 3: {
                LevelDef l = base(8, 3, "Zeitschleife", "Drei Schleifen auf einer Linie", 700);
                l.paths.add(join(pts(30, 560, 160, 570), loop(300, 360, 130), pts(470, 560), loop(640, 360, 130),
                        pts(810, 560), loop(980, 360, 130), pts(1130, 570, 1250, 560)));
                rock(l, 300, 360, 30);
                rock(l, 640, 360, 30);
                rock(l, 980, 360, 30);
                pad(l, Kind.PAD_DAMAGE, 470, 650);
                pad(l, Kind.PAD_DAMAGE, 810, 110);
                return l;
            }
            case 4: {
                LevelDef l = base(8, 4, "Gravitation", "Ein schwarzes Loch in der Mitte – alles kreist darum", 760);
                l.paths.add(join(pts(30, 150, 330, 150), arc(640, 340, 250, 220, 520), pts(330, 560, 520, 665, 1250, 665)));
                rock(l, 640, 340, 140);
                pad(l, Kind.PAD_SPEED, 1150, 160);
                pad(l, Kind.PAD_SPEED, 130, 600);
                return l;
            }
            case 5: {
                LevelDef l = base(8, 5, "Sturm", "Drei Wellen gegeneinander", 1500);
                l.paths.add(wave(30, 1250, 360, 230, 1.5, 0, 40));
                l.paths.add(wave(30, 1250, 360, 230, 1.5, Math.PI, 40));
                l.paths.add(wave(1250, 30, 360, 90, 3, Math.PI / 2, 40));
                pad(l, Kind.PAD_DAMAGE, 640, 360);
                pad(l, Kind.PAD_SPEED, 640, 660);
                return l;
            }
            default: {
                LevelDef l = base(8, 6, "Horizont", "Der letzte Weg – Titanen alle fünf Wellen", 1700);
                l.paths.add(rounded(90, 30, 100, 1000, 100, 1000, 300, 280, 300, 280, 500, 640, 500, 640, 360));
                l.paths.add(rounded(90, 1250, 200, 1140, 200, 1140, 620, 820, 620, 820, 360, 640, 360));
                l.paths.add(rounded(90, 30, 650, 420, 650, 420, 420, 640, 360));
                rock(l, 640, 200, 44);
                pad(l, Kind.PAD_DAMAGE, 1000, 440);
                pad(l, Kind.PAD_RANGE, 160, 200);
                return l;
            }
        }
    }
}
