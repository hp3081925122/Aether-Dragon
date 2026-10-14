package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.SpiritSkillEntity;
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

// 灵魂技能实体渲染：符文为面向相机的旋转光盘，天剑为竖直十字面片
public class SpiritSkillRenderer extends EntityRenderer<SpiritSkillEntity> {
    private static final ResourceLocation RUNE = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/spirit_skill/spirit_rune.png");
    private static final ResourceLocation SWORD = new ResourceLocation(AetherDragon.MOD_ID, "textures/entity/spirit_skill/spirit_sword.png");
    private static final int FULL_BRIGHT = 15728880;

    public SpiritSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SpiritSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTicks;
        if (entity.getKind() == SpiritSkillEntity.SWORD) {
            // 天剑：实体位置为剑尖，剑身向上 4.8 格；位置按时间插值
            float alpha = entity.getSwordAlpha(partialTicks);
            double y = entity.getSwordY(age);
            double lerpY = Mth.lerp(partialTicks, entity.yo, entity.getY());
            poseStack.pushPose();
            poseStack.translate(0.0D, y - lerpY, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 53.0F));
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(SWORD));
            for (int i = 0; i < 2; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(i * 90.0F));
                Matrix4f pose = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                vertex(consumer, pose, normal, -0.6F, 0.0F, 0.0F, 0.0F, 1.0F, alpha);
                vertex(consumer, pose, normal, 0.6F, 0.0F, 0.0F, 1.0F, 1.0F, alpha);
                vertex(consumer, pose, normal, 0.6F, 4.8F, 0.0F, 1.0F, 0.0F, alpha);
                vertex(consumer, pose, normal, -0.6F, 4.8F, 0.0F, 0.0F, 0.0F, alpha);
                poseStack.popPose();
            }
            poseStack.popPose();
        } else {
            // 符文：面向相机，自转并轻微脉动
            float size = 0.55F + 0.08F * Mth.sin(age * 0.4F);
            float grow = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
            size *= grow;
            poseStack.pushPose();
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.ZP.rotationDegrees(age * 12.0F));
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(RUNE));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            vertex(consumer, pose, normal, -size, -size, 0.0F, 0.0F, 1.0F, 1.0F);
            vertex(consumer, pose, normal, size, -size, 0.0F, 1.0F, 1.0F, 1.0F);
            vertex(consumer, pose, normal, size, size, 0.0F, 1.0F, 0.0F, 1.0F);
            vertex(consumer, pose, normal, -size, size, 0.0F, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    // 全亮顶点，带透明度
    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v, float alpha) {
        consumer.vertex(pose, x, y, z).color(1.0F, 1.0F, 1.0F, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public boolean shouldRender(SpiritSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(SpiritSkillEntity entity) {
        if (entity.getKind() == SpiritSkillEntity.SWORD) {
            return SWORD;
        }
        return RUNE;
    }
}
