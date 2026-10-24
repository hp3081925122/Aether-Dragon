package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AetherWorldgenPack {
    private AetherWorldgenPack() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded("aether")) {
            return;
        }
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "datapacks/aether_world"), PackType.SERVER_DATA,
                Component.literal("Aether Dragon - Aether World"), PackSource.BUILT_IN, true, Pack.Position.TOP);
        LogUtils.getLogger().info("Aether detected, Aether Dragon Gold City moved to the Aether dimension");
    }
}
