package com.wildlands.registry;

import com.wildlands.Wildlands;
import com.wildlands.block.BranchBlock;
import com.wildlands.block.WoodLeavesBlock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Блоки деревьев: для каждой породы бревно, ветвь и листва. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Wildlands.MOD_ID);

    public static final String[] SPECIES = {"oak", "birch", "spruce"};

    public static final Map<String, RegistryObject<Block>> LOG = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> BRANCH = new LinkedHashMap<>();
    public static final Map<String, RegistryObject<Block>> LEAVES = new LinkedHashMap<>();

    static {
        for (String sp : SPECIES) {
            MapColor color = sp.equals("birch") ? MapColor.SAND : sp.equals("spruce") ? MapColor.PODZOL : MapColor.WOOD;
            LOG.put(sp, register(sp + "_log", p -> new RotatedPillarBlock(p),
                    () -> BlockBehaviour.Properties.of().mapColor(color).strength(2.0F).sound(SoundType.WOOD)));
            BRANCH.put(sp, register(sp + "_branch", p -> new BranchBlock(p),
                    () -> BlockBehaviour.Properties.of().mapColor(color).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
            LEAVES.put(sp, register(sp + "_leaves", p -> new WoodLeavesBlock(p),
                    () -> BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).sound(SoundType.GRASS).noOcclusion()
                            .isSuffocating((s, l, ps) -> false).isViewBlocking((s, l, ps) -> false)));
        }
    }

    private static RegistryObject<Block> register(String name, Function<BlockBehaviour.Properties, Block> factory,
                                                  java.util.function.Supplier<BlockBehaviour.Properties> props) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Wildlands.MOD_ID, name));
        return BLOCKS.register(name, () -> factory.apply(props.get().setId(key)));
    }

    private ModBlocks() {
    }
}
