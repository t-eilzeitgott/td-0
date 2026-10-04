package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.NeonText;
import neontd.gfx.Theme;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.math.Mathx;
import neontd.physics.Path;
import neontd.progress.Medals;
import neontd.render.LevelPreview;
import neontd.save.RunSave;
import neontd.ui.Button;
import neontd.ui.Easing;
import neontd.ui.Smooth;
import neontd.ui.Ui;
import neontd.ui.Viewport;

/**
 * Levelauswahl in zwei Stufen: die Übersicht der 10 Kapitel (plus "Eigene Level") und je Kapitel die 15 Karten mit
 * Pfad-Vorschau und Medaillen.
 */
public final class LevelSelectScene extends Scene {
    /** Eine Karte im scrollbaren Raster (Koordinaten im Inhaltsraum, ohne Scroll-Versatz). */
    private static final class Card {
        LevelDef level;
        Path[] paths;
        boolean isNew;
        /** Kapitelkarte der Übersicht (1–10, {@link Levels#CUSTOM} für die eigenen Level), sonst 0. */
        int chapter;
        double x;
        double y;
        double w;
        double h;
        final Smooth appear = new Smooth(0, 7);
        final Smooth hover = new Smooth(0, 14);
        final Smooth press = new Smooth(0, 24);
        double delay;
    }

    private final Ui ui = new Ui();
    private final Button back;
    /** 0 = Kapitelübersicht, 1–10 = ein Kapitel, {@link Levels#CUSTOM} = eigene Level. */
    private final int chapter;
    private final List<Card> cards = new ArrayList<>();
    private Card pressedCard;
    private Card hoverCard;
    private int pressedIcon; // 0 keiner, 1 bearbeiten, 2 löschen
    private double downX;
    private double downY;
    private double lastY;
    private double lastT;
    private boolean scrolling;
    private double scroll;
    private double scrollMax;
    private double velocity;
    private double time;

    private double gridTop;
    private double gridBottom;
    private double cardW;
    private double cardH;
    private double titleY;

    private Card confirmDelete;
    private final Ui confirmUi = new Ui();
    private final Smooth confirmIn = new Smooth(0, 12);
    /** Startdialog: Fortsetzen / Neues Spiel / Endlos – erscheint nur, wenn es etwas zu wählen gibt. */
    private Card startCard;
    private final Ui startUi = new Ui();
    private final Smooth startIn = new Smooth(0, 12);
    private String toast;
    private double toastTime;

    public LevelSelectScene(App app) {
        this(app, 0);
    }

    public LevelSelectScene(App app, int chapter) {
        super(app);
        this.chapter = chapter;
        back = ui.add(new Button("", Icon.BACK, Theme.CYAN, this::goBack));
        buildCards();
    }

    private void goBack() {
        app.goTo(chapter == 0 ? new MenuScene(app) : new LevelSelectScene(app, 0));
    }

    private void buildCards() {
        cards.clear();
        double delay = 0.1;
        double step = chapter == 0 ? 0.05 : 0.035;
        if (chapter == 0) {
            for (int c = 1; c <= Levels.CHAPTERS; c++) {
                Card k = card(Levels.chapter(c).get(0), false, delay);
                k.chapter = c;
                cards.add(k);
                delay += step;
            }
            Card own = card(null, false, delay);
            own.chapter = Levels.CUSTOM;
            cards.add(own);
            return;
        }
        if (chapter != Levels.CUSTOM) {
            for (LevelDef l : Levels.chapter(chapter)) {
                cards.add(card(l, false, delay));
                delay += step;
            }
            return;
        }
        for (LevelDef l : app.levels.loadAll()) {
            cards.add(card(l, false, delay));
            delay += step;
        }
        Card plus = new Card();
        plus.isNew = true;
        plus.delay = delay;
        plus.appear.target = 1;
        cards.add(plus);
    }

    private Card card(LevelDef l, boolean isNew, double delay) {
        Card c = new Card();
        c.level = l;
        c.isNew = isNew;
        c.delay = delay;
        c.appear.target = 1;
        if (l == null) {
            c.paths = new Path[0];
            return c;
        }
        try {
            c.paths = l.buildPaths();
        } catch (RuntimeException e) {
            c.paths = new Path[0]; // beschädigtes Level: Vorschau leer lassen
        }
        return c;
    }

