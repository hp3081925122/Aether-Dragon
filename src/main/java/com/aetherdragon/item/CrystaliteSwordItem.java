package com.aetherdragon.item;

import com.aetherdragon.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

public final class CrystaliteSwordItem extends SwordItem {
    public CrystaliteSwordItem() {
        super(new AetherTier(500, 2.0F, 6.0F, 3, 20), new Item.Properties().attributes(SwordItem.createAttributes(new AetherTier(500, 2.0F, 6.0F, 3, 20), 3.0F, -2.4F)));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.aether_dragon.crystalitesword").withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);
        if (player.getMainHandItem().is(this)) {
            player.getCooldowns().addCooldown(this, 280);
        }
        if (player.getMainHandItem().is(this) && level instanceof ServerLevel server) {
            Vec3 center = player.position();
            AABB range = new AABB(center, center).inflate(5.0D);
            for (Entity target : level.getEntitiesOfClass(Entity.class, range, entity -> true)) {
                LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(server);
                if (lightning != null) {
                    BlockPos pos = target.blockPosition();
                    lightning.moveTo(pos.getX(), pos.getY(), pos.getZ());
                    server.addFreshEntity(lightning);
                }
                server.sendParticles(ModParticles.RUNESEFFECT.get(), center.x, center.y, center.z, 7, 3.0D, 3.0D, 3.0D, 1.0D);
            }
        }
        return result;
    }
}


