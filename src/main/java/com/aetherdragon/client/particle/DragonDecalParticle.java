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
import net.minecraft.world.phys.AABB;

// 水平爪痕刀光：xd 为半尺寸，yd 为朝向角度，zd 为 1 时左右镜像
public class DragonDecalParticle extends TextureSheetParticle {
    private final float size;
    private final float yaw;
    private final boolean mirror;

    protected DragonDecalParticle(ClientLevel level, double x, double y, double z, double size, double yaw, double mirror, SpriteSet sprites) {
        super(level, x, y, z);
        // 刀光一闪而过
        this.size = (float) Math.max(0.5D, size);
        this.yaw = (float) yaw * Mth.DEG_TO_RAD;
        this.mirror = mirror > 0.5D;
        this.lifetime = 15;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float progress = Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
        // 快速张开并淡出
        float scale = this.size * (0.85F + 0.2F * Math.min(1.0F, progress * 3.0F));
        float fadeProgress = Mth.clamp((progress - 0.45F) / 0.55F, 0.0F, 1.0F);
        float alpha = 1.0F - fadeProgress * fadeProgress;
        // 图像上方对应朝向前方，右方对应朝向右侧
        float fx = -Mth.sin(this.yaw) * scale;
        float fz = Mth.cos(this.yaw) * scale;
        float rx = -Mth.cos(this.yaw) * scale;
        float rz = -Mth.sin(this.yaw) * scale;
        if (this.mirror) {
            rx = -rx;
            rz = -rz;
        }
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = 15728880;
        // 四角：左前、左后、右后、右前
        float ax = px - rx + fx;
        float az = pz - rz + fz;
        float bx = px - rx - fx;
        float bz = pz - rz - fz;
        float cx = px + rx - fx;
        float cz = pz + rz - fz;
        float dx = px + rx + fx;
        float dz = pz + rz + fz;
        // 正反两面都画
        buffer.addVertex(ax, py, az).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(bx, py, bz).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(cx, py, cz).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(dx, py, dz).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(dx, py, dz).setUv(u1, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(cx, py, cz).setUv(u1, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(bx, py, bz).setUv(u0, v1).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
        buffer.addVertex(ax, py, az).setUv(u0, v0).setColor(this.rCol, this.gCol, this.bCol, alpha).setLight(light);
    }


    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return AABB.INFINITE;
    }

    public static final class SlashProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SlashProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new DragonDecalParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
