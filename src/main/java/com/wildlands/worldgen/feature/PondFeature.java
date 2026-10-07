package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Пруд на ровной земле. Не использует биомные запросы за пределами области генерации (на этом падал
 * ванильный LakeFeature) и не выходит за радиус 11 от точки (область генерации даёт 16). Вода ставится только если вокруг пруда
 * (кольцо шириной 1) земля на том же уровне, то есть вода никуда не потечёт.
 */
public class PondFeature extends Feature<PondConfig> {
    private static final int FLAGS = 18;

    public PondFeature(Codec<PondConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<PondConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        PondConfig config = context.config();
        int lo = Math.min(config.minRadius(), config.maxRadius());
        int hi = Math.max(config.minRadius(), config.maxRadius());
        double rx = lo + random.nextInt(hi - lo + 1) + random.nextDouble();
        double rz = lo + random.nextInt(hi - lo + 1) + random.nextDouble();
        // wobble: неровный берег
        double phase = random.nextDouble() * Math.PI * 2;
        int ground = origin.getY() - 1;
        int reach = (int) Math.ceil(Math.max(rx, rz) * 1.75) + 1;
        if (reach > 11) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double nx = dx / rx, nz = dz / rz;
                double ang = Math.atan2(dz, dx);
                double lim = 1.0 + 0.18 * Math.sin(ang * 3 + phase) + 0.1 * Math.sin(ang * 5 - phase);
                boolean inside = nx * nx + nz * nz <= lim * lim;
                boolean ring = !inside && nx * nx + nz * nz <= (lim + 0.45) * (lim + 0.45);
                if (inside) {
                    cells.add(new int[] {dx, dz});
                }
                pos.set(origin.getX() + dx, ground, origin.getZ() + dz);
                if (inside || ring) {
                    // дно и берег: твёрдая земля на уровне поверхности
                    if (!solidSoil(level, pos)) {
                        return false;
                    }
                    // над берегом и водой свободно (деревьев и построек нет)
                    pos.set(origin.getX() + dx, ground + 1, origin.getZ() + dz);
                    if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(BlockTags.REPLACEABLE_BY_TREES)) {
                        return false;
                    }
                }
            }
        }
        if (cells.size() < 8) {
            return false;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int[] c : cells) {
            int x = origin.getX() + c[0], z = origin.getZ() + c[1];
            double d = Math.sqrt((c[0] / rx) * (c[0] / rx) + (c[1] / rz) * (c[1] / rz));
            int depth = d < 0.55 ? 2 : 1;
            for (int k = 0; k < depth; k++) {
                pos.set(x, ground - k, z);
                level.setBlock(pos, water, FLAGS);
            }
            // дно: глина или песок/гравий, в зависимости от глубины
            pos.set(x, ground - depth, z);
            if (solidSoil(level, pos)) {
                level.setBlock(pos, d < 0.55 ? Blocks.CLAY.defaultBlockState() : Blocks.DIRT.defaultBlockState(), FLAGS);
            }
            // растения над водой убираем
            pos.set(x, ground + 1, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            if (random.nextInt(9) == 0 && d < 0.8) {
                level.setBlock(pos, Blocks.LILY_PAD.defaultBlockState(), FLAGS);
            }
        }
        return true;
    }

    private static boolean solidSoil(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, s -> s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.CLAY)
                || s.is(Blocks.GRAVEL) || s.is(Blocks.STONE));
    }
}
