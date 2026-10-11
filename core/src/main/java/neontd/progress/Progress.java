package neontd.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import neontd.math.Mathx;
import neontd.sim.Perks;
import neontd.sim.Special;
import neontd.sim.TowerType;

/**
 * Das Spielerprofil: Erfahrung (XP), Spielerlevel, Statistiken und was daraus folgt – freigeschaltete Türme,
 * Startbonus und Titel. Reine Daten und Rechenregeln, ohne Bildschirm und Speicher (siehe {@code ProfileJson}
 * und {@code SaveStore} für das Ablegen).
 *
 * <p>XP gibt es für jede besiegte Welle (mehr bei Titan-Wellen) und einmalig für den ersten Sieg über ein Level.
 * Die Kurve ist so gewählt, dass der erste vollständige Durchgang Level 4 bringt (Frost und Mörser) und
 * 1000 Endlos-Wellen Level 63.
 */
public final class Progress {
    public static final int MAX_LEVEL = 100;
    /** XP-Obergrenze: 9·10¹⁵ – XP sind ein double mit ganzen Zahlen, das bis 2⁵³ ≈ 9,007·10¹⁵ exakt rechnet. */
    public static final double MAX_XP = 9_000_000_000_000_000.0;
    /** Obergrenze der int-Zähler (Abschüsse, Wellen, Spiele). */
    public static final int MAX_COUNT = 2_000_000_000;
    public static final int MAX_NAME = 16;
    /** Einmaliger Bonus für den ersten Sieg über ein Level. */
    public static final int FIRST_WIN_XP = 300;
    public static final int WIN_XP = 100;
    /** Kristalle für jeden Level-Aufstieg. */
    public static final int LEVEL_UP_CRYSTALS = 5;
    /** Kristalle je Medaillenstufe (Bronze … Platin), einmalig je Level. */
    public static final int[] MEDAL_CRYSTALS = {6, 14, 30, 70};
    public static final int WIN_CRYSTALS_FIRST = 10;
    public static final int WIN_CRYSTALS = 3;

    /** Anzeigename; leer = "SPIELER". */
    public String name = "";
    public double xp;
    public int kills;
    public int wavesCleared;
    public int gamesPlayed;
    public int gamesWon;
    /** Levelkennungen, die mindestens einmal gewonnen wurden. */
    public final Set<String> won = new TreeSet<>();
    /** Je Level: weiteste besiegte Welle im normalen Spiel. */
    public final Map<String, Integer> bestWave = new TreeMap<>();
    /** Je Level: weiteste besiegte Welle im Endlosmodus. */
    public final Map<String, Integer> bestEndless = new TreeMap<>();
    /** Zeitpunkt der letzten Änderung (Millisekunden seit 1970) – Grundlage für das Zusammenführen mit der Cloud. */
    public double updated;

    // ---- Shop, Errungenschaften, Aufgaben, Season (siehe Abschnitt "Meta") ----
    /** Alle je verdienten Kristalle; das Guthaben ist dies minus der Preise aller gekauften Dinge. */
    public double crystalsEarned;
    public final Set<String> ownedTowers = new TreeSet<>();
    public final Set<String> ownedSpecials = new TreeSet<>();
    public final Map<String, Integer> perkLevels = new TreeMap<>();
    public final Set<String> achievements = new TreeSet<>();
    /** Lebenslange Zähler (Titanen, Elite-Gegner, Türme, Upgrades, Spezials, Tagesaufgaben, Season-Stufen …). */
    public final Map<String, Double> stats = new TreeMap<>();
    /** Tageszahl (seit 1970), für die die Tagesaufgaben gelten, samt Fortschritt und Abholmarken. */
    public int dailyDay = -1;
    public final int[] dailyProgress = new int[Quests.DAILY];
    public final boolean[] dailyClaimed = new boolean[Quests.DAILY];
    public boolean dailyBonusClaimed;
    /** Monats-Kennung der laufenden Season, Season-XP, abgeholte Stufen und Monatsaufgaben. */
    public int seasonId = -1;
    public int seasonXp;
    public final boolean[] seasonClaimed = new boolean[Season.TIERS + 1];
    public final int[] monthlyProgress = new int[Quests.MONTHLY];
    public final boolean[] monthlyClaimed = new boolean[Quests.MONTHLY];
    /** Zeitpunkt des letzten Zurücksetzens (ms): Der Stand mit dem jüngeren Zurücksetzen gewinnt beim Zusammenführen. */
    public double resetEpoch;

