package com.wildlands.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Генератор деревьев v2: скелет (space colonization или ярусы), радиусы по pipe model,
 * растеризация в блоки разной толщины, листва кластерами на тонких концах.
 * Не зависит от Minecraft.
 */
public final class TreeBuilder {
    public enum Species {
        OAK, BIRCH, SPRUCE;

        public static Species byName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Показатель pipe model: r^n = сумма r_i^n. */
    static final double PIPE_N = 2.5;
    static final int MAX_RADIUS = 12;
    static final int MAX_NODES = 1100;

    private static final class Node {
        double x, y, z;
        int parent;
        int children;
        double r;

        Node(double x, double y, double z, int parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.parent = parent;
        }
    }

    private final Random rnd;
    private final List<Node> nodes = new ArrayList<>();
    private final TreeModel model = new TreeModel();
    private final List<Integer> trunkIdx = new ArrayList<>();
    private final int height;
    private double trunkRadius;
    /** Диаметр ствола у земли в блоках (до сбега), см. trunkWidthExact. */
    private double dbhBlocks = 1.0;
    /** 0: шар/эллипсоид; >0: яйцевидная крона, сужающаяся кверху. */
    private double crownTaper = 0;
    private final java.util.Map<Integer, int[]> footprints = new java.util.HashMap<>();
    private double leafR;
    private double leafFlat;
    private double leafDensity;
    private double leafTwigRadius;
    private double crownR;
    private double hollow;

    private TreeBuilder(int height, long seed) {
        this.height = height;
        this.rnd = new Random(seed);
    }

    public static TreeModel generate(Species species, int height, long seed) {
        TreeBuilder b = new TreeBuilder(height, seed);
        switch (species) {
            case OAK -> b.oak();
            case BIRCH -> b.birch();
            case SPRUCE -> b.spruce();
        }
        b.computeRadii();
        b.rasterize();
        b.leaves(species);
        b.model.trimTwigs(4, 8);
        b.model.finish(MAX_RADIUS, 0, height + 6);
        return b.model;
    }

    // ---------------------------------------------------------------- виды

    private void oak() {
        double h = height;
        double base = 0.27 * h + rnd.nextDouble() * 0.04 * h;
        crownR = Math.min(10.0, 0.55 * h);
        hollow = 0.10;
        dbhBlocks = 0.046 * h;                                           // дуб 20 м: DBH около 0.9 м
        trunkRadius = Math.max(0.5, Math.min(0.95, 0.016 * h + 0.28));
        leafR = 3.0;
        leafFlat = 0.7;
        leafDensity = 0.97;
        leafTwigRadius = 0.22;
        trunk(h * (0.80 + rnd.nextDouble() * 0.1), 0.5, 0.18);
        limbs(5 + rnd.nextInt(3), base * 0.8, h * 0.66, 32, 58, crownR * 0.8, crownR * 1.15, 0.05);
        double cy = base + (h - base) * 0.5;
        spaceColonization(cy, (h - base) * 0.52, crownR, 340, 4.4, 1.5, 0.05, 1.0);
    }

    private void birch() {
        double h = height;
        double base = 0.33 * h + rnd.nextDouble() * 0.05 * h;
        crownR = Math.min(5.2, 0.24 * h);
        crownTaper = 0.6;
        hollow = 0.12;
        dbhBlocks = 0.028 * h;                                           // берёза стройнее
        trunkRadius = Math.max(0.5, Math.min(0.70, 0.010 * h + 0.36));
        leafR = 2.5;
        leafFlat = 0.85;
        leafDensity = 0.97;
        leafTwigRadius = 0.12;
        trunk(h * (0.9 + rnd.nextDouble() * 0.06), 0.9, 0.5);
        limbs(6 + rnd.nextInt(3), base, h * 0.8, 24, 46, crownR * 0.7, crownR * 1.1, 0.03);
        double cy = base + (h - base) * 0.5;
        spaceColonization(cy, (h - base) * 0.52, crownR, 220, 3.8, 1.6, 0.18, 1.0);
    }

