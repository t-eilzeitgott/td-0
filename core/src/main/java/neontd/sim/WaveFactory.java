package neontd.sim;

import java.util.ArrayList;
import java.util.List;
import neontd.math.Mathx;

/**
 * Erzeugt Wellen nach einer festen Kurve – für eigene Level, als Grundlage der handgebauten und für den
 * Endlosmodus.
 *
 * <p>Damit die Kurve bis Welle 1000 trägt, gilt: Die Lebenspunkte wachsen quadratisch, ab Welle 100 zusätzlich
 * leicht exponentiell (Welle 1000: ca. 16 Mio. für einen Block, ca. 470 Mio. für einen Titan – noch im int-Bereich,
 * darüber wird bei {@link #MAX_HP} gekappt). Die <i>Anzahl</i> der Gegner ist nach oben begrenzt (Bildschirm und
 * Rechenzeit), und die Belohnung pro Abschuss wächst langsamer als die Lebenspunkte – dafür bringen die
 * Meisterstufen der Türme (siehe {@link UpgradeTrack}) den Ausgleich. Ein Bot mit einfacher Strategie schafft die
 * 1000 Wellen gerade eben (siehe {@code EndlessProbe} und {@code EndlessTest}).
 */
public final class WaveFactory {
    /** Ab dieser Welle (1-basiert) greifen Beschleunigung und Belohnungs-Skalierung des Endlosmodus. */
    public static final int ENDLESS_FROM = 21;
    /** Obergrenze für Lebenspunkte eines einzelnen Gegners. */
    public static final int MAX_HP = 1_000_000_000;

    /** Exponent der Prämien-Skalierung in Sechzehnteln (10/16 = 0,625). */
    private static final int REWARD_SIXTEENTHS = 10;
    /** Ab dieser Welle wachsen die Lebenspunkte zusätzlich um {@link #LATE_GROWTH} pro Welle (exponentiell). */
    private static final int LATE_FROM = 100;
    private static final double LATE_GROWTH = 0.005;

    private WaveFactory() {
    }

    /** x hoch (k/16), nur mit Wurzeln – auf JVM und im Browser bitgleich. */
    static double powSixteenths(double x, int k) {
        double r = 1;
        double s = x;
        for (int bit = 8; bit >= 1; bit >>= 1) {
            s = Math.sqrt(s);
            if ((k & bit) != 0) {
                r *= s;
            }
        }
        return r;
    }

    /** Basis-Lebenspunkte eines Standard-Gegners in Welle {@code wave} (1-basiert). */
    public static int baseHp(int wave) {
        double w = wave - 1;
        double v = 10 * (1 + 0.21 * w + 0.0175 * w * w);
        for (int i = LATE_FROM; i < wave && v < MAX_HP; i++) {
            v *= 1 + LATE_GROWTH;
        }
        return clampHp(v);
    }

    public static int hp(EnemyType type, int wave) {
        return clampHp(baseHp(wave) * type.hpMul);
    }

    private static int clampHp(double v) {
        return v >= MAX_HP ? MAX_HP : Math.max(1, Mathx.roundToInt(v));
    }

    /**
     * Faktor auf Abschuss- und Wellenprämien: 1 bis Welle 20, danach (HP/HP₂₀)^0,625. Die Prämien wachsen also
     * deutlich langsamer als die Lebenspunkte; den Rest muss der Spieler über kluges Ausbauen der Meisterstufen
     * herausholen.
     */
    public static double rewardScale(int wave) {
        if (wave < ENDLESS_FROM) {
            return 1;
        }
        double x = (double) baseHp(wave) / baseHp(ENDLESS_FROM - 1);
        return powSixteenths(x, REWARD_SIXTEENTHS);
    }

    public static int bonus(int wave) {
        return scaled(50 + 5 * wave, rewardScale(wave));
    }

    private static int scaled(int base, double scale) {
        double v = base * scale;
        return v >= 1.0e9 ? 1_000_000_000 : Math.max(1, Mathx.roundToInt(v));
    }

    /** Eine Welle der generierten Kurve (1-basiert). */
    public static WaveDef wave(int wave) {
        double rs = rewardScale(wave);
        // Im Endlosmodus wird dichter gespawnt, damit eine Welle nicht ewig dauert.
        double pace = wave >= ENDLESS_FROM ? 0.8 : 1.0;
        WaveDef w = new WaveDef().bonus(bonus(wave));
        w.add(EnemyType.BLOCK, Math.min(36, 6 + 2 * wave), Math.max(0.35, 1.1 - 0.03 * wave) * pace, 0,
                hp(EnemyType.BLOCK, wave), scaled(EnemyType.BLOCK.reward, rs));
        if (wave >= 3) {
            w.add(EnemyType.DART, Math.min(30, 3 + wave), 0.55 * pace, 2.0, hp(EnemyType.DART, wave),
                    scaled(EnemyType.DART.reward, rs));
        }
        if (wave >= 6 && wave % 2 == 0) {
            w.add(EnemyType.SPLIT, Math.min(12, 2 + wave / 3), 1.4 * pace, 3.0, hp(EnemyType.SPLIT, wave),
                    scaled(EnemyType.SPLIT.reward, rs));
        }
        if (wave >= 8) {
            w.add(EnemyType.TANK, Math.min(12, 1 + (wave - 8) / 3), 2.5 * pace, 4.0, hp(EnemyType.TANK, wave),
                    scaled(EnemyType.TANK.reward, rs));
        }
        if (wave % 10 == 0) {
            w.add(EnemyType.BOSS, Math.min(5, 1 + wave / 30), 6.0, 6.0, hp(EnemyType.BOSS, wave),
                    scaled(EnemyType.BOSS.reward, rs));
        }
        if (wave >= ENDLESS_FROM) {
            // Abwechslung: jede vierte Welle ein Schwarm, jede vierte (versetzt) ein Dart-Ansturm.
            if (wave % 4 == 0) {
                w.add(EnemyType.MINI, 28, 0.16, 1.0, hp(EnemyType.MINI, wave), scaled(EnemyType.MINI.reward, rs));
            } else if (wave % 4 == 2) {
                w.add(EnemyType.DART, 18, 0.22, 8.0, hp(EnemyType.DART, wave), scaled(EnemyType.DART.reward, rs));
            }
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