    /** Übernimmt alle Werte eines anderen Profils (z. B. nach dem Zusammenführen mit der Cloud). */
    public void copyFrom(Progress o) {
        name = o.name;
        xp = o.xp;
        kills = o.kills;
        wavesCleared = o.wavesCleared;
        gamesPlayed = o.gamesPlayed;
        gamesWon = o.gamesWon;
        won.clear();
        won.addAll(o.won);
        bestWave.clear();
        bestWave.putAll(o.bestWave);
        bestEndless.clear();
        bestEndless.putAll(o.bestEndless);
        updated = o.updated;
        crystalsEarned = o.crystalsEarned;
        ownedTowers.clear();
        ownedTowers.addAll(o.ownedTowers);
        ownedSpecials.clear();
        ownedSpecials.addAll(o.ownedSpecials);
        perkLevels.clear();
        perkLevels.putAll(o.perkLevels);
        achievements.clear();
        achievements.addAll(o.achievements);
        stats.clear();
        stats.putAll(o.stats);
        dailyDay = o.dailyDay;
        System.arraycopy(o.dailyProgress, 0, dailyProgress, 0, dailyProgress.length);
        System.arraycopy(o.dailyClaimed, 0, dailyClaimed, 0, dailyClaimed.length);
        dailyBonusClaimed = o.dailyBonusClaimed;
        seasonId = o.seasonId;
        seasonXp = o.seasonXp;
        System.arraycopy(o.seasonClaimed, 0, seasonClaimed, 0, seasonClaimed.length);
        System.arraycopy(o.monthlyProgress, 0, monthlyProgress, 0, monthlyProgress.length);
        System.arraycopy(o.monthlyClaimed, 0, monthlyClaimed, 0, monthlyClaimed.length);
        resetEpoch = o.resetEpoch;
    }

    // ---------------------------------------------------------------------------------- Level und XP

    /** XP, die für den Aufstieg von {@code level} auf {@code level + 1} nötig sind. */
    public static int xpForNext(int level) {
        int l = Math.max(1, level) - 1;
        return 120 + 40 * l + 6 * l * l;
    }

    /** Gesamt-XP, ab der {@code level} erreicht ist (Level 1 = 0). */
    public static int xpAtLevel(int level) {
        int total = 0;
        for (int l = 1; l < Math.min(level, MAX_LEVEL); l++) {
            total += xpForNext(l);
        }
        return total;
    }

    public static int levelForXp(double xp) {
        int level = 1;
        double left = xp;
        while (level < MAX_LEVEL && left >= xpForNext(level)) {
            left -= xpForNext(level);
            level++;
        }
        return level;
    }

    public int level() {
        return levelForXp(xp);
    }

    /** XP innerhalb des aktuellen Levels. */
    public double xpIntoLevel() {
        return xp - xpAtLevel(level());
    }

    /** XP, die für das nächste Level insgesamt nötig sind (0 bei Höchststufe). */
    public int xpSpan() {
        int l = level();
        return l >= MAX_LEVEL ? 0 : xpForNext(l);
    }

    /** Fortschritt im aktuellen Level, 0..1. */
    public double levelFraction() {
        int span = xpSpan();
        return span == 0 ? 1 : Math.min(1, xpIntoLevel() / span);
    }

