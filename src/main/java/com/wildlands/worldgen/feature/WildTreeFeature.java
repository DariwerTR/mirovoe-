package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.wildlands.block.BranchBlock;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/** Ставит дерево v2 (бревна, ветви разной толщины и листва) по чертежу из {@link TreeBuilder}. */
public class WildTreeFeature extends Feature<WildTreeConfig> {
    /** 2 = отправить клиентам, 16 = без обновления форм соседей. */
    private static final int FLAGS = 18;
    /** Сколько блоков земли можно досыпать под основание ствола. */
    private static final int MAX_FILL = 1;

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
        // листва ванильная: прозрачная, с цветом биома и штатным опаданием
        Block leaves = switch (sp) {
            case "birch" -> Blocks.BIRCH_LEAVES;
            case "spruce" -> Blocks.SPRUCE_LEAVES;
            default -> Blocks.OAK_LEAVES;
        };

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

        // стволы не ставим вплотную друг к другу: естественный разброс деревьев
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    pos.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                        return false;
                    }
                }
            }
        }

        // Прибрежный лес: ставим только если рядом (до 6 блоков) есть вода на уровне земли
        if (config.nearWater() && !waterNearby(level, origin)) {
            return false;
        }

        // Поляны. В настоящем лесу есть окна света на месте упавших деревьев, там густо растёт подрост
        // (смена поколений, Forest ecology). Крупные деревья пропускаем там, где низкочастотный шум мал,
        // а в самых светлых местах оставляем в основном подрост.
        double gap = gapNoise(level.getSeed(), origin.getX(), origin.getZ());
        boolean young = !config.dead() && Math.max(config.minHeight(), config.maxHeight()) <= 10;
        if (young) {
            if (gap > 0.6 && random.nextFloat() < (gap - 0.6f) * 2.0f) {
                return false;
            }
        } else if (!config.dead() && !config.nearWater() && gap < 0.30 && random.nextFloat() < (0.30f - (float) gap) / 0.30f * 0.95f) {
            return false;
        }

        int lo = Math.min(config.minHeight(), config.maxHeight());
        int hi = Math.max(config.minHeight(), config.maxHeight());
        // больше молодых и средних деревьев, меньше великанов
        int height = Math.min(hi, lo + (int) ((hi - lo + 1) * Math.pow(random.nextFloat(), 1.5)));

        // Высотная поясность. Граница леса там, где средняя температура вегетации около 6 °C; у границы
        // деревья редеют и мельчают, выше их нет (Tree line). Масштаб около 70 м на блок: граница дуба на
        // 34 блока выше моря (около 2.4 км), берёзы на 40, ели на 46. Начинают редеть за 14 блоков до границы.
        {
            int alt = origin.getY() - 63;
            int line = switch (sp) {
                case "spruce" -> 46;
                case "birch" -> 40;
                default -> 34;
            };
            if (alt > line - 14) {
                float t = (alt - (line - 14)) / 14.0f;
                if (t >= 1.0f || random.nextFloat() < t * 0.85f) {
                    return false;
                }
                height = Math.max(Math.min(6, hi), Math.round(height * (1.0f - 0.55f * t)));
            }
        }

        TreeModel model = TreeBuilder.generate(species, height, random.nextLong());
        List<TreeModel.Voxel> voxels = model.voxels();
        if (config.dead()) {
            // сухостой: ни листвы, ни вершины (обломана на 75-90% высоты)
            int cut = (int) (height * (0.75 + 0.15 * random.nextFloat()));
            voxels = voxels.stream().filter(v -> v.wood() && v.y() <= cut).toList();
        }

        // земля под деревом: под каждым блоком основания должна быть твёрдая земля.
        // Пустоты под стволом (до MAX_FILL блоков) засыпаем землёй; если глубже, дерево не ставим.
        BlockState soil = level.getBlockState(origin.below());
        if (!soil.is(BlockTags.DIRT) || soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.PODZOL) || soil.is(Blocks.MYCELIUM)) {
            soil = Blocks.DIRT.defaultBlockState();
        }
        // Земля под деревом. Колонны ствола (дерево продолжается выше y=3) обязаны стоять на земле:
        // пустоту до MAX_FILL блоков засыпаем землёй, глубже дерево не ставим. Контрфорсы у основания
        // (дальше вверх не идут) без опоры просто не ставим: никаких корней вниз и висящих блоков.
        java.util.Set<Long> fill = new java.util.TreeSet<>();
        java.util.Set<Long> dropped = new java.util.HashSet<>();
        java.util.Set<Long> trunkCols = new java.util.HashSet<>();
        for (TreeModel.Voxel v : voxels) {
            if (v.wood() && v.y() == 3) {
                trunkCols.add(BlockPos.asLong(v.x(), 0, v.z()));
            }
        }
        java.util.Set<Long> woodKeys = new java.util.HashSet<>();
        for (TreeModel.Voxel v : voxels) {
            if (v.wood()) {
                woodKeys.add(BlockPos.asLong(v.x(), v.y(), v.z()));
            }
        }
        BlockPos.MutableBlockPos g = new BlockPos.MutableBlockPos();
        for (TreeModel.Voxel v : voxels) {
            // опоры ищем только у нижних блоков колонны: если под блоком древесина дерева, он стоит на ней
            if (!v.wood() || v.y() > 2 || woodKeys.contains(BlockPos.asLong(v.x(), v.y() - 1, v.z()))) {
                continue;
            }
            int x = origin.getX() + v.x(), z = origin.getZ() + v.z();
            int depth = 0;
            boolean found = false;
            for (int yy = origin.getY() + v.y() - 1; depth <= MAX_FILL; yy--) {
                g.set(x, yy, z);
                if (isSolidGround(level, g)) {
                    found = true;
                    break;
                }
                depth++;
            }
            if (!found) {
                if (trunkCols.contains(BlockPos.asLong(v.x(), 0, v.z()))) {
                    return false;
                }
                dropped.add(BlockPos.asLong(v.x(), v.y(), v.z()));
                continue;
            }
            if (depth > 0 && !trunkCols.contains(BlockPos.asLong(v.x(), 0, v.z()))) {
                // контрфорс на краю уступа: не заполняем, убираем
                dropped.add(BlockPos.asLong(v.x(), v.y(), v.z()));
                continue;
            }
            for (int d = 1; d <= depth; d++) {
                fill.add(BlockPos.asLong(x, origin.getY() + v.y() - d, z));
            }
        }
        for (long l : fill) {
            level.setBlock(BlockPos.of(l), soil, FLAGS);
        }
        for (TreeModel.Voxel v : voxels) {
            if (v.wood() && !dropped.contains(BlockPos.asLong(v.x(), v.y(), v.z()))) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, woodState(log, branch, v), FLAGS);
                }
            }
        }
        BlockState leafBase = leaves.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, false);
        for (TreeModel.Voxel v : voxels) {
            if (!v.wood()) {
                pos.set(origin.getX() + v.x(), origin.getY() + v.y(), origin.getZ() + v.z());
                if (isFree(level, pos)) {
                    level.setBlock(pos, leafBase.setValue(BlockStateProperties.DISTANCE,
                            Math.max(1, Math.min(7, v.distance()))), FLAGS);
                }
            }
        }
        return true;
    }

    private static boolean waterNearby(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dz = -6; dz <= 6; dz += 2) {
                for (int dy = -3; dy <= 0; dy++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(p).is(Blocks.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Значение шума 0..1 с масштабом около 30 блоков (две октавы), детерминированное по зерну мира. */
    private static double gapNoise(long seed, int x, int z) {
        return 0.65 * valueNoise(seed, x / 30.0, z / 30.0) + 0.35 * valueNoise(seed ^ 0x9E3779B97F4A7C15L, x / 13.0, z / 13.0);
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(seed, x0, z0), b = lattice(seed, x0 + 1, z0);
        double c = lattice(seed, x0, z0 + 1), d = lattice(seed, x0 + 1, z0 + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double lattice(long seed, int x, int z) {
        long h = seed * 6364136223846793005L + x * 1442695040888963407L + z * 2862933555777941757L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h & 0xFFFFFFL) / (double) 0x1000000L;
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

    private static boolean isSolidGround(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, st -> !st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced() && !st.is(BlockTags.LOGS) && !st.is(BlockTags.LEAVES)
                && !st.is(BlockTags.REPLACEABLE_BY_TREES));
    }

    private static boolean isFree(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }
}
