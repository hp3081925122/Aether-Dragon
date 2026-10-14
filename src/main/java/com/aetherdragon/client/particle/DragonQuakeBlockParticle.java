package com.aetherdragon.client.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 被震起的地面方块：渲染真实方块模型，弹起后落回原位消失；坐标为方块最小角，yd 为起跳速度
public class DragonQuakeBlockParticle extends Particle {
    private final BlockState state;
    private final BlockPos blockPos;
    private final double baseY;
    private final float tiltX;
    private final float tiltZ;

    protected DragonQuakeBlockParticle(ClientLevel level, double x, double y, double z, double lift, BlockState state) {
        super(level, x, y, z);
        this.state = state;
        this.blockPos = BlockPos.containing(x + 0.5D, y + 0.5D, z + 0.5D);
        this.baseY = y;
        this.xd = 0.0D;
        this.yd = lift;
        this.zd = 0.0D;
        this.gravity = 1.6F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = 40;
        // 随机轻微倾斜，让一圈方块不那么整齐
        this.tiltX = (this.random.nextFloat() - 0.5F) * 30.0F;
        this.tiltZ = (this.random.nextFloat() - 0.5F) * 30.0F;
    }

    @Override
    public void tick() {
        super.tick();
        // 落回地面即消失
        if (this.age > 2 && this.y <= this.baseY) {
            remove();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        double x = Mth.lerp(partialTicks, this.xo, this.x) - cam.x();
        double y = Math.max(this.baseY, Mth.lerp(partialTicks, this.yo, this.y)) - cam.y();
        double z = Mth.lerp(partialTicks, this.zo, this.z) - cam.z();
        // 倾斜幅度随离地高度变化
        float height = (float) (y + cam.y() - this.baseY);
        float tilt = Mth.clamp(height * 1.5F, 0.0F, 1.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(x + 0.5D, y + 0.5D, z + 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(this.tiltX * tilt));
        poseStack.mulPose(Axis.ZP.rotationDegrees(this.tiltZ * tilt));
        poseStack.scale(0.98F, 0.98F, 0.98F);
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(this.level, this.blockPos.above());
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(this.state, poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    public static final class Provider implements ParticleProvider<BlockParticleOption> {
        @Override
        public Particle createParticle(BlockParticleOption options, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new DragonQuakeBlockParticle(level, x, y, z, yd, options.getState());
        }
    }
}
