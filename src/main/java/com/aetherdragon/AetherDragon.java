package com.aetherdragon;

import com.aetherdragon.registry.ModBlocks;
import com.aetherdragon.registry.ModCreativeTabs;
import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModItems;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AetherDragon.MOD_ID)
public final class AetherDragon {
    public static final String MOD_ID = "aether_dragon";

    public AetherDragon(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, DragonCombatConfig.SPEC, "aether_dragon-common.toml");
        ModBlocks.BLOCKS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        ModCreativeTabs.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModSounds.SOUNDS.register(bus);
        bus.addListener(this::registerAttributes);
        com.aetherdragon.network.DragonMountNetwork.register();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        for (var entry : ModEntities.MOBS.entrySet()) {
            event.put(entry.getValue().get(), com.aetherdragon.entity.RebornMobEntity.createAttributes(entry.getKey()).build());
        }
        event.put(ModEntities.TAMED_DRAGON.get(), com.aetherdragon.entity.TamedAetherDragonEntity.createAttributes().build());
    }
}



