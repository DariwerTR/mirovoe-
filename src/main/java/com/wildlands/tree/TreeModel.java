package com.wildlands.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Чертёж дерева v2: древесина с толщиной (классы 1..8) и листва. Не зависит от Minecraft.
 * Класс толщины t: ветвь шириной 2t пикселей из 16; 8 это полное бревно.
 */
public final class TreeModel {
    public static final int FULL = 8;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;
    /** Максимальное расстояние листа до древесины, при котором лист не опадает. */
    public static final int MAX_LEAF_DISTANCE = 6;

    /** Порядок направлений для битовой маски связей. */
    public static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};
    public static final int UP = 0, DOWN = 1, NORTH = 2, SOUTH = 3, EAST = 4, WEST = 5;

    /**
     * @param wood     true: древесина, false: лист
     * @param cls      класс толщины (для древесины)
     * @param axis     ось бревна (для cls == FULL)
     * @param conn     маска связей с соседней древесиной, бит i соответствует DIRS[i]
     * @param distance расстояние до древесины (для листа)
     */
    public record Voxel(int x, int y, int z, boolean wood, int cls, int axis, int conn, int distance) {
        public boolean connects(int dir) {
            return (conn & (1 << dir)) != 0;
        }
    }

    private static final int OFF = 512;
    private final Map<Long, Integer> wood = new HashMap<>();
    private final Map<Long, Integer> leaves = new HashMap<>();

    static long key(int x, int y, int z) {
        return ((long) (x + OFF) & 1023L) | (((long) (z + OFF) & 1023L) << 10) | (((long) (y + OFF) & 1023L) << 20);
    }

    static int kx(long k) {
        return (int) (k & 1023L) - OFF;
    }

    static int kz(long k) {
        return (int) ((k >> 10) & 1023L) - OFF;
    }

    static int ky(long k) {
        return (int) ((k >> 20) & 1023L) - OFF;
    }

    void setWood(int x, int y, int z, int cls, int axis) {
        long k = key(x, y, z);
        Integer old = wood.get(k);
        if (old == null || (old >> 2) < cls) {
            wood.put(k, (cls << 2) | axis);
        }
    }

    void setLeaf(int x, int y, int z) {
        leaves.putIfAbsent(key(x, y, z), 0);
    }

    public boolean hasWood(int x, int y, int z) {
        return wood.containsKey(key(x, y, z));
    }

    /** Отбрасывает блоки вне допустимой области, считает расстояния листвы и удаляет недостижимые листья. */
    void finish(int maxHorizontalRadius, int minY, int maxY) {
        wood.keySet().removeIf(k -> out(k, maxHorizontalRadius, minY, maxY));
        pruneDisconnectedWood();
        leaves.keySet().removeIf(k -> out(k, maxHorizontalRadius, minY, maxY) || wood.containsKey(k));
        ArrayDeque<Long> queue = new ArrayDeque<>();
        Map<Long, Integer> dist = new HashMap<>();
        for (Long k : wood.keySet()) {
            dist.put(k, 0);
            queue.add(k);
        }
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int d = dist.get(k);
            if (d >= MAX_LEAF_DISTANCE) {
                continue;
            }
            for (int[] o : DIRS) {
                long nk = key(kx(k) + o[0], ky(k) + o[1], kz(k) + o[2]);
                if (leaves.containsKey(nk) && !dist.containsKey(nk)) {
                    dist.put(nk, d + 1);
                    queue.add(nk);
                }
            }
        }
        Iterator<Map.Entry<Long, Integer>> it = leaves.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> e = it.next();
            Integer d = dist.get(e.getKey());
            if (d == null) {
                it.remove();
            } else {
                e.setValue(d);
            }
        }
    }

    /** Оставляет только древесину, связанную гранями со стволом у основания (0, 0, 0). */
    private void pruneDisconnectedWood() {
        long root = key(0, 0, 0);
        if (!wood.containsKey(root)) {
            return;
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        seen.add(root);
        queue.add(root);
        while (!queue.isEmpty()) {
            long k = queue.poll();
            for (int[] o : DIRS) {
                long nk = key(kx(k) + o[0], ky(k) + o[1], kz(k) + o[2]);
                if (wood.containsKey(nk) && seen.add(nk)) {
                    queue.add(nk);
                }
            }
        }
        wood.keySet().retainAll(seen);
    }

    private static boolean out(long k, int r, int minY, int maxY) {
        return Math.abs(kx(k)) > r || Math.abs(kz(k)) > r || ky(k) < minY || ky(k) > maxY;
    }

    public List<Voxel> voxels() {
        List<Voxel> out = new ArrayList<>(wood.size() + leaves.size());
        for (Map.Entry<Long, Integer> e : wood.entrySet()) {
            long k = e.getKey();
            int x = kx(k), y = ky(k), z = kz(k);
            int conn = 0;
            for (int i = 0; i < 6; i++) {
                if (wood.containsKey(key(x + DIRS[i][0], y + DIRS[i][1], z + DIRS[i][2]))) {
                    conn |= 1 << i;
                }
            }
            out.add(new Voxel(x, y, z, true, e.getValue() >> 2, e.getValue() & 3, conn, 0));
        }
        for (Map.Entry<Long, Integer> e : leaves.entrySet()) {
            long k = e.getKey();
            out.add(new Voxel(kx(k), ky(k), kz(k), false, 0, 0, 0, e.getValue()));
        }
        return out;
    }

    public int woodCount() {
        return wood.size();
    }

    public int leafCount() {
        return leaves.size();
    }

    public int maxHorizontalExtent() {
        int m = 0;
        for (long k : wood.keySet()) {
            m = Math.max(m, Math.max(Math.abs(kx(k)), Math.abs(kz(k))));
        }
        for (long k : leaves.keySet()) {
            m = Math.max(m, Math.max(Math.abs(kx(k)), Math.abs(kz(k))));
        }
        return m;
    }

    public int topY() {
        int m = 0;
        for (long k : wood.keySet()) {
            m = Math.max(m, ky(k));
        }
        for (long k : leaves.keySet()) {
            m = Math.max(m, ky(k));
        }
        return m;
    }
}