    /**
     * Ель: прямой ствол, конус из ярусов. Каждые 3 блока по высоте мутовка из 5-9 ветвей, которые
     * свисают вниз, а к концу загибаются вверх. Хвоя это «лапы»: полоса шире к середине ветви
     * и свисающий нижний слой. Между ярусами видны ствол и просветы.
     */
    private void spruce() {
        double h = height;
        dbhBlocks = 0.036 * h;                                           // ель 35 м: DBH около 1.2 м
        trunkRadius = Math.max(0.5, Math.min(0.85, 0.012 * h + 0.34));
        trunk(h - 0.5, 0.12, 0.04);
        double y0 = Math.max(3.0, 0.22 * h);
        double rmax = Math.max(3.6, Math.min(7.0, 0.21 * h));
        double golden = Math.PI * (3 - Math.sqrt(5));
        double phase = rnd.nextDouble() * Math.PI * 2;
        int tier = 0;
        for (double y = y0; y <= h - 1.5; y += 3.0, tier++) {
            double t = (y - y0) / (h - y0);
            double reach = rmax * (1.0 - t) + 1.0;
            // слабый ритм «густо-реже» между ярусами, как у настоящих елей
            reach *= (tier % 3 == 2) ? 0.82 : 1.0;
            int count = (int) Math.max(6, Math.min(10, Math.round(reach * 1.5)));
            int ti = trunkNodeAt(y);
            phase += golden;
            for (int i = 0; i < count; i++) {
                double ang = phase + i * (Math.PI * 2 / count) + (rnd.nextDouble() - 0.5) * 0.35;
                spruceBranch(ti, ang, reach * (0.85 + rnd.nextDouble() * 0.3), t);
            }
        }
        // вершина: тонкий шпиль
        Node top = nodes.get(trunkIdx.get(trunkIdx.size() - 1));
        int tx = (int) Math.floor(top.x), tz = (int) Math.floor(top.z), ty = (int) Math.floor(top.y);
        model.setLeaf(tx, ty + 1, tz);
        model.setLeaf(tx, ty, tz);
        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            model.setLeaf(tx + o[0], ty - 1, tz + o[1]);
            model.setLeaf(tx + o[0], ty - 2, tz + o[1]);
        }
        model.setLeaf(tx, ty - 1, tz);
        model.setLeaf(tx, ty - 2, tz);
    }

    private void spruceBranch(int from, double angle, double len, double t) {
        Node s = nodes.get(from);
        double dx = Math.cos(angle), dz = Math.sin(angle);
        double px = -dz, pz = dx; // горизонтальный перпендикуляр
        int steps = Math.max(2, (int) Math.round(len));
        int prev = from;
        double x = s.x, y = s.y, z = s.z;
        double droop = 0.26 - 0.14 * t;
        for (int i = 1; i <= steps; i++) {
            double f = (double) i / steps;
            x += dx;
            z += dz;
            y += f < 0.65 ? -droop : droop * 1.6;
            prev = add(x, y, z, prev);
            int vx = (int) Math.floor(x), vy = (int) Math.floor(y), vz = (int) Math.floor(z);
            // хвоя: полоса вдоль ветви, шире к середине, плюс свисающий нижний слой
            int width = f < 0.2 ? 0 : (f < 0.85 ? 1 : 0);
            if (len > 4.5 && f > 0.35 && f < 0.8) {
                width = 2;
            }
            model.setLeaf(vx, vy, vz);
            for (int w = 1; w <= width; w++) {
                model.setLeaf((int) Math.floor(x + px * w), vy, (int) Math.floor(z + pz * w));
                model.setLeaf((int) Math.floor(x - px * w), vy, (int) Math.floor(z - pz * w));
            }
            if (f > 0.25) {
                model.setLeaf(vx, vy - 1, vz);
                if (width >= 1) {
                    model.setLeaf((int) Math.floor(x + px), vy - 1, (int) Math.floor(z + pz));
                    model.setLeaf((int) Math.floor(x - px), vy - 1, (int) Math.floor(z - pz));
                }
            }
            if (f > 0.45 && f < 0.9 && len > 5.5) {
                model.setLeaf(vx, vy - 2, vz);
            }
        }
    }

