package neontd.progress;

import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.level.WaveStyle;
import neontd.sim.Special;
import neontd.sim.TowerType;

/**
 * Die Arena: ein Wettkampf, der eine Woche läuft (Montag 0 Uhr bis Sonntag 24 Uhr, Ortszeit). Alle spielen dieselbe
 * Karte mit denselben Regeln – gleicher Turm-Pool für alle (unabhängig von Spielerlevel und Shop), zwei Joker-Spezials,
 * 10 Leben, keine Forschung, keine Startboni, keine Meisterstufen. Die Wellen werden immer härter, bis der Lauf
 * endet; gezählt werden Punkte (siehe {@link neontd.sim.World#arena}). Welche Arena dran ist, ergibt sich nur aus der
 * Kalenderwoche, also sehen alle Geräte dieselbe.
 */
public final class Arena {
    /** Leben in jeder Arena. */
    public static final int LIVES = 10;
    /** Kristalle für die Platzierung der Vorwoche (Platz 1, 2, 3) und fürs Mitmachen. */
    public static final int[] PODIUM_CRYSTALS = {300, 200, 120};
    public static final int PARTICIPATION_CRYSTALS = 40;

    /** Eine Arena: Karte, Gegnermischung, Turm-Pool, Joker und Härte. */
    public static final class Def {
        public final String name;
        public final String levelId;
        public final WaveStyle style;
        public final WaveStyle alt;
        public final TowerType[] pool;
        public final Special[] jokers;
        public final int money;
        public final double hpMul;
        public final double hpRamp;
        public final double speedMul;
        /** Kurze Regelzeile für Karte und Banner. */
        public final String rule;

        Def(String name, String levelId, WaveStyle style, WaveStyle alt, TowerType[] pool, Special[] jokers, int money,
            double hpMul, double hpRamp, double speedMul, String rule) {
            this.name = name;
            this.levelId = levelId;
            this.style = style;
            this.alt = alt;
            this.pool = pool;
            this.jokers = jokers;
            this.money = money;
            this.hpMul = hpMul;
            this.hpRamp = hpRamp;
            this.speedMul = speedMul;
            this.rule = rule;
        }
    }

    private static TowerType[] pool(TowerType... t) {
        return t;
    }

    private static Special[] jokers(Special... s) {
        return s;
    }

    /**
     * Die Wochen im Wechsel. Härte ({@code hpMul}, {@code hpRamp}) ist mit dem Test-Bot kalibriert: Er kommt
     * ungefähr bis Welle 22–30 (siehe {@code ArenaProbe}); wer Ausbaupfade, Pads, Joker und Vorlauf geschickt nutzt,
     * deutlich weiter.
     */
    static final Def[] ROTATION = {
        new Def("Boulevard", "stadt-6", WaveStyle.KLASSIK, WaveStyle.ANSTURM,
                pool(TowerType.PULSE, TowerType.MORTAR, TowerType.FROST, TowerType.SNIPER, TowerType.ARC),
                jokers(Special.CRIT, Special.OVERCLOCK), 650, 0.57, 0.040, 1.0,
                "Die klassischen fünf – drei Pads am Boulevard"),
        new Def("Doppelhelix", "tiefsee-2", WaveStyle.BOLLWERK, WaveStyle.SCHWARM,
                pool(TowerType.PULSE, TowerType.SALVE, TowerType.FROST, TowerType.MORTAR, TowerType.TESLA),
                jokers(Special.PIERCE, Special.CHILL), 800, 0.37, 0.040, 1.0,
                "Zwei Bahnen, Schilde und Schwärme im Wechsel"),
        new Def("Schmiede", "magma-4", WaveStyle.PANZER, WaveStyle.SCHWERGEWICHT,
                pool(TowerType.PULSE, TowerType.SNIPER, TowerType.MORTAR, TowerType.FROST, TowerType.RAILGUN),
                jokers(Special.VULN, Special.BOSSBANE), 600, 1.25, 0.040, 1.0,
                "Panzer und Kolosse – nur Wucht hilft"),
        new Def("Kreuzgang", "garten-3", WaveStyle.SPLITTER, WaveStyle.SCHWARM,
                pool(TowerType.MORTAR, TowerType.ARC, TowerType.SALVE, TowerType.FROST, TowerType.INFERNO),
                jokers(Special.SPLASH, Special.BURN), 850, 0.40, 0.040, 1.0,
                "Splitter zerfallen in Schwärme – Flächenschaden zählt"),
        new Def("Docking", "hafen-1", WaveStyle.PHANTOME, WaveStyle.ANSTURM,
                pool(TowerType.PULSE, TowerType.SNIPER, TowerType.TESLA, TowerType.FROST, TowerType.VENOM),
                jokers(Special.STUN, Special.LONGSHOT), 600, 0.80, 0.040, 1.1,
                "Phantome im Tiefflug, 10 % schneller"),
        new Def("Glockenturm", "kathedrale-5", WaveStyle.LAZARETT, WaveStyle.BOLLWERK,
                pool(TowerType.SNIPER, TowerType.SALVE, TowerType.MORTAR, TowerType.FROST, TowerType.SHREDDER),
                jokers(Special.EXECUTE, Special.AFTERSHOCK), 650, 0.71, 0.040, 1.0,
                "Heiler hinter Schilden – wer zuerst fällt, entscheidet"),
        new Def("Netz", "strom-3", WaveStyle.CHAOS, null,
                pool(TowerType.PULSE, TowerType.ARC, TowerType.TESLA, TowerType.MORTAR, TowerType.GRAVITON),
                jokers(Special.CRIT, Special.KNOCKBACK), 950, 0.80, 0.040, 1.0,
                "Chaos auf zwei Bahnen – jede Welle anders"),
        new Def("Gravitation", "horizont-4", WaveStyle.TITANEN, WaveStyle.PANZER,
                pool(TowerType.SNIPER, TowerType.RAILGUN, TowerType.FROST, TowerType.SALVE, TowerType.PRISMA),
                jokers(Special.BOSSBANE, Special.AURA), 800, 0.85, 0.040, 1.0,
                "Alle fünf Wellen ein Titan"),
    };

