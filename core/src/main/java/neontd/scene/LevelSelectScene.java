package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.level.Worlds;
import neontd.physics.Path;
import neontd.progress.Medals;
import neontd.progress.Progress;
import neontd.render.LevelPreview;
import neontd.save.RunSave;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Fmt;

/**
 * Levelauswahl in zwei Stufen: die Welten (Akzent, Sterne, Hürde) und je Welt die sechs Strecken mit Vorschau,
 * Sternen, Rekord und Medaille. Ein Tipp auf ein Level öffnet ein Blatt mit Spielen / Fortsetzen / Endlos.
 * Eigene Level aus dem Editor haben eine eigene Seite.
 */
public final class LevelSelectScene extends PanelScene {
    /** 0 = Welten, 1–8 = eine Welt, {@link Levels#CUSTOM} = eigene Level. */
    private final int chapter;
    private final List<LevelDef> levels = new ArrayList<>();
    private final List<Path[]> paths = new ArrayList<>();

    public LevelSelectScene(App app) {
        this(app, 0);
    }

    public LevelSelectScene(App app, int chapter) {
        super(app, chapter == 0 ? "Welten" : Levels.chapterName(chapter),
                chapter >= 1 && chapter <= Worlds.COUNT ? Worlds.accent(chapter) : Theme.ACCENT);
        this.chapter = chapter;
        back.onClick = () -> app.goTo(chapter == 0 ? new MenuScene(app) : new LevelSelectScene(app, 0));
        if (chapter >= 1 && chapter <= Worlds.COUNT) {
            for (LevelDef l : Levels.chapter(chapter)) {
                levels.add(l);
                paths.add(l.buildPaths());
            }
        } else if (chapter == Levels.CUSTOM) {
            for (LevelDef l : app.levels.loadAll()) {
                levels.add(l);
                paths.add(l.buildPaths());
            }
        }
    }

