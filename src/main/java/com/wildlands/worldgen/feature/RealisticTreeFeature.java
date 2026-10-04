package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.wildlands.tree.TreeBlueprint;
import com.wildlands.tree.TreeGenerator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Ставит в мир дерево, форму которого строит {@link TreeGenerator}.
 * Вся геометрия живёт в пакете com.wildlands.tree и не зависит от Minecraft; здесь только перенос блоков в мир.
 */
public class RealisticTreeFeature extends Feature<RealisticTreeConfig> {
    /** 2 = отправить клиентам, 16 = без обновления форм соседей (как у ванильных деревьев при генерации). */
    private static final int FLAGS = 18;

    public RealisticTreeFeature(Codec<RealisticTreeConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<RealisticTreeConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RealisticTreeConfig config = context.config();
        RandomSource random = context.random();

        TreeGenerator.Species species;
        try {
            species = TreeGenerator.Species.byName(config.species());
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Дерево растёт только на земле и только если под ствол есть свободное место.
        if (!level.getBlockState(origin.below()).is(BlockTags.DIRT)) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 4; y++) {
            pos.set(origin.getX(), origin.getY() + y, origin.getZ());
            if (!isFree(level, pos)) {
                return false;
            }
        }

        int lo = Math.min(config.minHeight(), config.maxHeight());
        int hi = Math.max(config.minHeight(), config.maxHeight());
        int height = lo + random.nextInt(hi - lo + 1);

        TreeBlueprint blueprint = TreeGenerator.generate(species, height, random.nextLong());
        List<TreeBlueprint.Voxel> voxels = blueprint.voxels();

        for (TreeBlueprint.Voxel v : voxels) {
            if (v.isLog()) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, logState(config.log(), v.kind()), FLAGS);
                }
            }
        }
        for (TreeBlueprint.Voxel v : voxels) {
            if (!v.isLog()) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, leafState(config.leaves(), v.distance()), FLAGS);
                }
            }
        }
        return true;
    }

    private static boolean isFree(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }

    private static BlockState logState(BlockState base, int kind) {
        if (!base.hasProperty(BlockStateProperties.AXIS)) {
            return base;
        }
        Direction.Axis axis = switch (kind) {
            case TreeBlueprint.LOG_X -> Direction.Axis.X;
            case TreeBlueprint.LOG_Z -> Direction.Axis.Z;
            default -> Direction.Axis.Y;
        };
        return base.setValue(BlockStateProperties.AXIS, axis);
    }

    private static BlockState leafState(BlockState base, int distance) {
        BlockState state = base;
        if (state.hasProperty(BlockStateProperties.DISTANCE)) {
            state = state.setValue(BlockStateProperties.DISTANCE, Math.max(1, Math.min(7, distance)));
        }
        if (state.hasProperty(BlockStateProperties.PERSISTENT)) {
            state = state.setValue(BlockStateProperties.PERSISTENT, false);
        }
        return state;
    }
}
