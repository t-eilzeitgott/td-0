package neontd.progress;

import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.level.WaveStyle;

/**
 * Die Tages-Challenge: Jeden Tag dasselbe Level mit demselben Modifikator für alle – gespielt im Endlosmodus,
 * gezählt wird die weiteste besiegte Welle. Ergibt sich nur aus dem Datum, also sehen alle Geräte dieselbe
 * Aufgabe; die Ergebnisse landen in der globalen Bestenliste des Tages.
 */
public final class Daily {
    /** Ein Modifikator, der das Level an diesem Tag anders spielen lässt. */
    public enum Modifier {
        TEMPO("Tempo", "Gegner sind 25 % schneller", 0xFF5470),
        SPARSAM("Sparsam", "Nur 60 % Startgeld", 0xFFC857),
        GLAS("Glas", "Nur 6 Leben", 0xFF3D71),
        TITANEN("Titanen", "Alle fünf Wellen ein Boss", 0xFF3DCB),
        SCHWARM("Schwarm", "Alles kommt als Schwarm", 0x39FF88),
        PANZER("Panzer", "Panzerkolonnen von Anfang an", 0xB6FF3B),
        PHANTOME("Phantome", "Geisterhafte Gegner", 0x9B7DFF),
        ZÄH("Zäh", "Gegner haben 30 % mehr Leben", 0xFF8A00);

        public final String label;
        public final String desc;
        public final int color;

        Modifier(String label, String desc, int color) {
            this.label = label;
            this.desc = desc;
            this.color = color;
        }
    }

    /** Kristalle für die Tages-Challenge, sobald Welle {@link #REWARD_WAVE} erreicht ist. */
    public static final int REWARD = 40;
    public static final int REWARD_WAVE = 15;

    public final int day;
    public final LevelDef level;
    public final Modifier modifier;

    private Daily(int day, LevelDef level, Modifier modifier) {
        this.day = day;
        this.level = level;
        this.modifier = modifier;
    }

    /** Die Challenge des Tages {@code day} (Tageszahl seit 1970). */
    public static Daily today(int day) {
        int d = Math.max(0, day);
        int h = (int) ((d * 2654435761L) >>> 8) & 0x7fffffff;
        java.util.List<LevelDef> all = Levels.builtins();
        LevelDef l = all.get((d * 7 + h % 5) % all.size());
        Modifier m = Modifier.values()[(d * 3 + h / 7) % Modifier.values().length];
        return new Daily(day, l, m);
    }

    /** Kennung des Tages (Rekorde und Bestenliste). */
    public String id() {
        return "daily-" + day;
    }

    public static boolean isDaily(String levelId) {
        return levelId != null && levelId.startsWith("daily-");
    }

    /** Das Level mit dem Modifikator, zum Spielen im Endlosmodus. */
    public LevelDef build() {
        LevelDef l = level.copy();
        l.id = id();
        l.name = level.name;
        l.blurb = modifier.label + ": " + modifier.desc;
        switch (modifier) {
            case TEMPO:
                l.speedMul = 1.25;
                break;
            case SPARSAM:
                l.startMoney = (int) Math.round(level.startMoney * 0.6 / 10) * 10;
                break;
            case GLAS:
                l.startLives = 6;
                break;
            case TITANEN:
                l.style = WaveStyle.TITANEN;
                l.altStyle = null;
                l.bossLevel = true;
                break;
            case SCHWARM:
                l.style = WaveStyle.SCHWARM;
                l.altStyle = null;
                break;
            case PANZER:
                l.style = WaveStyle.PANZER;
                l.altStyle = null;
                l.tier = Math.max(l.chapter, 5);
                break;
            case PHANTOME:
                l.style = WaveStyle.PHANTOME;
                l.altStyle = null;
                l.tier = Math.max(l.chapter, 7);
                break;
            default:
                l.hpMul = level.hpMul * 1.3;
                break;
        }
        return l;
    }
}