    // ------------------------------------------------------------- скелет

    private int add(double x, double y, double z, int parent) {
        Node n = new Node(x, y, z, parent);
        nodes.add(n);
        if (parent >= 0) {
            nodes.get(parent).children++;
        }
        return nodes.size() - 1;
    }

    private int trunkNodeAt(double y) {
        int best = trunkIdx.get(0);
        double bd = 1e9;
        for (int i : trunkIdx) {
            double d = Math.abs(nodes.get(i).y - y);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** Несущие ветви: ответвления от ствола, которые потом обрастают веточками. */
    private void limbs(int count, double yFrom, double yTo, double elevMin, double elevMax,
                       double lenMin, double lenMax, double gravity) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        double az = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double y = yFrom + (yTo - yFrom) * (i + rnd.nextDouble() * 0.6) / count;
            az += golden * (0.8 + rnd.nextDouble() * 0.5);
            double el = Math.toRadians(elevMin + (elevMax - elevMin) * rnd.nextDouble());
            double len = (lenMin + (lenMax - lenMin) * rnd.nextDouble()) * (1.0 - 0.3 * i / count);
            int from = trunkNodeAt(y);
            Node t = nodes.get(from);
            double dx = Math.cos(az) * Math.cos(el), dy = Math.sin(el), dz = Math.sin(az) * Math.cos(el);
            branch(from, t.x, t.y, t.z, dx, dy, dz, len, gravity, 2);
        }
    }

    /** Почти прямая ветвь с развилками: дочерние ветви расходятся под углом 30-65 градусов. */
    private void branch(int prev, double x, double y, double z, double dx, double dy, double dz,
                        double len, double gravity, int depth) {
        for (double d = 0; d < len; d += 1.0) {
            dx += (rnd.nextDouble() - 0.5) * 0.07;
            dz += (rnd.nextDouble() - 0.5) * 0.07;
            dy -= gravity;
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l;
            dy /= l;
            dz /= l;
            x += dx;
            y += dy;
            z += dz;
            prev = add(x, y, z, prev);
            if (depth > 0 && d >= 2 && d < len - 2 && rnd.nextDouble() < 0.30) {
                double yaw = (rnd.nextBoolean() ? 1 : -1) * Math.toRadians(30 + rnd.nextDouble() * 35);
                double cs = Math.cos(yaw), sn = Math.sin(yaw);
                double ndx = dx * cs - dz * sn, ndz = dx * sn + dz * cs, ndy = dy + 0.15;
                double nl = Math.sqrt(ndx * ndx + ndy * ndy + ndz * ndz);
                branch(prev, x, y, z, ndx / nl, ndy / nl, ndz / nl, (len - d) * (0.55 + 0.25 * rnd.nextDouble()),
                        gravity, depth - 1);
            }
        }
    }

    /** Ствол до высоты top с лёгким изгибом (wobble) и наклоном (lean). */
    private void trunk(double top, double wobble, double lean) {
        double x = 0.5, y = 0, z = 0.5;
        int prev = add(x, y, z, -1);
        trunkIdx.add(prev);
        double la = rnd.nextDouble() * Math.PI * 2;
        double lx = Math.cos(la) * lean * 0.12, lz = Math.sin(la) * lean * 0.12;
        double wx = 0, wz = 0;
        while (y < top) {
            wx = wx * 0.7 + (rnd.nextDouble() - 0.5) * wobble * 0.25;
            wz = wz * 0.7 + (rnd.nextDouble() - 0.5) * wobble * 0.25;
            x += lx + wx;
            z += lz + wz;
            y += 1.0;
            prev = add(x, y, z, prev);
            trunkIdx.add(prev);
        }
    }

