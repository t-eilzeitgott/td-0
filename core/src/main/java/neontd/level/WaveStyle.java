package neontd.level;

import neontd.sim.EnemyType;

/**
 * Wie die Wellen eines Levels zusammengesetzt sind. Jeder Stil verteilt die Last einer Welle (ihre gesamten Lebenspunkte
 * wie in der Standardkurve) auf Gegnerarten – zu Beginn des Levels nach {@link #early}, am Ende nach {@link #late}.
 * Dadurch bleibt die Schwierigkeit vergleichbar, aber jedes Level spielt sich anders: Schwärme gegen Schwergewichte,
 * Schilde und Heiler, Phantome, Titanen.
 */
public enum WaveStyle {
    //             Name           Kurzbeschreibung                                   Tempo  Welle 1 …              Welle 20
    //                                                                                     BLOCK DART MINI SPLIT TANK SHLD ARMR HEAL GHST
    KLASSIK("Klassik", "Ausgewogene Mischung aus Blöcken, Darts und Kolossen", 1.0,
            w(.55, .25, 0, .05, .15, 0, 0, 0, 0), w(.38, .2, .1, .1, .22, 0, 0, 0, 0)),
    SCHWARM("Schwarm", "Unzählige kleine, flinke Gegner – Flächenschaden lohnt sich", 0.65,
            w(.2, .3, .45, .05, 0, 0, 0, 0, 0), w(.08, .22, .6, .1, 0, 0, 0, 0, 0)),
    ANSTURM("Ansturm", "Schnelle Darts und Phantome kommen dicht an dicht", 0.5,
            w(.2, .55, .2, 0, 0, 0, 0, 0, .05), w(.08, .42, .15, 0, 0, 0, 0, 0, .35)),
    SCHWERGEWICHT("Schwergewichte", "Wenige, extrem zähe Kolosse – Einzelschaden zählt", 1.35,
            w(.35, 0, 0, .1, .55, 0, 0, 0, 0), w(.15, 0, 0, .15, .5, 0, .2, 0, 0)),
    SPLITTER("Splitter", "Raute um Raute zerfällt in Minis – ein Kettenreaktions-Level", 1.1,
            w(.2, 0, .1, .55, .15, 0, 0, 0, 0), w(.08, 0, .17, .55, .2, 0, 0, 0, 0)),
    BOLLWERK("Bollwerk", "Schildträger fangen Schaden ab – Dauerfeuer schlägt das Schild", 1.0,
            w(.3, .1, 0, 0, .2, .4, 0, 0, 0), w(.15, .05, 0, 0, .2, .55, .05, 0, 0)),
    PANZER("Panzerkolonne", "Gepanzerte Gegner halbieren schwache Treffer – Schwergewichte gefragt", 1.2,
            w(.25, 0, 0, 0, .25, .1, .4, 0, 0), w(.1, 0, 0, 0, .25, .15, .5, 0, 0)),
    LAZARETT("Lazarett", "Sanitäter heilen die Kolonne – erst die Heiler ausschalten", 1.0,
            w(.45, .1, 0, 0, .2, .05, 0, .2, 0), w(.25, .1, 0, .05, .2, .15, .05, .2, 0)),
    PHANTOME("Phantome", "Geisterhafte Gegner werden immer wieder unverwundbar", 0.9,
            w(.15, .3, .15, 0, 0, 0, 0, .1, .3), w(.05, .2, .1, 0, 0, .05, 0, .1, .5)),
    TITANEN("Titanen", "Alle fünf Wellen ein Boss – mit Geleitschutz", 1.1,
            w(.5, .1, 0, 0, .25, .05, 0, .1, 0), w(.35, .05, 0, .05, .3, .1, 0, .15, 0)),
    CHAOS("Chaos", "Jede Welle betont eine andere Gegnerart – nichts ist vorhersehbar", 0.9,
            w(.2, .15, .1, .1, .15, .1, .1, .05, .05), w(.15, .1, .1, .12, .15, .12, .12, .07, .07));

    /** Reihenfolge der Gewichte: {@link #TYPES}. */
    public static final EnemyType[] TYPES = {EnemyType.BLOCK, EnemyType.DART, EnemyType.MINI, EnemyType.SPLIT,
        EnemyType.TANK, EnemyType.SHIELD, EnemyType.ARMORED, EnemyType.HEALER, EnemyType.GHOST};

    public final String label;
    public final String blurb;
    /** Faktor auf die Abstände zwischen zwei Gegnern (kleiner = dichter). */
    public final double pace;
    public final double[] early;
    public final double[] late;

    WaveStyle(String label, String blurb, double pace, double[] early, double[] late) {
        this.label = label;
        this.blurb = blurb;
        this.pace = pace;
        this.early = early;
        this.late = late;
    }

    private static double[] w(double... v) {
        return v;
    }
}
