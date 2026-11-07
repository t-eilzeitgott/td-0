package neontd.scene;

import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Theme;
import neontd.level.LevelDef;
import neontd.level.Worlds;
import neontd.physics.Path;
import neontd.progress.Arena;
import neontd.render.LevelPreview;
import neontd.render.TowerArt;
import neontd.save.Leaderboard;
import neontd.save.RunSave;
import neontd.sim.TowerType;
import neontd.ui.Button;
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Kit;

/**
 * Die Arena der Woche: Karte, Regeln und Restzeit, der eigene Stand, die Rangliste (bester Lauf je Spieler mit
 * Versuchen), der Verlauf der letzten Läufe und die Ruhmeshalle der Wochensieger. Unten die Taste „Antreten“.
 * Die Platzierung der Vorwoche bringt einmalig Kristalle.
 */
public final class ArenaScene extends PanelScene {
    private final Arena arena;
    private final Arena previous;
    private final LevelDef level;
    private final Path[] paths;
    private final Button play;
    private List<Leaderboard.ArenaEntry> list;
    private List<Leaderboard.FeedEntry> feed;
    private List<Leaderboard.HallEntry> hall;
    private boolean listLoading;
    private boolean feedLoading;
    private boolean hallLoading;
    private boolean listFailed;
    private boolean feedFailed;
    private boolean hallFailed;
    /** Platz der Vorwoche (0 = nicht dabei oder unbekannt) und ob die Belohnung noch abzuholen ist. */
    private int prevRank;
    private int prevPlayers;

    public ArenaScene(App app) {
        super(app, "Arena", Theme.ACCENT);
        arena = Arena.of(app.today());
        previous = arena.previous();
        level = arena.build();
        paths = level.buildPaths();
        RunSave run = app.saves.resumable(arena.levelId());
        play = ui.add(new Button(run != null ? "Fortsetzen · Welle " + (run.snapshot.waveIndex + 1) : "Antreten", Icon.PLAY,
                Theme.ACCENT, this::start));
        play.style = Button.Style.PRIMARY;
        play.painter = (g, b) -> Kit.primary(g, -b.w / 2, -b.h / 2, b.w, b.h, b.label, Icon.PLAY, Theme.ACCENT,
                Math.max(b.hover.value, b.press.value), app.vp.u);
        setTabs("Rangliste", "Verlauf", "Ruhmeshalle");
        loadList(true);
        loadPrevious();
    }

    private void start() {
        if (!app.board.hasName()) {
            app.goTo(new NameScene(app, true));
            return;
        }
        RunSave r = app.saves.resumable(arena.levelId());
        app.goTo(GameScene.arena(app, arena, r));
    }

    // -------------------------------------------------------------------------------------------- Laden

    private void loadList(boolean force) {
        if (!app.board.available()) {
            listFailed = true;
            return;
        }
        listLoading = true;
        listFailed = false;
        app.board.arenaList(arena.id, force, l -> {
            listLoading = false;
            if (l == null) {
                listFailed = true;
            } else {
                list = l;
            }
        });
    }

    private void loadFeed() {
        if (!app.board.available()) {
            feedFailed = true;
            return;
        }
        feedLoading = true;
        feedFailed = false;
        app.board.arenaFeed(arena.id, l -> {
            feedLoading = false;
            if (l == null) {
                feedFailed = true;
            } else {
                feed = l;
            }
        });
    }

    private void loadHall() {
        if (!app.board.available()) {
            hallFailed = true;
            return;
        }
        hallLoading = true;
        hallFailed = false;
        app.board.arenaHall(l -> {
            hallLoading = false;
            if (l == null) {
                hallFailed = true;
            } else {
                hall = l;
            }
        });
    }

