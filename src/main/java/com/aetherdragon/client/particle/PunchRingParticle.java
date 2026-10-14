package com.aetherdragon.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// 拳压冲击环：竖直圆环垂直于朝向，一边扩张一边向前推进；xd 为最终半径，yd 为朝向角度，zd 为每 tick 前进距离
public class PunchRingParticle extends TextureSheetParticle {
    private final float radius;
    private final float yaw;

    protected PunchRingParticle(ClientLevel level, double x, double y, double z, double radius, double yaw, double speed, SpriteSet sprites) {
        super(level, x, y, z);
        this.radius = (float) Math.max(0.3D, radius);
        this.yaw = (float) yaw * Mth.DEG_TO_RAD;
        this.lifetime = 9;
        this.gravity = 0.0F;
        this.friction = 0.8F;
        this.hasPhysics = false;
        // 沿朝向前进，逐渐减速
        this.xd = -Mth.sin(this.yaw) * speed;
        this.yd = 0.0D;
        this.zd = Mth.cos(this.yaw) * speed;
        this.pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float progress = Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
        // 快速扩张后减缓
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        float r = this.radius * (0.25F + 0.75F * eased);
        float alpha = 1.0F - progress * progress;
        // 圆环平面由右方向与竖直方向张成
        float rx = -Mth.cos(this.yaw) * r;
        float rz = -Mth.sin(this.yaw) * r;
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = 15728880;
        // 正反两面都画
        buffer.vertex(px - rx, py + r, pz - rz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px - rx, py - r, pz - rz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px + rx, py - r, pz + rz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px + rx, py + r, pz + rz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px + rx, py + r, pz + rz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px + rx, py - r, pz + rz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px - rx, py - r, pz - rz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(px - rx, py + r, pz - rz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new PunchRingParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
