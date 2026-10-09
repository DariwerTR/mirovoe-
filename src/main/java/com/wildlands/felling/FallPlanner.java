package com.wildlands.felling;

/**
 * Выбор направления падения.
 * Основа: от игрока (как у настоящего лесоруба, дерево валят в сторону пропила, а сам он уходит назад).
 * Добавка: наклон кроны (центр масс), не больше 50 градусов от основы. Если путь перекрыт (склон, другие деревья,
 * здание), пробуем соседние направления и берём то, где дерево ляжет дальше всего.
 */
public final class FallPlanner {
    private FallPlanner() {
    }

    /** Результат выбора. */
    public record Choice(Fall fall, double restAngle, double deviationDeg) {
    }

    public static Choice choose(Piece piece, Collider col, double awayX, double awayZ, double leanWeight) {
        double al = Math.hypot(awayX, awayZ);
        double ax = al < 1e-6 ? 1 : awayX / al, az = al < 1e-6 ? 0 : awayZ / al;
        Fall probe = new Fall(piece, ax, az);
        double[] lean = probe.leanVector();
        double bx = ax + leanWeight * lean[0], bz = az + leanWeight * lean[1];
        double base = Math.atan2(az, ax);
        double want = Math.atan2(bz, bx);
        if (Math.hypot(bx, bz) < 1e-3) {
            want = base;
        }
        double dev = norm(want - base);
        double lim = Math.toRadians(50);
        if (Math.abs(dev) > lim) {
            want = base + Math.signum(dev) * lim;
        }

        double[] offsets = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
        Choice best = null;
        double bestScore = -1e9;
        for (double off : offsets) {
            double ang = want + Math.toRadians(off);
            Fall f = new Fall(piece, Math.cos(ang), Math.sin(ang));
            double rest = FallSim.restAngle(f, col);
            double fromAway = Math.abs(norm(ang - base));
            double score = rest / FallSim.THETA_MAX - 0.10 * Math.abs(Math.toRadians(off)) - (fromAway > Math.toRadians(100) ? 0.5 : 0);
            if (best == null || score > bestScore + 1e-9) {
                best = new Choice(f, rest, Math.toDegrees(fromAway));
                bestScore = score;
            }
            if (off == 0 && rest >= Math.toRadians(55)) {
                break;
            }
        }
        return best;
    }

    private static double norm(double a) {
        while (a > Math.PI) {
            a -= 2 * Math.PI;
        }
        while (a < -Math.PI) {
            a += 2 * Math.PI;
        }
        return a;
    }
}
