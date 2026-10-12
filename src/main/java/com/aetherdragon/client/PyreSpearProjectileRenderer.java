package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.RebornProjectileEntity;
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
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

// 燔祭长矛投射物渲染：使用物品栏贴图，沿飞行方向摆放（十字交叉面片，绕枪身自转）
public final class PyreSpearProjectileRenderer extends EntityRenderer<RebornProjectileEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/item/pyre_spear.png");
    // 贴图半边长；物品贴图中长矛沿对角线，矛尖在右上角
    private static final float HALF = 0.6F;
    // 整体向后偏移，让矛尖贴近实体位置
    private static final float BACK = -0.35F;

    public PyreSpearProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(RebornProjectileEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float yRot = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        float age = entity.tickCount + partialTicks;
        // 本地 +x 为飞行方向
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(xRot));
        poseStack.translate(BACK, 0.0D, 0.0D);
        if (!entity.isStuck()) {
            // 飞行时绕枪身自转
            poseStack.mulPose(Axis.XP.rotationDegrees(age * 24.0F));
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int i = 0; i < 2; i++) {
            // 两片面片互成 90 度；面内旋转 -45 度，把贴图对角线上的长矛转到飞行方向
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(i * 90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45.0F));
            quad(consumer, poseStack, -HALF, HALF, -HALF, HALF, light);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    // 单面四边形：x 从 x0 到 x1，y 从 y0 到 y1，贴图 u 随 x 递增
    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x0, float x1, float y0, float y1, int light) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(consumer, pose, normal, x0, y0, 0.0F, 1.0F, light);
        vertex(consumer, pose, normal, x1, y0, 1.0F, 1.0F, light);
        vertex(consumer, pose, normal, x1, y1, 1.0F, 0.0F, light);
        vertex(consumer, pose, normal, x0, y1, 0.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v, int light) {
        consumer.addVertex(pose, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRender(RebornProjectileEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(RebornProjectileEntity entity) {
        return TEXTURE;
    }
}

