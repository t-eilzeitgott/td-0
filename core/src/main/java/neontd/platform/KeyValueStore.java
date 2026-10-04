package neontd.platform;

/** Einfacher, dauerhafter Schlüssel-Wert-Speicher (Browser: localStorage, Desktop: Datei). */
public interface KeyValueStore {
    /** @return der gespeicherte Text oder {@code null} */
    String get(String key);

    void put(String key, String value);

    void remove(String key);
}
