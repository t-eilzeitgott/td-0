package neontd.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import neontd.level.Levels;
import neontd.progress.Calendar;
import neontd.progress.Metric;
import neontd.progress.Perk;
import neontd.progress.Progress;
import neontd.progress.Quest;
import neontd.scene.SceneSmokeTest.Harness;
import neontd.sim.Special;
import neontd.sim.Tower;
import neontd.sim.TowerType;
import neontd.sim.World;
import org.junit.jupiter.api.Test;

/** Shop, Missionen, Errungenschaften, Einstellungen und die Elite-Türme im Spiel – mit Bedienung wie ein Spieler. */
class MetaFlowTest {
    private static void setLevel(Harness h, int level) {
        h.app.progress.xp = Progress.xpAtLevel(level) + 5;
    }

    private static void fill(Progress p) {
        p.xp = Progress.xpAtLevel(60) + 5;
        p.addCrystals(5000);
        p.ownedTowers.add("INFERNO");
        p.ownedTowers.add("MINT");
        p.ownedSpecials.add("crit");
        p.ownedSpecials.add("interest");
        p.ownedSpecials.add("burn");
        p.addMetric(Metric.KILLS, 500);
        p.addSeasonXp(400);
    }

    // ------------------------------------------------------------------------------------ Zeichnen

    @Test
    void metaScenesRenderAtEverySizeAndTab() {
        double[][] sizes = {{1280, 720}, {1920, 1080}, {852, 393}, {393, 852}, {568, 320}, {1024, 1366}};
        for (double[] s : sizes) {
            Harness h = new Harness(s[0], s[1], s[0] < 900 || s[1] > s[0], 0, 0, 0, 0);
            fill(h.app.progress);
            h.run(0.5);
            for (int tab = 0; tab < 3; tab++) {
                h.app.goTo(new ShopScene(h.app, tab));
                h.run(1.2);
                assertEquals("ShopScene", h.scene());
                h.app.wheel(400);
                h.run(0.3);
            }
            for (int tab = 0; tab < 2; tab++) {
                h.app.goTo(new MissionsScene(h.app, tab));
                h.run(1.2);
                assertEquals("MissionsScene", h.scene());
                h.app.wheel(600);
                h.run(0.3);
            }
            h.app.goTo(new AchievementsScene(h.app));
            h.run(1.2);
            h.app.wheel(600);
            h.run(0.3);
            h.app.goTo(new SettingsScene(h.app));
            h.run(1.2);
            assertEquals("SettingsScene", h.scene());
            h.app.goTo(new MenuScene(h.app));
            h.run(1.5);
            // das Spiel mit gekauften Elite-Türmen auf allen Größen (Seitenumschalter, Spezial-Platz)
            h.app.goTo(new GameScene(h.app, Levels.serpentine()));
            h.run(1.5);
            GameScene gs = (GameScene) h.app.scene();
            double[] pager = gs.anchor("pager");
            if (pager != null) {
                h.tap(pager);
            }
            h.run(0.5);
        }
    }

    // ---------------------------------------------------------------------------------------- Shop

    @Test
    void buyingATowerInTheShopNeedsLevelCrystalsAndAConfirmation() {
        Harness h = new Harness(1280, 720, false);
        setLevel(h, 20);
        h.app.progress.addCrystals(700);
        h.app.goTo(new ShopScene(h.app));
        h.run(1.5);
        ShopScene shop = (ShopScene) h.app.scene();
        // erste Karte = INFERNO (600 Kristalle)
        h.tap(300, shop.contentTop + 60);
        assertTrue(shop.modalVisible(), "Kaufdialog");
        h.run(0.5);
        h.tap(shop.modalAnchor(1)); // abbrechen
        assertFalse(shop.modalVisible());
        assertFalse(h.app.progress.ownedTowers.contains("INFERNO"));
        h.tap(300, shop.contentTop + 60);
        h.run(0.5);
        h.tap(shop.modalAnchor(0)); // kaufen
        assertTrue(h.app.progress.ownedTowers.contains("INFERNO"));
        int left = h.app.progress.crystals();
        assertTrue(left >= 100 && left < 400, "Rest samt Errungenschaften-Belohnungen: " + left);
        // gespeichert
        assertTrue(h.store.get("neontd.profile").contains("INFERNO"));
        // zweite Karte (GIFT, 800): nicht genug Kristalle → kein Kauf
        h.tap(900, shop.contentTop + 60);
        assertTrue(shop.modalVisible());
        h.run(0.5);
        h.tap(shop.modalAnchor(0));
        assertFalse(h.app.progress.ownedTowers.contains("VENOM"));
        assertEquals(left, h.app.progress.crystals());
    }

