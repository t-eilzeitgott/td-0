package neontd.save;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import neontd.platform.KeyValueStore;

/**
 * Online-Teil des Spiels über eine kleine Supabase-Datenbank: Spielername (weltweit eindeutig), Login-Log beim
 * App-Start, die allgemeinen Bestenlisten (Tages-Challenge, Endlos, Sterne) und die Wochen-Arena.
 *
 * <p>Alles läuft über Datenbank-Funktionen ({@code rpc/neontd_*}); der öffentliche Schlüssel kann die Tabellen nicht
 * direkt lesen oder schreiben. Jedes Gerät hat eine zufällige, private Kennung ({@link #device()}); öffentlich
 * sichtbar ist nur eine daraus abgeleitete "Kennung", an der das Spiel den eigenen Eintrag erkennt. Ohne Netz merkt
 * sich das Spiel ausstehende Einträge und schickt sie später – nichts geht verloren.
 */
public final class Leaderboard {
    /** Ein Eintrag einer allgemeinen Liste. */
    public static final class Entry {
        public final String name;
        public final int score;
        public final String info;
        public final boolean mine;

        Entry(String name, int score, String info, boolean mine) {
            this.name = name;
            this.score = score;
            this.info = info;
            this.mine = mine;
        }
    }

    /** Ein Platz in der Arena-Rangliste (bester Lauf je Spieler). */
    public static final class ArenaEntry {
        public final int rank;
        public final String kennung;
        public final String name;
        public final long score;
        public final int wave;
        public final int streak;
        public final int attempts;
        public final long[] splits;
        public final boolean mine;
        public final double at;

        ArenaEntry(int rank, String kennung, String name, long score, int wave, int streak, int attempts, long[] splits,
                   boolean mine, double at) {
            this.rank = rank;
            this.kennung = kennung;
            this.name = name;
            this.score = score;
            this.wave = wave;
            this.streak = streak;
            this.attempts = attempts;
            this.splits = splits;
            this.mine = mine;
            this.at = at;
        }

        /** Punktestand nach {@code k} besiegten Wellen oder -1. */
        public long splitAt(int k) {
            return k >= 1 && k <= splits.length ? splits[k - 1] : -1;
        }
    }

    /** Ein Lauf im Verlauf der Woche. */
    public static final class FeedEntry {
        public final String name;
        public final long score;
        public final int wave;
        public final boolean record;
        public final boolean mine;
        public final double at;

        FeedEntry(String name, long score, int wave, boolean record, boolean mine, double at) {
            this.name = name;
            this.score = score;
            this.wave = wave;
            this.record = record;
            this.mine = mine;
            this.at = at;
        }
    }

    /** Sieger einer vergangenen Woche. */
    public static final class HallEntry {
        public final String week;
        public final String name;
        public final long score;
        public final int wave;
        public final int players;
        public final boolean mine;

        HallEntry(String week, String name, long score, int wave, int players, boolean mine) {
            this.week = week;
            this.name = name;
            this.score = score;
            this.wave = wave;
            this.players = players;
            this.mine = mine;
        }
    }

    /** Ein beendeter Arena-Lauf (wird so lange vorgemerkt, bis der Server ihn angenommen hat). */
    public static final class ArenaRun {
        public final String week;
        public final long score;
        public final int wave;
        public final int seconds;
        public final int streak;
        public final String splits;
        public final String info;

        public ArenaRun(String week, long score, int wave, int seconds, int streak, String splits, String info) {
            this.week = week;
            this.score = score;
            this.wave = wave;
            this.seconds = seconds;
            this.streak = streak;
            this.splits = splits == null ? "" : splits;
            this.info = info == null ? "" : info;
        }
    }

    /** Antwort des Servers auf einen Arena-Lauf. */
    public static final class ArenaResult {
        /** angenommen? Sonst steht in {@link #error} warum (oder "offline"). */
        public final boolean ok;
        public final String error;
        public final long best;
        public final int rank;
        public final int players;
        public final boolean record;

        public ArenaResult(boolean ok, String error, long best, int rank, int players, boolean record) {
            this.ok = ok;
            this.error = error;
            this.best = best;
            this.rank = rank;
            this.players = players;
            this.record = record;
        }
    }

