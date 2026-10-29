package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.gfx.Wordmark;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.level.Worlds;
import neontd.math.Mathx;
import neontd.physics.Path;
import neontd.progress.Progress;
import neontd.progress.Quest;
import neontd.progress.Quests;
import neontd.render.LevelPreview;
import neontd.save.RunSave;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Easing;
import neontd.ui.Fmt;
import neontd.ui.Smooth;
import neontd.ui.Viewport;

/**
 * Startseite im cleanen Stil: Wortmarke im Kopf, darunter die Karte „Weiter“ mit dem nächsten Level (oder dem
 * gespeicherten Lauf), die Profilzeile mit Serie, die Tages-Challenge und Tagesaufgaben, die Welten mit Sternen und
 * die Kacheln zu Shop, Season, Erfolgen, Bestenliste, Editor und Einstellungen. Alles scrollt.
 */
public final class MenuScene extends PanelScene {
    private final Button settings;
    private final Smooth in = new Smooth(0, 6);
    private LevelDef next;
    private Path[] nextPaths;
    private RunSave nextRun;
    private String otherRunId;
    private RunSave otherRun;
    private neontd.progress.Arena arena;
    private List<neontd.save.Leaderboard.ArenaEntry> arenaList;

    public MenuScene(App app) {
        super(app, "", Theme.ACCENT);
        back.visible = false;
        settings = ui.add(new Button("", Icon.GEAR, Theme.MUTED, () -> app.goTo(new SettingsScene(app))));
        settings.painter = (g, b) -> {
            double r = Math.min(b.w, b.h) / 2;
            g.fillCircle(0, 0, r, Colors.lerp(Theme.SURFACE2, Theme.SURFACE3, Math.max(b.hover.value, b.press.value)));
            Icons.draw(g, Icon.GEAR, 0, 0, r * 0.5, Theme.TEXT, 0);
        };
        refresh();
        in.target = 1;
    }

    /** Position der Wortmarke im Kopf: {linke Kante, Mitte y, Zeichenhöhe}. */
    static double[] wordmarkBox(Viewport vp) {
        double u = vp.u;
        double m = 16 * u;
        double contentW = Math.min(vp.safeW() - 2 * m, 720 * u);
        double contentX = vp.insetL + (vp.safeW() - contentW) / 2;
        return new double[] {contentX + 2 * u, vp.insetT + m + 20 * u, 20 * u};
    }

    private void refresh() {
        Progress p = app.progress;
        app.checkPeriods();
        next = p.nextLevel();
        nextPaths = next.buildPaths();
        nextRun = app.saves.resumable(next.id);
        otherRun = null;
        otherRunId = null;
        double best = -1;
        for (String id : app.saves.runLevelIds()) {
            if (id.equals(next.id)) {
                continue;
            }
            RunSave r = app.saves.resumable(id);
            if (r != null && Levels.find(id) != null && r.t > best) {
                best = r.t;
                otherRun = r;
                otherRunId = id;
            }
        }
    }

    @Override
    public void onEnter() {
        refresh();
        arena = neontd.progress.Arena.of(app.today());
        arenaList = app.board.cachedArena(arena.id);
        app.board.arenaList(arena.id, false, l -> {
            if (l != null) {
                arenaList = l;
            }
        });
    }

    @Override
    public void layout() {
        super.layout();
        Viewport vp = app.vp;
        double u = vp.u;
        double bs = 40 * u;
        settings.bounds(contentX + contentW - bs, vp.insetT + 16 * u, bs, bs);
        // der Kristall-Chip rückt neben das Zahnrad
        chipShift = bs + 10 * u;
    }

    private double chipShift;

    @Override
    protected void drawCrystalChip(Gfx g, double u) {
        double y = app.vp.insetT + 16 * u + 4 * u;
        Draw.chipRight(g, contentX + contentW - chipShift, y, 32 * u, Icon.CRYSTAL, Fmt.compact(app.progress.crystals()),
                Theme.CRYSTAL, true);
    }

    @Override
    protected void tick(double dt) {
        in.update(dt);
    }

    @Override
    public void render(Gfx g) {
        super.render(g);
        double[] box = wordmarkBox(app.vp);
        Wordmark.draw(g, box[0], box[1], box[2], 4, null, time);
    }