    /** Fügt XP hinzu. @return Anzahl der dabei aufgestiegenen Level */
    public int addXp(double amount) {
        if (amount <= 0) {
            return 0;
        }
        int before = level();
        xp = Math.min(MAX_XP, xp + amount);
        int gained = level() - before;
        if (gained > 0) {
            addCrystals(LEVEL_UP_CRYSTALS * gained);
        }
        return gained;
    }

    // --------------------------------------------------------------------------------- Spielereignisse

    /** XP für eine besiegte Welle (1-basiert): 6 + Wellennummer, doppelt bei Titan-Wellen. */
    public static int waveXp(int wave, boolean boss) {
        int base = 6 + wave;
        return boss ? base * 2 : base;
    }

    /** Verbucht eine besiegte Welle (normales Spiel). @return erhaltene XP */
    public int onWaveCleared(int wave, boolean boss) {
        return onWaveCleared(wave, boss, false);
    }

    /**
     * Verbucht eine besiegte Welle: XP (mit dem Bonus aus der Forschung), Kristalle, Aufgaben-Fortschritt und
     * Season-XP. @return erhaltene XP
     */
    public int onWaveCleared(int wave, boolean boss, boolean endless) {
        int gain = Mathx.roundToInt(waveXp(wave, boss) * (1 + 0.03 * perkLevel(Perk.XP)));
        wavesCleared++;
        addXp(gain);
        earnCrystals(waveCrystals(wave, boss));
        addMetric(Metric.WAVES, 1);
        if (endless) {
            addMetric(Metric.ENDLESS_WAVES, 1);
        }
        double w = stat("seasonwaves") + 1;
        if (w >= Season.WAVES_PER_XP) {
            w = 0;
            addSeasonXp(1);
        }
        stats.put("seasonwaves", w);
        return gain;
    }

    /** Grundwert der Kristalle für eine besiegte Welle: 1 + Welle/25 (höchstens +5), Titan-Wellen +3. */
    public static int waveCrystals(int wave, boolean boss) {
        return 1 + Math.min(5, wave / 25) + (boss ? 3 : 0);
    }

    /** Verbucht den Sieg über ein Level. @return erhaltene XP */
    public int onWon(String levelId) {
        gamesWon++;
        boolean first = won.add(levelId);
        int gain = Mathx.roundToInt((WIN_XP + (first ? FIRST_WIN_XP : 0)) * (1 + 0.03 * perkLevel(Perk.XP)));
        addXp(gain);
        earnCrystals(first ? WIN_CRYSTALS_FIRST : WIN_CRYSTALS);
        addMetric(Metric.WINS, 1);
        return gain;
    }

    /** Ein Level wurde begonnen (zählt für Statistik und Aufgaben, nicht beim Fortsetzen). */
    public void onPlayStarted() {
        gamesPlayed++;
        addMetric(Metric.PLAYS, 1);
    }

    /** Neue Medaillenstufen eines Levels: einmalige Kristalle (Bronze 6 … Platin 70). @return ihre Summe */
    public int onMedals(int from, int to) {
        int sum = 0;
        for (int i = Math.max(0, from); i < to && i < MEDAL_CRYSTALS.length; i++) {
            sum += MEDAL_CRYSTALS[i];
        }
        return sum > 0 ? earnCrystals(sum) : 0;
    }

    public void onRecord(String levelId, int cleared, boolean endless) {
        Map<String, Integer> map = endless ? bestEndless : bestWave;
        Integer old = map.get(levelId);
        if (old == null || cleared > old) {
            map.put(levelId, cleared);
        }
    }

    public int best(String levelId, boolean endless) {
        Integer v = (endless ? bestEndless : bestWave).get(levelId);
        return v == null ? 0 : v;
    }

    /** Weiteste besiegte Welle eines Levels – Normalspiel und Endlosmodus zusammen. */
    public int bestOverall(String levelId) {
        return Math.max(best(levelId, false), best(levelId, true));
    }

