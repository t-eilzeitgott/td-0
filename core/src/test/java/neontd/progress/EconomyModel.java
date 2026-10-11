package neontd.progress;

import java.util.ArrayList;
import java.util.List;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.sim.Special;
import neontd.sim.TowerType;

/**
 * Messwerkzeug (kein Test): Wie lange braucht ein Spieler, der jeden Tag etwa 10 Minuten spielt, bis er im Shop alles
 * gekauft hat? Das Modell spielt Tag für Tag mit der echten {@link Progress}-Logik: je Tag {@code WAVES_PER_DAY}
 * Wellen (halb normal, halb Endlos), alle Aufgaben abholen, alle Season-Stufen abholen, Errungenschaften prüfen und
 * dann günstigste kaufbare Dinge kaufen.
 *
 * <pre>java -cp core/build/classes/java/main:core/build/classes/java/test neontd.progress.EconomyModel [wellenProTag]</pre>
 */
public final class EconomyModel {
    private EconomyModel() {
    }

    public static void main(String[] args) {
        int wavesPerDay = args.length > 0 ? Integer.parseInt(args[0]) : 40;
        simulate(wavesPerDay, true);
    }

    /** Spielt Tag für Tag und liefert den Tag (1-basiert), an dem alles gekauft ist, oder -1 nach 400 Tagen. */
    static int simulate(int wavesPerDay, boolean print) {
        Progress p = new Progress();
        p.grantLegacyCrystals();
        int day0 = Calendar.daysFromCivil(2026, 1, 1);
        List<LevelDef> levels = Levels.builtins();
        int nextLevel = 0;
        int doneDay = -1;
        double cumulative = 0;
        for (int d = 0; d < 400; d++) {
            p.refreshPeriods(day0 + d);
            double before = p.crystalsEarned;
            int avgWave = Math.min(150, 12 + (int) (0.7 * p.level()));
            for (int i = 0; i < wavesPerDay; i++) {
                int wave = Math.max(1, avgWave + (i % 7) - 3);
                boolean boss = wave % 10 == 0;
                boolean endless = i % 2 == 1;
                p.onWaveCleared(wave, boss, endless);
                p.addMetric(Metric.KILLS, 25 + wave / 2);
                if (boss) {
                    p.addMetric(Metric.BOSSES, 1);
                }
                if (wave >= 7) {
                    p.addMetric(Metric.ELITES, 4);
                }
            }
            p.addMetric(Metric.BUILDS, 22);
            p.addMetric(Metric.UPGRADES, 55);
            if (p.ownedSpecials.size() > 0) {
                p.addMetric(Metric.SPECIALS, 2);
            }
            // zwei Läufe; jeder zweite Tag ein neues Level mit Bronze (Sieg), danach Silber/Gold allmählich
            p.onPlayStarted();
            p.onPlayStarted();
            if (d % 2 == 0 && nextLevel < levels.size()) {
                String id = levels.get(nextLevel).id;
                int med0 = p.medal(id);
                p.onWon(id);
                p.onRecord(id, 20, false);
                p.onMedals(med0, p.medal(id));
                nextLevel++;
            }
            if (d % 5 == 1 && nextLevel > 3) {
                String id = levels.get(Math.max(0, nextLevel - 3)).id;
                int med0 = p.medal(id);
                p.onRecord(id, med0 < 2 ? 50 : med0 < 3 ? 100 : 250, true);
                p.onMedals(med0, p.medal(id));
            }
            for (int i = 0; i < Quests.DAILY; i++) {
                p.claimDaily(i);
            }
            for (int i = 0; i < Quests.MONTHLY; i++) {
                p.claimMonthly(i);
            }
            for (int t = 1; t <= Season.TIERS; t++) {
                p.claimSeason(t);
            }
            p.checkAchievements();
            buyAll(p);
            cumulative += p.crystalsEarned - before;
            if (print && (d % 15 == 14 || doneDay >= 0 && d == doneDay)) {
                System.out.printf("Tag %3d  Level %2d  Einnahmen/Tag %.0f  Guthaben %d  Türme %d/8  Spezials %d/16  Forschung %d/%d  Erfolge %d/%d%n",
                        d + 1, p.level(), (p.crystalsEarned - before), p.crystals(), p.ownedTowers.size(),
                        p.ownedSpecials.size(), perkLevels(p), totalPerkLevels(), done(p), Achievements.totalTiers());
            }
            if (doneDay < 0 && everything(p)) {
                doneDay = d;
                if (print) {
                    System.out.printf("ALLES FREIGESCHALTET an Tag %d (%.1f Monate), Einnahmen gesamt %.0f%n", d + 1,
                            (d + 1) / 30.0, cumulative);
                }
                break;
            }
        }
        if (print) {
            if (doneDay < 0) {
                System.out.printf("nach 400 Tagen noch nicht alles: Türme %d/8, Spezials %d/16, Forschung %d/%d%n",
                        p.ownedTowers.size(), p.ownedSpecials.size(), perkLevels(p), totalPerkLevels());
            }
            System.out.printf("Summe aller Preise: Türme %d, Spezials %d, Forschung %d%n", towerTotal(), specialTotal(),
                    perkTotal());
        }
        return doneDay < 0 ? -1 : doneDay + 1;
    }

    private static int done(Progress p) {
        int n = 0;
        for (Achievements.Family f : Achievements.FAMILIES) {
            n += p.achievementTiers(f);
        }
        return n;
    }

    private static boolean everything(Progress p) {
        return p.ownedTowers.size() == 8 && p.ownedSpecials.size() == Special.values().length
                && perkLevels(p) == totalPerkLevels();
    }

    private static int perkLevels(Progress p) {
        int n = 0;
        for (Perk k : Perk.values()) {
            n += p.perkLevel(k);
        }
        return n;
    }

    private static int totalPerkLevels() {
        int n = 0;
        for (Perk k : Perk.values()) {
            n += k.maxLevel;
        }
        return n;
    }

    private static int towerTotal() {
        int n = 0;
        for (TowerType t : TowerType.values()) {
            n += t.crystals;
        }
        return n;
    }

    private static int specialTotal() {
        int n = 0;
        for (Special s : Special.values()) {
            n += s.price;
        }
        return n;
    }

    private static int perkTotal() {
        int n = 0;
        for (Perk k : Perk.values()) {
            n += k.totalCost(k.maxLevel);
        }
        return n;
    }

    /** Kauft immer das günstigste, was Level und Guthaben erlauben, bis nichts mehr geht. */
    private static void buyAll(Progress p) {
        boolean again = true;
        while (again) {
            again = false;
            int best = Integer.MAX_VALUE;
            Object what = null;
            for (TowerType t : TowerType.values()) {
                if (t.tier == 2 && !p.ownedTowers.contains(t.name()) && p.level() >= t.unlockLevel && t.crystals < best) {
                    best = t.crystals;
                    what = t;
                }
            }
            for (Special s : Special.values()) {
                if (!p.owns(s) && p.level() >= s.needLevel && s.price < best) {
                    best = s.price;
                    what = s;
                }
            }
            for (Perk k : Perk.values()) {
                int lv = p.perkLevel(k);
                if (lv < k.maxLevel && p.level() >= k.needLevel && k.cost(lv) < best) {
                    best = k.cost(lv);
                    what = k;
                }
            }
            if (what != null && p.crystals() >= best) {
                if (what instanceof TowerType) {
                    p.buyTower((TowerType) what);
                } else if (what instanceof Special) {
                    p.buySpecial((Special) what);
                } else {
                    p.buyPerk((Perk) what);
                }
                again = true;
            }
        }
    }
}
