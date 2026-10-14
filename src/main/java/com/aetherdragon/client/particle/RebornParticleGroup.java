package com.aetherdragon.client.particle;

import com.aetherdragon.client.GeometryCapture;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState;

public final class RebornParticleGroup extends ParticleGroup<Particle> {
    public static final ParticleRenderType TYPE = new ParticleRenderType("aether_dragon:geometry");
    public RebornParticleGroup(ParticleEngine engine) { super(engine); }
    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partialTick) {
        GeometryCapture geometry = new GeometryCapture();
        for (Particle particle : particles) {
            if (particle instanceof CapturedParticle captured) captured.extractGeometry(geometry, camera, partialTick);
        }
        return (collector, cameraState) -> geometry.submit(new PoseStack(), collector);
    }
}
