package neontd.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.level.LevelDef;
import neontd.level.LevelStore;
import neontd.platform.MemoryStore;
import neontd.progress.Progress;
import neontd.sim.AutoPlayer;
import neontd.sim.SimListener;
import neontd.sim.World;
import org.junit.jupiter.api.Test;

/** Gleicht zwei "Geräte" über einen nachgebauten GitHub-Gist-Server ab. */
class SyncTest {
    private static final String TOKEN_A = "ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    /** Kleine Nachbildung der GitHub-API (Anmeldung, Gist-Liste, lesen, anlegen, ändern). */
    static final class FakeGitHub implements Http {
        final Map<String, Map<String, Object>> gists = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        String validToken = TOKEN_A;
        int seq = 1;
        boolean offline;

        @Override
        public void request(String method, String url, String[] headers, String body, Callback cb) {
            log.add(method + " " + url.replace(GistSync.API, ""));
            if (offline) {
                cb.done(0, "offline");
                return;
            }
            String auth = "";
            for (int i = 0; i + 1 < headers.length; i += 2) {
                if (headers[i].equals("Authorization")) {
                    auth = headers[i + 1];
                }
            }
            if (!auth.equals("Bearer " + validToken)) {
                cb.done(401, "{\"message\":\"Bad credentials\"}");
                return;
            }
            String path = url.substring(GistSync.API.length());
            if (path.equals("/user")) {
                cb.done(200, "{\"login\":\"anna\"}");
            } else if (path.startsWith("/gists?")) {
                List<Object> list = new ArrayList<>();
                for (Map<String, Object> g : gists.values()) {
                    Map<String, Object> files = new LinkedHashMap<>();
                    for (String f : Json.map(g, "files").keySet()) {
                        files.put(f, new LinkedHashMap<String, Object>());
                    }
                    Map<String, Object> light = new LinkedHashMap<>();
                    light.put("id", g.get("id"));
                    light.put("files", files);
                    list.add(light);
                }
                cb.done(200, Json.write(list));
            } else if (path.equals("/gists") && method.equals("POST")) {
                Map<String, Object> req = Json.asMap(Json.parse(body));
                assertEquals(Boolean.FALSE, req.get("public"), "Spielstand muss privat sein");
                String id = "g" + seq++;
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("id", id);
                g.put("files", readFiles(req));
                gists.put(id, g);
                cb.done(201, Json.write(g));
            } else if (path.startsWith("/gists/")) {
                String id = path.substring("/gists/".length());
                Map<String, Object> g = gists.get(id);
                if (g == null) {
                    cb.done(404, "{\"message\":\"Not Found\"}");
                } else if (method.equals("GET")) {
                    cb.done(200, Json.write(g));
                } else {
                    Map<String, Object> req = Json.asMap(Json.parse(body));
                    g.put("files", readFiles(req));
                    cb.done(200, Json.write(g));
                }
            } else {
                cb.done(404, "{}");
            }
        }

        private static Map<String, Object> readFiles(Map<String, Object> req) {
            Map<String, Object> files = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : Json.map(req, "files").entrySet()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("content", Json.str(Json.asMap(e.getValue()), "content", ""));
                files.put(e.getKey(), f);
            }
            return files;
        }