    @Override
    public boolean back() {
        if (super.back()) {
            return true;
        }
        if (chapter != 0) {
            app.goTo(new LevelSelectScene(app, 0));
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------------------------------- Inhalt

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        if (chapter == 0) {
            y = worlds(g, x, y, w, u);
        } else if (chapter == Levels.CUSTOM) {
            y = custom(g, x, y, w, u);
        } else {
            y = worldLevels(g, x, y, w, u);
        }
        return y - y0 + 16 * u;
    }

    private double worlds(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int total = p.totalStars();
        g.text(total + " von " + Worlds.maxStarsThrough(Worlds.COUNT) + " Sternen", x + 2 * u, y + 10 * u, 13 * u, Theme.MUTED,
                Gfx.ALIGN_LEFT, false);
        y += 30 * u;
        boolean wide = w > 560 * u;
        double gap = 10 * u;
        double cw = wide ? (w - gap) / 2 : w;
        double ch = 84 * u;
        for (int i = 0; i < Worlds.COUNT; i++) {
            int world = i + 1;
            double cx = wide ? x + (i % 2) * (cw + gap) : x;
            double cy = wide ? y + (i / 2) * (ch + gap) : y + i * (ch + gap);
            boolean open = p.worldUnlocked(world);
            String tag = "world:" + world;
            double lift = pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
            int accent = Worlds.accent(world);
            Draw.accentCard(g, cx, cy, cw, ch, 18 * u, open ? accent : Theme.SURFACE3, open ? lift * 0.6 : 0);
            hit(cx, cy, cw, ch, tag);
            g.save();
            g.alpha(open ? 1 : 0.5);
            g.fillCircle(cx + 28 * u, cy + ch / 2, 10 * u, Colors.withAlpha(accent, 0.25));
            g.fillCircle(cx + 28 * u, cy + ch / 2, 5 * u, accent);
            g.text(Worlds.name(world), cx + 52 * u, cy + ch / 2 - 14 * u, 17 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            Draw.fit(g, Worlds.TAGLINES[i], cx + 52 * u, cy + ch / 2 + 6 * u, 12 * u, cw - 150 * u, Theme.MUTED, Gfx.ALIGN_LEFT,
                    false);
            if (open) {
                int stars = p.worldStars(world);
                g.text(stars + " / " + (Worlds.LEVELS * 3) + " ★", cx + 52 * u, cy + ch / 2 + 24 * u, 11 * u, Theme.MUTED,
                        Gfx.ALIGN_LEFT, false);
                Draw.bar(g, cx + cw - 110 * u, cy + ch / 2 + 20 * u, 80 * u, 5 * u, stars / (double) (Worlds.LEVELS * 3), accent);
                Draw.chevron(g, cx + cw - 18 * u, cy + ch / 2, 5 * u, Theme.MUTED, true);
            } else {
                Draw.lock(g, cx + cw - 22 * u, cy + ch / 2 - 8 * u, 8 * u);
                g.text("ab " + Worlds.REQUIRED_STARS[world - 1] + " ★", cx + cw - 22 * u, cy + ch / 2 + 16 * u, 11 * u,
                        Theme.MUTED, Gfx.ALIGN_CENTER, false);
            }
            g.restore();
        }
        int rows = wide ? (Worlds.COUNT + 1) / 2 : Worlds.COUNT;
        y += rows * (ch + gap);
        // Eigene Level
        double eh = 64 * u;
        double lift = pressed("custom") ? 1 : (hovered("custom") ? 0.5 : 0);
        Draw.card(g, x, y, w, eh, 18 * u, lift * 0.6);
        hit(x, y, w, eh, "custom");
        Draw.iconBadge(g, x + 30 * u, y + eh / 2, 16 * u, Icon.EDITOR, Theme.MAGENTA);
        g.text("Eigene Level", x + 58 * u, y + eh / 2 - 8 * u, 15 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int n = app.levels.loadAll().size();
        g.text(n == 0 ? "Noch keine – zeichne eine im Editor" : n + (n == 1 ? " Level" : " Level") + " aus dem Editor",
                x + 58 * u, y + eh / 2 + 10 * u, 12 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.chevron(g, x + w - 18 * u, y + eh / 2, 5 * u, Theme.MUTED, true);
        return y + eh;
    }

    private double worldLevels(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int accent = Worlds.accent(chapter);
        int stars = p.worldStars(chapter);
        g.text(Worlds.TAGLINES[chapter - 1] + "  ·  " + stars + " / " + (Worlds.LEVELS * 3) + " ★", x + 2 * u, y + 10 * u, 13 * u,
                Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 30 * u;
        boolean wide = w > 560 * u;
        double gap = 10 * u;
        double cw = wide ? (w - gap) / 2 : w;
        double previewH = Math.min(cw * 0.36, 150 * u);
        double ch = 16 * u + previewH + 64 * u;
        for (int i = 0; i < levels.size(); i++) {
            LevelDef l = levels.get(i);
            double cx = wide ? x + (i % 2) * (cw + gap) : x;
            double cy = wide ? y + (i / 2) * (ch + gap) : y + i * (ch + gap);
            boolean open = p.levelUnlocked(l.id);
            levelCard(g, cx, cy, cw, ch, previewH, l, paths.get(i), open, accent, u, "level:" + i);
        }
        int rows = wide ? (levels.size() + 1) / 2 : levels.size();
        return y + rows * (ch + gap) - gap;
    }

    private void levelCard(Gfx g, double cx, double cy, double cw, double ch, double previewH, LevelDef l, Path[] pp,
                           boolean open, int accent, double u, String tag) {
        Progress p = app.progress;
        double lift = pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
        Draw.card(g, cx, cy, cw, ch, 18 * u, open ? lift * 0.6 : 0);
        hit(cx, cy, cw, ch, tag);
        double pad = 10 * u;
        g.fillRoundRect(cx + pad, cy + pad, cw - 2 * pad, previewH, 12 * u, Theme.BG);
        g.save();
        g.alpha(open ? 1 : 0.35);
        LevelPreview.draw(g, pp, l.width, l.height, cx + pad + 6 * u, cy + pad + 6 * u, cw - 2 * pad - 12 * u, previewH - 12 * u,
                l.builtin ? Worlds.lane(l.chapter) : 0x4A62FF, open ? time : 0);
        g.restore();
        if (!open) {
            Draw.lock(g, cx + cw / 2, cy + pad + previewH / 2, 12 * u);
        }
        double ty = cy + pad + previewH + 18 * u;
        String name = l.builtin ? l.number + "  ·  " + l.name : l.name;
        Draw.fit(g, name, cx + pad + 4 * u, ty, 16 * u, cw - 2 * pad - 90 * u, open ? Theme.TEXT : Theme.MUTED, Gfx.ALIGN_LEFT, true);
        if (l.builtin) {
            Draw.stars(g, cx + cw - pad - 32 * u, ty, 8 * u, p.stars(l.id), 3);
        }
        ty += 20 * u;
        String sub;
        int best = p.bestOverall(l.id);
        int medal = p.medal(l.id);
        RunSave run = app.saves.resumable(l.id);
        if (!open) {
            sub = l.number == 1 ? "Welt gesperrt" : "Einen Stern in Level " + (l.number - 1) + " holen";
        } else if (run != null) {
            sub = "Gespeichert · Welle " + (run.snapshot.waveIndex + 1) + "  ·  " + styleText(l);
        } else if (best > 0) {
            sub = "Rekord Welle " + best + (medal > 0 ? "  ·  " + Medals.NAMES[medal - 1].charAt(0) + Medals.NAMES[medal - 1].substring(1).toLowerCase() : "")
                    + "  ·  " + styleText(l);
        } else {
            sub = styleText(l) + (l.blurb.isEmpty() ? "" : "  ·  " + l.blurb);
        }
        Draw.fit(g, sub, cx + pad + 4 * u, ty, 12 * u, cw - 2 * pad - 8 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        if (open && run != null) {
            g.fillCircle(cx + cw - pad - 6 * u, ty, 3 * u, Theme.GREEN);
        }
    }

    private static String styleText(LevelDef l) {
        if (l.style == null) {
            return l.waveCount + " Wellen";
        }
        return l.style.label + (l.altStyle != null ? " / " + l.altStyle.label : "") + (l.bossLevel ? "  ·  Boss" : "");
    }

    private double custom(Gfx g, double x, double y, double w, double u) {
        double eh = 56 * u;
        double lift = pressed("new") ? 1 : (hovered("new") ? 0.5 : 0);
        g.fillRoundRect(x, y, w, eh, 16 * u, Colors.lerp(Theme.ACCENT, 0xFFFFFF, 0.15 * lift));
        hit(x, y, w, eh, "new");
        Icons.draw(g, Icon.PLUS, x + 26 * u, y + eh / 2, 8 * u, 0x000000, 0);
        g.text("Neues Level zeichnen", x + 44 * u, y + eh / 2, 16 * u, 0x000000, Gfx.ALIGN_LEFT, true);
        y += eh + 12 * u;
        if (levels.isEmpty()) {
            double h = 80 * u;
            Draw.card(g, x, y, w, h, 16 * u, 0);
            g.text("Noch keine eigenen Level.", x + w / 2, y + h / 2, 14 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            return y + h;
        }
        boolean wide = w > 560 * u;
        double gap = 10 * u;
        double cw = wide ? (w - gap) / 2 : w;
        double previewH = Math.min(cw * 0.36, 150 * u);
        double ch = 16 * u + previewH + 64 * u;
        for (int i = 0; i < levels.size(); i++) {
            double cx = wide ? x + (i % 2) * (cw + gap) : x;
            double cy = wide ? y + (i / 2) * (ch + gap) : y + i * (ch + gap);
            levelCard(g, cx, cy, cw, ch, previewH, levels.get(i), paths.get(i), true, Theme.ACCENT, u, "level:" + i);
        }
        int rows = wide ? (levels.size() + 1) / 2 : levels.size();
        return y + rows * (ch + gap) - gap;
    }

    // ------------------------------------------------------------------------------------- Aktionen

    @Override
    protected void onTap(Object tag) {
        String t = tag.toString();
        if (t.startsWith("world:")) {
            int world = Integer.parseInt(t.substring(6));
            if (app.progress.worldUnlocked(world)) {
                app.goTo(new LevelSelectScene(app, world));
            } else {
                toast("Noch " + (Worlds.REQUIRED_STARS[world - 1] - app.progress.totalStars()) + " Sterne bis "
                        + Worlds.name(world), Theme.STAR);
            }
        } else if (t.equals("custom")) {
            app.goTo(new LevelSelectScene(app, Levels.CUSTOM));
        } else if (t.equals("new")) {
            app.goTo(new EditorScene(app, null));
        } else if (t.startsWith("level:")) {
            open(levels.get(Integer.parseInt(t.substring(6))));
        }
    }

    /** Blatt mit den Möglichkeiten eines Levels; ohne Wahl geht es direkt los. */
    private void open(LevelDef l) {
        Progress p = app.progress;
        if (!p.levelUnlocked(l.id)) {
            toast(l.number == 1 ? "Diese Welt ist noch gesperrt" : "Hol dir zuerst einen Stern in Level " + (l.number - 1),
                    Theme.MUTED);
            return;
        }
        RunSave run = app.saves.resumable(l.id);
        boolean endless = p.endlessUnlocked(l.id) || !l.builtin;
        List<Button> buttons = new ArrayList<>();
        if (run != null) {
            buttons.add(new Button("Fortsetzen · Welle " + (run.snapshot.waveIndex + 1), Icon.PLAY, Theme.GREEN,
                    () -> app.goTo(GameScene.resume(app, l, run))));
            buttons.add(new Button("Neu starten", Icon.LOOP, Theme.TEXT, () -> {
                app.saves.endRun(l.id);
                app.goTo(new GameScene(app, l));
            }));
        } else {
            buttons.add(new Button("Spielen", Icon.PLAY, Theme.ACCENT, () -> app.goTo(new GameScene(app, l))));
        }
        if (endless) {
            buttons.add(new Button("Endlosmodus", Icon.WAVES, Theme.MAGENTA, () -> {
                app.saves.endRun(l.id);
                app.goTo(GameScene.endless(app, l));
            }));
        }
        if (!l.builtin) {
            buttons.add(new Button("Bearbeiten", Icon.PENCIL, Theme.TEXT, () -> app.goTo(new EditorScene(app, l))));
            buttons.add(new Button("Löschen", Icon.TRASH, Theme.RED, () -> {
                app.levels.delete(l.id);
                app.cloud.markDirty();
                app.goTo(new LevelSelectScene(app, Levels.CUSTOM));
            }));
        }
        if (run == null && !endless && l.builtin) {
            // nichts zu wählen: direkt los
            app.goTo(new GameScene(app, l));
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(styleText(l) + "  ·  " + l.startLives + " Leben  ·  " + Fmt.compact(l.startMoney) + " Startgeld");
        int best = p.bestOverall(l.id);
        if (best > 0) {
            int medal = p.medal(l.id);
            int next = Medals.nextTarget(medal);
            lines.add("Rekord: Welle " + best + (next > 0 ? "  ·  nächste Medaille ab Welle " + next : "  ·  Platin!"));
        }
        if (l.builtin) {
            lines.add("Sterne: 1 Sieg · 2 mit halben Leben · 3 ohne Verlust");
        }
        sheet(l.name, Theme.ACCENT, lines.toArray(new String[0]), buttons.toArray(new Button[0]));
    }
}