    /** Platz der Vorwoche holen (für die Belohnung). */
    private void loadPrevious() {
        if (!app.board.available() || app.progress.stat(rewardKey()) > 0) {
            return;
        }
        app.board.arenaPrevious(previous.id, l -> {
            if (l == null) {
                return;
            }
            for (Leaderboard.ArenaEntry e : l) {
                if (e.mine) {
                    prevRank = e.rank;
                    prevPlayers = l.size();
                }
            }
        });
    }

    private String rewardKey() {
        return "arenareward-" + previous.id;
    }

    private int rewardCrystals() {
        if (prevRank >= 1 && prevRank <= Arena.PODIUM_CRYSTALS.length) {
            return Arena.PODIUM_CRYSTALS[prevRank - 1];
        }
        return Arena.PARTICIPATION_CRYSTALS;
    }

    @Override
    protected void onTab(int i) {
        if (i == 0) {
            loadList(false);
        } else if (i == 1 && feed == null && !feedLoading) {
            loadFeed();
        } else if (i == 2 && hall == null && !hallLoading) {
            loadHall();
        }
    }

    @Override
    public void layout() {
        super.layout();
        double u = app.vp.u;
        double bh = Kit.BUTTON * u;
        play.bounds(contentX, contentBottom - bh - 8 * u, contentW, bh);
        contentBottom -= bh + 24 * u;
    }

    // ------------------------------------------------------------------------------------------ Inhalt

    private double lift(String tag) {
        return pressed(tag) ? 1 : (hovered(tag) ? 0.5 : 0);
    }

    @Override
    protected double drawContent(Gfx g, double x, double y0, double w, double u) {
        double y = y0;
        if (tab == 0) {
            if (prevRank > 0 && app.progress.stat(rewardKey()) <= 0) {
                y += rewardRow(g, x, y, w, u) + 24 * u;
            }
            y += hero(g, x, y, w, u) + Kit.SECTION_GAP * u;
            y += status(g, x, y, w, u) + Kit.SECTION_GAP * u;
            y += ranking(g, x, y, w, u);
        } else if (tab == 1) {
            y += feedList(g, x, y, w, u);
        } else {
            y += hallList(g, x, y, w, u);
        }
        return y - y0 + 16 * u;
    }

