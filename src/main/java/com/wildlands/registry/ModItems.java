package com.wildlands.registry;

import com.wildlands.Wildlands;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Предметы-блоки деревьев. Выдать в игре: /give @s wildlands:oak_log */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Wildlands.MOD_ID);

    static {
        for (String sp : ModBlocks.SPECIES) {
            blockItem(sp + "_log", ModBlocks.LOG.get(sp));
            blockItem(sp + "_branch", ModBlocks.BRANCH.get(sp));
            blockItem(sp + "_leaves", ModBlocks.LEAVES.get(sp));
        }
    }

    private static RegistryObject<Item> blockItem(String name, Supplier<Block> block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Wildlands.MOD_ID, name));
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(key).useBlockDescriptionPrefix()));
    }

    private ModItems() {
    }
}
