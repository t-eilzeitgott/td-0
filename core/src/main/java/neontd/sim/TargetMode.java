package neontd.sim;

/** Zielwahl eines Turms. */
public enum TargetMode {
    FIRST("ERSTER"),
    LAST("LETZTER"),
    STRONG("STÄRKSTER"),
    CLOSE("NÄCHSTER");

    public final String label;

    TargetMode(String label) {
        this.label = label;
    }

    public TargetMode next() {
        TargetMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
