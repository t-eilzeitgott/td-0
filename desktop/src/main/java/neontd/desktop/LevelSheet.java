package neontd.desktop;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import neontd.gfx.Colors;
import neontd.gfx.Gfx;
import neontd.level.LevelDef;
import neontd.level.Levels;
import neontd.level.Zone;
import neontd.physics.Path;
import neontd.render.PathArt;
import neontd.render.WorldView;
import neontd.sim.SimListener;
import neontd.sim.World;

/**
 * Entwicklungswerkzeug: zeichnet alle Level einer Welt als Übersichtsblatt (Bahn, Zonen, mögliche Bauplätze als
 * Punkte), damit sich das Streckendesign auf einen Blick prüfen lässt.
 *
 * <pre>java neontd.desktop.LevelSheet OUT_DIR [welt]</pre>
 */
public final class LevelSheet {
    private LevelSheet() {
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "sheets");
        out.mkdirs();
        int from = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        int to = args.length > 1 ? from : Levels.CHAPTERS;
        for (int w = from; w <= to; w++) {
            List<LevelDef> levels = Levels.chapter(w);
            int cols = 2;
            int rows = (levels.size() + cols - 1) / cols;
            double cw = 640;
            double ch = 360 + 40;
            BufferedImage img = new BufferedImage((int) (cols * cw), (int) (rows * ch), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setColor(java.awt.Color.BLACK);
            g2.fillRect(0, 0, img.getWidth(), img.getHeight());
            Java2DGfx gfx = new Java2DGfx();
            gfx.begin(g2, img.getWidth(), img.getHeight());
            for (int i = 0; i < levels.size(); i++) {
                LevelDef l = levels.get(i);
                double ox = (i % cols) * cw;
                double oy = (i / cols) * ch;
                gfx.save();
                gfx.translate(ox, oy + 40);
                gfx.scale(0.5, 0.5);
                draw(gfx, l);
                gfx.restore();
                gfx.text(l.name + "  ·  " + l.id + "  ·  " + (l.style == null ? "" : l.style.label)
                        + (l.altStyle != null ? "/" + l.altStyle.label : "") + "  ·  $" + l.startMoney + "  ·  hp×" + l.hpMul
                        + "  ·  Bauplätze " + spots(l), ox + 10, oy + 20, 16, 0xFFFFFF, Gfx.ALIGN_LEFT, true);
            }
            g2.dispose();
            File f = new File(out, "welt-" + w + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("wrote " + f);
        }
    }

    static int spots(LevelDef l) {
        World w = new World(l, SimListener.NONE);
        int spots = 0;
        for (double y = 40; y < w.height - 30; y += 30) {
            for (double x = 40; x < w.width - 30; x += 30) {
                if (w.checkPlacement(x, y) == World.PlaceCheck.OK) {
                    spots++;
                }
            }
        }
        return spots;
    }

    static void draw(Gfx g, LevelDef l) {
        World w = new World(l, SimListener.NONE);
        WorldView.drawBackground(g, w.width, w.height);
        for (double y = 40; y < w.height - 30; y += 30) {
            for (double x = 40; x < w.width - 30; x += 30) {
                if (w.checkPlacement(x, y) == World.PlaceCheck.OK) {
                    g.fillCircle(x, y, 3, Colors.withAlpha(0xFFFFFF, 0.25));
                }
            }
        }
        for (Zone z : l.zones) {
            if (z.kind.pad()) {
                g.strokeCircle(z.x, z.y, z.w, 3, z.kind.color);
            } else if (z.round()) {
                g.fillCircle(z.x, z.y, z.w, Colors.withAlpha(0x3A3A52, 0.9));
            } else {
                g.fillRoundRect(z.x - z.w, z.y - z.h, z.w * 2, z.h * 2, 8, Colors.withAlpha(0x3A3A52, 0.9));
            }
        }
        for (Path p : w.paths) {
            PathArt.drawLane(g, p, 0, 1);
        }
        for (Path p : w.paths) {
            PathArt.drawEndpoints(g, p, 0, 0);
        }
        WorldView.drawFrame(g, w.width, w.height);
    }
}
