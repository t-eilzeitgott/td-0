package neontd.ui;

/** Zahlen kurz und gut lesbar darstellen (für Geld, Preise, Punkte). */
public final class Fmt {
    private Fmt() {
    }

    /** Bis 99 999 ausgeschrieben, darüber 123K / 1.2M / 12M / 1.2B. */
    public static String compact(int v) {
        if (v < 0) {
            return "-" + compact(-v);
        }
        if (v < 100_000) {
            return Integer.toString(v);
        }
        if (v < 1_000_000) {
            return (v / 1000) + "K";
        }
        if (v < 10_000_000) {
            return (v / 1_000_000) + "." + ((v % 1_000_000) / 100_000) + "M";
        }
        if (v < 1_000_000_000) {
            return (v / 1_000_000) + "M";
        }
        return (v / 1_000_000_000) + "." + ((v % 1_000_000_000) / 100_000_000) + "B";
    }

    /** Wie {@link #compact}, aber schon ab 10 000 mit K (für schmale Plätze). */
    public static String tight(int v) {
        if (v < 10_000) {
            return Integer.toString(Math.max(0, v));
        }
        if (v < 100_000) {
            return (v / 1000) + "." + ((v % 1000) / 100) + "K";
        }
        return compact(v);
    }
}