    /** Laufende Woche seit 1970 (Montag-basiert; Woche 0 begann am Montag, 29.12.1969). */
    public final int week;
    /** Kennung der Kalenderwoche nach ISO 8601, z. B. {@code 2026-W44} (Bestenliste). */
    public final String id;
    public final int isoYear;
    public final int isoWeek;
    public final Def def;

    private Arena(int week) {
        this.week = week;
        int monday = week * 7 - 3;
        int thursday = monday + 3;
        int[] c = Calendar.civil(thursday);
        this.isoYear = c[0];
        this.isoWeek = (thursday - Calendar.daysFromCivil(c[0], 1, 1)) / 7 + 1;
        this.id = isoYear + "-W" + (isoWeek < 10 ? "0" : "") + isoWeek;
        this.def = ROTATION[((week % ROTATION.length) + ROTATION.length) % ROTATION.length];
    }

    /** Montag-basierte Wochennummer eines Tages (Tage seit 1970). */
    public static int weekOf(int day) {
        return Math.floorDiv(day + 3, 7);
    }

    /** Die Arena der Woche, in der der Tag {@code day} liegt. */
    public static Arena of(int day) {
        return new Arena(weekOf(day));
    }

    /** Die Arena der Vorwoche. */
    public Arena previous() {
        return new Arena(week - 1);
    }

    /** Erster Tag (Montag) dieser Arena. */
    public int firstDay() {
        return week * 7 - 3;
    }

    /** Erster Tag der nächsten Arena (Montag darauf). */
    public int endDay() {
        return firstDay() + 7;
    }

    /** Millisekunden bis zum Ende der Woche (Montag 0 Uhr Ortszeit). */
    public double remainingMillis(double nowMillis, double offsetMillis) {
        return endDay() * Calendar.DAY_MS - (nowMillis + offsetMillis);
    }

    /** Kurzer Text für die Restzeit: "3 T 4 Std", "5 Std 12 Min", "12 Min". */
    public static String remainingText(double ms) {
        long min = (long) Math.max(0, Math.floor(ms / 60000.0));
        long d = min / (60 * 24);
        long h = (min / 60) % 24;
        long m = min % 60;
        if (d > 0) {
            return d + " T " + h + " Std";
        }
        if (h > 0) {
            return h + " Std " + m + " Min";
        }
        return Math.max(1, m) + " Min";
    }

    /** Anzeige "KW 44". */
    public String label() {
        return "KW " + isoWeek;
    }

    /** Level-Kennung des Arena-Laufs (Speicherstand). */
    public String levelId() {
        return "arena-" + id;
    }

    public static boolean isArena(String levelId) {
        return levelId != null && levelId.startsWith("arena-");
    }

    /** Ist der Turm in dieser Arena erlaubt? */
    public boolean allows(TowerType t) {
        for (TowerType p : def.pool) {
            if (p == t) {
                return true;
            }
        }
        return false;
    }

    /** Das Level dieser Arena: Karte und Pads der Vorlage, eigene Wellen, 10 Leben, festes Startgeld. */
    public LevelDef build() {
        LevelDef src = Levels.find(def.levelId);
        LevelDef l = src.copy();
        l.id = levelId();
        l.name = def.name;
        l.blurb = def.rule;
        l.builtin = true;
        l.style = def.style;
        l.altStyle = def.alt;
        l.tier = 9;
        l.number = 1;
        l.bossLevel = def.style == WaveStyle.TITANEN;
        l.startLives = LIVES;
        l.startMoney = def.money;
        l.hpMul = def.hpMul;
        l.hpRamp = def.hpRamp;
        l.speedMul = def.speedMul;
        return l;
    }

    /** Bauabfolge für den Test-Bot (Kalibrierung, Hintergrund-Demo) aus dem Pool der Arena. */
    public TowerType[] botOrder() {
        int[] pattern = {0, 0, 1, 2, 0, 3, 4, 1, 2, 3, 0, 4, 1, 3};
        TowerType[] order = new TowerType[pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            order[i] = def.pool[pattern[i] % def.pool.length];
        }
        return order;
    }

    /** Alle Arenen der Rotation (für Werkzeuge und Tests). */
    public static int rotationSize() {
        return ROTATION.length;
    }

    /** Die Arena mit dem Rotationsplatz {@code i} (Werkzeuge und Tests). */
    public static Arena ofRotation(int i, int nearWeek) {
        int base = nearWeek - ((nearWeek % ROTATION.length) + ROTATION.length) % ROTATION.length;
        return new Arena(base + i);
    }
}
