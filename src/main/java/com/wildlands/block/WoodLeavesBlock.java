package com.wildlands.block;

import com.wildlands.util.RenderLayerHook;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Листва дерева. Опадает, если по цепочке листьев длиной до 6 блоков нет ни одного бревна или ветви.
 * Блоки, поставленные игроком, не опадают (persistent = true по умолчанию); генерация мира ставит false.
 * Проверка идёт при случайном тике, поэтому соседние обновления переопределять не нужно.
 */
public class WoodLeavesBlock extends Block {
    public static final BooleanProperty PERSISTENT = BlockStateProperties.PERSISTENT;
    private static final int MAX_STEPS = 6;
    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    public WoodLeavesBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PERSISTENT, true));
        RenderLayerHook.cutout(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PERSISTENT);
    }

    /** Без @Override (см. BranchBlock): при другой сигнатуре листва просто не будет опадать. */
    public boolean isRandomlyTicking(BlockState state) {
        return !state.getValue(PERSISTENT);
    }

    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PERSISTENT)) {
            return;
        }
        if (!woodNearby(level, pos)) {
            Block.dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }

    private static boolean woodNearby(Level level, BlockPos start) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>();
        frontier.add(start);
        seen.add(start.asLong());
        for (int step = 0; step < MAX_STEPS; step++) {
            while (!frontier.isEmpty()) {
                BlockPos p = frontier.poll();
                for (int[] d : DIRS) {
                    BlockPos q = p.offset(d[0], d[1], d[2]);
                    if (!seen.add(q.asLong())) {
                        continue;
                    }
                    BlockState s = level.getBlockState(q);
                    if (s.is(BlockTags.LOGS)) {
                        return true;
                    }
                    if (s.getBlock() instanceof WoodLeavesBlock) {
                        next.add(q);
                    }
                }
            }
            ArrayDeque<BlockPos> tmp = frontier;
            frontier = next;
            next = tmp;
        }
        return false;
    }
}
