package neontd.sim;

/**
 * Ereignisse der Simulation. Die Darstellung (Partikel, Töne, UI) hängt sich hier ein, die Simulation selbst
 * weiß nichts davon. Alle Methoden sind optional.
 */
public interface SimListener {
    SimListener NONE = new SimListener() { };

    default void onWaveStarted(int index, WaveDef wave) { }

    default void onWaveCleared(int index, int bonus) { }

    default void onEnemySpawned(Enemy e) { }

    default void onEnemyDamaged(Enemy e, int damage) { }

    default void onEnemyKilled(Enemy e, int reward) { }

    default void onEnemyLeaked(Enemy e, int livesLost) { }

    default void onTowerPlaced(Tower t) { }

    default void onTowerUpgraded(Tower t, UpgradeTrack track) { }

    default void onTowerSold(Tower t, double refund) { }

    default void onSpecialSet(Tower t, Special special) { }

    default void onBranchBought(Tower t, Branch branch, int rank) { }

    /** Arena: Welle besiegt – {@code points} Wellenpunkte, mit Vorlauf {@code early}, Serie danach {@code streak}. */
    default void onArenaWave(int index, long points, boolean early, int streak) { }

    /** Arena: Ein Gegner kam durch und hat eine Serie von {@code streak} Wellen beendet. */
    default void onStreakBroken(int streak) { }

    /** Eine Münze hat Geld erzeugt. */
    default void onTowerIncome(Tower t, double amount) { }

    default void onTowerFired(Tower t, double angle) { }

    default void onProjectileSpawned(Projectile p) { }

    default void onExplosion(double x, double y, double radius, int color) { }

    default void onNova(Tower t, double radius) { }

    default void onBeam(double x1, double y1, double x2, double y2, int color) { }

    /** Blitzkette: pts = x0,y0,x1,y1,... (nur die ersten {@code count} Punkte gültig; Array wird wiederverwendet). */
    default void onLightning(double[] pts, int count, int color) { }

    default void onGameEnded(boolean won) { }
}
