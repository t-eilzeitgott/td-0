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
import neontd.ui.Kit;

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
        play.painter = (g, b) -> Kit.primary(g, -b.w / 2, -b.h / 2, b.w, b.h, b.label, Icon.PLAY, Theme.ACCENT,
                Math.max(b.hover.value * 0.5, b.press.value), app.vp.u);
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
        double bh = Kit.BUTTON * u;
        play.bounds(contentX, contentBottom - bh - 6 * u, contentW, bh);
        contentBottom -= bh + 22 * u;
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        int accent = Worlds.accent(daily.level.chapter);
        Kit.kicker(g, "Heute  ·  " + Worlds.name(daily.level.chapter) + "  ·  Level " + daily.level.number, x, y + 8 * u, u,
                Colors.lighten(accent, 0.1));
        y += 30 * u;
        Draw.fit(g, daily.level.name, x, y + 4 * u, Kit.T_HERO * u, w, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 36 * u;
        g.text(daily.modifier.label, x, y, Kit.T_BODY * u, Colors.lighten(daily.modifier.color, 0.2), Gfx.ALIGN_LEFT, true);
        double mw = g.textWidth(daily.modifier.label, Kit.T_BODY * u, true);
        Draw.cut(g, "  ·  " + daily.modifier.desc, x + mw, y, Kit.T_BODY * u, w - mw, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 18 * u;
        double previewH = Math.min(Math.min(w * 0.62, 300 * u), app.vp.h * 0.42);
        g.save();
        g.clipRect(x - 10 * u, y, w + 20 * u, previewH);
        g.radialGlow(x + w / 2, y + previewH / 2, w * 0.6, Colors.withAlpha(accent, 0.07));
        LevelPreview.draw(g, paths, level.width, level.height, x + 6 * u, y + 10 * u, w - 12 * u, previewH - 20 * u,
                Worlds.lane(daily.level.chapter), time);
        g.restore();
        y += previewH + 16 * u;

        // Eigener Stand
        int best = app.progress.dailyBestToday(app.today());
        Kit.stat(g, best > 0 ? "Welle " + best : "–", best > 0 ? "Deine beste Welle heute" : "Noch nicht gespielt", x, y,
                best > 0 ? Theme.TEXT : Theme.MUTED, u);
        Kit.stat(g, "+" + Daily.REWARD, "Kristalle ab Welle " + Daily.REWARD_WAVE, x + w * 0.52, y, Theme.CRYSTAL, u);
        y += 60 * u;
        y += Kit.paragraph(g, "Endlosmodus – die weiteste Welle zählt.", x, y, w, Kit.T_CAPTION * u, Theme.MUTED, u);
        y += Kit.SECTION_GAP * u;

        // Bestenliste des Tages
        y += Kit.section(g, "Bestenliste heute", null, 0, x, y, w, u);
        if (list == null || list.isEmpty()) {
            String msg = loading ? "Lädt …" : (failed ? "Keine Verbindung" : "Noch keine Einträge – sei der Erste!");
            g.text(msg, x, y + 20 * u, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            y += 44 * u;
        } else {
            int n = Math.min(10, list.size());
            for (int i = 0; i < n; i++) {
                Leaderboard.Entry e = list.get(i);
                y += LeaderboardScene.rankRow(g, x, y, w, u, i + 1, e.name, null, "Welle " + Fmt.compact(e.score), e.mine,
                        Theme.ACCENT, i < n - 1);
            }
        }
        return y - y0 + 16 * u;
    }
}
