package neontd.save;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import neontd.platform.KeyValueStore;

/**
 * Globale Bestenlisten über eine kleine Supabase-Tabelle (nur Einfügen und Lesen; der öffentliche Schlüssel darf
 * nichts anderes). Jedes Gerät hat eine zufällige Kennung; gezählt wird je Liste und Gerät der beste Eintrag.
 * Listen: {@code daily-<Tag>} (Tages-Challenge, Wellen), {@code endless} (beste Endlos-Welle), {@code stars}
 * (Sterne insgesamt). Alles ist best-effort: ohne Netz passiert einfach nichts, das Spiel merkt sich den
 * ausstehenden Eintrag und versucht es später erneut.
 */
public final class Leaderboard {
    /** Ein Eintrag einer Liste. */
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

    public static final String BOARD_ENDLESS = "endless";
    public static final String BOARD_STARS = "stars";

    private static final String URL = "https://obwtpegujjuwcsvmadqg.supabase.co/rest/v1/";
    private static final String KEY = "sb_publishable_VDRVzKlPbfNZrUV-95d0ag_D6FtDC4j";
    private static final String DEVICE_KEY = "neontd.device";
    private static final String PENDING_KEY = "neontd.board.pending";
    private static final String SENT_KEY = "neontd.board.sent";

    private final Http http;
    private final KeyValueStore store;
    private final String device;
    /** Zuletzt gesendete Punkte je Liste (damit nichts doppelt oder schlechter geschickt wird). */
    private final Map<String, Integer> sent = new java.util.TreeMap<>();
    /** Ausstehende Einträge: Liste → Punkte (werden beim nächsten Versuch geschickt). */
    private final Map<String, Integer> pending = new java.util.TreeMap<>();
    private final Map<String, String> pendingInfo = new java.util.TreeMap<>();
    private boolean sending;

    public Leaderboard(Http http, KeyValueStore store) {
        this.http = http;
        this.store = store;
        String id = store.get(DEVICE_KEY);
        if (id == null || id.length() < 8) {
            id = randomId(store);
            store.put(DEVICE_KEY, id);
        }
        this.device = id;
        load(SENT_KEY, sent);
        load(PENDING_KEY, pending);
    }

    private static String randomId(KeyValueStore store) {
        StringBuilder sb = new StringBuilder("d");
        long seed = (long) (Math.random() * 4_000_000_000L) ^ System.nanoTime();
        for (int i = 0; i < 20; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            sb.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt((int) ((seed >>> 33) % 36)));
        }
        return sb.toString();
    }

    private void load(String key, Map<String, Integer> into) {
        Object o = Json.tryParse(store.get(key));
        Map<String, Object> m = Json.asMap(o);
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

    /** Schickt ausstehende Einträge (einen je Aufruf). Vom Spiel regelmäßig aufgerufen. */
    public void flush(String playerName) {
        if (http == null || sending || pending.isEmpty()) {
            return;
        }
        final String board = pending.keySet().iterator().next();
        final int score = pending.get(board);
        String info = pendingInfo.get(board);
        String name = cleanName(playerName);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"board\":").append(Json.write(board)).append(",\"geraet\":").append(Json.write(device))
                .append(",\"name\":").append(Json.write(name)).append(",\"punkte\":").append(score);
        if (info != null && !info.isEmpty()) {
            sb.append(",\"info\":").append(Json.write(info.length() > 60 ? info.substring(0, 60) : info));
        }
        sb.append('}');
        sending = true;
        http.request("POST", URL + "neontd_bestenliste", headers("return=minimal"), sb.toString(), (status, body) -> {
            sending = false;
            if (status >= 200 && status < 300) {
                pending.remove(board);
                pendingInfo.remove(board);
                sent.put(board, score);
                save(PENDING_KEY, pending);
                save(SENT_KEY, sent);
            } else if (status >= 400 && status < 500) {
                // abgelehnt (z. B. ungültige Daten): nicht ewig wiederholen
                pending.remove(board);
                pendingInfo.remove(board);
                save(PENDING_KEY, pending);
            }
        });
    }

    /** Lädt die besten Einträge einer Liste (höchstens 50). */
    public void top(String board, Consumer<List<Entry>> cb) {
        if (http == null) {
            cb.accept(null);
            return;
        }
        String url = URL + "neontd_top?select=name,punkte,info,geraet&board=eq." + board
                + "&order=punkte.desc,erstellt.asc&limit=50";
        http.request("GET", url, headers(null), null, (status, body) -> {
            if (status < 200 || status >= 300) {
                cb.accept(null);
                return;
            }
            List<Object> list = Json.asList(Json.tryParse(body));
            if (list == null) {
                cb.accept(null);
                return;
            }
            List<Entry> out = new ArrayList<>();
            for (Object o : list) {
                Map<String, Object> m = Json.asMap(o);
                if (m == null) {
                    continue;
                }
                out.add(new Entry(Json.str(m, "name", "?"), Json.integer(m, "punkte", 0), Json.str(m, "info", ""),
                        device.equals(Json.str(m, "geraet", ""))));
            }
            cb.accept(out);
        });
    }

    private static String[] headers(String prefer) {
        List<String> h = new ArrayList<>();
        h.add("apikey");
        h.add(KEY);
        h.add("Authorization");
        h.add("Bearer " + KEY);
        h.add("Content-Type");
        h.add("application/json");
        if (prefer != null) {
            h.add("Prefer");
            h.add(prefer);
        }
        return h.toArray(new String[0]);
    }

    static String cleanName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty()) {
            n = "Spieler";
        }
        return n.length() > 16 ? n.substring(0, 16) : n;
    }
}
