package com.wildlands.felling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Геометрия падения: твёрдое тело (древесина и листва) поворачивается вокруг шарнира на верхней грани пенька,
 * на дальнем от падения краю разреза. Для любого угла {@link #frame} возвращает занятые ячейки.
 *
 * Древесина растеризуется по рёбрам графа (соседние блоки соединяются линией 3D DDA), а не по отдельным точкам:
 * тонкий ствол остаётся тонким и связным под любым наклоном, без дыр и без «разжирения».
 * Листва переносится поточечно (ей дыры не мешают).
 */
public final class Fall {
    public final Piece piece;
    /** Единичный горизонтальный вектор направления падения. */
    public final double dirX, dirZ;
    /** Шарнир (мировые координаты). */
    public final double pivX, pivY, pivZ;
    /** Высота падающей части над шарниром и максимальный радиус от шарнира. */
    public final double height, radius;
    /** Рёбра графа древесины (пары индексов). */
    private final int[] edgeA, edgeB;
    /** Сломанные блоки (по индексам) больше не возвращаются. */
    public final boolean[] woodDead, leafDead;
    private final double[] wx, wy, wz, lx, ly, lz;

    public Fall(Piece piece, double dirX, double dirZ) {
        this.piece = piece;
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) {
            dirX = 1;
            dirZ = 0;
            len = 1;
        }
        this.dirX = dirX / len;
        this.dirZ = dirZ / len;

        int n = piece.wood.length;
        wx = new double[n];
        wy = new double[n];
        wz = new double[n];
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long k = piece.wood[i];
            wx[i] = Cells.x(k) + 0.5;
            wy[i] = Cells.y(k) + 0.5;
            wz[i] = Cells.z(k) + 0.5;
            index.put(k, i);
        }
        int m = piece.leaves.length;
        lx = new double[m];
        ly = new double[m];
        lz = new double[m];
        for (int i = 0; i < m; i++) {
            long k = piece.leaves[i];
            lx[i] = Cells.x(k) + 0.5;
            ly[i] = Cells.y(k) + 0.5;
            lz[i] = Cells.z(k) + 0.5;
        }
        woodDead = new boolean[n];
        leafDead = new boolean[m];

        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long k = piece.wood[i];
            int x = Cells.x(k), y = Cells.y(k), z = Cells.z(k);
            Integer j;
            if ((j = index.get(Cells.pack(x + 1, y, z))) != null && WorldView.linked(piece.woodInfo[i], piece.woodInfo[j], 4)) {
                edges.add(new int[]{i, j});
            }
            if ((j = index.get(Cells.pack(x, y + 1, z))) != null && WorldView.linked(piece.woodInfo[i], piece.woodInfo[j], 0)) {
                edges.add(new int[]{i, j});
            }
            if ((j = index.get(Cells.pack(x, y, z + 1))) != null && WorldView.linked(piece.woodInfo[i], piece.woodInfo[j], 3)) {
                edges.add(new int[]{i, j});
            }
        }
        edgeA = new int[edges.size()];
        edgeB = new int[edges.size()];
        for (int i = 0; i < edges.size(); i++) {
            edgeA[i] = edges.get(i)[0];
            edgeB[i] = edges.get(i)[1];
        }

        // Шарнир. Нижний слой падающей древесины рядом с разрезом (не дальше 2 блоков по горизонтали).
        int yb = Integer.MAX_VALUE;
        for (long k : piece.wood) {
            if (Math.max(Math.abs(Cells.x(k) - piece.cutX), Math.abs(Cells.z(k) - piece.cutZ)) <= 2) {
                yb = Math.min(yb, Cells.y(k));
            }
        }
        if (yb == Integer.MAX_VALUE) {
            for (long k : piece.wood) {
                yb = Math.min(yb, Cells.y(k));
            }
        }
        // Шарнир на уровне верхней грани пенька. Если разрез сделан в самом нижнем блоке (yb == cutY + 1),
        // это верх земли под ним; при наклонной древесине без блока над разрезом остаётся шов нужной высоты.
        double py = yb - 1;
        double sumAlong = 0, sumPerp = 0;
        double maxAlong = -1e9;
        int cnt = 0;
        double perpX = -this.dirZ, perpZ = this.dirX;
        for (long k : piece.wood) {
            if (Cells.y(k) != yb || Math.max(Math.abs(Cells.x(k) - piece.cutX), Math.abs(Cells.z(k) - piece.cutZ)) > 2) {
                continue;
            }
            double cx = Cells.x(k) + 0.5, cz = Cells.z(k) + 0.5;
            double along = cx * this.dirX + cz * this.dirZ;
            maxAlong = Math.max(maxAlong, along + 0.5 * (Math.abs(this.dirX) + Math.abs(this.dirZ)));
            sumPerp += cx * perpX + cz * perpZ;
            sumAlong += along;
            cnt++;
        }
        if (cnt == 0) {
            maxAlong = (piece.cutX + 0.5) * this.dirX + (piece.cutZ + 0.5) * this.dirZ + 0.5;
            sumPerp = (piece.cutX + 0.5) * perpX + (piece.cutZ + 0.5) * perpZ;
            cnt = 1;
        }
        double perp = sumPerp / cnt;
        this.pivX = maxAlong * this.dirX + perp * perpX;
        this.pivZ = maxAlong * this.dirZ + perp * perpZ;
        this.pivY = py;

        double h = 0, r = 1;
        for (int i = 0; i < n; i++) {
            h = Math.max(h, wy[i] + 0.5 - py);
            r = Math.max(r, Math.sqrt(sq(wx[i] - pivX) + sq(wy[i] - py) + sq(wz[i] - pivZ)));
        }
        for (int i = 0; i < m; i++) {
            h = Math.max(h, ly[i] + 0.5 - py);
            r = Math.max(r, Math.sqrt(sq(lx[i] - pivX) + sq(ly[i] - py) + sq(lz[i] - pivZ)));
        }
        this.height = Math.max(h, 2);
        this.radius = r;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Центр масс по горизонтали относительно шарнира, в долях высоты (для наклона кроны). */
    public double[] leanVector() {
        double sx = 0, sz = 0, w = 0;
        for (int i = 0; i < wx.length; i++) {
            double wt = 3.0;
            sx += (wx[i] - pivX) * wt;
            sz += (wz[i] - pivZ) * wt;
            w += wt;
        }
        for (int i = 0; i < lx.length; i++) {
            sx += lx[i] - pivX;
            sz += lz[i] - pivZ;
            w += 1.0;
        }
        if (w == 0) {
            return new double[]{0, 0};
        }
        return new double[]{sx / w / height, sz / w / height};
    }

    private void rot(double c, double s, double x, double y, double z, double[] o) {
        double rx = x - pivX, ry = y - pivY, rz = z - pivZ;
        double along = rx * dirX + rz * dirZ;
        double ux = rx - along * dirX, uz = rz - along * dirZ;
        double a2 = along * c + ry * s;
        double y2 = -along * s + ry * c;
        o[0] = pivX + ux + a2 * dirX;
        o[1] = pivY + y2;
        o[2] = pivZ + uz + a2 * dirZ;
    }

    /** Ось (0 X, 1 Y, 2 Z) единичного вектора после поворота на угол с косинусом c и синусом s. */
    private int rotAxis(double c, double s, int axis) {
        double vx = axis == 0 ? 1 : 0, vy = axis == 1 ? 1 : 0, vz = axis == 2 ? 1 : 0;
        double along = vx * dirX + vz * dirZ;
        double ux = vx - along * dirX, uz = vz - along * dirZ;
        double a2 = along * c + vy * s;
        double y2 = -along * s + vy * c;
        return dominant(ux + a2 * dirX, y2, uz + a2 * dirZ);
    }

    private static int dominant(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ay >= ax && ay >= az) {
            return 1;
        }
        return ax >= az ? 0 : 2;
    }

    /** Ось бревна по исходной информации: вертикальное бревно (флаг F_VERT) считается осью Y, иначе по соседям. */
    private int sourceAxis(int i) {
        int f = piece.woodInfo[i];
        if ((f & WorldView.F_VERT) != 0) {
            return 1;
        }
        if ((f & WorldView.F_AXIS_X) != 0) {
            return 0;
        }
        return (f & WorldView.F_AXIS_Z) != 0 ? 2 : -1;
    }

    /** Занятые ячейки при повороте на угол theta (радианы). */
    public Frame frame(double theta) {
        double c = Math.cos(theta), s = Math.sin(theta);
        Map<Long, int[]> cells = new HashMap<>(); // ключ -> {приоритет, источник, ось, лист?}
        double[] a = new double[3], b = new double[3];
        int n = wx.length;
        // концы: сами блоки
        for (int i = 0; i < n; i++) {
            if (woodDead[i]) {
                continue;
            }
            rot(c, s, wx[i], wy[i], wz[i], a);
            int cx = (int) Math.floor(a[0]), cy = (int) Math.floor(a[1]), cz = (int) Math.floor(a[2]);
            int sa = sourceAxis(i);
            int axis = sa >= 0 ? rotAxis(c, s, sa) : -1;
            putWood(cells, Cells.pack(cx, cy, cz), 200 + thick(i), i, axis);
        }
        // рёбра: линия между повёрнутыми центрами
        for (int e = 0; e < edgeA.length; e++) {
            int i = edgeA[e], j = edgeB[e];
            if (woodDead[i] || woodDead[j]) {
                continue;
            }
            rot(c, s, wx[i], wy[i], wz[i], a);
            rot(c, s, wx[j], wy[j], wz[j], b);
            int edgeAxis = dominant(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
            line(cells, a, b, i, j, edgeAxis);
        }
        // листва
        int m = lx.length;
        List<long[]> leafList = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (leafDead[i]) {
                continue;
            }
            rot(c, s, lx[i], ly[i], lz[i], a);
            long k = Cells.pack((int) Math.floor(a[0]), (int) Math.floor(a[1]), (int) Math.floor(a[2]));
            leafList.add(new long[]{k, i});
        }

        int total = cells.size();
        Map<Long, Integer> leafPick = new HashMap<>();
        for (long[] l : leafList) {
            if (!cells.containsKey(l[0]) && !leafPick.containsKey(l[0])) {
                leafPick.put(l[0], (int) l[1]);
            }
        }
        total += leafPick.size();
        long[] outCells = new long[total];
        int[] outSrc = new int[total];
        boolean[] outWood = new boolean[total];
        int[] outAxis = new int[total];
        int p = 0;
        for (Map.Entry<Long, int[]> en : cells.entrySet()) {
            int[] v = en.getValue();
            outCells[p] = en.getKey();
            outSrc[p] = v[1];
            outWood[p] = true;
            outAxis[p] = v[2] >= 0 ? v[2] : fallbackAxis(c, s, v[1]);
            p++;
        }
        for (Map.Entry<Long, Integer> en : leafPick.entrySet()) {
            outCells[p] = en.getKey();
            outSrc[p] = en.getValue();
            outWood[p] = false;
            outAxis[p] = 1;
            p++;
        }
        return new Frame(theta, outCells, outSrc, outWood, outAxis);
    }

    /** Если у блока нет собственной оси (ветвь), ось по вертикали, повёрнутой на тот же угол. */
    private int fallbackAxis(double c, double s, int src) {
        return rotAxis(c, s, 1);
    }

    private int thick(int i) {
        return Math.min(99, (piece.woodInfo[i] & WorldView.F_THICK) != 0 ? 50 : 10);
    }

    private static void putWood(Map<Long, int[]> cells, long key, int priority, int src, int axis) {
        int[] old = cells.get(key);
        if (old == null || old[0] < priority) {
            cells.put(key, new int[]{priority, src, axis});
        }
    }

    /** Линия 3D DDA (Amanatides-Woo) между двумя точками: цепочка ячеек, соседних по граням. */
    private void line(Map<Long, int[]> cells, double[] a, double[] b, int ia, int ib, int axis) {
        int x = (int) Math.floor(a[0]), y = (int) Math.floor(a[1]), z = (int) Math.floor(a[2]);
        int ex = (int) Math.floor(b[0]), ey = (int) Math.floor(b[1]), ez = (int) Math.floor(b[2]);
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tDx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMx = dx == 0 ? Double.POSITIVE_INFINITY : (sx > 0 ? (x + 1 - a[0]) : (a[0] - x)) * tDx;
        double tMy = dy == 0 ? Double.POSITIVE_INFINITY : (sy > 0 ? (y + 1 - a[1]) : (a[1] - y)) * tDy;
        double tMz = dz == 0 ? Double.POSITIVE_INFINITY : (sz > 0 ? (z + 1 - a[2]) : (a[2] - z)) * tDz;
        int guard = Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z) + 3;
        double t = 0;
        for (int step = 0; step <= guard; step++) {
            int src = t < 0.5 ? ia : ib;
            if (!(woodDead[src])) {
                putWood(cells, Cells.pack(x, y, z), 100 + thick(src), src, axis);
            } else {
                int other = src == ia ? ib : ia;
                if (!woodDead[other]) {
                    putWood(cells, Cells.pack(x, y, z), 100 + thick(other), other, axis);
                }
            }
            if (x == ex && y == ey && z == ez) {
                break;
            }
            if (tMx <= tMy && tMx <= tMz) {
                t = tMx;
                tMx += tDx;
                x += sx;
            } else if (tMy <= tMz) {
                t = tMy;
                tMy += tDy;
                y += sy;
            } else {
                t = tMz;
                tMz += tDz;
                z += sz;
            }
        }
    }
}
