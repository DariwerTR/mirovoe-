package com.wildlands.registry;

import com.wildlands.Wildlands;
import com.wildlands.worldgen.feature.RealisticTreeConfig;
import com.wildlands.worldgen.feature.RealisticTreeFeature;
import com.wildlands.worldgen.feature.WildTreeConfig;
import com.wildlands.worldgen.feature.WildTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Регистрация собственных типов признаков генерации мира. */
public final class ModFeatures {
    /** Регистрируется на шине мода в конструкторе {@link Wildlands}. */
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, Wildlands.MOD_ID);

    public static final RegistryObject<RealisticTreeFeature> REALISTIC_TREE =
            FEATURES.register("realistic_tree", () -> new RealisticTreeFeature(RealisticTreeConfig.CODEC));

    public static final RegistryObject<WildTreeFeature> WILD_TREE =
            FEATURES.register("wild_tree", () -> new WildTreeFeature(WildTreeConfig.CODEC));

    private ModFeatures() {
    }
}
