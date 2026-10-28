package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Daily;
import neontd.save.Leaderboard;
import neontd.ui.Draw;
import neontd.ui.Fmt;

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
        double pad = 14 * u;
        String[] heads = {"Tages-Challenge · " + Daily.today(app.today()).level.name, "Beste Endlos-Welle", "Sterne insgesamt"};
        String[] units = {"Welle", "Welle", "★"};
        g.text(heads[tab], x + 2 * u, y + 10 * u, 13 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 30 * u;
        List<Leaderboard.Entry> list = lists[tab];
        if (list == null) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            String msg = loading[tab] ? "Lädt …" : (failed[tab] ? "Keine Verbindung – später noch einmal versuchen."
                    : "Noch keine Einträge.");
            g.text(msg, x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            if (failed[tab] && app.board.available()) {
                hit(x, y, w, h, "retry");
            }
            return y + h - y0 + 20 * u;
        }
        if (list.isEmpty()) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            g.text("Noch keine Einträge – sei der Erste!", x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            return y + h - y0 + 20 * u;
        }
        double rowH = 46 * u;
        double h = list.size() * rowH;
        Draw.card(g, x, y, w, h, 16 * u, 0);
        for (int i = 0; i < list.size(); i++) {
            Leaderboard.Entry e = list.get(i);
            double ry = y + i * rowH;
            if (e.mine) {
                g.fillRoundRect(x + 4 * u, ry + 3 * u, w - 8 * u, rowH - 6 * u, 10 * u, Colors.withAlpha(Theme.MAGENTA, 0.12));
            }
            int c = i == 0 ? Theme.STAR : (i == 1 ? 0xC9D1E6 : (i == 2 ? 0xE08A3C : Theme.MUTED));
            g.text(Integer.toString(i + 1), x + pad + 10 * u, ry + rowH / 2, 14 * u, c, Gfx.ALIGN_CENTER, true);
            Draw.fit(g, e.name, x + pad + 32 * u, ry + rowH / 2 - (e.info.isEmpty() ? 0 : 7 * u), 15 * u, w - 150 * u,
                    e.mine ? Colors.lighten(Theme.MAGENTA, 0.4) : Theme.TEXT, Gfx.ALIGN_LEFT, true);
            if (!e.info.isEmpty()) {
                Draw.fit(g, e.info, x + pad + 32 * u, ry + rowH / 2 + 10 * u, 11 * u, w - 150 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            }
            g.text(Fmt.compact(e.score) + " " + units[tab], x + w - pad, ry + rowH / 2, 14 * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            if (i < list.size() - 1) {
                Draw.divider(g, x + pad + 32 * u, ry + rowH, w - pad - 32 * u);
            }
        }
        y += h + 14 * u;
        g.text("Einträge zählen je Gerät den besten Lauf. Name: Profil → Stift.", x + w / 2, y + 8 * u, 11 * u, Theme.MUTED,
                Gfx.ALIGN_CENTER, false);
        return y + 30 * u - y0;
    }

    @Override
    protected void onTap(Object tag) {
        if ("retry".equals(tag)) {
            load(tab);
        }
    }
}
