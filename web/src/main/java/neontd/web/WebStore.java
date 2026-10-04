package neontd.web;

import java.util.HashMap;
import java.util.Map;
import neontd.platform.KeyValueStore;

/** Speichert im localStorage des Browsers; ist der gesperrt (z. B. privater Modus), bleibt es im Arbeitsspeicher. */
final class WebStore implements KeyValueStore {
    private final Map<String, String> fallback = new HashMap<>();

    @Override
    public String get(String key) {
        String v = Js.storageGet(key);
        return v != null ? v : fallback.get(key);
    }

    @Override
    public void put(String key, String value) {
        if (!Js.storageSet(key, value)) {
            fallback.put(key, value);
        } else {
            fallback.remove(key);
        }
    }

    @Override
    public void remove(String key) {
        Js.storageRemove(key);
        fallback.remove(key);
    }
}
