package com.wildlands.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Генератор форм деревьев. Все числа в блоках (1 блок = 1 метр). Результат полностью определяется
 * видом, высотой и зерном, поэтому дерево не «ломается» на границе чанков.
 *
 * Параметры подобраны по ботаническим описаниям (docs/REFERENCES.md) и настраиваются по предпросмотру.
 */
public final class TreeGenerator {
    public enum Species {
        OAK, BIRCH, SPRUCE;

        public static Species byName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Максимальный вылет кроны от оси ствола: дерево должно помещаться в область генерации мира. */
    public static final int MAX_RADIUS = 12;

    private static final double TAU = Math.PI * 2;

    private TreeGenerator() {
    }

    public static TreeBlueprint generate(Species species, int height, long seed) {
        Ctx c = new Ctx(height, seed);
        switch (species) {
            case OAK -> oak(c);
            case BIRCH -> birch(c);
            case SPRUCE -> spruce(c);
        }
        c.applyLeaves();
        c.bp.finish(MAX_RADIUS, -3, height + 8);
        return c.bp;
    }

    // ------------------------------------------------------------------ контекст и примитивы

    private static final class Pen {
        boolean has;
        int x, y, z;
    }

    private static final class Ctx {
        final TreeBlueprint bp = new TreeBlueprint();
        final Random rnd;
        final long seed;
        final int h;
        final List<double[]> clusters = new ArrayList<>();

        Ctx(int h, long seed) {
            this.h = h;
            this.seed = seed;
            this.rnd = new Random(seed);
        }

        double rand(double a, double b) {
            return a + (b - a) * rnd.nextDouble();
        }

        /** Бревно толщиной r вокруг точки (r меньше 0,95 даёт один блок, около 1 даёт «плюс», больше даёт круглое сечение). */
        void sphere(int cx, int cy, int cz, double r, int kind) {
            double lim = r * r + 0.1;
            if (lim < 1.0) {
                bp.setLog(cx, cy, cz, kind);
                return;
            }
            int rr = (int) Math.ceil(r);
            for (int dx = -rr; dx <= rr; dx++) {
                for (int dy = -rr; dy <= rr; dy++) {
                    for (int dz = -rr; dz <= rr; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= lim) {
                            bp.setLog(cx + dx, cy + dy, cz + dz, kind);
                        }
                    }
                }
            }
        }

        /** Ставит бревно и добивает промежуточные блоки, чтобы ветвь не рвалась по диагонали. */
        void pathLog(Pen p, double x, double y, double z, double r, int kind) {
            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            int cz = (int) Math.round(z);
            if (p.has) {
                int ix = p.x, iy = p.y, iz = p.z;
                while (Math.abs(cx - ix) + Math.abs(cy - iy) + Math.abs(cz - iz) > 1) {
                    int ax = Math.abs(cx - ix), ay = Math.abs(cy - iy), az = Math.abs(cz - iz);
                    if (ay >= ax && ay >= az) {
                        iy += Integer.signum(cy - iy);
                    } else if (ax >= az) {
                        ix += Integer.signum(cx - ix);
                    } else {
                        iz += Integer.signum(cz - iz);
                    }
                    sphere(ix, iy, iz, r, kind);
                }
            }
            sphere(cx, cy, cz, r, kind);
            p.has = true;
            p.x = cx;
            p.y = cy;
            p.z = cz;
        }

        void cluster(double x, double y, double z, double rx, double ry, double rz, double loose) {
            clusters.add(new double[] {x, y, z, rx, ry, rz, loose});
        }

        void applyLeaves() {
            for (double[] k : clusters) {
                int x0 = (int) Math.floor(k[0] - k[3]) - 1, x1 = (int) Math.ceil(k[0] + k[3]) + 1;
                int y0 = (int) Math.floor(k[1] - k[4]) - 1, y1 = (int) Math.ceil(k[1] + k[4]) + 1;
                int z0 = (int) Math.floor(k[2] - k[5]) - 1, z1 = (int) Math.ceil(k[2] + k[5]) + 1;
                for (int ix = x0; ix <= x1; ix++) {
                    for (int iy = y0; iy <= y1; iy++) {
                        for (int iz = z0; iz <= z1; iz++) {
                            double dx = (ix - k[0]) / k[3], dy = (iy - k[1]) / k[4], dz = (iz - k[2]) / k[5];
                            double nd = dx * dx + dy * dy + dz * dz;
                            if (nd + (hash01(ix, iy, iz) - 0.5) * k[6] < 1.0) {
                                bp.setLeaf(ix, iy, iz);
                            }
                        }
                    }
                }
            }
        }

