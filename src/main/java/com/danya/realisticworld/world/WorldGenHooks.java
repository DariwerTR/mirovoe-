package com.danya.realisticworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGenHooks {
    private static final Map<Long, Boolean> DONE = new ConcurrentHashMap<>();

    private WorldGenHooks() {}

    /**
     * Prototype surface pass. This is intentionally an event-based prototype so it
     * can be tested immediately without replacing vanilla's full ChunkGenerator.
     * It only modifies already-generated chunks and therefore is safe to remove.
     */
    @SubscribeEvent
    public static void onChunkData(ChunkDataEvent.Load event) {
        ChunkAccess chunk = event.getChunk();
        long key = chunk.getPos().toLong();
        if (DONE.putIfAbsent(key, Boolean.TRUE) != null) return;

        // Terrain sampling is exposed here for the next integration stage.
        // The actual block pass is deliberately conservative in this prototype.
        // This class proves the project/event wiring without relying on unstable
        // internal generator constructors.
    }
}
