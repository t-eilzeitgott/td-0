package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Calendar;
import neontd.progress.Progress;
import neontd.progress.Quest;
import neontd.progress.Quests;
import neontd.progress.Season;
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Kit;

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
        super(app, "Missionen", Theme.GREEN);
        setTabs("Täglich", "Season");
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
        setTabLabel(TAB_DAILY, daily > 0 ? "Täglich ●" : "Täglich");
        setTabLabel(TAB_SEASON, season > 0 ? "Season ●" : "Season");
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

    private double lift(Object tag) {
        return pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
    }

    private double drawDaily(Gfx g, double x, double y0, double w, double u) {
        Progress p = app.progress;
        double y = y0;
        y += Kit.section(g, "Tagesaufgaben", "neu in " + timeLeft(), Theme.MUTED, x, y, w, u);
        Quest[] q = p.dailyQuests();
        String[] tiers = {"Leicht", "Mittel", "Schwer"};
        for (int i = 0; i < q.length; i++) {
            y += questRow(g, x, y, w, u, q[i], p.dailyProgress[i], p.dailyClaimed[i], p.dailyReady(i),
                    TIER_COLORS[i], tiers[i], "d" + i, true);
        }
        // Bonus für alle drei
        boolean allDone = p.dailyBonusClaimed;
        double bh = Kit.ROW * u;
        Icons.draw(g, allDone ? Icon.CHECK : Icon.STAR, x + 14 * u, y + bh / 2, 10 * u, allDone ? Theme.GREEN : Theme.CRYSTAL, 0);
        Kit.row(g, x, y, w, bh, 44 * u, "Alle drei abgeholt", "Bonus  +" + Quests.DAILY_BONUS_CRYSTALS + " Kristalle  ·  +"
                + Quests.DAILY_BONUS_SEASON_XP + " Season-XP", allDone ? "Erhalten" : null, Theme.GREEN, false, false, 0,
                allDone, u);
        y += bh + 28 * u;
        y += Kit.paragraph(g, "Aufgaben zählen nur in normalen Spielen, nicht im Editor-Test. Die Season-XP der Aufgaben "
                + "füllen den Season-Pass.", x, y, w, Kit.T_CAPTION * u, Theme.MUTED, u);
        return y - y0 + 24 * u;
    }

    /** Eine Aufgabe als offene Zeile: Status-Kreis, Titel, Belohnungen, Fortschritt, rechts Abholen. */
    private double questRow(Gfx g, double x, double y, double w, double u, Quest q, int progress, boolean claimed,
                            boolean ready, int color, String kicker, Object tag, boolean divider) {
        double h = 98 * u;
        double lft = lift(tag);
        if (ready) {
            lft = Math.max(lft, 0.35 + 0.35 * Math.sin(time * 4));
        }
        Kit.rowPress(g, x, y, w, h, lft, u);
        hit(x, y, w, h, tag);
        double tx = x + 44 * u;
        double cy = y + 30 * u;
        if (claimed) {
            Icons.draw(g, Icon.CHECK, x + 14 * u, cy, 9 * u, Theme.GREEN, 0);
        } else {
            g.strokeCircle(x + 14 * u, cy, 10 * u, 1.8 * u, Colors.withAlpha(color, ready ? 1 : 0.55));
            if (ready) {
                g.fillCircle(x + 14 * u, cy, 5 * u, Theme.GREEN);
            }
        }
        double right = x + w;
        double aw = 0;
        if (ready) {
            aw = Kit.pill(g, "Abholen", right, cy, Theme.GREEN, pressed(tag) ? 1 : 0, u) + 12 * u;
        } else if (claimed) {
            g.text("Erledigt", right, cy, Kit.T_BODY * u, Theme.GREEN, Gfx.ALIGN_RIGHT, true);
            aw = g.textWidth("Erledigt", Kit.T_BODY * u, true) + 12 * u;
        }
        Draw.cut(g, q.text(), tx, cy, Kit.T_ROW * u, right - aw - tx, claimed ? Theme.MUTED : Theme.TEXT, Gfx.ALIGN_LEFT, true);
        double my = cy + 24 * u;
        double f = q.target == 0 ? 1 : (double) progress / q.target;
        g.save();
        g.alpha(claimed ? 0.55 : 1);
        double xx = tx;
        g.text(kicker.toUpperCase(), xx, my, Kit.T_KICKER * 0.92 * u, color, Gfx.ALIGN_LEFT, true);
        xx += g.textWidth(kicker.toUpperCase(), Kit.T_KICKER * 0.92 * u, true) + 14 * u;
        xx += Kit.iconValue(g, xx, my, Icon.CRYSTAL, "+" + q.crystals, Theme.CRYSTAL, Theme.TEXT, Kit.T_CAPTION * u, u) + 14 * u;
        g.text("+" + q.xp + " XP", xx, my, Kit.T_CAPTION * u, Theme.MAGENTA, Gfx.ALIGN_LEFT, true);
        xx += g.textWidth("+" + q.xp + " XP", Kit.T_CAPTION * u, true) + 14 * u;
        String prog = Fmt.compact(progress) + " / " + Fmt.compact(q.target);
        double pw = g.textWidth(prog, Kit.T_CAPTION * u, true);
        String sxp = "+" + q.seasonXp + " SXP";
        if (xx + g.textWidth(sxp, Kit.T_CAPTION * u, true) < right - pw - 12 * u) {
            g.text(sxp, xx, my, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, true);
        }
        g.text(prog, right, my, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_RIGHT, true);
        Kit.meter(g, tx, my + 18 * u, w - 44 * u, claimed ? 1 : f, claimed ? Colors.withAlpha(Theme.GREEN, 0.6)
                : (ready ? Theme.GREEN : color), u);
        g.restore();
        if (divider) {
            Kit.hairline(g, tx, y + h - 1, w - 44 * u);
        }
        return h;
    }

    private double drawSeason(Gfx g, double x, double y0, double w, double u) {
        Progress p = app.progress;
        int tier = p.seasonTier();
        double y = y0;
        int daysLeft = Calendar.daysLeftInMonth(Math.max(0, p.dailyDay));
        Kit.kicker(g, "Season", x, y + 8 * u, u, Theme.CYAN);
        g.text("noch " + daysLeft + (daysLeft == 1 ? " Tag" : " Tage"), x + w, y + 8 * u, Kit.T_CAPTION * u, Theme.MUTED,
                Gfx.ALIGN_RIGHT, false);
        y += 30 * u;
        String month = Calendar.monthName(Math.max(0, p.seasonId));
        if (month.length() > 1) {
            month = month.charAt(0) + month.substring(1).toLowerCase();
        }
        String name = month + " " + Calendar.yearOf(Math.max(0, p.seasonId));
        Draw.fit(g, name, x, y + 4 * u, Kit.T_HERO * u, w, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 44 * u;
        int into = tier >= Season.TIERS ? Season.XP_PER_TIER : p.seasonXp - tier * Season.XP_PER_TIER;
        Kit.stat(g, tier + " / " + Season.TIERS, "Stufe", x, y, Theme.TEXT, u);
        Kit.stat(g, into + " / " + Season.XP_PER_TIER, "XP bis zur nächsten Stufe", x + w * 0.42, y, Theme.TEXT, u);
        y += 62 * u;
        Kit.meter(g, x, y, w, (double) into / Season.XP_PER_TIER, Theme.CYAN, u);
        y += 14 * u;
        int ready = 0;
        for (int t = 1; t <= Season.TIERS; t++) {
            ready += p.seasonReady(t) ? 1 : 0;
        }
        if (ready > 0) {
            double bh = 48 * u;
            y += 6 * u;
            Kit.primary(g, x, y, w, bh, "Alle abholen (" + ready + ")", Icon.CRYSTAL, Theme.GREEN, lift("claimAll"), u);
            hit(x, y, w, bh, "claimAll");
            y += bh;
        }
        y += Kit.SECTION_GAP * u;

        y += Kit.section(g, "Monatsaufgaben", null, 0, x, y, w, u);
        Quest[] q = p.monthlyQuests();
        for (int i = 0; i < q.length; i++) {
            y += questRow(g, x, y, w, u, q[i], p.monthlyProgress[i], p.monthlyClaimed[i], p.monthlyReady(i),
                    Theme.CYAN, "Monat", "m" + i, i < q.length - 1);
        }
        y += Kit.SECTION_GAP * u;
        y += Kit.section(g, "Belohnungen", "gesamt " + Fmt.compact(Season.totalCrystals()) + " ◆", Theme.MUTED, x, y, w, u);
        int cols = w > 520 * u ? 6 : 5;
        double cw = w / cols;
        double ch = 78 * u;
        for (int t = 1; t <= Season.TIERS; t++) {
            int i = t - 1;
            double tx = x + (i % cols) * cw;
            double ty = y + (i / cols) * ch;
            boolean claimed = p.seasonClaimed[t];
            boolean rdy = p.seasonReady(t);
            boolean reached = t <= tier;
            String ms = Season.milestone(t);
            String tag = "t" + t;
            double l = rdy ? 0.45 + 0.45 * Math.sin(time * 4) : lift(tag);
            if (l > 0) {
                g.fillRoundRect(tx + 3 * u, ty + 2 * u, cw - 6 * u, ch - 8 * u, 14 * u,
                        Colors.withAlpha(rdy ? Theme.GREEN : 0xFFFFFF, rdy ? 0.12 * l + 0.06 : 0.05 * l));
            }
            hit(tx, ty, cw, ch, tag);
            double cx = tx + cw / 2;
            g.save();
            g.alpha(claimed ? 0.45 : (reached ? 1 : 0.38));
            int nc = rdy ? Theme.GREEN : (ms != null ? Theme.YELLOW : Theme.MUTED);
            g.text(Integer.toString(t), cx, ty + 16 * u, Kit.T_CAPTION * u, nc, Gfx.ALIGN_CENTER, true);
            String val = Integer.toString(Season.crystals(t));
            double vw = g.textWidth(val, Kit.T_ROW * u, true) + 6 * u + Kit.T_ROW * u * 0.84;
            Kit.iconValue(g, cx - vw / 2, ty + 39 * u, Icon.CRYSTAL, val, Theme.CRYSTAL, Theme.TEXT, Kit.T_ROW * u, u);
            if (ms != null) {
                Draw.fit(g, ms, cx, ty + 60 * u, 10 * u, cw - 8 * u, Theme.YELLOW, Gfx.ALIGN_CENTER, true);
            }
            g.restore();
            if (claimed) {
                Icons.draw(g, Icon.CHECK, cx + 16 * u, ty + 16 * u, 5 * u, Theme.GREEN, 0);
            } else if (!reached) {
                Icons.draw(g, Icon.LOCK, cx + 16 * u, ty + 16 * u, 4.5 * u, Colors.withAlpha(Theme.MUTED, 0.6), 0);
            }
        }
        int rows = (Season.TIERS + cols - 1) / cols;
        y += rows * ch;
        return y - y0 + 24 * u;
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
