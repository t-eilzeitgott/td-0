package neontd.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.save.ProfileJson;
import neontd.sim.Special;
import neontd.sim.TowerType;
import org.junit.jupiter.api.Test;

/** Kristalle, Shop, Aufgaben, Season, Errungenschaften, Zurücksetzen und das Tempo der Freischaltungen. */
class ProgressionTest {
    private static Progress rich(double crystals, int level) {
        Progress p = new Progress();
        p.xp = Progress.xpAtLevel(level) + 1;
        p.addCrystals(crystals);
        return p;
    }

    // ---------------------------------------------------------------------------------------- Shop

    @Test
    void crystalsAreEarnedMinusSpentAndNeverNegative() {
        Progress p = rich(1000, 20);
        assertEquals(1000, p.crystals());
        assertEquals(Progress.Buy.OK, p.buyTower(TowerType.INFERNO));
        assertEquals(1000 - TowerType.INFERNO.crystals, p.crystals());
        assertEquals(Progress.Buy.OWNED, p.buyTower(TowerType.INFERNO));
        assertTrue(p.isUnlocked(TowerType.INFERNO));
        assertFalse(p.isUnlocked(TowerType.VENOM));
        assertEquals(Progress.Buy.LEVEL, p.buyTower(TowerType.ORBITAL), "Level 55 nötig");
        assertEquals(Progress.Buy.INVALID, p.buyTower(TowerType.PULSE));
        Progress poor = rich(10, 60);
        assertEquals(Progress.Buy.FUNDS, poor.buyTower(TowerType.ORBITAL));
        assertEquals(10, poor.crystals());
    }

    @Test
    void specialsAndPerksCostCrystalsAndRespectLevelAndMax() {
        Progress p = rich(100000, 60);
        for (Special s : Special.values()) {
            assertEquals(Progress.Buy.OK, p.buySpecial(s), s.name());
            assertTrue(p.owns(s));
            assertEquals(Progress.Buy.OWNED, p.buySpecial(s));
        }
        double spent = p.spentCrystals();
        for (Perk k : Perk.values()) {
            for (int l = 0; l < k.maxLevel; l++) {
                assertEquals(Progress.Buy.OK, p.buyPerk(k), k.name() + " " + l);
            }
            assertEquals(Progress.Buy.MAX, p.buyPerk(k));
            assertEquals(k.maxLevel, p.perkLevel(k));
        }
        assertTrue(p.spentCrystals() > spent);
        assertEquals(100000 - (int) p.spentCrystals(), p.crystals());
        Progress low = rich(100000, 3);
        assertEquals(Progress.Buy.LEVEL, low.buySpecial(Special.AURA));
        assertEquals(Progress.Buy.LEVEL, low.buyPerk(Perk.DAMAGE));
    }

    @Test
    void perksTurnIntoWorldBonuses() {
        Progress p = rich(100000, 60);
        for (Perk k : Perk.values()) {
            while (p.buyPerk(k) == Progress.Buy.OK) {
                // bis zum Maximum
            }
        }
        neontd.sim.Perks w = p.perks();
        assertEquals(1.10, w.damage, 1e-9);
        assertEquals(1.08, w.range, 1e-9);
        assertEquals(0.85, w.buildCost, 1e-9);
        assertEquals(0.09, w.sell, 1e-9);
        assertEquals(1.20, w.bounty, 1e-9);
        assertEquals(1.30, w.waveBonus, 1e-9);
        assertEquals(Math.min(300, 10 * 59) + 400, p.startMoneyBonus());
        assertEquals(5 + 5, p.startLivesBonus());
    }

    // ------------------------------------------------------------------------------------ Einnahmen

