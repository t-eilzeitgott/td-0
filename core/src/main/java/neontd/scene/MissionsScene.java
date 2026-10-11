package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.progress.Calendar;
import neontd.progress.Progress;
import neontd.progress.Quest;
import neontd.progress.Quests;
import neontd.progress.Season;
import neontd.ui.Fmt;

/**
 * Missionen: drei Tagesaufgaben (leicht, mittel, schwer) und der Season-Pass des Monats mit sechs Monatsaufgaben und
 * 30 Belohnungsstufen. Fertige Aufgaben und Stufen werden mit einem Tipp abgeholt.
 */
public final class MissionsScene extends PanelScene {
    private static final int TAB_DAILY = 0;
    private static final int TAB_SEASON = 1;
    private static final int[] TIER_COLORS = {Theme.GREEN, Theme.YELLOW, Theme.RED};

    private String flash;
    private double flashTime;

    public MissionsScene(App app) {
        this(app, TAB_DAILY);
    }

    public MissionsScene(App app, int tab) {
        super(app, "MISSIONEN", Theme.GREEN);
        setTabs("TÄGLICH", "SEASON");
        selectTab(tab);
        app.checkPeriods();
    }

    @Override
    protected void tick(double dt) {
        if (flashTime > 0) {
            flashTime -= dt;
        }
        Progress p = app.progress;
        int daily = 0;
        for (int i = 0; i < Quests.DAILY; i++) {
            daily += p.dailyReady(i) ? 1 : 0;
        }
        int season = p.claimable() - daily;
        setTabLabel(TAB_DAILY, daily > 0 ? "TÄGLICH  ●" : "TÄGLICH");
        setTabLabel(TAB_SEASON, season > 0 ? "SEASON  ●" : "SEASON");
    }

    // ------------------------------------------------------------------------------------- Zeichnen

    @Override
    protected double drawContent(Gfx g, double x, double y, double w, double u) {
        return tab == TAB_DAILY ? drawDaily(g, x, y, w, u) : drawSeason(g, x, y, w, u);
    }

    private String timeLeft() {
        double now = app.platform.nowMillis() + app.platform.localOffsetMillis();
        double left = Calendar.DAY_MS - (now % Calendar.DAY_MS + Calendar.DAY_MS) % Calendar.DAY_MS;
        int mins = (int) (left / 60000);
        return (mins / 60) + " Std " + (mins % 60) + " Min";
    }

