package com.aetherdragon.item;

import com.aetherdragon.entity.RebornProjectileEntity;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

public final class PyreSpearItem extends Item {
    public PyreSpearItem() {
        super(new Item.Properties().durability(400).rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aether_dragon.pyrespear").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 60;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 1.3F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getAbilities().instabuild || !findAmmo(player).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        ItemStack ammo = findAmmo(player);
        if (!player.getAbilities().instabuild && ammo.isEmpty()) {
            player.stopUsingItem();
            return;
        }
        RebornProjectileEntity projectile = RebornProjectileEntity.shootShulker(level, player, 3.0F);
        if (projectile == null) {
            return;
        }
        if (player.getAbilities().instabuild) {
            projectile.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.CREATIVE_ONLY;
        }
        if (!player.getAbilities().instabuild) {
            ammo.shrink(1);
        }
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        if (level instanceof ServerLevel server) {
            server.playSound(null, player.blockPosition(), ModSounds.ALL.get("sword").get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(this, 60);
        }
        player.stopUsingItem();
    }

    private ItemStack findAmmo(Player player) {
        ItemStack held = ProjectileWeaponItem.getHeldProjectile(player, stack -> stack.is(Items.SHULKER_SHELL));
        if (!held.isEmpty()) {
            return held;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.SHULKER_SHELL)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}