    @Test
    void wavesWinsMedalsAndLevelUpsPayCrystals() {
        Progress p = new Progress();
        double c0 = p.crystalsEarned;
        p.onWaveCleared(10, false);
        assertEquals(1, p.crystalsEarned - c0, "1 + Welle/25");
        p.onWaveCleared(30, true);
        assertEquals(1 + 5, p.crystalsEarned - c0, "Welle 30 mit Titan: 1 + 1 + 3");
        double c1 = p.crystalsEarned;
        int l1 = p.level();
        p.onWon("a");
        assertEquals(Progress.WIN_CRYSTALS_FIRST, p.crystalsEarned - c1 - Progress.LEVEL_UP_CRYSTALS * (p.level() - l1),
                0.01);
        double c2 = p.crystalsEarned;
        int l2 = p.level();
        p.onWon("a");
        assertEquals(Progress.WIN_CRYSTALS, p.crystalsEarned - c2 - Progress.LEVEL_UP_CRYSTALS * (p.level() - l2),
                0.01);
        assertEquals(6 + 14, p.onMedals(0, 2));
        assertEquals(70, p.onMedals(3, 4));
        // Level-Aufstieg: 5 Kristalle je Stufe
        Progress q = new Progress();
        int before = q.level();
        double cq = q.crystalsEarned;
        q.addXp(Progress.xpForNext(1));
        assertEquals(before + 1, q.level());
        assertEquals(Progress.LEVEL_UP_CRYSTALS, q.crystalsEarned - cq, 0.001);
    }

    @Test
    void crystalPerkRaisesGameIncomeButNotQuestRewards() {
        Progress p = rich(100000, 60);
        for (int i = 0; i < 10; i++) {
            p.buyPerk(Perk.CRYSTALS);
        }
        double c0 = p.crystalsEarned;
        assertEquals(Math.round(10 * 1.3), p.earnCrystals(10));
        assertEquals(c0 + 13, p.crystalsEarned, 1e-9);
        assertEquals(5, p.addCrystals(5));
    }

    @Test
    void legacyProfilesGetStartingCrystalsOnce() {
        Progress p = new Progress();
        p.xp = Progress.xpAtLevel(10);
        p.won.add("serpentine");
        p.onRecord("serpentine", 55, false);
        assertTrue(p.grantLegacyCrystals());
        double expected = 5 * 9 + 10 + 6 + 14;
        assertEquals(expected, p.crystalsEarned, 1e-9);
        assertFalse(p.grantLegacyCrystals(), "nur einmal");
        assertEquals(expected, p.crystalsEarned, 1e-9);
    }

    // ---------------------------------------------------------------------------------------- Aufgaben

    @Test
    void dailyQuestsAreDeterministicDifferentPerDayAndPayWhenClaimed() {
        int day = Calendar.daysFromCivil(2026, 3, 14);
        Quest[] a = Quests.daily(day);
        Quest[] b = Quests.daily(day);
        assertEquals(3, a.length);
        for (int i = 0; i < 3; i++) {
            assertEquals(a[i].id, b[i].id);
            assertTrue(a[i].target > 0 && a[i].crystals > 0);
        }
        boolean differs = false;
        for (int d = 1; d < 30; d++) {
            Quest[] o = Quests.daily(day + d);
            differs |= !o[0].id.equals(a[0].id) || !o[2].id.equals(a[2].id);
            assertTrue(o[0].metric != o[1].metric && o[1].metric != o[2].metric && o[0].metric != o[2].metric,
                    "drei verschiedene Aufgaben am Tag " + d);
        }
        assertTrue(differs);

        Progress p = new Progress();
        p.refreshPeriods(day);
        assertEquals(0, p.claimDaily(0), "noch nichts geschafft");
        for (int i = 0; i < 3; i++) {
            p.addMetric(a[i].metric, a[i].target);
            // Aufgaben mit derselben Metrik (WAVES zählt über onWaveCleared) sind hier direkt erfüllt
        }
        double c0 = p.crystalsEarned;
        double sxp = p.seasonXp;
        int got = 0;
        for (int i = 0; i < 3; i++) {
            assertTrue(p.dailyReady(i), "Aufgabe " + i);
            got += p.claimDaily(i);
            assertFalse(p.dailyReady(i));
            assertEquals(0, p.claimDaily(i), "nur einmal");
        }
        assertEquals(a[0].crystals + a[1].crystals + a[2].crystals + Quests.DAILY_BONUS_CRYSTALS, got);
        assertEquals(c0 + got, p.crystalsEarned, 5 + 1e-9 * 0 + 100, "Level-Aufstiege durch Aufgaben-XP dürfen dazukommen");
        assertTrue(p.seasonXp >= sxp + 3 * 40 + Quests.DAILY_BONUS_SEASON_XP);
        assertTrue(p.dailyBonusClaimed);
    }

