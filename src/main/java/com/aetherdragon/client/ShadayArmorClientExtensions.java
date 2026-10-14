package com.aetherdragon.client;

import com.aetherdragon.client.model.ShadayArmorModel;
import com.aetherdragon.item.ShadayArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class ShadayArmorClientExtensions implements IClientItemExtensions {
    @Override
    public Model getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model original) {
        boolean baby = layerType == EquipmentClientInfo.LayerType.HUMANOID_BABY;
        var layer = baby ? ShadayArmorModel.BABY_LAYER_LOCATION : ShadayArmorModel.LAYER_LOCATION;
        return new ShadayArmorModel(Minecraft.getInstance().getEntityModels().bakeLayer(layer), ((ShadayArmorItem) stack.getItem()).armorType());
    }
}
