package neontd.sim;

import java.util.ArrayList;
import java.util.List;

/** Erzeugt Wellen nach einer festen Kurve – für eigene Level und als Grundlage der handgebauten. */
public final class WaveFactory {
    private WaveFactory() {
    }

    /** Basis-Lebenspunkte eines Standard-Gegners in Welle {@code wave} (1-basiert). */
    public static int baseHp(int wave) {
        double w = wave - 1;
        return (int) Math.round(10 * (1 + 0.21 * w + 0.0175 * w * w));
    }

    public static int hp(EnemyType type, int wave) {
        return Math.max(1, (int) Math.round(baseHp(wave) * type.hpMul));
    }

    public static int bonus(int wave) {
        return 50 + 5 * wave;
    }

    /** Eine Welle der generierten Kurve (1-basiert). */
    public static WaveDef wave(int wave) {
        WaveDef w = new WaveDef().bonus(bonus(wave));
        w.add(EnemyType.BLOCK, Math.min(36, 6 + 2 * wave), Math.max(0.35, 1.1 - 0.03 * wave), 0, hp(EnemyType.BLOCK, wave));
        if (wave >= 3) {
            w.add(EnemyType.DART, Math.min(30, 3 + wave), 0.55, 2.0, hp(EnemyType.DART, wave));
        }
        if (wave >= 6 && wave % 2 == 0) {
            w.add(EnemyType.SPLIT, 2 + wave / 3, 1.4, 3.0, hp(EnemyType.SPLIT, wave));
        }
        if (wave >= 8) {
            w.add(EnemyType.TANK, 1 + (wave - 8) / 3, 2.5, 4.0, hp(EnemyType.TANK, wave));
        }
        if (wave % 10 == 0) {
            w.add(EnemyType.BOSS, 1 + wave / 30, 6.0, 6.0, hp(EnemyType.BOSS, wave));
        }
        return w;
    }

    public static List<WaveDef> generate(int count) {
        List<WaveDef> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(wave(i));
        }
        return list;
    }
}
