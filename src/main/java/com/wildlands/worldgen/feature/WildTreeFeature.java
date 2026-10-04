package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.wildlands.block.BranchBlock;
import com.wildlands.block.WoodLeavesBlock;
import com.wildlands.registry.ModBlocks;
import com.wildlands.tree.TreeBuilder;
import com.wildlands.tree.TreeModel;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Ставит дерево v2 (бревна, ветви разной толщины и листва) по чертежу из {@link TreeBuilder}. */
public class WildTreeFeature extends Feature<WildTreeConfig> {
    /** 2 = отправить клиентам, 16 = без обновления форм соседей. */
    private static final int FLAGS = 18;

    public WildTreeFeature(Codec<WildTreeConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<WildTreeConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        WildTreeConfig config = context.config();
        RandomSource random = context.random();

        TreeBuilder.Species species;
        try {
            species = TreeBuilder.Species.byName(config.species());
        } catch (IllegalArgumentException e) {
            return false;
        }
        String sp = config.species();
        Block log = ModBlocks.LOG.get(sp).get();
        BranchBlock branch = (BranchBlock) ModBlocks.BRANCH.get(sp).get();
        Block leaves = ModBlocks.LEAVES.get(sp).get();

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

        TreeModel model = TreeBuilder.generate(species, height, random.nextLong());
        List<TreeModel.Voxel> voxels = model.voxels();

        for (TreeModel.Voxel v : voxels) {
            if (v.wood()) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, woodState(log, branch, v), FLAGS);
                }
            }
        }
        BlockState leafState = leaves.defaultBlockState().setValue(WoodLeavesBlock.PERSISTENT, false);
        for (TreeModel.Voxel v : voxels) {
            if (!v.wood()) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, leafState, FLAGS);
                }
            }
        }
        return true;
    }

    private static BlockState woodState(Block log, BranchBlock branch, TreeModel.Voxel v) {
        if (v.cls() >= TreeModel.FULL) {
            Direction.Axis axis = switch (v.axis()) {
                case TreeModel.AXIS_X -> Direction.Axis.X;
                case TreeModel.AXIS_Z -> Direction.Axis.Z;
                default -> Direction.Axis.Y;
            };
            return log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        }
        return branch.stateFor(v.cls(), v.conn());
    }

    private static boolean isFree(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }
}
