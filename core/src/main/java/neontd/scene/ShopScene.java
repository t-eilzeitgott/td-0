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
import neontd.ui.Draw;
import neontd.ui.Fmt;
import neontd.ui.Kit;

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
        super(app, "Shop", Theme.CRYSTAL);
        setTabs("Türme", "Spezial", "Forschung");
        selectTab(tab);
    }

    @Override
    protected void tick(double dt) {
        if (flashTime > 0) {
            flashTime -= dt;
        }
    }

    // ------------------------------------------------------------------------------------- Zeichnen

    /** „INFERNO“ → „Inferno“ (Namen stehen in den Daten in Großbuchstaben). */
    static String nice(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        StringBuilder b = new StringBuilder();
        boolean start = true;
        for (char c : s.toCharArray()) {
            b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == ' ' || c == '-';
        }
        return b.toString();
    }

    @Override
    protected double drawContent(Gfx g, double x, double y, double w, double u) {
        String intro;
        switch (tab) {
            case TAB_TOWERS:
                intro = "Elite-Türme stehen nach dem Kauf in jedem Spiel im Turm-Shop.";
                break;
            case TAB_SPECIALS:
                intro = "Spezial-Upgrades baust du im Spiel in den Spezial-Platz eines Turms ein.";
                break;
            default:
                intro = "Forschung wirkt dauerhaft in jedem Spiel.";
                break;
        }
        double h = Kit.paragraph(g, intro, x, y, w, Kit.T_BODY * 0.95 * u, Theme.MUTED, u) + 14 * u;
        switch (tab) {
            case TAB_TOWERS:
                return h + drawTowers(g, x, y + h, w, u);
            case TAB_SPECIALS:
                return h + drawSpecials(g, x, y + h, w, u);
            default:
                return h + drawPerks(g, x, y + h, w, u);
        }
    }

    private double lift(Object tag) {
        double hv = Math.max(hovered(tag) ? 0.5 : 0, pressed(tag) ? 1 : 0);
        if (flashTag == tag && flashTime > 0) {
            hv = Math.max(hv, Math.min(1, flashTime / 0.4));
        }
        return hv;
    }

    /** Rechte Spalte: Besitz, Sperre oder Preis. @return Breite */
    private double status(Gfx g, double right, double cy, double u, boolean owned, boolean lvl, boolean afford, int price,
                          int needLevel) {
        double fs = Kit.T_BODY * u;
        if (owned) {
            String t = "Besitzt";
            g.text(t, right, cy, fs, Theme.GREEN, Gfx.ALIGN_RIGHT, true);
            double tw = g.textWidth(t, fs, true);
            Icons.draw(g, Icon.CHECK, right - tw - 12 * u, cy, 6.5 * u, Theme.GREEN, 0);
            return tw + 22 * u;
        }
        if (lvl) {
            String t = "Level " + needLevel;
            g.text(t, right, cy, fs, Theme.MUTED, Gfx.ALIGN_RIGHT, true);
            double tw = g.textWidth(t, fs, true);
            Draw.lock(g, right - tw - 12 * u, cy, 6 * u);
            return tw + 22 * u;
        }
        return Kit.iconValueRight(g, right, cy, Icon.CRYSTAL, Fmt.compact(price), Theme.CRYSTAL,
                afford ? Theme.TEXT : Theme.NEG, Kit.T_ROW * u, u);
    }

    /** Gemeinsame Zeile: Symbol links (vom Aufrufer), Titel, bis zu zwei Zeilen Text, rechts der Status. */
    private double item(Gfx g, double x, double y, double w, double u, Object tag, boolean dim, String title, String desc,
                        String meta, boolean owned, boolean lvl, boolean afford, int price, int need, boolean last) {
        double tx = x + 64 * u;
        double right = x + w;
        double sw = 96 * u;
        java.util.List<String> lines = wrap(g, desc, Kit.T_CAPTION * u, right - tx);
        int nl = Math.min(2, lines.size());
        double h = 30 * u + nl * 18 * u + (meta != null ? 20 * u : 0) + 22 * u;
        h = Math.max(h, 92 * u);
        Kit.rowPress(g, x, y, w, h, lift(tag), u);
        hit(x, y, w, h, tag);
        g.save();
        g.alpha(dim ? 0.45 : 1);
        double ty = y + 26 * u;
        Draw.cut(g, title, tx, ty, Kit.T_ROW * 1.06 * u, right - sw - tx, Theme.TEXT, Gfx.ALIGN_LEFT, true);
        ty += 22 * u;
        for (int k = 0; k < nl; k++) {
            g.text(lines.get(k), tx, ty, Kit.T_CAPTION * u, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            ty += 18 * u;
        }
        if (meta != null) {
            g.text(meta, tx, ty + 2 * u, Kit.T_CAPTION * 0.95 * u, Colors.withAlpha(0xFFFFFF, 0.45), Gfx.ALIGN_LEFT, false);
        }
        g.restore();
        status(g, right, y + 26 * u, u, owned, lvl, afford, price, need);
        if (!last) {
            Kit.hairline(g, tx, y + h - 1, right - tx);
        }
        return h;
    }

    private double drawTowers(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double y0 = y;
        List<TowerType> list = new ArrayList<>();
        for (TowerType t : TowerType.values()) {
            if (t.tier == 2) {
                list.add(t);
            }
        }
        for (int i = 0; i < list.size(); i++) {
            TowerType t = list.get(i);
            boolean owned = p.ownedTowers.contains(t.name());
            boolean lvl = p.level() < t.unlockLevel;
            boolean afford = p.crystals() >= t.crystals;
            String meta = t.passive ? "Baukosten " + Fmt.compact(t.cost) + "  ·  Hilfsturm"
                    : "Baukosten " + Fmt.compact(t.cost) + "  ·  Reichweite " + (int) t.range;
            double h = item(g, x, y, w, u, t, lvl && !owned, nice(t.label), t.tagline, meta, owned, lvl, afford, t.crystals,
                    t.unlockLevel, i == list.size() - 1);
            g.save();
            g.alpha(lvl && !owned ? 0.45 : 1);
            TowerArt.drawIcon(g, t, x + 24 * u, y + h / 2, 24 * u, app.time);
            g.restore();
            y += h;
        }
        return y - y0 + 10 * u;
    }

    private double drawSpecials(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double y0 = y;
        Special[] all = Special.values();
        for (int i = 0; i < all.length; i++) {
            Special s = all[i];
            boolean owned = p.owns(s);
            boolean lvl = p.level() < s.needLevel;
            boolean afford = p.crystals() >= s.price;
            double h = item(g, x, y, w, u, s, lvl && !owned, nice(s.label), s.desc, null, owned, lvl, afford, s.price,
                    s.needLevel, i == all.length - 1);
            g.save();
            g.alpha(lvl && !owned ? 0.45 : 1);
            double icx = x + 24 * u;
            double icy = y + h / 2;
            Neon.ngon(g, icx, icy, 22 * u, 6, Math.PI / 6, 2.2, s.color, 6);
            Icons.draw(g, Icon.STAR, icx, icy, 10 * u, s.color, 3);
            g.restore();
            y += h;
        }
        return y - y0 + 10 * u;
    }

    private double drawPerks(Gfx g, double x, double y, double w, double u) {
        Progress p = app.progress;
        double y0 = y;
        Perk[] all = Perk.values();
        for (int i = 0; i < all.length; i++) {
            Perk k = all[i];
            int lv = p.perkLevel(k);
            boolean max = lv >= k.maxLevel;
            boolean lock = p.level() < k.needLevel;
            int cost = max ? 0 : k.cost(lv);
            boolean afford = !max && p.crystals() >= cost;
            double tx = x + 64 * u;
            double right = x + w;
            double h = 104 * u;
            Kit.rowPress(g, x, y, w, h, lift(k), u);
            hit(x, y, w, h, k);
            g.save();
            g.alpha(lock ? 0.45 : 1);
            // Stufe als großer Wert links
            g.text(Integer.toString(lv), x + 24 * u, y + 34 * u, 26 * u, max ? Theme.GREEN : Theme.CRYSTAL, Gfx.ALIGN_CENTER, true);
            g.text("von " + k.maxLevel, x + 24 * u, y + 58 * u, Kit.T_CAPTION * 0.9 * u, Theme.MUTED, Gfx.ALIGN_CENTER, false);
            Draw.cut(g, nice(k.label), tx, y + 26 * u, Kit.T_ROW * 1.06 * u, right - 110 * u - tx, Theme.TEXT, Gfx.ALIGN_LEFT,
                    true);
            Draw.cut(g, k.desc, tx, y + 50 * u, Kit.T_CAPTION * u, right - tx, Theme.MUTED, Gfx.ALIGN_LEFT, false);
            // Stufenpunkte
            double gap = 4 * u;
            double pip = Math.min(26 * u, (right - tx - gap * (k.maxLevel - 1)) / k.maxLevel);
            for (int s = 0; s < k.maxLevel; s++) {
                double px = tx + s * (pip + gap);
                g.fillRoundRect(px, y + 72 * u, pip, 4 * u, 2 * u,
                        s < lv ? (max ? Theme.GREEN : Theme.CRYSTAL) : Colors.withAlpha(0xFFFFFF, 0.1));
            }
            g.restore();
            if (max) {
                String t = "Voll";
                g.text(t, right, y + 26 * u, Kit.T_BODY * u, Theme.GREEN, Gfx.ALIGN_RIGHT, true);
                Icons.draw(g, Icon.CHECK, right - g.textWidth(t, Kit.T_BODY * u, true) - 12 * u, y + 26 * u, 6.5 * u,
                        Theme.GREEN, 0);
            } else {
                status(g, right, y + 26 * u, u, false, lock, afford, cost, k.needLevel);
            }
            if (i < all.length - 1) {
                Kit.hairline(g, tx, y + h - 1, right - tx);
            }
            y += h;
        }
        return y - y0 + 10 * u;
    }

    // -------------------------------------------------------------------------------------- Eingabe

    @Override
    protected void onTap(Object tag) {
        Progress p = app.progress;
        if (tag instanceof TowerType) {
            TowerType t = (TowerType) tag;
            if (p.ownedTowers.contains(t.name())) {
                inform(nice(t.label), t.color, new String[] {t.tagline, "Du besitzt diesen Turm – er steht in jedem Spiel",
                    "im Turm-Shop zur Verfügung (Seite 2)."});
                return;
            }
            if (p.level() < t.unlockLevel) {
                toast("Erst Spielerlevel " + t.unlockLevel + " erreichen", Theme.RED);
                return;
            }
            confirm(nice(t.label) + " kaufen?", t.color, new String[] {t.tagline,
                "Preis: " + t.crystals + " Kristalle (Guthaben " + p.crystals() + ")"}, "Kaufen", () -> {
                buy(t, p.buyTower(t), nice(t.label));
            });
        } else if (tag instanceof Special) {
            Special s = (Special) tag;
            if (p.owns(s)) {
                inform(nice(s.label), s.color, new String[] {s.desc, "Im Spiel baust du es über den Spezial-Platz",
                    "eines Turms ein (gegen Geld, tauschbar)."});
                return;
            }
            if (p.level() < s.needLevel) {
                toast("Erst Spielerlevel " + s.needLevel + " erreichen", Theme.RED);
                return;
            }
            confirm(nice(s.label) + " kaufen?", s.color, new String[] {s.desc,
                "Preis: " + s.price + " Kristalle (Guthaben " + p.crystals() + ")"}, "Kaufen", () -> {
                buy(s, p.buySpecial(s), nice(s.label));
            });
        } else if (tag instanceof Perk) {
            Perk k = (Perk) tag;
            int lv = p.perkLevel(k);
            if (lv >= k.maxLevel) {
                toast(nice(k.label) + " ist voll ausgebaut", Theme.GREEN);
                return;
            }
            if (p.level() < k.needLevel) {
                toast("Erst Spielerlevel " + k.needLevel + " erreichen", Theme.RED);
                return;
            }
            int cost = k.cost(lv);
            confirm(nice(k.label) + " ausbauen?", Theme.CRYSTAL, new String[] {k.desc,
                "Stufe " + lv + " → " + (lv + 1) + "  ·  " + cost + " Kristalle (Guthaben " + p.crystals() + ")"},
                "Ausbauen", () -> buy(k, p.buyPerk(k), nice(k.label)));
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
