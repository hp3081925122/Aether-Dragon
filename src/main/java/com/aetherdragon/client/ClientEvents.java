package com.aetherdragon.client;

import com.aetherdragon.registry.ModEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MOBS.get("aetherdragon").get(), DragonRenderer::new);
    }
}