    private void spaceColonization(double cy, double halfH, double crownR, int attractorCount,
                                   double influence, double kill, double tropism, double step) {
        List<double[]> att = new ArrayList<>();
        int guard = 0;
        while (att.size() < attractorCount && guard++ < attractorCount * 40) {
            double px = (rnd.nextDouble() * 2 - 1), py = (rnd.nextDouble() * 2 - 1), pz = (rnd.nextDouble() * 2 - 1);
            if (px * px + py * py + pz * pz > 1) {
                continue;
            }
            // верх крон чуть приплюснут, низ округлый
            double yy = py > 0 ? py * 0.9 : py;
            if (crownTaper > 0 && py > 0) {
                // яйцевидная крона: к вершине сужается (берёза)
                double sc = Math.pow(1.0 - py, crownTaper);
                px *= sc;
                pz *= sc;
            }
            att.add(new double[] {nodes.get(0).x + px * crownR, cy + yy * halfH, nodes.get(0).z + pz * crownR});
        }
        boolean[] dead = new boolean[att.size()];
        for (int iter = 0; iter < 160 && nodes.size() < MAX_NODES; iter++) {
            int n = nodes.size();
            double[][] sum = new double[n][3];
            int[] cnt = new int[n];
            for (int a = 0; a < att.size(); a++) {
                if (dead[a]) {
                    continue;
                }
                double[] p = att.get(a);
                int best = -1;
                double bd = influence * influence;
                for (int i = 0; i < n; i++) {
                    Node nd = nodes.get(i);
                    double dx = p[0] - nd.x, dy = p[1] - nd.y, dz = p[2] - nd.z;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < kill * kill) {
                        dead[a] = true;
                        best = -1;
                        break;
                    }
                    if (d < bd) {
                        bd = d;
                        best = i;
                    }
                }
                if (best >= 0) {
                    Node nd = nodes.get(best);
                    double dx = p[0] - nd.x, dy = p[1] - nd.y, dz = p[2] - nd.z;
                    double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    sum[best][0] += dx / l;
                    sum[best][1] += dy / l;
                    sum[best][2] += dz / l;
                    cnt[best]++;
                }
            }
            boolean grew = false;
            for (int i = 0; i < n && nodes.size() < MAX_NODES; i++) {
                if (cnt[i] == 0) {
                    continue;
                }
                double dx = sum[i][0] / cnt[i], dy = sum[i][1] / cnt[i] + tropism, dz = sum[i][2] / cnt[i];
                dx += (rnd.nextDouble() - 0.5) * 0.12;
                dy += (rnd.nextDouble() - 0.5) * 0.12;
                dz += (rnd.nextDouble() - 0.5) * 0.12;
                double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (l < 1e-6) {
                    continue;
                }
                Node p = nodes.get(i);
                double nx = p.x + dx / l * step, ny = p.y + dy / l * step, nz = p.z + dz / l * step;
                boolean tooClose = false;
                for (int j = 0; j < nodes.size(); j++) {
                    Node q = nodes.get(j);
                    double ex = q.x - nx, ey = q.y - ny, ez = q.z - nz;
                    if (ex * ex + ey * ey + ez * ez < step * step * 0.25) {
                        tooClose = true;
                        break;
                    }
                }
                if (!tooClose) {
                    add(nx, ny, nz, i);
                    grew = true;
                }
            }
            if (!grew) {
                break;
            }
        }
    }

    /** Pipe model: радиус родителя из радиусов детей, затем масштаб под радиус ствола. */
    private void computeRadii() {
        for (Node n : nodes) {
            n.r = 0;
        }
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (n.children == 0) {
                n.r = 1.0;
            }
            if (n.parent >= 0) {
                Node p = nodes.get(n.parent);
                p.r = Math.pow(Math.pow(p.r, PIPE_N) + Math.pow(n.r, PIPE_N), 1.0 / PIPE_N);
            }
        }
        // цепочки без ветвления не утолщаются: радиус родителя равен радиусу ребёнка
        double root = nodes.get(0).r;
        double scale = trunkRadius / root;
        for (Node n : nodes) {
            n.r = Math.max(0.065, n.r * scale);
        }
    }

    // ------------------------------------------------------- растеризация

    private void rasterize() {
        boolean[] isTrunk = new boolean[nodes.size()];
        for (int i : trunkIdx) {
            isTrunk[i] = true;
        }
        for (int i = 1; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            Node p = nodes.get(n.parent);
            boolean trunkSeg = isTrunk[i] && isTrunk[n.parent];
            double dx = n.x - p.x, dy = n.y - p.y, dz = n.z - p.z;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int axis = Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz) ? TreeModel.AXIS_Y
                    : (Math.abs(dx) >= Math.abs(dz) ? TreeModel.AXIS_X : TreeModel.AXIS_Z);
            int samples = Math.max(2, (int) Math.ceil(len / 0.2));
            int[] prev = null;
            for (int s = 0; s <= samples; s++) {
                double f = (double) s / samples;
                double x = p.x + dx * f, y = p.y + dy * f, z = p.z + dz * f;
                double r = p.r + (n.r - p.r) * f;
                int[] cur = {(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)};
                boolean thick = r >= 0.5;
                int cls = thick ? TreeModel.FULL : Math.max(1, Math.min(7, (int) Math.round(r * 16)));
                if (prev != null) {
                    bridge(prev, cur, cls, axis);
                }
                model.setWood(cur[0], cur[1], cur[2], cls, axis);
                if (trunkSeg && thick) {
                    int w = trunkWidth(y);
                    if (w > 1) {
                        trunkFootprint(x, y, z, w);
                    }
                }
                prev = cur;
            }
        }
        buttresses();
    }

    /**
     * Ширина ствола в блоках на высоте y. Реальные пропорции: диаметр на высоте груди (DBH) растёт
     * примерно как 0.03-0.05 от высоты дерева (дуб 20 м около 0.8 м, ель 35 м около 1.0 м),
     * к вершине ствол сбегает в конус (форма ствола: на середине высоты около 0.6 DBH).
     * Пока ширина меньше 1.5 блока, ствол это одно бревно; шире 2x2 только у гигантов.
     */
    private double trunkWidthExact(double y) {
        double f = Math.max(0.0, Math.min(1.0, y / Math.max(1.0, height)));
        return dbhBlocks * (1.0 - 0.55 * Math.pow(f, 0.8));
    }

    private int trunkWidth(double y) {
        return Math.max(1, Math.min(3, (int) Math.floor(trunkWidthExact(y) + 0.5)));
    }

    /** Квадратное сечение w x w вокруг оси ствола; у 3x3 срезаны углы, чтобы было круглее. */
    private void trunkFootprint(double x, double y, double z, int w) {
        int vy = (int) Math.floor(y);
        // один слой по высоте: сечение выбирается один раз (по первому образцу), иначе из-за изгиба
        // ствола получаются «жирные» неровные слои шире заданных
        int[] o = footprints.get(vy);
        if (o == null) {
            if ((w & 1) == 1) {
                o = new int[] {(int) Math.floor(x) - w / 2, (int) Math.floor(z) - w / 2, w};
            } else {
                o = new int[] {(int) Math.round(x) - w / 2, (int) Math.round(z) - w / 2, w};
            }
            footprints.put(vy, o);
        }
        w = o[2];
        int x0 = o[0], z0 = o[1];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < w; j++) {
                if (w >= 3 && (i == 0 || i == w - 1) && (j == 0 || j == w - 1)) {
                    continue;
                }
                model.setWood(x0 + i, vy, z0 + j, TreeModel.FULL, TreeModel.AXIS_Y);
            }
        }
    }

    /**
     * Контрфорсы: у основания крупного дерева 2-4 коротких бревна лежат вплотную к стволу на уровне
     * земли. Никаких корней вниз: бревно стоит прямо на земле, а если под ним пустота, его убирает
     * генерация (см. WildTreeFeature).
     */
    private void buttresses() {
        if (height < 16) {
            return;
        }
        int w = trunkWidth(0.5);
        int lo = (w & 1) == 1 ? -(w / 2) : 1 - w / 2;
        int hi = lo + w - 1;
        int count = 2 + rnd.nextInt(3);
        int side0 = rnd.nextInt(4);
        for (int k = 0; k < count; k++) {
            int side = (side0 + k + (rnd.nextBoolean() ? 1 : 0)) & 3;
            int off = lo + rnd.nextInt(Math.max(1, hi - lo + 1));
            int bx, bz, axis;
            switch (side) {
                case 0 -> { bx = hi + 1; bz = off; axis = TreeModel.AXIS_X; }
                case 1 -> { bx = lo - 1; bz = off; axis = TreeModel.AXIS_X; }
                case 2 -> { bx = off; bz = hi + 1; axis = TreeModel.AXIS_Z; }
                default -> { bx = off; bz = lo - 1; axis = TreeModel.AXIS_Z; }
            }
            model.setWood(bx, 0, bz, TreeModel.FULL, axis);
        }
    }

    /** Заполняет промежуточные блоки, чтобы соседние блоки ветви соприкасались гранями. */
    private void bridge(int[] a, int[] b, int cls, int axis) {
        int[] c = {a[0], a[1], a[2]};
        for (int d = 0; d < 3; d++) {
            while (c[d] != b[d]) {
                c[d] += Integer.signum(b[d] - c[d]);
                model.setWood(c[0], c[1], c[2], cls, axis);
            }
        }
    }

    // ------------------------------------------------------------ листва

    private void leaves(Species sp) {
        if (sp == Species.SPRUCE) {
            return;
        }
        List<Node> leafy = new ArrayList<>();
        double minR = 1e9;
        for (Node n : nodes) {
            minR = Math.min(minR, n.r);
        }
        double thr = Math.max(leafTwigRadius, minR * 2.0);
        for (Node n : nodes) {
            if (n.r <= thr && n.y > 2.5) {
                leafy.add(n);
            }
        }
        for (Node n : leafy) {
            boolean tip = n.children == 0;
            double hd = Math.hypot(n.x - 0.5, n.z - 0.5);
            if (hd < crownR * hollow) {
                continue;
            }
            // низкочастотный шум вырезает просветы между кластерами
            double lobe = Math.sin(n.x * 0.9 + n.y * 0.5) * Math.cos(n.z * 0.8 - n.y * 0.7);
            if (hollow > 0 && lobe < -2.0) {
                continue;
            }
            if (!tip && rnd.nextDouble() > (sp == Species.SPRUCE ? 0.8 : leafDensity * 0.8)) {
                continue;
            }
            double cyOff = sp == Species.SPRUCE ? -0.5 : 0.1;
            blob(n.x, n.y + cyOff, n.z, tip ? leafR : leafR * 0.8);
        }
        if (sp == Species.SPRUCE) {
            Node top = nodes.get(trunkTopIndex());
            for (int dy = 0; dy < 3; dy++) {
                int w = 1 - dy / 2;
                for (int dx = -w; dx <= w; dx++) {
                    for (int dz = -w; dz <= w; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= w + (dy == 0 ? 1 : 0)) {
                            model.setLeaf((int) Math.floor(top.x) + dx, (int) Math.floor(top.y) + dy - 1,
                                    (int) Math.floor(top.z) + dz);
                        }
                    }
                }
            }
        }
    }

    private int trunkTopIndex() {
        int best = 0;
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).y > nodes.get(best).y) {
                best = i;
            }
        }
        return best;
    }

    private void blob(double cx, double cy, double cz, double rad) {
        int ri = (int) Math.ceil(rad + 1);
        for (int ix = -ri; ix <= ri; ix++) {
            for (int iy = -ri; iy <= ri; iy++) {
                for (int iz = -ri; iz <= ri; iz++) {
                    int vx = (int) Math.floor(cx) + ix, vy = (int) Math.floor(cy) + iy, vz = (int) Math.floor(cz) + iz;
                    double dx = vx + 0.5 - cx, dy = (vy + 0.5 - cy) / leafFlat, dz = vz + 0.5 - cz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz) / rad;
                    double noise = rnd.nextDouble() * 0.55;
                    if (d + noise < 1.0 && rnd.nextDouble() < leafDensity + 0.1) {
                        model.setLeaf(vx, vy, vz);
                    }
                }
            }
        }
    }
}
