package com.wildlands.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Настройки признака «реалистичное дерево». Читаются из JSON (data/wildlands/worldgen/configured_feature).
 *
 * @param species    вид дерева: oak, birch или spruce
 * @param log        блок бревна (ось подставляется автоматически по направлению ветви)
 * @param leaves     блок листвы (расстояние до бревна считается автоматически)
 * @param minHeight  минимальная высота дерева в блоках
 * @param maxHeight  максимальная высота дерева в блоках
 */
public record RealisticTreeConfig(String species, BlockState log, BlockState leaves, int minHeight, int maxHeight)
        implements FeatureConfiguration {

    public static final Codec<RealisticTreeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("species").forGetter(RealisticTreeConfig::species),
            BlockState.CODEC.fieldOf("log").forGetter(RealisticTreeConfig::log),
            BlockState.CODEC.fieldOf("leaves").forGetter(RealisticTreeConfig::leaves),
            Codec.intRange(4, 48).fieldOf("min_height").forGetter(RealisticTreeConfig::minHeight),
            Codec.intRange(4, 48).fieldOf("max_height").forGetter(RealisticTreeConfig::maxHeight)
    ).apply(instance, RealisticTreeConfig::new));
}
