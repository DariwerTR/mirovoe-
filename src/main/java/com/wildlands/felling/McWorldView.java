package com.wildlands.felling;

import com.wildlands.block.BranchBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/** Реализация {@link WorldView} и {@link Collider} для мира Minecraft. Только чтение. */
public final class McWorldView implements WorldView, Collider {
    private final ServerLevel level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    /** Коробки игроков рядом с падением: дерево их не давит. */
    private List<AABB> players = List.of();

    public McWorldView(ServerLevel level) {
        this.level = level;
    }

    public void setPlayers(List<AABB> boxes) {
        this.players = boxes;
    }

    /** Древесина: любые бревна (тег logs, в нём же ветви мода). */
    public static boolean isWood(BlockState s) {
        return s.is(BlockTags.LOGS);
    }

    /** Вертикальное бревно или ветвь со связями вверх и вниз: так выглядит ствол, стоящий на земле. */
    public static boolean isVertical(BlockState s) {
        if (!isWood(s)) {
            return false;
        }
        if (s.getBlock() instanceof BranchBlock) {
            return s.getValue(BranchBlock.UP) && s.getValue(BranchBlock.DOWN);
        }
        if (s.hasProperty(RotatedPillarBlock.AXIS)) {
            return s.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        }
        return true;
    }

    /** Флаги древесины для {@link WorldView#info}. */
    public static int woodFlags(BlockState s) {
        int f = WorldView.K_WOOD;
        if (s.getBlock() instanceof BranchBlock) {
            f |= WorldView.F_NATURAL;
            if (s.getValue(BranchBlock.THICKNESS) >= 5) {
                f |= WorldView.F_THICK;
            }
            int mask = 0;
            for (int i = 0; i < BranchBlock.CONNECTIONS.length; i++) {
                if (s.getValue(BranchBlock.CONNECTIONS[i])) {
                    mask |= 1 << i;
                }
            }
            f |= mask << WorldView.CONN_SHIFT;
            if (s.getValue(BranchBlock.UP) && s.getValue(BranchBlock.DOWN)) {
                f |= WorldView.F_VERT;
            }
            return f;
        }
        f |= WorldView.F_THICK | WorldView.F_LOG;
        if (s.hasProperty(RotatedPillarBlock.AXIS)) {
            Direction.Axis a = s.getValue(RotatedPillarBlock.AXIS);
            if (a == Direction.Axis.Y) {
                f |= WorldView.F_VERT;
            } else if (a == Direction.Axis.X) {
                f |= WorldView.F_AXIS_X;
            } else {
                f |= WorldView.F_AXIS_Z;
            }
        } else {
            f |= WorldView.F_VERT;
        }
        return f;
    }

    /** Лист, выросший сам (не поставленный игроком). */
    public static boolean isNaturalLeaf(BlockState s) {
        return s.is(BlockTags.LEAVES) && s.hasProperty(BlockStateProperties.PERSISTENT)
                && !s.getValue(BlockStateProperties.PERSISTENT);
    }

    /** Свободно для дерева: воздух, жидкость, трава и прочее заменяемое. */
    public static boolean isOpen(BlockState s) {
        return s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced();
    }

    @Override
    public int info(int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.isLoaded(pos)) {
            return K_SOLID;
        }
        BlockState s = level.getBlockState(pos);
        if (isWood(s)) {
            return woodFlags(s);
        }
        if (s.is(BlockTags.LEAVES)) {
            return K_LEAF | (isNaturalLeaf(s) ? F_NATURAL : 0);
        }
        if (isOpen(s)) {
            return K_FREE;
        }
        return K_SOLID;
    }

    @Override
    public int classify(int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.isLoaded(pos)) {
            return SOLID;
        }
        if (!players.isEmpty()) {
            for (AABB b : players) {
                if (b.maxX > x && b.minX < x + 1 && b.maxY > y && b.minY < y + 1 && b.maxZ > z && b.minZ < z + 1) {
                    return PLAYER;
                }
            }
        }
        BlockState s = level.getBlockState(pos);
        if (isWood(s)) {
            return (woodFlags(s) & F_THICK) != 0 ? FOREIGN_THICK : FOREIGN_THIN;
        }
        if (s.is(BlockTags.LEAVES)) {
            return FOREIGN_LEAF;
        }
        return isOpen(s) ? FREE : SOLID;
    }
}
