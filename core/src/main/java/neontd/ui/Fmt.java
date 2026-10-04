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

    /** Wie {@link #compact(int)} für Geldbeträge bis 9,99 Mrd.: ab einer Milliarde „1.2B“ … „9.9B“, darüber „10B“. */
    public static String compact(double v) {
        if (v < 0) {
            return "-" + compact(-v);
        }
        if (v < 1_000_000_000.0) {
            return compact((int) v);
        }
        double b = Math.floor(v / 100_000_000.0) / 10; // eine Nachkommastelle, ohne Aufrunden
        return b >= 10 ? "10B" : (int) b + "." + (int) Math.round((b - Math.floor(b)) * 10) + "B";
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
