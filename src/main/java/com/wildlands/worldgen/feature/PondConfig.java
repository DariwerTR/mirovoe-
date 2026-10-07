package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Небольшой пруд.
 *
 * @param minRadius минимальный радиус (блоков)
 * @param maxRadius максимальный радиус, не больше 6 (при 6 пруд достигает 11 блоков), чтобы пруд не выходил за область генерации
 */
public record PondConfig(int minRadius, int maxRadius) implements FeatureConfiguration {
    public static final Codec<PondConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(2, 6).fieldOf("min_radius").forGetter(PondConfig::minRadius),
            Codec.intRange(2, 6).fieldOf("max_radius").forGetter(PondConfig::maxRadius)
    ).apply(instance, PondConfig::new));
}
