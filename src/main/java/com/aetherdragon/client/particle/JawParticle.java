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

// 咬合粒子：一对齿列在朝向平面内相向合拢后淡出；xd 为尺寸，yd 为朝向角度，zd 为 0/1 表示两半中的哪一半
// 贴图约定：图像底边为齿尖，顶边为颚背
public class JawParticle extends TextureSheetParticle {
    // 合拢用时与总时长
    private static final float CLOSE_TICKS = 3.0F;
    private final float size;
    private final float yaw;
    private final boolean second;
    // 为真时上下合拢（撕咬），为假时左右合拢（钳夹）
    private final boolean vertical;

    protected JawParticle(ClientLevel level, double x, double y, double z, double size, double yaw, double half, boolean vertical, SpriteSet sprites) {
        super(level, x, y, z);
        this.size = (float) Math.max(0.3D, size);
        this.yaw = (float) yaw * Mth.DEG_TO_RAD;
        this.second = half > 0.5D;
        this.vertical = vertical;
        this.lifetime = 12;
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
        float age = this.age + partialTicks;
        // 合拢进度：先慢后快，猛然咬合
        float close = Mth.clamp(age / CLOSE_TICKS, 0.0F, 1.0F);
        close = close * close;
        // 齿尖离中线的距离：从张开到略微交错
        float height = this.size * 0.5F;
        float gap = Mth.lerp(close, this.size * 0.9F, -height * 0.2F);
        // 淡出
        float fade = Mth.clamp((age - 6.0F) / 6.0F, 0.0F, 1.0F);
        float alpha = 1.0F - fade * fade;
        // 右方向与上方向（与 DragonDecalParticle 一致：前方为 (-sin, cos)）
        float rx = -Mth.cos(this.yaw);
        float rz = -Mth.sin(this.yaw);
        // 合拢轴 a 与齿列展开轴 s
        float ax = 0.0F;
        float ay = 1.0F;
        float az = 0.0F;
        float sx = rx;
        float sy = 0.0F;
        float sz = rz;
        if (!this.vertical) {
            ax = rx;
            ay = 0.0F;
            az = rz;
            sx = 0.0F;
            sy = 1.0F;
            sz = 0.0F;
        }
        if (this.second) {
            // 另一半位于中线另一侧，齿尖同样朝向中线
            ax = -ax;
            ay = -ay;
            az = -az;
        }
        float width = this.size;
        float near = gap;
        float far = gap + height;
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = 15728880;
        // 四角：远端左、近端左、近端右、远端右；v0 为颚背（远端），v1 为齿尖（近端）
        float x0 = px + ax * far - sx * width;
        float y0 = py + ay * far - sy * width;
        float z0 = pz + az * far - sz * width;
        float x1 = px + ax * near - sx * width;
        float y1 = py + ay * near - sy * width;
        float z1 = pz + az * near - sz * width;
        float x2 = px + ax * near + sx * width;
        float y2 = py + ay * near + sy * width;
        float z2 = pz + az * near + sz * width;
        float x3 = px + ax * far + sx * width;
        float y3 = py + ay * far + sy * width;
        float z3 = pz + az * far + sz * width;
        // 正反两面都画
        buffer.vertex(x0, y0, z0).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x1, y1, z1).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x2, y2, z2).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x3, y3, z3).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x3, y3, z3).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x2, y2, z2).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x1, y1, z1).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
        buffer.vertex(x0, y0, z0).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, alpha).uv2(light).endVertex();
    }

    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // 撕咬：上下合拢
    public static final class BiteProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BiteProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new JawParticle(level, x, y, z, xd, yd, zd, true, this.sprites);
        }
    }

    // 钳夹：左右合拢
    public static final class PinchProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public PinchProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new JawParticle(level, x, y, z, xd, yd, zd, false, this.sprites);
        }
    }
}
