package neontd.sim;

import java.util.ArrayList;

/**
 * Ein Abbild des Spielstands zum Speichern und Fortsetzen (Türme, Gegner im Anflug, Geld, Leben, Welle). Geschosse
 * und Effekte gehören nicht dazu – sie sind nach Sekundenbruchteilen ohnehin verschwunden. Der Zustand ist
 * absichtlich nur an Zeitpunkten abbildbar, an denen keine Welle mehr Gegner nachliefert (siehe
 * {@link World#snapshot()}).
 */
public final class WorldSnapshot {
    /** Ein Turm. */
    public static final class TowerData {
        public int type;
        public double x;
        public double y;
        public final int[] level = new int[3];
        public int invested;
        public int mode;
    }

    /** Ein Gegner im Anflug. */
    public static final class EnemyData {
        public int type;
        public int path;
        public int wave;
        public int hp;
        public double dist;
        public int reward;
        public double slowFactor = 1;
        public double slowTimer;
    }

    public int money;
    public int lives;
    public int kills;
    public int waveIndex;
    public int clearedWaves;
    public double time;
    public boolean endless;
    public boolean autoStart;
    public final ArrayList<TowerData> towers = new ArrayList<>();
    public final ArrayList<EnemyData> enemies = new ArrayList<>();
}
