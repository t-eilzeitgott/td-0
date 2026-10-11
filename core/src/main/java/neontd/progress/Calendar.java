package neontd.progress;

/**
 * Datumsrechnung für Tagesaufgaben und Season (ohne java.time, läuft auch im Browser): Tage seit 1970 und
 * Monats-Kennungen. Ein Tag beginnt um Mitternacht der Ortszeit des Geräts.
 */
public final class Calendar {
    public static final double DAY_MS = 86_400_000.0;
    private static final String[] MONTHS = {"JANUAR", "FEBRUAR", "MÄRZ", "APRIL", "MAI", "JUNI", "JULI", "AUGUST",
        "SEPTEMBER", "OKTOBER", "NOVEMBER", "DEZEMBER"};

    private Calendar() {
    }

    /** Tage seit 1970-01-01 für einen Zeitpunkt (ms seit 1970, UTC) und den Versatz der Ortszeit (ms). */
    public static int dayOf(double nowMillis, double offsetMillis) {
        return (int) Math.floor((nowMillis + offsetMillis) / DAY_MS);
    }

    /** {Jahr, Monat 1–12, Tag 1–31} zu einer Tageszahl seit 1970 (proleptisch gregorianisch). */
    public static int[] civil(int days) {
        int z = days + 719468;
        int era = (z >= 0 ? z : z - 146096) / 146097;
        int doe = z - era * 146097;
        int yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        int y = yoe + era * 400;
        int doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        int mp = (5 * doy + 2) / 153;
        int d = doy - (153 * mp + 2) / 5 + 1;
        int m = mp < 10 ? mp + 3 : mp - 9;
        return new int[] {m <= 2 ? y + 1 : y, m, d};
    }

    /** Tageszahl zu Jahr, Monat (1–12) und Tag. */
    public static int daysFromCivil(int y, int m, int d) {
        int yy = m <= 2 ? y - 1 : y;
        int era = (yy >= 0 ? yy : yy - 399) / 400;
        int yoe = yy - era * 400;
        int mp = m > 2 ? m - 3 : m + 9;
        int doy = (153 * mp + 2) / 5 + d - 1;
        int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** Kennung des Monats einer Tageszahl: Jahr · 12 + Monat − 1 (auch die Season-Nummer). */
    public static int monthId(int day) {
        int[] c = civil(day);
        return c[0] * 12 + c[1] - 1;
    }

    public static String monthName(int monthId) {
        return MONTHS[((monthId % 12) + 12) % 12];
    }

    public static int yearOf(int monthId) {
        return monthId / 12;
    }

    /** Tage vom heutigen bis zum letzten Tag des Monats (heute eingerechnet). */
    public static int daysLeftInMonth(int day) {
        int[] c = civil(day);
        int next = c[1] == 12 ? daysFromCivil(c[0] + 1, 1, 1) : daysFromCivil(c[0], c[1] + 1, 1);
        return next - day;
    }
}
