package com.aetherdragon;

import com.aetherdragon.entity.RebornMobEntity;
import com.aetherdragon.block.SpecialSpawnerBlock;
import com.aetherdragon.registry.ModItems;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RebornGameplayEvents {
    private static final Map<String, Consumer<RebornMobEntity>> JOIN_HANDLERS = Map.of();
    private static final Map<String, Consumer<RebornMobEntity>> DEATH_HANDLERS = Map.of(
            "aetherdragon", RebornGameplayEvents::handleDragonDeath
    );
    private static final Map<String, Consumer<RebornMobEntity>> HURT_HANDLERS = Map.of();

    private RebornGameplayEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof RebornMobEntity entity)) {
            return;
        }
        Consumer<RebornMobEntity> handler = JOIN_HANDLERS.get(entity.getIdPath());
        if (handler != null) {
            handler.accept(entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || event.player.tickCount % 20 != 0
                || !(event.player.level() instanceof ServerLevel server)) {
            return;
        }
        BlockPos center = event.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -8, -8), center.offset(8, 8, 8))) {
            if (server.getBlockState(pos).getBlock() instanceof SpecialSpawnerBlock spawner) {
                spawner.checkPlayerProximity(server, pos, event.player);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof RebornMobEntity entity) || !(entity.level() instanceof ServerLevel server)) {
            return;
        }
        entity.triggerAction("death");
        Consumer<RebornMobEntity> handler = DEATH_HANDLERS.get(entity.getIdPath());
        if (handler != null) {
            handler.accept(entity);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof RebornMobEntity entity) || entity.level().isClientSide) {
            return;
        }
        Consumer<RebornMobEntity> handler = HURT_HANDLERS.get(entity.getIdPath());
        if (handler != null) {
            handler.accept(entity);
        }
    }

    private static void handleGolemJoin(RebornMobEntity entity) {
        if (!entity.getPersistentData().getBoolean("reborn_golem_initialized")) {
            entity.getPersistentData().putBoolean("reborn_golem_initialized", true);
            entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 10, false, false));
            entity.invulnerableTime = 100;
            entity.triggerAction("activate");
        }
    }

    private static void handleMinionJoin(RebornMobEntity entity) {
        if (!entity.getPersistentData().getBoolean("reborn_minion_initialized") && entity.level() instanceof ServerLevel server) {
            entity.getPersistentData().putBoolean("reborn_minion_initialized", true);
            entity.triggerAction("spawn");
            sendParticles(server, ModParticles.RUNESEFFECT.get(), entity, 6);
        }
    }

    private static void handleDragonDeath(RebornMobEntity entity) {
        if (!entity.isDragonPhaseTwo() || !(entity.level() instanceof ServerLevel server)) {
            return;
        }
        drop(server, entity, ModItems.ALL.get("shaday_helmet").get());
        drop(server, entity, ModItems.ALL.get("shaday_chestplate").get());
        drop(server, entity, ModItems.ALL.get("shaday_leggings").get());
        explode(server, entity, Level.ExplosionInteraction.NONE);
        playVictory(server, entity);
        sendParticles(server, ParticleTypes.EXPLOSION, entity, 7);
        sendParticles(server, ModParticles.FIRE.get(), entity, 17);
    }

    private static void drop(ServerLevel server, RebornMobEntity entity, net.minecraft.world.level.ItemLike itemLike) {
        ItemEntity item = new ItemEntity(server, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(itemLike));
        item.setPickUpDelay(10);
        server.addFreshEntity(item);
    }

    private static void playVictory(ServerLevel server, RebornMobEntity entity) {
        server.playSound(null, BlockPos.containing(entity.position()), ModSounds.ALL.get("victorymmm").get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    private static void explode(ServerLevel server, RebornMobEntity entity, Level.ExplosionInteraction interaction) {
        server.explode(null, entity.getX(), entity.getY(), entity.getZ(), 0.0F, interaction);
    }

    private static void sendParticles(ServerLevel server, ParticleOptions particle, RebornMobEntity entity, int count) {
        server.sendParticles(particle, entity.getX(), entity.getY(), entity.getZ(), count, 3.0D, 3.0D, 3.0D, 1.0D);
    }
}
