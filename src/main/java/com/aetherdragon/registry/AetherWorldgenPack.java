package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AetherWorldgenPack {
    private AetherWorldgenPack() {
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || !ModList.get().isLoaded("aether")) {
            return;
        }
        Path path = ModList.get().getModFileById(AetherDragon.MOD_ID).getFile().findResource("datapacks/aether_world");
        Pack pack = Pack.readMetaAndCreate(AetherDragon.MOD_ID + ":aether_world", Component.literal("Aether Dragon - Aether World"), true,
                id -> new PathPackResources(id, path, true), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
        if (pack == null) {
            LogUtils.getLogger().error("Failed to create Aether Dragon aether_world data pack from {}", path);
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
        LogUtils.getLogger().info("Aether detected, Aether Dragon Gold City moved to the Aether dimension");
    }
}
