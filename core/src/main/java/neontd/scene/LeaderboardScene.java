package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Daily;
import neontd.save.Leaderboard;
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Kit;

/**
 * Bestenlisten: Tages-Challenge (Welle), Endlos (beste Welle überhaupt) und Sterne. Die Listen kommen aus der
 * globalen Tabelle; der eigene Eintrag ist hervorgehoben. Ohne Netz zeigt die Seite das ehrlich an.
 */
public final class LeaderboardScene extends PanelScene {
    private static final String[] BOARDS = {"", Leaderboard.BOARD_ENDLESS, Leaderboard.BOARD_STARS};

    private final List<Leaderboard.Entry>[] lists;
    private final boolean[] loading = new boolean[3];
    private final boolean[] failed = new boolean[3];
    private String daily;

    @SuppressWarnings("unchecked")
    public LeaderboardScene(App app) {
        super(app, "Bestenliste", Theme.MAGENTA);
        lists = new List[3];
        daily = Daily.today(app.today()).id();
        BOARDS[0] = daily;
        setTabs("Heute", "Endlos", "Sterne");
        load(0);
    }

    @Override
    protected void onTab(int i) {
        if (lists[i] == null && !loading[i]) {
            load(i);
        }
    }

    private void load(int i) {
        if (!app.board.available()) {
            failed[i] = true;
            return;
        }
        loading[i] = true;
        failed[i] = false;
        app.board.top(BOARDS[i], list -> {
            loading[i] = false;
            if (list == null) {
                failed[i] = true;
            } else {
                lists[i] = list;
            }
        });
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        String[] heads = {"Tages-Challenge  ·  " + Daily.today(app.today()).level.name, "Beste Endlos-Welle", "Sterne insgesamt"};
        String[] units = {"Welle", "Welle", "★"};
        g.text(heads[tab], x, y + 8 * u, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 30 * u;
        List<Leaderboard.Entry> list = lists[tab];
        if (list == null || list.isEmpty()) {
            String msg = list != null ? "Noch keine Einträge – sei der Erste!"
                    : (loading[tab] ? "Lädt …" : (failed[tab] ? "Keine Verbindung" : "Noch keine Einträge."));
            double h = 140 * u;
            Icons.draw(g, Icon.BOARD, x + w / 2, y + 44 * u, 16 * u, Colors.withAlpha(Theme.MAGENTA, 0.6), 0);
            g.text(msg, x + w / 2, y + 86 * u, Kit.T_ROW * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            if (list == null && failed[tab] && app.board.available()) {
                g.text("Zum erneuten Laden tippen", x + w / 2, y + 112 * u, Kit.T_CAPTION * u, Theme.ACCENT, Gfx.ALIGN_CENTER,
                        false);
                hit(x, y, w, h, "retry");
            }
            return y + h - y0 + 20 * u;
        }
        for (int i = 0; i < list.size(); i++) {
            Leaderboard.Entry e = list.get(i);
            y += rankRow(g, x, y, w, u, i + 1, e.name, e.info, Fmt.compact(e.score) + " " + units[tab], e.mine, Theme.MAGENTA,
                    i < list.size() - 1);
        }
        y += 24 * u;
        y += Kit.paragraph(g, "Einträge zählen je Gerät den besten Lauf. Den Namen änderst du im Profil.", x, y, w,
                Kit.T_CAPTION * u, Theme.MUTED, u);
        return y + 24 * u - y0;
    }

    /** Ranglistenzeile: Platz in eigener Spalte, Name fett, Wert rechts; der eigene Eintrag in Akzentfarbe mit Punkt. */
    static double rankRow(Gfx g, double x, double y, double w, double u, int rank, String name, String info, String value,
                          boolean mine, int accent, boolean divider) {
        double h = (info == null || info.isEmpty() ? 56 : 64) * u;
        int rc = rank == 1 ? Theme.STAR : (rank == 2 ? 0xC9D1E6 : (rank == 3 ? 0xE08A3C : Theme.MUTED));
        g.text(Integer.toString(rank), x + 14 * u, y + h / 2, (rank <= 3 ? Kit.T_ROW : Kit.T_BODY) * u, rc, Gfx.ALIGN_CENTER,
                true);
        double tx = x + 44 * u;
        double vw = g.textWidth(value, Kit.T_BODY * u, true) + 16 * u;
        int nc = mine ? Colors.lighten(accent, 0.3) : Theme.TEXT;
        double nameW = x + w - vw - tx - (mine ? 18 * u : 0);
        boolean hasInfo = info != null && !info.isEmpty();
        double ny = y + h / 2 - (hasInfo ? 10 * u : 0);
        Draw.cut(g, name, tx, ny, Kit.T_ROW * u, nameW, nc, Gfx.ALIGN_LEFT, true);
        if (mine) {
            double nw = Math.min(g.textWidth(name, Kit.T_ROW * u, true), nameW);
            g.fillCircle(tx + nw + 10 * u, ny, 3.5 * u, accent);
        }
        if (hasInfo) {
            Draw.cut(g, info, tx, y + h / 2 + 12 * u, Kit.T_CAPTION * u, x + w - vw - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        }
        g.text(value, x + w, y + h / 2, Kit.T_BODY * u, mine ? nc : Theme.TEXT, Gfx.ALIGN_RIGHT, true);
        if (divider) {
            Kit.hairline(g, tx, y + h - 1, x + w - tx);
        }
        return h;
    }

    @Override
    protected void onTap(Object tag) {
        if ("retry".equals(tag)) {
            load(tab);
        }
    }
}
