package com.wildlands;

import com.wildlands.registry.ModFeatures;
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
        ModFeatures.FEATURES.register(context.getModBusGroup());
    }
}
