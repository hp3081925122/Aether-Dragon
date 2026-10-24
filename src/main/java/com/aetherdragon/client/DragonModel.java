package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

public final class DragonModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "aether_dragon");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entities/dragon.png");
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "aether_dragon");

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(T entity) {
        return ANIMATION;
    }

}
