package neontd.desktop;

import java.util.ArrayList;
import java.util.List;
import neontd.save.Http;

/**
 * Nur für das Screenshot-Werkzeug: beantwortet die Datenbank-Funktionen des Spiels mit Beispieldaten (erfundene
 * Vornamen), damit Arena und Bestenlisten in der Dokumentation gefüllt aussehen. Antworten kommen erst beim nächsten
 * {@link #deliver()} – wie im Browser asynchron.
 */
final class DemoHttp implements Http {
    static final String ME = "me0000000000";

    private final List<Runnable> queue = new ArrayList<>();
    /** Angemeldeter Name ({@code null} = noch keiner – wie bei einem neuen Gerät). */
    private String myName;
    /** Hat der eigene Spieler in der Woche schon einen Lauf (und mit welchen Punkten)? */
    long myScore = 248_310;

    @Override
    public void request(String method, String url, String[] headers, String body, Callback cb) {
        String fn = url.substring(url.lastIndexOf('/') + 1);
        String resp = respond(fn, body == null ? "" : body);
        queue.add(() -> cb.done(200, resp));
    }

    /** Liefert die wartenden Antworten aus. */
    void deliver() {
        List<Runnable> now = new ArrayList<>(queue);
        queue.clear();
        for (Runnable r : now) {
            r.run();
        }
    }

    private static String arg(String body, String key) {
        int i = body.indexOf("\"" + key + "\":");
        if (i < 0) {
            return "";
        }
        int s = i + key.length() + 3;
        if (s < body.length() && body.charAt(s) == '"') {
            int e = body.indexOf('"', s + 1);
            return body.substring(s + 1, e);
        }
        int e = s;
        while (e < body.length() && body.charAt(e) != ',' && body.charAt(e) != '}') {
            e++;
        }
        return body.substring(s, e);
    }

    private static String splits(long total, int waves) {
        StringBuilder sb = new StringBuilder();
        for (int k = 1; k <= waves; k++) {
            if (k > 1) {
                sb.append(',');
            }
            sb.append(Math.round(total * Math.pow(k / (double) waves, 3)));
        }
        return sb.toString();
    }

    private String row(int rank, String kennung, String name, long score, int wave, int streak, int tries) {
        return "{\"rang\":" + rank + ",\"kennung\":\"" + kennung + "\",\"name\":\"" + name + "\",\"punkte\":" + score
                + ",\"welle\":" + wave + ",\"serie\":" + streak + ",\"versuche\":" + tries + ",\"splits\":\""
                + splits(score, wave) + "\",\"erstellt\":\"2026-10-28T18:12:00+00:00\"}";
    }

    private String me() {
        return myName == null ? "Neo" : myName;
    }

    private String respond(String fn, String body) {
        switch (fn) {
            case "neontd_login":
                return "{\"ok\":true,\"kennung\":\"" + ME + "\",\"name\":" + (myName == null ? "null" : "\"" + myName + "\"")
                        + ",\"woche\":\"2026-W44\"}";
            case "neontd_anmelden":
                myName = arg(body, "p_name");
                return "{\"ok\":true,\"kennung\":\"" + ME + "\",\"name\":\"" + myName + "\"}";
            case "neontd_arena_lauf":
                return "{\"ok\":true,\"best\":" + Math.max(myScore, Long.parseLong(arg(body, "p_punkte")))
                        + ",\"rang\":2,\"spieler\":7,\"rekord\":true}";
            case "neontd_arena_liste": {
                boolean prev = !arg(body, "p_woche").endsWith("W44");
                List<String> rows = new ArrayList<>();
                rows.add(row(1, "a1", "Mia", 312_450, 24, 20, 9));
                if (prev) {
                    rows.add(row(2, ME, me(), 290_120, 23, 20, 6));
                    rows.add(row(3, "a2", "Tobi", 251_800, 22, 14, 4));
                } else {
                    rows.add(row(2, ME, me(), myScore, 22, 20, 7));
                    rows.add(row(3, "a2", "Tobi", 231_900, 21, 17, 4));
                }
                rows.add(row(4, "a3", "Lena", 198_340, 20, 12, 11));
                rows.add(row(5, "a4", "Kai", 156_020, 19, 19, 3));
                rows.add(row(6, "a5", "Jonas", 121_700, 17, 9, 2));
                rows.add(row(7, "a6", "Ela", 88_410, 15, 15, 1));
                return "[" + String.join(",", rows) + "]";
            }
            case "neontd_arena_verlauf":
                return "[{\"kennung\":\"a1\",\"name\":\"Mia\",\"punkte\":312450,\"welle\":24,\"rekord\":true,\"erstellt\":\"2026-10-29T10:41:00+00:00\"},"
                        + "{\"kennung\":\"a3\",\"name\":\"Lena\",\"punkte\":143200,\"welle\":18,\"rekord\":false,\"erstellt\":\"2026-10-29T10:12:00+00:00\"},"
                        + "{\"kennung\":\"" + ME + "\",\"name\":\"" + me() + "\",\"punkte\":248310,\"welle\":22,\"rekord\":true,\"erstellt\":\"2026-10-29T08:55:00+00:00\"},"
                        + "{\"kennung\":\"a2\",\"name\":\"Tobi\",\"punkte\":231900,\"welle\":21,\"rekord\":true,\"erstellt\":\"2026-10-28T21:30:00+00:00\"},"
                        + "{\"kennung\":\"a4\",\"name\":\"Kai\",\"punkte\":156020,\"welle\":19,\"rekord\":true,\"erstellt\":\"2026-10-28T19:02:00+00:00\"},"
                        + "{\"kennung\":\"a1\",\"name\":\"Mia\",\"punkte\":276800,\"welle\":23,\"rekord\":true,\"erstellt\":\"2026-10-28T17:40:00+00:00\"}]";
            case "neontd_arena_ruhm":
                return "[{\"woche\":\"2026-W43\",\"kennung\":\"a1\",\"name\":\"Mia\",\"punkte\":290120,\"welle\":23,\"teilnehmer\":7},"
                        + "{\"woche\":\"2026-W42\",\"kennung\":\"" + ME + "\",\"name\":\"" + me() + "\",\"punkte\":268400,\"welle\":22,\"teilnehmer\":6},"
                        + "{\"woche\":\"2026-W41\",\"kennung\":\"a1\",\"name\":\"Mia\",\"punkte\":301770,\"welle\":24,\"teilnehmer\":5}]";
            case "neontd_liste":
                return "[{\"kennung\":\"a1\",\"name\":\"Mia\",\"punkte\":41,\"info\":\"Boulevard\"},"
                        + "{\"kennung\":\"" + ME + "\",\"name\":\"" + me() + "\",\"punkte\":37,\"info\":\"Grotte\"},"
                        + "{\"kennung\":\"a2\",\"name\":\"Tobi\",\"punkte\":29,\"info\":\"Kessel\"}]";
            default:
                return "{\"ok\":true}";
        }
    }
}
