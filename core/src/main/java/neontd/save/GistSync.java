package neontd.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neontd.level.LevelStore;
import neontd.progress.Progress;

/**
 * Gleicht den Spielstand mit einem <b>privaten GitHub-Gist</b> des Spielers ab. Das ist der einzige Ort bei
 * GitHub, an dem sich pro Nutzer Daten speichern lassen, ohne dass es dafür einen eigenen Server braucht. Der
 * Spieler legt einmalig ein Zugriffstoken (nur Berechtigung <i>Gists</i>) an; es bleibt ausschließlich im lokalen
 * Speicher dieses Geräts und geht nur an {@code api.github.com}.
 *
 * <p>Ablauf von {@link #sync}: Gist lesen → mit dem lokalen Stand zusammenführen ({@link SaveBundle#merge}) →
 * lokal übernehmen → bei Abweichung zurückschreiben. Es geht dabei nie Fortschritt verloren, auch wenn zwei
 * Geräte parallel gespielt haben.
 */
public final class GistSync {
    public static final String API = "https://api.github.com";
    public static final String FILE = "neon-td-save.json";
    public static final String DESCRIPTION = "Neon TD Spielstand";
    /** Mindestabstand zwischen automatischen Abgleichen (Sekunden). */
    public static final double AUTO_INTERVAL = 90;

    public enum Status { IDLE, WORKING, OK, ERROR }

    /** Wird nach jedem Abschluss aufgerufen (Anzeige aktualisieren). */
    public interface Listener {
        void changed(GistSync sync);
    }

    private final Http http;
    private final SaveStore saves;
    private final LevelStore levels;
    private final Progress live;

    public Status status = Status.IDLE;
    /** Verständliche Meldung zum letzten Ergebnis. */
    public String message = "";
    public Listener listener;
    private boolean again;
    private boolean dirty;
    private double lastStart = -1e12;
    private int depth;

    public GistSync(Http http, SaveStore saves, LevelStore levels, Progress live) {
        this.http = http;
        this.saves = saves;
        this.levels = levels;
        this.live = live;
    }

    public boolean available() {
        return http != null;
    }

    public boolean connected() {
        return saves.cloudConnected();
    }

    public String user() {
        return saves.cloud("user");
    }

