package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Achievements;
import neontd.progress.Progress;
import neontd.ui.Fmt;

/** Errungenschaften: je Familie die nächste Stufe mit Fortschritt, Belohnung und den schon erreichten Stufen. */
public final class AchievementsScene extends PanelScene {
    public AchievementsScene(App app) {
        super(app, "ERRUNGENSCHAFTEN", Theme.YELLOW);
        // Beim Öffnen prüfen, damit auch Stände aus früheren Versionen ihre Stufen sofort bekommen.
        if (!app.progress.checkAchievements().isEmpty()) {
            app.commitProgress();
        }
    }

    @Override
    protected double drawContent(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        int done = 0;
        for (Achievements.Family f : Achievements.FAMILIES) {
            done += p.achievementTiers(f);
        }
        g.text(done + " / " + Achievements.totalTiers() + " Stufen erreicht", x, y + 10 * u, 14 * u, Theme.TEXT,
                Gfx.ALIGN_LEFT, true);
        bar(g, x + w * 0.5, y + 6 * u, w * 0.5, 8 * u, (double) done / Achievements.totalTiers(), Theme.YELLOW);
        double gap = 12 * u;
        int cols = Math.max(1, Math.min(2, (int) Math.floor((w + gap) / (420 * u + gap))));
        double cw = (w - gap * (cols - 1)) / cols;
        double ch = 86 * u;
        double top = y + 28 * u;
        for (int i = 0; i < Achievements.FAMILIES.length; i++) {
            Achievements.Family f = Achievements.FAMILIES[i];
            double cx = x + (i % cols) * (cw + gap);
            double cy = top + (i / cols) * (ch + gap);
            int got = p.achievementTiers(f);
            boolean complete = got >= f.tiers();
            card(g, cx, cy, cw, ch, f.color, 0, false);
            double pad = 14 * u;
            fitText(g, f.name, cx + pad, cy + ch * 0.2, 15 * u, cw * 0.6, complete ? Theme.GREEN : Theme.TEXT,
                    Gfx.ALIGN_LEFT, true);
            // Stufensterne rechts oben
            double sr = 6 * u;
            for (int t = 0; t < f.tiers(); t++) {
                double sx = cx + cw - pad - sr - (f.tiers() - 1 - t) * (sr * 2 + 3 * u);
                if (t < got) {
                    Icons.draw(g, Icon.STAR, sx, cy + ch * 0.2, sr, f.color, 2);
                } else {
                    g.strokeCircle(sx, cy + ch * 0.2, sr * 0.8, 1.2, Colors.withAlpha(f.color, 0.4));
                }
            }
            if (complete) {
                g.text("ALLE STUFEN ERREICHT", cx + pad, cy + ch * 0.5, 12 * u, Theme.GREEN, Gfx.ALIGN_LEFT, true);
                bar(g, cx + pad, cy + ch * 0.68, cw - 2 * pad, 8 * u, 1, Theme.GREEN);
            } else {
                int t = got;
                double v = Math.min(p.achievementValue(f.kind), f.targets[t]);
                fitText(g, f.describe(t), cx + pad, cy + ch * 0.45, 12.5 * u, cw - 2 * pad - 70 * u, Theme.TEXT_DIM,
                        Gfx.ALIGN_LEFT, false);
                Icons.draw(g, Icon.CRYSTAL, cx + cw - pad - 40 * u, cy + ch * 0.45, 7 * u, Theme.CRYSTAL, 2);
                g.text("+" + f.rewards[t], cx + cw - pad - 30 * u, cy + ch * 0.45, 12 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
                bar(g, cx + pad, cy + ch * 0.68, cw - 2 * pad, 8 * u, v / f.targets[t], f.color);
                g.text(Fmt.compact(v) + " / " + Fmt.compact(f.targets[t]), cx + cw - pad, cy + ch * 0.9, 11 * u,
                        Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, true);
            }
        }
        int rows = (Achievements.FAMILIES.length + cols - 1) / cols;
        return top + rows * (ch + gap) - y + 6 * u;
    }
}
