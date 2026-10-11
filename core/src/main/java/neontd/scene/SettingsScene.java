package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.progress.Progress;

/**
 * Einstellungen: Profil (Name, Cloud-Speicher), und die beiden Zurücksetzen-Funktionen – "Spielerlevel zurücksetzen"
 * (nur Level und XP) und "Alles zurücksetzen" (das ganze Profil). Beides fragt vorher nach.
 */
public final class SettingsScene extends PanelScene {
    private static final String RESET_LEVEL = "resetLevel";
    private static final String RESET_ALL = "resetAll";
    private static final String PROFILE = "profile";
    private static final String CREDITS = "credits";

    public SettingsScene(App app) {
        super(app, "EINSTELLUNGEN", Theme.TEXT_DIM);
    }

    private void row(Gfx g, double x, double y, double w, double h, double u, String label, String sub, Icon icon,
                     int color, Object tag) {
        double hv = Math.max(hovered(tag) ? 1 : 0, pressed(tag) ? 1 : 0);
        card(g, x, y, w, h, color, hv, false);
        Icons.draw(g, icon, x + 30 * u, y + h / 2, 13 * u, color, 3);
        fitText(g, label, x + 58 * u, y + h * 0.36, 16 * u, w - 80 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        fitText(g, sub, x + 58 * u, y + h * 0.68, 12 * u, w - 80 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
        hit(x, y, w, h, tag);
    }

    @Override
    protected double drawContent(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double h = 70 * u;
        double cy = y;
        g.text("PROFIL", x, cy + 10 * u, 13 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, true);
        cy += 24 * u;
        row(g, x, cy, w, h, u, p.displayName() + "  ·  LEVEL " + p.level(), "Name, Cloud-Speicher und Statistik öffnen",
                Icon.STAR, Theme.MAGENTA, PROFILE);
        cy += h + 12 * u;

        g.text("ZURÜCKSETZEN", x, cy + 10 * u, 13 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, true);
        cy += 24 * u;
        row(g, x, cy, w, h, u, "SPIELERLEVEL ZURÜCKSETZEN",
                "Level " + p.level() + " und XP zurück auf 0 – Medaillen, Shop und Aufgaben bleiben", Icon.UNDO,
                Theme.ORANGE, RESET_LEVEL);
        cy += h + 12 * u;
        row(g, x, cy, w, h, u, "ALLES ZURÜCKSETZEN",
                "Das ganze Profil: Level, Medaillen, Kristalle, Shop, Errungenschaften", Icon.TRASH, Theme.RED,
                RESET_ALL);
        cy += h + 12 * u;
        double lh = 16 * u;
        String[] notes = {
            "Eigene Level aus dem Editor bleiben immer erhalten.",
            "Ist der Cloud-Speicher verbunden, gilt das Zurücksetzen auf allen Geräten.",
        };
        for (String n : notes) {
            fitText(g, n, x, cy + lh / 2, 11.5 * u, w, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            cy += lh;
        }
        cy += 14 * u;
        g.text("Neon TD  ·  " + neontd.progress.Season.TIERS + " Season-Stufen, "
                + neontd.progress.Achievements.totalTiers() + " Errungenschaften-Stufen", x, cy + 8 * u, 11.5 * u,
                Colors.withAlpha(Theme.TEXT_DIM, 0.7), Gfx.ALIGN_LEFT, false);
        return cy - y + 24 * u;
    }

    @Override
    protected void onTap(Object tag) {
        if (PROFILE.equals(tag)) {
            app.goTo(new ProfileScene(app));
        } else if (RESET_LEVEL.equals(tag)) {
            Progress p = app.progress;
            confirm("LEVEL ZURÜCKSETZEN?", Theme.ORANGE, new String[] {
                "Spielerlevel " + p.level() + " und alle XP werden gelöscht.",
                "Grundtürme sind danach wieder gesperrt, bis du aufsteigst.",
                "Medaillen, Kristalle, Shop-Käufe und Aufgaben bleiben."}, "LEVEL ZURÜCKSETZEN", () -> {
                app.resetPlayerLevel();
                toast("Spielerlevel zurückgesetzt", Theme.ORANGE);
            });
        } else if (RESET_ALL.equals(tag)) {
            confirm("ALLES ZURÜCKSETZEN?", Theme.RED, new String[] {
                "Das gesamte Profil wird gelöscht:",
                "Level, Medaillen, Kristalle, Shop, Errungenschaften, Season.",
                "Das lässt sich nicht rückgängig machen!"}, "ALLES LÖSCHEN", () -> {
                app.resetEverything();
                toast("Profil zurückgesetzt", Theme.RED);
            });
        }
    }
}
