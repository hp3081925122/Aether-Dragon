package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.item.ShadayArmorItem;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AetherDragon.MOD_ID);
    public static final Map<String, DeferredHolder<Item, Item>> ALL = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, DeferredHolder<net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Block>> entry : ModBlocks.ALL.entrySet()) {
            ALL.put(entry.getKey(), ITEMS.register(entry.getKey(), () -> new BlockItem(entry.getValue().get(), new Item.Properties().setId(itemKey(entry.getKey())))));
        }
        ALL.put("shaday_helmet", ITEMS.register("shaday_helmet", () -> new ShadayArmorItem(ArmorType.HELMET, new Item.Properties().setId(itemKey("shaday_helmet")))));
        ALL.put("shaday_chestplate", ITEMS.register("shaday_chestplate", () -> new ShadayArmorItem(ArmorType.CHESTPLATE, new Item.Properties().setId(itemKey("shaday_chestplate")))));
        ALL.put("shaday_leggings", ITEMS.register("shaday_leggings", () -> new ShadayArmorItem(ArmorType.LEGGINGS, new Item.Properties().setId(itemKey("shaday_leggings")))));
        ALL.put("aetherdragon_spawn_egg", ITEMS.register("aetherdragon_spawn_egg", () -> new SpawnEggItem(new Item.Properties().setId(itemKey("aetherdragon_spawn_egg")).spawnEgg(ModEntities.MOBS.get("aetherdragon").get()))));
    }

    private static ResourceKey<Item> itemKey(String id) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, id));
    }

    private ModItems() {
    }
}
