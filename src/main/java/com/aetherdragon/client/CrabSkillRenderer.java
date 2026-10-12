package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.CrabSkillEntity;
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

// 盐蟹王技能实体渲染：泡泡为面向相机的半透明球面，水柱与浪墙为滚动 UV 的半透明面片，盐晶为阶梯晶簇
public class CrabSkillRenderer extends EntityRenderer<CrabSkillEntity> {
    private static final ResourceLocation BUBBLE = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/crab_skill/crab_bubble.png");
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/crab_skill/crab_water.png");
    private static final ResourceLocation WAVE = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/crab_skill/crab_wave.png");
    private static final ResourceLocation SALT = ResourceLocation.fromNamespaceAndPath(AetherDragon.MOD_ID, "textures/entity/crab_skill/crab_salt.png");
    private static final int FULL_BRIGHT = 15728880;
    // 浪墙沿宽度切成的段数
    private static final int WAVE_SEGMENTS = 12;

    public CrabSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CrabSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        float age = entity.tickCount + partialTicks;
        switch (entity.getKind()) {
            case CrabSkillEntity.GEYSER -> renderGeyser(entity, partialTicks, age, poseStack, buffers);
            case CrabSkillEntity.WAVE -> renderWave(entity, partialTicks, age, poseStack, buffers);
            case CrabSkillEntity.SALT -> renderSalt(entity, partialTicks, poseStack, buffers);
            default -> renderBubble(age, poseStack, buffers);
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    private void renderBubble(float age, PoseStack poseStack, MultiBufferSource buffers) {
        // 泡泡：面向相机，轻微晃动缩放
        float size = 0.45F + 0.04F * Mth.sin(age * 0.5F);
        float grow = Mth.clamp(age / 4.0F, 0.0F, 1.0F);
        size *= grow;
        float stretch = 1.0F + 0.06F * Mth.sin(age * 0.8F);
        poseStack.pushPose();
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(BUBBLE));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        vertex(consumer, pose, normal, -size * stretch, -size, 0.0F, 0.0F, 1.0F, 0.75F);
        vertex(consumer, pose, normal, size * stretch, -size, 0.0F, 1.0F, 1.0F, 0.75F);
        vertex(consumer, pose, normal, size * stretch, size, 0.0F, 1.0F, 0.0F, 0.75F);
        vertex(consumer, pose, normal, -size * stretch, size, 0.0F, 0.0F, 0.0F, 0.75F);
        poseStack.popPose();
    }

    private void renderGeyser(CrabSkillEntity entity, float partialTicks, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float height = entity.getGeyserHeight(partialTicks);
        if (height <= 0.0F) {
            return;
        }
        // 水柱：四片面片围成柱体，UV 向上滚动；外层更宽更淡
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(WATER));
        float scroll = -age * 0.12F;
        float vLength = height / 2.0F;
        float[] widths = {1.4F, 0.9F};
        float[] alphas = {0.45F, 0.8F};
        for (int layer = 0; layer < 2; layer++) {
            float half = widths[layer] * 0.5F;
            for (int i = 0; i < 4; i++) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(i * 45.0F + age * 6.0F));
                Matrix4f pose = poseStack.last().pose();
                Matrix3f normal = poseStack.last().normal();
                vertex(consumer, pose, normal, -half, 0.0F, 0.0F, 0.0F, scroll + vLength, alphas[layer]);
                vertex(consumer, pose, normal, half, 0.0F, 0.0F, 1.0F, scroll + vLength, alphas[layer]);
                vertex(consumer, pose, normal, half * 0.8F, height, 0.0F, 1.0F, scroll, alphas[layer]);
                vertex(consumer, pose, normal, -half * 0.8F, height, 0.0F, 0.0F, scroll, alphas[layer]);
                poseStack.popPose();
            }
        }
    }

    private void renderWave(CrabSkillEntity entity, float partialTicks, float age, PoseStack poseStack, MultiBufferSource buffers) {
        float height = entity.getWaveHeight(partialTicks);
        if (height <= 0.0F) {
            return;
        }
        // 浪墙：横向分段的弧形墙，两端落后于中央，顶部前倾卷起
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(WAVE));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partialTicks, entity.yRotO, entity.getYRot())));
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        float half = CrabSkillEntity.WAVE_HALF_WIDTH;
        float scroll = age * 0.05F;
        for (int i = 0; i < WAVE_SEGMENTS; i++) {
            float l0 = -half + 2.0F * half * i / WAVE_SEGMENTS;
            float l1 = -half + 2.0F * half * (i + 1) / WAVE_SEGMENTS;
            float b0 = -l0 * l0 / (half * half) * 1.2F;
            float b1 = -l1 * l1 / (half * half) * 1.2F;
            float u0 = scroll + i * 0.25F;
            float u1 = scroll + (i + 1) * 0.25F;
            // 墙身：从地面到浪尖
            vertex(consumer, pose, normal, l0, 0.0F, b0, u0, 1.0F, 0.8F);
            vertex(consumer, pose, normal, l1, 0.0F, b1, u1, 1.0F, 0.8F);
            vertex(consumer, pose, normal, l1, height, b1 + 0.4F, u1, 0.15F, 0.8F);
            vertex(consumer, pose, normal, l0, height, b0 + 0.4F, u0, 0.15F, 0.8F);
            // 浪尖：向前卷出一截
            vertex(consumer, pose, normal, l0, height, b0 + 0.4F, u0, 0.15F, 0.9F);
            vertex(consumer, pose, normal, l1, height, b1 + 0.4F, u1, 0.15F, 0.9F);
            vertex(consumer, pose, normal, l1, height - 0.3F, b1 + 1.1F, u1, 0.0F, 0.9F);
            vertex(consumer, pose, normal, l0, height - 0.3F, b0 + 1.1F, u0, 0.0F, 0.9F);
        }
        poseStack.popPose();
    }

    private void renderSalt(CrabSkillEntity entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffers) {
        float rise = entity.getSaltRise(partialTicks);
        if (rise <= 0.0F) {
            return;
        }
        // 盐晶：三片交叉面片，从地下顶出
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(SALT));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 37.0F));
        poseStack.translate(0.0D, (rise - 1.0F) * 2.4F, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.getId() % 5) * 3.0F - 6.0F));
        for (int i = 0; i < 3; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(i * 60.0F));
            Matrix4f pose = poseStack.last().pose();
            Matrix3f normal = poseStack.last().normal();
            vertex(consumer, pose, normal, -0.6F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
            vertex(consumer, pose, normal, 0.6F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            vertex(consumer, pose, normal, 0.6F, 2.4F, 0.0F, 1.0F, 0.0F, 1.0F);
            vertex(consumer, pose, normal, -0.6F, 2.4F, 0.0F, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    // 全亮顶点，带透明度
    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v, float alpha) {
        consumer.addVertex(pose, x, y, z).setColor(1.0F, 1.0F, 1.0F, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(0.0F, 1.0F, 0.0F);
    }

    @Override
    public boolean shouldRender(CrabSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(CrabSkillEntity entity) {
        return BUBBLE;
    }
}