    @Test
    void newDayResetsDailiesAndNewMonthStartsANewSeason() {
        Progress p = new Progress();
        int day = Calendar.daysFromCivil(2026, 1, 31);
        assertTrue(p.refreshPeriods(day));
        assertEquals(Calendar.monthId(day), p.seasonId);
        p.addSeasonXp(400);
        p.claimSeason(1);
        p.addMetric(Metric.KILLS, 99999);
        assertFalse(p.refreshPeriods(day), "derselbe Tag ändert nichts");
        assertTrue(p.refreshPeriods(day + 1), "neuer Tag und neuer Monat");
        assertEquals(0, p.seasonXp);
        assertFalse(p.seasonClaimed[1]);
        assertEquals(0, p.dailyProgress[0]);
        assertEquals(0, p.monthlyProgress[0]);
        // Rückwärtsspringende Uhr: nichts geht verloren
        p.addSeasonXp(100);
        assertFalse(p.refreshPeriods(day));
        assertEquals(100, p.seasonXp);
    }

    @Test
    void monthlyQuestsDifferByMonthAndCountSixDistinctMetrics() {
        int m = Calendar.monthId(Calendar.daysFromCivil(2026, 5, 3));
        Quest[] q = Quests.monthly(m);
        assertEquals(6, q.length);
        java.util.Set<Metric> seen = new java.util.HashSet<>();
        for (Quest x : q) {
            assertTrue(seen.add(x.metric), "jede Metrik nur einmal: " + x.metric);
            assertTrue(x.target >= 5 && x.crystals >= 100 && x.seasonXp > 0);
        }
        Quest[] other = Quests.monthly(m + 1);
        boolean differs = false;
        for (int i = 0; i < 6; i++) {
            differs |= !q[i].id.equals(other[i].id);
        }
        assertTrue(differs);
    }

    @Test
    void seasonTiersUnlockWithXpAndPayOnce() {
        Progress p = new Progress();
        p.refreshPeriods(Calendar.daysFromCivil(2026, 6, 1));
        assertEquals(0, p.seasonTier());
        assertFalse(p.seasonReady(1));
        p.addSeasonXp(Season.XP_PER_TIER * 3 + 20);
        assertEquals(3, p.seasonTier());
        assertEquals(0, p.claimSeason(4), "Stufe 4 noch nicht erreicht");
        int got = p.claimSeason(1) + p.claimSeason(2) + p.claimSeason(3);
        assertEquals(Season.crystals(1) + Season.crystals(2) + Season.crystals(3), got);
        assertEquals(0, p.claimSeason(1));
        assertEquals(0, p.claimable());
        p.addSeasonXp(Season.MAX_XP * 2);
        assertEquals(Season.TIERS, p.seasonTier());
        assertEquals(Season.TIERS - 3, p.claimable());
        assertEquals(null, p.seasonBadge());
        for (int t = 4; t <= 10; t++) {
            p.claimSeason(t);
        }
        assertEquals("SEASON-STARTER", p.seasonBadge(), "Stufe 10 bringt den Titel");
        assertEquals("SEASON-STARTER", Season.milestone(10));
        assertEquals("SEASON-MEISTER", Season.milestone(30));
        assertTrue(Season.totalCrystals() > 2500 && Season.totalCrystals() < 4500, "Season: " + Season.totalCrystals());
    }

    // ---------------------------------------------------------------------------- Errungenschaften

    @Test
    void achievementsUnlockOnceAndPayTheirCrystals() {
        Progress p = new Progress();
        assertTrue(p.checkAchievements().isEmpty());
        p.addMetric(Metric.KILLS, 120);
        double c0 = p.crystalsEarned;
        java.util.List<String> got = p.checkAchievements();
        assertEquals(1, got.size(), got.toString());
        assertTrue(got.get(0).startsWith("ABSCHUSS-SERIE"));
        assertEquals(10, p.crystalsEarned - c0, 1e-9);
        assertTrue(p.checkAchievements().isEmpty(), "nicht doppelt");
        p.addMetric(Metric.KILLS, 1000);
        assertEquals(1, p.checkAchievements().size(), "Stufe 2");
        assertEquals(2, p.achievementTiers(Achievements.FAMILIES[0]));
    }

