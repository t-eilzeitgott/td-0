package neontd.scene;

import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.progress.Progress;
import neontd.ui.Draw;
import neontd.ui.Kit;

/**
 * Einstellungen: Profil (Name, Cloud-Speicher), und die beiden Zurücksetzen-Funktionen – "Spielerlevel zurücksetzen"
 * (nur Level und XP) und "Alles zurücksetzen" (das ganze Profil). Beides fragt vorher nach.
 */
public final class SettingsScene extends PanelScene {
    static final String RESET_LEVEL = "resetLevel";
    static final String RESET_ALL = "resetAll";
    private static final String PROFILE = "profile";
    private static final String CREDITS = "credits";
    private static final String INSTALL = "install";

    public SettingsScene(App app) {
        super(app, "Einstellungen", Theme.ACCENT);
    }

    private double lift(Object tag) {
        return pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
    }

    /** Zeile ohne Fläche: Symbol, Titel (in {@code titleColor}), Untertitel, Chevron, Haarlinie. */
    private double row(Gfx g, double x, double y, double w, double u, String label, String sub, Icon icon, int color,
                       int titleColor, Object tag, boolean divider) {
        double h = Kit.ROW * u;
        Kit.rowPress(g, x, y, w, h, lift(tag), u);
        hit(x, y, w, h, tag);
        Icons.draw(g, icon, x + 14 * u, y + h / 2, 10 * u, color, 0);
        double tx = x + 44 * u;
        double right = x + w - 22 * u;
        Draw.cut(g, label, tx, y + h / 2 - 10 * u, Kit.T_ROW * u, right - tx, titleColor, Gfx.ALIGN_LEFT, true);
        Draw.cut(g, sub, tx, y + h / 2 + 12 * u, Kit.T_CAPTION * u, right - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.chevron(g, x + w - 6 * u, y + h / 2, 5.5 * u, Colors.withAlpha(0xFFFFFF, 0.35), true);
        if (divider) {
            Kit.hairline(g, tx, y + h - 1, x + w - tx);
        }
        return h;
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        Progress p = app.progress;
        double y = y0;
        boolean install = InstallScene.offered(app);
        y += Kit.section(g, "Profil", null, 0, x, y, w, u);
        y += row(g, x, y, w, u, p.displayName() + "  ·  Level " + p.level(), "Name, Cloud-Speicher und Statistik",
                Icon.STAR, Theme.MAGENTA, Theme.TEXT, PROFILE, install);
        if (install) {
            y += row(g, x, y, w, u, "Als App installieren", "Auf den Home-Bildschirm: Vollbild und auch offline",
                    Icon.PLUS, Theme.ACCENT, Theme.TEXT, INSTALL, false);
        }
        y += Kit.SECTION_GAP * u;
        y += Kit.section(g, "Zurücksetzen", null, 0, x, y, w, u);
        y += row(g, x, y, w, u, "Spielerlevel zurücksetzen",
                "Level " + p.level() + " und XP auf 0 – Medaillen, Shop und Aufgaben bleiben", Icon.UNDO,
                Theme.ORANGE, Theme.ORANGE, RESET_LEVEL, true);
        y += row(g, x, y, w, u, "Alles zurücksetzen",
                "Level, Medaillen, Kristalle, Shop, Errungenschaften", Icon.TRASH, Theme.RED, Theme.RED, RESET_ALL, false);
        y += 22 * u;
        y += Kit.paragraph(g, "Eigene Level aus dem Editor bleiben immer erhalten. Ist der Cloud-Speicher verbunden, gilt "
                + "das Zurücksetzen auf allen Geräten.", x, y, w, Kit.T_CAPTION * u, Theme.MUTED, u);
        y += Kit.SECTION_GAP * u;
        g.text("Neon TD  ·  v" + neontd.app.Version.NAME, x + w / 2, y, Kit.T_CAPTION * u, Colors.withAlpha(0xFFFFFF, 0.3),
                Gfx.ALIGN_CENTER, false);
        y += 20 * u;
        g.text(neontd.progress.Season.TIERS + " Season-Stufen  ·  " + neontd.progress.Achievements.totalTiers()
                + " Errungenschaften-Stufen", x + w / 2, y, 11.5 * u, Colors.withAlpha(0xFFFFFF, 0.22), Gfx.ALIGN_CENTER, false);
        return y - y0 + 30 * u;
    }

    @Override
    protected void onTap(Object tag) {
        if (PROFILE.equals(tag)) {
            app.goTo(new ProfileScene(app));
        } else if (INSTALL.equals(tag)) {
            app.goTo(new InstallScene(app, false));
        } else if (RESET_LEVEL.equals(tag)) {
            Progress p = app.progress;
            confirm("Spielerlevel zurücksetzen?", Theme.ORANGE, new String[] {
                "Spielerlevel " + p.level() + " und alle XP werden gelöscht.",
                "Grundtürme sind danach wieder gesperrt, bis du aufsteigst.",
                "Medaillen, Kristalle, Shop-Käufe und Aufgaben bleiben."}, "Zurücksetzen", () -> {
                app.resetPlayerLevel();
                toast("Spielerlevel zurückgesetzt", Theme.ORANGE);
            });
        } else if (RESET_ALL.equals(tag)) {
            confirm("Alles zurücksetzen?", Theme.RED, new String[] {
                "Das gesamte Profil wird gelöscht:",
                "Level, Medaillen, Kristalle, Shop, Errungenschaften, Season.",
                "Das lässt sich nicht rückgängig machen!"}, "Alles löschen", () -> {
                app.resetEverything();
                toast("Profil zurückgesetzt", Theme.RED);
            });
        }
    }
}
