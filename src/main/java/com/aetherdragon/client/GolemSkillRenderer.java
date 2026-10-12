package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.GolemSkillEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// 水晶石傀儡晶体渲染：主晶刺 + 两根斜生小晶刺组成晶簇，按破土进度从地下顶出
public class GolemSkillRenderer extends EntityRenderer<GolemSkillEntity> {
    private static final ResourceLocation CRYSTAL = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/golem_skill/golem_crystal.png");
    private static final int FULL_BRIGHT = 15728880;

    public GolemSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(GolemSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float rise = entity.getRise(partialTicks);
        if (rise > 0.0F) {
            float scale = entity.getScale();
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(CRYSTAL));
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
            // 整体从地下顶出
            poseStack.translate(0.0D, (rise - 1.0F) * 3.0F * scale, 0.0D);
            // 主晶刺略微倾斜
            float tilt = (entity.getId() % 7) * 2.0F - 6.0F;
            drawCrystal(poseStack, consumer, 0.0F, 0.0F, tilt, 0.5F * scale, 3.0F * scale);
            // 两根小晶刺向外斜生
            drawCrystal(poseStack, consumer, 0.35F * scale, 0.1F * scale, 28.0F, 0.3F * scale, 1.6F * scale);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(140.0F));
            drawCrystal(poseStack, consumer, 0.35F * scale, -0.1F * scale, 24.0F, 0.26F * scale, 1.3F * scale);
            poseStack.popPose();
            poseStack.popPose();
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    // 单根晶刺：三片面片绕竖轴均分，offset 处向外倾斜 tilt 度
    private static void drawCrystal(PoseStack poseStack, VertexConsumer consumer, float offsetX, float offsetZ, float tilt, float halfWidth, float height) {
        poseStack.pushPose();
        poseStack.translate(offsetX, 0.0D, offsetZ);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-tilt));
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            vertex(consumer, pose, normal, -halfWidth, 0.0F, 0.0F, 1.0F);
            vertex(consumer, pose, normal, halfWidth, 0.0F, 1.0F, 1.0F);
            vertex(consumer, pose, normal, halfWidth, height, 1.0F, 0.0F);
            vertex(consumer, pose, normal, -halfWidth, height, 0.0F, 0.0F);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRender(GolemSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(GolemSkillEntity entity) {
        return CRYSTAL;
    }
}

