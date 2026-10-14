package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.RebornMobEntity;
import com.aetherdragon.item.ShadayArmorItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AetherDragon.MOD_ID);
    public static final Map<String, RegistryObject<Item>> ALL = new LinkedHashMap<>();

    static {
        for (Map.Entry<String, RegistryObject<net.minecraft.world.level.block.Block>> entry : ModBlocks.ALL.entrySet()) {
            register(entry.getKey(), () -> new BlockItem(entry.getValue().get(), new Item.Properties()));
        }
        register("shaday_helmet", () -> new ShadayArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET));
        register("shaday_chestplate", () -> new ShadayArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
        register("shaday_leggings", () -> new ShadayArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS));
        String[] spawnEggs = {"aetherdragon"};
        for (String id : spawnEggs) {
            RegistryObject<net.minecraft.world.entity.EntityType<RebornMobEntity>> entity = ModEntities.MOBS.get(id);
            if (entity != null) {
                int[] colors = spawnEggColors(id);
                register(id + "_spawn_egg", () -> new ForgeSpawnEggItem(entity::get, colors[0], colors[1], new Item.Properties()));
            }
        }
    }

    private static int[] spawnEggColors(String id) {
        return switch (id) {
            case "shimaenaga" -> new int[]{-1908244, -8350532};
            case "twinkler", "buterfly" -> new int[]{-1252895, -4423552};
            case "crystalitegolem" -> new int[]{-14755911, -1450953};
            case "percipitus" -> new int[]{-8482125, -2164745};
            case "crystaliteminion" -> new int[]{-15532088, -14887803};
            case "mutatedcritter" -> new int[]{-4234544, -11778986};
            case "aether_naga" -> new int[]{-11778986, -4234544};
            case "spirit" -> new int[]{-9858064, -1586885};
            case "saltcrab" -> new int[]{-527894, -2829364};
            case "saltcrabking" -> new int[]{-527894, -1256960};
            case "eldershulker" -> new int[]{-1727951, -12985922};
            case "guardianangel" -> new int[]{-394759, -6018};
            case "aetherdragon" -> new int[]{-1783512, -14637313};
            default -> new int[]{-1, -1};
        };
    }

    private static void register(String id, Supplier<? extends Item> supplier) {
        ALL.put(id, ITEMS.register(id, supplier));
    }

    private ModItems() {
    }
}


