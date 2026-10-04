package neontd.progress;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import neontd.sim.TowerType;

/**
 * Das Spielerprofil: Erfahrung (XP), Spielerlevel, Statistiken und was daraus folgt – freigeschaltete Türme,
 * Startbonus und Titel. Reine Daten und Rechenregeln, ohne Bildschirm und Speicher (siehe {@code ProfileJson}
 * und {@code SaveStore} für das Ablegen).
 *
 * <p>XP gibt es für jede besiegte Welle (mehr bei Titan-Wellen) und einmalig für den ersten Sieg über ein Level.
 * Die Kurve ist so gewählt, dass der erste vollständige Durchgang Level 4 bringt (Frost und Mörser) und
 * 1000 Endlos-Wellen Level 63.
 */
public final class Progress {
    public static final int MAX_LEVEL = 100;
    /** XP-Obergrenze (bleibt weit unter dem int-Bereich). */
    public static final int MAX_XP = 2_000_000_000;
    public static final int MAX_NAME = 16;
    /** Einmaliger Bonus für den ersten Sieg über ein Level. */
    public static final int FIRST_WIN_XP = 300;
    public static final int WIN_XP = 100;

    /** Anzeigename; leer = "SPIELER". */
    public String name = "";
    public int xp;
    public int kills;
    public int wavesCleared;
    public int gamesPlayed;
    public int gamesWon;
    /** Levelkennungen, die mindestens einmal gewonnen wurden. */
    public final Set<String> won = new TreeSet<>();
    /** Je Level: weiteste besiegte Welle im normalen Spiel. */
    public final Map<String, Integer> bestWave = new TreeMap<>();
    /** Je Level: weiteste besiegte Welle im Endlosmodus. */
    public final Map<String, Integer> bestEndless = new TreeMap<>();
    /** Zeitpunkt der letzten Änderung (Millisekunden seit 1970) – Grundlage für das Zusammenführen mit der Cloud. */
    public double updated;

    /** Übernimmt alle Werte eines anderen Profils (z. B. nach dem Zusammenführen mit der Cloud). */
    public void copyFrom(Progress o) {
        name = o.name;
        xp = o.xp;
        kills = o.kills;
        wavesCleared = o.wavesCleared;
        gamesPlayed = o.gamesPlayed;
        gamesWon = o.gamesWon;
        won.clear();
        won.addAll(o.won);
        bestWave.clear();
        bestWave.putAll(o.bestWave);
        bestEndless.clear();
        bestEndless.putAll(o.bestEndless);
        updated = o.updated;
    }

    // ---------------------------------------------------------------------------------- Level und XP

    /** XP, die für den Aufstieg von {@code level} auf {@code level + 1} nötig sind. */
    public static int xpForNext(int level) {
        int l = Math.max(1, level) - 1;
        return 120 + 40 * l + 6 * l * l;
    }

