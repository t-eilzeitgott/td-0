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
import neontd.progress.Arena;
import neontd.render.LevelPreview;
import neontd.render.TowerArt;
import neontd.save.Leaderboard;
import neontd.save.RunSave;
import neontd.sim.Special;
import neontd.sim.TowerType;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Fmt;

/**
 * Die Arena der Woche: Karte, Regeln und Restzeit, der eigene Stand, die Rangliste (bester Lauf je Spieler mit
 * Versuchen), der Verlauf der letzten Läufe und die Ruhmeshalle der Wochensieger. Unten die Taste „Antreten“.
 * Die Platzierung der Vorwoche bringt einmalig Kristalle.
 */
public final class ArenaScene extends PanelScene {
    private final Arena arena;
    private final Arena previous;
    private final LevelDef level;
    private final Path[] paths;
    private final Button play;
    private List<Leaderboard.ArenaEntry> list;
    private List<Leaderboard.FeedEntry> feed;
    private List<Leaderboard.HallEntry> hall;
    private boolean listLoading;
    private boolean feedLoading;
    private boolean hallLoading;
    private boolean listFailed;
    private boolean feedFailed;
    private boolean hallFailed;
    /** Platz der Vorwoche (0 = nicht dabei oder unbekannt) und ob die Belohnung noch abzuholen ist. */
    private int prevRank;
    private int prevPlayers;

    public ArenaScene(App app) {
        super(app, "Arena", Theme.ACCENT);
        arena = Arena.of(app.today());
        previous = arena.previous();
        level = arena.build();
        paths = level.buildPaths();
        RunSave run = app.saves.resumable(arena.levelId());
        play = ui.add(new Button(run != null ? "Fortsetzen · Welle " + (run.snapshot.waveIndex + 1) : "Antreten", Icon.PLAY,
                Theme.ACCENT, this::start));
        play.style = Button.Style.PRIMARY;
        setTabs("Rangliste", "Verlauf", "Ruhmeshalle");
        loadList(true);
        loadPrevious();
    }

    private void start() {
        if (!app.board.hasName()) {
            app.goTo(new NameScene(app, true));
            return;
        }
        RunSave r = app.saves.resumable(arena.levelId());
        app.goTo(GameScene.arena(app, arena, r));
    }

    // -------------------------------------------------------------------------------------------- Laden

    private void loadList(boolean force) {
        if (!app.board.available()) {
            listFailed = true;
            return;
        }
        listLoading = true;
        listFailed = false;
        app.board.arenaList(arena.id, force, l -> {
            listLoading = false;
            if (l == null) {
                listFailed = true;
            } else {
                list = l;
            }
        });
    }

    private void loadFeed() {
        if (!app.board.available()) {
            feedFailed = true;
            return;
        }
        feedLoading = true;
        feedFailed = false;
        app.board.arenaFeed(arena.id, l -> {
            feedLoading = false;
            if (l == null) {
                feedFailed = true;
            } else {
                feed = l;
            }
        });
    }

    private void loadHall() {
        if (!app.board.available()) {
            hallFailed = true;
            return;
        }
        hallLoading = true;
        hallFailed = false;
        app.board.arenaHall(l -> {
            hallLoading = false;
            if (l == null) {
                hallFailed = true;
            } else {
                hall = l;
            }
        });
    }

    /** Platz der Vorwoche holen (für die Belohnung). */
    private void loadPrevious() {
        if (!app.board.available() || app.progress.stat(rewardKey()) > 0) {
            return;
        }
        app.board.arenaPrevious(previous.id, l -> {
            if (l == null) {
                return;
            }
            for (Leaderboard.ArenaEntry e : l) {
                if (e.mine) {
                    prevRank = e.rank;
                    prevPlayers = l.size();
                }
            }
        });
    }

    private String rewardKey() {
        return "arenareward-" + previous.id;
    }