        int count(String prefix) {
            int n = 0;
            for (String s : log) {
                if (s.startsWith(prefix)) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Ein Gerät mit eigenem Speicher. */
    static final class Device {
        final MemoryStore kv = new MemoryStore();
        final double[] now;
        final SaveStore saves;
        final LevelStore levels;
        final Progress live;
        final GistSync sync;

        Device(FakeGitHub gh, double start) {
            now = new double[] {start};
            saves = new SaveStore(kv, () -> now[0]);
            levels = new LevelStore(kv, () -> now[0]);
            live = saves.loadProgress();
            sync = new GistSync(gh, saves, levels, live);
        }

        LevelDef addLevel() {
            LevelDef l = levels.createNew();
            l.paths.add(new double[] {40, 100, 640, 120, 1200, 600});
            levels.save(l);
            return l;
        }
    }

    @Test
    void connectCreatesPrivateGistAndRemembersIt() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.addXp(700);
        a.saves.saveProgress(a.live);
        a.sync.connect(TOKEN_A);
        assertEquals(GistSync.Status.OK, a.sync.status, a.sync.message);
        assertTrue(a.sync.connected());
        assertEquals("anna", a.sync.user());
        assertEquals("anna", a.live.name, "Name wird aus dem GitHub-Konto übernommen, wenn leer");
        assertEquals(1, gh.gists.size());
        String gist = a.saves.cloud("gist");
        assertFalse(gist.isEmpty());
        assertTrue(Json.map(gh.gists.get(gist), "files").containsKey(GistSync.FILE));
        SaveBundle stored = SaveBundle.decode(Json.str(Json.map(Json.map(gh.gists.get(gist), "files"), GistSync.FILE),
                "content", ""));
        assertNotNull(stored);
        assertEquals(a.live.xp, stored.profile.xp);
        // Das Token steht nur im lokalen Speicher, nie in den hochgeladenen Daten.
        assertFalse(Json.write(gh.gists).contains(TOKEN_A));
    }

    @Test
    void twoDevicesMergeProgressLevelsAndRuns() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.addXp(900);
        a.live.kills = 40;
        a.live.onWon("serpentine");
        a.saves.saveProgress(a.live);
        LevelDef la = a.addLevel();
        a.sync.connect(TOKEN_A);
        assertEquals(GistSync.Status.OK, a.sync.status, a.sync.message);

        Device b = new Device(gh, 2000);
        b.live.addXp(200);
        b.live.kills = 90;
        b.live.name = "Zweitgerät";
        b.saves.saveProgress(b.live);
        LevelDef lb = b.addLevel();
        b.sync.connect(TOKEN_A);
        assertEquals(GistSync.Status.OK, b.sync.status, b.sync.message);
        assertEquals(1, gh.gists.size(), "dasselbe Gist wird wiederverwendet");
        assertEquals(a.live.xp > 200 ? a.live.xp : 200, b.live.xp);
        assertEquals(90, b.live.kills);
        assertTrue(b.live.won.contains("serpentine"));
        assertEquals(2, b.levels.loadAll().size());
        assertNotNull(b.levels.load(la.id));

        a.now[0] = 3000;
        a.sync.sync();
        assertEquals(GistSync.Status.OK, a.sync.status, a.sync.message);
        assertEquals(2, a.levels.loadAll().size());
        assertNotNull(a.levels.load(lb.id));
        assertEquals(90, a.live.kills);
        assertEquals(b.live.xp, a.live.xp);
        assertEquals(b.live.name, a.live.name, "der jüngere Name gewinnt");
    }

    @Test
    void deletedLevelStaysDeletedEverywhere() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        LevelDef l = a.addLevel();
        a.sync.connect(TOKEN_A);
        Device b = new Device(gh, 1500);
        b.sync.connect(TOKEN_A);
        assertNotNull(b.levels.load(l.id));

