package neontd.sim;

import java.util.ArrayList;
import java.util.List;

/** Eine Welle = mehrere Spawn-Gruppen, die zeitversetzt beginnen. */
public final class WaveDef {
    /** Eine Gruppe gleichartiger Gegner. */
    public static final class Group {
        public final EnemyType type;
        public final int count;
        /** Sekunden zwischen zwei Gegnern. */
        public final double interval;
        /** Sekunden bis zum ersten Gegner (ab Wellenstart). */
        public final double delay;
        public final int hp;
        /** Pfadindex oder -1 für abwechselnd über alle Pfade. */
        public final int path;
        /** Geld pro abgeschossenem Gegner (wächst im Endlosmodus mit den Lebenspunkten). */
        public final int reward;

        public Group(EnemyType type, int count, double interval, double delay, int hp, int path) {
            this(type, count, interval, delay, hp, path, type.reward);
        }

        public Group(EnemyType type, int count, double interval, double delay, int hp, int path, int reward) {
            this.type = type;
            this.count = count;
            this.interval = interval;
            this.delay = delay;
            this.hp = hp;
            this.path = path;
            this.reward = reward;
        }
    }

    public final List<Group> groups = new ArrayList<>();
    /** Geldbonus, wenn die Welle komplett besiegt ist. */
    public int bonus;

    public WaveDef add(EnemyType type, int count, double interval, double delay, int hp) {
        groups.add(new Group(type, count, interval, delay, hp, -1));
        return this;
    }

    /** Wie {@link #add(EnemyType, int, double, double, int)}, mit eigener Abschuss-Belohnung. */
    public WaveDef add(EnemyType type, int count, double interval, double delay, int hp, int reward) {
        groups.add(new Group(type, count, interval, delay, hp, -1, reward));
        return this;
    }

    public WaveDef bonus(int bonus) {
        this.bonus = bonus;
        return this;
    }

    public int totalEnemies() {
        int n = 0;
        for (Group g : groups) {
            n += g.count;
        }
        return n;
    }

    /** Summe der Lebenspunkte aller Gegner der Welle (ohne Splitter-Kinder) – für Balance-Auswertungen. */
    public double totalHp() {
        double n = 0;
        for (Group g : groups) {
            n += (double) g.count * g.hp;
        }
        return n;
    }
}
