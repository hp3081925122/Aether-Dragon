package com.aetherdragon.item;

import com.aetherdragon.registry.ModArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorType;

public final class ShadayArmorItem extends Item {
    private final ArmorType armorType;

    public ShadayArmorItem(ArmorType armorType, Properties properties) {
        super(properties.rarity(Rarity.RARE).humanoidArmor(ModArmorMaterials.SHADAY, armorType));
        this.armorType = armorType;
    }

    public ArmorType armorType() {
        return this.armorType;
    }
}