    /** Antwort auf die Namensanmeldung. */
    public interface NameCallback {
        /** @param error null bei Erfolg, sonst "vergeben", "ungueltig", "zu_oft" oder "offline" */
        void done(boolean ok, String error);
    }

    public static final String BOARD_ENDLESS = "endless";
    public static final String BOARD_STARS = "stars";
    public static final int MIN_NAME = 2;
    public static final int MAX_NAME = 16;

    private static final String URL = "https://obwtpegujjuwcsvmadqg.supabase.co/rest/v1/rpc/";
    private static final String KEY = "sb_publishable_VDRVzKlPbfNZrUV-95d0ag_D6FtDC4j";
    private static final String DEVICE_KEY = "neontd.device";
    private static final String PENDING_KEY = "neontd.board.pending";
    private static final String SENT_KEY = "neontd.board.sent";
    private static final String NAME_KEY = "neontd.player.name";
    private static final String NAME_PENDING_KEY = "neontd.player.pending";
    private static final String KENNUNG_KEY = "neontd.player.kennung";
    private static final String ARENA_PENDING_KEY = "neontd.arena.pending";
    private static final String ARENA_BEST_PREFIX = "neontd.arena.best.";
    /** So lange gilt eine geladene Arena-Liste als frisch (Sekunden). */
    private static final double FRESH = 25;

    private final Http http;
    private final KeyValueStore store;
    private final String device;
    /** Zuletzt gesendete Punkte je Liste (damit nichts doppelt oder schlechter geschickt wird). */
    private final Map<String, Integer> sent = new java.util.TreeMap<>();
    /** Ausstehende Einträge: Liste → Punkte (werden beim nächsten Versuch geschickt). */
    private final Map<String, Integer> pending = new java.util.TreeMap<>();
    private final Map<String, String> pendingInfo = new java.util.TreeMap<>();
    private final List<ArenaRun> arenaPending = new ArrayList<>();
    private Consumer<ArenaResult> arenaWaiting;
    private ArenaRun arenaWaitingFor;
    private boolean sending;
    private boolean loggedIn;
    private String kennung;
    private double clock;
    private double retryAt;

    // Zwischenspeicher der Arena-Listen
    private String cachedWeek;
    private List<ArenaEntry> cachedList;
    private double cachedAt = -1e9;
    private final List<Consumer<List<ArenaEntry>>> listWaiters = new ArrayList<>();
    private boolean listLoading;

    public Leaderboard(Http http, KeyValueStore store) {
        this.http = http;
        this.store = store;
        String id = store.get(DEVICE_KEY);
        if (id == null || id.length() < 8) {
            id = randomId();
            store.put(DEVICE_KEY, id);
        }
        this.device = id;
        this.kennung = store.get(KENNUNG_KEY);
        load(SENT_KEY, sent);
        load(PENDING_KEY, pending);
        loadArenaPending();
    }