    @Test
    void achievementDefinitionsAreConsistent() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Achievements.Family f : Achievements.FAMILIES) {
            assertEquals(f.targets.length, f.rewards.length);
            for (int t = 0; t < f.tiers(); t++) {
                assertTrue(ids.add(f.id(t)), "doppelte Kennung " + f.id(t));
                assertTrue(f.rewards[t] > 0 && f.targets[t] > 0);
                if (t > 0) {
                    assertTrue(f.targets[t] > f.targets[t - 1] && f.rewards[t] > f.rewards[t - 1], f.name);
                }
                assertFalse(f.describe(t).contains("{n}"));
            }
        }
        assertTrue(Achievements.totalTiers() >= 60);
        assertTrue(Achievements.totalCrystals() > 3000);
    }

    // ------------------------------------------------------------------------------ Zurücksetzen

    @Test
    void resetPlayerLevelKeepsEverythingElseAndResetAllWipesTheProfile() {
        Progress p = rich(1000, 30);
        p.name = "Anna";
        p.buyTower(TowerType.INFERNO);
        p.onRecord("serpentine", 60, false);
        p.won.add("serpentine");
        p.resetPlayerLevel(1000);
        assertEquals(1, p.level());
        assertEquals(0, p.xp);
        assertTrue(p.ownedTowers.contains("INFERNO"));
        assertEquals(2, p.medal("serpentine"));
        assertEquals(1000, p.resetEpoch);

        p.resetAll(2000);
        assertEquals("Anna", p.name, "der Name bleibt");
        assertEquals(0, p.medal("serpentine"));
        assertTrue(p.ownedTowers.isEmpty());
        assertEquals(0, p.crystalsEarned);
        assertEquals(2000, p.resetEpoch);
        assertTrue(p.won.isEmpty());
    }

    @Test
    void aResetWinsTheCloudMergeInsteadOfBeingUndone() {
        Progress old = rich(500, 30);
        old.onRecord("serpentine", 60, false);
        old.updated = 100;
        Progress reset = new Progress();
        reset.copyFrom(old);
        reset.resetAll(5000);
        reset.updated = 5000;
        Progress merged = ProfileJson.merge(old, reset);
        assertEquals(0, merged.medal("serpentine"));
        assertEquals(1, merged.level());
        assertEquals(5000, merged.resetEpoch);
        Progress merged2 = ProfileJson.merge(reset, old);
        assertEquals(0, merged2.medal("serpentine"));
        // Ein Spiel auf dem anderen Gerät nach dem Zurücksetzen (gleiche Epoche) wird dagegen normal vereinigt
        Progress other = new Progress();
        other.copyFrom(reset);
        other.onRecord("zickzack", 25, false);
        Progress m3 = ProfileJson.merge(reset, other);
        assertEquals(1, m3.medal("zickzack"));
    }

    // ----------------------------------------------------------------------- Speichern und Zusammenführen

    @Test
    void metaStateRoundTripsThroughJson() {
        Progress p = rich(3000, 40);
        p.buyTower(TowerType.INFERNO);
        p.buySpecial(Special.CRIT);
        p.buyPerk(Perk.DAMAGE);
        p.refreshPeriods(Calendar.daysFromCivil(2026, 2, 10));
        p.addMetric(Metric.KILLS, 500);
        p.addMetric(Metric.BOSSES, 7);
        p.addSeasonXp(333);
        p.claimSeason(1);
        p.checkAchievements();
        Progress q = ProfileJson.fromMap(neontd.save.Json.asMap(neontd.save.Json.tryParse(
                neontd.save.Json.write(ProfileJson.toMap(p)))));
        assertTrue(ProfileJson.sameAs(p, q));
        assertEquals(p.crystals(), q.crystals());
        assertTrue(q.ownedTowers.contains("INFERNO") && q.owns(Special.CRIT));
        assertEquals(1, q.perkLevel(Perk.DAMAGE));
        assertEquals(333, q.seasonXp);
        assertTrue(q.seasonClaimed[1]);
        assertEquals(7, q.stat("bosses"), 1e-9);
        assertEquals(p.achievements, q.achievements);
        assertEquals(p.dailyDay, q.dailyDay);
    }

    @Test
    void mergeUnitesPurchasesAndKeepsTheBalanceNonNegative() {
        Progress a = rich(5000, 40);
        Progress b = new Progress();
        b.copyFrom(a);
        a.buyTower(TowerType.INFERNO);
        b.buyTower(TowerType.VENOM);
        b.buySpecial(Special.CRIT);
        a.perkLevels.put("damage", 2);
        b.perkLevels.put("damage", 3);
        Progress m = ProfileJson.merge(a, b);
        assertTrue(m.ownedTowers.contains("INFERNO") && m.ownedTowers.contains("VENOM"));
        assertTrue(m.owns(Special.CRIT));
        assertEquals(3, m.perkLevel(Perk.DAMAGE));
        assertTrue(m.crystals() >= 0);
        assertTrue(m.crystalsEarned >= m.spentCrystals());
    }

    @Test
    void mergeTakesTheNewerDayAndSameDayProgressMaximum() {
        int day = Calendar.daysFromCivil(2026, 4, 10);
        Progress a = new Progress();
        Progress b = new Progress();
        a.refreshPeriods(day);
        b.refreshPeriods(day);
        a.dailyProgress[0] = 5;
        b.dailyProgress[0] = 9;
        b.dailyClaimed[1] = true;
        Progress m = ProfileJson.merge(a, b);
        assertEquals(9, m.dailyProgress[0]);
        assertTrue(m.dailyClaimed[1]);
        Progress c = new Progress();
        c.refreshPeriods(day + 1);
        Progress m2 = ProfileJson.merge(a, c);
        assertEquals(day + 1, m2.dailyDay);
        assertEquals(0, m2.dailyProgress[0]);
    }

    // ----------------------------------------------------------------------------------- Kalender

    @Test
    void calendarMathMatchesKnownDates() {
        assertEquals(0, Calendar.daysFromCivil(1970, 1, 1));
        assertEquals(19723, Calendar.daysFromCivil(2024, 1, 1));
        int[] c = Calendar.civil(Calendar.daysFromCivil(2026, 10, 11));
        assertEquals(2026, c[0]);
        assertEquals(10, c[1]);
        assertEquals(11, c[2]);
        assertEquals(Calendar.monthId(Calendar.daysFromCivil(2026, 12, 31)) + 1,
                Calendar.monthId(Calendar.daysFromCivil(2027, 1, 1)));
        assertEquals(31, Calendar.daysLeftInMonth(Calendar.daysFromCivil(2026, 1, 1)));
        assertEquals(1, Calendar.daysLeftInMonth(Calendar.daysFromCivil(2026, 2, 28)));
        assertEquals(29, Calendar.daysLeftInMonth(Calendar.daysFromCivil(2024, 2, 1)));
        assertEquals("OKTOBER", Calendar.monthName(Calendar.monthId(Calendar.daysFromCivil(2026, 10, 11))));
        // lokale Mitternacht: UTC+2 verschiebt den Tageswechsel
        assertEquals(1, Calendar.dayOf(Calendar.DAY_MS - 1, 3_600_000.0 * 2));
        assertEquals(0, Calendar.dayOf(Calendar.DAY_MS - 1, 0));
    }

    // ------------------------------------------------------------------------------ Wirtschaft

    @Test
    void fullUnlockTakesMonthsAtAboutTenMinutesAPlayDay() {
        // 40 Wellen ≈ 10 Minuten bei 2–3-fachem Tempo
        int days = EconomyModel.simulate(40, false);
        assertTrue(days >= 75 && days <= 200, "alles freigeschaltet nach " + days + " Tagen");
        int casual = EconomyModel.simulate(25, false);
        assertTrue(casual > days, "wer weniger spielt, braucht länger");
        int intense = EconomyModel.simulate(80, false);
        assertTrue(intense > 45 && intense < days, "auch viel Spielen dauert noch Wochen: " + intense);
    }
}
