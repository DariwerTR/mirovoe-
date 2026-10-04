package com.wildlands.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ветвь дерева: ядро толщиной 2*thickness пикселей и рукава к соседней древесине.
 * Формы совпадают с моделями (см. tools/assets/gen_tree_assets.py).
 */
public class BranchBlock extends Block {
    public static final IntegerProperty THICKNESS = IntegerProperty.create("thickness", 1, 7);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    /** Порядок совпадает с TreeModel.DIRS: вверх, вниз, север, юг, восток, запад. */
    public static final BooleanProperty[] CONNECTIONS = {UP, DOWN, NORTH, SOUTH, EAST, WEST};

    private static final VoxelShape[] SHAPES = new VoxelShape[8 * 64];

    public BranchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(THICKNESS, 4)
                .setValue(UP, false).setValue(DOWN, false)
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(THICKNESS, UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    /** Состояние ветви по толщине и маске связей (бит i соответствует CONNECTIONS[i]). */
    public BlockState stateFor(int thickness, int connMask) {
        BlockState s = defaultBlockState().setValue(THICKNESS, Math.max(1, Math.min(7, thickness)));
        for (int i = 0; i < CONNECTIONS.length; i++) {
            s = s.setValue(CONNECTIONS[i], (connMask & (1 << i)) != 0);
        }
        return s;
    }

    /**
     * Форма столкновений. Без @Override намеренно: если у этой версии Minecraft другая сигнатура,
     * метод просто не сработает, и блок останется полным кубом (игра не сломается).
     */
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int t = state.getValue(THICKNESS);
        int mask = 0;
        for (int i = 0; i < CONNECTIONS.length; i++) {
            if (state.getValue(CONNECTIONS[i])) {
                mask |= 1 << i;
            }
        }
        int idx = t * 64 + mask;
        VoxelShape cached = SHAPES[idx];
        if (cached == null) {
            cached = buildShape(t, mask);
            SHAPES[idx] = cached;
        }
        return cached;
    }

    private static VoxelShape buildShape(int t, int mask) {
        double a = 8 - t, b = 8 + t;
        VoxelShape shape = Block.box(a, a, a, b, b, b);
        if ((mask & 1) != 0) {
            shape = Shapes.or(shape, Block.box(a, b, a, b, 16, b));
        }
        if ((mask & 2) != 0) {
            shape = Shapes.or(shape, Block.box(a, 0, a, b, a, b));
        }
        if ((mask & 4) != 0) {
            shape = Shapes.or(shape, Block.box(a, a, 0, b, b, a));
        }
        if ((mask & 8) != 0) {
            shape = Shapes.or(shape, Block.box(a, a, b, b, b, 16));
        }
        if ((mask & 16) != 0) {
            shape = Shapes.or(shape, Block.box(b, a, a, 16, b, b));
        }
        if ((mask & 32) != 0) {
            shape = Shapes.or(shape, Block.box(0, a, a, a, b, b));
        }
        return shape;
    }
}
