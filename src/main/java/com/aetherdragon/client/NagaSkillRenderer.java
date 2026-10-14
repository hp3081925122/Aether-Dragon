package com.aetherdragon.client;

import com.aetherdragon.entity.NagaSkillEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 天境娜迦技能实体渲染：石柱为三格方块堆叠从地下升起，巨石为旋转的大方块
public class NagaSkillRenderer extends EntityRenderer<NagaSkillEntity> {
    private static final BlockState PILLAR_BODY = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState PILLAR_TOP = Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    private static final BlockState BOULDER = Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public NagaSkillRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(NagaSkillEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (entity.getKind() == NagaSkillEntity.BOULDER) {
            // 巨石：1.6 倍大小，绕两轴翻滚
            float spin = entity.getSpin(partialTicks);
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 41.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(spin));
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin * 0.6F));
            poseStack.scale(1.6F, 1.6F, 1.6F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            this.blocks.renderSingleBlock(BOULDER, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        } else {
            float height = entity.getPillarHeight(partialTicks);
            if (height > 0.0F) {
                // 石柱：1.2 格见方、3 格高，按比例从地下顶出
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(entity.getId() * 23.0F));
                poseStack.translate(0.0D, 3.0F * height - 3.0F, 0.0D);
                poseStack.scale(1.2F, 1.0F, 1.2F);
                for (int i = 0; i < 3; i++) {
                    poseStack.pushPose();
                    poseStack.translate(-0.5D, i, -0.5D);
                    BlockState state = PILLAR_BODY;
                    if (i == 2) {
                        state = PILLAR_TOP;
                    }
                    this.blocks.renderSingleBlock(state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
                poseStack.popPose();
            }
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
    }

    @Override
    public boolean shouldRender(NagaSkillEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(NagaSkillEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
