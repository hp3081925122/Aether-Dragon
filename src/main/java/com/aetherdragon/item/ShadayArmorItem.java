package com.aetherdragon.item;

import com.aetherdragon.client.model.ShadayArmorModel;
import com.aetherdragon.registry.ModArmorMaterials;
import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;


public final class ShadayArmorItem extends ArmorItem {
    public ShadayArmorItem(Type type) {
        super(ModArmorMaterials.SHADAY, type, new Item.Properties().rarity(Rarity.RARE).durability(type.getDurability(50)));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> defaultModel) {
                ShadayArmorModel<LivingEntity> source = new ShadayArmorModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ShadayArmorModel.LAYER_LOCATION));
                ModelPart empty = new ModelPart(Collections.emptyList(), Collections.emptyMap());
                Map<String, ModelPart> parts;
                if (getType() == Type.HELMET) {
                    parts = Map.of(
                            "head", source.head,
                            "hat", empty,
                            "body", empty,
                            "right_arm", empty,
                            "left_arm", empty,
                            "right_leg", empty,
                            "left_leg", empty
                    );
                } else if (getType() == Type.CHESTPLATE) {
                    parts = Map.of(
                            "head", empty,
                            "hat", empty,
                            "body", source.jacket,
                            "right_arm", source.rightArm,
                            "left_arm", source.leftArm,
                            "right_leg", empty,
                            "left_leg", empty
                    );
                } else if (getType() == Type.LEGGINGS) {
                    parts = Map.of(
                            "head", empty,
                            "hat", empty,
                            "body", empty,
                            "right_arm", empty,
                            "left_arm", empty,
                            "right_leg", source.rightLeg,
                            "left_leg", source.leftLeg
                    );
                } else {
                    parts = Map.of(
                            "head", empty,
                            "hat", empty,
                            "body", empty,
                            "right_arm", empty,
                            "left_arm", empty,
                            "right_leg", source.rightBoots,
                            "left_leg", source.leftBoots
                    );
                }
                HumanoidModel<?> armorModel = new HumanoidModel<>(new ModelPart(Collections.emptyList(), parts));
                armorModel.crouching = living.isShiftKeyDown();
                armorModel.riding = defaultModel.riding;
                armorModel.young = living.isBaby();
                return armorModel;
            }
        });
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, EquipmentSlot slot, net.minecraft.world.item.ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.fromNamespaceAndPath("aether_dragon", "textures/entities/valkyrie_armor.png");
    }
}
