package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.DragonSkillEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class DragonSkillRenderer extends EntityRenderer<DragonSkillEntity, SkillRenderState> {
    private static final Identifier FEATHER = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/dragon_skill/dragon_feather.png");
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/dragon_skill/dragon_crystal.png");
    private static final Identifier LIGHTNING = Identifier.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/dragon_skill/dragon_lightning.png");
    private static final int FULL_BRIGHT = 15728880;

    public DragonSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SkillRenderState createRenderState() { return new SkillRenderState(); }

    @Override
    public void extractRenderState(DragonSkillEntity entity, SkillRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.geometry = new GeometryCapture();
        extractGeometry(entity, entity.getYRot(), partialTick, new PoseStack(), state.geometry, state.lightCoords);
    }

    @Override
    public void submit(SkillRenderState state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        state.geometry.submit(pose, collector);
    }

    private void extractGeometry(DragonSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        switch (entity.getKind()) {
            case DragonSkillEntity.FEATHER -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, entity.xRotO, entity.getXRot())));
                poseStack.mulPose(Axis.XP.rotationDegrees((entity.tickCount + partialTicks) * 25.0F));
                VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutout(FEATHER));
                drawCross(poseStack, consumer, -1.0F, 1.0F, -1.0F, 1.0F, true);
                poseStack.popPose();
            }
            case DragonSkillEntity.CRYSTAL -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTicks) * 12.0F));
                VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutout(CRYSTAL));
                drawCross(poseStack, consumer, -0.9F, 0.9F, 0.0F, 3.6F, false);
                poseStack.popPose();
            }
            case DragonSkillEntity.SPIKE -> {
                float height = entity.getSpikeHeight(partialTicks);
                if (height > 0.0F) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 37.0F));
                    poseStack.translate(0.0D, 3.0F * height, 0.0D);
                    poseStack.scale(1.0F, -height, 1.0F);
                    VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutout(CRYSTAL));
                    drawCross(poseStack, consumer, -0.8F, 0.8F, 0.0F, 3.0F, false);
                    poseStack.popPose();
                }
            }
            default -> {
                float flash = entity.getThunderFlash(partialTicks);
                if (flash > 0.0F) {
                    renderLightning(entity, partialTicks, poseStack, buffers, flash);
                }
            }
        }
    }

    private static void drawCross(PoseStack poseStack, VertexConsumer consumer, float a0, float a1, float b0, float b1, boolean horizontal) {
        for (int i = 0; i < 2; i++) {
            poseStack.pushPose();
            if (horizontal) {
                poseStack.mulPose(Axis.XP.rotationDegrees(i * 90.0F));
            } else {
                poseStack.mulPose(Axis.YP.rotationDegrees(i * 90.0F));
            }
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            if (horizontal) {
                vertex(consumer, pose, normal, b0, a0, 0.0F, 0.0F, 1.0F);
                vertex(consumer, pose, normal, b1, a0, 0.0F, 1.0F, 1.0F);
                vertex(consumer, pose, normal, b1, a1, 0.0F, 1.0F, 0.0F);
                vertex(consumer, pose, normal, b0, a1, 0.0F, 0.0F, 0.0F);
            } else {
                vertex(consumer, pose, normal, a0, b0, 0.0F, 0.0F, 1.0F);
                vertex(consumer, pose, normal, a1, b0, 0.0F, 1.0F, 1.0F);
                vertex(consumer, pose, normal, a1, b1, 0.0F, 1.0F, 0.0F);
                vertex(consumer, pose, normal, a0, b1, 0.0F, 0.0F, 0.0F);
            }
            poseStack.popPose();
        }
    }

    private void renderLightning(DragonSkillEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, float flash) {
        Vec3 cam = this.entityRenderDispatcher.camera.position();
        double ex = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double ez = Mth.lerp(partialTicks, entity.zo, entity.getZ());
        float dx = (float) (cam.x - ex);
        float dz = (float) (cam.z - ez);
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3F) {
            len = 1.0F;
        }
        float[] widths = {2.6F * flash, 1.0F * flash};
        float v0 = (entity.tickCount % 4) * 0.25F;
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.eyes(LIGHTNING));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        for (float width : widths) {
            float sx = -dz / len * width * 0.5F;
            float sz = dx / len * width * 0.5F;
            vertex(consumer, pose, normal, -sx, 0.0F, -sz, 0.0F, v0 + 2.0F);
            vertex(consumer, pose, normal, sx, 0.0F, sz, 1.0F, v0 + 2.0F);
            vertex(consumer, pose, normal, sx, 40.0F, sz, 1.0F, v0);
            vertex(consumer, pose, normal, -sx, 40.0F, -sz, 0.0F, v0);
        }
    }

    static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRender(DragonSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    public Identifier getTextureLocation(DragonSkillEntity entity) {
        return FEATHER;
    }
}