    private double drawDaily(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        g.text("Neue Aufgaben in " + timeLeft(), x + w, y + 10 * u, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        g.text("TAGESAUFGABEN", x, y + 10 * u, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        double cy = y + 26 * u;
        Quest[] q = p.dailyQuests();
        double ch = 92 * u;
        for (int i = 0; i < q.length; i++) {
            questCard(g, x, cy, w, ch, u, q[i], p.dailyProgress[i], p.dailyClaimed[i], p.dailyReady(i),
                    TIER_COLORS[i], "d" + i);
            cy += ch + 10 * u;
        }
        // Bonus für alle drei
        boolean allDone = p.dailyBonusClaimed;
        double bh = 56 * u;
        card(g, x, cy, w, bh, Theme.CRYSTAL, 0, !allDone && false);
        Icons.draw(g, Icon.STAR, x + 28 * u, cy + bh / 2, 12 * u, allDone ? Theme.GREEN : Theme.CRYSTAL, 3);
        g.text("ALLE DREI ABGEHOLT", x + 52 * u, cy + bh * 0.36, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Bonus: +" + Quests.DAILY_BONUS_CRYSTALS + " Kristalle  ·  +" + Quests.DAILY_BONUS_SEASON_XP
                + " Season-XP", x + 52 * u, cy + bh * 0.68, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        if (allDone) {
            Icons.draw(g, Icon.CHECK, x + w - 28 * u, cy + bh / 2, 11 * u, Theme.GREEN, 3);
        }
        cy += bh + 14 * u;
        double lh = 16 * u;
        String[] tips = {"Aufgaben zählen nur in normalen Spielen (nicht im Editor-Test).",
            "Die Season-XP der Aufgaben füllen den Season-Pass."};
        for (String t : tips) {
            fitText(g, t, x, cy + lh / 2, 11.5 * u, w, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            cy += lh;
        }
        return cy - y + 8 * u;
    }

    private void questCard(Gfx g, double x, double y, double w, double h, double u, Quest q, int progress,
                           boolean claimed, boolean ready, int color, Object tag) {
        double hv = Math.max(hovered(tag) ? 1 : 0, pressed(tag) ? 1 : 0);
        if (ready) {
            hv = Math.max(hv, 0.5 + 0.5 * Math.sin(time * 5));
        }
        card(g, x, y, w, h, ready ? Theme.GREEN : color, hv, claimed);
        g.save();
        g.alpha(claimed ? 0.5 : 1);
        double pad = 14 * u;
        double rightW = Math.min(120 * u, w * 0.3);
        double tw = w - pad * 2 - rightW;
        fitText(g, q.text(), x + pad, y + h * 0.26, 16 * u, tw, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        double f = q.target == 0 ? 1 : (double) progress / q.target;
        bar(g, x + pad, y + h * 0.46, tw, 8 * u, f, color);
        g.text(Fmt.compact(progress) + " / " + Fmt.compact(q.target), x + pad, y + h * 0.7, 12 * u, Theme.TEXT_DIM,
                Gfx.ALIGN_LEFT, true);
        double rx = x + pad + tw * 0.38;
        Icons.draw(g, Icon.CRYSTAL, rx, y + h * 0.7, 7 * u, Theme.CRYSTAL, 2);
        g.text("+" + q.crystals, rx + 11 * u, y + h * 0.7, 12 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("+" + q.xp + " XP", rx + 11 * u + 46 * u, y + h * 0.7, 12 * u, Theme.MAGENTA, Gfx.ALIGN_LEFT, true);
        g.restore();
        // Abholen
        double bx = x + w - pad - rightW;
        double by = y + h * 0.22;
        double bw = rightW;
        double bh = h * 0.56;
        if (claimed) {
            Icons.draw(g, Icon.CHECK, bx + bw / 2, y + h / 2, 12 * u, Theme.GREEN, 3);
        } else if (ready) {
            g.fillRoundRect(bx, by, bw, bh, bh * 0.3, Colors.withAlpha(Theme.GREEN, 0.22));
            Neon.roundRect(g, bx, by, bw, bh, bh * 0.3, 2, Theme.GREEN, 6);
            g.text("ABHOLEN", bx + bw / 2, by + bh / 2, 14 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        } else {
            g.text("+" + q.seasonXp + " SXP", bx + bw / 2, y + h / 2, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_CENTER, true);
        }
        hit(x, y, w, h, tag);
    }

    private double drawSeason(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int tier = p.seasonTier();
        // Kopf: Name, Stufe, XP-Balken, Resttage, "Alle abholen"
        double hh = 96 * u;
        card(g, x, y, w, hh, Theme.CYAN, 0, false);
        String name = "SEASON  " + Calendar.monthName(Math.max(0, p.seasonId)) + " " + Calendar.yearOf(Math.max(0, p.seasonId));
        fitText(g, name, x + 16 * u, y + hh * 0.24, 17 * u, w * 0.6, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int daysLeft = Calendar.daysLeftInMonth(Math.max(0, p.dailyDay));
        g.text("noch " + daysLeft + (daysLeft == 1 ? " Tag" : " Tage"), x + w - 16 * u, y + hh * 0.24, 12 * u,
                Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, true);
        int into = tier >= Season.TIERS ? Season.XP_PER_TIER : p.seasonXp - tier * Season.XP_PER_TIER;
        bar(g, x + 16 * u, y + hh * 0.46, w - 32 * u, 10 * u, (double) into / Season.XP_PER_TIER, Theme.CYAN);
        g.text("STUFE " + tier + " / " + Season.TIERS + "   ·   " + into + " / " + Season.XP_PER_TIER + " XP",
                x + 16 * u, y + hh * 0.72, 12 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        int ready = 0;
        for (int t = 1; t <= Season.TIERS; t++) {
            ready += p.seasonReady(t) ? 1 : 0;
        }
        if (ready > 0) {
            double bw = 130 * u;
            double bh = 30 * u;
            double bx = x + w - 16 * u - bw;
            double by = y + hh * 0.62;
            g.fillRoundRect(bx, by, bw, bh, bh * 0.3, Colors.withAlpha(Theme.GREEN, 0.22));
            Neon.roundRect(g, bx, by, bw, bh, bh * 0.3, 2, Theme.GREEN, 5 + 3 * Math.sin(time * 5));
            g.text("ALLE ABHOLEN (" + ready + ")", bx + bw / 2, by + bh / 2, 12 * u, Theme.TEXT, Gfx.ALIGN_CENTER, true);
            hit(bx, by, bw, bh, "claimAll");
        }
        double cy = y + hh + 14 * u;

        g.text("MONATSAUFGABEN", x, cy + 4 * u, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        cy += 20 * u;
        Quest[] q = p.monthlyQuests();
        double ch = 92 * u;
        for (int i = 0; i < q.length; i++) {
            questCard(g, x, cy, w, ch, u, q[i], p.monthlyProgress[i], p.monthlyClaimed[i], p.monthlyReady(i),
                    Theme.CYAN, "m" + i);
            cy += ch + 10 * u;
        }
        cy += 6 * u;
        g.text("BELOHNUNGEN", x, cy + 4 * u, 14 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text("Alle Stufen zusammen: " + Fmt.compact(Season.totalCrystals()) + " Kristalle", x + w, cy + 4 * u,
                12 * u, Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        cy += 20 * u;
        double gap = 8 * u;
        int cols = Math.max(3, Math.min(6, (int) Math.floor((w + gap) / (88 * u + gap))));
        double tw = (w - gap * (cols - 1)) / cols;
        double th = 66 * u;
        for (int t = 1; t <= Season.TIERS; t++) {
            int i = t - 1;
            double tx = x + (i % cols) * (tw + gap);
            double ty = cy + (i / cols) * (th + gap);
            boolean claimed = p.seasonClaimed[t];
            boolean rdy = p.seasonReady(t);
            boolean reached = t <= tier;
            String ms = Season.milestone(t);
            int col = ms != null ? Theme.YELLOW : (rdy ? Theme.GREEN : Theme.CYAN);
            double hv = rdy ? 0.5 + 0.5 * Math.sin(time * 5) : (pressed("t" + t) || hovered("t" + t) ? 1 : 0);
            card(g, tx, ty, tw, th, col, hv, !reached);
            g.save();
            g.alpha(claimed ? 0.5 : (reached ? 1 : 0.6));
            g.text(Integer.toString(t), tx + 8 * u, ty + 13 * u, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, true);
            Icons.draw(g, Icon.CRYSTAL, tx + tw / 2 - 12 * u, ty + th * 0.52, 8 * u, Theme.CRYSTAL, 2);
            g.text(Integer.toString(Season.crystals(t)), tx + tw / 2 - 1 * u, ty + th * 0.52, 14 * u, Theme.TEXT,
                    Gfx.ALIGN_LEFT, true);
            if (ms != null) {
                fitText(g, ms, tx + tw / 2, ty + th * 0.84, 9 * u, tw - 6 * u, Theme.YELLOW, Gfx.ALIGN_CENTER, true);
            }
            g.restore();
            if (claimed) {
                Icons.draw(g, Icon.CHECK, tx + tw - 12 * u, ty + 13 * u, 7 * u, Theme.GREEN, 2);
            } else if (!reached) {
                Icons.draw(g, Icon.LOCK, tx + tw - 12 * u, ty + 13 * u, 6 * u, Theme.TEXT_DIM, 0);
            }
            hit(tx, ty, tw, th, "t" + t);
        }
        int rows = (Season.TIERS + cols - 1) / cols;
        return cy + rows * (th + gap) - y + 8 * u;
    }

    // -------------------------------------------------------------------------------------- Eingabe

    @Override
    protected void onTap(Object tag) {
        Progress p = app.progress;
        String s = (String) tag;
        int got = 0;
        if (s.equals("claimAll")) {
            for (int t = 1; t <= Season.TIERS; t++) {
                got += p.claimSeason(t);
            }
        } else if (s.startsWith("d")) {
            got = p.claimDaily(s.charAt(1) - '0');
        } else if (s.startsWith("m")) {
            got = p.claimMonthly(s.charAt(1) - '0');
        } else if (s.startsWith("t")) {
            got = p.claimSeason(Integer.parseInt(s.substring(1)));
        }
        if (got > 0) {
            List<String> ach = p.checkAchievements();
            app.commitProgress();
            toast("+" + got + " Kristalle" + (ach.isEmpty() ? "" : "  ·  " + ach.get(0)), Theme.CRYSTAL);
        }
    }
}
