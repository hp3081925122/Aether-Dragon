package com.aetherdragon.client;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModItems;
import com.aetherdragon.client.model.ShadayArmorModel;
import com.aetherdragon.client.particle.*;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SmokeParticle;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOBS.get("aetherdragon").get(), DragonRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON_SKILL.get(), DragonSkillRenderer::new);
    }
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ShadayArmorModel.LAYER_LOCATION, () -> ShadayArmorModel.createBodyLayer(false));
        event.registerLayerDefinition(ShadayArmorModel.BABY_LAYER_LOCATION, () -> ShadayArmorModel.createBodyLayer(true));
    }
    public static void registerItemExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new ShadayArmorClientExtensions(), ModItems.ALL.get("shaday_helmet").get(), ModItems.ALL.get("shaday_chestplate").get(), ModItems.ALL.get("shaday_leggings").get());
    }
    public static void registerParticleGroups(net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent event) {
        event.register(RebornParticleGroup.TYPE, RebornParticleGroup::new);
    }
    public static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FIRE.get(), FlameParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RUNESEFFECT.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WHITESMOKE.get(), SmokeParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DRAGON_SHOCKWAVE.get(), DragonShockwaveParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DRAGON_SHARD.get(), DragonShardParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DRAGON_WARNING.get(), DragonShockwaveParticle.WarningProvider::new);
        event.registerSpriteSet(ModParticles.DRAGON_SLASH.get(), DragonDecalParticle.SlashProvider::new);
        event.registerSpecial(ModParticles.DRAGON_QUAKE_BLOCK.get(), new DragonQuakeBlockParticle.Provider());
    }
}