        double hash01(int x, int y, int z) {
            long v = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
            v ^= v >>> 33;
            v *= 0xff51afd7ed558ccdL;
            v ^= v >>> 33;
            v *= 0xc4ceb9fe1a85ec53L;
            v ^= v >>> 33;
            return (v >>> 11) * (1.0 / (1L << 53));
        }
    }

    private static int axisOf(double dx, double dy, double dz) {
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) {
            return TreeBlueprint.LOG_X;
        }
        return az >= ay ? TreeBlueprint.LOG_Z : TreeBlueprint.LOG_Y;
    }

    /**
     * Ствол: ось слегка отклоняется и колеблется, толщина плавно уменьшается, у земли расширение.
     * Возвращает точки оси ствола (x, y, z).
     */
    private static List<double[]> trunk(Ctx c, double top, double rBase, double rTop, double taper,
                                        double lean, double leanAz, double wobble, double flare) {
        List<double[]> pts = new ArrayList<>();
        Pen pen = new Pen();
        double lx = Math.cos(leanAz), lz = Math.sin(leanAz);
        double ph1 = c.rnd.nextDouble() * TAU, ph2 = c.rnd.nextDouble() * TAU;
        int n = (int) Math.max(2, Math.round(top / 0.5));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double y = t * top;
            double drift = lean * t * t;
            double ox = lx * drift + wobble * Math.sin(ph1 + t * 5.0) * t;
            double oz = lz * drift + wobble * Math.sin(ph2 + t * 5.0) * t;
            double r = rTop + (rBase - rTop) * Math.pow(1 - t, taper);
            if (y < 3) {
                r += flare * (3 - y) / 3.0;
            }
            c.pathLog(pen, ox, y, oz, r, TreeBlueprint.LOG_Y);
            pts.add(new double[] {ox, y, oz});
        }
        return pts;
    }

    /**
     * Ветвь: идёт в заданном направлении, чуть изгибается, толщина меняется от r0 до r1.
     * upTend тянет ветвь вверх (положительное) или вниз, droopTail добавляет это только на последней трети.
     * logFrac задаёт, какая часть длины одета в брёвна (остальное будет листвой).
     */
    private static List<double[]> branch(Ctx c, double x, double y, double z, double dx, double dy, double dz,
                                         double length, double r0, double r1, double upTend, double jitter,
                                         double droopTail, double logFrac) {
        List<double[]> pts = new ArrayList<>();
        double step = 0.5;
        int n = (int) Math.max(1, Math.round(length / step));
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;
        Pen pen = new Pen();
        pts.add(new double[] {x, y, z});
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            dy += (upTend + (t > 0.6 ? droopTail : 0)) * step;
            dx += (c.rnd.nextDouble() - 0.5) * jitter * step * 2;
            dz += (c.rnd.nextDouble() - 0.5) * jitter * step * 2;
            len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len;
            dy /= len;
            dz /= len;
            x += dx * step;
            y += dy * step;
            z += dz * step;
            if (t <= logFrac) {
                c.pathLog(pen, x, y, z, r0 + (r1 - r0) * t, axisOf(dx, dy, dz));
            }
            pts.add(new double[] {x, y, z});
        }
        return pts;
    }

    private static double[] randomDirection(Ctx c) {
        double a = c.rnd.nextDouble() * TAU;
        double e = Math.asin(c.rnd.nextDouble() * 2 - 1);
        return new double[] {Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e)};
    }

    private static double[] pointDir(List<double[]> pts, int idx) {
        double[] a = pts.get(Math.max(0, idx - 1));
        double[] b = pts.get(Math.min(pts.size() - 1, idx + 1));
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return l < 1e-6 ? new double[] {0, 1, 0} : new double[] {dx / l, dy / l, dz / l};
    }

    // ------------------------------------------------------------------ дуб

    private static void oak(Ctx c) {
        int h = c.h;
        Random r = c.rnd;
        double scale = h / 18.0;
        double trunkTop = h * c.rand(0.56, 0.62);
        List<double[]> trunk = trunk(c, trunkTop, 1.3, 0.8, 0.8, c.rand(0, 1.6), c.rand(0, TAU), 0.5, 0.9);

        // Поверхностные корни
        int roots = 4 + r.nextInt(3);
        double ra = c.rand(0, TAU);
        for (int i = 0; i < roots; i++) {
            double az = ra + i * TAU / roots + c.rand(-0.4, 0.4);
            branch(c, 0, 1.3, 0, Math.cos(az), -0.5, Math.sin(az), c.rand(2.5, 4.0), 0.9, 0.5, 0, 0.2, 0, 1.0);
        }

        // Крупные нижние и верхние ветви
        int limbs = 6 + r.nextInt(3);
        double az0 = c.rand(0, TAU);
        double asymAz = c.rand(0, TAU);
        for (int i = 0; i < limbs; i++) {
            double u = (i + 0.5) / limbs;
            int ti = (int) Math.min(trunk.size() - 1, Math.round((0.50 + 0.5 * u) * (trunk.size() - 1)));
            double[] s = trunk.get(ti);
            double az = az0 + i * 2.399963 + c.rand(-0.3, 0.3);
            double elev = Math.toRadians(14 + 50 * u + c.rand(0, 12));
            double len = h * (0.37 - 0.07 * u) * c.rand(0.85, 1.15) * (1 + 0.22 * Math.cos(az - asymAz));
            double dx = Math.cos(az) * Math.cos(elev), dz = Math.sin(az) * Math.cos(elev), dy = Math.sin(elev);
            List<double[]> pts = branch(c, s[0], s[1], s[2], dx, dy, dz, len, 1.15 - 0.2 * u, 0.5, 0.11, 0.25, 0.0, 1.0);

            int kids = 3 + r.nextInt(3);
            for (int k = 0; k < kids; k++) {
                double t = 0.3 + 0.6 * (k + r.nextDouble()) / kids;
                int idx = (int) (t * (pts.size() - 1));
                double[] p = pts.get(idx);
                double[] pd = pointDir(pts, idx);
                double[] rd = randomDirection(c);
                double kdx = pd[0] * 0.5 + rd[0] * 0.9, kdy = pd[1] * 0.5 + rd[1] * 0.9 + 0.3, kdz = pd[2] * 0.5 + rd[2] * 0.9;
                double klen = len * c.rand(0.35, 0.6);
                List<double[]> kp = branch(c, p[0], p[1], p[2], kdx, kdy, kdz, klen, 0.6, 0.4, 0.14, 0.35, 0.0, 1.0);
                double[] e = kp.get(kp.size() - 1);
                double cr = (1.8 + r.nextDouble() * 0.8) * Math.max(0.8, scale);
                c.cluster(e[0], e[1], e[2], cr, cr * 0.78, cr, 0.8);
            }
            double[] tip = pts.get(pts.size() - 1);
            double tr = (2.4 + r.nextDouble() * 0.8) * Math.max(0.8, scale);
            c.cluster(tip[0], tip[1], tip[2], tr, tr * 0.8, tr, 0.8);
            if (u > 0.45) {
                double[] mid = pts.get((int) (pts.size() * 0.6));
                c.cluster(mid[0], mid[1], mid[2], tr * 0.6, tr * 0.4, tr * 0.6, 0.9);
            }
        }

        // Центральный лидер и макушка
        double[] top = trunk.get(trunk.size() - 1);
        List<double[]> leader = branch(c, top[0], top[1], top[2], c.rand(-0.2, 0.2), 1, c.rand(-0.2, 0.2),
                Math.max(3, h * 0.95 - trunkTop), 0.9, 0.5, 0.0, 0.3, 0.0, 1.0);
        double[] lt = leader.get(leader.size() - 1);
        double lr = 3.0 * Math.max(0.8, scale);
        c.cluster(lt[0], lt[1] + 0.3, lt[2], lr, lr * 0.8, lr, 0.8);
    }

    // ------------------------------------------------------------------ берёза

    private static void birch(Ctx c) {
        int h = c.h;
        Random r = c.rnd;
        List<double[]> trunk = trunk(c, h - 1.0, 0.55, 0.45, 1.0, c.rand(0.3, 1.6), c.rand(0, TAU), 0.7, 0.2);
        double crownBase = h * 0.38;
        int n = Math.max(7, (int) Math.round(h * 0.65));
        double az0 = c.rand(0, TAU);
        double lmax = h * 0.26;
        for (int i = 0; i < n; i++) {
            double tt = (i + r.nextDouble()) / n;
            double y = crownBase + tt * (h - 1.0 - crownBase);
            int ti = (int) Math.min(trunk.size() - 1, Math.round(y / (h - 1.0) * (trunk.size() - 1)));
            double[] s = trunk.get(ti);
            double profile = Math.max(0.3, Math.sqrt(Math.max(0, 1 - Math.pow(2 * tt - 0.75, 2))));
            double len = lmax * profile * c.rand(0.7, 1.15);
            double az = az0 + i * 2.399963 + c.rand(-0.3, 0.3);
            double elev = Math.toRadians(c.rand(30, 58));
            double dx = Math.cos(az) * Math.cos(elev), dz = Math.sin(az) * Math.cos(elev), dy = Math.sin(elev);
            List<double[]> pts = branch(c, s[0], s[1], s[2], dx, dy, dz, len, 0.5, 0.5, 0.04, 0.4, -0.32, 1.0);

            int twigs = 1 + r.nextInt(2);
            for (int k = 0; k < twigs; k++) {
                int idx = (int) ((0.45 + 0.4 * r.nextDouble()) * (pts.size() - 1));
                double[] p = pts.get(idx);
                double[] pd = pointDir(pts, idx);
                List<double[]> tp = branch(c, p[0], p[1], p[2], pd[0] + c.rand(-0.6, 0.6), pd[1] * 0.3 - 0.1,
                        pd[2] + c.rand(-0.6, 0.6), len * 0.45, 0.5, 0.5, 0.0, 0.3, -0.4, 1.0);
                double[] e = tp.get(tp.size() - 1);
                double cr = c.rand(1.1, 1.5);
                c.cluster(e[0], e[1], e[2], cr, cr * 0.85, cr, 1.3);
            }
            double[] e = pts.get(pts.size() - 1);
            double cr = c.rand(1.3, 1.8);
            c.cluster(e[0], e[1], e[2], cr, cr * 0.8, cr, 1.2);
            for (int q = 3; q < pts.size() - 1; q += 2) {
                double[] p = pts.get(q);
                c.cluster(p[0], p[1], p[2], 1.25, 1.0, 1.25, 1.3);
            }
        }
        double[] top = trunk.get(trunk.size() - 1);
        c.cluster(top[0], top[1] + 0.5, top[2], 1.6, 2.0, 1.6, 1.0);
    }

    // ------------------------------------------------------------------ ель

    private static void spruce(Ctx c) {
        int h = c.h;
        Random r = c.rnd;
        double rBase = h >= 24 ? 1.35 : 0.9;
        trunk(c, h - 0.5, rBase, 0.5, 1.4, 0, 0, 0.0, 0.4);

        double y0 = h * 0.2 + r.nextDouble();
        double rmax = Math.max(3.8, Math.min(6.8, h * 0.18));
        double span = h - 1 - y0;
        int tier = 0;
        for (double y = y0; y <= h - 1; y += 1.0, tier++) {
            double u = (y - y0) / span;
            boolean main = tier % 3 == 0;
            if (!main && r.nextDouble() < 0.25) {
                continue;
            }
            int cnt = main ? (u < 0.75 ? 6 + r.nextInt(3) : 3 + r.nextInt(2)) : 3;
            double lenFull = rmax * Math.pow(1 - u, 0.9) + 0.8;
            double az0 = c.rand(0, TAU);
            for (int k = 0; k < cnt; k++) {
                double az = az0 + k * TAU / cnt + c.rand(-0.35, 0.35);
                double len = lenFull * (main ? c.rand(0.8, 1.1) : c.rand(0.35, 0.6));
                double elev = Math.toRadians(-30 + 50 * u + c.rand(-8, 8));
                double dx = Math.cos(az) * Math.cos(elev), dz = Math.sin(az) * Math.cos(elev), dy = Math.sin(elev);
                List<double[]> pts = branch(c, 0, y, 0, dx, dy, dz, len, 0.5, 0.5, 0.10, 0.15, 0.0, 0.5);
                double rf = main ? 1.5 : 1.0;
                for (int i = 2; i < pts.size(); i += 2) {
                    double[] p = pts.get(i);
                    double f = i / (double) (pts.size() - 1);
                    c.cluster(p[0], p[1], p[2], rf, main ? 0.85 : 0.7, rf, 0.8);
                    if (main && u < 0.6 && f > 0.4) {
                        c.cluster(p[0], p[1] - 1.2, p[2], rf * 0.7, 0.8, rf * 0.7, 0.9);
                    }
                }
            }
        }
        c.cluster(0, h - 2.0, 0, 1.2, 2.6, 1.2, 0.4);
    }
}
