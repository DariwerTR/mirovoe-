package com.wildlands.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Чертёж дерева: набор блоков относительно основания ствола (0, 0, 0).
 * Не зависит от Minecraft, поэтому форму деревьев можно проверять и настраивать отдельно от игры
 * (см. tools/preview/Preview.java).
 */
public final class TreeBlueprint {
    public static final int LEAF = 1;
    public static final int LOG_X = 2;
    public static final int LOG_Y = 3;
    public static final int LOG_Z = 4;

    /** Максимальное расстояние листа до ближайшего бревна, при котором лист в игре не опадает. */
    public static final int MAX_LEAF_DISTANCE = 6;

    public record Voxel(int x, int y, int z, int kind, int distance) {
        public boolean isLog() {
            return kind >= LOG_X;
        }
    }

    private static final int OFF = 512;
    private final Map<Long, Integer> kinds = new HashMap<>();
    private final Map<Long, Integer> distances = new HashMap<>();

    private static long key(int x, int y, int z) {
        return ((long) (x + OFF) & 1023L) | (((long) (z + OFF) & 1023L) << 10) | (((long) (y + OFF) & 1023L) << 20);
    }

    private static int kx(long k) {
        return (int) (k & 1023L) - OFF;
    }

    private static int kz(long k) {
        return (int) ((k >> 10) & 1023L) - OFF;
    }

    private static int ky(long k) {
        return (int) ((k >> 20) & 1023L) - OFF;
    }

    void setLog(int x, int y, int z, int kind) {
        kinds.put(key(x, y, z), kind);
    }

    void setLeaf(int x, int y, int z) {
        kinds.putIfAbsent(key(x, y, z), LEAF);
    }

    public int kindAt(int x, int y, int z) {
        return kinds.getOrDefault(key(x, y, z), 0);
    }

    /**
     * Завершающая обработка: отбрасывает блоки за пределами допустимой области,
     * считает расстояние от каждого листа до бревна (как это делает игра) и удаляет листья,
     * которые были бы слишком далеко и сразу опали.
     */
    void finish(int maxHorizontalRadius, int minY, int maxY) {
        Iterator<Long> it = kinds.keySet().iterator();
        while (it.hasNext()) {
            long k = it.next();
            if (Math.abs(kx(k)) > maxHorizontalRadius || Math.abs(kz(k)) > maxHorizontalRadius
                    || ky(k) < minY || ky(k) > maxY) {
                it.remove();
            }
        }
        distances.clear();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Map.Entry<Long, Integer> e : kinds.entrySet()) {
            if (e.getValue() >= LOG_X) {
                distances.put(e.getKey(), 0);
                queue.add(e.getKey());
            }
        }
        int[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int d = distances.get(k);
            if (d >= MAX_LEAF_DISTANCE) {
                continue;
            }
            for (int[] o : n) {
                long nk = key(kx(k) + o[0], ky(k) + o[1], kz(k) + o[2]);
                Integer kind = kinds.get(nk);
                if (kind != null && kind == LEAF && !distances.containsKey(nk)) {
                    distances.put(nk, d + 1);
                    queue.add(nk);
                }
            }
        }
        kinds.entrySet().removeIf(e -> e.getValue() == LEAF && !distances.containsKey(e.getKey()));
    }

    public List<Voxel> voxels() {
        List<Voxel> out = new ArrayList<>(kinds.size());
        for (Map.Entry<Long, Integer> e : kinds.entrySet()) {
            long k = e.getKey();
            out.add(new Voxel(kx(k), ky(k), kz(k), e.getValue(), distances.getOrDefault(k, 0)));
        }
        return out;
    }

    public int count(boolean logs) {
        int c = 0;
        for (int v : kinds.values()) {
            if ((v >= LOG_X) == logs) {
                c++;
            }
        }
        return c;
    }

    public int maxHorizontalExtent() {
        int m = 0;
        for (long k : kinds.keySet()) {
            m = Math.max(m, Math.max(Math.abs(kx(k)), Math.abs(kz(k))));
        }
        return m;
    }

    public int topY() {
        int m = 0;
        for (long k : kinds.keySet()) {
            m = Math.max(m, ky(k));
        }
        return m;
    }
}
