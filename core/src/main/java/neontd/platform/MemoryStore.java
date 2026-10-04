package neontd.platform;

import java.util.HashMap;
import java.util.Map;

/** Speicher nur im Arbeitsspeicher – für Tests und als Notlösung, wenn der echte Speicher gesperrt ist. */
public final class MemoryStore implements KeyValueStore {
    private final Map<String, String> data = new HashMap<>();

    @Override
    public String get(String key) {
        return data.get(key);
    }

    @Override
    public void put(String key, String value) {
        data.put(key, value);
    }

    @Override
    public void remove(String key) {
        data.remove(key);
    }
}
