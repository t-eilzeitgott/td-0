package neontd.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import neontd.platform.MemoryStore;
import org.junit.jupiter.api.Test;

/** Spielername, Login-Log und Arena-Läufe gegen einen nachgebauten Server. */
class LeaderboardTest {
    /** Nimmt Anfragen entgegen und antwortet nach Vorgabe (Status 0 = kein Netz). */
    static final class FakeHttp implements Http {
        final List<String> calls = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        int status = 200;
        java.util.function.Function<String, String> answer = fn -> "{\"ok\":true}";

        @Override
        public void request(String method, String url, String[] headers, String body, Callback cb) {
            String fn = url.substring(url.lastIndexOf('/') + 1);
            calls.add(fn);
            bodies.add(body);
            cb.done(status, status == 0 ? "offline" : answer.apply(fn));
        }
    }

    @Test
    void namesAreCleanedAndChecked() {
        assertEquals("Anna B", Leaderboard.cleanName("  Anna   B  "));
        assertEquals(16, Leaderboard.cleanName("abcdefghijklmnopqrstuvwxyz").length());
        assertNull(Leaderboard.nameProblem("Jörg-2"));
        assertNull(Leaderboard.nameProblem("Mia"));
        assertNotNull(Leaderboard.nameProblem("A"));
        assertNotNull(Leaderboard.nameProblem("_Mia"));
        assertNotNull(Leaderboard.nameProblem("Mia<b>"));
    }

    @Test
    void registrationHandlesTakenNamesAndOffline() {
        FakeHttp http = new FakeHttp();
        MemoryStore store = new MemoryStore();
        Leaderboard b = new Leaderboard(http, store);
        assertFalse(b.hasName());
        http.answer = fn -> "{\"ok\":false,\"fehler\":\"vergeben\"}";
        String[] err = {null};
        b.register("Mia", (ok, e) -> err[0] = ok ? "ok" : e);
        assertEquals("vergeben", err[0]);
        assertFalse(b.hasName());
        // offline: vorgemerkt, gilt erst einmal
        http.status = 0;
        b.register("Neo", (ok, e) -> err[0] = ok ? "ok:" + e : e);
        assertEquals("ok:offline", err[0]);
        assertTrue(b.hasName());
        assertTrue(b.namePending());
        // wieder online: flush bestätigt den Namen und protokolliert den Start
        http.status = 200;
        http.answer = fn -> fn.equals("neontd_anmelden") ? "{\"ok\":true,\"kennung\":\"k1\",\"name\":\"Neo\"}"
                : "{\"ok\":true,\"kennung\":\"k1\",\"name\":\"Neo\"}";
        b.flush();
        assertTrue(http.calls.contains("neontd_login"));
        assertTrue(http.calls.contains("neontd_anmelden"));
        assertFalse(b.namePending());
        assertEquals("Neo", b.playerName());
        assertTrue(b.isMine("k1"));
        assertFalse(b.isMine("k2"));
        // Die Gerätekennung geht mit, aber nur an die Funktionen
        assertTrue(http.bodies.get(http.bodies.size() - 1).contains(b.device()));
    }

