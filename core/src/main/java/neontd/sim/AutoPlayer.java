package neontd.sim;

import neontd.math.Mathx;
import neontd.physics.Path;

/**
 * Ein einfacher, skriptgesteuerter Spieler. Er baut Türme dorthin, wo sie am meisten Pfad abdecken, und gibt
 * übriges Geld für Upgrades aus. Verwendet für Balance-Tests und die lebendige Hintergrund-Demo im Hauptmenü.
 */
public final class AutoPlayer {
    private static final TowerType[] BUILD_ORDER = {
        TowerType.PULSE, TowerType.PULSE, TowerType.MORTAR, TowerType.FROST, TowerType.PULSE,
        TowerType.ARC, TowerType.SNIPER, TowerType.MORTAR, TowerType.ARC, TowerType.FROST,
        TowerType.PULSE, TowerType.SNIPER
    };

    /** Ob der Bot selbst neue Wellen startet (für Tests mit genau einer Welle abschaltbar). */
    public boolean startWaves = true;

    private final World world;
    private final boolean upgrades;
    private final int maxTowers;
    private final TowerType[] order;
    private int buildIndex;
    private int stuck;

    /**
     * @param upgrades  ob Geld für Upgrades ausgegeben wird
     * @param maxTowers Obergrenze der Turmanzahl
     * @param order     Bauabfolge oder {@code null} für die Standardfolge
     */
    public AutoPlayer(World world, boolean upgrades, int maxTowers, TowerType[] order) {
        this.world = world;
        this.upgrades = upgrades;
        this.maxTowers = maxTowers;
        this.order = order == null ? BUILD_ORDER : order;
    }

    /** Ein "Denkschritt": startet Wellen, baut und verbessert. Mehrfach pro Sekunde aufrufen reicht völlig. */
    public void think() {
        if (world.state != World.State.RUNNING) {
            return;
        }
        if (startWaves && world.readyForNextWave()) {
            world.startNextWave();
        }
        for (int guard = 0; guard < 4; guard++) {
            if (!buyOne()) {
                break;
            }
        }
    }

    /**
     * Strategie: Die Zahl der Türme wächst mit dem Fortschritt (drei plus eine je zwei gestartete Wellen). Fehlt noch
     * ein Turm, wird dafür gespart; sonst fließt das Geld in Upgrades.
     */
    private boolean buyOne() {
        int desired = Math.min(maxTowers, world.endless ? 3 + world.waveIndex : 3 + world.waveIndex / 2);
        TowerType next = order[buildIndex % order.length];
        if (world.towers.size() < desired && stuck < 3) {
            if (world.money < next.cost) {
                return false; // sparen
            }
            double[] spot = bestSpot(next);
            if (spot != null && world.placeTower(next, spot[0], spot[1]) != null) {
                buildIndex++;
                return true;
            }
            stuck++;
        }
        if (!upgrades) {
            return false;
        }
        return upgradeOne();
    }

    /** Verbessert die günstigste sinnvolle Stufe; bevorzugt Schaden, dann Tempo, dann Reichweite. */
    private boolean upgradeOne() {
        if (world.endless) {
            return upgradeOneEndless();
        }
        Tower bestTower = null;
        UpgradeTrack bestTrack = null;
        double bestScore = -1;
        for (int i = 0; i < world.towers.size(); i++) {
            Tower t = world.towers.get(i);
            for (UpgradeTrack track : UpgradeTrack.values()) {
                int cost = world.upgradeCost(t, track);
                if (cost < 0 || cost > world.money) {
                    continue;
                }
                double value = track == UpgradeTrack.DAMAGE ? 1.0 : (track == UpgradeTrack.SPEED ? 0.9 : 0.45);
                // Gleichmäßig ausbauen: niedrige Stufen und günstige Preise zählen mehr.
                double score = value / (1 + t.level(track)) / Math.sqrt(cost);
                if (score > bestScore) {
                    bestScore = score;
                    bestTower = t;
                    bestTrack = track;
                }
            }
        }
        return bestTower != null && world.upgrade(bestTower, bestTrack);
    }

    /**
     * Endlosmodus: Die Preise der Meisterstufen wachsen exponentiell, der Zugewinn je Stufe bleibt gleich. Darum
     * zählt der relative Zugewinn pro Preis – so wird über alle Türme hinweg das günstigste Plus gekauft.
     */
    private boolean upgradeOneEndless() {
        Tower bestTower = null;
        UpgradeTrack bestTrack = null;
        double bestScore = -1;
        for (int i = 0; i < world.towers.size(); i++) {
            Tower t = world.towers.get(i);
            for (UpgradeTrack track : UpgradeTrack.values()) {
                int cost = world.upgradeCost(t, track);
                if (cost < 0 || cost > world.money) {
                    continue;
                }
                int lvl = t.level(track);
                double gain = track.multAt(lvl + 1) / track.multAt(lvl) - 1;
                double weight = track == UpgradeTrack.DAMAGE ? 1.0 : (track == UpgradeTrack.SPEED ? 0.95 : 0.3);
                double score = weight * gain / cost;
                if (score > bestScore) {
                    bestScore = score;
                    bestTower = t;
                    bestTrack = track;
                }
            }
        }
        return bestTower != null && world.upgrade(bestTower, bestTrack);
    }

    /** Position mit der größten abgedeckten Pfadlänge (auf einem 24er-Raster). */
    private double[] bestSpot(TowerType type) {
        double bestScore = 0;
        double[] best = null;
        double range = type.range;
        for (double y = 40; y < world.height - 30; y += 24) {
            for (double x = 40; x < world.width - 30; x += 24) {
                if (world.checkPlacement(x, y) != World.PlaceCheck.OK) {
                    continue;
                }
                double cover = 0;
                for (Path p : world.paths) {
                    for (int i = 0; i < p.pointCount(); i += 2) {
                        if (Mathx.distSq(x, y, p.x(i), p.y(i)) <= range * range) {
                            cover += 8;
                        }
                    }
                }
                // Etwas Abstand zu anderen Türmen, damit nicht alle an derselben Stelle kleben.
                for (int i = 0; i < world.towers.size(); i++) {
                    Tower o = world.towers.get(i);
                    if (Mathx.distSq(x, y, o.x, o.y) < 90 * 90) {
                        cover *= 0.75;
                    }
                }
                if (cover > bestScore) {
                    bestScore = cover;
                    best = new double[] {x, y};
                }
            }
        }
        return best;
    }
}
