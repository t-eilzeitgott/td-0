package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.level.LevelDef;
import neontd.level.Worlds;
import neontd.physics.Path;
import neontd.progress.Daily;
import neontd.render.LevelPreview;
import neontd.save.Leaderboard;
import neontd.save.RunSave;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Fmt;

/**
 * Die Tages-Challenge: Level und Modifikator des Tages, der eigene Rekord, die heutige Bestenliste und die
 * Spielen-Taste (Endlosmodus – wie weit kommst du?).
 */
public final class DailyScene extends PanelScene {
    private final Daily daily;
    private final LevelDef level;
    private final Path[] paths;
    private final Button play;
    private List<Leaderboard.Entry> list;
    private boolean loading;
    private boolean failed;

    public DailyScene(App app) {
        super(app, "Tages-Challenge", Theme.ACCENT);
        daily = Daily.today(app.today());
        level = daily.build();
        paths = level.buildPaths();
        RunSave run = app.saves.resumable(level.id);
        play = ui.add(new Button(run != null ? "Fortsetzen · Welle " + (run.snapshot.waveIndex + 1) : "Spielen", Icon.PLAY,
                Theme.ACCENT, () -> {
                    RunSave r = app.saves.resumable(level.id);
                    app.goTo(r != null ? GameScene.resume(app, level, r) : GameScene.endless(app, level));
                }));
        play.style = Button.Style.PRIMARY;
        if (app.board.available()) {
            loading = true;
            app.board.top(daily.id(), l -> {
                loading = false;
                if (l == null) {
                    failed = true;
                } else {
                    list = l;
                }
            });
        } else {
            failed = true;
        }
    }

    @Override
    public void layout() {
        super.layout();
        double u = app.vp.u;
        double bh = 52 * u;
        play.bounds(contentX, contentBottom - bh - 8 * u, contentW, bh);
        contentBottom -= bh + 20 * u;
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        double pad = 16 * u;
        int accent = Worlds.accent(daily.level.chapter);
        double previewH = Math.min(w * 0.4, 220 * u);
        double h = pad + 20 * u + previewH + 12 * u + 60 * u + pad;
        Draw.accentCard(g, x, y, w, h, 20 * u, accent, 0);
        double ty = y + pad + 8 * u;
        g.text(Worlds.name(daily.level.chapter) + "  ·  Level " + daily.level.number, x + pad, ty, 11 * u,
                Colors.lighten(accent, 0.15), Gfx.ALIGN_LEFT, true);
        Draw.chipRight(g, x + w - pad, ty - 12 * u, 24 * u, Icon.CALENDAR, "Heute", Theme.ACCENT, true);
        ty += 20 * u;
        g.fillRoundRect(x + pad, ty, w - 2 * pad, previewH, 14 * u, Theme.BG);
        LevelPreview.draw(g, paths, level.width, level.height, x + pad + 8 * u, ty + 8 * u, w - 2 * pad - 16 * u,
                previewH - 16 * u, Worlds.lane(daily.level.chapter), time);
        g.strokeRoundRect(x + pad, ty, w - 2 * pad, previewH, 14 * u, 1, Theme.LINE);
        ty += previewH + 14 * u;
        Draw.fit(g, daily.level.name, x + pad, ty + 6 * u, 22 * u, w - 2 * pad, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        ty += 30 * u;
        double cw = Draw.chip(g, x + pad, ty, 26 * u, null, daily.modifier.label, daily.modifier.color, true);
        Draw.fit(g, daily.modifier.desc, x + pad + cw + 10 * u, ty + 13 * u, 13 * u, w - 2 * pad - cw - 10 * u, Theme.MUTED,
                Gfx.ALIGN_LEFT, false);
        y += h + 12 * u;

        // Eigener Stand
        int best = app.progress.dailyBestToday(app.today());
        double rh = 60 * u;
        Draw.card(g, x, y, w, rh, 16 * u, 0);
        Draw.iconBadge(g, x + 30 * u, y + rh / 2, 16 * u, Icon.WAVES, Theme.ACCENT);
        g.text(best > 0 ? "Deine beste Welle heute: " + best : "Noch nicht gespielt", x + 58 * u, y + rh / 2 - 8 * u, 14 * u,
                Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Ab Welle " + Daily.REWARD_WAVE + ": +" + Daily.REWARD + " Kristalle  ·  Endlosmodus, zählt die weiteste Welle",
                x + 58 * u, y + rh / 2 + 10 * u, 11 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += rh + 12 * u;

        // Bestenliste des Tages
        g.text("Bestenliste heute", x + 2 * u, y + 10 * u, 13 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 26 * u;
        if (list == null || list.isEmpty()) {
            double eh = 64 * u;
            Draw.card(g, x, y, w, eh, 16 * u, 0);
            String msg = loading ? "Lädt …" : (failed ? "Keine Verbindung." : "Noch keine Einträge – sei der Erste!");
            g.text(msg, x + w / 2, y + eh / 2, 13 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            y += eh;
        } else {
            int n = Math.min(10, list.size());
            double rowH = 40 * u;
            Draw.card(g, x, y, w, n * rowH, 16 * u, 0);
            for (int i = 0; i < n; i++) {
                Leaderboard.Entry e = list.get(i);
                double ry = y + i * rowH;
                if (e.mine) {
                    g.fillRoundRect(x + 4 * u, ry + 3 * u, w - 8 * u, rowH - 6 * u, 10 * u, Colors.withAlpha(Theme.ACCENT, 0.12));
                }
                g.text(Integer.toString(i + 1), x + pad + 8 * u, ry + rowH / 2, 13 * u, i < 3 ? Theme.STAR : Theme.MUTED,
                        Gfx.ALIGN_CENTER, true);
                Draw.fit(g, e.name, x + pad + 30 * u, ry + rowH / 2, 14 * u, w - 130 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
                g.text("Welle " + Fmt.compact(e.score), x + w - pad, ry + rowH / 2, 13 * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            }
            y += n * rowH;
        }
        return y - y0 + 16 * u;
    }
}
