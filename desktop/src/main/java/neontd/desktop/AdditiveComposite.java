package neontd.desktop;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/** Additives Mischen ("Licht addiert sich") für Java2D – das Gegenstück zu {@code globalCompositeOperation='lighter'}. */
final class AdditiveComposite implements Composite {
    private final double alpha;

    AdditiveComposite(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        final boolean premultiplied = srcColorModel.isAlphaPremultiplied();
        final boolean srcHasAlpha = srcColorModel.hasAlpha();
        return new CompositeContext() {
            @Override
            public void dispose() {
            }

            @Override
            public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
                int w = Math.min(src.getWidth(), dstIn.getWidth());
                int h = Math.min(src.getHeight(), dstIn.getHeight());
                int sb = src.getNumBands();
                int db = dstIn.getNumBands();
                int[] sp = new int[w * sb];
                int[] dp = new int[w * db];
                for (int y = 0; y < h; y++) {
                    src.getPixels(src.getMinX(), src.getMinY() + y, w, 1, sp);
                    dstIn.getPixels(dstIn.getMinX(), dstIn.getMinY() + y, w, 1, dp);
                    for (int x = 0; x < w; x++) {
                        double k = alpha;
                        if (srcHasAlpha && sb >= 4 && !premultiplied) {
                            k *= sp[x * sb + 3] / 255.0;
                        }
                        for (int c = 0; c < 3; c++) {
                            int v = dp[x * db + c] + (int) (sp[x * sb + c] * k);
                            dp[x * db + c] = v > 255 ? 255 : v;
                        }
                    }
                    dstOut.setPixels(dstOut.getMinX(), dstOut.getMinY() + y, w, 1, dp);
                }
            }
        };
    }
}
