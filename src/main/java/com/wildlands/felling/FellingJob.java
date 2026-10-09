package com.wildlands.felling;

import com.mojang.logging.LogUtils;
import com.wildlands.block.BranchBlock;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Одно падающее дерево. Каждый тик: применяет очередь изменений блоков (не больше лимита за тик),
 * затем просит у {@link FallSim} следующий кадр и превращает его в изменения мира.
 * Блоки меняются без обновления соседей (флаги 2 + 16): падение не вызывает лавину физики и освещения.
 */
final class FellingJob {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = 18;
    /** Порядок как у масок связей: вверх, вниз, север, юг, восток, запад. */
    private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    private record Op(long pos, BlockState state, BlockState old) {
    }

    private final ServerLevel level;
    private final Fall fall;
    private final FallSim sim;
    private final McWorldView view;
    private final BlockState[] woodStates;
    private final BlockState[] leafStates;
    private final Map<Long, BlockState> cur = new HashMap<>();
    private final ArrayDeque<Op> ops = new ArrayDeque<>();
    private final Map<Integer, Integer> hurtAt = new HashMap<>();
    private int age;
    private int frames;
    private boolean cracked;

    FellingJob(ServerLevel level, Fall fall, FallSim sim, McWorldView view) {
        this.level = level;
        this.fall = fall;
        this.sim = sim;
        this.view = view;
        Piece p = fall.piece;
        woodStates = new BlockState[p.wood.length];
        leafStates = new BlockState[p.leaves.length];
        for (int i = 0; i < p.wood.length; i++) {
            woodStates[i] = level.getBlockState(BlockPos.of(p.wood[i]));
            cur.put(p.wood[i], woodStates[i]);
        }
        for (int i = 0; i < p.leaves.length; i++) {
            leafStates[i] = level.getBlockState(BlockPos.of(p.leaves[i]));
            cur.put(p.leaves[i], leafStates[i]);
        }
    }

    ServerLevel level() {
        return level;
    }

    /** @return true, когда падение закончено и задачу можно удалять. */
    boolean tick() {
        age++;
        try {
            return step();
        } catch (RuntimeException e) {
            LOGGER.error("Wildlands: ошибка при падении дерева, падение прервано", e);
            return true;
        }
    }

    private boolean step() {
        if (age > 900) {
            // страховка: падение не должно длиться дольше 45 секунд
            finish();
            return true;
        }
        refreshPlayers();
        int budget = FellingConfig.blocksPerTick();
        while (budget > 0 && !ops.isEmpty()) {
            apply(ops.poll());
            budget--;
        }
        if (!ops.isEmpty()) {
            return false;
        }
        if (sim.done()) {
            finish();
            return true;
        }
        Frame f = sim.tick();
        if (f != null) {
            frames++;
            Set<Long> woodKeys = buildOps(f);
            effects(f, woodKeys);
            while (budget > 0 && !ops.isEmpty()) {
                apply(ops.poll());
                budget--;
            }
        }
        if (sim.done() && ops.isEmpty()) {
            finish();
            return true;
        }
        return false;
    }

    private void refreshPlayers() {
        double r = Math.max(24, fall.radius + 8);
        AABB zone = new AABB(fall.pivX - r, fall.pivY - 8, fall.pivZ - r, fall.pivX + r, fall.pivY + fall.height + r, fall.pivZ + r);
        List<Player> list = level.getEntitiesOfClass(Player.class, zone);
        java.util.ArrayList<AABB> boxes = new java.util.ArrayList<>(list.size());
        for (Player pl : list) {
            if (!pl.isSpectator()) {
                boxes.add(pl.getBoundingBox().inflate(0.3));
            }
        }
        view.setPlayers(boxes);
    }

    private static Direction.Axis axisOf(int a) {
        return a == 0 ? Direction.Axis.X : a == 2 ? Direction.Axis.Z : Direction.Axis.Y;
    }