    private static String randomId() {
        StringBuilder sb = new StringBuilder("d");
        long seed = (long) (Math.random() * 4_000_000_000L) ^ System.nanoTime();
        for (int i = 0; i < 20; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt((int) ((seed >>> 33) % 36)));
        }
        return sb.toString();
    }

    private void load(String key, Map<String, Integer> into) {
        Map<String, Object> m = Json.asMap(Json.tryParse(store.get(key)));
        if (m == null) {
            return;
        }
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() instanceof Number) {
                into.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
        }
    }

    private void save(String key, Map<String, Integer> m) {
        store.put(key, Json.write(new java.util.LinkedHashMap<String, Object>(m)));
    }

    public String device() {
        return device;
    }

    public boolean available() {
        return http != null;
    }

    // ------------------------------------------------------------------------------------------ Spielername

    /** Der bestätigte Spielername oder {@code null}, solange keiner gewählt wurde. */
    public String playerName() {
        String n = store.get(NAME_KEY);
        if (n == null || n.isEmpty()) {
            n = store.get(NAME_PENDING_KEY);
        }
        return n == null || n.isEmpty() ? null : n;
    }

    /** Hat der Spieler schon einen Namen gewählt (auch wenn er wegen fehlenden Netzes noch nicht bestätigt ist)? */
    public boolean hasName() {
        return playerName() != null;
    }

    /** Wartet der Name noch auf die Bestätigung durch den Server? */
    public boolean namePending() {
        String p = store.get(NAME_PENDING_KEY);
        return p != null && !p.isEmpty();
    }

    /** Bereinigt eine Eingabe (Leerzeichen zusammenfassen, Länge begrenzen). */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length() && sb.length() < MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n') {
                space = sb.length() > 0;
                continue;
            }
            if (space) {
                sb.append(' ');
                space = false;
                if (sb.length() >= MAX_NAME) {
                    break;
                }
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }

    private static boolean nameChar(char c, boolean first) {
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
            return true;
        }
        if ("ÄÖÜäöüß".indexOf(c) >= 0) {
            return true;
        }
        return !first && (c == ' ' || c == '.' || c == '_' || c == '-');
    }

    /** Prüft einen Namen; liefert {@code null}, wenn er passt, sonst einen verständlichen Hinweis. */
    public static String nameProblem(String raw) {
        String n = cleanName(raw);
        if (n.length() < MIN_NAME) {
            return "Mindestens " + MIN_NAME + " Zeichen";
        }
        for (int i = 0; i < n.length(); i++) {
            if (!nameChar(n.charAt(i), i == 0)) {
                return i == 0 ? "Bitte mit Buchstabe oder Zahl beginnen" : "Nur Buchstaben, Zahlen, Leerzeichen und . _ -";
            }
        }
        return null;
    }

    /**
     * Meldet einen Namen an (oder ändert ihn). Ohne Netz wird der Name vorgemerkt und später bestätigt; ist er dann
     * vergeben, fragt das Spiel beim nächsten Start erneut.
     */
    public void register(String raw, NameCallback cb) {
        final String n = cleanName(raw);
        String problem = nameProblem(n);
        if (problem != null) {
            cb.done(false, "ungueltig");
            return;
        }
        if (http == null) {
            store.put(NAME_KEY, n);
            store.remove(NAME_PENDING_KEY);
            cb.done(true, null);
            return;
        }
        String body = "{\"p_geraet\":" + Json.write(device) + ",\"p_name\":" + Json.write(n)
                + ",\"p_version\":" + Json.write(neontd.app.Version.NAME) + ",\"p_plattform\":" + Json.write(platformInfo) + "}";
        http.request("POST", URL + "neontd_anmelden", headers(), body, (status, text) -> {
            if (status >= 200 && status < 300) {
                Map<String, Object> m = Json.asMap(Json.tryParse(text));
                if (m != null && Json.bool(m, "ok", false)) {
                    store.put(NAME_KEY, Json.str(m, "name", n));
                    store.remove(NAME_PENDING_KEY);
                    setKennung(Json.str(m, "kennung", null));
                    cb.done(true, null);
                } else {
                    cb.done(false, m == null ? "fehler" : Json.str(m, "fehler", "fehler"));
                }
            } else if (status == 0) {
                // kein Netz: vormerken, später bestätigen
                store.put(NAME_PENDING_KEY, n);
                cb.done(true, "offline");
            } else {
                cb.done(false, "fehler");
            }
        });
    }

    private void setKennung(String k) {
        if (k != null && !k.isEmpty()) {
            kennung = k;
            store.put(KENNUNG_KEY, k);
        }
    }

    /** Wird ein Eintrag mit dieser öffentlichen Kennung von diesem Gerät gemacht? */
    public boolean isMine(String k) {
        return k != null && k.equals(kennung);
    }

    private String platformInfo = "";

    /** Kurzbeschreibung der Plattform für das Login-Log (z. B. "iPhone · App"). */
    public void setPlatformInfo(String info) {
        platformInfo = info == null ? "" : (info.length() > 120 ? info.substring(0, 120) : info);
    }

    /** Protokolliert den App-Start (einmal je Start; Fehler werden ignoriert). */
    public void login() {
        if (http == null || loggedIn) {
            return;
        }
        loggedIn = true;
        String body = "{\"p_geraet\":" + Json.write(device) + ",\"p_version\":" + Json.write(neontd.app.Version.NAME)
                + ",\"p_plattform\":" + Json.write(platformInfo) + "}";
        http.request("POST", URL + "neontd_login", headers(), body, (status, text) -> {
            if (status == 0) {
                loggedIn = false; // beim nächsten Versuch erneut
                return;
            }
            Map<String, Object> m = Json.asMap(Json.tryParse(text));
            if (m == null) {
                return;
            }
            setKennung(Json.str(m, "kennung", null));
            String server = Json.str(m, "name", null);
            if (server != null && !server.isEmpty() && !namePending()) {
                store.put(NAME_KEY, server);
            }
        });
    }

    // ------------------------------------------------------------------------------- allgemeine Bestenlisten

    /** Merkt einen Eintrag vor (nur, wenn er besser ist als der zuletzt gesendete) und schickt ihn bei Gelegenheit. */
    public void submit(String board, int score, String info) {
        if (score <= 0 || board == null) {
            return;
        }
        Integer s = sent.get(board);
        Integer p = pending.get(board);
        if ((s != null && s >= score) || (p != null && p >= score)) {
            return;
        }
        pending.put(board, score);
        pendingInfo.put(board, info == null ? "" : info);
        save(PENDING_KEY, pending);
    }

    /** Schickt Ausstehendes (Name, Einträge, Arena-Läufe – eins je Aufruf). Vom Spiel jede Sekunde aufgerufen. */
    public void flush() {
        clock += 1;
        if (http == null || sending || clock < retryAt) {
            return;
        }
        if (!loggedIn) {
            login();
        }
        if (namePending()) {
            sending = true;
            register(store.get(NAME_PENDING_KEY), (ok, err) -> {
                sending = false;
                if ("offline".equals(err)) {
                    retryAt = clock + 15;
                } else if (!ok) {
                    // vergeben oder ungültig: der Spieler wählt beim nächsten Start neu
                    store.remove(NAME_PENDING_KEY);
                    store.remove(NAME_KEY);
                }
            });
            return;
        }
        if (store.get(NAME_KEY) == null) {
            return; // erst mit Namen eintragen
        }
        if (!arenaPending.isEmpty()) {
            sendArena(arenaPending.get(0));
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        final String board = pending.keySet().iterator().next();
        final int score = pending.get(board);
        String info = pendingInfo.get(board);
        String body = "{\"p_geraet\":" + Json.write(device) + ",\"p_board\":" + Json.write(board) + ",\"p_punkte\":" + score
                + ",\"p_info\":" + Json.write(info == null ? "" : info) + "}";
        sending = true;
        http.request("POST", URL + "neontd_eintragen", headers(), body, (status, text) -> {
            sending = false;
            Map<String, Object> m = Json.asMap(Json.tryParse(text));
            boolean ok = status >= 200 && status < 300 && m != null && Json.bool(m, "ok", false);
            if (ok) {
                pending.remove(board);
                pendingInfo.remove(board);
                sent.put(board, score);
                save(PENDING_KEY, pending);
                save(SENT_KEY, sent);
            } else if (status >= 200 && status < 500 && status != 0 && !"zu_schnell".equals(m == null ? null : Json.str(m, "fehler", ""))) {
                // abgelehnt (ungültige Daten): nicht ewig wiederholen
                pending.remove(board);
                pendingInfo.remove(board);
                save(PENDING_KEY, pending);
            } else {
                retryAt = clock + 10;
            }
        });
    }

    /** Lädt die besten Einträge einer allgemeinen Liste (höchstens 50). */
    public void top(String board, Consumer<List<Entry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        String body = "{\"p_board\":" + Json.write(board) + ",\"p_limit\":50}";
        http.request("POST", URL + "neontd_liste", headers(), body, (status, text) -> {
            List<Object> list = status >= 200 && status < 300 ? Json.asList(Json.tryParse(text)) : null;
            if (list == null) {
                cb.accept(null);
                return;
            }
            List<Entry> out = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                if (m != null) {
                    out.add(new Entry(Json.str(m, "name", "?"), Json.integer(m, "punkte", 0), Json.str(m, "info", ""),
                            isMine(Json.str(m, "kennung", ""))));
                }
            }
            cb.accept(out);
        });
    }

    // -------------------------------------------------------------------------------------------- Arena

    private void loadArenaPending() {
        List<Object> list = Json.asList(Json.tryParse(store.get(ARENA_PENDING_KEY)));
        if (list == null) {
            return;
        }
        for (Object o : list) {
            Map<String, Object> m = Json.asMap(o);
            if (m != null) {
                arenaPending.add(new ArenaRun(Json.str(m, "w", ""), (long) Json.num(m, "s", 0), Json.integer(m, "v", 0),
                        Json.integer(m, "d", 0), Json.integer(m, "k", 0), Json.str(m, "p", ""), Json.str(m, "i", "")));
            }
        }
    }

    private void saveArenaPending() {
        List<Object> list = new ArrayList<>();
        for (ArenaRun r : arenaPending) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("w", r.week);
            m.put("s", (double) r.score);
            m.put("v", r.wave);
            m.put("d", r.seconds);
            m.put("k", r.streak);
            m.put("p", r.splits);
            m.put("i", r.info);
            list.add(m);
        }
        store.put(ARENA_PENDING_KEY, Json.write(list));
    }

    /** Wie viele Arena-Läufe noch auf das Senden warten. */
    public int arenaPendingCount() {
        return arenaPending.size();
    }

    /**
     * Meldet einen beendeten Arena-Lauf. Läufe ohne besiegte Welle zählen nicht. {@code cb} bekommt die Antwort
     * des Servers (Rang, Bestwert) – oder ein Ergebnis mit {@code error = "offline"}, wenn er erst später geht.
     */
    public void submitArena(ArenaRun run, Consumer<ArenaResult> cb) {
        if (run.wave < 1) {
            if (cb != null) {
                cb.accept(new ArenaResult(false, "leer", 0, 0, 0, false));
            }
            return;
        }
        rememberBest(run);
        if (http == null) {
            if (cb != null) {
                cb.accept(new ArenaResult(false, "offline", 0, 0, 0, false));
            }
            return;
        }
        arenaPending.add(run);
        saveArenaPending();
        arenaWaiting = cb;
        arenaWaitingFor = run;
        retryAt = 0;
        if (!sending && store.get(NAME_KEY) != null && !namePending()) {
            sendArena(arenaPending.get(0));
        } else if (cb != null && store.get(NAME_KEY) == null && !namePending()) {
            cb.accept(new ArenaResult(false, "unbekannt", 0, 0, 0, false));
            arenaWaiting = null;
        }
    }

    private void sendArena(final ArenaRun run) {
        String body = "{\"p_geraet\":" + Json.write(device) + ",\"p_woche\":" + Json.write(run.week)
                + ",\"p_punkte\":" + Math.min(Integer.MAX_VALUE, run.score) + ",\"p_welle\":" + run.wave
                + ",\"p_dauer\":" + run.seconds + ",\"p_serie\":" + run.streak + ",\"p_splits\":" + Json.write(run.splits)
                + ",\"p_info\":" + Json.write(run.info) + ",\"p_version\":" + Json.write(neontd.app.Version.NAME) + "}";
        sending = true;
        http.request("POST", URL + "neontd_arena_lauf", headers(), body, (status, text) -> {
            sending = false;
            Map<String, Object> m = Json.asMap(Json.tryParse(text));
            ArenaResult res;
            if (status >= 200 && status < 300 && m != null) {
                boolean ok = Json.bool(m, "ok", false);
                String err = ok ? null : Json.str(m, "fehler", "fehler");
                if ("zu_schnell".equals(err)) {
                    retryAt = clock + 11;
                    return; // gleich noch einmal
                }
                if ("unbekannt".equals(err) && store.get(NAME_KEY) != null) {
                    // Der Server kennt den Namen (noch) nicht: erneut anmelden, dann den Lauf schicken
                    store.put(NAME_PENDING_KEY, store.get(NAME_KEY));
                    return;
                }
                res = new ArenaResult(ok, err, (long) Json.num(m, "best", 0), Json.integer(m, "rang", 0),
                        Json.integer(m, "spieler", 0), Json.bool(m, "rekord", false));
                arenaPending.remove(run);
                saveArenaPending();
                cachedAt = -1e9; // Rangliste neu laden
            } else if (status == 0 || status >= 500) {
                retryAt = clock + 15;
                res = new ArenaResult(false, "offline", 0, 0, 0, false);
            } else {
                arenaPending.remove(run);
                saveArenaPending();
                res = new ArenaResult(false, "fehler", 0, 0, 0, false);
            }
            if (arenaWaiting != null && arenaWaitingFor == run) {
                Consumer<ArenaResult> c = arenaWaiting;
                if (res.ok || !"offline".equals(res.error)) {
                    arenaWaiting = null;
                    arenaWaitingFor = null;
                }
                c.accept(res);
            }
        });
    }

    /** Merkt sich den besten eigenen Lauf der Woche auf dem Gerät (auch ohne Netz, für Vergleich und Anzeige). */
    private void rememberBest(ArenaRun run) {
        long old = arenaBest(run.week);
        if (run.score <= old) {
            return;
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("s", (double) run.score);
        m.put("v", run.wave);
        m.put("k", run.streak);
        m.put("p", run.splits);
        store.put(ARENA_BEST_PREFIX + run.week, Json.write(m));
    }

    /** Eigene Bestpunktzahl der Woche auf diesem Gerät (0 = noch kein Lauf). */
    public long arenaBest(String week) {
        Map<String, Object> m = Json.asMap(Json.tryParse(store.get(ARENA_BEST_PREFIX + week)));
        return m == null ? 0 : (long) Json.num(m, "s", 0);
    }

    /** Welle des eigenen Bestlaufs der Woche. */
    public int arenaBestWave(String week) {
        Map<String, Object> m = Json.asMap(Json.tryParse(store.get(ARENA_BEST_PREFIX + week)));
        return m == null ? 0 : Json.integer(m, "v", 0);
    }

    /** Zwischenstände des eigenen Bestlaufs der Woche. */
    public long[] arenaBestSplits(String week) {
        Map<String, Object> m = Json.asMap(Json.tryParse(store.get(ARENA_BEST_PREFIX + week)));
        return m == null ? new long[0] : parseSplits(Json.str(m, "p", ""));
    }

    static long[] parseSplits(String s) {
        if (s == null || s.isEmpty()) {
            return new long[0];
        }
        String[] parts = s.split(",");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Long.parseLong(parts[i].trim());
            } catch (NumberFormatException e) {
                out[i] = i > 0 ? out[i - 1] : 0;
            }
        }
        return out;
    }

    /** Zuletzt geladene Rangliste der Woche (ohne neu zu laden) oder {@code null}. */
    public List<ArenaEntry> cachedArena(String week) {
        return week.equals(cachedWeek) ? cachedList : null;
    }

    /** Rangliste der Woche (bester Lauf je Spieler); nutzt eine frische Zwischenspeicherung. */
    public void arenaList(String week, boolean force, Consumer<List<ArenaEntry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        if (!force && week.equals(cachedWeek) && cachedList != null && clock - cachedAt < FRESH) {
            cb.accept(cachedList);
            return;
        }
        listWaiters.add(cb);
        if (listLoading) {
            return;
        }
        listLoading = true;
        final String w = week;
        fetchArena(week, out -> {
            listLoading = false;
            if (out != null) {
                cachedWeek = w;
                cachedList = out;
                cachedAt = clock;
            }
            List<Consumer<List<ArenaEntry>>> waiting = new ArrayList<>(listWaiters);
            listWaiters.clear();
            for (Consumer<List<ArenaEntry>> c : waiting) {
                c.accept(out);
            }
        });
    }

    /** Rangliste einer anderen (vergangenen) Woche, ohne Zwischenspeicher – für die Belohnung der Vorwoche. */
    public void arenaPrevious(String week, Consumer<List<ArenaEntry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        fetchArena(week, cb);
    }

    private void fetchArena(String week, Consumer<List<ArenaEntry>> cb) {
        String body = "{\"p_woche\":" + Json.write(week) + "}";
        http.request("POST", URL + "neontd_arena_liste", headers(), body, (status, text) -> {
            List<Object> list = status >= 200 && status < 300 ? Json.asList(Json.tryParse(text)) : null;
            if (list == null) {
                cb.accept(null);
                return;
            }
            List<ArenaEntry> out = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                if (m == null) {
                    continue;
                }
                String k = Json.str(m, "kennung", "");
                out.add(new ArenaEntry(Json.integer(m, "rang", out.size() + 1), k, Json.str(m, "name", "?"),
                        (long) Json.num(m, "punkte", 0), Json.integer(m, "welle", 0), Json.integer(m, "serie", 0),
                        Json.integer(m, "versuche", 1), parseSplits(Json.str(m, "splits", "")), isMine(k),
                        parseTime(Json.str(m, "erstellt", ""))));
            }
            cb.accept(out);
        });
    }

    /** Die letzten Läufe der Woche (neueste zuerst). */
    public void arenaFeed(String week, Consumer<List<FeedEntry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        String body = "{\"p_woche\":" + Json.write(week) + ",\"p_limit\":30}";
        http.request("POST", URL + "neontd_arena_verlauf", headers(), body, (status, text) -> {
            List<Object> list = status >= 200 && status < 300 ? Json.asList(Json.tryParse(text)) : null;
            if (list == null) {
                cb.accept(null);
                return;
            }
            List<FeedEntry> out = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                if (m != null) {
                    out.add(new FeedEntry(Json.str(m, "name", "?"), (long) Json.num(m, "punkte", 0), Json.integer(m, "welle", 0),
                            Json.bool(m, "rekord", false), isMine(Json.str(m, "kennung", "")),
                            parseTime(Json.str(m, "erstellt", ""))));
                }
            }
            cb.accept(out);
        });
    }

    /** Die Sieger der vergangenen Wochen (neueste zuerst). */
    public void arenaHall(Consumer<List<HallEntry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        http.request("POST", URL + "neontd_arena_ruhm", headers(), "{\"p_limit\":26}", (status, text) -> {
            List<Object> list = status >= 200 && status < 300 ? Json.asList(Json.tryParse(text)) : null;
            if (list == null) {
                cb.accept(null);
                return;
            }
            List<HallEntry> out = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                if (m != null) {
                    out.add(new HallEntry(Json.str(m, "woche", ""), Json.str(m, "name", "?"), (long) Json.num(m, "punkte", 0),
                            Json.integer(m, "welle", 0), Json.integer(m, "teilnehmer", 0),
                            isMine(Json.str(m, "kennung", ""))));
                }
            }
            cb.accept(out);
        });
    }

    /** Zeitpunkt "2026-10-29T10:29:08.313529+00:00" in Millisekunden seit 1970 (0 bei Fehlern). */
    static double parseTime(String s) {
        try {
            int y = Integer.parseInt(s.substring(0, 4));
            int mo = Integer.parseInt(s.substring(5, 7));
            int d = Integer.parseInt(s.substring(8, 10));
            int h = Integer.parseInt(s.substring(11, 13));
            int mi = Integer.parseInt(s.substring(14, 16));
            int se = Integer.parseInt(s.substring(17, 19));
            double ms = neontd.progress.Calendar.daysFromCivil(y, mo, d) * neontd.progress.Calendar.DAY_MS
                    + ((h * 60.0 + mi) * 60 + se) * 1000;
            int tz = Math.max(s.lastIndexOf('+'), s.lastIndexOf('-'));
            if (tz > 18 && s.length() >= tz + 6) {
                int oh = Integer.parseInt(s.substring(tz + 1, tz + 3));
                int om = Integer.parseInt(s.substring(tz + 4, tz + 6));
                double off = (oh * 60.0 + om) * 60000;
                ms -= s.charAt(tz) == '+' ? off : -off;
            }
            return ms;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String[] headers() {
        return new String[] {"apikey", KEY, "Authorization", "Bearer " + KEY, "Content-Type", "application/json"};
    }
}
