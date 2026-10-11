package neontd.scene;

import java.util.ArrayList;
import java.util.List;
import neontd.app.App;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Icons;
import neontd.gfx.Icons.Icon;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.progress.Perk;
import neontd.progress.Progress;
import neontd.render.TowerArt;
import neontd.sim.Special;
import neontd.sim.TowerType;
import neontd.ui.Fmt;

/**
 * Der Shop im Hauptmenü: Elite-Türme, Spezial-Upgrades und Forschung (dauerhafte Boni) gegen Kristalle. Alles, was
 * hier gekauft wird, steht danach in jedem Spiel zur Verfügung.
 */
public final class ShopScene extends PanelScene {
    private static final int TAB_TOWERS = 0;
    private static final int TAB_SPECIALS = 1;
    private static final int TAB_PERKS = 2;

    private Object flashTag;
    private double flashTime;

    public ShopScene(App app) {
        this(app, TAB_TOWERS);
    }

    public ShopScene(App app, int tab) {
        super(app, "SHOP", Theme.CRYSTAL);
        setTabs("TÜRME", "SPEZIAL", "FORSCHUNG");
        selectTab(tab);
    }

    @Override
    protected void tick(double dt) {
        if (flashTime > 0) {
            flashTime -= dt;
        }
    }

    // ------------------------------------------------------------------------------------- Zeichnen

    private int columns(double w, double u, double minCard) {
        int cols = (int) Math.floor((w + 12 * u) / (minCard * u + 12 * u));
        return Math.max(1, Math.min(3, cols));
    }

    @Override
    protected double drawContent(Gfx g, double x, double y, double w, double u) {
        switch (tab) {
            case TAB_TOWERS:
                return drawTowers(g, x, y, w, u);
            case TAB_SPECIALS:
                return drawSpecials(g, x, y, w, u);
            default:
                return drawPerks(g, x, y, w, u);
        }
    }