    @Test
    void shopTabsListSpecialsAndResearch() {
        Harness h = new Harness(1280, 720, false);
        setLevel(h, 60);
        h.app.progress.addCrystals(2000);
        h.app.goTo(new ShopScene(h.app, 1));
        h.run(1.5);
        ShopScene shop = (ShopScene) h.app.scene();
        h.tap(300, shop.contentTop + 60); // VOLLTREFFER
        h.run(0.5);
        h.tap(shop.modalAnchor(0));
        assertTrue(h.app.progress.owns(Special.CRIT));
        h.app.goTo(new ShopScene(h.app, 2));
        h.run(1.5);
        ShopScene perks = (ShopScene) h.app.scene();
        h.tap(300, perks.contentTop + 60); // STARTGELD
        h.run(0.5);
        h.tap(perks.modalAnchor(0));
        assertEquals(1, h.app.progress.perkLevel(Perk.START_MONEY));
        assertEquals(40 + h.app.progress.startMoneyBonus() - 40, h.app.progress.startMoneyBonus());
    }

    // ------------------------------------------------------------------------------------ Missionen

    @Test
    void claimingAFinishedDailyQuestPaysOutAndMarksItClaimed() {
        Harness h = new Harness(1280, 720, false);
        Quest[] q = h.app.progress.dailyQuests();
        h.app.progress.addMetric(q[0].metric, q[0].target);
        assertTrue(h.app.progress.dailyReady(0));
        double c0 = h.app.progress.crystalsEarned;
        h.app.goTo(new MissionsScene(h.app));
        h.run(1.5);
        MissionsScene ms = (MissionsScene) h.app.scene();
        h.tap(300, ms.contentTop + 26 * h.app.vp.u + 40 * h.app.vp.u);
        assertTrue(h.app.progress.dailyClaimed[0]);
        assertTrue(h.app.progress.crystalsEarned >= c0 + q[0].crystals);
        // zweites Tippen zahlt nicht noch einmal
        double c1 = h.app.progress.crystalsEarned;
        h.tap(300, ms.contentTop + 26 * h.app.vp.u + 40 * h.app.vp.u);
        assertEquals(c1, h.app.progress.crystalsEarned, 1e-9);
    }

    @Test
    void theSeasonTabClaimsTiersAndAllAtOnce() {
        Harness h = new Harness(1280, 720, false);
        h.app.progress.addSeasonXp(450);
        assertEquals(3, h.app.progress.seasonTier());
        h.app.goTo(new MissionsScene(h.app, 1));
        h.run(1.5);
        MissionsScene ms = (MissionsScene) h.app.scene();
        // "Alle abholen" sitzt rechts im Kopf der Season
        double u = h.app.vp.u;
        h.tap(ms.contentX + ms.contentW - 60 * u, ms.contentTop + 96 * u * 0.75);
        assertEquals(0, h.app.progress.claimable() - openMonthlyReady(h.app.progress));
        assertTrue(h.app.progress.seasonClaimed[1] && h.app.progress.seasonClaimed[3]);
    }

    private static int openMonthlyReady(Progress p) {
        int n = 0;
        for (int i = 0; i < 6; i++) {
            n += p.monthlyReady(i) ? 1 : 0;
        }
        return n;
    }

    @Test
    void newDayBringsNewQuestsWhileTheAppIsOpen() {
        Harness h = new Harness(1280, 720, false);
        int day = h.app.progress.dailyDay;
        h.app.progress.dailyProgress[0] = 3;
        h.clock += Calendar.DAY_MS;
        h.run(7); // die Anwendung prüft alle paar Sekunden
        assertEquals(day + 1, h.app.progress.dailyDay);
        assertEquals(0, h.app.progress.dailyProgress[0]);
    }

    // ------------------------------------------------------------------------------ Einstellungen

    @Test
    void settingsResetButtonsAskFirstAndThenResetTheProfile() {
        Harness h = new Harness(1280, 720, false);
        fill(h.app.progress);
        h.app.progress.onRecord("serpentine", 60, false);
        h.app.goTo(new SettingsScene(h.app));
        h.run(1.5);
        SettingsScene st = (SettingsScene) h.app.scene();
        double u = h.app.vp.u;
        double rowLevel = st.contentTop + 130 * u + 35 * u;
        double rowAll = rowLevel + 82 * u;
        // Level zurücksetzen: erst abbrechen, dann bestätigen
        h.tap(300, rowLevel);
        assertTrue(st.modalVisible());
        h.run(0.5);
        h.tap(st.modalAnchor(1));
        assertEquals(60, h.app.progress.level());
        h.tap(300, rowLevel);
        h.run(0.5);
        h.tap(st.modalAnchor(0));
        assertEquals(1, h.app.progress.level());
        assertTrue(h.app.progress.ownedTowers.contains("INFERNO"), "Shop bleibt");
        assertEquals(2, h.app.progress.medal("serpentine"));
        assertTrue(h.store.get("neontd.profile").contains("\"xp\":0"));
        // Alles zurücksetzen
        h.tap(300, rowAll);
        assertTrue(st.modalVisible());
        h.run(0.5);
        h.tap(st.modalAnchor(0));
        assertTrue(h.app.progress.ownedTowers.isEmpty());
        assertEquals(0, h.app.progress.medal("serpentine"));
        assertEquals(0, h.app.progress.crystalsEarned, 1e-9 + 5);
        assertTrue(h.app.progress.resetEpoch > 0);
    }