    /** Medaillenstufe eines Levels: 0 = keine, 1 = Bronze … 4 = Platin (siehe {@link Medals}). */
    public int medal(String levelId) {
        return Medals.tier(bestOverall(levelId));
    }

    /** Alle Medaillen über alle Level (Bronze, Silber, Gold, Platin zählen je eine). */
    public int totalMedals() {
        Set<String> ids = new TreeSet<>(bestWave.keySet());
        ids.addAll(bestEndless.keySet());
        int n = 0;
        for (String id : ids) {
            n += medal(id);
        }
        return n;
    }

    /**
     * Ist das Level spielbar? Das allererste eingebaute Level und eigene Level immer; sonst, sobald im Vorgänger
     * Bronze erreicht ist (siehe {@link neontd.level.Levels#prerequisite}) oder das Level schon einmal gespielt wurde.
     */
    public boolean levelUnlocked(String levelId) {
        neontd.level.LevelDef l = neontd.level.Levels.find(levelId);
        if (l == null) {
            return true;
        }
        neontd.level.LevelDef need = neontd.level.Levels.prerequisite(l);
        return need == null || medal(need.id) >= 1 || bestOverall(levelId) > 0;
    }

    /** Anzahl Level eines Kapitels mit mindestens dieser Medaillenstufe (1 = Bronze). */
    public int chapterMedals(int chapter, int minTier) {
        int n = 0;
        for (neontd.level.LevelDef l : neontd.level.Levels.chapter(chapter)) {
            if (medal(l.id) >= minTier) {
                n++;
            }
        }
        return n;
    }

    /** Ein Kapitel ist offen, sobald sein erstes Level spielbar ist. */
    public boolean chapterUnlocked(int chapter) {
        return levelUnlocked(neontd.level.Levels.chapter(chapter).get(0).id);
    }

    /** Der Endlosmodus eines Levels ist nach dem ersten Sieg offen. */
    public boolean endlessUnlocked(String levelId) {
        return won.contains(levelId);
    }

    // --------------------------------------------------------------------------------------- Freischaltungen

    /** Darf der Turm gebaut werden? Grundtürme ab ihrem Spielerlevel, Elite-Türme erst nach dem Kauf im Shop. */
    public boolean isUnlocked(TowerType type) {
        return type.tier == 1 ? level() >= type.unlockLevel : ownedTowers.contains(type.name());
    }

    /** Türme, die genau mit diesem Level freigeschaltet werden. */
    public static TowerType[] unlockedAt(int level) {
        int n = 0;
        for (TowerType t : TowerType.values()) {
            if (t.tier == 1 && t.unlockLevel == level) {
                n++;
            }
        }
        TowerType[] r = new TowerType[n];
        int i = 0;
        for (TowerType t : TowerType.values()) {
            if (t.tier == 1 && t.unlockLevel == level) {
                r[i++] = t;
            }
        }
        return r;
    }

    /** Das nächste noch gesperrte Turm-Level oder 0, wenn alles frei ist. */
    public int nextUnlockLevel() {
        int lvl = level();
        int best = 0;
        for (TowerType t : TowerType.values()) {
            if (t.tier == 1 && t.unlockLevel > lvl && (best == 0 || t.unlockLevel < best)) {
                best = t.unlockLevel;
            }
        }
        return best;
    }

    /** Zusätzliches Startgeld: 10 je Level über 1, höchstens 300. */
    public int startMoneyBonus() {
        return Math.min(300, 10 * (level() - 1)) + 40 * perkLevel(Perk.START_MONEY);
    }

    /** Zusätzliche Start-Leben: eines je fünf Level, höchstens fünf. */
    public int startLivesBonus() {
        return Math.min(5, level() / 5) + perkLevel(Perk.LIVES);
    }

