package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Achievements;
import neontd.progress.Progress;
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Kit;

/** Errungenschaften: je Familie die nächste Stufe mit Fortschritt, Belohnung und den schon erreichten Stufen. */
public final class AchievementsScene extends PanelScene {
    public AchievementsScene(App app) {
        super(app, "Erfolge", Theme.YELLOW);
        // Beim Öffnen prüfen, damit auch Stände aus früheren Versionen ihre Stufen sofort bekommen.
        if (!app.progress.checkAchievements().isEmpty()) {
            app.commitProgress();
        }
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        Progress p = app.progress;
        int done = 0;
        for (Achievements.Family f : Achievements.FAMILIES) {
            done += p.achievementTiers(f);
        }
        double y = y0;
        Kit.stat(g, done + " / " + Achievements.totalTiers(), "Stufen erreicht", x, y, Theme.TEXT, u);
        y += 62 * u;
        Kit.meter(g, x, y, w, (double) done / Achievements.totalTiers(), Theme.YELLOW, u);
        y += Kit.SECTION_GAP * u;
        int cols = w > 600 * u ? 2 : 1;
        double colGap = 28 * u;
        double cw = (w - colGap * (cols - 1)) / cols;
        double ch = 104 * u;
        for (int i = 0; i < Achievements.FAMILIES.length; i++) {
            Achievements.Family f = Achievements.FAMILIES[i];
            double cx = x + (i % cols) * (cw + colGap);
            double cy = y + (i / cols) * ch;
            int got = p.achievementTiers(f);
            boolean complete = got >= f.tiers();
            // Stufen als Punkte rechts oben
            double sr = 5 * u;
            double ty = cy + 28 * u;
            for (int t = 0; t < f.tiers(); t++) {
                double sx = cx + cw - sr - (f.tiers() - 1 - t) * (sr * 2 + 5 * u);
                if (t < got) {
                    Icons.draw(g, Icon.STAR, sx, ty, sr * 1.25, f.color, 0);
                } else {
                    g.strokeCircle(sx, ty, sr * 0.8, 1.3 * u, Colors.withAlpha(0xFFFFFF, 0.22));
                }
            }
            double starsW = f.tiers() * (sr * 2 + 5 * u);
            Kit.dot(g, cx + 5 * u, ty, 4.5 * u, f.color);
            Draw.cut(g, titleCase(f.name), cx + 22 * u, ty, Kit.T_ROW * u, cw - starsW - 30 * u, complete ? Theme.GREEN : Theme.TEXT,
                    Gfx.ALIGN_LEFT, true);
            double my = ty + 26 * u;
            if (complete) {
                g.text("Alle Stufen erreicht", cx + 22 * u, my, Kit.T_CAPTION * u, Theme.GREEN, Gfx.ALIGN_LEFT, false);
                Kit.meter(g, cx + 22 * u, my + 20 * u, cw - 22 * u, 1, Colors.withAlpha(Theme.GREEN, 0.6), u);
            } else {
                int t = got;
                double v = Math.min(p.achievementValue(f.kind), f.targets[t]);
                double rw = Kit.iconValueRight(g, cx + cw, my, Icon.CRYSTAL, "+" + f.rewards[t], Theme.CRYSTAL, Theme.TEXT,
                        Kit.T_CAPTION * u, u);
                Draw.cut(g, f.describe(t), cx + 22 * u, my, Kit.T_CAPTION * u, cw - 22 * u - rw - 12 * u, Theme.MUTED,
                        Gfx.ALIGN_LEFT, false);
                Kit.meter(g, cx + 22 * u, my + 20 * u, cw - 22 * u, v / f.targets[t], f.color, u);
                g.text(Fmt.compact(v) + " / " + Fmt.compact(f.targets[t]), cx + cw, my + 38 * u, 11.5 * u, Theme.MUTED,
                        Gfx.ALIGN_RIGHT, true);
            }
            boolean lastRow = (i / cols) == (Achievements.FAMILIES.length - 1) / cols;
            if (!lastRow) {
                Kit.hairline(g, cx + 22 * u, cy + ch - 1, cw - 22 * u);
            }
        }
        int rows = (Achievements.FAMILIES.length + cols - 1) / cols;
        return y + rows * ch - y0 + 16 * u;
    }

    /** „ABSCHUSS-SERIE“ → „Abschuss-Serie“. */
    private static String titleCase(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == ' ' || c == '-';
        }
        return b.toString();
    }
}