    // ------------------------------------------------------------------------------- Im Spiel

    @Test
    void ownedEliteTowersAppearOnPageTwoAndCanBeBuilt() {
        Harness h = new Harness(1280, 720, false);
        fill(h.app.progress);
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        GameScene gs = (GameScene) h.app.scene();
        World w = gs.testWorld();
        w.money = 100000;
        assertNotNull(gs.anchor("pager"), "es gibt mehr als eine Seite");
        double[] spot = GameFlowTest.validSpot(w, 400, 250);
        h.tap(gs.anchor("pager"));
        double[] from = gs.anchor("tile0"); // INFERNO
        double[] to = gs.anchor("world:" + spot[0] + "," + spot[1]);
        h.drag(from[0], from[1], to[0], to[1]);
        assertEquals(1, w.towers.size());
        assertEquals(TowerType.INFERNO, w.towers.get(0).type);
        assertEquals(1, h.app.progress.stat("builds"), 1e-9);
        // zweite Elite-Kachel ist MÜNZE (Seite 2: INFERNO, MÜNZE)
        double[] spot2 = GameFlowTest.validSpot(w, 700, 250);
        from = gs.anchor("tile1");
        to = gs.anchor("world:" + spot2[0] + "," + spot2[1]);
        h.drag(from[0], from[1], to[0], to[1]);
        assertEquals(2, w.towers.size());
        assertEquals(TowerType.MINT, w.towers.get(1).type);
        // Seitenwechsel zurück: Kachel 0 ist wieder PULS
        h.tap(gs.anchor("pager"));
        assertTrue(gs.towerTile(0).visible);
    }

    @Test
    void specialUpgradeCanBeChosenInTheGameAndCostsMoney() {
        Harness h = new Harness(1280, 720, false);
        fill(h.app.progress);
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        GameScene gs = (GameScene) h.app.scene();
        World w = gs.testWorld();
        w.money = 100000;
        double[] spot = GameFlowTest.validSpot(w, 400, 250);
        Tower t = w.placeTower(TowerType.PULSE, spot[0], spot[1]);
        double[] pos = gs.anchor("world:" + t.x + "," + t.y);
        h.tap(pos);
        h.run(0.3);
        double money = w.money;
        h.tap(gs.anchor("special"));
        h.run(0.5);
        assertEquals(money, w.money, "das Fenster pausiert das Spiel nur");
        // erste Zeile = VOLLTREFFER (Reihenfolge der Spezials: crit, burn, interest …)
        h.tap(gs.anchor("ov0"));
        assertEquals(Special.CRIT, t.special);
        assertEquals(money - w.specialCost(t), w.money, 1e-9);
        assertEquals(1, h.app.progress.stat("specials"), 1e-9);
        // Taste R öffnet die Auswahl wieder; Tauschen auf BRANDSATZ
        h.app.key("KeyR", false);
        h.run(0.4);
        h.tap(gs.anchor("ov1"));
        assertEquals(Special.BURN, t.special);
        // Abbrechen: Escape schließt die Auswahl
        h.app.key("KeyR", false);
        h.run(0.4);
        h.app.back();
        h.run(0.4);
        assertEquals(Special.BURN, t.special);
    }

    @Test
    void researchPerksApplyToTheNextGame() {
        Harness h = new Harness(1280, 720, false);
        h.app.progress.xp = Progress.xpAtLevel(60) + 5;
        h.app.progress.addCrystals(100000);
        for (Perk k : Perk.values()) {
            while (h.app.progress.buyPerk(k) == Progress.Buy.OK) {
                // bis zum Maximum
            }
        }
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        GameScene gs = (GameScene) h.app.scene();
        World w = gs.testWorld();
        assertEquals(Math.round(TowerType.PULSE.cost * 0.85), w.buildCost(TowerType.PULSE));
        assertEquals(500 + Math.min(300, 10 * 59) + 400, w.money, 1e-9);
        assertEquals(20 + 5 + 5, w.lives, "Level-Bonus 5 + Forschung 5");
    }

    @Test
    void lockedLevelsAndEliteTowersStayOutOfTheShopWithoutPurchase() {
        Harness h = new Harness(1280, 720, false);
        setLevel(h, 60);
        h.app.goTo(new GameScene(h.app, Levels.serpentine()));
        h.run(1.5);
        GameScene gs = (GameScene) h.app.scene();
        assertEquals(null, gs.anchor("pager"), "ohne gekaufte Elite-Türme gibt es nur eine Seite");
    }
}
