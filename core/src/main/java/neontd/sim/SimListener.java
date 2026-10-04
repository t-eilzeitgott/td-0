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

    default void onTowerSold(Tower t, int refund) { }

    default void onTowerFired(Tower t, double angle) { }

    default void onProjectileSpawned(Projectile p) { }

    default void onExplosion(double x, double y, double radius, int color) { }

    default void onNova(Tower t, double radius) { }

    default void onBeam(double x1, double y1, double x2, double y2, int color) { }

    /** Blitzkette: pts = x0,y0,x1,y1,... (nur die ersten {@code count} Punkte gültig; Array wird wiederverwendet). */
    default void onLightning(double[] pts, int count, int color) { }

    default void onGameEnded(boolean won) { }
}
