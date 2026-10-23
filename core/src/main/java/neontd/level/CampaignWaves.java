package neontd.level;

import java.util.ArrayList;
import java.util.List;
import neontd.sim.EnemyType;
import neontd.sim.WaveDef;
import neontd.sim.WaveFactory;

/**
 * Baut die Wellen eines eingebauten Levels aus seinem {@link WaveStyle}: Die Last (Lebenspunkte) jeder Welle folgt der
 * Standardkurve, wird aber nach den Gewichten des Stils auf Gegnerarten verteilt. Welche Arten es überhaupt gibt, hängt
 * vom Kapitel ab – so tauchen neue Gegner nach und nach auf (siehe {@link #introduced}).
 */
public final class CampaignWaves {
    /** Ab diesem Kapitel gibt es die Gegnerart (Reihenfolge wie {@link WaveStyle#TYPES}). */
    private static final int[] FROM_CHAPTER = {1, 1, 2, 3, 3, 4, 5, 6, 7};
    /** Höchste Anzahl je Art in einer Welle. */
    private static final int[] CAP = {60, 60, 80, 14, 16, 20, 14, 5, 24};
    /** Grundabstand zwischen zwei Gegnern (Sekunden) – wird mit dem Tempo des Stils und dem Wellenverlauf verrechnet. */
    private static final double[] GAP = {0.9, 0.55, 0.17, 1.4, 2.5, 1.0, 2.0, 2.2, 0.6};
    /** Wie viel Last ein Gegner in der Welle "kostet", relativ zu seinen Lebenspunkten (Schild und Splitter-Kinder zählen mit). */
    private static final double[] WEIGHT = {1, 1, 1, 1.9, 1, 1.4, 1.3, 1, 1};

    private CampaignWaves() {
    }

    /** In welchem Kapitel taucht die Art zum ersten Mal auf? */
    public static int introduced(EnemyType type) {
        for (int i = 0; i < WaveStyle.TYPES.length; i++) {
            if (WaveStyle.TYPES[i] == type) {
                return FROM_CHAPTER[i];
            }
        }
        return type == EnemyType.BOSS ? 1 : 1;
    }

    /** Welle {@code wave} (1-basiert; auch über 20 hinaus für den Endlosmodus) des Levels. */
    public static WaveDef wave(LevelDef level, int wave) {
        WaveStyle style = level.style;
        if (wave <= 2) {
            // Aufwärmen: die ersten beiden Wellen jedes Levels sind schlichte Blöcke (wie die Standardkurve).
            return WaveFactory.wave(wave, level.hpMul);
        }
        double t = Math.min(1.0, (wave - 1) / 19.0);
        int chapter = Math.max(1, level.chapter);
        int round = Math.max(0, (level.number - 1) / 5);
        double[] w = new double[WaveStyle.TYPES.length];
        double sum = 0;
        for (int i = 0; i < w.length; i++) {
            double v = style.early[i] + (style.late[i] - style.early[i]) * t;
            if (style == WaveStyle.CHAOS) {
                // jede Welle betont eine andere Art
                v += (i == (wave * 4 + level.number) % w.length) ? 0.45 : 0;
            } else if (level.altStyle != null && wave % 2 == 0) {
                // abwechselnde Wellen: zweiter Stil an den geraden Wellen
                v = level.altStyle.early[i] + (level.altStyle.late[i] - level.altStyle.early[i]) * t;
            }
            if (FROM_CHAPTER[i] > chapter) {
                v = 0;
            }
            w[i] = v;
            sum += v;
        }
        if (sum <= 0) {
            w[0] = 1;
            sum = 1;
        }
        // Last der Standardwelle ohne Titanen
        WaveDef classic = WaveFactory.wave(wave);
        double budget = 0;
        for (WaveDef.Group g : classic.groups) {
            if (g.type != EnemyType.BOSS) {
                budget += (double) g.count * g.hp;
            }
        }
        boolean boss = bossCount(level, wave) > 0;
        if (boss) {
            budget *= 0.85;
        }
        double pace = (wave >= WaveFactory.ENDLESS_FROM ? 0.8 : 1.0) * style.pace * (1 - 0.1 * round);
        WaveDef d = new WaveDef().bonus(WaveFactory.bonus(wave));
        double delay = 0;
        double stagger = style == WaveStyle.ANSTURM ? 0.6 : 2.2;
        int total = 0;
        for (int i = 0; i < w.length; i++) {
            double share = w[i] / sum;
            if (share < 0.02) {
                continue;
            }
            EnemyType type = WaveStyle.TYPES[i];
            int baseHp = WaveFactory.hp(type, wave);
            int count = (int) Math.round(share * budget / (baseHp * WEIGHT[i]));
            count = Math.max(1, Math.min(CAP[i], count));
            if (total + count > 220) {
                count = Math.max(0, 220 - total);
            }
            if (count == 0) {
                continue;
            }
            total += count;
            // Der Abstand schrumpft mit dem Wellenverlauf wie bei der Standardkurve (aber nicht unter ein Minimum)
            double gap = Math.max(GAP[i] * 0.4, GAP[i] * (1.1 - 0.025 * Math.min(wave, 30)) * pace);
            d.groups.add(new WaveDef.Group(type, count, gap, delay, WaveFactory.clamped(baseHp * level.hpMul), -1,
                    WaveFactory.reward(type, wave)));
            delay += stagger;
        }
        if (boss) {
            int n = bossCount(level, wave);
            d.groups.add(new WaveDef.Group(EnemyType.BOSS, n, 6.0, delay + 1.0,
                    WaveFactory.clamped(WaveFactory.hp(EnemyType.BOSS, wave) * level.hpMul), -1,
                    WaveFactory.reward(EnemyType.BOSS, wave)));
        }
        return d;
    }

    /** Anzahl Titanen in der Welle: Welle 10 einer, Welle 20 zwei (wie bisher); Titanen-Level alle fünf Wellen. */
    static int bossCount(LevelDef level, int wave) {
        if (level.style == WaveStyle.TITANEN) {
            if (wave % 5 != 0) {
                return 0;
            }
            int base = wave <= 10 ? 1 : 2;
            return Math.min(5, base + (wave >= 20 ? level.number / 8 : 0));
        }
        if (wave % 10 != 0) {
            return 0;
        }
        return wave == 20 ? 2 : Math.min(5, 1 + wave / 30);
    }

    /** Alle Wellen 1 bis {@code count}. */
    public static List<WaveDef> build(LevelDef level, int count) {
        List<WaveDef> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(wave(level, i));
        }
        return list;
    }
}
