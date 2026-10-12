package com.aetherdragon.item;

import com.aetherdragon.registry.ModParticles;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

public final class SpiritSwordItem extends ShieldItem {
    public SpiritSwordItem() {
        super(new net.minecraft.world.item.Item.Properties().durability(800));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aether_dragon.spiritsword").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot slot, LivingEntity entity) {
        return slot != EquipmentSlot.OFFHAND && super.canEquip(stack, slot, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (!player.getMainHandItem().is(this)) {
            return result;
        }
        player.getCooldowns().addCooldown(this, 40);
        if (level instanceof ServerLevel server) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 50, 2, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, 50, 2, false, false));
            server.sendParticles(ModParticles.WHITESMOKE.get(), player.getX(), player.getY(), player.getZ(), 5, 3.0D, 3.0D, 3.0D, 1.0D);
        }
        return result;
    }
}

