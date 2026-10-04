package neontd.progress;

/**
 * Medaillen je Level nach der weitesten besiegten Welle (Normalspiel und Endlosmodus zusammen): Bronze ab Welle 20
 * (= Sieg über die handgebauten Wellen), Silber ab 50, Gold ab 100, Platin ab 250. Jede Medaille bringt einmalig XP.
 */
public final class Medals {
    public static final int[] WAVES = {20, 50, 100, 250};
    public static final String[] NAMES = {"BRONZE", "SILBER", "GOLD", "PLATIN"};
    public static final int[] COLORS = {0xE08A3C, 0xC9D1E6, 0xFFD60A, 0x6FF3FF};
    public static final int[] XP = {150, 400, 1000, 3000};

    private Medals() {
    }

    /** Erreichte Stufe: 0 = keine, 1 = Bronze … 4 = Platin. */
    public static int tier(int clearedWaves) {
        int t = 0;
        for (int i = 0; i < WAVES.length; i++) {
            if (clearedWaves >= WAVES[i]) {
                t = i + 1;
            }
        }
        return t;
    }

    /** XP für alle Medaillen, die beim Übergang von Stufe {@code from} auf {@code to} neu dazukommen. */
    public static int xpBetween(int from, int to) {
        int sum = 0;
        for (int i = Math.max(0, from); i < to && i < XP.length; i++) {
            sum += XP[i];
        }
        return sum;
    }

    /** Wellen bis zur nächsten Medaille oder 0 bei Platin. */
    public static int nextTarget(int tier) {
        return tier >= WAVES.length ? 0 : WAVES[tier];
    }
}
