package neontd.render;

import neontd.sim.Enemy;

/** Kurze, gut lesbare Zahlenbeschriftung für Lebenspunkte (1.2K, 15K, 2.3M). */
public final class HpLabel {
    private HpLabel() {
    }

    public static String format(int hp) {
        if (hp < 1000) {
            return Integer.toString(Math.max(0, hp));
        }
        if (hp < 10000) {
            return (hp / 1000) + "." + ((hp % 1000) / 100) + "K";
        }
        if (hp < 1000000) {
            return (hp / 1000) + "K";
        }
        return (hp / 1000000) + "." + ((hp % 1000000) / 100000) + "M";
    }

    /** Beschriftung des Gegners; wird nur neu gebaut, wenn sich die Lebenspunkte geändert haben. */
    public static String of(Enemy e) {
        if (e.hpLabelValue != e.hp) {
            e.hpLabelValue = e.hp;
            e.hpLabel = format(e.hp);
        }
        return e.hpLabel;
    }
}
