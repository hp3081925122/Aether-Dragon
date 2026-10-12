package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, AetherDragon.MOD_ID);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SHADAY = ARMOR_MATERIALS.register("shaday", () -> {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 4);
        defense.put(ArmorItem.Type.CHESTPLATE, 7);
        defense.put(ArmorItem.Type.LEGGINGS, 9);
        defense.put(ArmorItem.Type.BOOTS, 4);
        return new ArmorMaterial(defense, 30, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "shaday"))), 4.0F, 0.1F);
    });

    private ModArmorMaterials() {
    }
}
