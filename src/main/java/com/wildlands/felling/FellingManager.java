package com.wildlands.felling;

import com.mojang.logging.LogUtils;
import com.wildlands.block.BranchBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Рубка деревьев. Игрок ломает вертикальный блок ствола топором: событие запоминается, на следующем тике
 * (когда блок уже удалён) ищется падающая часть дерева и запускается {@link FellingJob}.
 *
 * Падает любое дерево со стволом и природной листвой или ветвями: и деревья мода, и ванильные.
 * Присев (Shift), игрок ломает один блок как обычно. Ствол толще одного блока надо перерубить насквозь.
 */
public final class FellingManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int[][] DIRS = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    private static final class Pending {
        final ServerLevel level;
        final BlockPos pos;
        final UUID player;
        final double px, pz;
        int age;

        Pending(ServerLevel level, BlockPos pos, UUID player, double px, double pz) {
            this.level = level;
            this.pos = pos;
            this.player = player;
            this.px = px;
            this.pz = pz;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<FellingJob> JOBS = new ArrayList<>();

    private FellingManager() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, FellingConfig.SPEC);
        BlockEvent.BreakEvent.BUS.addListener(FellingManager::onBreak);
        TickEvent.LevelTickEvent.Post.BUS.addListener(FellingManager::onLevelTick);
        ServerStoppingEvent.BUS.addListener(FellingManager::onStopping);
    }

    private static void onStopping(ServerStoppingEvent event) {
        PENDING.clear();
        JOBS.clear();
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!FellingConfig.enabled()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (FellingConfig.sneakDisables() && player.isShiftKeyDown()) {
            return;
        }
        if (FellingConfig.requireAxe() && !player.getMainHandItem().is(ItemTags.AXES)) {
            return;
        }
        if (!McWorldView.isVertical(event.getState())) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        for (Pending p : PENDING) {
            if (p.level == level && p.pos.equals(pos)) {
                return;
            }
        }
        PENDING.add(new Pending(level, pos, player.getUUID(), player.getX(), player.getZ()));
    }

    private static void onLevelTick(TickEvent.LevelTickEvent.Post event) {
        if (!(event.level() instanceof ServerLevel level)) {
            return;
        }
        if (!PENDING.isEmpty()) {
            processPending(level);
        }
        if (!JOBS.isEmpty()) {
            Iterator<FellingJob> it = JOBS.iterator();
            while (it.hasNext()) {
                FellingJob job = it.next();
                if (job.level() == level && job.tick()) {
                    it.remove();
                }
            }
        }
    }

    private static void processPending(ServerLevel level) {
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level != level) {
                continue;
            }
            p.age++;
            if (p.age < 1) {
                continue;
            }
            it.remove();
            try {
                start(level, p);
            } catch (RuntimeException e) {
                LOGGER.error("Wildlands: не удалось начать падение дерева", e);
            }
        }
    }

    private static void start(ServerLevel level, Pending p) {
        if (McWorldView.isWood(level.getBlockState(p.pos))) {
            return; // блок не сломан (отменили другим модом)
        }
        McWorldView view = new McWorldView(level);
        Piece piece = TreeScan.scan(view, p.pos.getX(), p.pos.getY(), p.pos.getZ(), FellingConfig.maxWood(), FellingConfig.maxBlocks());
        if (piece == null) {
            return;
        }
        double ax = p.pos.getX() + 0.5 - p.px;
        double az = p.pos.getZ() + 0.5 - p.pz;
        FallPlanner.Choice choice = FallPlanner.choose(piece, view, ax, az, 0.8);
        FallSim sim = new FallSim(choice.fall(), view, FellingConfig.speed());
        if (choice.restAngle() < Math.toRadians(3.0)) {
            return; // деревья вокруг и земля не дают сдвинуться ни в одну сторону
        }
        fixNeighbours(level, p.pos, piece);
        JOBS.add(new FellingJob(level, choice.fall(), sim, view));
        chargeTool(level, p.player, piece);
    }

    /** У оставшихся рядом с разрезом ветвей убираем связь с пропавшим блоком, чтобы не торчали «рукава» в пустоту. */
    private static void fixNeighbours(ServerLevel level, BlockPos cut, Piece piece) {
        Set<Long> falling = new HashSet<>();
        for (long k : piece.wood) {
            falling.add(k);
        }
        for (int d = 0; d < 6; d++) {
            BlockPos n = cut.offset(DIRS[d][0], DIRS[d][1], DIRS[d][2]);
            if (falling.contains(n.asLong())) {
                continue;
            }
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof BranchBlock && s.getValue(BranchBlock.CONNECTIONS[d ^ 1])) {
                level.setBlock(n, s.setValue(BranchBlock.CONNECTIONS[d ^ 1], false), 18);
            }
        }
    }

    /** Топор тупится в зависимости от размера дерева (кроме творческого режима: там hurtAndBreak ничего не делает). */
    private static void chargeTool(ServerLevel level, UUID playerId, Piece piece) {
        int cap = FellingConfig.toolDamageCap();
        if (cap <= 0) {
            return;
        }
        Player pl = level.getPlayerByUUID(playerId);
        if (!(pl instanceof ServerPlayer sp)) {
            return;
        }
        ItemStack tool = sp.getMainHandItem();
        if (!tool.is(ItemTags.AXES)) {
            return;
        }
        int cost = Math.min(cap, piece.wood.length / 60);
        if (cost > 0) {
            tool.hurtAndBreak(cost, level, sp, item -> { });
        }
    }
}
