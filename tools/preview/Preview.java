import com.wildlands.tree.TreeBlueprint;
import com.wildlands.tree.TreeGenerator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Предпросмотр форм деревьев без запуска игры. Рисует каждое дерево спереди и сбоку.
 *
 * Запуск из корня репозитория:
 *   javac -d build/preview src/main/java/com/wildlands/tree/*.java tools/preview/Preview.java
 *   java -Djava.awt.headless=true -cp build/preview Preview build/preview/png
 */
public final class Preview {
    private static final int SCALE = 8;
    private static final int HALF_W = 15;
    private static final int ROWS = 42;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/preview/png");
        out.mkdirs();
        draw(out, "oak", TreeGenerator.Species.OAK, new int[] {14, 19, 24});
        draw(out, "birch", TreeGenerator.Species.BIRCH, new int[] {12, 16, 20});
        draw(out, "spruce", TreeGenerator.Species.SPRUCE, new int[] {20, 27, 34});
    }

    private static void draw(File dir, String name, TreeGenerator.Species sp, int[] heights) throws Exception {
        int pw = (2 * HALF_W + 1) * SCALE, ph = ROWS * SCALE;
        BufferedImage img = new BufferedImage(pw * heights.length, ph * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xCFE6F5));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < heights.length; i++) {
            TreeBlueprint bp = TreeGenerator.generate(sp, heights[i], 1000L + i * 37L);
            panel(g, bp, i * pw, 0, pw, ph, true);
            panel(g, bp, i * pw, ph, pw, ph, false);
            System.out.printf("%s h=%d  logs=%d leaves=%d  topY=%d  maxRadius=%d%n", name, heights[i],
                    bp.count(true), bp.count(false), bp.topY(), bp.maxHorizontalExtent());
        }
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    private static void panel(Graphics2D g, TreeBlueprint bp, int ox, int oy, int pw, int ph, boolean front) {
        g.setColor(new Color(0x7A5C3A));
        g.fillRect(ox, oy + ph - 4 * SCALE, pw, 4 * SCALE);
        List<TreeBlueprint.Voxel> list = new java.util.ArrayList<>(bp.voxels());
        // дальние рисуем первыми
        list.sort(Comparator.comparingInt((TreeBlueprint.Voxel v) -> front ? -v.z() : -v.x()));
        for (TreeBlueprint.Voxel v : list) {
            int h = front ? v.x() : v.z();
            int depth = front ? v.z() : v.x();
            int px = ox + (h + HALF_W) * SCALE;
            int py = oy + ph - 4 * SCALE - (v.y() + 1) * SCALE;
            double shade = Math.max(0.55, Math.min(1.0, 1.0 - depth * 0.03));
            Color c;
            if (v.isLog()) {
                c = shade(new Color(0x5E4129), shade);
            } else {
                int jit = ((v.x() * 31 + v.y() * 17 + v.z() * 13) & 7) * 4;
                c = shade(new Color(0x2F7A34 + jit * 0x000100), shade);
            }
            g.setColor(c);
            g.fillRect(px, py, SCALE - 1, SCALE - 1);
        }
    }

    private static Color shade(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }
}