    // ------------------------------------------------------------------------------------------ Layout

    @Override
    public void layout() {
        Viewport vp = app.vp;
        double u = vp.u;
        double m = 14 * u;
        double bs = 46 * u;
        back.bounds(vp.insetL + m, vp.insetT + m, bs, bs);
        titleY = vp.insetT + m + bs / 2;
        gridTop = vp.insetT + m + bs + m;
        gridBottom = vp.h - vp.insetB - m * 0.5;
        double gridW = vp.safeW() - 2 * m;
        double gap = 14 * u;
        double minW = 250 * u;
        int cols = Math.max(1, (int) Math.floor((gridW + gap) / (minW + gap)));
        cols = Math.min(cols, 4);
        cardW = (gridW - gap * (cols - 1)) / cols;
        cardW = Math.min(cardW, 420 * u);
        cardH = cardW * (chapter == 0 ? 0.7 : 0.78);
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            int row = i / cols;
            int inRow = Math.min(cols, cards.size() - row * cols);
            double rowW = inRow * cardW + (inRow - 1) * gap;
            double x0 = vp.insetL + m + (gridW - rowW) / 2; // jede Zeile mittig, auch die letzte, unvollständige
            c.x = x0 + (i % cols) * (cardW + gap);
            c.y = row * (cardH + gap);
            c.w = cardW;
            c.h = cardH;
        }
        int rows = (cards.size() + cols - 1) / cols;
        double content = rows * cardH + (rows - 1) * gap;
        scrollMax = Math.max(0, content - (gridBottom - gridTop));
        scroll = Mathx.clamp(scroll, 0, scrollMax);
        layoutConfirm();
        layoutStart();
    }

    private void layoutStart() {
        Viewport vp = app.vp;
        double u = vp.u;
        double bw = Math.min(vp.safeW() * 0.84, 340 * u);
        double bh = 52 * u;
        double y0 = vp.h / 2 - 44 * u;
        for (int i = 0; i < startUi.buttons.size(); i++) {
            startUi.buttons.get(i).bounds(vp.w / 2 - bw / 2, y0 + i * (bh + 10 * u), bw, bh);
        }
    }

    private void layoutConfirm() {
        Viewport vp = app.vp;
        double u = vp.u;
        double bw = Math.min(vp.safeW() * 0.8, 320 * u);
        double bh = 54 * u;
        for (int i = 0; i < confirmUi.buttons.size(); i++) {
            confirmUi.buttons.get(i).bounds(vp.w / 2 - bw / 2, vp.h / 2 + 10 * u + i * (bh + 12 * u), bw, bh);
        }
    }

    // ------------------------------------------------------------------------------------------ Update

    @Override
    public void update(double dt) {
        time += dt;
        ui.update(dt);
        confirmUi.update(dt);
        confirmIn.target = confirmDelete != null ? 1 : 0;
        confirmIn.update(dt);
        startUi.update(dt);
        startIn.target = startCard != null ? 1 : 0;
        startIn.update(dt);
        for (Card c : cards) {
            if (c.delay > 0) {
                c.delay -= dt;
                c.appear.value = 0;
            } else {
                c.appear.update(dt);
            }
            c.hover.target = (c == hoverCard && pressedCard == null) ? 1 : 0;
            c.hover.update(dt);
            c.press.update(dt);
        }
        if (!scrolling && pressedCard == null && Math.abs(velocity) > 5) {
            scroll = Mathx.clamp(scroll - velocity * dt, 0, scrollMax);
            velocity *= Math.exp(-4.5 * dt);
            if (scroll <= 0 || scroll >= scrollMax) {
                velocity = 0;
            }
        }
        if (toastTime > 0) {
            toastTime -= dt;
        }
    }

    // ----------------------------------------------------------------------------------------- Zeichnen

    @Override
    public void render(Gfx g) {
        Viewport vp = app.vp;
        double u = vp.u;
        g.fillRect(0, 0, vp.w, vp.h, Theme.BLACK);
        drawBackdrop(g, vp);

        // Karten (beschnitten auf den Rasterbereich)
        g.save();
        g.clipRect(0, gridTop - 6 * u, vp.w, gridBottom - gridTop + 12 * u);
        for (Card c : cards) {
            drawCard(g, c, u);
        }
        g.restore();
        drawScrollBar(g, u);

        String title = chapter == 0 ? "KAPITEL WÄHLEN" : chapter == Levels.CUSTOM ? "EIGENE LEVEL"
                : "KAPITEL " + chapter + " - " + Levels.chapterName(chapter).toUpperCase();
        double titleW = vp.safeW() - 2 * (46 * u + 28 * u);
        NeonText.draw(g, title, vp.w / 2, titleY, Math.min(34 * u, titleW / (title.length() * 0.85)), Theme.CYAN, 8, 0.4, time);
        ui.render(g);

        if (toastTime > 0 && toast != null) {
            double a = Mathx.clamp01(toastTime / 0.4);
            double w = g.textWidth(toast, 15 * u, true) + 34 * u;
            double h = 34 * u;
            g.save();
            g.alpha(a);
            g.fillRoundRect(vp.w / 2 - w / 2, vp.h - vp.insetB - h - 16 * u, w, h, h / 2, Colors.withAlpha(0x000000, 0.9));
            Neon.roundRect(g, vp.w / 2 - w / 2, vp.h - vp.insetB - h - 16 * u, w, h, h / 2, 1.6, Theme.RED, 5);
            g.text(toast, vp.w / 2, vp.h - vp.insetB - h / 2 - 16 * u, 15 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.restore();
        }
        if (confirmIn.value > 0.01 && confirmDelete != null) {
            drawConfirm(g, u);
        }
        if (startIn.value > 0.01 && startCard != null) {
            drawStart(g, u);
        }
    }

    private void drawStart(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(startIn.value));
        LevelDef l = startCard.level;
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.82));
        NeonText.draw(g, l.name.toUpperCase(), vp.w / 2, vp.h / 2 - 118 * u,
                Math.min(34 * u, vp.safeW() * 0.84 / Math.max(6, l.name.length() * 0.75)), Theme.CYAN, 10, 0.3, time);
        int best = app.progress.best(l.id, false);
        int bestEndless = app.progress.best(l.id, true);
        String rec = "Rekord: Welle " + best + (bestEndless > 0 ? "   ·   Endlos: Welle " + bestEndless : "");
        g.text(rec, vp.w / 2, vp.h / 2 - 84 * u, Math.max(11, 14 * u), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        RunSave run = app.saves.resumable(l.id);
        if (run != null) {
            String t = "Gespeichert: Welle " + (run.snapshot.waveIndex + 1) + (run.snapshot.endless ? " (Endlos)" : "");
            g.text(t, vp.w / 2, vp.h / 2 - 62 * u, Math.max(11, 14 * u), Theme.GREEN, Gfx.ALIGN_CENTER, true);
        }
        startUi.render(g);
        g.restore();
    }

    private void drawBackdrop(Gfx g, Viewport vp) {
        // sehr dezentes Raster als Hintergrund
        g.beginPath();
        double step = 56 * vp.u;
        double off = (time * 6) % step;
        for (double x = -off; x < vp.w; x += step) {
            g.moveTo(x, 0);
            g.lineTo(x, vp.h);
        }
        for (double y = -off; y < vp.h; y += step) {
            g.moveTo(0, y);
            g.lineTo(vp.w, y);
        }
        g.stroke(1, Colors.withAlpha(Theme.GRID, 0.55));
    }

    private void drawScrollBar(Gfx g, double u) {
        if (scrollMax <= 0) {
            return;
        }
        double trackH = gridBottom - gridTop;
        double barH = Math.max(30 * u, trackH * trackH / (trackH + scrollMax));
        double y = gridTop + (trackH - barH) * (scroll / scrollMax);
        g.fillRoundRect(app.vp.w - app.vp.insetR - 6 * u, y, 3 * u, barH, 2 * u, Colors.withAlpha(Theme.CYAN, 0.45));
    }

    private void drawCard(Gfx g, Card c, double u) {
        double ap = Easing.outCubic(Mathx.clamp01(c.appear.value));
        if (ap <= 0.01) {
            return;
        }
        double x = c.x;
        double y = gridTop + c.y - scroll + (1 - ap) * 30 * u;
        if (y > gridBottom || y + c.h < gridTop - 10 * u) {
            return;
        }
        double w = c.w;
        double h = c.h;
        double hv = c.hover.value;
        double pr = c.press.value;
        int accent = c.isNew ? Theme.GREEN : c.chapter == Levels.CUSTOM ? Theme.MAGENTA
                : (c.chapter > 0 || c.level.builtin) ? chapterColor(c.chapter > 0 ? c.chapter : c.level.chapter) : Theme.MAGENTA;
        g.save();
        g.alpha(ap);
        g.translate(x + w / 2, y + h / 2);
        double sc = 1 + 0.025 * hv - 0.03 * pr;
        g.scale(sc, sc);
        g.translate(-w / 2, -h / 2);
        double r = 18 * u;
        g.fillRoundRect(0, 0, w, h, r, Colors.withAlpha(Theme.PANEL, 0.95));
        g.fillRoundRect(0, 0, w, h, r, Colors.withAlpha(accent, 0.04 + 0.08 * hv + 0.12 * pr));
        Neon.roundRect(g, 0, 0, w, h, r, 2, accent, 4 + 8 * hv);

        if (c.chapter > 0) {
            drawChapterCard(g, c, w, h, u, accent, hv);
        } else if (c.isNew) {
            double cx = w / 2;
            double cy = h * 0.42;
            double pulse = 0.5 + 0.5 * Math.sin(time * 2.2);
            Neon.circle(g, cx, cy, h * 0.22, 2.4, accent, 8 + 4 * pulse);
            Icons.draw(g, Icon.PLUS, cx, cy, h * 0.12, accent, 5);
            g.text("NEUES LEVEL", w / 2, h * 0.80, Math.max(13, 17 * u), Theme.TEXT, Gfx.ALIGN_CENTER, true);
            g.text("Zeichne deinen eigenen Pfad", w / 2, h * 0.80 + 20 * u, Math.max(10, 12 * u), Theme.TEXT_DIM, Gfx.ALIGN_CENTER, false);
        } else {
            double pad = 14 * u;
            LevelPreview.draw(g, c.paths, c.level.width, c.level.height, pad, pad, w - 2 * pad, h * 0.60,
                    accent, hv > 0.3 ? time : 0);
            double ty = h * 0.60 + pad + 14 * u;
            g.text(c.level.name, pad, ty, Math.max(14, 18 * u), Theme.TEXT, Gfx.ALIGN_LEFT, true);
            int best = app.progress.best(c.level.id, false);
            int bestEndless = app.progress.best(c.level.id, true);
            int overall = app.progress.bestOverall(c.level.id);
            int tier = app.progress.medal(c.level.id);
            String info;
            if (c.level.builtin) {
                info = "Nr. " + c.level.number + "/" + Levels.PER_CHAPTER + (overall > 0 ? "  ·  Rekord " + overall : "")
                        + (tier < 4 ? "  ·  " + Medals.NAMES[tier] + " ab " + Medals.WAVES[tier] : "");
            } else {
                info = c.level.waveCount + " Wellen  ·  eigenes Level" + (best > 0 ? "  ·  Rekord " + best : "")
                        + (bestEndless > 0 ? "  ·  ∞ " + bestEndless : "");
            }
            g.text(info, pad, ty + 22 * u, Math.max(10, 12 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            if (c.level.builtin) {
                drawMedals(g, w - pad, pad + 10 * u, 9 * u, tier);
            }
            if (!app.progress.levelUnlocked(c.level.id)) {
                // gesperrt: abgedunkelt, Schloss und Hinweis
                g.fillRoundRect(0, 0, w, h, r, Colors.withAlpha(0x000000, 0.78));
                Icons.draw(g, Icon.LOCK, w / 2, h * 0.38, Math.min(w, h) * 0.12, Theme.TEXT_DIM, 0);
                LevelDef need = Levels.prerequisite(c.level);
                String prev = need != null ? need.name : "";
                double fs = Math.max(10, 13 * u);
                String t = "Erst BRONZE in „" + prev + "“";
                double tw = g.textWidth(t, fs, true);
                double sh = Math.min(1, (w - 2 * pad) / Math.max(1, tw));
                g.text(t, w / 2, h * 0.38 + Math.min(w, h) * 0.2, fs * sh, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
            } else if (app.saves.resumable(c.level.id) != null) {
                double bs = Math.max(9, 11 * u);
                String t = "▶ GESPEICHERT";
                double bw = g.textWidth(t, bs, true) + 16 * u;
                g.fillRoundRect(pad, pad, bw, 22 * u, 11 * u, Colors.withAlpha(0x000000, 0.8));
                Neon.roundRect(g, pad, pad, bw, 22 * u, 11 * u, 1.4, Theme.GREEN, 4);
                g.text(t, pad + bw / 2, pad + 11 * u, bs, Theme.GREEN, Gfx.ALIGN_CENTER, true);
            } else if (app.progress.endlessUnlocked(c.level.id)) {
                double bs = Math.max(9, 11 * u);
                String t = "∞ ENDLOS FREI";
                double bw = g.textWidth(t, bs, true) + 16 * u;
                g.fillRoundRect(pad, pad, bw, 22 * u, 11 * u, Colors.withAlpha(0x000000, 0.8));
                Neon.roundRect(g, pad, pad, bw, 22 * u, 11 * u, 1.4, Theme.MAGENTA, 4);
                g.text(t, pad + bw / 2, pad + 11 * u, bs, Theme.MAGENTA, Gfx.ALIGN_CENTER, true);
            }
            // Start-Symbol
            double ps = 20 * u;
            Icons.draw(g, Icon.PLAY, w - pad - ps, ty + 8 * u, ps * 0.8, accent, 4 + 4 * hv);
            if (!c.level.builtin) {
                double is = 17 * u;
                drawIconButton(g, Icon.PENCIL, w - pad - is, pad + is, is, Theme.CYAN, pressedCard == c && pressedIcon == 1);
                drawIconButton(g, Icon.TRASH, w - pad - is * 4.3, pad + is, is, Theme.RED, pressedCard == c && pressedIcon == 2);
            }
        }
        g.restore();
    }

    private static final int[] CHAPTER_COLORS = {Theme.CYAN, 0x39FF88, 0xFFD23F, 0xFF7A3D, 0xFF4F9A, 0xB26BFF,
        0x4DA3FF, 0x3DF2E0, 0xFF5E5E, 0xE8FF3D};

    private static int chapterColor(int ch) {
        return ch >= 1 && ch <= CHAPTER_COLORS.length ? CHAPTER_COLORS[ch - 1] : Theme.CYAN;
    }

    /** Kapitelkarte: Pfad-Vorschau des ersten Levels, Name, Fortschrittsbalken (Bronze von 15) und Medaillenzahlen. */
    private void drawChapterCard(Gfx g, Card c, double w, double h, double u, int accent, double hv) {
        double pad = 14 * u;
        boolean custom = c.chapter == Levels.CUSTOM;
        if (custom) {
            double cx = w / 2;
            double cy = h * 0.34;
            double pulse = 0.5 + 0.5 * Math.sin(time * 2.2);
            Neon.circle(g, cx, cy, h * 0.2, 2.4, accent, 8 + 4 * pulse);
            Icons.draw(g, Icon.PENCIL, cx, cy, h * 0.11, accent, 5);
        } else {
            LevelPreview.draw(g, c.paths, c.level.width, c.level.height, pad, pad, w - 2 * pad, h * 0.50,
                    accent, hv > 0.3 ? time : 0);
        }
        double ty = h * 0.50 + pad + 14 * u;
        String name = custom ? "Eigene Level" : "Kapitel " + c.chapter + " - " + Levels.chapterName(c.chapter);
        g.text(name, pad, ty, Math.max(14, 18 * u), Theme.TEXT, Gfx.ALIGN_LEFT, true);
        if (custom) {
            g.text("Zeichne und spiele deine eigenen Pfade", pad, ty + 22 * u, Math.max(10, 12 * u), Theme.TEXT_DIM,
                    Gfx.ALIGN_LEFT, false);
            return;
        }
        int bronze = app.progress.chapterMedals(c.chapter, 1);
        int silver = app.progress.chapterMedals(c.chapter, 2);
        int gold = app.progress.chapterMedals(c.chapter, 3);
        int plat = app.progress.chapterMedals(c.chapter, 4);
        String info = bronze + "/" + Levels.PER_CHAPTER + " Bronze  ·  " + silver + " Silber  ·  " + gold + " Gold"
                + (plat > 0 ? "  ·  " + plat + " Platin" : "");
        g.text(info, pad, ty + 22 * u, Math.max(10, 12 * u), Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        double by = ty + 38 * u;
        double bw = w - 2 * pad;
        g.fillRoundRect(pad, by, bw, 6 * u, 3 * u, Colors.withAlpha(0xFFFFFF, 0.1));
        if (bronze > 0) {
            g.fillRoundRect(pad, by, bw * bronze / Levels.PER_CHAPTER, 6 * u, 3 * u, accent);
        }
        if (!app.progress.chapterUnlocked(c.chapter)) {
            double r = 18 * u;
            g.fillRoundRect(0, 0, w, h, r, Colors.withAlpha(0x000000, 0.78));
            Icons.draw(g, Icon.LOCK, w / 2, h * 0.24, Math.min(w, h) * 0.1, Theme.TEXT_DIM, 0);
            LevelDef need = Levels.prerequisite(c.level);
            double fs = Math.max(10, 13 * u);
            String t = "Erst BRONZE in „" + (need == null ? "" : need.name) + "“";
            double tw = g.textWidth(t, fs, true);
            double sh = Math.min(1, (w - 2 * pad) / Math.max(1, tw));
            g.text(t, w / 2, h * 0.24 + Math.min(w, h) * 0.17, fs * sh, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        }
    }

    /** Vier Medaillen-Scheiben (Bronze … Platin) rechtsbündig; erreichte leuchten, die übrigen sind nur umrandet. */
    private void drawMedals(Gfx g, double right, double cy, double r, int tier) {
        double gap = r * 0.5;
        for (int i = 3; i >= 0; i--) {
            double cx = right - r - (3 - i) * (2 * r + gap);
            int col = Medals.COLORS[i];
            if (i < tier) {
                g.fillCircle(cx, cy, r, Colors.withAlpha(col, 0.35));
                Neon.circle(g, cx, cy, r, 1.8, col, 5);
                Icons.draw(g, Icon.STAR, cx, cy, r * 0.55, col, 2);
            } else {
                g.strokeCircle(cx, cy, r, 1.2, Colors.withAlpha(col, 0.35));
            }
        }
    }

    private void drawIconButton(Gfx g, Icon icon, double cx, double cy, double r, int color, boolean pressed) {
        g.fillCircle(cx, cy, r * 1.35, Colors.withAlpha(0x000000, 0.75));
        Neon.circle(g, cx, cy, r * 1.35, 1.6, Colors.withAlpha(color, pressed ? 1 : 0.7), pressed ? 8 : 3);
        Icons.draw(g, icon, cx, cy, r * 0.72, color, 2);
    }

    private void drawConfirm(Gfx g, double u) {
        Viewport vp = app.vp;
        double a = Easing.outCubic(Mathx.clamp01(confirmIn.value));
        g.save();
        g.alpha(a);
        g.fillRect(0, 0, vp.w, vp.h, Colors.withAlpha(0x000000, 0.8));
        NeonText.draw(g, "LEVEL LÖSCHEN?", vp.w / 2, vp.h / 2 - 70 * u, Math.min(34 * u, vp.safeW() * 0.84 / 8.5), Theme.RED, 10, 0.3, time);
        g.text(confirmDelete.level.name, vp.w / 2, vp.h / 2 - 28 * u, 16 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        confirmUi.render(g);
        g.restore();
    }

    // ------------------------------------------------------------------------------------------ Eingabe

    private double contentY(double screenY) {
        return screenY - gridTop + scroll;
    }

    private Card cardAt(double x, double y) {
        if (y < gridTop - 6 || y > gridBottom + 6) {
            return null;
        }
        double cy = contentY(y);
        for (Card c : cards) {
            if (c.appear.value > 0.5 && x >= c.x && x <= c.x + c.w && cy >= c.y && cy <= c.y + c.h) {
                return c;
            }
        }
        return null;
    }

    private int iconAt(Card c, double x, double y) {
        if (c == null || c.isNew || c.chapter > 0 || c.level.builtin) {
            return 0;
        }
        double u = app.vp.u;
        double pad = 14 * u;
        double is = 17 * u;
        double sy = gridTop + c.y - scroll;
        double cyIcon = sy + pad + is;
        double hit = is * 1.5;
        double cxEdit = c.x + c.w - pad - is;
        double cxDel = c.x + c.w - pad - is * 4.3;
        if (Mathx.dist(x, y, cxEdit, cyIcon) <= hit) {
            return 1;
        }
        if (Mathx.dist(x, y, cxDel, cyIcon) <= hit) {
            return 2;
        }
        return 0;
    }

    @Override
    public void pointerDown(double x, double y, boolean touch) {
        if (startCard != null) {
            startUi.pointerDown(x, y);
            return;
        }
        if (confirmDelete != null) {
            confirmUi.pointerDown(x, y);
            return;
        }
        if (ui.pointerDown(x, y)) {
            return;
        }
        downX = x;
        downY = y;
        lastY = y;
        lastT = time;
        scrolling = false;
        velocity = 0;
        pressedCard = cardAt(x, y);
        pressedIcon = iconAt(pressedCard, x, y);
        if (pressedCard != null) {
            pressedCard.press.target = 1;
        }
    }

    @Override
    public void pointerMove(double x, double y, boolean pressed, boolean touch) {
        if (startCard != null) {
            startUi.pointerMove(x, y, !touch);
            return;
        }
        if (confirmDelete != null) {
            confirmUi.pointerMove(x, y, !touch);
            return;
        }
        ui.pointerMove(x, y, !touch);
        if (!pressed) {
            hoverCard = touch ? null : cardAt(x, y);
            return;
        }
        if (ui.isPressing()) {
            return;
        }
        double u = app.vp.u;
        if (!scrolling && Math.abs(y - downY) > 9 * u) {
            scrolling = true;
            if (pressedCard != null) {
                pressedCard.press.target = 0;
            }
            pressedCard = null;
        }
        if (scrolling) {
            double dy = y - lastY;
            scroll = Mathx.clamp(scroll - dy, 0, scrollMax);
            double dt = Math.max(1.0 / 240, time - lastT);
            velocity = Mathx.lerp(velocity, dy / dt, 0.4);
            lastY = y;
            lastT = time;
        }
    }

    @Override
    public void pointerUp(double x, double y, boolean touch) {
        if (startCard != null) {
            startUi.pointerUp(x, y);
            return;
        }
        if (confirmDelete != null) {
            confirmUi.pointerUp(x, y);
            return;
        }
        if (ui.pointerUp(x, y)) {
            return;
        }
        Card c = pressedCard;
        pressedCard = null;
        if (c != null) {
            c.press.target = 0;
            if (!scrolling && cardAt(x, y) == c) {
                activate(c, iconAt(c, x, y));
            }
        }
        scrolling = false;
        if (touch) {
            hoverCard = null;
        }
    }

    @Override
    public void pointerCancel() {
        ui.cancel();
        confirmUi.cancel();
        startUi.cancel();
        if (pressedCard != null) {
            pressedCard.press.target = 0;
        }
        pressedCard = null;
        scrolling = false;
    }

    @Override
    public void wheel(double dy) {
        scroll = Mathx.clamp(scroll + dy, 0, scrollMax);
    }

    private void activate(Card c, int icon) {
        if (c.chapter > 0) {
            if (c.chapter != Levels.CUSTOM && !app.progress.chapterUnlocked(c.chapter)) {
                LevelDef need = Levels.prerequisite(c.level);
                toast = "Erst Bronze (Welle 20) in „" + (need == null ? "" : need.name) + "“ erreichen";
                toastTime = 2.6;
                return;
            }
            app.goTo(new LevelSelectScene(app, c.chapter));
            return;
        }
        if (c.isNew) {
            app.goTo(new EditorScene(app, null));
            return;
        }
        if (icon == 1) {
            app.goTo(new EditorScene(app, c.level.copy()));
        } else if (icon == 2) {
            askDelete(c);
        } else {
            if (!app.progress.levelUnlocked(c.level.id)) {
                LevelDef need = Levels.prerequisite(c.level);
                toast = "Erst Bronze (Welle 20) in „" + (need == null ? "" : need.name) + "“ erreichen";
                toastTime = 2.6;
                return;
            }
            String problem = c.level.validate();
            if (problem != null) {
                toast = "Nicht spielbar: " + problem;
                toastTime = 2.6;
                return;
            }
            openStart(c);
        }
    }

    /** Startet gleich, wenn es nichts zu wählen gibt; sonst Dialog mit Fortsetzen / Neu / Endlos. */
    private void openStart(Card c) {
        RunSave run = app.saves.resumable(c.level.id);
        boolean endless = app.progress.endlessUnlocked(c.level.id);
        if (run == null && !endless) {
            app.goTo(new GameScene(app, c.level));
            return;
        }
        startCard = c;
        startUi.clear();
        if (run != null) {
            String label = "FORTSETZEN · WELLE " + (run.snapshot.waveIndex + 1);
            Button resume = new Button(label, Icon.PLAY, Theme.GREEN, () -> {
                startCard = null;
                app.goTo(GameScene.resume(app, c.level, run));
            });
            resume.neonFont = true;
            resume.fontScale = 0.8;
            startUi.add(resume).appearAfter(0.1);
        }
        Button fresh = new Button(run != null ? "NEUES SPIEL (VERWIRFT STAND)" : "NEUES SPIEL", Icon.LOOP, Theme.CYAN, () -> {
            startCard = null;
            app.saves.endRun(c.level.id);
            app.goTo(new GameScene(app, c.level));
        });
        fresh.neonFont = true;
        fresh.fontScale = 0.8;
        startUi.add(fresh).appearAfter(0.16);
        if (endless) {
            Button inf = new Button("ENDLOSMODUS", Icon.WAVES, Theme.MAGENTA, () -> {
                startCard = null;
                app.saves.endRun(c.level.id);
                app.goTo(GameScene.endless(app, c.level));
            });
            inf.neonFont = true;
            inf.fontScale = 0.8;
            startUi.add(inf).appearAfter(0.22);
        }
        Button cancel = new Button("ABBRECHEN", Icon.CROSS, Theme.TEXT_DIM, () -> startCard = null);
        cancel.neonFont = true;
        cancel.fontScale = 0.8;
        startUi.add(cancel).appearAfter(0.28);
        layoutStart();
    }

    private void askDelete(Card c) {
        confirmDelete = c;
        confirmUi.clear();
        Button yes = new Button("LÖSCHEN", Icon.TRASH, Theme.RED, () -> {
            app.levels.delete(c.level.id);
            confirmDelete = null;
            buildCards();
            layout();
        });
        Button no = new Button("ABBRECHEN", Icon.CROSS, Theme.CYAN, () -> confirmDelete = null);
        yes.neonFont = true;
        no.neonFont = true;
        confirmUi.add(yes).appearAfter(0.1);
        confirmUi.add(no).appearAfter(0.18);
        layoutConfirm();
    }

    @Override
    public boolean back() {
        if (startCard != null) {
            startCard = null;
            return true;
        }
        if (confirmDelete != null) {
            confirmDelete = null;
            return true;
        }
        if (chapter != 0) {
            app.goTo(new LevelSelectScene(app, 0));
            return true;
        }
        return false;
    }
}
