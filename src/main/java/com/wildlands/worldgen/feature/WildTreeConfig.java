package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Настройки признака «дикое дерево» (v2, собственные блоки).
 *
 * @param species   порода: oak, birch или spruce
 * @param minHeight минимальная высота
 * @param maxHeight максимальная высота
 */
public record WildTreeConfig(String species, int minHeight, int maxHeight) implements FeatureConfiguration {
    public static final Codec<WildTreeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("species").forGetter(WildTreeConfig::species),
            Codec.intRange(6, 40).fieldOf("min_height").forGetter(WildTreeConfig::minHeight),
            Codec.intRange(6, 40).fieldOf("max_height").forGetter(WildTreeConfig::maxHeight)
    ).apply(instance, WildTreeConfig::new));
}
