package com.aetherdragon;

import com.aetherdragon.registry.ModBlocks;
import com.aetherdragon.registry.ModCreativeTabs;
import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModItems;
import com.aetherdragon.registry.ModArmorMaterials;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod(AetherDragon.MOD_ID)
public final class AetherDragon {
    public static final String MOD_ID = "aether_dragon";

    public AetherDragon(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DragonCombatConfig.SPEC, "aether_dragon-common.toml");
        ModArmorMaterials.ARMOR_MATERIALS.register(bus);
        ModBlocks.BLOCKS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        ModCreativeTabs.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModSounds.SOUNDS.register(bus);
        bus.addListener(this::registerAttributes);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        for (var entry : ModEntities.MOBS.entrySet()) {
            event.put(entry.getValue().get(), com.aetherdragon.entity.RebornMobEntity.createAttributes(entry.getKey()).build());
        }
    }

}



