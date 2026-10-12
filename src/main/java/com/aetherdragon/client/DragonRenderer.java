package com.aetherdragon.client;

import com.aetherdragon.entity.RebornMobEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class DragonRenderer extends GeoEntityRenderer<RebornMobEntity, DragonRenderState> {
    public DragonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonModel());
        this.shadowRadius = 0.5F;
    }

    @Override
    public DragonRenderState createRenderState(RebornMobEntity entity, Void context) {
        return new DragonRenderState();
    }
}