    private double rewardRow(Gfx g, double x, double y, double w, double u) {
        double h = Kit.ROW * u;
        int c = prevRank == 1 ? Theme.STAR : (prevRank <= 3 ? Theme.ACCENT : Theme.POS);
        hit(x, y, w, h, "reward");
        Kit.rowPress(g, x, y, w, h, lift("reward"), u);
        Icons.draw(g, Icon.TROPHY, x + 14 * u, y + h / 2, 11 * u, c, 0);
        double pw = Kit.pill(g, "Abholen  +" + rewardCrystals() + " ◆", x + w, y + h / 2, c, lift("reward"), u);
        String head = previous.label() + ": Platz " + prevRank + (prevPlayers > 0 ? " von " + prevPlayers : "");
        Draw.cut(g, head, x + 44 * u, y + h / 2 - 10 * u, Kit.T_ROW * u, w - pw - 56 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        Draw.cut(g, prevRank <= 3 ? "Podium – danke fürs Mitkämpfen!" : "Danke fürs Mitmachen!", x + 44 * u,
                y + h / 2 + 12 * u, Kit.T_CAPTION * u, w - pw - 56 * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        return h;
    }

    /** Karte der Woche: Kicker, Name, große Vorschau direkt auf Schwarz, Regeln, Turm-Pool, Joker. */
    private double hero(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        int accent = Worlds.accent(level.chapter);
        String left = Arena.remainingText(arena.remainingMillis(app.platform.nowMillis(), app.platform.localOffsetMillis()));
        Kit.kicker(g, arena.label() + "  ·  noch " + left, x, y + 8 * u, u, Colors.lighten(Theme.ACCENT, 0.1));
        // Regeln als Textlink rechts
        double lw = g.textWidth("Regeln", Kit.T_BODY * u, true);
        hit(x + w - lw - 30 * u, y - 6 * u, lw + 30 * u, 30 * u, "rules");
        Kit.link(g, "Regeln", x + w - 16 * u, y + 8 * u, Theme.ACCENT, lift("rules"), u);
        Draw.chevron(g, x + w - 4 * u, y + 8 * u, 5 * u, Theme.ACCENT, true);
        y += 30 * u;
        Draw.fit(g, level.name, x, y + 4 * u, Kit.T_HERO * u, w, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 36 * u;
        y += Kit.paragraph(g, arena.def.rule, x, y - 9 * u, w, Kit.T_BODY * u, Theme.MUTED, u) + 4 * u;
        double previewH = Math.min(w * 0.58, 280 * u);
        g.save();
        g.clipRect(x - 10 * u, y, w + 20 * u, previewH);
        g.radialGlow(x + w / 2, y + previewH / 2, w * 0.6, Colors.withAlpha(accent, 0.07));
        LevelPreview.draw(g, paths, level.width, level.height, x + 6 * u, y + 10 * u, w - 12 * u, previewH - 20 * u,
                Worlds.lane(level.chapter), time);
        g.restore();
        y += previewH + 14 * u;
        // Eckdaten als Kennzahlen
        double cw = w / 3;
        Kit.stat(g, Integer.toString(Arena.LIVES), "Leben", x, y, Theme.LIFE, u);
        Kit.stat(g, Fmt.compact(arena.def.money), "Startgeld", x + cw, y, Theme.MONEY, u);
        String third = arena.def.speedMul > 1 ? "+" + Math.round((arena.def.speedMul - 1) * 100) + " %"
                : (level.style != null ? level.style.label : "–");
        Draw.fit(g, third, x + 2 * cw, y + 15 * u, Kit.T_STAT * u, cw, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        g.text(arena.def.speedMul > 1 ? "Tempo" : "Stil", x + 2 * cw, y + 42 * u, Kit.T_CAPTION * u, Theme.MUTED,
                Gfx.ALIGN_LEFT, false);
        y += 66 * u;
        // Turm-Pool: Symbole frei nebeneinander
        g.text("Türme", x, y + 8 * u, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        y += 22 * u;
        double ir = 17 * u;
        double ix = x + ir;
        for (TowerType t : arena.def.pool) {
            TowerArt.drawIcon(g, t, ix, y + ir, ir, time);
            ix += ir * 2 + 14 * u;
        }
        y += ir * 2 + 18 * u;
        StringBuilder jokers = new StringBuilder();
        for (int i = 0; i < arena.def.jokers.length; i++) {
            jokers.append(i > 0 ? "  ·  " : "").append(arena.def.jokers[i].label);
        }
        g.text("Joker", x, y + 8 * u, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
        Draw.cut(g, jokers.toString(), x + 52 * u, y + 8 * u, Kit.T_BODY * u, w - 52 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        y += 20 * u;
        return y - y0;
    }

    /** Eigener Stand: Bestwert groß, Platz und Abstand – Herausfordern als Textlink. */
    private double status(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += Kit.section(g, "Dein Stand", null, 0, x, y, w, u);
        Leaderboard.ArenaEntry mine = mine();
        long best = Math.max(app.board.arenaBest(arena.id), mine != null ? mine.score : 0);
        double lw = g.textWidth("Herausfordern", Kit.T_BODY * u, true) + 30 * u;
        if (best <= 0) {
            g.text("Noch kein Lauf", x, y + 15 * u, Kit.T_STAT * 0.8 * u, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            Draw.cut(g, "Jeder Versuch zählt – nur der beste bleibt stehen.", x, y + 42 * u, Kit.T_CAPTION * u, w,
                    Theme.MUTED, Gfx.ALIGN_LEFT, false);
        } else {
            String rank = mine != null ? "Platz " + mine.rank + " von " + (list == null ? "?" : list.size()) : "Dein Bestwert";
            String gap = gapText(mine);
            g.text(Fmt.points(best), x, y + 15 * u, Kit.T_STAT * u, mine != null && mine.rank == 1 ? Theme.STAR : Theme.TEXT,
                    Gfx.ALIGN_LEFT, true);
            Draw.cut(g, rank + (gap != null ? "  ·  " + gap : ""), x, y + 42 * u, Kit.T_CAPTION * u, w, Theme.MUTED,
                    Gfx.ALIGN_LEFT, false);
        }
        hit(x + w - lw, y, lw, 52 * u, "share");
        Icons.draw(g, Icon.FLAG, x + w - lw + 8 * u, y + 24 * u, 7 * u, Theme.ACCENT, 0);
        Kit.link(g, "Herausfordern", x + w, y + 24 * u, Theme.ACCENT, lift("share"), u);
        y += 56 * u;
        return y - y0;
    }

    /** Rangfarbe: Gold, Silber, Bronze, sonst gedämpft. */
    private static int rankColor(int rank) {
        return rank == 1 ? Theme.STAR : (rank == 2 ? 0xC9D1E6 : (rank == 3 ? 0xE08A3C : Theme.MUTED));
    }

    /** Leerer Zustand: zentrierter, gedämpfter Hinweis ohne Kasten. */
    private double empty(Gfx g, String msg, double x, double y, double w, double u) {
        g.text(msg, x + w / 2, y + 40 * u, Kit.T_BODY * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
        return 80 * u;
    }

    /** Eine Ranglistenzeile: Rang, Name, Untertitel, Wert rechts; eigener Eintrag in Akzentfarbe mit Punkt. */
    private void rankRow(Gfx g, double x, double y, double w, double h, String rank, int rankColor, String name, String sub,
                         String value, String value2, int value2Color, boolean mine, boolean divider, double u) {
        g.text(rank, x + 14 * u, y + h / 2, Kit.T_ROW * u, rankColor, Gfx.ALIGN_CENTER, true);
        double nx = x + 44 * u;
        double vw = g.textWidth(value, Kit.T_ROW * u, true) + 16 * u;
        int nc = mine ? Colors.lighten(Theme.ACCENT, 0.2) : Theme.TEXT;
        double nameW = w - vw - (nx - x) - (mine ? 16 * u : 0);
        Draw.cut(g, name, nx, y + h / 2 - 10 * u, Kit.T_ROW * u, nameW, nc, Gfx.ALIGN_LEFT, true);
        if (mine) {
            double tw = Math.min(nameW, g.textWidth(name, Kit.T_ROW * u, true));
            g.fillCircle(nx + tw + 10 * u, y + h / 2 - 10 * u, 3.5 * u, Theme.ACCENT);
        }
        Draw.cut(g, sub, nx, y + h / 2 + 12 * u, Kit.T_CAPTION * u, w - vw - (nx - x), Theme.MUTED, Gfx.ALIGN_LEFT, false);
        if (value2 != null) {
            g.text(value, x + w, y + h / 2 - 10 * u, Kit.T_ROW * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
            g.text(value2, x + w, y + h / 2 + 12 * u, Kit.T_CAPTION * u, value2Color, Gfx.ALIGN_RIGHT, true);
        } else {
            g.text(value, x + w, y + h / 2, Kit.T_ROW * u, Theme.TEXT, Gfx.ALIGN_RIGHT, true);
        }
        if (divider) {
            Kit.hairline(g, nx, y + h - 1, w - (nx - x));
        }
    }

    private Leaderboard.ArenaEntry mine() {
        if (list == null) {
            return null;
        }
        for (Leaderboard.ArenaEntry e : list) {
            if (e.mine) {
                return e;
            }
        }
        return null;
    }

    private String gapText(Leaderboard.ArenaEntry mine) {
        if (mine == null || list == null) {
            return null;
        }
        Leaderboard.ArenaEntry above = null;
        for (Leaderboard.ArenaEntry e : list) {
            if (!e.mine && e.score >= mine.score && (above == null || e.score < above.score)) {
                above = e;
            }
        }
        if (above != null) {
            return "noch " + Fmt.points(above.score - mine.score + 1) + " bis " + above.name;
        }
        return list.size() > 1 ? "du führst" : null;
    }

    private double ranking(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        String trailing = null;
        if (list != null && !list.isEmpty()) {
            int tries = 0;
            for (Leaderboard.ArenaEntry e : list) {
                tries += e.attempts;
            }
            trailing = list.size() + " Spieler  ·  " + tries + " Läufe";
        }
        y += Kit.section(g, "Rangliste", trailing, Theme.MUTED, x, y, w, u);
        if (list == null || list.isEmpty()) {
            String msg = listLoading ? "Lädt …" : (listFailed ? "Keine Verbindung – antippen zum Neuladen."
                    : "Noch niemand angetreten – sei der Erste!");
            if (listFailed && app.board.available()) {
                hit(x, y, w, 80 * u, "retry");
            }
            return y + empty(g, msg, x, y, w, u) - y0;
        }
        double rowH = Kit.ROW * u;
        for (int i = 0; i < list.size(); i++) {
            Leaderboard.ArenaEntry e = list.get(i);
            String sub = "Welle " + e.wave + "  ·  Serie " + e.streak + "  ·  " + e.attempts + (e.attempts == 1 ? " Lauf" : " Läufe");
            rankRow(g, x, y, w, rowH, Integer.toString(e.rank), rankColor(e.rank), e.name, sub, Fmt.points(e.score), null, 0,
                    e.mine, i < list.size() - 1, u);
            y += rowH;
        }
        y += 18 * u;
        g.text("Gezählt wird je Spieler der beste Lauf der Woche.", x + w / 2, y, Kit.T_CAPTION * u, Theme.MUTED,
                Gfx.ALIGN_CENTER, false);
        return y + 20 * u - y0;
    }

    private double feedList(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += Kit.section(g, "Letzte Läufe", arena.label(), Theme.MUTED, x, y, w, u);
        if (feed == null || feed.isEmpty()) {
            String msg = feedLoading ? "Lädt …" : (feedFailed ? "Keine Verbindung." : "Noch keine Läufe diese Woche.");
            return y + empty(g, msg, x, y, w, u) - y0;
        }
        double rowH = Kit.ROW * u;
        double now = app.platform.nowMillis();
        for (int i = 0; i < feed.size(); i++) {
            Leaderboard.FeedEntry e = feed.get(i);
            g.fillCircle(x + 14 * u, y + rowH / 2, 4.5 * u, e.record ? Theme.POS : Colors.withAlpha(0xFFFFFF, 0.2));
            rankRow(g, x, y, w, rowH, "", 0, e.name, "Welle " + e.wave + "  ·  " + ago(now - e.at), Fmt.points(e.score),
                    e.record ? "Rekord" : null, Theme.POS, e.mine, i < feed.size() - 1, u);
            y += rowH;
        }
        return y + 10 * u - y0;
    }

    static String ago(double ms) {
        double min = Math.max(0, ms / 60000.0);
        if (min < 1) {
            return "gerade eben";
        }
        if (min < 60) {
            return "vor " + (int) min + " Min";
        }
        double h = min / 60;
        if (h < 24) {
            return "vor " + (int) h + " Std";
        }
        int d = (int) (h / 24);
        return d == 1 ? "gestern" : "vor " + d + " Tagen";
    }

    private double hallList(Gfx g, double x, double y, double w, double u) {
        double y0 = y;
        y += Kit.section(g, "Wochensieger", null, 0, x, y, w, u);
        if (hall == null || hall.isEmpty()) {
            String msg = hallLoading ? "Lädt …" : (hallFailed ? "Keine Verbindung." : "Die erste Woche läuft noch – wer holt sie?");
            return y + empty(g, msg, x, y, w, u) - y0;
        }
        java.util.Map<String, Integer> wins = new java.util.LinkedHashMap<>();
        for (Leaderboard.HallEntry e : hall) {
            wins.put(e.name, wins.getOrDefault(e.name, 0) + 1);
        }
        double rowH = Kit.ROW * u;
        for (int i = 0; i < hall.size(); i++) {
            Leaderboard.HallEntry e = hall.get(i);
            Icons.draw(g, Icon.TROPHY, x + 14 * u, y + rowH / 2, 10 * u, Theme.STAR, 0);
            String wk = "KW " + (e.week.length() >= 8 ? Integer.parseInt(e.week.substring(6)) : 0);
            int n = wins.getOrDefault(e.name, 1);
            rankRow(g, x, y, w, rowH, "", 0, e.name + (n > 1 ? "  ·  " + n + "× Sieger" : ""),
                    wk + "  ·  Welle " + e.wave + "  ·  " + e.players + " Spieler", Fmt.points(e.score), null, 0, e.mine,
                    i < hall.size() - 1, u);
            y += rowH;
        }
        return y + 10 * u - y0;
    }

    // ---------------------------------------------------------------------------------------- Aktionen

    @Override
    protected void onTap(Object tag) {
        if ("retry".equals(tag)) {
            loadList(true);
        } else if ("rules".equals(tag)) {
            sheet("So zählt die Arena", Theme.ACCENT, new String[] {
                "Alle spielen dieselbe Karte mit denselben Türmen. Spielerlevel, Shop und Forschung zählen hier nicht.",
                Arena.LIVES + " Leben, keine Meisterstufen – die Wellen werden immer härter, bis der Lauf endet.",
                "Punkte: Abschüsse (Prämie × Welle) und Wellenbonus (100 × Welle), beides × Serien-Multiplikator.",
                "Serie: Jede Welle ohne durchgelassenen Gegner bringt +0,1 – bis ×3,0. Ein Durchbruch setzt auf ×1,0.",
                "Vorlauf: Startest du die nächste Welle, während noch Gegner laufen, zählt ihr Wellenbonus doppelt.",
                "Es zählt dein bester Lauf der Woche. Montag 0 Uhr startet eine neue Arena; Platz 1–3 bekommen "
                        + Arena.PODIUM_CRYSTALS[0] + " / " + Arena.PODIUM_CRYSTALS[1] + " / " + Arena.PODIUM_CRYSTALS[2]
                        + " Kristalle, Mitmachen " + Arena.PARTICIPATION_CRYSTALS + ".",
            }, new Button("Verstanden", null, Theme.ACCENT, null));
        } else if ("share".equals(tag)) {
            long best = app.board.arenaBest(arena.id);
            Leaderboard.ArenaEntry m = mine();
            if (m != null) {
                best = Math.max(best, m.score);
            }
            String text = best > 0
                    ? "Ich habe " + Fmt.points(best) + " Punkte in der Neon-TD-Arena (" + arena.label() + ", " + level.name
                            + ") – schlägst du das? https://t-eilzeitgott.github.io/td-0/"
                    : "Neon-TD-Arena " + arena.label() + " (" + level.name + "): Wer holt diese Woche die meisten Punkte?"
                            + " https://t-eilzeitgott.github.io/td-0/";
            app.platform.copyText(text, ok -> toast(ok ? "Text kopiert – schick ihn weiter" : "Kopieren ging nicht",
                    ok ? Theme.POS : Theme.NEG));
        } else if ("reward".equals(tag)) {
            if (app.progress.stat(rewardKey()) > 0 || prevRank <= 0) {
                return;
            }
            app.progress.stats.put(rewardKey(), 1.0);
            int got = app.progress.addCrystals(rewardCrystals());
            app.commitProgress();
            toast("+" + got + " Kristalle für " + previous.label(), Theme.STAR);
        }
    }

    /** Für Tests und Screenshots: Spielen-Taste. */
    public double[] playAnchor() {
        return new double[] {play.cx(), play.cy()};
    }
}
