package neontd.app;

/** Freiwillige Unterstützung über Ko-fi: leer lassen, dann zeigt die App nichts an. */
public final class Support {
    public static final String URL = "https://ko-fi.com/talfahrt";

    private Support() {
    }

    /** Adresse des eingebetteten Formulars (ohne Feed, für ein Sheet in der App). */
    public static String embedUrl() {
        return URL + "/?hidefeed=true&widget=true&embed=true&preview=true";
    }

    public static boolean enabled() {
        return !URL.isEmpty();
    }
}
