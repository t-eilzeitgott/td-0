package neontd.save;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import neontd.platform.KeyValueStore;
import neontd.progress.Progress;

/**
 * Lokaler Speicher für Spielerprofil, gespeicherte Läufe und die Cloud-Einstellungen – oben auf dem
 * {@link KeyValueStore} (Browser: localStorage, Desktop: Datei).
 */
public final class SaveStore {
    private static final String PROFILE = "neontd.profile";
    private static final String RUN_PREFIX = "neontd.run.";
    private static final String RUN_INDEX = "neontd.runs";
    private static final String CLOUD_PREFIX = "neontd.cloud.";

    private final KeyValueStore kv;
    private final DoubleSupplier clock;

    public SaveStore(KeyValueStore kv, DoubleSupplier clock) {
        this.kv = kv;
        this.clock = clock;
    }

    public double now() {
        return clock.getAsDouble();
    }

    // ------------------------------------------------------------------------------------------- Profil

    public Progress loadProgress() {
        String raw = kv.get(PROFILE);
        if (raw == null || raw.isEmpty()) {
            return new Progress();
        }
        return ProfileJson.fromMap(Json.asMap(Json.tryParse(raw)));
    }

    /** Speichert das Profil und stempelt es mit der aktuellen Zeit. */
    public void saveProgress(Progress p) {
        p.updated = now();
        kv.put(PROFILE, Json.write(ProfileJson.toMap(p)));
    }

    /** Übernimmt ein (zusammengeführtes) Profil unverändert, inklusive seines Zeitstempels. */
    public void putProgress(Progress p) {
        kv.put(PROFILE, Json.write(ProfileJson.toMap(p)));
    }

    // -------------------------------------------------------------------------------------------- Läufe

    private List<String> runIds() {
        List<String> list = new ArrayList<>();
        String raw = kv.get(RUN_INDEX);
        if (raw != null && !raw.isEmpty()) {
            for (String id : raw.split(",")) {
                if (!id.isEmpty() && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    /** Kennungen aller Level, zu denen ein Lauf oder ein Ende-Marker gespeichert ist. */
    public List<String> runLevelIds() {
        return runIds();
    }

    /** Der gespeicherte Lauf eines Levels oder {@code null}. Auch beendete Läufe (Marker) werden geliefert. */
    public RunSave loadRun(String levelId) {
        return RunSave.decode(levelId, kv.get(RUN_PREFIX + levelId));
    }

    /** Der fortsetzbare Lauf eines Levels oder {@code null}. */
    public RunSave resumable(String levelId) {
        RunSave r = loadRun(levelId);
        return r != null && r.resumable() ? r : null;
    }

    public void saveRun(RunSave r) {
        putRun(r);
    }

    /** Markiert den Lauf als beendet (Sieg, Niederlage oder bewusst neu gestartet). */
    public void endRun(String levelId) {
        if (kv.get(RUN_PREFIX + levelId) != null) {
            putRun(RunSave.ended(levelId, now()));
        }
    }

    /** Übernimmt einen Lauf unverändert (Cloud-Abgleich). */
    public void putRun(RunSave r) {
        kv.put(RUN_PREFIX + r.levelId, r.encode());
        List<String> ids = runIds();
        if (!ids.contains(r.levelId)) {
            ids.add(r.levelId);
            kv.put(RUN_INDEX, String.join(",", ids));
        }
    }

    // --------------------------------------------------------------------------------------- Cloud-Daten

    public String cloud(String key) {
        String v = kv.get(CLOUD_PREFIX + key);
        return v == null ? "" : v;
    }

    public void setCloud(String key, String value) {
        if (value == null || value.isEmpty()) {
            kv.remove(CLOUD_PREFIX + key);
        } else {
            kv.put(CLOUD_PREFIX + key, value);
        }
    }

    public boolean cloudConnected() {
        return !cloud("token").isEmpty();
    }
}
