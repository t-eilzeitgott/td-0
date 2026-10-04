package neontd.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import neontd.platform.KeyValueStore;

/** Dauerhafter Speicher in einer Datei im Benutzerordner ({@code ~/.neon-td/save.properties}). */
final class FileStore implements KeyValueStore {
    private final Path file;
    private final Properties props = new Properties();

    FileStore() {
        Path dir = Paths.get(System.getProperty("user.home", "."), ".neon-td");
        this.file = dir.resolve("save.properties");
        try {
            Files.createDirectories(dir);
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            System.err.println("Speicherstand konnte nicht gelesen werden: " + e.getMessage());
        }
    }

    @Override
    public synchronized String get(String key) {
        return props.getProperty(key);
    }

    @Override
    public synchronized void put(String key, String value) {
        props.setProperty(key, value);
        flush();
    }

    @Override
    public synchronized void remove(String key) {
        props.remove(key);
        flush();
    }

    private void flush() {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Neon TD");
        } catch (IOException e) {
            System.err.println("Speichern fehlgeschlagen: " + e.getMessage());
        }
    }
}
