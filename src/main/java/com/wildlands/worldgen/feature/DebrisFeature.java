package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.wildlands.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Упавшие брёвна, пни и кусты. Всё стоит строго на земле: каждый блок проверяется на опору,
 * блоки без опоры не ставятся (никаких висящих брёвен).
 */
public class DebrisFeature extends Feature<DebrisConfig> {
    private static final int FLAGS = 18;

    public DebrisFeature(Codec<DebrisConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<DebrisConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        DebrisConfig config = context.config();
        RandomSource random = context.random();
        if (!ModBlocks.LOG.containsKey(config.species()) || !onSoil(level, origin.below()) || !free(level, origin)) {
            return false;
        }
        Block log = ModBlocks.LOG.get(config.species()).get();
        return switch (config.kind()) {
            case "fallen_log" -> fallenLog(level, origin, random, log);
            case "stump" -> stump(level, origin, random, log);
            case "bush" -> bush(level, origin, random, config.species());
            default -> false;
        };
    }

    private boolean fallenLog(WorldGenLevel level, BlockPos origin, RandomSource random, Block log) {
        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int len = 4 + random.nextInt(5);
        BlockState state = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, dir.getAxis());
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < len; i++) {
            pos.set(origin.getX() + dir.getStepX() * i, origin.getY(), origin.getZ() + dir.getStepZ() * i);
            // бревно лежит на земле, над ним свободно; на неровности или у воды останавливаемся
            if (!free(level, pos) || !onSoil(level, pos.below())) {
                break;
            }
            level.setBlock(pos, state, FLAGS);
            placed++;
            if (random.nextInt(3) == 0) {
                BlockPos above = pos.above();
                if (free(level, above)) {
                    level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), FLAGS);
                }
            }
        }
        return placed >= 3;
    }

    private boolean stump(WorldGenLevel level, BlockPos origin, RandomSource random, Block log) {
        BlockState state = log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        int h = 1 + random.nextInt(2);
        for (int i = 0; i < h; i++) {
            BlockPos p = origin.above(i);
            if (!free(level, p)) {
                return i > 0;
            }
            level.setBlock(p, state, FLAGS);
        }
        BlockPos top = origin.above(h);
        if (free(level, top) && random.nextBoolean()) {
            level.setBlock(top, Blocks.MOSS_CARPET.defaultBlockState(), FLAGS);
        }
        return true;
    }

    /** Куст: небольшой неровный клубок постоянной ванильной листвы прямо на земле. */
    private boolean bush(WorldGenLevel level, BlockPos origin, RandomSource random, String species) {
        Block leaves = switch (species) {
            case "birch" -> Blocks.BIRCH_LEAVES;
            case "spruce" -> Blocks.SPRUCE_LEAVES;
            default -> Blocks.OAK_LEAVES;
        };
        BlockState state = leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = 1 + random.nextInt(2);
        int placed = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.6);
                    if (d > r + 0.4 || random.nextFloat() < 0.2f + 0.15f * dy) {
                        continue;
                    }
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    // нижний слой только на земле, верхний только над уже поставленным листом
                    boolean ok = dy == 0 ? onSoil(level, pos.below()) : level.getBlockState(pos.below()).is(leaves);
                    if (ok && free(level, pos)) {
                        level.setBlock(pos, state, FLAGS);
                        placed++;
                    }
                }
            }
        }
        return placed >= 3;
    }

    private static boolean onSoil(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.DIRT);
    }

    private static boolean free(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, s -> s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES));
    }
}
