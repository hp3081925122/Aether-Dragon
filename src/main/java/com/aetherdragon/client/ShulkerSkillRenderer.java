package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.ShulkerSkillEntity;
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

// 远古潜影贝技能实体渲染：潜影弹为面向相机的发光圆盘，碎刃为沿速度旋转的十字面片，缠藤为三根摇摆的藤蔓
public class ShulkerSkillRenderer extends EntityRenderer<ShulkerSkillEntity> {
    private static final ResourceLocation ORB = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/shulker_skill/shulker_orb.png");
    private static final ResourceLocation SHARD = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/shulker_skill/shulker_shard.png");
    private static final ResourceLocation VINE = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/shulker_skill/shulker_vine.png");
    private static final int FULL_BRIGHT = 15728880;

    public ShulkerSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ShulkerSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTicks;
        switch (entity.getKind()) {
            case ShulkerSkillEntity.SHARD -> {
                // 碎刃：沿速度方向，绕自身轴旋转
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, entity.yRotO, entity.getYRot())));
                poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(age * 30.0F));
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SHARD));
                for (int i = 0; i < 2; i++) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(i * 90.0F));
                    quad(consumer, poseStack, -0.35F, 0.35F, -0.7F, 0.7F, light, 1.0F);
                    poseStack.popPose();
                }
                poseStack.popPose();
            }
            case ShulkerSkillEntity.VINE -> {
                float height = entity.getVineHeight(partialTicks);
                if (height > 0.0F) {
                    // 缠藤：三根藤蔓围成一簇，从地下冒出并来回摇摆
                    VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(VINE));
                    for (int i = 0; i < 3; i++) {
                        poseStack.pushPose();
                        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 29.0F + i * 120.0F));
                        poseStack.translate(0.45D, 0.0D, 0.0D);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(-12.0F + Mth.sin(age * 0.25F + i * 2.0F) * 8.0F));
                        poseStack.translate(0.0D, height - VINE_LENGTH, 0.0D);
                        quad(consumer, poseStack, -0.5F, 0.5F, 0.0F, VINE_LENGTH, FULL_BRIGHT, 1.0F);
                        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                        quad(consumer, poseStack, -0.5F, 0.5F, 0.0F, VINE_LENGTH, FULL_BRIGHT, 1.0F);
                        poseStack.popPose();
                    }
                }
            }
            default -> {
                // 潜影弹：面向相机，自转并脉动
                float size = 0.32F + 0.05F * Mth.sin(age * 0.7F);
                poseStack.pushPose();
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
                poseStack.mulPose(Axis.ZP.rotationDegrees(age * 15.0F));
                VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
                quad(consumer, poseStack, -size, size, -size, size, FULL_BRIGHT, 1.0F);
                poseStack.popPose();
            }
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    private static final float VINE_LENGTH = 3.0F;

    // 单面四边形：x 从 x0 到 x1，y 从 y0 到 y1
    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x0, float x1, float y0, float y1, int light, float alpha) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(consumer, pose, normal, x0, y0, 0.0F, 1.0F, light, alpha);
        vertex(consumer, pose, normal, x1, y0, 1.0F, 1.0F, light, alpha);
        vertex(consumer, pose, normal, x1, y1, 1.0F, 0.0F, light, alpha);
        vertex(consumer, pose, normal, x0, y1, 0.0F, 0.0F, light, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float u, float v, int light, float alpha) {
        consumer.vertex(pose, x, y, 0.0F).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public boolean shouldRender(ShulkerSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(ShulkerSkillEntity entity) {
        return ORB;
    }
}
