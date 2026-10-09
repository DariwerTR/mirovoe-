package com.wildlands;

import com.wildlands.felling.FellingManager;
import com.wildlands.registry.ModBlocks;
import com.wildlands.registry.ModFeatures;
import com.wildlands.registry.ModItems;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Точка входа мода. Здесь только подключение регистраций;
 * вся логика живёт в пакетах registry, worldgen, tree (см. ROADMAP.md).
 */
@Mod(Wildlands.MOD_ID)
public class Wildlands {
    public static final String MOD_ID = "wildlands";

    public Wildlands(FMLJavaModLoadingContext context) {
        ModBlocks.BLOCKS.register(context.getModBusGroup());
        ModItems.ITEMS.register(context.getModBusGroup());
        ModFeatures.FEATURES.register(context.getModBusGroup());
        FellingManager.register(context);
    }
}