        a.now[0] = 2000;
        a.levels.delete(l.id);
        a.sync.sync();
        b.now[0] = 2500;
        b.sync.sync();
        assertNull(b.levels.load(l.id), "Löschung kommt auf dem zweiten Gerät an");
        a.now[0] = 3000;
        a.sync.sync();
        assertNull(a.levels.load(l.id), "und wird nicht wiederbelebt");
        assertTrue(a.levels.loadAll().isEmpty() && b.levels.loadAll().isEmpty());
    }

    @Test
    void endedRunIsNotResurrectedByTheCloud() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        World w = new World(neontd.level.Levels.serpentine(), SimListener.NONE);
        w.enableEndless();
        AutoPlayer bot = new AutoPlayer(w, true, 10, null);
        for (int i = 0; i < 60 * 200 || !w.canSnapshot(); i++) {
            if (i % 20 == 0) {
                bot.think();
            }
            w.step();
        }
        a.now[0] = 1100;
        a.saves.saveRun(RunSave.of("serpentine", a.now[0], w.snapshot()));
        a.sync.connect(TOKEN_A);

        Device b = new Device(gh, 1200);
        b.sync.connect(TOKEN_A);
        assertNotNull(b.saves.resumable("serpentine"), "Lauf erscheint auf dem zweiten Gerät");

        b.now[0] = 1300;
        b.saves.endRun("serpentine");
        b.sync.sync();
        a.now[0] = 1400;
        a.sync.sync();
        assertNull(a.saves.resumable("serpentine"));
        assertNotNull(a.saves.loadRun("serpentine"));
    }

    @Test
    void unchangedStateCausesNoWrite() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.sync.connect(TOKEN_A);
        int writes = gh.count("PATCH") + gh.count("POST");
        a.now[0] = 5000;
        a.sync.sync();
        a.sync.sync();
        assertEquals(writes, gh.count("PATCH") + gh.count("POST"), "nichts zu schreiben");
        assertEquals(GistSync.Status.OK, a.sync.status);
    }

    @Test
    void errorsAreExplainedAndNothingIsStoredForBadTokens() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.sync.connect("ghp_falschfalschfalschfalschfalsch");
        assertEquals(GistSync.Status.ERROR, a.sync.status);
        assertTrue(a.sync.message.contains("Token"), a.sync.message);
        assertFalse(a.sync.connected());
        a.sync.connect("zu kurz");
        assertEquals(GistSync.Status.ERROR, a.sync.status);
        assertFalse(a.sync.connected());

        a.sync.connect(TOKEN_A);
        assertTrue(a.sync.connected());
        gh.offline = true;
        a.sync.sync();
        assertEquals(GistSync.Status.ERROR, a.sync.status);
        assertTrue(a.sync.message.contains("Verbindung"), a.sync.message);
        assertTrue(a.sync.connected(), "ein Netzfehler trennt nicht");
        gh.offline = false;
        gh.validToken = "ghp_anderesanderesanderesanderes11";
        a.sync.sync();
        assertEquals(GistSync.Status.ERROR, a.sync.status);
        assertTrue(a.sync.message.contains("ungültig"), a.sync.message);
    }

    @Test
    void deletedGistIsRecreated() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.addXp(500);
        a.saves.saveProgress(a.live);
        a.sync.connect(TOKEN_A);
        gh.gists.clear();
        a.now[0] = 9000;
        a.sync.sync();
        assertEquals(GistSync.Status.OK, a.sync.status, a.sync.message);
        assertEquals(1, gh.gists.size());
        SaveBundle stored = SaveBundle.decode(Json.str(Json.map(Json.map(gh.gists.values().iterator().next(), "files"),
                GistSync.FILE), "content", ""));
        assertEquals(a.live.xp, stored.profile.xp);
    }

    @Test
    void disconnectKeepsLocalDataAndForgetsTheToken() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.addXp(500);
        a.saves.saveProgress(a.live);
        a.sync.connect(TOKEN_A);
        a.sync.disconnect();
        assertFalse(a.sync.connected());
        assertEquals("", a.saves.cloud("token"));
        assertEquals(500, a.saves.loadProgress().xp);
        assertEquals(1, gh.gists.size(), "die Cloud-Kopie bleibt bestehen");
        a.sync.sync(); // ohne Token: kein Zugriff
        assertEquals(0, gh.count("GET /gists/"), gh.log.toString());
    }

    @Test
    void autoSyncRespectsDirtyFlagAndInterval() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.sync.connect(TOKEN_A);
        int before = gh.log.size();
        a.sync.tick();
        assertEquals(before, gh.log.size(), "nichts zu tun");
        a.sync.markDirty();
        a.now[0] += 10_000; // 10 s später: zu früh
        a.sync.tick();
        assertEquals(before, gh.log.size());
        a.now[0] += 200_000;
        a.sync.tick();
        assertTrue(gh.log.size() > before);
        a.sync.markDirty();
        int mid = gh.log.size();
        a.sync.flush(); // flush ignoriert den Mindestabstand
        assertTrue(gh.log.size() > mid);
    }

    @Test
    void exportCodeRoundTripsProfileAndLevels() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.addXp(1234);
        a.live.name = "Zoë";
        a.saves.saveProgress(a.live);
        LevelDef l = a.addLevel();
        String code = SaveBundle.collect(a.saves, a.levels).toCode();
        assertTrue(code.startsWith("NTD1:"));

        Device b = new Device(gh, 5000);
        SaveBundle imported = SaveBundle.fromCode("  " + code + "\n");
        assertNotNull(imported);
        SaveBundle merged = SaveBundle.merge(SaveBundle.collect(b.saves, b.levels), imported);
        merged.applyTo(b.saves, b.levels);
        assertEquals(1234, b.saves.loadProgress().xp);
        assertEquals("Zoë", b.saves.loadProgress().name);
        assertNotNull(b.levels.load(l.id));
        assertNull(SaveBundle.fromCode("NTD1:***"));
        assertNull(SaveBundle.fromCode("hallo"));
        assertNull(SaveBundle.fromCode(null));
        assertNull(SaveBundle.decode("{\"app\":\"anderes-spiel\"}"));
    }

    @Test
    void loginNameIsOnlyASuggestionForEmptyNames() {
        FakeGitHub gh = new FakeGitHub();
        Device a = new Device(gh, 1000);
        a.live.name = "Tester";
        a.saves.saveProgress(a.live);
        a.sync.connect(TOKEN_A);
        assertEquals("Tester", a.live.name);

        Device b = new Device(gh, 5000); // später, aber ohne eigenen Namen
        b.sync.connect(TOKEN_A);
        assertEquals("Tester", b.live.name, "der in der Cloud vorhandene Name gewinnt gegen den Vorschlag");
        assertEquals("Tester", b.saves.loadProgress().name);

        Device c = new Device(new FakeGitHub(), 100); // eigene Cloud ohne Namen: Vorschlag greift
        c.sync.connect(TOKEN_A);
        assertEquals("anna", c.live.name);
    }
}
