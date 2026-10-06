package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Лесной «мусор»: упавшие брёвна, пни, кусты.
 *
 * @param kind    fallen_log, stump или bush
 * @param species oak, birch или spruce (порода бревна и листвы)
 */
public record DebrisConfig(String kind, String species) implements FeatureConfiguration {
    public static final Codec<DebrisConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(DebrisConfig::kind),
            Codec.STRING.fieldOf("species").forGetter(DebrisConfig::species)
    ).apply(instance, DebrisConfig::new));
}