    public double lastOk() {
        String s = saves.cloud("lastOk");
        try {
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------------------------------ Anmelden

    /** Prüft ein Token und verbindet, wenn es gültig ist; gleicht danach sofort ab. */
    public void connect(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (http == null) {
            finish(Status.ERROR, "Cloud-Speicher wird auf diesem Gerät nicht unterstützt.");
            return;
        }
        if (token.isEmpty() || token.length() < 20 || token.indexOf(' ') >= 0) {
            finish(Status.ERROR, "Das sieht nicht wie ein GitHub-Token aus.");
            return;
        }
        status = Status.WORKING;
        message = "Prüfe Token …";
        notifyChanged();
        http.request("GET", API + "/user", headers(token), null, (code, body) -> {
            if (code != 200) {
                finish(Status.ERROR, describe(code, body, "Token abgelehnt"));
                return;
            }
            Map<String, Object> user = Json.asMap(Json.tryParse(body));
            saves.setCloud("token", token);
            saves.setCloud("user", Json.str(user, "login", ""));
            saves.setCloud("gist", "");
            if (live.name.isEmpty() && !saves.cloud("user").isEmpty()) {
                live.name = Progress.cleanName(saves.cloud("user"));
                saves.saveProgress(live);
            }
            status = Status.IDLE;
            sync();
        });
    }

    public void disconnect() {
        saves.setCloud("token", "");
        saves.setCloud("user", "");
        saves.setCloud("gist", "");
        saves.setCloud("lastOk", "");
        status = Status.IDLE;
        message = "Getrennt. Dein Spielstand bleibt auf diesem Gerät.";
        notifyChanged();
    }

    // -------------------------------------------------------------------------------- Automatik

    /** Merkt vor, dass sich etwas geändert hat; {@link #tick} gleicht bei Gelegenheit ab. */
    public void markDirty() {
        dirty = true;
    }

    /** Regelmäßig aufrufen (z. B. jede Sekunde): gleicht ab, wenn etwas offen ist und genug Zeit verging. */
    public void tick() {
        if (dirty && connected() && status != Status.WORKING && saves.now() - lastStart > AUTO_INTERVAL * 1000) {
            sync();
        }
    }

    /** Sofortiger Abgleich (z. B. beim Verlassen der Seite), unabhängig vom Mindestabstand. */
    public void flush() {
        if (dirty && connected() && status != Status.WORKING) {
            sync();
        }
    }

    // ------------------------------------------------------------------------------------------ Abgleich

    public void sync() {
        if (http == null || !connected()) {
            return;
        }
        if (status == Status.WORKING) {
            again = true;
            return;
        }
        status = Status.WORKING;
        message = "Gleiche ab …";
        dirty = false;
        lastStart = saves.now();
        depth = 0;
        notifyChanged();
        String id = saves.cloud("gist");
        if (id.isEmpty()) {
            findGist(1);
        } else {
            readGist(id);
        }
    }

    private String[] headers(String token) {
        return new String[] {
            "Accept", "application/vnd.github+json",
            "Authorization", "Bearer " + token,
            "X-GitHub-Api-Version", "2022-11-28",
            "Content-Type", "application/json"
        };
    }

    private String[] headers() {
        return headers(saves.cloud("token"));
    }

    /** Sucht den eigenen Spielstand-Gist (höchstens 3 Seiten à 100 Gists). */
    private void findGist(int page) {
        http.request("GET", API + "/gists?per_page=100&page=" + page, headers(), null, (code, body) -> {
            if (code != 200) {
                finish(Status.ERROR, describe(code, body, "Gists konnten nicht gelesen werden"));
                return;
            }
            List<Object> list = Json.asList(Json.tryParse(body));
            if (list != null) {
                for (Object o : list) {
                    Map<String, Object> g = Json.asMap(o);
                    Map<String, Object> files = Json.map(g, "files");
                    if (files != null && files.containsKey(FILE) && !Json.str(g, "id", "").isEmpty()) {
                        saves.setCloud("gist", Json.str(g, "id", ""));
                        readGist(Json.str(g, "id", ""));
                        return;
                    }
                }
            }
            if (list != null && list.size() >= 100 && page < 3) {
                findGist(page + 1);
            } else {
                push(null, null); // noch keiner: neu anlegen
            }
        });
    }

    private void readGist(String id) {
        http.request("GET", API + "/gists/" + id, headers(), null, (code, body) -> {
            if (code == 404 && depth++ == 0) {
                saves.setCloud("gist", ""); // gelöscht? Dann neu suchen/anlegen.
                findGist(1);
                return;
            }
            if (code != 200) {
                finish(Status.ERROR, describe(code, body, "Spielstand konnte nicht geladen werden"));
                return;
            }
            Map<String, Object> g = Json.asMap(Json.tryParse(body));
            Map<String, Object> file = Json.map(Json.map(g, "files"), FILE);
            SaveBundle remote = null;
            if (file != null) {
                if (Json.bool(file, "truncated", false)) {
                    finish(Status.ERROR, "Der Spielstand in der Cloud ist zu groß zum Laden.");
                    return;
                }
                remote = SaveBundle.decode(Json.str(file, "content", ""));
            }
            push(id, remote);
        });
    }

    /** Führt zusammen, übernimmt lokal und schreibt bei Bedarf zurück. */
    private void push(String id, SaveBundle remote) {
        SaveBundle local = SaveBundle.collect(saves, levels);
        SaveBundle merged = remote == null ? local : SaveBundle.merge(local, remote);
        merged.applyTo(saves, levels);
        live.copyFrom(merged.profile);
        if (remote != null && merged.sameContent(remote)) {
            ok("Synchronisiert – alles auf dem neuesten Stand.");
            return;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("content", merged.encode());
        Map<String, Object> files = new LinkedHashMap<>();
        files.put(FILE, content);
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("files", files);
        String method;
        String url;
        if (id == null) {
            req.put("description", DESCRIPTION);
            req.put("public", Boolean.FALSE);
            method = "POST";
            url = API + "/gists";
        } else {
            method = "PATCH";
            url = API + "/gists/" + id;
        }
        http.request(method, url, headers(), Json.write(req), (code, body) -> {
            if (code != 200 && code != 201) {
                finish(Status.ERROR, describe(code, body, "Spielstand konnte nicht gespeichert werden"));
                return;
            }
            if (id == null) {
                Map<String, Object> g = Json.asMap(Json.tryParse(body));
                saves.setCloud("gist", Json.str(g, "id", ""));
            }
            ok(remote == null ? "Cloud-Speicher angelegt und gesichert." : "Synchronisiert und gesichert.");
        });
    }

    // --------------------------------------------------------------------------------------- Ergebnis

    private void ok(String msg) {
        saves.setCloud("lastOk", Double.toString(saves.now()));
        finish(Status.OK, msg);
    }

    private void finish(Status s, String msg) {
        status = s;
        message = msg;
        notifyChanged();
        if (again && s == Status.OK) {
            again = false;
            sync();
        } else {
            again = false;
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.changed(this);
        }
    }

    /** Übersetzt Fehlercodes in eine verständliche Meldung. */
    static String describe(int code, String body, String what) {
        switch (code) {
            case 0:
                return "Keine Verbindung zu GitHub. " + what + ".";
            case 401:
                return "Token ungültig oder abgelaufen – bitte neu verbinden.";
            case 403:
                return "GitHub verweigert den Zugriff (fehlende Berechtigung „Gists“ oder Limit erreicht).";
            case 404:
                return "Nicht gefunden – hat das Token die Berechtigung „Gists“?";
            case 422:
                return "GitHub hat die Anfrage abgelehnt (" + what + ").";
            default:
                return what + " (Fehler " + code + ").";
        }
    }
}