    /** Превращает кадр в очередь изменений мира. Возвращает ячейки древесины кадра (для урона). */
    private Set<Long> buildOps(Frame f) {
        int n = f.size();
        boolean[] valid = new boolean[n];
        Set<Long> woodKeys = new HashSet<>();
        for (int i = 0; i < n; i++) {
            long k = f.cells[i];
            boolean ok = true;
            if (!cur.containsKey(k)) {
                int cls = view.classify(Cells.x(k), Cells.y(k), Cells.z(k));
                ok = f.wood[i]
                        ? (cls == Collider.FREE || cls == Collider.FOREIGN_LEAF || cls == Collider.FOREIGN_THIN)
                        : (cls == Collider.FREE || cls == Collider.PLAYER);
            }
            valid[i] = ok;
            if (ok && f.wood[i]) {
                woodKeys.add(k);
            }
        }
        Map<Long, BlockState> next = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            if (!valid[i]) {
                continue;
            }
            long k = f.cells[i];
            BlockState st;
            if (f.wood[i]) {
                BlockState base = woodStates[f.src[i]];
                if (base.getBlock() instanceof BranchBlock branch) {
                    int mask = 0;
                    for (int d = 0; d < 6; d++) {
                        if (woodKeys.contains(Cells.pack(Cells.x(k) + DIRS[d][0], Cells.y(k) + DIRS[d][1], Cells.z(k) + DIRS[d][2]))) {
                            mask |= 1 << d;
                        }
                    }
                    st = branch.stateFor(base.getValue(BranchBlock.THICKNESS), mask);
                } else if (base.hasProperty(RotatedPillarBlock.AXIS)) {
                    st = base.setValue(RotatedPillarBlock.AXIS, axisOf(f.axis[i]));
                } else {
                    st = base;
                }
            } else {
                st = leafStates[f.src[i]];
            }
            next.put(k, st);
        }
        for (Map.Entry<Long, BlockState> e : cur.entrySet()) {
            if (!next.containsKey(e.getKey())) {
                ops.add(new Op(e.getKey(), null, e.getValue()));
            }
        }
        for (Map.Entry<Long, BlockState> e : next.entrySet()) {
            BlockState old = cur.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) {
                ops.add(new Op(e.getKey(), e.getValue(), old));
            }
        }
        cur.clear();
        cur.putAll(next);
        return woodKeys;
    }

    private void apply(Op op) {
        BlockPos p = BlockPos.of(op.pos);
        if (!level.isLoaded(p)) {
            cur.remove(op.pos);
            return;
        }
        BlockState world = level.getBlockState(p);
        if (op.state == null) {
            if (world.equals(op.old)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            return;
        }
        if (op.old == null) {
            if (!McWorldView.isOpen(world) && !McWorldView.isWood(world) && !world.is(BlockTags.LEAVES)) {
                cur.remove(op.pos);
                return;
            }
        } else if (!world.equals(op.old)) {
            // кто-то изменил блок во время падения: оставляем как есть
            cur.remove(op.pos);
            return;
        }
        level.setBlock(p, op.state, FLAGS);
    }

    private void effects(Frame f, Set<Long> woodKeys) {
        BlockPos pivot = BlockPos.containing(fall.pivX, fall.pivY + 1, fall.pivZ);
        if (woodStates.length == 0) {
            return;
        }
        if (!cracked) {
            cracked = true;
            level.playSound(null, fall.pivX, fall.pivY + 1, fall.pivZ,
                    woodStates[0].getSoundType(level, pivot, null).getBreakSound(), SoundSource.BLOCKS, 3.0F, 0.55F);
        } else if (frames <= 4) {
            level.playSound(null, fall.pivX, fall.pivY + 1, fall.pivZ,
                    woodStates[0].getSoundType(level, pivot, null).getHitSound(), SoundSource.BLOCKS, 1.6F, 0.5F + 0.1F * frames);
        }
        if (leafStates.length > 0 && frames % 2 == 0) {
            double t = f.theta;
            double h = fall.height * 0.75;
            double cx = fall.pivX + fall.dirX * h * Math.sin(t);
            double cz = fall.pivZ + fall.dirZ * h * Math.sin(t);
            double cy = fall.pivY + h * Math.cos(t);
            BlockPos at = BlockPos.containing(cx, cy, cz);
            level.playSound(null, cx, cy, cz, leafStates[0].getSoundType(level, at, null).getBreakSound(),
                    SoundSource.BLOCKS, 1.6F, 0.7F + 0.5F * level.getRandom().nextFloat());
        }
        if (!FellingConfig.hurtMobs() || woodKeys.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long k : woodKeys) {
            minX = Math.min(minX, Cells.x(k));
            maxX = Math.max(maxX, Cells.x(k));
            minY = Math.min(minY, Cells.y(k));
            maxY = Math.max(maxY, Cells.y(k));
            minZ = Math.min(minZ, Cells.z(k));
            maxZ = Math.max(maxZ, Cells.z(k));
        }
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e instanceof Player) {
                continue;
            }
            Integer last = hurtAt.get(e.getId());
            if (last != null && age - last < 8) {
                continue;
            }
            AABB bb = e.getBoundingBox();
            boolean hit = false;
            for (int x = (int) Math.floor(bb.minX); x <= (int) Math.floor(bb.maxX) && !hit; x++) {
                for (int y = (int) Math.floor(bb.minY); y <= (int) Math.floor(bb.maxY) && !hit; y++) {
                    for (int z = (int) Math.floor(bb.minZ); z <= (int) Math.floor(bb.maxZ); z++) {
                        if (woodKeys.contains(Cells.pack(x, y, z))) {
                            hit = true;
                            break;
                        }
                    }
                }
            }
            if (hit) {
                double dist = Math.hypot(e.getX() - fall.pivX, e.getZ() - fall.pivZ);
                float dmg = (float) Math.max(2.0, Math.min(40.0, sim.omega() * Math.max(2.0, dist) * 0.7));
                hurtAt.put(e.getId(), age);
                e.hurtServer(level, level.damageSources().generic(), dmg);
            }
        }
    }

    /** Завершение: расстояния листвы для естественного опадания, звук и пыль удара. */
    private void finish() {
        // расстояния листвы до древесины (как у ванильной листвы: 1 рядом с бревном, 7 и больше значит опадёт)
        Map<Long, Integer> dist = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (Map.Entry<Long, BlockState> e : cur.entrySet()) {
            if (McWorldView.isWood(e.getValue())) {
                dist.put(e.getKey(), 0);
                queue.add(e.getKey());
            }
        }
        // лист рядом с чужим бревном (соседнее дерево) тоже получает опору
        for (Map.Entry<Long, BlockState> e : cur.entrySet()) {
            if (!e.getValue().is(BlockTags.LEAVES)) {
                continue;
            }
            long k = e.getKey();
            for (int[] d : DIRS) {
                long nk = Cells.pack(Cells.x(k) + d[0], Cells.y(k) + d[1], Cells.z(k) + d[2]);
                if (!cur.containsKey(nk) && McWorldView.isWood(level.getBlockState(BlockPos.of(nk)))) {
                    dist.putIfAbsent(k, 1);
                    queue.add(k);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            long k = queue.poll();
            int dk = dist.get(k);
            if (dk >= 6) {
                continue;
            }
            for (int[] d : DIRS) {
                long nk = Cells.pack(Cells.x(k) + d[0], Cells.y(k) + d[1], Cells.z(k) + d[2]);
                BlockState s = cur.get(nk);
                if (s != null && s.is(BlockTags.LEAVES) && !dist.containsKey(nk)) {
                    dist.put(nk, dk + 1);
                    queue.add(nk);
                }
            }
        }
        for (Map.Entry<Long, BlockState> e : cur.entrySet()) {
            BlockState s = e.getValue();
            if (s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.DISTANCE)) {
                int d = Math.min(7, dist.getOrDefault(e.getKey(), 7));
                if (s.getValue(BlockStateProperties.DISTANCE) != d) {
                    BlockPos p = BlockPos.of(e.getKey());
                    if (level.getBlockState(p).equals(s)) {
                        level.setBlock(p, s.setValue(BlockStateProperties.DISTANCE, d), FLAGS);
                    }
                }
            }
        }
        if (!sim.moved() || woodStates.length == 0) {
            return;
        }
        // удар о землю: в самой дальней точке дерева
        long far = 0;
        double best = -1;
        for (Map.Entry<Long, BlockState> e : cur.entrySet()) {
            long k = e.getKey();
            double dx = Cells.x(k) + 0.5 - fall.pivX, dz = Cells.z(k) + 0.5 - fall.pivZ, dy = Cells.y(k) - fall.pivY;
            double dd = dx * dx + dy * dy + dz * dz;
            if (dd > best) {
                best = dd;
                far = k;
            }
        }
        double x = Cells.x(far) + 0.5, y = Cells.y(far) + 0.5, z = Cells.z(far) + 0.5;
        BlockPos at = BlockPos.containing(x, y, z);
        double speed = sim.impactSpeed();
        float vol = (float) Math.max(1.5, Math.min(4.0, 1.0 + speed / 10.0));
        level.playSound(null, x, y, z, woodStates[0].getSoundType(level, at, null).getPlaceSound(), SoundSource.BLOCKS, vol, 0.45F);
        BlockState ground = Blocks.DIRT.defaultBlockState();
        BlockState below = level.getBlockState(at.below());
        if (!McWorldView.isOpen(below) && !McWorldView.isWood(below) && !below.is(BlockTags.LEAVES)) {
            ground = below;
        }
        int count = (int) Math.max(12, Math.min(80, speed * 2.0));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y - 0.4, z, count, 1.2, 0.2, 1.2, 0.1);
        // шелест кроны при приземлении
        if (leafStates.length > 0) {
            level.playSound(null, x, y, z, leafStates[0].getSoundType(level, at, null).getBreakSound(), SoundSource.BLOCKS, 2.5F, 0.6F);
        }
    }

}
