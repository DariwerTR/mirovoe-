package com.wildlands.felling;

import java.util.HashSet;
import java.util.Set;

/**
 * Динамика падения: однородный стержень на шарнире (угловое ускорение 3g/(2L) * sin(theta)).
 * Каждый вызов {@link #tick()} это один игровой тик. Когда хвост сместился минимум на блок, строится новый кадр;
 * если кадр упирается в землю или ствол соседнего дерева, угол уточняется бисекцией и падение заканчивается.
 *
 * Правила столкновений:
 * <ul>
 *   <li>толстая древесина (бревно, толстая ветвь) упирается в землю, постройки, чужие стволы и игроков;</li>
 *   <li>тонкие ветки и листва ломаются о землю и стволы, а чужие ветки и листву проламывают сами;</li>
 *   <li>ячейки самой падающей части свободны.</li>
 * </ul>
 */
public final class FallSim {
    /** Больше 90 градусов: на склоне вниз дерево может лечь чуть дальше горизонтали. */
    public static final double THETA_MAX = Math.toRadians(100);
    private static final double G = 9.8;

    private final Fall fall;
    private final Collider col;
    private final double timeScale;
    private Set<Long> own = new HashSet<>();
    private double theta;
    private double omega;
    private double lastTheta = 0;
    private boolean done;
    private double impact;
    private int ticks;

    public FallSim(Fall fall, Collider col, double timeScale) {
        this.fall = fall;
        this.col = col;
        this.timeScale = timeScale;
        for (long k : fall.piece.wood) {
            own.add(k);
        }
        for (long k : fall.piece.leaves) {
            own.add(k);
        }
        // дерево уже надрезано: начальный наклон и толчок
        this.theta = Math.toRadians(3.0);
        this.omega = 0.25;
    }

    public boolean done() {
        return done;
    }

    /** Скорость хвоста в блоках за секунду в момент остановки. */
    public double impactSpeed() {
        return impact * fall.radius;
    }

    /** Угловая скорость, рад/с. */
    public double omega() {
        return omega;
    }

    public double theta() {
        return lastTheta;
    }

    public int ticks() {
        return ticks;
    }

    /** Один тик. Возвращает новый кадр, если пора его показать, иначе null. */
    public Frame tick() {
        if (done) {
            return null;
        }
        ticks++;
        double k = 1.5 * G / fall.height;
        int sub = 4;
        double dt = 0.05 * timeScale / sub;
        for (int i = 0; i < sub; i++) {
            omega += k * Math.sin(theta) * dt;
            theta += omega * dt;
        }
        if (theta > THETA_MAX) {
            theta = THETA_MAX;
        }
        if (fall.radius * (theta - lastTheta) < 1.0 && theta < THETA_MAX) {
            return null;
        }
        Frame cand = fall.frame(theta);
        if (!blockedWood(cand)) {
            Frame f = commit(cand);
            lastTheta = theta;
            if (theta >= THETA_MAX) {
                done = true;
                impact = omega;
            }
            return f;
        }
        double lo = lastTheta, hi = theta;
        for (int i = 0; i < 10; i++) {
            double mid = 0.5 * (lo + hi);
            if (blockedWood(fall.frame(mid))) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        Frame f = commit(fall.frame(lo));
        lastTheta = lo;
        done = true;
        impact = omega;
        return f;
    }

    /** Получилось ли дерево сдвинуться с места (угол больше 2 градусов). */
    public boolean moved() {
        return lastTheta > Math.toRadians(2.0);
    }

    private boolean blockedWood(Frame f) {
        for (int i = 0; i < f.cells.length; i++) {
            if (!f.wood[i]) {
                continue;
            }
            long c = f.cells[i];
            if (own.contains(c)) {
                continue;
            }
            int cls = col.classify(Cells.x(c), Cells.y(c), Cells.z(c));
            boolean thick = (fall.piece.woodInfo[f.src[i]] & WorldView.F_THICK) != 0;
            if (cls == Collider.PLAYER || (thick && (cls == Collider.SOLID || cls == Collider.FOREIGN_THICK))) {
                return true;
            }
        }
        return false;
    }

    /** Убирает из кадра ломающиеся элементы (и помечает их сломанными навсегда), запоминает занятые ячейки. */
    private Frame commit(Frame f) {
        int n = f.cells.length;
        boolean[] keep = new boolean[n];
        int kept = 0;
        for (int i = 0; i < n; i++) {
            long c = f.cells[i];
            boolean ok = true;
            if (!own.contains(c)) {
                int cls = col.classify(Cells.x(c), Cells.y(c), Cells.z(c));
                if (f.wood[i]) {
                    boolean thick = (fall.piece.woodInfo[f.src[i]] & WorldView.F_THICK) != 0;
                    if (cls == Collider.PLAYER || cls == Collider.SOLID || cls == Collider.FOREIGN_THICK) {
                        ok = false;
                        if (!thick) {
                            fall.woodDead[f.src[i]] = true;
                        }
                    }
                } else if (cls == Collider.SOLID || cls == Collider.FOREIGN_LEAF || cls == Collider.FOREIGN_THIN
                        || cls == Collider.FOREIGN_THICK) {
                    // лист в этой ячейке просто не показываем; в другом кадре он может появиться снова
                    ok = false;
                }
            }
            keep[i] = ok;
            if (ok) {
                kept++;
            }
        }
        long[] cells = new long[kept];
        int[] src = new int[kept];
        boolean[] wood = new boolean[kept];
        int[] axis = new int[kept];
        int p = 0;
        Set<Long> nextOwn = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                cells[p] = f.cells[i];
                src[p] = f.src[i];
                wood[p] = f.wood[i];
                axis[p] = f.axis[i];
                nextOwn.add(f.cells[i]);
                p++;
            }
        }
        own = nextOwn;
        return new Frame(f.theta, cells, src, wood, axis);
    }

    /**
     * Наибольший угол, до которого дерево повернётся в данном направлении без остановки (радианы).
     * Для выбора направления падения, ничего не меняет.
     */
    public static double restAngle(Fall fall, Collider col) {
        Set<Long> own = new HashSet<>();
        for (long k : fall.piece.wood) {
            own.add(k);
        }
        for (long k : fall.piece.leaves) {
            own.add(k);
        }
        double step = Math.max(0.03, 0.8 / Math.max(1.0, fall.radius));
        double lo = 0;
        for (double th = step; ; th += step) {
            double t = Math.min(th, THETA_MAX);
            if (blockedWood(fall, col, own, fall.frame(t))) {
                double a = lo, b = t;
                for (int i = 0; i < 8; i++) {
                    double mid = 0.5 * (a + b);
                    if (blockedWood(fall, col, own, fall.frame(mid))) {
                        b = mid;
                    } else {
                        a = mid;
                    }
                }
                return a;
            }
            lo = t;
            if (t >= THETA_MAX) {
                return THETA_MAX;
            }
        }
    }

    private static boolean blockedWood(Fall fall, Collider col, Set<Long> own, Frame f) {
        for (int i = 0; i < f.cells.length; i++) {
            if (!f.wood[i] || own.contains(f.cells[i])) {
                continue;
            }
            long c = f.cells[i];
            int cls = col.classify(Cells.x(c), Cells.y(c), Cells.z(c));
            boolean thick = (fall.piece.woodInfo[f.src[i]] & WorldView.F_THICK) != 0;
            if (cls == Collider.PLAYER || (thick && (cls == Collider.SOLID || cls == Collider.FOREIGN_THICK))) {
                return true;
            }
        }
        return false;
    }
}
