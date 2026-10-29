package neontd.platform;

/** Stand „Zum Home-Bildschirm“ im Browser (für die Installations-Seite vor dem ersten Spielen). */
public final class InstallInfo {
    /** Gerätefamilie: iOS (Anleitung über „Teilen“), Android (Dialog oder Menü), Desktop (kein Hinweis). */
    public enum Kind { IOS, ANDROID, DESKTOP }

    /** Läuft schon als installierte App (vom Home-Bildschirm gestartet)? */
    public final boolean standalone;
    public final Kind kind;
    /** iOS in einem Browser ohne „Zum Home-Bildschirm“ (In-App-Browser von Instagram, Facebook …). */
    public final boolean iosNoShare;
    /** iOS-Browser außer Safari (Teilen sitzt oben in der Adressleiste). */
    public final boolean iosOtherBrowser;
    /** Der Installations-Dialog des Browsers kann per Knopf geöffnet werden (Android/Chromium). */
    public final boolean canPrompt;
    /** Gerade installiert – jetzt vom Home-Bildschirm öffnen. */
    public final boolean installed;

    public InstallInfo(boolean standalone, Kind kind, boolean iosNoShare, boolean iosOtherBrowser, boolean canPrompt,
                       boolean installed) {
        this.standalone = standalone;
        this.kind = kind;
        this.iosNoShare = iosNoShare;
        this.iosOtherBrowser = iosOtherBrowser;
        this.canPrompt = canPrompt;
        this.installed = installed;
    }

    /** Aus der Kurzform des Browsers: "standalone|ios|noShare|other|canPrompt|installed" mit 0/1. */
    public static InstallInfo parse(String s) {
        String[] p = s == null ? new String[0] : s.split("\\|");
        if (p.length < 6) {
            return null;
        }
        Kind k = "ios".equals(p[1]) ? Kind.IOS : ("android".equals(p[1]) ? Kind.ANDROID : Kind.DESKTOP);
        return new InstallInfo("1".equals(p[0]), k, "1".equals(p[2]), "1".equals(p[3]), "1".equals(p[4]), "1".equals(p[5]));
    }
}