    // ------------------------------------------------------------------------------------------------ Meta

    /** Ergebnis eines Kaufs im Shop. */
    public enum Buy { OK, LEVEL, FUNDS, OWNED, MAX, INVALID }

    public double stat(String key) {
        Double v = stats.get(key);
        return v == null ? 0 : v;
    }

    private void addStat(String key, double n) {
        stats.put(key, Math.min(Mathx.MAX_SAFE, stat(key) + n));
    }

    public int perkLevel(Perk p) {
        Integer v = perkLevels.get(p.key());
        return v == null ? 0 : v;
    }

    // ---- Kristalle

    /** Preise aller gekauften Türme, Spezials und Forschungsstufen zusammen. */
    public double spentCrystals() {
        double sum = 0;
        for (TowerType t : TowerType.values()) {
            if (t.tier == 2 && ownedTowers.contains(t.name())) {
                sum += t.crystals;
            }
        }
        for (Special sp : Special.values()) {
            if (ownedSpecials.contains(sp.key)) {
                sum += sp.price;
            }
        }
        for (Perk p : Perk.values()) {
            sum += p.totalCost(perkLevel(p));
        }
        return sum;
    }

    /** Aktuelles Guthaben. */
    public int crystals() {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.floor(crystalsEarned - spentCrystals())));
    }

    /** Schreibt Kristalle gut (ohne Boni). @return die gutgeschriebene Menge */
    public int addCrystals(double amount) {
        if (amount <= 0) {
            return 0;
        }
        crystalsEarned = Math.min(Mathx.MAX_SAFE, crystalsEarned + amount);
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }

    /** Kristalle aus dem Spiel (Wellen, Siege, Medaillen) mit dem Bonus der Forschung. */
    public int earnCrystals(int base) {
        return addCrystals(Math.max(1, Mathx.roundToInt(base * (1 + 0.03 * perkLevel(Perk.CRYSTALS)))));
    }

    // ---- Käufe

    public boolean owns(Special s) {
        return ownedSpecials.contains(s.key);
    }

    public Buy buyTower(TowerType t) {
        if (t.tier != 2) {
            return Buy.INVALID;
        }
        if (ownedTowers.contains(t.name())) {
            return Buy.OWNED;
        }
        if (level() < t.unlockLevel) {
            return Buy.LEVEL;
        }
        if (crystals() < t.crystals) {
            return Buy.FUNDS;
        }
        ownedTowers.add(t.name());
        return Buy.OK;
    }

    public Buy buySpecial(Special s) {
        if (owns(s)) {
            return Buy.OWNED;
        }
        if (level() < s.needLevel) {
            return Buy.LEVEL;
        }
        if (crystals() < s.price) {
            return Buy.FUNDS;
        }
        ownedSpecials.add(s.key);
        return Buy.OK;
    }

    public Buy buyPerk(Perk p) {
        int lv = perkLevel(p);
        if (lv >= p.maxLevel) {
            return Buy.MAX;
        }
        if (level() < p.needLevel) {
            return Buy.LEVEL;
        }
        if (crystals() < p.cost(lv)) {
            return Buy.FUNDS;
        }
        perkLevels.put(p.key(), lv + 1);
        return Buy.OK;
    }

    /** Die Boni aus der Forschung als Beiwerk für die Spielwelt. */
    public Perks perks() {
        Perks r = new Perks();
        r.damage = 1 + 0.01 * perkLevel(Perk.DAMAGE);
        r.range = 1 + 0.01 * perkLevel(Perk.RANGE);
        r.buildCost = 1 - 0.015 * perkLevel(Perk.DISCOUNT);
        r.sell = 0.015 * perkLevel(Perk.SELL);
        r.bounty = 1 + 0.02 * perkLevel(Perk.BOUNTY);
        r.waveBonus = 1 + 0.03 * perkLevel(Perk.WAVE_BONUS);
        return r;
    }

    // ---- Zähler, Aufgaben, Season

    /** Zählt ein Ereignis für Statistik, Tages- und Monatsaufgaben. */
    public void addMetric(Metric m, int n) {
        if (n <= 0) {
            return;
        }
        switch (m) {
            case KILLS:
                kills = (int) Math.min(MAX_COUNT, (double) kills + n);
                break;
            case BOSSES:
            case ELITES:
            case BUILDS:
            case UPGRADES:
            case SPECIALS:
                addStat(m.key(), n);
                break;
            default:
                break; // WAVES, ENDLESS_WAVES, WINS, PLAYS: die Zähler führen onWaveCleared/onWon/onPlayStarted
        }
        if (dailyDay >= 0) {
            Quest[] d = Quests.daily(dailyDay);
            for (int i = 0; i < d.length; i++) {
                if (d[i].metric == m && !dailyClaimed[i]) {
                    dailyProgress[i] = (int) Math.min(d[i].target, (double) dailyProgress[i] + n);
                }
            }
        }
        if (seasonId >= 0) {
            Quest[] mo = Quests.monthly(seasonId);
            for (int i = 0; i < mo.length; i++) {
                if (mo[i].metric == m && !monthlyClaimed[i]) {
                    monthlyProgress[i] = (int) Math.min(mo[i].target, (double) monthlyProgress[i] + n);
                }
            }
        }
    }

    /**
     * Stellt die Tages- und Monatsaufgaben auf den Tag {@code day} (Tageszahl seit 1970) um: neuer Tag = neue
     * Aufgaben, neuer Monat = neue Season. Rückwärts springende Uhren werden ignoriert. @return ob sich etwas geändert hat
     */
    public boolean refreshPeriods(int day) {
        boolean changed = false;
        if (day > dailyDay) {
            dailyDay = day;
            java.util.Arrays.fill(dailyProgress, 0);
            java.util.Arrays.fill(dailyClaimed, false);
            dailyBonusClaimed = false;
            changed = true;
        }
        int month = Calendar.monthId(day);
        if (month > seasonId) {
            seasonId = month;
            seasonXp = 0;
            java.util.Arrays.fill(seasonClaimed, false);
            java.util.Arrays.fill(monthlyProgress, 0);
            java.util.Arrays.fill(monthlyClaimed, false);
            changed = true;
        }
        return changed;
    }

    public Quest[] dailyQuests() {
        return Quests.daily(Math.max(0, dailyDay));
    }

    public Quest[] monthlyQuests() {
        return Quests.monthly(Math.max(0, seasonId));
    }

    public boolean dailyReady(int i) {
        return dailyDay >= 0 && !dailyClaimed[i] && dailyProgress[i] >= dailyQuests()[i].target;
    }

    public boolean monthlyReady(int i) {
        return seasonId >= 0 && !monthlyClaimed[i] && monthlyProgress[i] >= monthlyQuests()[i].target;
    }

    /** Holt eine Tagesaufgabe ab. @return gutgeschriebene Kristalle (inklusive Tagesbonus) oder 0 */
    public int claimDaily(int i) {
        if (!dailyReady(i)) {
            return 0;
        }
        Quest q = dailyQuests()[i];
        dailyClaimed[i] = true;
        int got = addCrystals(q.crystals);
        addXp(q.xp);
        addSeasonXp(q.seasonXp);
        addStat("dailies", 1);
        boolean all = true;
        for (boolean c : dailyClaimed) {
            all &= c;
        }
        if (all && !dailyBonusClaimed) {
            dailyBonusClaimed = true;
            got += addCrystals(Quests.DAILY_BONUS_CRYSTALS);
            addSeasonXp(Quests.DAILY_BONUS_SEASON_XP);
        }
        return got;
    }

    public int claimMonthly(int i) {
        if (!monthlyReady(i)) {
            return 0;
        }
        Quest q = monthlyQuests()[i];
        monthlyClaimed[i] = true;
        int got = addCrystals(q.crystals);
        addXp(q.xp);
        addSeasonXp(q.seasonXp);
        return got;
    }

    public void addSeasonXp(int n) {
        if (n > 0) {
            seasonXp = Math.min(Season.MAX_XP, seasonXp + n);
        }
    }

    public int seasonTier() {
        return Season.tierFor(seasonXp);
    }

    public boolean seasonReady(int tier) {
        return tier >= 1 && tier <= Season.TIERS && tier <= seasonTier() && !seasonClaimed[tier];
    }

    /** Holt die Belohnung einer Season-Stufe ab. @return Kristalle oder 0 */
    public int claimSeason(int tier) {
        if (!seasonReady(tier)) {
            return 0;
        }
        seasonClaimed[tier] = true;
        addStat("seasontiers", 1);
        if (Season.milestone(tier) != null) {
            stats.put("badge" + tier / 10, 1.0);
        }
        return addCrystals(Season.crystals(tier));
    }

    /** Wie viele Belohnungen warten auf Abholung (Tages-, Monatsaufgaben, Season-Stufen)? */
    public int claimable() {
        int n = 0;
        for (int i = 0; i < Quests.DAILY; i++) {
            n += dailyReady(i) ? 1 : 0;
        }
        for (int i = 0; i < Quests.MONTHLY; i++) {
            n += monthlyReady(i) ? 1 : 0;
        }
        for (int t = 1; t <= seasonTier(); t++) {
            n += seasonClaimed[t] ? 0 : 1;
        }
        return n;
    }

    /** Der höchste Season-Titel, den man je erreicht hat (Stufe 10/20/30), oder {@code null}. */
    public String seasonBadge() {
        for (int k = 3; k >= 1; k--) {
            if (stat("badge" + k) > 0) {
                return Season.milestone(k * 10);
            }
        }
        return null;
    }

    // ---- Errungenschaften

    public double achievementValue(Achievements.Kind k) {
        switch (k) {
            case KILLS: return kills;
            case WAVES: return wavesCleared;
            case BOSSES: return stat("bosses");
            case ELITES: return stat("elites");
            case BUILDS: return stat("builds");
            case UPGRADES: return stat("upgrades");
            case SPECIALS_BUILT: return stat("specials");
            case WINS: return gamesWon;
            case MEDALS: return totalMedals();
            case PLATINUM: {
                int n = 0;
                for (neontd.level.LevelDef l : neontd.level.Levels.builtins()) {
                    n += medal(l.id) >= 4 ? 1 : 0;
                }
                return n;
            }
            case CHAPTERS: {
                int n = 0;
                for (int c = 1; c <= neontd.level.Levels.CHAPTERS; c++) {
                    n += chapterMedals(c, 1) >= neontd.level.Levels.PER_CHAPTER ? 1 : 0;
                }
                return n;
            }
            case BEST_ENDLESS: {
                int best = 0;
                for (int v : bestEndless.values()) {
                    best = Math.max(best, v);
                }
                return best;
            }
            case PLAYER_LEVEL: return level();
            case TOWERS_OWNED: return ownedTowers.size();
            case SPECIALS_OWNED: return ownedSpecials.size();
            case PERK_LEVELS: {
                int n = 0;
                for (Perk p : Perk.values()) {
                    n += perkLevel(p);
                }
                return n;
            }
            case DAILIES: return stat("dailies");
            case SEASON_TIERS: return stat("seasontiers");
            default: return crystalsEarned;
        }
    }

    /** Erreichte Stufen einer Errungenschaften-Familie. */
    public int achievementTiers(Achievements.Family f) {
        int n = 0;
        for (int t = 0; t < f.tiers(); t++) {
            n += achievements.contains(f.id(t)) ? 1 : 0;
        }
        return n;
    }

    /**
     * Prüft alle Errungenschaften und schreibt neu erreichte Stufen mit ihren Kristallen gut (auch Folgestufen, die
     * dadurch erreicht werden). @return Meldungen wie "ABSCHUSS-SERIE · Besiege 100 Gegner (+10)" für die Anzeige
     */
    public List<String> checkAchievements() {
        List<String> out = new ArrayList<>();
        boolean again = true;
        while (again) {
            again = false;
            for (Achievements.Family f : Achievements.FAMILIES) {
                double v = achievementValue(f.kind);
                for (int t = 0; t < f.tiers(); t++) {
                    if (v >= f.targets[t] && achievements.add(f.id(t))) {
                        addCrystals(f.rewards[t]);
                        out.add(f.name + " · " + f.describe(t) + " (+" + f.rewards[t] + ")");
                        again = true;
                    }
                }
            }
        }
        return out;
    }

    /**
     * Für Spielstände aus der Zeit vor dem Shop: schreibt einmalig Kristalle für das bisher Erreichte gut (Level,
     * Medaillen, erste Siege), damit alte Spieler nicht bei null anfangen. @return ob etwas gutgeschrieben wurde
     */
    public boolean grantLegacyCrystals() {
        if (stats.containsKey("legacy")) {
            return false;
        }
        stats.put("legacy", 1.0);
        double sum = LEVEL_UP_CRYSTALS * (level() - 1) + WIN_CRYSTALS_FIRST * won.size();
        Set<String> ids = new TreeSet<>(bestWave.keySet());
        ids.addAll(bestEndless.keySet());
        for (String id : ids) {
            for (int i = 0; i < medal(id) && i < MEDAL_CRYSTALS.length; i++) {
                sum += MEDAL_CRYSTALS[i];
            }
        }
        addCrystals(sum);
        return sum > 0;
    }

    // ---- Zurücksetzen

    /**
     * Setzt Spielerlevel und XP zurück (alles andere bleibt). Der Zeitpunkt sorgt dafür, dass der Cloud-Abgleich den
     * zurückgesetzten Stand übernimmt und nicht den alten wiederherstellt.
     */
    public void resetPlayerLevel(double nowMillis) {
        xp = 0;
        markReset(nowMillis);
    }

    /** Setzt das ganze Profil zurück (der Name bleibt): Level, Medaillen, Shop, Errungenschaften, Aufgaben. */
    public void resetAll(double nowMillis) {
        String keep = name;
        double epoch = resetEpoch;
        copyFrom(new Progress());
        name = keep;
        resetEpoch = epoch;
        markReset(nowMillis);
    }

    private void markReset(double nowMillis) {
        resetEpoch = Math.max(nowMillis, resetEpoch + 1);
        updated = Math.max(updated, nowMillis);
    }

    // ------------------------------------------------------------------------------------------- Titel

    private static final int[] TITLE_FROM = {1, 3, 6, 10, 15, 25, 40, 60, 80, 100};
    private static final String[] TITLES = {
        "REKRUT", "FUNKE", "PULSGEBER", "WÄCHTER", "STRATEGE", "ARCHITEKT", "MEISTER", "LEGENDE", "TITANBEZWINGER",
        "NEON-GOTT"
    };

    public static String titleFor(int level) {
        String t = TITLES[0];
        for (int i = 0; i < TITLE_FROM.length; i++) {
            if (level >= TITLE_FROM[i]) {
                t = TITLES[i];
            }
        }
        return t;
    }

    public String title() {
        return titleFor(level());
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? "SPIELER" : name.trim();
    }

    /** Bereinigt einen eingegebenen Namen: höchstens {@link #MAX_NAME} Zeichen, ohne Steuerzeichen und Ränder. */
    public static String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length() && sb.length() < MAX_NAME; i++) {
            char c = raw.charAt(i);
            if (c >= 32 && c != 127 && c != '<' && c != '>') {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