    private double drawTowers(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double gap = 12 * u;
        int cols = columns(w, u, 400);
        double cw = (w - gap * (cols - 1)) / cols;
        double ch = 118 * u;
        List<TowerType> list = new ArrayList<>();
        for (TowerType t : TowerType.values()) {
            if (t.tier == 2) {
                list.add(t);
            }
        }
        for (int i = 0; i < list.size(); i++) {
            TowerType t = list.get(i);
            double cx = x + (i % cols) * (cw + gap);
            double cy = y + (i / cols) * (ch + gap);
            boolean owned = p.ownedTowers.contains(t.name());
            boolean lvl = p.level() < t.unlockLevel;
            boolean afford = p.crystals() >= t.crystals;
            double hv = Math.max(hovered(t) ? 1 : 0, pressed(t) ? 1 : 0);
            if (flashTag == t && flashTime > 0) {
                hv = 1;
            }
            card(g, cx, cy, cw, ch, t.color, hv, lvl && !owned);
            g.save();
            g.alpha(lvl && !owned ? 0.45 : 1);
            TowerArt.drawIcon(g, t, cx + ch * 0.5, cy + ch * 0.5, ch * 0.3, app.time);
            double tx = cx + ch * 0.98;
            double tw = cw - ch * 0.98 - 12 * u;
            fitText(g, t.label, tx, cy + ch * 0.2, 17 * u, tw, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            fitText(g, t.tagline, tx, cy + ch * 0.4, 12 * u, tw, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            String stats = t.passive ? "Baukosten " + Fmt.compact(t.cost) + "  ·  Hilfsturm"
                    : "Baukosten " + Fmt.compact(t.cost) + "  ·  Reichweite " + (int) t.range;
            fitText(g, stats, tx, cy + ch * 0.58, 11 * u, tw, Colors.withAlpha(Theme.TEXT, 0.75), Gfx.ALIGN_LEFT, false);
            g.restore();
            statusLine(g, tx, cy + ch * 0.8, tw, u, owned, lvl, afford, t.crystals, t.unlockLevel);
            hit(cx, cy, cw, ch, t);
        }
        int rows = (list.size() + cols - 1) / cols;
        return rows * (ch + gap) + 6 * u;
    }

    /** Zeile "BESITZT" / "LV n" / Preis. */
    private void statusLine(Gfx g, double x, double y, double w, double u, boolean owned, boolean lvl, boolean afford,
                            int price, int needLevel) {
        if (owned) {
            Icons.draw(g, Icon.CHECK, x + 8 * u, y, 8 * u, Theme.GREEN, 3);
            g.text("BESITZT", x + 22 * u, y, 13 * u, Theme.GREEN, Gfx.ALIGN_LEFT, true);
            return;
        }
        if (lvl) {
            Icons.draw(g, Icon.LOCK, x + 8 * u, y, 8 * u, Theme.TEXT_DIM, 0);
            g.text("AB SPIELERLEVEL " + needLevel, x + 22 * u, y, 12 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, true);
            return;
        }
        Icons.draw(g, Icon.CRYSTAL, x + 8 * u, y, 8 * u, Theme.CRYSTAL, 3);
        g.text(Fmt.compact(price), x + 22 * u, y, 14 * u, afford ? Theme.TEXT : Theme.RED, Gfx.ALIGN_LEFT, true);
        if (afford) {
            g.text("KAUFEN", x + w, y, 12 * u, Theme.CRYSTAL, Gfx.ALIGN_RIGHT, true);
        }
    }

    private double drawSpecials(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double gap = 12 * u;
        int cols = columns(w, u, 400);
        double cw = (w - gap * (cols - 1)) / cols;
        double ch = 100 * u;
        Special[] all = Special.values();
        for (int i = 0; i < all.length; i++) {
            Special s = all[i];
            double cx = x + (i % cols) * (cw + gap);
            double cy = y + (i / cols) * (ch + gap);
            boolean owned = p.owns(s);
            boolean lvl = p.level() < s.needLevel;
            boolean afford = p.crystals() >= s.price;
            double hv = Math.max(hovered(s) ? 1 : 0, pressed(s) ? 1 : 0);
            if (flashTag == s && flashTime > 0) {
                hv = 1;
            }
            card(g, cx, cy, cw, ch, s.color, hv, lvl && !owned);
            g.save();
            g.alpha(lvl && !owned ? 0.45 : 1);
            double ir = ch * 0.26;
            double icx = cx + ch * 0.45;
            double icy = cy + ch * 0.45;
            Neon.ngon(g, icx, icy, ir * 1.25, 6, Math.PI / 6, 2.2, s.color, 6);
            Icons.draw(g, Icon.STAR, icx, icy, ir * 0.7, s.color, 3);
            double tx = cx + ch * 0.95;
            double tw = cw - ch * 0.95 - 12 * u;
            fitText(g, s.label, tx, cy + ch * 0.2, 16 * u, tw, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            List<String> lines = wrap(g, s.desc, 11.5 * u, tw);
            for (int k = 0; k < Math.min(2, lines.size()); k++) {
                g.text(lines.get(k), tx, cy + ch * 0.4 + k * 14 * u, 11.5 * u, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            }
            g.restore();
            statusLine(g, tx, cy + ch * 0.85, tw, u, owned, lvl, afford, s.price, s.needLevel);
            hit(cx, cy, cw, ch, s);
        }
        int rows = (all.length + cols - 1) / cols;
        return rows * (ch + gap) + 6 * u;
    }

    private double drawPerks(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double gap = 12 * u;
        int cols = columns(w, u, 400);
        double cw = (w - gap * (cols - 1)) / cols;
        double ch = 104 * u;
        Perk[] all = Perk.values();
        for (int i = 0; i < all.length; i++) {
            Perk k = all[i];
            double cx = x + (i % cols) * (cw + gap);
            double cy = y + (i / cols) * (ch + gap);
            int lv = p.perkLevel(k);
            boolean max = lv >= k.maxLevel;
            boolean lock = p.level() < k.needLevel;
            int cost = max ? 0 : k.cost(lv);
            boolean afford = !max && p.crystals() >= cost;
            double hv = Math.max(hovered(k) ? 1 : 0, pressed(k) ? 1 : 0);
            if (flashTag == k && flashTime > 0) {
                hv = 1;
            }
            int col = Theme.CRYSTAL;
            card(g, cx, cy, cw, ch, col, hv, lock);
            g.save();
            g.alpha(lock ? 0.45 : 1);
            double tx = cx + 14 * u;
            double tw = cw - 28 * u;
            fitText(g, k.label, tx, cy + ch * 0.2, 16 * u, tw * 0.62, Theme.TEXT, Gfx.ALIGN_LEFT, true);
            g.text("STUFE " + lv + "/" + k.maxLevel, cx + cw - 14 * u, cy + ch * 0.2, 12 * u,
                    max ? Theme.GREEN : Theme.TEXT_DIM, Gfx.ALIGN_RIGHT, true);
            fitText(g, k.desc, tx, cy + ch * 0.4, 12 * u, tw, Theme.TEXT_DIM, Gfx.ALIGN_LEFT, false);
            // Stufenpunkte
            double pip = Math.min(14 * u, (tw - 4 * u * (k.maxLevel - 1)) / k.maxLevel);
            for (int s = 0; s < k.maxLevel; s++) {
                double px = tx + s * (pip + 4 * u);
                g.fillRoundRect(px, cy + ch * 0.54, pip, 6 * u, 3 * u,
                        s < lv ? Theme.CRYSTAL : Colors.withAlpha(0xFFFFFF, 0.12));
            }
            g.restore();
            double ly = cy + ch * 0.82;
            if (max) {
                Icons.draw(g, Icon.CHECK, tx + 8 * u, ly, 8 * u, Theme.GREEN, 3);
                g.text("VOLL AUSGEBAUT", tx + 22 * u, ly, 13 * u, Theme.GREEN, Gfx.ALIGN_LEFT, true);
            } else {
                statusLine(g, tx, ly, tw, u, false, lock, afford, cost, k.needLevel);
            }
            hit(cx, cy, cw, ch, k);
        }
        int rows = (all.length + cols - 1) / cols;
        return rows * (ch + gap) + 6 * u;
    }

    // -------------------------------------------------------------------------------------- Eingabe

    @Override
    protected void onTap(Object tag) {
        Progress p = app.progress;
        if (tag instanceof TowerType) {
            TowerType t = (TowerType) tag;
            if (p.ownedTowers.contains(t.name())) {
                inform(t.label, t.color, new String[] {t.tagline, "Du besitzt diesen Turm – er steht in jedem Spiel",
                    "im Turm-Shop zur Verfügung (Seite 2)."});
                return;
            }
            if (p.level() < t.unlockLevel) {
                toast("Erst Spielerlevel " + t.unlockLevel + " erreichen", Theme.RED);
                return;
            }
            confirm(t.label + " KAUFEN?", t.color, new String[] {t.tagline,
                "Preis: " + t.crystals + " Kristalle (Guthaben " + p.crystals() + ")"}, "KAUFEN", () -> {
                buy(t, p.buyTower(t), t.label);
            });
        } else if (tag instanceof Special) {
            Special s = (Special) tag;
            if (p.owns(s)) {
                inform(s.label, s.color, new String[] {s.desc, "Im Spiel baust du es über den Spezial-Platz",
                    "eines Turms ein (gegen Geld, tauschbar)."});
                return;
            }
            if (p.level() < s.needLevel) {
                toast("Erst Spielerlevel " + s.needLevel + " erreichen", Theme.RED);
                return;
            }
            confirm(s.label + " KAUFEN?", s.color, new String[] {s.desc,
                "Preis: " + s.price + " Kristalle (Guthaben " + p.crystals() + ")"}, "KAUFEN", () -> {
                buy(s, p.buySpecial(s), s.label);
            });
        } else if (tag instanceof Perk) {
            Perk k = (Perk) tag;
            int lv = p.perkLevel(k);
            if (lv >= k.maxLevel) {
                toast(k.label + " ist voll ausgebaut", Theme.GREEN);
                return;
            }
            if (p.level() < k.needLevel) {
                toast("Erst Spielerlevel " + k.needLevel + " erreichen", Theme.RED);
                return;
            }
            int cost = k.cost(lv);
            confirm(k.label + " AUSBAUEN?", Theme.CRYSTAL, new String[] {k.desc,
                "Stufe " + lv + " → " + (lv + 1) + "  ·  " + cost + " Kristalle (Guthaben " + p.crystals() + ")"},
                "AUSBAUEN", () -> buy(k, p.buyPerk(k), k.label));
        }
    }

    private void buy(Object tag, Progress.Buy result, String name) {
        switch (result) {
            case OK:
                flashTag = tag;
                flashTime = 0.8;
                List<String> ach = app.progress.checkAchievements();
                app.commitProgress();
                toast(ach.isEmpty() ? name + " gekauft!" : "Errungenschaft: " + ach.get(0), Theme.GREEN);
                break;
            case FUNDS:
                toast("Nicht genug Kristalle", Theme.RED);
                break;
            case LEVEL:
                toast("Spielerlevel zu niedrig", Theme.RED);
                break;
            default:
                break;
        }
    }
}
