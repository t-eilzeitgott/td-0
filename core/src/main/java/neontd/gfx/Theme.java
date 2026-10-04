package neontd.gfx;

import neontd.math.Mathx;

/** Farbpalette und Maße im Neon-Stil: tiefes Schwarz, gesättigte Leuchtfarben, klare Formen. */
public final class Theme {
    private Theme() {
    }

    public static final int BLACK = 0x000000;
    /** Füllung von Panels und Karten. */
    public static final int PANEL = 0x07070D;
    public static final int PANEL_EDGE = 0x24243C;
    public static final int GRID = 0x14142A;
    public static final int LANE_FILL = 0x090913;

    public static final int TEXT = 0xFFFFFF;
    public static final int TEXT_DIM = 0x8A8FB0;

    public static final int CYAN = 0x00E5FF;
    public static final int MAGENTA = 0xFF3DCB;
    public static final int ORANGE = 0xFF8A00;
    public static final int YELLOW = 0xFFD60A;
    public static final int GREEN = 0x39FF88;
    public static final int RED = 0xFF3355;
    public static final int BLUE = 0x4DA6FF;
    public static final int VIOLET = 0x9B7DFF;

    public static final int MONEY = YELLOW;
    public static final int LIFE = 0xFF3D71;
    public static final int GOOD = GREEN;
    public static final int BAD = RED;

    /** Farbverlauf der Gegner nach Lebenspunkten: wenig = gelb, viel = violett/indigo (wie bei BBTAN). */
    private static final int[] ENEMY_STOPS = {0xFFE600, 0xFF9A00, 0xFF3B30, 0xFF2D95, 0xC13DFF, 0x6A5CFF};

    public static int enemyColor(int hp) {
        double t = Mathx.clamp01(Math.log(Math.max(1, hp)) / Math.log(4000.0));
        double pos = t * (ENEMY_STOPS.length - 1);
        int i = Math.min(ENEMY_STOPS.length - 2, (int) Math.floor(pos));
        return Colors.lerp(ENEMY_STOPS[i], ENEMY_STOPS[i + 1], pos - i);
    }
}