    // ---------------------------------------------------------------------------------------- Inhalt

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        Progress p = app.progress;
        double y = y0 + 4 * u;
        double gap = 12 * u;
        double a = Easing.outCubic(Mathx.clamp01(in.value));
        g.save();
        g.alpha(a);
        g.translate(0, (1 - a) * 10 * u);

        y += heroCard(g, x, y, w, u) + gap;
        y += arenaCard(g, x, y, w, u) + gap;
        if (otherRun != null) {
            y += resumeRow(g, x, y, w, u) + gap;
        }
        y += profileRow(g, x, y, w, u) + gap;
        y += todayCard(g, x, y, w, u) + gap;
        y += worldsCard(g, x, y, w, u) + gap;
        y += tiles(g, x, y, w, u) + gap;
        g.text("v" + neontd.app.Version.NAME, x + w, y + 6 * u, 11 * u, Theme.MUTED, Gfx.ALIGN_RIGHT, false);
        y += 24 * u;
        g.restore();
        return y - y0;
    }

    /** Die große Karte „Weiter“: nächstes Level mit Vorschau, Sternen und der Spielen-Taste. */
    private double heroCard(Gfx g, double x, double y, double w, double u) {
        int world = next.chapter;
        int accent = Worlds.accent(world);
        boolean resume = nextRun != null && nextRun.snapshot != null;
        double pad = 16 * u;
        double previewH = Math.min(w * 0.42, 240 * u);
        double h = pad + 18 * u + 26 * u + previewH + 12 * u + 50 * u + pad;
        double lift = pressed("play") ? 1 : (hovered("play") ? 0.5 : 0);
        Draw.accentCard(g, x, y, w, h, 22 * u, accent, lift * 0.5);
        hit(x, y, w, h, "play");
        double tx = x + pad;
        double ty = y + pad + 8 * u;
        String kicker = (resume ? "FORTSETZEN" : "WEITER") + "  ·  " + Worlds.name(world) + "  ·  Level " + next.number;
        g.text(kicker, tx, ty, 11 * u, Colors.lighten(accent, 0.15), Gfx.ALIGN_LEFT, true);
        ty += 24 * u;
        Draw.fit(g, next.name, tx, ty, 24 * u, w - 2 * pad - 90 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Draw.stars(g, x + w - pad - 34 * u, ty, 9 * u, app.progress.stars(next.id), 3);
        ty += 20 * u;
        // Vorschau
        double px = x + pad;
        double py = ty;
        double pw = w - 2 * pad;
        g.fillRoundRect(px, py, pw, previewH, 14 * u, Theme.BG);
        g.save();
        g.clipRect(px, py, pw, previewH);
        LevelPreview.draw(g, nextPaths, next.width, next.height, px + 8 * u, py + 8 * u, pw - 16 * u, previewH - 16 * u,
                Worlds.lane(world), time);
        g.restore();
        g.strokeRoundRect(px, py, pw, previewH, 14 * u, 1, Theme.LINE);
        ty += previewH + 12 * u;
        // Fußzeile: Stil-Chip und Spielen-Taste
        double bh = 50 * u;
        String sub = next.style == null ? "" : next.style.label + "  ·  " + (next.bossLevel ? "Boss alle 5 Wellen" : "20 Wellen");
        if (resume) {
            sub = "Welle " + (nextRun.snapshot.waveIndex + 1) + "  ·  " + Fmt.compact(nextRun.snapshot.money) + " Geld";
        }
        double bw = Math.min(w * 0.42, 190 * u);
        double bx = x + w - pad - bw;
        Draw.fit(g, sub, tx, ty + bh / 2 - 9 * u, 13 * u, bx - tx - 10 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.cut(g, next.blurb, tx, ty + bh / 2 + 10 * u, 11 * u, bx - tx - 10 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        g.fillRoundRect(bx, ty, bw, bh, 16 * u, Colors.lerp(accent, 0xFFFFFF, 0.15 * lift));
        Icons.draw(g, Icon.PLAY, bx + 22 * u, ty + bh / 2, 8 * u, 0x000000, 0);
        g.text(resume ? "Fortsetzen" : "Spielen", bx + 36 * u, ty + bh / 2, 17 * u, 0x000000, Gfx.ALIGN_LEFT, true);
        return h;
    }

    /** Die Arena der Woche: Restzeit, eigener Platz, wer führt – und Antreten. */
    private double arenaCard(Gfx g, double x, double y, double w, double u) {
        if (arena == null) {
            arena = neontd.progress.Arena.of(app.today());
        }
        double pad = 16 * u;
        double h = 112 * u;
        double lift = pressed("arena") ? 1 : (hovered("arena") ? 0.5 : 0);
        Draw.accentCard(g, x, y, w, h, 20 * u, Theme.ACCENT, lift * 0.5);
        hit(x, y, w, h, "arena");
        double tx = x + pad;
        double ty = y + pad + 8 * u;
        String left = neontd.progress.Arena.remainingText(arena.remainingMillis(app.platform.nowMillis(),
                app.platform.localOffsetMillis()));
        g.text("ARENA  ·  " + arena.label() + "  ·  noch " + left, tx, ty, 11 * u, Colors.lighten(Theme.ACCENT, 0.15),
                Gfx.ALIGN_LEFT, true);
        ty += 26 * u;
        double bw = Math.min(w * 0.36, 150 * u);
        double bx = x + w - pad - bw;
        Draw.fit(g, arena.def.name, tx, ty, 22 * u, bx - tx - 10 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        ty += 24 * u;
        // Stand
        neontd.save.Leaderboard.ArenaEntry mine = null;
        neontd.save.Leaderboard.ArenaEntry lead = null;
        if (arenaList != null) {
            for (neontd.save.Leaderboard.ArenaEntry e : arenaList) {
                if (e.mine) {
                    mine = e;
                }
                if (lead == null || e.score > lead.score) {
                    lead = e;
                }
            }
        }
        long best = app.board.arenaBest(arena.id);
        String line1;
        String line2;
        if (mine != null) {
            line1 = "Platz " + mine.rank + " von " + arenaList.size() + "  ·  " + Fmt.points(mine.score);
            line2 = lead != null && !lead.mine ? "Es führt " + lead.name + " mit " + Fmt.points(lead.score) : "Du führst!";
        } else if (best > 0) {
            line1 = "Dein Bestwert: " + Fmt.points(best);
            line2 = lead != null ? "Es führt " + lead.name + " mit " + Fmt.points(lead.score) : "Wöchentlicher Highscore";
        } else {
            line1 = "Noch kein Lauf diese Woche";
            line2 = lead != null ? "Es führt " + lead.name + " mit " + Fmt.points(lead.score)
                    : "Gleiche Regeln für alle – wer holt die Woche?";
        }
        Draw.fit(g, line1, tx, ty, 14 * u, bx - tx - 10 * u, mine != null && mine.rank == 1 ? Theme.STAR : Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        Draw.cut(g, line2, tx, ty + 19 * u, 12 * u, bx - tx - 10 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        double bh = 44 * u;
        double by = y + h - pad - bh;
        g.fillRoundRect(bx, by, bw, bh, 14 * u, Colors.lerp(Theme.ACCENT, 0xFFFFFF, 0.15 * lift));
        Icons.draw(g, Icon.TROPHY, bx + 22 * u, by + bh / 2, 7 * u, 0x000000, 0);
        g.text("Antreten", bx + 36 * u, by + bh / 2, 16 * u, 0x000000, Gfx.ALIGN_LEFT, true);
        return h;
    }

    /** Ein anderer gespeicherter Lauf (nicht das nächste Level). */
    private double resumeRow(Gfx g, double x, double y, double w, double u) {
        LevelDef l = Levels.find(otherRunId);
        double h = 56 * u;
        double lift = pressed("resume") ? 1 : (hovered("resume") ? 0.5 : 0);
        Draw.card(g, x, y, w, h, 16 * u, lift * 0.5);
        hit(x, y, w, h, "resume");
        Draw.iconBadge(g, x + 30 * u, y + h / 2, 16 * u, Icon.SAVE, Worlds.accent(l.chapter));
        g.text("Gespeicherter Lauf", x + 56 * u, y + h / 2 - 9 * u, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(l.name + "  ·  Welle " + (otherRun.snapshot.waveIndex + 1), x + 56 * u, y + h / 2 + 10 * u, 12 * u,
                Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.chevron(g, x + w - 18 * u, y + h / 2, 5 * u, Theme.MUTED, true);
        return h;
    }

    /** Profilzeile: Level-Ring, Name, Titel, XP-Balken, Serie. */
    private double profileRow(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double h = 64 * u;
        double lift = pressed("profile") ? 1 : (hovered("profile") ? 0.5 : 0);
        Draw.card(g, x, y, w, h, 16 * u, lift * 0.5);
        hit(x, y, w, h, "profile");
        double cx = x + 32 * u;
        double cy = y + h / 2;
        double r = 20 * u;
        g.strokeCircle(cx, cy, r, 3 * u, Theme.SURFACE3);
        g.beginPath();
        g.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * p.levelFraction());
        g.stroke(3 * u, Theme.MAGENTA);
        g.text(Integer.toString(p.level()), cx, cy, 15 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        double tx = x + 64 * u;
        int streak = p.streakNow(app.today());
        double right = x + w - 14 * u;
        double chipW = 0;
        if (streak > 0) {
            chipW = Draw.chipRight(g, right, cy - 14 * u, 28 * u, Icon.FLAME, streak + (streak == 1 ? " Tag" : " Tage"),
                    p.playedToday(app.today()) ? Theme.ORANGE : Theme.MUTED, true) + 10 * u;
        }
        Draw.fit(g, p.displayName(), tx, cy - 10 * u, 16 * u, right - chipW - tx, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(p.title() + "  ·  " + p.totalStars() + " Sterne", tx, cy + 11 * u, 12 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        return h;
    }

    /** Karte „Heute“: Tages-Challenge und Tagesaufgaben mit Abholen. */
    private double todayCard(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        Quest[] q = p.dailyQuests();
        double pad = 14 * u;
        double rowH = 44 * u;
        double h = pad + 20 * u + 52 * u + q.length * rowH + pad;
        Draw.card(g, x, y, w, h, 18 * u, 0);
        double ty = y + pad + 8 * u;
        g.text("Heute", x + pad, ty, 16 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int claimable = p.claimable();
        if (claimable > 0) {
            Draw.chipRight(g, x + w - pad, ty - 12 * u, 24 * u, null, claimable + " abholbar", Theme.GREEN, true);
        }
        ty += 22 * u;
        // Tages-Challenge
        neontd.progress.Daily d = neontd.progress.Daily.today(app.today());
        double ch = 44 * u;
        double lift = pressed("daily") ? 1 : (hovered("daily") ? 0.5 : 0);
        Draw.raised(g, x + pad, ty, w - 2 * pad, ch, 12 * u, lift * 0.6);
        hit(x + pad, ty, w - 2 * pad, ch, "daily");
        Draw.iconBadge(g, x + pad + 22 * u, ty + ch / 2, 14 * u, Icon.CALENDAR, Worlds.accent(d.level.chapter));
        Draw.fit(g, "Tages-Challenge: " + d.level.name, x + pad + 44 * u, ty + ch / 2 - 8 * u, 14 * u, w - 2 * pad - 140 * u,
                Theme.TEXT, Gfx.ALIGN_LEFT, true);
        String dsub = d.modifier.label + (p.dailyBestToday(app.today()) > 0
                ? "  ·  Beste Welle " + p.dailyBestToday(app.today()) : "  ·  " + neontd.progress.Daily.REWARD + " Kristalle");
        Draw.fit(g, dsub, x + pad + 44 * u, ty + ch / 2 + 10 * u, 12 * u, w - 2 * pad - 140 * u, Theme.MUTED, Gfx.ALIGN_LEFT,
                false);
        g.fillRoundRect(x + w - pad - 80 * u, ty + 8 * u, 70 * u, ch - 16 * u, 10 * u, Colors.withAlpha(Theme.ACCENT, 0.16));
        g.text("Spielen", x + w - pad - 45 * u, ty + ch / 2, 13 * u, Colors.lighten(Theme.ACCENT, 0.3), Gfx.ALIGN_CENTER, true);
        ty += ch + 8 * u;
        for (int i = 0; i < q.length; i++) {
            Quest quest = q[i];
            boolean ready = p.dailyReady(i);
            boolean done = p.dailyClaimed[i];
            double ry = ty + i * rowH;
            String tag = "claim:" + i;
            if (ready) {
                hit(x + pad, ry, w - 2 * pad, rowH, tag);
            }
            double f = Math.min(1, (double) p.dailyProgress[i] / quest.target);
            int c = done ? Theme.MUTED : (ready ? Theme.GREEN : Theme.ACCENT);
            g.text(quest.text(), x + pad, ry + 12 * u, 13 * u, done ? Theme.MUTED : Theme.TEXT, Gfx.ALIGN_LEFT, false);
            String val = done ? "✓" : (ready ? "Abholen" : Fmt.compact(p.dailyProgress[i]) + "/" + Fmt.compact(quest.target));
            if (ready) {
                double bw = 72 * u;
                g.fillRoundRect(x + w - pad - bw, ry + 2 * u, bw, 22 * u, 8 * u,
                        Colors.withAlpha(Theme.GREEN, pressed(tag) ? 0.4 : 0.2));
                g.text("+" + quest.crystals + " ◆", x + w - pad - bw / 2, ry + 13 * u, 12 * u, Colors.lighten(Theme.GREEN, 0.3),
                        Gfx.ALIGN_CENTER, true);
            } else {
                g.text(val, x + w - pad, ry + 12 * u, 12 * u, Theme.MUTED, Gfx.ALIGN_RIGHT, true);
            }
            Draw.bar(g, x + pad, ry + 26 * u, w - 2 * pad, 5 * u, done ? 1 : f, c);
        }
        return h;
    }

    /** Welten in einer Reihe: Akzentpunkt, Name, Sterne, Schloss. */
    private double worldsCard(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double pad = 14 * u;
        int cols = w > 560 * u ? 4 : 2;
        int rows = (Worlds.COUNT + cols - 1) / cols;
        double tileH = 60 * u;
        double gapT = 8 * u;
        double h = pad + 20 * u + 10 * u + rows * (tileH + gapT) - gapT + pad;
        Draw.card(g, x, y, w, h, 18 * u, 0);
        double ty = y + pad + 8 * u;
        g.text("Welten", x + pad, ty, 16 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int total = p.totalStars();
        Draw.chipRight(g, x + w - pad, ty - 12 * u, 24 * u, Icon.STAR, total + " / " + Worlds.maxStarsThrough(Worlds.COUNT),
                Theme.STAR, true);
        ty += 22 * u;
        double tw = (w - 2 * pad - gapT * (cols - 1)) / cols;
        for (int i = 0; i < Worlds.COUNT; i++) {
            int world = i + 1;
            double tx = x + pad + (i % cols) * (tw + gapT);
            double yy = ty + (i / cols) * (tileH + gapT);
            boolean open = p.worldUnlocked(world);
            String tag = "world:" + world;
            double lift = pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
            Draw.raised(g, tx, yy, tw, tileH, 12 * u, open ? lift * 0.6 : 0);
            hit(tx, yy, tw, tileH, tag);
            int accent = Worlds.accent(world);
            g.save();
            g.alpha(open ? 1 : 0.45);
            g.fillCircle(tx + 16 * u, yy + 20 * u, 5 * u, accent);
            Draw.fit(g, Worlds.name(world), tx + 28 * u, yy + 20 * u, 13 * u, tw - 36 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            if (open) {
                int stars = p.worldStars(world);
                g.text(stars + " / " + (Worlds.LEVELS * 3) + " ★", tx + 12 * u, yy + 42 * u, 11 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
                Draw.bar(g, tx + tw - 70 * u, yy + 39 * u, 58 * u, 4 * u, stars / (double) (Worlds.LEVELS * 3), accent);
            } else {
                g.text("ab " + Worlds.REQUIRED_STARS[world - 1] + " ★", tx + 12 * u, yy + 42 * u, 11 * u, Theme.MUTED,
                        Gfx.ALIGN_LEFT, false);
                Draw.lock(g, tx + tw - 16 * u, yy + 40 * u, 6 * u);
            }
            g.restore();
        }
        return h;
    }

    /** Kacheln: Shop, Season, Erfolge, Bestenliste, Editor. */
    private double tiles(Gfx g, double x, double y, double w, double u) {
        String[][] items = {
            {"shop", "Shop", "Elite-Türme, Spezials, Forschung"},
            {"missions", "Season", "Season-Pass und Monatsaufgaben"},
            {"achievements", "Erfolge", "Errungenschaften"},
            {"board", "Bestenliste", "Tages-Challenge und Endlos-Rekorde"},
            {"editor", "Level-Editor", "Eigene Strecken zeichnen"},
        };
        Icon[] icons = {Icon.CRYSTAL, Icon.FLAG, Icon.TROPHY, Icon.BOARD, Icon.EDITOR};
        int[] colors = {Theme.CRYSTAL, Theme.GREEN, Theme.YELLOW, Theme.MAGENTA, Theme.CYAN};
        double rowH = 58 * u;
        double h = items.length * rowH;
        Draw.card(g, x, y, w, h, 18 * u, 0);
        for (int i = 0; i < items.length; i++) {
            double ry = y + i * rowH;
            String tag = items[i][0];
            double lift = pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
            if (lift > 0) {
                g.fillRoundRect(x + 4 * u, ry + 3 * u, w - 8 * u, rowH - 6 * u, 12 * u, Colors.withAlpha(0xFFFFFF, 0.04 * lift));
            }
            hit(x, ry, w, rowH, tag);
            Draw.iconBadge(g, x + 30 * u, ry + rowH / 2, 16 * u, icons[i], colors[i]);
            g.text(items[i][1], x + 58 * u, ry + rowH / 2 - 8 * u, 15 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            g.text(items[i][2], x + 58 * u, ry + rowH / 2 + 10 * u, 12 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            boolean badge = tag.equals("missions") && app.progress.claimable() > 0;
            if (badge) {
                g.fillCircle(x + w - 40 * u, ry + rowH / 2, 4 * u, Theme.RED);
            }
            Draw.chevron(g, x + w - 18 * u, ry + rowH / 2, 5 * u, Theme.MUTED, true);
            if (i < items.length - 1) {
                Draw.divider(g, x + 58 * u, ry + rowH, w - 58 * u);
            }
        }
        return h;
    }

    // ---------------------------------------------------------------------------------------- Aktionen

    @Override
    protected void onTap(Object tag) {
        String t = tag.toString();
        if (t.equals("play")) {
            startNext();
        } else if (t.equals("resume")) {
            LevelDef l = Levels.find(otherRunId);
            if (l != null && otherRun != null) {
                app.goTo(GameScene.resume(app, l, otherRun));
            }
        } else if (t.equals("arena")) {
            app.goTo(new ArenaScene(app));
        } else if (t.equals("profile")) {
            app.goTo(new ProfileScene(app));
        } else if (t.equals("daily")) {
            app.goTo(new DailyScene(app));
        } else if (t.startsWith("claim:")) {
            int i = Integer.parseInt(t.substring(6));
            int got = app.progress.claimDaily(i);
            if (got > 0) {
                app.commitProgress();
                toast("+" + got + " Kristalle", Theme.GREEN);
            }
        } else if (t.startsWith("world:")) {
            int world = Integer.parseInt(t.substring(6));
            if (app.progress.worldUnlocked(world)) {
                app.goTo(new LevelSelectScene(app, world));
            } else {
                toast("Noch " + (Worlds.REQUIRED_STARS[world - 1] - app.progress.totalStars()) + " Sterne bis "
                        + Worlds.name(world), Theme.STAR);
            }
        } else if (t.equals("shop")) {
            app.goTo(new ShopScene(app));
        } else if (t.equals("missions")) {
            app.goTo(new MissionsScene(app));
        } else if (t.equals("achievements")) {
            app.goTo(new AchievementsScene(app));
        } else if (t.equals("board")) {
            app.goTo(new LeaderboardScene(app));
        } else if (t.equals("editor")) {
            app.goTo(new EditorScene(app, null));
        }
    }

    private void startNext() {
        if (nextRun != null && nextRun.snapshot != null) {
            app.goTo(GameScene.resume(app, next, nextRun));
        } else {
            app.goTo(new GameScene(app, next));
        }
    }

    @Override
    public boolean key(String code, boolean ctrl) {
        if (code.equals("Enter") || code.equals("Space")) {
            startNext();
            return true;
        }
        if (code.equals("KeyE")) {
            app.goTo(new EditorScene(app, null));
            return true;
        }
        if (code.equals("KeyL")) {
            app.goTo(new LevelSelectScene(app));
            return true;
        }
        return false;
    }

    /** Für Tests und das Screenshot-Werkzeug: die Spielen-Taste liegt in der ersten Karte. */
    public double[] playAnchor() {
        return new double[] {contentX + contentW - 60 * app.vp.u, contentTop + 20 * app.vp.u};
    }
}
