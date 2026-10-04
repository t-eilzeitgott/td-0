package neontd.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.level.LevelDef;
import neontd.level.LevelStore;
import neontd.level.Levels;
import neontd.platform.MemoryStore;
import neontd.progress.Progress;
import neontd.sim.AutoPlayer;
import neontd.sim.Enemy;
import neontd.sim.SimListener;
import neontd.sim.Tower;
import neontd.sim.World;
import neontd.sim.WorldSnapshot;
import org.junit.jupiter.api.Test;

class SaveTest {
    // ------------------------------------------------------------------------------------------- Json

    @Test
    void jsonRoundTripsValuesAndEscapes() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", "Zeile 1\nZeile \"2\"\t\\ äöü € 😀 \u0001");
        m.put("int", 42);
        m.put("neg", -7);
        m.put("frac", 0.1 + 0.2);
        m.put("big", 4_000_000_000.0);
        m.put("flag", true);
        m.put("nothing", null);
        List<Object> l = new ArrayList<>();
        l.add(1);
        l.add("x");
        l.add(new ArrayList<Object>());
        m.put("list", l);
        String text = Json.write(m);
        Map<String, Object> back = Json.asMap(Json.parse(text));
        assertEquals(m.get("text"), back.get("text"));
        assertEquals(42, Json.integer(back, "int", 0));
        assertEquals(-7, Json.integer(back, "neg", 0));
        assertEquals(0.1 + 0.2, Json.num(back, "frac", 0), 0.0);
        assertEquals(4_000_000_000.0, Json.num(back, "big", 0), 0.0);
        assertTrue(Json.bool(back, "flag", false));
        assertTrue(back.containsKey("nothing"));
        assertEquals(3, Json.list(back, "list").size());
        assertEquals(text, Json.write(back), "stabile Ausgabe");
        assertTrue(text.contains("4000000000"), text);
    }

    @Test
    void jsonRejectsGarbage() {
        for (String bad : new String[] {"", "{", "[1,", "{\"a\":}", "{a:1}", "[1 2]", "\"abc", "nul", "1 2", "{\"a\":1,}",
            "\"\\u12\""}) {
            assertNull(Json.tryParse(bad), "sollte ungültig sein: " + bad);
        }
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{"));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            deep.append('[');
        }
        assertNull(Json.tryParse(deep.toString()), "zu tief");
    }

    @Test
    void jsonParsesWhitespaceAndUnicodeEscapes() {
        Map<String, Object> m = Json.asMap(Json.parse(" \n{ \"a\" : [ 1 , 2.5e1 , \"\\u00e4\\n\" ] , \"b\":{ } }\t"));
        assertEquals(25.0, Json.at(Json.list(m, "a"), 1), 0.0);
        assertEquals("ä\n", Json.list(m, "a").get(2));
        assertTrue(Json.map(m, "b").isEmpty());
        assertEquals("fallback", Json.str(m, "missing", "fallback"));
    }

    @Test
    void base64RoundTripsAndRejectsGarbage() {
        for (String s : new String[] {"", "a", "ab", "abc", "abcd", "Größe €uro 😀 {\"x\":1}"}) {
            assertEquals(s, Base64Lite.decode(Base64Lite.encode(s)));
        }
        assertNull(Base64Lite.decode("***"));
        assertNull(Base64Lite.decode("A"));
    }

    // ---------------------------------------------------------------------------------------- Profil

    @Test
    void profileSurvivesLocalStoreAndIgnoresDamage() {
        MemoryStore kv = new MemoryStore();
        SaveStore store = new SaveStore(kv, () -> 1000);
        Progress p = new Progress();
        p.name = "Tester";
        p.addXp(5000);
        p.kills = 123;
        p.onWon("serpentine");
        p.onRecord("serpentine", 20, false);
        p.onRecord("serpentine", 77, true);
        store.saveProgress(p);
        assertEquals(1000, p.updated, 0);
        Progress q = store.loadProgress();
        assertEquals("Tester", q.name);
        assertEquals(p.xp, q.xp);
        assertEquals(123, q.kills);
        assertTrue(q.won.contains("serpentine"));
        assertEquals(77, q.best("serpentine", true));

        kv.put("neontd.profile", "{kaputt");
        assertEquals(0, store.loadProgress().xp);
        kv.put("neontd.profile", "{\"xp\":-5,\"name\":\"<b>x\",\"won\":[1,\"a\",\"\"],\"best\":{\"l\":\"zehn\"}}");
        Progress r = store.loadProgress();
        assertEquals(0, r.xp);
        assertEquals("bx", r.name);
        assertEquals(1, r.won.size());
        assertTrue(r.bestWave.isEmpty());
    }

    @Test
    void profileMergeNeverLosesProgress() {
        Progress a = new Progress();
        a.name = "Alt";
        a.updated = 10;
        a.xp = 900;
        a.kills = 50;
        a.won.add("x");
        a.onRecord("x", 20, false);
        Progress b = new Progress();
        b.name = "Neu";
        b.updated = 20;
        b.xp = 300;
        b.kills = 80;
        b.won.add("y");
        b.onRecord("x", 11, false);
        b.onRecord("x", 400, true);
        Progress m = ProfileJson.merge(a, b);
        assertEquals("Neu", m.name, "der jüngere Name gewinnt");
        assertEquals(900, m.xp);
        assertEquals(80, m.kills);
        assertEquals(2, m.won.size());
        assertEquals(20, m.best("x", false));
        assertEquals(400, m.best("x", true));
        assertEquals(20, m.updated, 0);
        assertTrue(ProfileJson.sameAs(m, ProfileJson.merge(b, a)), "Zusammenführen ist vertauschbar");
        assertTrue(ProfileJson.sameAs(m, ProfileJson.merge(m, a)), "und idempotent");
    }

    // --------------------------------------------------------------------------------------- Lauf

    private static World bot(int waves, int towers) {
        LevelDef def = Levels.serpentine();
        World w = new World(def, SimListener.NONE);
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, towers, null);
        int steps = 0;
        while (w.waveIndex < waves && w.state == World.State.RUNNING && steps < 60 * 60 * 30) {
            if (steps % 20 == 0) {
                bot.think();
            }
            w.step();
            steps++;
        }
        return w;
    }

    private static World snapshotMoment(World w) {
        for (int i = 0; i < 60 * 600 && !w.canSnapshot(); i++) {
            w.step();
        }
        assertTrue(w.canSnapshot());
        return w;
    }

    @Test
    void runSaveRestoresTheWorld() {
        World a = snapshotMoment(bot(12, 14));
        WorldSnapshot snap = a.snapshot();
        assertNotNull(snap);
        assertTrue(snap.towers.size() >= 5);

        String text = RunSave.of("serpentine", 5, snap).encode();
        RunSave loaded = RunSave.decode("serpentine", text);
        assertNotNull(loaded);
        assertTrue(loaded.resumable());

        World b = new World(Levels.serpentine(), SimListener.NONE);
        b.restore(loaded.snapshot);
        assertEquals(a.money, b.money);
        assertEquals(a.lives, b.lives);
        assertEquals(a.kills, b.kills);
        assertEquals(a.waveIndex, b.waveIndex);
        assertEquals(a.clearedWaves(), b.clearedWaves());
        assertEquals(a.activeWaves(), b.activeWaves());
        assertEquals(a.enemiesRemaining(), b.enemiesRemaining());
        assertEquals(a.towers.size(), b.towers.size());
        assertEquals(a.enemies.size(), b.enemies.size());
        for (int i = 0; i < a.towers.size(); i++) {
            Tower x = a.towers.get(i);
            Tower y = b.towers.get(i);
            assertEquals(x.type, y.type);
            assertEquals(x.x, y.x, 0);
            assertEquals(x.y, y.y, 0);
            assertEquals(x.invested, y.invested);
            assertEquals(x.damage, y.damage);
            assertEquals(x.mode, y.mode);
            assertEquals(x.level[0] + x.level[1] + x.level[2], y.level[0] + y.level[1] + y.level[2]);
        }
        // Abbild vom Abbild ist gleich – nichts geht beim Wiederherstellen verloren.
        assertEquals(text.replaceAll("\"t\":[0-9.]+", ""),
                RunSave.of("serpentine", 5, b.snapshot()).encode().replaceAll("\"t\":[0-9.]+", ""));

        // Und das Spiel läuft nahtlos weiter.
        AutoPlayer bot = new AutoPlayer(b, true, 14, null);
        int startWave = b.waveIndex;
        for (int i = 0; i < 60 * 240; i++) {
            if (i % 20 == 0) {
                bot.think();
            }
            b.step();
        }
        assertEquals(World.State.RUNNING, b.state);
        assertTrue(b.waveIndex > startWave);
    }

    @Test
    void snapshotNeedsAnIdleSpawner() {
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        assertTrue(w.canSnapshot());
        w.startNextWave();
        assertFalse(w.canSnapshot(), "während Gegner noch nachkommen");
        assertNull(w.snapshot());
    }

    @Test
    void brokenRunSavesAreRejectedOrSanitized() {
        assertNull(RunSave.decode("x", null));
        assertNull(RunSave.decode("x", "kaputt"));
        assertNull(RunSave.decode("x", "{\"t\":1}"), "ohne Türme/Gegner kein Lauf");
        RunSave over = RunSave.decode("x", "{\"t\":9,\"over\":true}");
        assertNotNull(over);
        assertFalse(over.resumable());

        // Unsinnige Inhalte werden beim Wiederherstellen übersprungen statt abzustürzen.
        RunSave r = RunSave.decode("x", "{\"t\":1,\"money\":-5,\"lives\":0,\"wave\":3,\"towers\":[[99,10,10,0,0,0,5,0],"
                + "[0,640,360,99,99,99,1,9],[0,5,5,0,0,0,100,0],[1,2]],\"enemies\":[[0,7,0,50,99999,3,1,0],[77,0,0,5,5,5,1,0],"
                + "[0,0,50,5,5,5,1,0]]}");
        assertNotNull(r);
        World w = new World(Levels.serpentine(), SimListener.NONE);
        w.restore(r.snapshot);
        assertEquals(0, w.money);
        assertEquals(1, w.lives);
        assertTrue(w.towers.size() <= 1);
        for (Tower t : w.towers) {
            assertTrue(t.level[0] <= 5 && t.level[1] <= 5 && t.level[2] <= 5);
        }
        assertEquals(1, w.enemies.size());
        Enemy e = w.enemies.get(0);
        assertTrue(e.dist < w.paths[e.pathIndex].length());
    }

    @Test
    void runIndexAndEndMarker() {
        MemoryStore kv = new MemoryStore();
        SaveStore store = new SaveStore(kv, () -> 7);
        assertNull(store.loadRun("a"));
        store.endRun("a"); // nichts gespeichert: kein Marker nötig
        assertNull(store.loadRun("a"));
        World w = snapshotMoment(bot(6, 8));
        store.saveRun(RunSave.of("a", 5, w.snapshot()));
        assertNotNull(store.resumable("a"));
        store.endRun("a");
        assertNull(store.resumable("a"));
        assertNotNull(store.loadRun("a"));
        assertEquals(7, store.loadRun("a").t, 0);
        assertEquals(1, store.runLevelIds().size());
    }

    // ------------------------------------------------------------------------------------------ Level

    @Test
    void levelStoreKeepsTimestampsAndTombstones() {
        MemoryStore kv = new MemoryStore();
        double[] now = {100};
        LevelStore store = new LevelStore(kv, () -> now[0]);
        LevelDef a = store.createNew();
        a.paths.add(new double[] {40, 100, 640, 120, 1200, 600});
        store.save(a);
        assertEquals(100, store.entries().get(a.id).t, 0);
        assertNotNull(store.entries().get(a.id).code);
        now[0] = 200;
        store.delete(a.id);
        LevelStore.Entry tomb = store.entries().get(a.id);
        assertNotNull(tomb);
        assertNull(tomb.code);
        assertEquals(200, tomb.t, 0);
        assertTrue(store.loadAll().isEmpty());
        // Neu speichern hebt den Marker auf.
        now[0] = 300;
        store.save(a);
        assertNotNull(store.entries().get(a.id).code);
        assertEquals(1, store.loadAll().size());
    }

    @Test
    void newLevelIdsDifferBetweenDevices() {
        LevelStore one = new LevelStore(new MemoryStore(), () -> 1.7e12);
        LevelStore two = new LevelStore(new MemoryStore(), () -> 1.7e12 + 12345);
        boolean differ = false;
        for (int i = 0; i < 20 && !differ; i++) {
            differ = !one.createNew().id.equals(two.createNew().id);
        }
        assertTrue(differ);
    }
}
