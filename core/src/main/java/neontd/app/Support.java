package neontd.app;

/** Freiwillige Unterstützung über Ko-fi: leer lassen, dann zeigt die App nichts an. */
public final class Support {
    public static final String URL = "https://ko-fi.com/talfahrt";

    private Support() {
    }

    public static boolean enabled() {
        return !URL.isEmpty();
    }
}
