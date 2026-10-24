package com.aetherdragon.client;

import com.aetherdragon.entity.TamedAetherDragonEntity;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;

public final class TamedDragonRenderer extends GeoEntityRenderer<TamedAetherDragonEntity, DragonRenderState> {
    public TamedDragonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonModel<>());
        this.shadowRadius = 2.0F;
    }

    @Override
    public DragonRenderState createRenderState(TamedAetherDragonEntity entity, Void context) {
        return new DragonRenderState();
    }

    @Override
    public void extractRenderState(TamedAetherDragonEntity entity, DragonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        DragonRenderer.alignMovementTransitions(state);
        state.beam = entity.isLaserActive();
        state.beamEnd = entity.getLaserEnd();
    }

    @Override
    public void submitRenderTasks(RenderPassInfo<DragonRenderState> pass, OrderedSubmitNodeCollector collector, RenderType type) {
        DragonRenderer.submitJawBeam(pass, collector);
        super.submitRenderTasks(pass, collector, type);
    }
}