    @Test
    void arenaRunsAreQueuedUntilTheServerTakesThem() {
        FakeHttp http = new FakeHttp();
        MemoryStore store = new MemoryStore();
        Leaderboard b = new Leaderboard(http, store);
        http.answer = fn -> "{\"ok\":true,\"kennung\":\"k1\",\"name\":\"Neo\"}";
        b.register("Neo", (ok, e) -> { });
        http.status = 0;
        Leaderboard.ArenaResult[] got = {null};
        b.submitArena(new Leaderboard.ArenaRun("2026-W44", 12345, 9, 400, 9, "100,300", "PULS 2"), r -> got[0] = r);
        assertNotNull(got[0]);
        assertEquals("offline", got[0].error);
        assertEquals(1, b.arenaPendingCount());
        assertEquals(12345, b.arenaBest("2026-W44"));
        assertEquals(9, b.arenaBestWave("2026-W44"));
        assertEquals(2, b.arenaBestSplits("2026-W44").length);
        // Neustart der App: die Warteschlange ist gespeichert
        Leaderboard b2 = new Leaderboard(http, store);
        assertEquals(1, b2.arenaPendingCount());
        http.status = 200;
        http.answer = fn -> fn.equals("neontd_arena_lauf") ? "{\"ok\":true,\"best\":12345,\"rang\":1,\"spieler\":3,\"rekord\":true}"
                : "{\"ok\":true,\"kennung\":\"k1\",\"name\":\"Neo\"}";
        for (int i = 0; i < 20 && b2.arenaPendingCount() > 0; i++) {
            b2.flush();
        }
        assertEquals(0, b2.arenaPendingCount());
        String body = http.bodies.get(http.calls.lastIndexOf("neontd_arena_lauf"));
        assertTrue(body.contains("\"p_woche\":\"2026-W44\""));
        assertTrue(body.contains("\"p_punkte\":12345"));
        assertTrue(body.contains("\"p_splits\":\"100,300\""));
        // Ein schlechterer Lauf überschreibt den Bestwert auf dem Gerät nicht
        b2.submitArena(new Leaderboard.ArenaRun("2026-W44", 500, 3, 100, 3, "", ""), null);
        assertEquals(12345, b2.arenaBest("2026-W44"));
    }

    @Test
    void arenaListIsParsedWithOwnEntryAndSplits() {
        FakeHttp http = new FakeHttp();
        Leaderboard b = new Leaderboard(http, new MemoryStore());
        http.answer = fn -> fn.equals("neontd_arena_liste")
                ? "[{\"rang\":1,\"kennung\":\"x\",\"name\":\"Mia\",\"punkte\":900,\"welle\":12,\"serie\":12,\"versuche\":4,"
                        + "\"splits\":\"100,400,900\",\"erstellt\":\"2026-10-29T10:29:08.313529+00:00\"},"
                        + "{\"rang\":2,\"kennung\":\"k1\",\"name\":\"Neo\",\"punkte\":800,\"welle\":11,\"serie\":3,\"versuche\":2,"
                        + "\"splits\":\"\",\"erstellt\":\"2026-10-29T09:00:00+00:00\"}]"
                : "{\"ok\":true,\"kennung\":\"k1\",\"name\":\"Neo\"}";
        b.login();
        List<List<Leaderboard.ArenaEntry>> out = new ArrayList<>();
        b.arenaList("2026-W44", true, out::add);
        assertEquals(1, out.size());
        List<Leaderboard.ArenaEntry> l = out.get(0);
        assertEquals(2, l.size());
        assertEquals("Mia", l.get(0).name);
        assertFalse(l.get(0).mine);
        assertTrue(l.get(1).mine);
        assertEquals(400, l.get(0).splitAt(2));
        assertEquals(-1, l.get(0).splitAt(4));
        assertEquals(4, l.get(0).attempts);
        assertEquals(l, b.cachedArena("2026-W44"));
        double t = Leaderboard.parseTime("2026-10-29T10:29:08.313529+00:00");
        assertEquals(neontd.progress.Calendar.daysFromCivil(2026, 10, 29) * 86_400_000.0 + (10 * 3600 + 29 * 60 + 8) * 1000.0, t, 1);
        assertEquals(Leaderboard.parseTime("2026-10-29T12:29:08+02:00"), t, 1000);
    }

    @Test
    void withoutNetworkTheNameIsLocal() {
        Leaderboard b = new Leaderboard(null, new MemoryStore());
        boolean[] ok = {false};
        b.register("Neo", (o, e) -> ok[0] = o);
        assertTrue(ok[0]);
        assertEquals("Neo", b.playerName());
        assertFalse(b.namePending());
        Leaderboard.ArenaResult[] got = {null};
        b.submitArena(new Leaderboard.ArenaRun("2026-W44", 10, 1, 30, 1, "10", ""), r -> got[0] = r);
        assertEquals("offline", got[0].error);
        assertEquals(10, b.arenaBest("2026-W44"));
    }
}
