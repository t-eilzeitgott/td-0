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
import neontd.ui.Kit;
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
            double k = Math.max(b.hover.value, b.press.value);
            if (k > 0) {
                g.fillCircle(0, 0, r, Colors.withAlpha(0xFFFFFF, 0.08 * k));
            }
            Icons.draw(g, Icon.GEAR, 0, 0, r * 0.46, Theme.TEXT, 0);
        };
        refresh();
        in.target = 1;
    }

    /** Position der Wortmarke im Kopf: {linke Kante, Mitte y, Zeichenhöhe}. */
    static double[] wordmarkBox(Viewport vp) {
        double u = vp.u;
        double m = Kit.MARGIN * u;
        double contentW = Math.min(vp.safeW() - 2 * m, 640 * u);
        double contentX = vp.insetL + (vp.safeW() - contentW) / 2;
        return new double[] {contentX, vp.insetT + 10 * u + 22 * u, 22 * u};
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
        double bs = 44 * u;
        settings.bounds(contentX + contentW - bs + 10 * u, vp.insetT + 10 * u, bs, bs);
        // die Kristalle rücken neben das Zahnrad
        chipShift = bs + 2 * u;
    }

    private double chipShift;

    @Override
    protected void drawCrystalChip(Gfx g, double u) {
        double cy = app.vp.insetT + 10 * u + 22 * u;
        Kit.iconValueRight(g, contentX + contentW - chipShift, cy, Icon.CRYSTAL, Fmt.compact(app.progress.crystals()),
                Theme.CRYSTAL, Theme.TEXT, 18 * u, u);
    }

    @Override
    protected void tick(double dt) {
        in.update(dt);
    }

    @Override
    public void render(Gfx g) {
        super.render(g);
        double[] box = wordmarkBox(app.vp);
        Wordmark.draw(g, box[0], box[1], box[2], 4, null, app.time); // gemeinsame Uhr: Flackern läuft über Szenenwechsel durch
    }

    // ---------------------------------------------------------------------------------------- Inhalt

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0 + 6 * u;
        double a = Easing.outCubic(Mathx.clamp01(in.value));
        g.save();
        g.alpha(a);
        g.translate(0, (1 - a) * 10 * u);

        y += hero(g, x, y, w, u) + Kit.SECTION_GAP * u;
        if (otherRun != null) {
            y += resumeRow(g, x, y, w, u) + 18 * u;
        }
        y += profileRow(g, x, y, w, u) + Kit.SECTION_GAP * u;
        y += arena(g, x, y, w, u) + Kit.SECTION_GAP * u;
        y += today(g, x, y, w, u) + Kit.SECTION_GAP * u;
        y += worlds(g, x, y, w, u) + Kit.SECTION_GAP * u;
        y += more(g, x, y, w, u) + 24 * u;
        g.text("Neon TD  ·  v" + neontd.app.Version.NAME, x + w / 2, y, Kit.T_CAPTION * u, Colors.withAlpha(0xFFFFFF, 0.3),
                Gfx.ALIGN_CENTER, false);
        y += 30 * u;
        g.restore();
        return y - y0;
    }

    private double lift(String tag) {
        return pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
    }

    /** Held: nächstes Level mit großer Vorschau direkt auf Schwarz und der Spielen-Taste. */
    private double hero(Gfx g, double x, double y, double w, double u) {
        int world = next.chapter;
        int accent = Worlds.accent(world);
        boolean resume = nextRun != null && nextRun.snapshot != null;
        double y0 = y;
        Kit.kicker(g, (resume ? "Fortsetzen" : "Weiter") + "  ·  " + Worlds.name(world) + "  ·  Level " + next.number,
                x, y + 8 * u, u, Colors.lighten(accent, 0.1));
        y += 30 * u;
        Draw.fit(g, next.name, x, y + 4 * u, Kit.T_HERO * u, w - 100 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Draw.stars(g, x + w - 34 * u, y + 4 * u, 10 * u, app.progress.stars(next.id), 3);
        y += 36 * u;
        String sub = next.style == null ? "" : next.style.label + "  ·  " + (next.bossLevel ? "Boss alle 5 Wellen" : "20 Wellen");
        if (resume) {
            sub = "Welle " + (nextRun.snapshot.waveIndex + 1) + "  ·  " + Fmt.compact(nextRun.snapshot.money) + " Geld";
        }
        g.text(sub, x, y, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 18 * u;
        // Vorschau ohne Rahmen: die Strecke leuchtet direkt auf dem Schwarz
        double previewH = Math.min(w * 0.62, 300 * u);
        g.radialGlow(x + w / 2, y + previewH / 2, Math.min(w * 0.5, previewH * 0.62), Colors.withAlpha(accent, 0.07));
        g.save();
        g.clipRect(x - 10 * u, y, w + 20 * u, previewH);
        LevelPreview.draw(g, nextPaths, next.width, next.height, x + 6 * u, y + 10 * u, w - 12 * u, previewH - 20 * u,
                Worlds.lane(world), time);
        g.restore();
        hit(x, y, w, previewH, "play");
        y += previewH + 8 * u;
        if (next.blurb != null && !next.blurb.isEmpty()) {
            y += Kit.paragraph(g, next.blurb, x, y, w, Kit.T_BODY * u, Colors.withAlpha(0xFFFFFF, 0.62), u) + 18 * u;
        }
        double bh = Kit.BUTTON * u;
        Kit.primary(g, x, y, w, bh, resume ? "Fortsetzen" : "Spielen", Icon.PLAY, accent, lift("play"), u);
        hit(x, y, w, bh, "play");
        y += bh;
        return y - y0;
    }

    /** Ein anderer gespeicherter Lauf (nicht das nächste Level). */
    private double resumeRow(Gfx g, double x, double y, double w, double u) {
        LevelDef l = Levels.find(otherRunId);
        double h = Kit.ROW * u;
        hit(x, y, w, h, "resume");
        Icons.draw(g, Icon.SAVE, x + 12 * u, y + h / 2, 9 * u, Worlds.accent(l.chapter), 0);
        Kit.row(g, x, y, w, h, 40 * u, "Gespeicherter Lauf", l.name + "  ·  Welle " + (otherRun.snapshot.waveIndex + 1),
                null, 0, true, false, lift("resume"), false, u);
        return h;
    }

    /** Profil: Level-Ring, Name, Titel, Serie – eine offene Zeile. */
    private double profileRow(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double h = 72 * u;
        Kit.rowPress(g, x, y, w, h, lift("profile"), u);
        hit(x, y, w, h, "profile");
        double r = 25 * u;
        Kit.ring(g, x + r, y + h / 2, r, p.levelFraction(), Theme.MAGENTA, Integer.toString(p.level()), u);
        double tx = x + 2 * r + 16 * u;
        int streak = p.streakNow(app.today());
        double right = x + w - 22 * u;
        double sw = 0;
        if (streak > 0) {
            sw = Kit.iconValueRight(g, right, y + h / 2, Icon.FLAME, streak + (streak == 1 ? " Tag" : " Tage"),
                    p.playedToday(app.today()) ? Theme.ORANGE : Theme.MUTED, p.playedToday(app.today()) ? Theme.TEXT : Theme.MUTED,
                    Kit.T_BODY * u, u) + 14 * u;
        }
        Draw.cut(g, p.displayName(), tx, y + h / 2 - 10 * u, 19 * u, right - sw - tx, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Draw.cut(g, p.title() + "  ·  " + p.totalStars() + " Sterne", tx, y + h / 2 + 13 * u, Kit.T_CAPTION * u,
                right - sw - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.chevron(g, x + w - 6 * u, y + h / 2, 5.5 * u, Colors.withAlpha(0xFFFFFF, 0.35), true);
        return h;
    }

    /** Arena der Woche als offene Sektion. */
    private double arena(Gfx g, double x, double y, double w, double u) {
        if (arena == null) {
            arena = neontd.progress.Arena.of(app.today());
        }
        double y0 = y;
        String left = neontd.progress.Arena.remainingText(arena.remainingMillis(app.platform.nowMillis(),
                app.platform.localOffsetMillis()));
        y += Kit.section(g, "Arena", arena.label() + "  ·  noch " + left, Theme.MUTED, x, y, w, u);
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
        String line;
        int lineColor = Theme.MUTED;
        if (mine != null) {
            line = "Platz " + mine.rank + " von " + arenaList.size() + "  ·  " + Fmt.points(mine.score)
                    + (lead != null && !lead.mine ? "  ·  " + lead.name + " führt" : "");
            lineColor = mine.rank == 1 ? Theme.STAR : Theme.TEXT;
        } else if (best > 0) {
            line = "Dein Bestwert " + Fmt.points(best);
        } else {
            line = lead != null ? "Es führt " + lead.name + " mit " + Fmt.points(lead.score) : "Noch kein Lauf diese Woche";
        }
        double h = 76 * u;
        Kit.rowPress(g, x, y, w, h, lift("arena"), u);
        hit(x, y, w, h, "arena");
        Icons.draw(g, Icon.TROPHY, x + 14 * u, y + h / 2, 11 * u, Theme.ACCENT, 0);
        double tx = x + 44 * u;
        double lw = g.textWidth("Antreten", Kit.T_BODY * u, true);
        Draw.cut(g, arena.def.name, tx, y + h / 2 - 12 * u, 22 * u, x + w - lw - 40 * u - tx, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Draw.cut(g, line, tx, y + h / 2 + 14 * u, Kit.T_CAPTION * u, x + w - lw - 40 * u - tx, lineColor, Gfx.ALIGN_LEFT,
                mine != null);
        Kit.link(g, "Antreten", x + w - 22 * u, y + h / 2, Theme.ACCENT, lift("arena"), u);
        Draw.chevron(g, x + w - 6 * u, y + h / 2, 5.5 * u, Theme.ACCENT, true);
        y += h;
        return y - y0;
    }

    /** Heute: Tages-Challenge und Tagesaufgaben. */
    private double today(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double y0 = y;
        int claimable = p.claimable();
        y += Kit.section(g, "Heute", claimable > 0 ? claimable + " abholbar" : null, Theme.GREEN, x, y, w, u);
        neontd.progress.Daily d = neontd.progress.Daily.today(app.today());
        double h = Kit.ROW * u;
        hit(x, y, w, h, "daily");
        Icons.draw(g, Icon.CALENDAR, x + 14 * u, y + h / 2, 10 * u, Worlds.accent(d.level.chapter), 0);
        String dsub = "Tages-Challenge  ·  " + d.modifier.label + (p.dailyBestToday(app.today()) > 0
                ? "  ·  Beste Welle " + p.dailyBestToday(app.today()) : "  ·  " + neontd.progress.Daily.REWARD + " Kristalle");
        Kit.row(g, x, y, w, h, 44 * u, d.level.name, dsub, "Spielen", Theme.ACCENT, true, true, lift("daily"), false, u);
        y += h;
        Quest[] q = p.dailyQuests();
        double rh = 64 * u;
        for (int i = 0; i < q.length; i++) {
            Quest quest = q[i];
            boolean ready = p.dailyReady(i);
            boolean done = p.dailyClaimed[i];
            String tag = "claim:" + i;
            double f = Math.min(1, (double) p.dailyProgress[i] / quest.target);
            if (ready) {
                hit(x, y, w, rh, tag);
            }
            double right = x + w;
            double vw;
            if (ready) {
                vw = Kit.pill(g, "Abholen  +" + quest.crystals + " ◆", right, y + rh / 2 - 8 * u, Theme.GREEN,
                        pressed(tag) ? 1 : 0, u);
            } else {
                String v = done ? "Erledigt" : Fmt.compact(p.dailyProgress[i]) + " / " + Fmt.compact(quest.target);
                g.text(v, right, y + rh / 2 - 8 * u, Kit.T_BODY * u, done ? Theme.GREEN : Theme.MUTED, Gfx.ALIGN_RIGHT, true);
                vw = g.textWidth(v, Kit.T_BODY * u, true);
            }
            Draw.cut(g, quest.text(), x + 44 * u, y + rh / 2 - 8 * u, Kit.T_ROW * u, right - vw - 16 * u - x - 44 * u,
                    done ? Theme.MUTED : Theme.TEXT, Gfx.ALIGN_LEFT, true);
            int c = done ? Colors.withAlpha(Theme.GREEN, 0.5) : (ready ? Theme.GREEN : Theme.ACCENT);
            if (done) {
                Icons.draw(g, Icon.CHECK, x + 14 * u, y + rh / 2 - 8 * u, 8 * u, Theme.GREEN, 0);
            } else {
                g.strokeCircle(x + 14 * u, y + rh / 2 - 8 * u, 9 * u, 1.6 * u, Colors.withAlpha(0xFFFFFF, 0.25));
            }
            Kit.meter(g, x + 44 * u, y + rh / 2 + 12 * u, w - 44 * u, done ? 1 : f, c, u);
            if (i < q.length - 1) {
                Kit.hairline(g, x + 44 * u, y + rh - 1, w - 44 * u);
            }
            y += rh;
        }
        return y - y0;
    }

    /** Welten: zweispaltig, ohne Kacheln – Farbpunkt, Name, Sterne und ein feiner Balken. */
    private double worlds(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double y0 = y;
        y += Kit.section(g, "Welten", "★ " + p.totalStars() + " / " + Worlds.maxStarsThrough(Worlds.COUNT), Theme.STAR,
                x, y, w, u);
        int cols = w > 520 * u ? 4 : 2;
        double colGap = 24 * u;
        double cw = (w - colGap * (cols - 1)) / cols;
        double ch = 74 * u;
        for (int i = 0; i < Worlds.COUNT; i++) {
            int world = i + 1;
            double cx = x + (i % cols) * (cw + colGap);
            double cy = y + (i / cols) * ch;
            boolean open = p.worldUnlocked(world);
            String tag = "world:" + world;
            hit(cx, cy, cw, ch, tag);
            Kit.rowPress(g, cx + 4 * u, cy, cw - 8 * u, ch, open ? lift(tag) : 0, u);
            int accent = Worlds.accent(world);
            g.save();
            g.alpha(open ? 1 : 0.42);
            Kit.dot(g, cx + 6 * u, cy + 24 * u, 5 * u, accent);
            Draw.cut(g, Worlds.name(world), cx + 20 * u, cy + 24 * u, Kit.T_ROW * u, cw - 24 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            if (open) {
                int stars = p.worldStars(world);
                g.text(stars + " / " + (Worlds.LEVELS * 3) + " ★", cx + 20 * u, cy + 47 * u, Kit.T_CAPTION * u, Theme.MUTED,
                        Gfx.ALIGN_LEFT, false);
                Kit.meter(g, cx + 20 * u, cy + 60 * u, cw - 24 * u, stars / (double) (Worlds.LEVELS * 3), accent, u);
            } else {
                Draw.lock(g, cx + 26 * u, cy + 48 * u, 5.5 * u);
                g.text("ab " + Worlds.REQUIRED_STARS[world - 1] + " ★", cx + 38 * u, cy + 48 * u, Kit.T_CAPTION * u, Theme.MUTED,
                        Gfx.ALIGN_LEFT, false);
            }
            g.restore();
        }
        y += ((Worlds.COUNT + cols - 1) / cols) * ch;
        return y - y0;
    }

    /** Weitere Bereiche als schlichte Liste. */
    private double more(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += Kit.section(g, "Mehr", null, 0, x, y, w, u);
        String[][] items = {
            {"shop", "Shop", "Elite-Türme, Spezials, Forschung"},
            {"missions", "Season", "Season-Pass und Monatsaufgaben"},
            {"achievements", "Erfolge", "Errungenschaften"},
            {"board", "Bestenliste", "Tages-Challenge und Endlos-Rekorde"},
            {"editor", "Level-Editor", "Eigene Strecken zeichnen"},
        };
        Icon[] icons = {Icon.CRYSTAL, Icon.FLAG, Icon.TROPHY, Icon.BOARD, Icon.EDITOR};
        int[] colors = {Theme.CRYSTAL, Theme.GREEN, Theme.YELLOW, Theme.MAGENTA, Theme.CYAN};
        double rh = Kit.ROW * u;
        for (int i = 0; i < items.length; i++) {
            String tag = items[i][0];
            hit(x, y, w, rh, tag);
            Icons.draw(g, icons[i], x + 14 * u, y + rh / 2, 10 * u, colors[i], 0);
            boolean badge = tag.equals("missions") && app.progress.claimable() > 0;
            Kit.row(g, x, y, w, rh, 44 * u, items[i][1], items[i][2], badge ? "●" : null, Theme.RED, true,
                    i < items.length - 1, lift(tag), false, u);
            y += rh;
        }
        return y - y0;
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
        return new double[] {contentX + contentW / 2, contentTop + 200 * app.vp.u};
    }

    @Override
    public boolean swipeBack() {
        return false;
    }
}
