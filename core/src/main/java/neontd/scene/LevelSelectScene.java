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
import neontd.ui.Kit;

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

    private double lift(String tag) {
        return pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
    }

    private double worlds(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int total = p.totalStars();
        Kit.iconValue(g, x, y + 10 * u, Icon.STAR, total + " von " + Worlds.maxStarsThrough(Worlds.COUNT) + " Sternen",
                Theme.STAR, Theme.MUTED, Kit.T_BODY * u, u);
        y += 30 * u;
        boolean wide = w > 560 * u;
        double gap = 28 * u;
        double cw = wide ? (w - gap) / 2 : w;
        double ch = 88 * u;
        for (int i = 0; i < Worlds.COUNT; i++) {
            int world = i + 1;
            double cx = wide ? x + (i % 2) * (cw + gap) : x;
            double cy = wide ? y + (i / 2) * ch : y + i * ch;
            boolean open = p.worldUnlocked(world);
            String tag = "world:" + world;
            int accent = Worlds.accent(world);
            hit(cx, cy, cw, ch, tag);
            Kit.rowPress(g, cx, cy, cw, ch, open ? lift(tag) : 0, u);
            g.save();
            g.alpha(open ? 1 : 0.45);
            Kit.dot(g, cx + 8 * u, cy + 26 * u, 6 * u, accent);
            double tx = cx + 30 * u;
            double right = cx + cw - (open ? 22 * u : 0);
            Draw.cut(g, Worlds.name(world), tx, cy + 26 * u, 20 * u, right - tx - 70 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            Draw.cut(g, Worlds.TAGLINES[i], tx, cy + 50 * u, Kit.T_CAPTION * u, right - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            if (open) {
                int stars = p.worldStars(world);
                g.text(stars + " / " + (Worlds.LEVELS * 3) + " ★", right, cy + 26 * u, Kit.T_BODY * u,
                        stars > 0 ? Theme.STAR : Theme.MUTED, Gfx.ALIGN_RIGHT, true);
                Kit.meter(g, tx, cy + 68 * u, right - tx, stars / (double) (Worlds.LEVELS * 3), accent, u);
                Draw.chevron(g, cx + cw - 6 * u, cy + 26 * u, 5.5 * u, Colors.withAlpha(0xFFFFFF, 0.35), true);
            } else {
                String req = "ab " + Worlds.REQUIRED_STARS[world - 1] + " ★";
                g.text(req, right, cy + 26 * u, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_RIGHT, true);
                Draw.lock(g, right - g.textWidth(req, Kit.T_BODY * u, true) - 14 * u, cy + 26 * u, 6 * u);
            }
            g.restore();
            if (!wide && i < Worlds.COUNT - 1) {
                Kit.hairline(g, cx + 30 * u, cy + ch - 1, cw - 30 * u);
            }
        }
        int rows = wide ? (Worlds.COUNT + 1) / 2 : Worlds.COUNT;
        y += rows * ch + Kit.SECTION_GAP * u;
        // Eigene Level
        y += Kit.section(g, "Eigene Level", null, 0, x, y, w, u);
        double eh = Kit.ROW * u;
        hit(x, y, w, eh, "custom");
        Icons.draw(g, Icon.EDITOR, x + 14 * u, y + eh / 2, 10 * u, Theme.MAGENTA, 0);
        int n = app.levels.loadAll().size();
        Kit.row(g, x, y, w, eh, 44 * u, "Aus dem Editor",
                n == 0 ? "Noch keine – zeichne eine im Editor" : n + " Level", null, 0, true, false, lift("custom"), false, u);
        return y + eh;
    }

    private double worldLevels(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int accent = Worlds.accent(chapter);
        int stars = p.worldStars(chapter);
        Draw.cut(g, Worlds.TAGLINES[chapter - 1], x, y + 10 * u, Kit.T_BODY * u, w - 90 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        g.text(stars + " / " + (Worlds.LEVELS * 3) + " ★", x + w, y + 10 * u, Kit.T_BODY * u, Theme.STAR, Gfx.ALIGN_RIGHT, true);
        Kit.meter(g, x, y + 30 * u, w, stars / (double) (Worlds.LEVELS * 3), accent, u);
        y += 52 * u;
        return levelList(g, x, y, w, u, accent);
    }

    private double levelList(Gfx g, double x, double y, double w, double u, int accent) {
        Progress p = app.progress;
        boolean wide = w > 560 * u;
        double gap = 28 * u;
        double cw = wide ? (w - gap) / 2 : w;
        double previewH = Math.min(cw * 0.46, 200 * u);
        double ch = previewH + 70 * u;
        for (int i = 0; i < levels.size(); i++) {
            LevelDef l = levels.get(i);
            double cx = wide ? x + (i % 2) * (cw + gap) : x;
            double cy = wide ? y + (i / 2) * (ch + gap) : y + i * (ch + gap);
            boolean open = !l.builtin || p.levelUnlocked(l.id);
            levelItem(g, cx, cy, cw, ch, previewH, l, paths.get(i), open, u, "level:" + i);
        }
        int rows = wide ? (levels.size() + 1) / 2 : levels.size();
        return y + rows * (ch + gap) - gap;
    }

    /** Ein Level: Vorschau direkt auf Schwarz, darunter Nummer, Name, Sterne und eine Zeile Status. */
    private void levelItem(Gfx g, double cx, double cy, double cw, double ch, double previewH, LevelDef l, Path[] pp,
                           boolean open, double u, String tag) {
        Progress p = app.progress;
        hit(cx, cy, cw, ch, tag);
        Kit.rowPress(g, cx, cy, cw, ch, open ? lift(tag) : 0, u);
        int lane = l.builtin ? Worlds.lane(l.chapter) : 0x4A62FF;
        g.save();
        g.clipRect(cx - 10 * u, cy, cw + 20 * u, previewH);
        if (open) {
            g.radialGlow(cx + cw / 2, cy + previewH / 2, cw * 0.5, Colors.withAlpha(lane, 0.05));
        }
        g.alpha(open ? 1 : 0.3);
        LevelPreview.draw(g, pp, l.width, l.height, cx + 6 * u, cy + 8 * u, cw - 12 * u, previewH - 16 * u, lane,
                open ? time : 0);
        g.restore();
        if (!open) {
            Draw.lock(g, cx + cw / 2, cy + previewH / 2, 14 * u);
        }
        double ty = cy + previewH + 22 * u;
        double nx = cx;
        if (l.builtin) {
            String num = Integer.toString(l.number);
            g.text(num, nx, ty, 20 * u, open ? Colors.lighten(Worlds.accent(l.chapter), 0.1) : Theme.MUTED, Gfx.ALIGN_LEFT, true);
            nx += g.textWidth(num, 20 * u, true) + 10 * u;
        }
        Draw.cut(g, l.name, nx, ty, 20 * u, cx + cw - 90 * u - nx, open ? Theme.TEXT : Theme.MUTED, Gfx.ALIGN_LEFT, true);
        if (l.builtin) {
            Draw.stars(g, cx + cw - 30 * u, ty, 9 * u, p.stars(l.id), 3);
        }
        ty += 26 * u;
        String sub;
        int subColor = Theme.MUTED;
        int best = p.bestOverall(l.id);
        int medal = p.medal(l.id);
        RunSave run = app.saves.resumable(l.id);
        if (!open) {
            sub = l.number == 1 ? "Welt gesperrt" : "Einen Stern in Level " + (l.number - 1) + " holen";
        } else if (run != null) {
            sub = "Gespeichert  ·  Welle " + (run.snapshot.waveIndex + 1) + "  ·  " + styleText(l);
            subColor = Theme.GREEN;
        } else if (best > 0) {
            sub = "Rekord Welle " + best + (medal > 0 ? "  ·  " + Medals.NAMES[medal - 1].charAt(0) + Medals.NAMES[medal - 1].substring(1).toLowerCase() : "")
                    + "  ·  " + styleText(l);
        } else {
            sub = styleText(l) + (l.blurb.isEmpty() ? "" : "  ·  " + l.blurb);
        }
        Draw.cut(g, sub, cx, ty, Kit.T_CAPTION * u, cw, subColor, Gfx.ALIGN_LEFT, false);
    }

    private static String styleText(LevelDef l) {
        if (l.style == null) {
            return l.waveCount + " Wellen";
        }
        return l.style.label + (l.altStyle != null ? " / " + l.altStyle.label : "") + (l.bossLevel ? "  ·  Boss" : "");
    }

    private double custom(Gfx g, double x, double y, double w, double u) {
        double eh = Kit.BUTTON * u;
        hit(x, y, w, eh, "new");
        Kit.primary(g, x, y, w, eh, "Neues Level zeichnen", Icon.PLUS, Theme.ACCENT, lift("new"), u);
        y += eh + Kit.SECTION_GAP * u;
        if (levels.isEmpty()) {
            g.text("Noch keine eigenen Level.", x + w / 2, y + 20 * u, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            return y + 40 * u;
        }
        return levelList(g, x, y, w, u, Theme.ACCENT);
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