    private int rewardCrystals() {
        if (prevRank >= 1 && prevRank <= Arena.PODIUM_CRYSTALS.length) {
            return Arena.PODIUM_CRYSTALS[prevRank - 1];
        }
        return Arena.PARTICIPATION_CRYSTALS;
    }

    @Override
    protected void onTab(int i) {
        if (i == 0) {
            loadList(false);
        } else if (i == 1 && feed == null && !feedLoading) {
            loadFeed();
        } else if (i == 2 && hall == null && !hallLoading) {
            loadHall();
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

    // ------------------------------------------------------------------------------------------ Inhalt

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        if (tab == 0) {
            if (prevRank > 0 && app.progress.stat(rewardKey()) <= 0) {
                y += rewardCard(g, x, y, w, u) + 12 * u;
            }
            y += heroCard(g, x, y, w, u) + 12 * u;
            y += statusCard(g, x, y, w, u) + 18 * u;
            y += ranking(g, x, y, w, u);
        } else if (tab == 1) {
            y += feedList(g, x, y, w, u);
        } else {
            y += hallList(g, x, y, w, u);
        }
        return y - y0 + 16 * u;
    }

    private double rewardCard(Gfx g, double x, double y, double w, double u) {
        double h = 64 * u;
        int c = prevRank == 1 ? Theme.STAR : (prevRank <= 3 ? Theme.ACCENT : Theme.POS);
        Draw.accentCard(g, x, y, w, h, 16 * u, c, pressed("reward") ? 0.6 : 0);
        hit(x, y, w, h, "reward");
        Draw.iconBadge(g, x + 30 * u, y + h / 2, 16 * u, Icon.TROPHY, c);
        String head = previous.label() + " abgeschlossen: Platz " + prevRank + (prevPlayers > 0 ? " von " + prevPlayers : "");
        Draw.fit(g, head, x + 56 * u, y + h / 2 - 9 * u, 14 * u, w - 160 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(prevRank <= 3 ? "Podium – danke fürs Mitkämpfen!" : "Danke fürs Mitmachen!", x + 56 * u, y + h / 2 + 10 * u,
                12 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        double bw = 84 * u;
        g.fillRoundRect(x + w - 14 * u - bw, y + h / 2 - 15 * u, bw, 30 * u, 10 * u, Colors.withAlpha(c, 0.2));
        g.text("+" + rewardCrystals() + " ◆", x + w - 14 * u - bw / 2, y + h / 2, 13 * u, Colors.lighten(c, 0.3),
                Gfx.ALIGN_CENTER, true);
        return h;
    }

    /** Karte der Woche: Name, Restzeit, Vorschau, Regelzeile, Turm-Pool, Joker. */
    private double heroCard(Gfx g, double x, double y, double w, double u) {
        int accent = Worlds.accent(level.chapter);
        double pad = 16 * u;
        double previewH = Math.min(w * 0.38, 210 * u);
        double h = pad + 18 * u + 28 * u + previewH + 14 * u + 20 * u + 16 * u + 40 * u + 14 * u + pad;
        Draw.accentCard(g, x, y, w, h, 22 * u, Theme.ACCENT, 0);
        double tx = x + pad;
        double ty = y + pad + 8 * u;
        String left = Arena.remainingText(arena.remainingMillis(app.platform.nowMillis(), app.platform.localOffsetMillis()));
        double rw = 80 * u;
        double rx = x + w - pad - rw;
        Draw.fit(g, "ARENA  ·  " + arena.label() + "  ·  noch " + left, tx, ty, 11 * u, rx - tx - 8 * u,
                Colors.lighten(Theme.ACCENT, 0.15), Gfx.ALIGN_LEFT, true);
        g.fillRoundRect(rx, ty - 4 * u, rw, 30 * u, 10 * u, pressed("rules") ? Theme.SURFACE3 : Theme.SURFACE2);
        hit(rx, ty - 4 * u, rw, 30 * u, "rules");
        g.text("Regeln", rx + rw / 2, ty + 11 * u, 13 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        ty += 26 * u;
        Draw.fit(g, level.name, tx, ty, 24 * u, rx - tx - 10 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        ty += 22 * u;
        g.fillRoundRect(tx, ty, w - 2 * pad, previewH, 14 * u, Theme.BG);
        g.save();
        g.clipRect(tx, ty, w - 2 * pad, previewH);
        LevelPreview.draw(g, paths, level.width, level.height, tx + 8 * u, ty + 8 * u, w - 2 * pad - 16 * u, previewH - 16 * u,
                Worlds.lane(level.chapter), time);
        g.restore();
        g.strokeRoundRect(tx, ty, w - 2 * pad, previewH, 14 * u, 1, Theme.LINE);
        ty += previewH + 18 * u;
        Draw.fit(g, arena.def.rule, tx, ty, 14 * u, w - 2 * pad, Theme.TEXT, Gfx.ALIGN_LEFT, false);
        ty += 20 * u;
        g.text(Arena.LIVES + " Leben  ·  " + arena.def.money + " Startgeld  ·  " + (level.style != null ? level.style.label : "")
                + (arena.def.speedMul > 1 ? "  ·  Tempo +" + Math.round((arena.def.speedMul - 1) * 100) + " %" : ""),
                tx, ty, 12 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        ty += 16 * u;
        // Turm-Pool als Symbole
        double ir = 15 * u;
        double ix = tx + ir;
        for (TowerType t : arena.def.pool) {
            g.fillCircle(ix, ty + ir + 4 * u, ir + 4 * u, Theme.SURFACE2);
            TowerArt.drawIcon(g, t, ix, ty + ir + 4 * u, ir * 0.78, time);
            ix += (ir + 4 * u) * 2 + 6 * u;
        }
        ty += 40 * u + 6 * u;
        StringBuilder jokers = new StringBuilder("Joker: ");
        for (int i = 0; i < arena.def.jokers.length; i++) {
            Special sp = arena.def.jokers[i];
            jokers.append(i > 0 ? " · " : "").append(sp.label);
        }
        Draw.fit(g, jokers.toString(), tx, ty, 12 * u, w - 2 * pad, Theme.MUTED, Gfx.ALIGN_LEFT, true);
        return h;
    }

    /** Eigener Stand: Bestwert, Platz, Abstand nach vorn – und die Taste zum Herausfordern. */
    private double statusCard(Gfx g, double x, double y, double w, double u) {
        double h = 72 * u;
        Draw.card(g, x, y, w, h, 16 * u, 0);
        Leaderboard.ArenaEntry mine = mine();
        long best = Math.max(app.board.arenaBest(arena.id), mine != null ? mine.score : 0);
        double tx = x + 16 * u;
        double cy = y + h / 2;
        double bw = 120 * u;
        if (best <= 0) {
            Draw.fit(g, "Noch kein Lauf diese Woche", tx, cy - 9 * u, 15 * u, w - bw - 40 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            Draw.cut(g, "Jeder Versuch zählt – nur der beste bleibt stehen.", tx, cy + 11 * u, 12 * u, w - bw - 40 * u,
                    Theme.MUTED, Gfx.ALIGN_LEFT, false);
        } else {
            String rank = mine != null ? "Platz " + mine.rank + " von " + (list == null ? "?" : list.size()) : "Dein Bestwert";
            Draw.fit(g, Fmt.points(best) + " Punkte", tx, cy - 9 * u, 16 * u, w - bw - 40 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            String gap = gapText(mine);
            Draw.fit(g, rank + (gap != null ? "  ·  " + gap : ""), tx, cy + 11 * u, 12 * u, w - bw - 40 * u,
                    mine != null && mine.rank == 1 ? Theme.STAR : Theme.MUTED, Gfx.ALIGN_LEFT, false);
        }
        double bx = x + w - 12 * u - bw;
        g.fillRoundRect(bx, cy - 17 * u, bw, 34 * u, 12 * u, pressed("share") ? Theme.SURFACE3 : Theme.SURFACE2);
        hit(bx, cy - 17 * u, bw, 34 * u, "share");
        Icons.draw(g, Icon.FLAG, bx + 18 * u, cy, 6 * u, Theme.ACCENT, 0);
        g.text("Herausfordern", bx + 30 * u, cy, 12 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        return h;
    }

    private Leaderboard.ArenaEntry mine() {
        if (list == null) {
            return null;
        }
        for (Leaderboard.ArenaEntry e : list) {
            if (e.mine) {
                return e;
            }
        }
        return null;
    }

    private String gapText(Leaderboard.ArenaEntry mine) {
        if (mine == null || list == null) {
            return null;
        }
        Leaderboard.ArenaEntry above = null;
        for (Leaderboard.ArenaEntry e : list) {
            if (!e.mine && e.score >= mine.score && (above == null || e.score < above.score)) {
                above = e;
            }
        }
        if (above != null) {
            return "noch " + Fmt.points(above.score - mine.score + 1) + " bis " + above.name;
        }
        return list.size() > 1 ? "du führst" : null;
    }

    private double ranking(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        g.text("Rangliste " + arena.label(), x + 2 * u, y, 13 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        if (list != null && !list.isEmpty()) {
            int tries = 0;
            for (Leaderboard.ArenaEntry e : list) {
                tries += e.attempts;
            }
            g.text(list.size() + " Spieler · " + tries + " Läufe", x + w - 2 * u, y, 12 * u, Theme.MUTED, Gfx.ALIGN_RIGHT, false);
        }
        y += 18 * u;
        if (list == null || list.isEmpty()) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            String msg = listLoading ? "Lädt …" : (listFailed ? "Keine Verbindung – antippen zum Neuladen."
                    : "Noch niemand angetreten – sei der Erste!");
            g.text(msg, x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            if (listFailed && app.board.available()) {
                hit(x, y, w, h, "retry");
            }
            return y + h - y0;
        }
        double rowH = 56 * u;
        double h = list.size() * rowH;
        Draw.card(g, x, y, w, h, 16 * u, 0);
        double pad = 14 * u;
        for (int i = 0; i < list.size(); i++) {
            Leaderboard.ArenaEntry e = list.get(i);
            double ry = y + i * rowH;
            if (e.mine) {
                g.fillRoundRect(x + 4 * u, ry + 3 * u, w - 8 * u, rowH - 6 * u, 12 * u, Colors.withAlpha(Theme.ACCENT, 0.12));
            }
            int c = e.rank == 1 ? Theme.STAR : (e.rank == 2 ? 0xC9D1E6 : (e.rank == 3 ? 0xE08A3C : Theme.MUTED));
            if (e.rank <= 3) {
                g.fillCircle(x + pad + 12 * u, ry + rowH / 2, 13 * u, Colors.withAlpha(c, 0.16));
            }
            g.text(Integer.toString(e.rank), x + pad + 12 * u, ry + rowH / 2, 14 * u, c, Gfx.ALIGN_CENTER, true);
            double nx = x + pad + 36 * u;
            double nameW = w - 170 * u;
            Draw.fit(g, e.name, nx, ry + rowH / 2 - 9 * u, 15 * u, nameW, e.mine ? Colors.lighten(Theme.ACCENT, 0.4) : Theme.TEXT,
                    Gfx.ALIGN_LEFT, true);
            String sub = "Welle " + e.wave + "  ·  Serie " + e.streak + "  ·  " + e.attempts + (e.attempts == 1 ? " Lauf" : " Läufe");
            Draw.fit(g, sub, nx, ry + rowH / 2 + 10 * u, 11 * u, nameW, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            g.text(Fmt.points(e.score), x + w - pad, ry + rowH / 2 - 1 * u, 15 * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            if (i < list.size() - 1) {
                Draw.divider(g, nx, ry + rowH, w - (nx - x));
            }
        }
        y += h + 14 * u;
        g.text("Gezählt wird je Spieler der beste Lauf der Woche.", x + w / 2, y + 6 * u, 11 * u, Theme.MUTED,
                Gfx.ALIGN_CENTER, false);
        return y + 24 * u - y0;
    }

    private double feedList(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += 10 * u;
        g.text("Die letzten Läufe in " + arena.label(), x + 2 * u, y, 13 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 18 * u;
        if (feed == null || feed.isEmpty()) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            String msg = feedLoading ? "Lädt …" : (feedFailed ? "Keine Verbindung." : "Noch keine Läufe diese Woche.");
            g.text(msg, x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            return y + h - y0;
        }
        double rowH = 52 * u;
        double h = feed.size() * rowH;
        Draw.card(g, x, y, w, h, 16 * u, 0);
        double now = app.platform.nowMillis();
        for (int i = 0; i < feed.size(); i++) {
            Leaderboard.FeedEntry e = feed.get(i);
            double ry = y + i * rowH;
            double cx = x + 26 * u;
            g.fillCircle(cx, ry + rowH / 2, 5 * u, e.record ? Theme.POS : Theme.SURFACE3);
            double nx = x + 44 * u;
            Draw.fit(g, e.name, nx, ry + rowH / 2 - 9 * u, 14 * u, w * 0.45, e.mine ? Colors.lighten(Theme.ACCENT, 0.4)
                    : Theme.TEXT, Gfx.ALIGN_LEFT, true);
            String sub = "Welle " + e.wave + "  ·  " + ago(now - e.at);
            g.text(sub, nx, ry + rowH / 2 + 10 * u, 11 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            g.text(Fmt.points(e.score), x + w - 14 * u, ry + rowH / 2 - 8 * u, 14 * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            if (e.record) {
                g.text("Rekord", x + w - 14 * u, ry + rowH / 2 + 10 * u, 11 * u, Theme.POS, Gfx.ALIGN_RIGHT, true);
            }
            if (i < feed.size() - 1) {
                Draw.divider(g, nx, ry + rowH, w - (nx - x));
            }
        }
        return y + h + 10 * u - y0;
    }

    static String ago(double ms) {
        double min = Math.max(0, ms / 60000.0);
        if (min < 1) {
            return "gerade eben";
        }
        if (min < 60) {
            return "vor " + (int) min + " Min";
        }
        double h = min / 60;
        if (h < 24) {
            return "vor " + (int) h + " Std";
        }
        int d = (int) (h / 24);
        return d == 1 ? "gestern" : "vor " + d + " Tagen";
    }

    private double hallList(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += 10 * u;
        g.text("Wochensieger", x + 2 * u, y, 13 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 18 * u;
        if (hall == null || hall.isEmpty()) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            String msg = hallLoading ? "Lädt …" : (hallFailed ? "Keine Verbindung." : "Die erste Woche läuft noch – wer holt sie?");
            g.text(msg, x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            return y + h - y0;
        }
        // Titel je Spieler zählen
        java.util.Map<String, Integer> wins = new java.util.LinkedHashMap<>();
        for (Leaderboard.HallEntry e : hall) {
            wins.put(e.name, wins.getOrDefault(e.name, 0) + 1);
        }
        double rowH = 56 * u;
        double h = hall.size() * rowH;
        Draw.card(g, x, y, w, h, 16 * u, 0);
        for (int i = 0; i < hall.size(); i++) {
            Leaderboard.HallEntry e = hall.get(i);
            double ry = y + i * rowH;
            Draw.iconBadge(g, x + 28 * u, ry + rowH / 2, 14 * u, Icon.TROPHY, Theme.STAR);
            double nx = x + 52 * u;
            String wk = "KW " + (e.week.length() >= 8 ? Integer.parseInt(e.week.substring(6)) : 0);
            int n = wins.getOrDefault(e.name, 1);
            Draw.fit(g, e.name + (n > 1 ? "  ·  " + n + "× Sieger" : ""), nx, ry + rowH / 2 - 9 * u, 14 * u, w * 0.5,
                    e.mine ? Colors.lighten(Theme.ACCENT, 0.4) : Theme.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(wk + "  ·  Welle " + e.wave + "  ·  " + e.players + " Spieler", nx, ry + rowH / 2 + 10 * u, 11 * u,
                    Theme.MUTED, Gfx.ALIGN_LEFT, false);
            g.text(Fmt.points(e.score), x + w - 14 * u, ry + rowH / 2, 14 * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            if (i < hall.size() - 1) {
                Draw.divider(g, nx, ry + rowH, w - (nx - x));
            }
        }
        return y + h + 10 * u - y0;
    }

    // ---------------------------------------------------------------------------------------- Aktionen

    @Override
    protected void onTap(Object tag) {
        if ("retry".equals(tag)) {
            loadList(true);
        } else if ("rules".equals(tag)) {
            sheet("So zählt die Arena", Theme.ACCENT, new String[] {
                "Alle spielen dieselbe Karte mit denselben Türmen. Spielerlevel, Shop und Forschung zählen hier nicht.",
                Arena.LIVES + " Leben, keine Meisterstufen – die Wellen werden immer härter, bis der Lauf endet.",
                "Punkte: Abschüsse (Prämie × Welle) und Wellenbonus (100 × Welle), beides × Serien-Multiplikator.",
                "Serie: Jede Welle ohne durchgelassenen Gegner bringt +0,1 – bis ×3,0. Ein Durchbruch setzt auf ×1,0.",
                "Vorlauf: Startest du die nächste Welle, während noch Gegner laufen, zählt ihr Wellenbonus doppelt.",
                "Es zählt dein bester Lauf der Woche. Montag 0 Uhr startet eine neue Arena; Platz 1–3 bekommen "
                        + Arena.PODIUM_CRYSTALS[0] + " / " + Arena.PODIUM_CRYSTALS[1] + " / " + Arena.PODIUM_CRYSTALS[2]
                        + " Kristalle, Mitmachen " + Arena.PARTICIPATION_CRYSTALS + ".",
            }, new Button("Verstanden", null, Theme.ACCENT, null));
        } else if ("share".equals(tag)) {
            long best = app.board.arenaBest(arena.id);
            Leaderboard.ArenaEntry m = mine();
            if (m != null) {
                best = Math.max(best, m.score);
            }
            String text = best > 0
                    ? "Ich habe " + Fmt.points(best) + " Punkte in der Neon-TD-Arena (" + arena.label() + ", " + level.name
                            + ") – schlägst du das? https://t-eilzeitgott.github.io/td-0/"
                    : "Neon-TD-Arena " + arena.label() + " (" + level.name + "): Wer holt diese Woche die meisten Punkte?"
                            + " https://t-eilzeitgott.github.io/td-0/";
            app.platform.copyText(text, ok -> toast(ok ? "Text kopiert – schick ihn weiter" : "Kopieren ging nicht",
                    ok ? Theme.POS : Theme.NEG));
        } else if ("reward".equals(tag)) {
            if (app.progress.stat(rewardKey()) > 0 || prevRank <= 0) {
                return;
            }
            app.progress.stats.put(rewardKey(), 1.0);
            int got = app.progress.addCrystals(rewardCrystals());
            app.commitProgress();
            toast("+" + got + " Kristalle für " + previous.label(), Theme.STAR);
        }
    }

    /** Für Tests und Screenshots: Spielen-Taste. */
    public double[] playAnchor() {
        return new double[] {play.cx(), play.cy()};
    }
}
