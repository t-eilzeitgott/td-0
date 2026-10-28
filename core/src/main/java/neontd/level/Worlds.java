package neontd.level;

/**
 * Die acht Welten der Kampagne. Jede Welt hat eine eigene Farbwelt (Akzent, Bahn), ein Thema, das sich in den
 * Strecken und Wellenstilen spiegelt, und eine Sternen-Hürde: Eine Welt öffnet, sobald in den vorigen genug
 * Sterne gesammelt sind (je Level bis zu drei) – wer weiterkommen will, spielt gute Level noch einmal besser.
 */
public final class Worlds {
    public static final int COUNT = 8;
    public static final int LEVELS = 6;

    public static final String[] IDS = {"stadt", "tiefsee", "magma", "garten", "hafen", "kathedrale", "strom", "horizont"};
    public static final String[] NAMES = {"Neonstadt", "Tiefsee", "Magmakern", "Gewächshaus", "Raumhafen",
        "Kathedrale", "Datenstrom", "Ereignishorizont"};
    public static final String[] TAGLINES = {
        "Erste Runden durch die leuchtende Stadt",
        "Strömungen, Riffe und Schildträger",
        "Enge Schluchten voller Panzer",
        "Schwärme, Ranken und Splitter",
        "Phantome zwischen den Docks",
        "Symmetrie, Heiler und Bollwerke",
        "Spiralen, Schleifen und Chaos",
        "Drei Wege, Titanen, kein Erbarmen"
    };
    /** Akzentfarbe der Welt (Karten, Rahmen, Überschriften). */
    public static final int[] ACCENT = {0x00E5FF, 0x3DD6C6, 0xFF7A1F, 0x5CFF7A, 0xB48BFF, 0xFFC857, 0xFF4FD8, 0xC9D6FF};
    /** Farbe der Gegnerbahn. */
    public static final int[] LANE = {0x4A62FF, 0x1F8BA8, 0xB33A1E, 0x2E9A4E, 0x6F4FD8, 0xA8862A, 0xB82E9E, 0x6E7AB8};
    /** Start-Leben je Welt – es wird knapper. */
    public static final int[] LIVES = {20, 20, 18, 18, 16, 16, 14, 12};
    /** Sterne (insgesamt), die eine Welt öffnen. Welt 1 ist immer offen. */
    public static final int[] REQUIRED_STARS = {0, 11, 25, 41, 59, 79, 100, 118};
    /** Je Welt drei Wellenstile: Signatur, zweiter, dritter. */
    public static final WaveStyle[][] STYLES = {
        {WaveStyle.KLASSIK, WaveStyle.ANSTURM, WaveStyle.SCHWARM},
        {WaveStyle.BOLLWERK, WaveStyle.KLASSIK, WaveStyle.SCHWARM},
        {WaveStyle.PANZER, WaveStyle.SCHWERGEWICHT, WaveStyle.BOLLWERK},
        {WaveStyle.SCHWARM, WaveStyle.SPLITTER, WaveStyle.ANSTURM},
        {WaveStyle.PHANTOME, WaveStyle.ANSTURM, WaveStyle.SPLITTER},
        {WaveStyle.LAZARETT, WaveStyle.BOLLWERK, WaveStyle.PANZER},
        {WaveStyle.CHAOS, WaveStyle.PHANTOME, WaveStyle.SCHWERGEWICHT},
        {WaveStyle.CHAOS, WaveStyle.LAZARETT, WaveStyle.PANZER},
    };

    private Worlds() {
    }

    public static String name(int world) {
        return NAMES[world - 1];
    }

    public static int accent(int world) {
        return ACCENT[Math.max(1, Math.min(COUNT, world)) - 1];
    }

    public static int lane(int world) {
        return LANE[Math.max(1, Math.min(COUNT, world)) - 1];
    }

    /** Höchstzahl an Sternen in allen Welten bis einschließlich {@code world}. */
    public static int maxStarsThrough(int world) {
        return world * LEVELS * 3;
    }
}
