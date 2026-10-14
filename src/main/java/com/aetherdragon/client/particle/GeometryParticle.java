package com.aetherdragon.client.particle;

import com.aetherdragon.client.GeometryCapture;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class GeometryParticle extends SingleQuadParticle implements CapturedParticle {
    private static final RenderType PARTICLE_RENDER_TYPE = RenderType.create("aether_dragon_particles",
            RenderSetup.builder(RenderPipelines.TRANSLUCENT_PARTICLE)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES)
                    .useLightmap().sortOnUpload().createRenderSetup());

    static {
        com.mojang.logging.LogUtils.getLogger().debug("Aether Dragon geometry particles use the vanilla translucent particle pipeline without entity overlay or face lighting");
    }

    protected GeometryParticle(ClientLevel level, double x, double y, double z) { super(level, x, y, z, null); }
    protected GeometryParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd, null);
    }
    protected void pickSprite(SpriteSet sprites) { setSprite(sprites.get(this.random)); }
    @Override
    protected Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override
    public ParticleRenderType getGroup() { return RebornParticleGroup.TYPE; }
    @Override
    public void extractGeometry(GeometryCapture geometry, Camera camera, float partialTicks) {
        render(geometry.getBuffer(PARTICLE_RENDER_TYPE), camera, partialTicks);
    }
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Quaternionf rotation = new Quaternionf();
        getFacingCameraMode().setRotation(rotation, camera, partialTicks);
        rotation.rotateZ(Mth.lerp(partialTicks, oRoll, roll));
        float size = getQuadSize(partialTicks);
        float px = (float)(Mth.lerp(partialTicks, xo, x) - camera.position().x);
        float py = (float)(Mth.lerp(partialTicks, yo, y) - camera.position().y);
        float pz = (float)(Mth.lerp(partialTicks, zo, z) - camera.position().z);
        float[][] corners = {{1,-1,getU1(),getV1()},{1,1,getU1(),getV0()},{-1,1,getU0(),getV0()},{-1,-1,getU0(),getV1()}};
        for (float[] corner : corners) {
            Vector3f pos = new Vector3f(corner[0], corner[1], 0).rotate(rotation).mul(size).add(px, py, pz);
            buffer.addVertex(pos.x, pos.y, pos.z).setUv(corner[2],corner[3]).setColor(rCol,gCol,bCol,alpha)
                    .setLight(getLightCoords(partialTicks)).setNormal(0,1,0);
        }
    }
}
