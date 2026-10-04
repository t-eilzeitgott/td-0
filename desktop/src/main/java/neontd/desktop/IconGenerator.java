package neontd.desktop;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import neontd.fx.Effects;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.gfx.Neon;
import neontd.gfx.Theme;
import neontd.render.PathArt;
import neontd.render.TowerArt;
import neontd.sim.TowerType;

/** Entwicklungswerkzeug: zeichnet das App-Icon (Neon-Turm vor einer Bahn mit Feuerwerk) in mehreren Größen. */
public final class IconGenerator {
    private IconGenerator() {
    }

    private static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, size, size);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Java2DGfx.exactAdditive = true;
        Java2DGfx g = new Java2DGfx();
        g.begin(g2, size, size);
        Neon.quality = 2;
        double s = size / 512.0;
        g.scale(s, s);

        // Bahn: geschwungenes Band von links unten nach rechts
        g.beginPath();
        g.moveTo(-40, 430);
        for (int i = 1; i <= 60; i++) {
            double t = i / 60.0;
            g.lineTo(-40 + t * 600, 430 - Math.sin(t * Math.PI) * 150 - t * 30);
        }
        g.additive(true);
        g.stroke(112, Colors.withAlpha(PathArt.LANE, 0.10));
        g.additive(false);
        g.stroke(88, Colors.withAlpha(PathArt.LANE, 0.95));
        g.stroke(78, Theme.LANE_FILL);
        g.strokeDashed(4, Colors.withAlpha(PathArt.LANE, 0.6), 22, 40, 0);

        // Turm in der Mitte oben
        g.save();
        g.translate(190, 190);
        Neon.circle(g, 0, 0, 150, 3.5, Colors.withAlpha(Theme.CYAN, 0.35), 14);
        g.restore();
        TowerArt.drawIcon(g, TowerType.PULSE, 190, 190, 112, 0);

        // Feuerwerk rechts oben
        Effects fx = new Effects();
        fx.firework(380, 150, Theme.enemyColor(40), 2.4);
        fx.firework(430, 300, Theme.enemyColor(900), 1.6);
        for (int i = 0; i < 9; i++) {
            fx.update(1.0 / 60);
        }
        fx.render(g);

        // Gegner mit Zahl auf der Bahn
        g.save();
        g.translate(150, 360);
        g.rotate(0.35);
        g.beginPath();
        g.roundRect(-34, -34, 68, 68, 18);
        g.fill(Colors.withAlpha(0xFF9A00, 0.22));
        Neon.stroke(g, 6, 0xFF9A00, 16);
        g.restore();
        g.text("9", 150, 361, 48, Theme.TEXT, Gfx.ALIGN_CENTER, true);
        g2.dispose();
        return img;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "icons");
        out.mkdirs();
        int[][] targets = {{512, 0}, {192, 0}, {180, 0}, {32, 0}};
        String[] names = {"icon-512.png", "icon-192.png", "apple-touch-icon.png", "favicon.png"};
        for (int i = 0; i < targets.length; i++) {
            BufferedImage img = render(targets[i][0]);
            File f = new File(out, names[i]);
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f);
        }
    }
}