    /** Gesamt-XP, ab der {@code level} erreicht ist (Level 1 = 0). */
    public static int xpAtLevel(int level) {
        int total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += xpForNext(l);
        }
        return total;
    }

    public static int levelForXp(int xp) {
        int level = 1;
        int left = xp;
        while (level < MAX_LEVEL && left >= xpForNext(level)) {
            left -= xpForNext(level);
            level++;
        }
        return level;
    }

    public int level() {
        return levelForXp(xp);
    }

    /** XP innerhalb des aktuellen Levels. */
    public int xpIntoLevel() {
        return xp - xpAtLevel(level());
    }

    /** XP, die für das nächste Level insgesamt nötig sind (0 bei Höchststufe). */
    public int xpSpan() {
        int l = level();
        return l >= MAX_LEVEL ? 0 : xpForNext(l);
    }

    /** Fortschritt im aktuellen Level, 0..1. */
    public double levelFraction() {
        int span = xpSpan();
        return span == 0 ? 1 : Math.min(1, (double) xpIntoLevel() / span);
    }

    /** Fügt XP hinzu. @return Anzahl der dabei aufgestiegenen Level */
    public int addXp(int amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = level();
        xp = (int) Math.min(MAX_XP, (double) xp + amount);
        return level() - before;
    }

    // --------------------------------------------------------------------------------- Spielereignisse

    /** XP für eine besiegte Welle (1-basiert): 6 + Wellennummer, doppelt bei Titan-Wellen. */
    public static int waveXp(int wave, boolean boss) {
        int base = 6 + wave;
        return boss ? base * 2 : base;
    }

    /** Verbucht eine besiegte Welle. @return erhaltene XP */
    public int onWaveCleared(int wave, boolean boss) {
        int gain = waveXp(wave, boss);
        wavesCleared++;
        addXp(gain);
        return gain;
    }

    /** Verbucht den Sieg über ein Level. @return erhaltene XP */
    public int onWon(String levelId) {
        gamesWon++;
        int gain = WIN_XP + (won.add(levelId) ? FIRST_WIN_XP : 0);
        addXp(gain);
        return gain;
    }

    public void onRecord(String levelId, int cleared, boolean endless) {
        Map<String, Integer> map = endless ? bestEndless : bestWave;
        Integer old = map.get(levelId);
        if (old == null || cleared > old) {
            map.put(levelId, cleared);
        }
    }

    public int best(String levelId, boolean endless) {
        Integer v = (endless ? bestEndless : bestWave).get(levelId);
        return v == null ? 0 : v;
    }

    /** Weiteste besiegte Welle eines Levels – Normalspiel und Endlosmodus zusammen. */
    public int bestOverall(String levelId) {
        return Math.max(best(levelId, false), best(levelId, true));
    }

    /** Medaillenstufe eines Levels: 0 = keine, 1 = Bronze … 4 = Platin (siehe {@link Medals}). */
    public int medal(String levelId) {
        return Medals.tier(bestOverall(levelId));
    }

    /** Alle Medaillen über alle Level (Bronze, Silber, Gold, Platin zählen je eine). */
    public int totalMedals() {
        Set<String> ids = new TreeSet<>(bestWave.keySet());
        ids.addAll(bestEndless.keySet());
        int n = 0;
        for (String id : ids) {
            n += medal(id);
        }
        return n;
    }

    /**
     * Ist das Level spielbar? Das erste eingebaute Level und eigene Level immer; jedes weitere, sobald im Level davor
     * mindestens Bronze erreicht ist.
     */
    public boolean levelUnlocked(String levelId) {
        int idx = neontd.level.Levels.indexOf(levelId);
        if (idx <= 0) {
            return true;
        }
        return medal(neontd.level.Levels.builtins().get(idx - 1).id) >= 1;
    }

    /** Der Endlosmodus eines Levels ist nach dem ersten Sieg offen. */
    public boolean endlessUnlocked(String levelId) {
        return won.contains(levelId);
    }

    // --------------------------------------------------------------------------------------- Freischaltungen

    public boolean isUnlocked(TowerType type) {
        return level() >= type.unlockLevel;
    }

    /** Türme, die genau mit diesem Level freigeschaltet werden. */
    public static TowerType[] unlockedAt(int level) {
        int n = 0;
        for (TowerType t : TowerType.values()) {
            if (t.unlockLevel == level) {
                n++;
            }
        }
        TowerType[] r = new TowerType[n];
        int i = 0;
        for (TowerType t : TowerType.values()) {
            if (t.unlockLevel == level) {
                r[i++] = t;
            }
        }
        return r;
    }

    /** Das nächste noch gesperrte Turm-Level oder 0, wenn alles frei ist. */
    public int nextUnlockLevel() {
        int lvl = level();
        int best = 0;
        for (TowerType t : TowerType.values()) {
            if (t.unlockLevel > lvl && (best == 0 || t.unlockLevel < best)) {
                best = t.unlockLevel;
            }
        }
        return best;
    }

    /** Zusätzliches Startgeld: 10 je Level über 1, höchstens 300. */
    public int startMoneyBonus() {
        return Math.min(300, 10 * (level() - 1));
    }

    /** Zusätzliche Start-Leben: eines je fünf Level, höchstens fünf. */
    public int startLivesBonus() {
        return Math.min(5, level() / 5);
    }

    // ------------------------------------------------------------------------------------------- Titel

    private static final int[] TITLE_FROM = {1, 3, 6, 10, 15, 25, 40, 60, 80, 100};
    private static final String[] TITLES = {
        "REKRUT", "FUNKE", "PULSGEBER", "WÄCHTER", "STRATEGE", "ARCHITEKT", "MEISTER", "LEGENDE", "TITANBEZWINGER",
        "NEON-GOTT"
    };

    public static String titleFor(int level) {
        String t = TITLES[0];
        for (int i = 0; i < TITLE_FROM.length; i++) {
            if (level >= TITLE_FROM[i]) {
                t = TITLES[i];
            }
        }
        return t;
    }

    public String title() {
        return titleFor(level());
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? "SPIELER" : name.trim();
    }

    /** Bereinigt einen eingegebenen Namen: höchstens {@link #MAX_NAME} Zeichen, ohne Steuerzeichen und Ränder. */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127 && c != '<' && c != '>') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
