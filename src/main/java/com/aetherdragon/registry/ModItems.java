package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraft.core.registries.Registries;
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
            ALL.put(entry.getKey(), ITEMS.register(entry.getKey(), () -> new BlockItem(entry.getValue().get(), new Item.Properties())));
        }
        ALL.put("aetherdragon_spawn_egg", ITEMS.register("aetherdragon_spawn_egg", () -> new SpawnEggItem(new Item.Properties().spawnEgg(ModEntities.MOBS.get("aetherdragon").get()))));
    }

    private ModItems() {
    }
}
