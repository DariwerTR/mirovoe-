package com.wildlands.felling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Поиск падающей части дерева после того, как в точке (cutX, cutY, cutZ) убрали блок ствола.
 *
 * <ol>
 *   <li>Связная древесина (по граням) вокруг разреза. Слишком большая постройка это не дерево.</li>
 *   <li>Корни: вертикальные бревна, стоящие на твёрдой земле. Всё, что связано с корнями, остаётся.</li>
 *   <li>Остальное падает. Лежащее на земле бревно корней не имеет (оно горизонтальное), поэтому не падает повторно.</li>
 *   <li>Листва: каждый природный лист достаётся ближайшей древесине (не дальше 6 блоков по листве).
 *       При равной дальности лист остаётся на месте, чтобы не утащить чужую крону.</li>
 *   <li>Отсев построек: нужна природная листва или ветви, которых нет в обычных постройках.</li>
 * </ol>
 */
public final class TreeScan {
    public static final int MAX_LEAF_STEPS = 6;
    private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};
    private static final int LABEL_GROUND = 0, LABEL_FALL = 1, LABEL_FOREIGN = 2;

    /** Причина отказа последнего вызова (для отладки и тестов). */
    public static String lastReason = "";

    private TreeScan() {
    }

    public static Piece scan(WorldView w, int cx, int cy, int cz, int maxWood, int maxTotal) {
        // 1. связная древесина вокруг разреза
        Map<Long, Integer> woodIdx = new HashMap<>();
        List<Long> woodList = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (int di = 0; di < 6; di++) {
            int[] d = DIRS[di];
            int x = cx + d[0], y = cy + d[1], z = cz + d[2];
            if (WorldView.kind(w.info(x, y, z)) == WorldView.K_WOOD && !woodIdx.containsKey(Cells.pack(x, y, z))) {
                long k = Cells.pack(x, y, z);
                woodIdx.put(k, woodList.size());
                woodList.add(k);
                queue.add(k);
            }
        }
        if (woodList.isEmpty()) {
            lastReason = "рядом с разрезом нет древесины";
            return null;
        }
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int x = Cells.x(k), y = Cells.y(k), z = Cells.z(k);
            int infoHere = w.info(x, y, z);
            for (int di = 0; di < 6; di++) {
                int[] d = DIRS[di];
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                long nk = Cells.pack(nx, ny, nz);
                if (woodIdx.containsKey(nk)) {
                    continue;
                }
                int ni = w.info(nx, ny, nz);
                if (WorldView.kind(ni) != WorldView.K_WOOD || !WorldView.linked(infoHere, ni, di)) {
                    continue;
                }
                if (woodList.size() >= maxWood) {
                    lastReason = "слишком много древесины (больше " + maxWood + ")";
                    return null;
                }
                woodIdx.put(nk, woodList.size());
                woodList.add(nk);
                queue.add(nk);
            }
        }

        // 2. корни и всё, что к ним привязано
        boolean[] grounded = new boolean[woodList.size()];
        for (int i = 0; i < woodList.size(); i++) {
            long k = woodList.get(i);
            int info = w.info(Cells.x(k), Cells.y(k), Cells.z(k));
            if ((info & WorldView.F_VERT) != 0
                    && WorldView.kind(w.info(Cells.x(k), Cells.y(k) - 1, Cells.z(k))) == WorldView.K_SOLID) {
                grounded[i] = true;
                queue.add(k);
            }
        }
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int x = Cells.x(k), y = Cells.y(k), z = Cells.z(k);
            int infoHere = w.info(x, y, z);
            for (int di = 0; di < 6; di++) {
                int[] d = DIRS[di];
                Integer j = woodIdx.get(Cells.pack(x + d[0], y + d[1], z + d[2]));
                if (j != null && !grounded[j]
                        && WorldView.linked(infoHere, w.info(x + d[0], y + d[1], z + d[2]), di)) {
                    grounded[j] = true;
                    queue.add(woodList.get(j));
                }
            }
        }
        int groundedCount = 0;
        List<Long> fall = new ArrayList<>();
        for (int i = 0; i < woodList.size(); i++) {
            // Наплывы у основания на уровне разреза и ниже (контрфорсы) остаются пеньком, они не поворачиваются
            if (grounded[i] || Cells.y(woodList.get(i)) <= cy) {
                grounded[i] = true;
                groundedCount++;
            } else {
                fall.add(woodList.get(i));
            }
        }
        if (fall.isEmpty()) {
            lastReason = "дерево ещё держится на земле";
            return null;
        }

        // 3. метки древесины для раздачи листвы: свои (земля), падающие, чужие
        Map<Long, Integer> label = new HashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < woodList.size(); i++) {
            long k = woodList.get(i);
            label.put(k, grounded[i] ? LABEL_GROUND : LABEL_FALL);
            minX = Math.min(minX, Cells.x(k));
            maxX = Math.max(maxX, Cells.x(k));
            minY = Math.min(minY, Cells.y(k));
            maxY = Math.max(maxY, Cells.y(k));
            minZ = Math.min(minZ, Cells.z(k));
            maxZ = Math.max(maxZ, Cells.z(k));
        }
        int m = MAX_LEAF_STEPS + 1;
        long volume = (long) (maxX - minX + 2 * m + 1) * (maxY - minY + 2 * m + 1) * (maxZ - minZ + 2 * m + 1);
        List<Long> foreign = new ArrayList<>();
        if (volume <= 400_000L) {
            for (int x = minX - m; x <= maxX + m; x++) {
                for (int z = minZ - m; z <= maxZ + m; z++) {
                    for (int y = minY - m; y <= maxY + m; y++) {
                        if (WorldView.kind(w.info(x, y, z)) == WorldView.K_WOOD) {
                            long k = Cells.pack(x, y, z);
                            if (!label.containsKey(k)) {
                                label.put(k, LABEL_FOREIGN);
                                foreign.add(k);
                            }
                        }
                    }
                }
            }
        }

        // 4. листва: слоями от древесины, на равных побеждает не падающая сторона
        Map<Long, Integer> leafLabel = new HashMap<>();
        List<List<Long>> frontier = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            frontier.add(new ArrayList<>());
        }
        for (int i = 0; i < woodList.size(); i++) {
            frontier.get(grounded[i] ? LABEL_GROUND : LABEL_FALL).add(woodList.get(i));
        }
        frontier.get(LABEL_FOREIGN).addAll(foreign);
        int[] order = {LABEL_GROUND, LABEL_FOREIGN, LABEL_FALL};
        List<Long> leaves = new ArrayList<>();
        for (int step = 0; step < MAX_LEAF_STEPS; step++) {
            List<List<Long>> next = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                next.add(new ArrayList<>());
            }
            for (int l : order) {
                for (long k : frontier.get(l)) {
                    int x = Cells.x(k), y = Cells.y(k), z = Cells.z(k);
                    for (int[] d : DIRS) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        int info = w.info(nx, ny, nz);
                        if (WorldView.kind(info) != WorldView.K_LEAF || (info & WorldView.F_NATURAL) == 0) {
                            continue;
                        }
                        long nk = Cells.pack(nx, ny, nz);
                        if (leafLabel.containsKey(nk)) {
                            continue;
                        }
                        leafLabel.put(nk, l);
                        next.get(l).add(nk);
                        if (l == LABEL_FALL) {
                            leaves.add(nk);
                        }
                    }
                }
            }
            frontier = next;
        }

        // 5. размеры и «деревность»
        if (fall.size() + leaves.size() > maxTotal) {
            lastReason = "слишком большое дерево (" + (fall.size() + leaves.size()) + " блоков)";
            return null;
        }
        int marks = 0;
        for (long k : fall) {
            if ((w.info(Cells.x(k), Cells.y(k), Cells.z(k)) & WorldView.F_NATURAL) != 0) {
                marks++;
            }
        }
        if (leaves.size() < 3 && marks < 2) {
            lastReason = "не похоже на дерево (листвы " + leaves.size() + ", ветвей " + marks + ")";
            return null;
        }

        long[] wood = new long[fall.size()];
        int[] info = new int[fall.size()];
        for (int i = 0; i < wood.length; i++) {
            wood[i] = fall.get(i);
            info[i] = w.info(Cells.x(wood[i]), Cells.y(wood[i]), Cells.z(wood[i]));
        }
        long[] lv = new long[leaves.size()];
        for (int i = 0; i < lv.length; i++) {
            lv[i] = leaves.get(i);
        }
        lastReason = "ok";
        return new Piece(cx, cy, cz, wood, info, lv, groundedCount, leaves.size(), marks);
    }
}
