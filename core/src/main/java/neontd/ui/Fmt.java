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

    /** Wie {@link #compact(int)} für Beträge bis 9·10¹⁵: ab einer Milliarde „1.2B“ / „123B“, dann „T“ (10¹²) und „Qa“ (10¹⁵). */
    public static String compact(double v) {
        if (v < 0) {
            return "-" + compact(-v);
        }
        if (v < 1_000_000_000.0) {
            return compact((int) v);
        }
        String[] unit = {"B", "T", "Qa"};
        double scale = 1_000_000_000.0;
        int i = 0;
        while (i < 2 && v >= scale * 1000) {
            scale *= 1000;
            i++;
        }
        double x = v / scale;
        if (x >= 10) {
            return (int) x + unit[i];
        }
        double t = Math.floor(x * 10) / 10; // eine Nachkommastelle, ohne Aufrunden
        return (int) t + "." + (int) Math.round((t - Math.floor(t)) * 10) + unit[i];
    }

    public static String tight(double v) {
        return v < 1_000_000_000.0 ? tight((int) Math.max(0, v)) : compact(v);
    }

    /** Ganze Zahl ohne Exponent (auch über dem int-Bereich), z. B. für Debug-Ausgaben. */
    public static String whole(double v) {
        if (v < 2_000_000_000.0) {
            return Integer.toString((int) v);
        }
        StringBuilder sb = new StringBuilder();
        double a = Math.floor(v);
        while (a >= 1) {
            double q = Math.floor(a / 10);
            sb.append((char) ('0' + (int) (a - q * 10)));
            a = q;
        }
        return sb.reverse().toString();
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
