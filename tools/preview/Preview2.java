import com.wildlands.tree.TreeBuilder;
import com.wildlands.tree.TreeModel;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Предпросмотр деревьев v2 в псевдо-3D: ветви рисуются их настоящей толщиной.
 * Запуск: javac -d build/preview src/main/java/com/wildlands/tree/*.java tools/preview/Preview2.java
 *         java -Djava.awt.headless=true -cp build/preview Preview2 build/preview/png
 */
public final class Preview2 {
    static final int W = 520, H = 640;
    static final double S = 11;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/preview/png");
        out.mkdirs();
        sheet(out, "v2_oak", TreeBuilder.Species.OAK, new int[] {14, 19, 24}, new Color(0x4A3828), new Color(0x3C8A2E));
        sheet(out, "v2_birch", TreeBuilder.Species.BIRCH, new int[] {12, 16, 20}, new Color(0xD9D6CC), new Color(0x6FA93A));
        sheet(out, "v2_spruce", TreeBuilder.Species.SPRUCE, new int[] {20, 27, 34}, new Color(0x4B3626), new Color(0x1F5A33));
    }

    static void sheet(File dir, String name, TreeBuilder.Species sp, int[] hs, Color wood, Color leaf) throws Exception {
        BufferedImage img = new BufferedImage(W * hs.length, H * 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xCFE6F5));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int i = 0; i < hs.length; i++) {
            long t0 = System.nanoTime();
            TreeModel m = TreeBuilder.generate(sp, hs[i], 500L + i * 71L);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("%s h=%d wood=%d leaves=%d top=%d extent=%d time=%dms%n", name, hs[i], m.woodCount(),
                    m.leafCount(), m.topY(), m.maxHorizontalExtent(), ms);
            render(g, m, i * W, 0, 0.4, 0.35, wood, leaf);
            render(g, m, i * W, H, 2.0, 0.35, wood, leaf);
        }
        g.dispose();
        ImageIO.write(img, "png", new File(dir, name + ".png"));
    }

    record Box(double x0, double y0, double z0, double x1, double y1, double z1, Color c, boolean leaf) {
    }

    static void render(Graphics2D g, TreeModel m, int ox, int oy, double yaw, double pitch, Color wood, Color leaf) {
        List<Box> boxes = new ArrayList<>();
        for (TreeModel.Voxel v : m.voxels()) {
            if (v.wood()) {
                double t = v.cls() / 16.0;
                double c = 0.5;
                if (v.cls() >= TreeModel.FULL) {
                    boxes.add(new Box(v.x(), v.y(), v.z(), v.x() + 1, v.y() + 1, v.z() + 1, wood, false));
                } else {
                    boxes.add(new Box(v.x() + c - t, v.y() + c - t, v.z() + c - t, v.x() + c + t, v.y() + c + t, v.z() + c + t, wood, false));
                    for (int d = 0; d < 6; d++) {
                        if (v.connects(d)) {
                            int[] o = TreeModel.DIRS[d];
                            double ax0 = v.x() + c - t, ax1 = v.x() + c + t, ay0 = v.y() + c - t, ay1 = v.y() + c + t,
                                    az0 = v.z() + c - t, az1 = v.z() + c + t;
                            if (o[0] > 0) { ax0 = v.x() + c + t; ax1 = v.x() + 1; }
                            if (o[0] < 0) { ax0 = v.x(); ax1 = v.x() + c - t; }
                            if (o[1] > 0) { ay0 = v.y() + c + t; ay1 = v.y() + 1; }
                            if (o[1] < 0) { ay0 = v.y(); ay1 = v.y() + c - t; }
                            if (o[2] > 0) { az0 = v.z() + c + t; az1 = v.z() + 1; }
                            if (o[2] < 0) { az0 = v.z(); az1 = v.z() + c - t; }
                            boxes.add(new Box(ax0, ay0, az0, ax1, ay1, az1, wood, false));
                        }
                    }
                }
            } else {
                int j = ((v.x() * 31 + v.y() * 17 + v.z() * 13) & 7) - 3;
                Color lc = new Color(clamp(leaf.getRed() + j * 5), clamp(leaf.getGreen() + j * 9), clamp(leaf.getBlue() + j * 4));
                boxes.add(new Box(v.x(), v.y(), v.z(), v.x() + 1, v.y() + 1, v.z() + 1, lc, true));
            }
        }
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
        double midY = m.topY() * 0.5;
        boxes.sort(Comparator.comparingDouble((Box b) -> -depth((b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2, (b.z0 + b.z1) / 2, cy, sy, cp, sp)));
        int cx = ox + W / 2, cyy = oy + H - 70;
        // земля
        g.setColor(new Color(0x6E8B3D));
        g.fillRect(ox, cyy, W, H - 70 + 70 - (cyy - oy));
        for (Box b : boxes) {
            drawBox(g, b, cx, cyy, cy, sy, cp, sp);
        }
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static double depth(double x, double y, double z, double cy, double sy, double cp, double sp) {
        double zz = x * sy + z * cy;
        return -y * sp + zz * cp;
    }

    static double[] proj(double x, double y, double z, double cy, double sy, double cp, double sp) {
        double xx = x * cy - z * sy;
        double zz = x * sy + z * cy;
        double yy = y * cp + zz * sp;
        return new double[] {xx, yy};
    }

    static void drawBox(Graphics2D g, Box b, int cx, int cyy, double cy, double sy, double cp, double sp) {
        double[][] n = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        double[] light = {1.0, 0.45, 0.8, 0.65, 0.72, 0.58};
        for (int f = 0; f < 6; f++) {
            double nx = n[f][0], ny = n[f][1], nz = n[f][2];
            double nzz = nx * sy + nz * cy;
            double nd = -ny * sp + nzz * cp;
            if (nd >= 0) {
                continue;
            }
            double[][] q = face(b, f);
            Path2D p = new Path2D.Double();
            for (int i = 0; i < 4; i++) {
                double[] s = proj(q[i][0], q[i][1], q[i][2], cy, sy, cp, sp);
                double px = cx + s[0] * S, py = cyy - s[1] * S;
                if (i == 0) {
                    p.moveTo(px, py);
                } else {
                    p.lineTo(px, py);
                }
            }
            p.closePath();
            Color c = b.c;
            g.setColor(new Color((int) (c.getRed() * light[f]), (int) (c.getGreen() * light[f]), (int) (c.getBlue() * light[f])));
            g.fill(p);
            if (b.leaf) {
                g.setColor(new Color(0, 0, 0, 40));
                g.draw(p);
            }
        }
    }

    static double[][] face(Box b, int f) {
        double x0 = b.x0, x1 = b.x1, y0 = b.y0, y1 = b.y1, z0 = b.z0, z1 = b.z1;
        return switch (f) {
            case 0 -> new double[][] {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
            case 1 -> new double[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case 2 -> new double[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
            case 3 -> new double[][] {{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}};
            case 4 -> new double[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            default -> new double[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
        };
    }
}
