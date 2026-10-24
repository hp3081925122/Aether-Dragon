package com.aetherdragon.item;

import com.aetherdragon.entity.TamedAetherDragonEntity;
import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class AetherDragonFluteItem extends Item {
    private static final System.Logger LOGGER = System.getLogger(AetherDragonFluteItem.class.getName());
    private static final String DRAGON_UUID_TAG = "DragonUUID";
    private static final String DRAGON_HEALTH_TAG = "DragonHealth";
    private static final double RECALL_RANGE = 64.0D;

    public AetherDragonFluteItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        CompoundTag tag = readData(stack);
        TamedAetherDragonEntity dragon = null;
        if (tag.hasUUID(DRAGON_UUID_TAG)) {
            UUID id = tag.getUUID(DRAGON_UUID_TAG);
            for (ServerLevel candidate : serverLevel.getServer().getAllLevels()) {
                if (candidate.getEntity(id) instanceof TamedAetherDragonEntity found && found.isAlive()) {
                    dragon = found;
                    break;
                }
            }
        }
        if (dragon != null) {
            if (dragon.level() != level || dragon.distanceToSqr(player) > RECALL_RANGE * RECALL_RANGE) {
                player.displayClientMessage(Component.translatable("message.aether_dragon.flute.too_far").withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            tag.putFloat(DRAGON_HEALTH_TAG, dragon.getHealth());
            tag.remove(DRAGON_UUID_TAG);
            writeData(stack, tag);
            serverLevel.sendParticles(ParticleTypes.CLOUD, dragon.getX(), dragon.getY() + 2.0D, dragon.getZ(), 40, 2.5D, 1.5D, 2.5D, 0.05D);
            dragon.ejectPassengers();
            dragon.discard();
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.aether_dragon.flute.recalled").withStyle(ChatFormatting.AQUA), true);
            player.getCooldowns().addCooldown(this, 20);
            LOGGER.log(System.Logger.Level.DEBUG, "Aether Dragon flute recalled mount for {0}", player.getName().getString());
            return InteractionResultHolder.success(stack);
        }
        CompoundTag persisted = TamedAetherDragonEntity.getPersistedData(serverPlayer);
        long now = serverLevel.getServer().overworld().getGameTime();
        long respawnAt = persisted.getLong(TamedAetherDragonEntity.RESPAWN_TAG);
        if (respawnAt > now) {
            long seconds = (respawnAt - now + 19L) / 20L;
            player.displayClientMessage(Component.translatable("message.aether_dragon.flute.recovering", seconds).withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (respawnAt != 0L) {
            persisted.remove(TamedAetherDragonEntity.RESPAWN_TAG);
            tag.remove(DRAGON_HEALTH_TAG);
            writeData(stack, tag);
        }
        TamedAetherDragonEntity created = ModEntities.TAMED_DRAGON.get().create(serverLevel);
        if (created == null) {
            return InteractionResultHolder.fail(stack);
        }
        Vec3 base = player.position().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(4.0D));
        created.moveTo(base.x, base.y, base.z, player.getYRot(), 0.0F);
        int lift = 0;
        while (lift < 6 && !serverLevel.noCollision(created)) {
            lift++;
            created.moveTo(base.x, base.y + lift, base.z, player.getYRot(), 0.0F);
        }
        if (!serverLevel.noCollision(created)) {
            player.displayClientMessage(Component.translatable("message.aether_dragon.flute.no_space").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        long token = serverLevel.random.nextLong();
        created.bindOwner(serverPlayer, token);
        if (tag.contains(DRAGON_HEALTH_TAG)) {
            created.setHealth(Math.max(1.0F, Math.min(tag.getFloat(DRAGON_HEALTH_TAG), created.getMaxHealth())));
        }
        persisted.putLong(TamedAetherDragonEntity.TOKEN_TAG, token);
        serverLevel.addFreshEntity(created);
        tag.putUUID(DRAGON_UUID_TAG, created.getUUID());
        tag.remove(DRAGON_HEALTH_TAG);
        writeData(stack, tag);
        serverLevel.sendParticles(ParticleTypes.CLOUD, created.getX(), created.getY() + 2.0D, created.getZ(), 40, 2.5D, 1.5D, 2.5D, 0.05D);
        level.playSound(null, created.blockPosition(), ModSounds.ALL.get("drw").get(), SoundSource.NEUTRAL, 1.5F, 1.0F);
        player.displayClientMessage(Component.translatable("message.aether_dragon.flute.summoned").withStyle(ChatFormatting.AQUA), true);
        player.getCooldowns().addCooldown(this, 20);
        LOGGER.log(System.Logger.Level.DEBUG, "Aether Dragon flute summoned mount {0} for {1}", created.getUUID(), player.getName().getString());
        return InteractionResultHolder.success(stack);
    }

    private static CompoundTag readData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void writeData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = readData(stack);
        if (tag.hasUUID(DRAGON_UUID_TAG)) {
            tooltip.add(Component.translatable("tooltip.aether_dragon.flute.summoned").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.aether_dragon.flute.resting").withStyle(ChatFormatting.AQUA));
            if (tag.contains(DRAGON_HEALTH_TAG)) {
                tooltip.add(Component.translatable("tooltip.aether_dragon.flute.health", String.format("%.0f", tag.getFloat(DRAGON_HEALTH_TAG))).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.aether_dragon.flute.usage").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.aether_dragon.flute.controls").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
