package com.aetherdragon.entity;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 盐蟹王 boss 战斗：技能选择、服务端时间轴与客户端特效
final class CrabCombat {
    static final int PINCER = 1;
    static final int SLAM = 2;
    static final int RUSH = 3;
    static final int BUBBLES = 4;
    static final int GEYSER = 5;
    static final int TIDAL = 6;
    static final int ENRAGE = 7;
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] DURATION = {0, 30, 50, 46, 50, 56, 60, 50};
    private static final int[] COOLDOWN = {0, 40, 150, 140, 160, 200, 260, 0};
    private static final String[] ANIMATIONS = {"", "skill_pincer", "skill_slam", "skill_rush", "skill_bubbles", "skill_geyser", "skill_tidal", "enrage"};
    static final byte EVENT_PINCER_LEFT = (byte) 150;
    static final byte EVENT_PINCER_RIGHT = (byte) 151;
    static final byte EVENT_SLAM = (byte) 152;
    static final byte EVENT_RUSH_HIT = (byte) 153;
    static final byte EVENT_DRUM = (byte) 154;
    static final byte EVENT_TIDAL = (byte) 155;
    static final byte EVENT_ENRAGE = (byte) 156;
    private static final int RUSH_START = 12;
    private static final int RUSH_END = 30;
    private static final int[] DRUM_TICKS = {10, 16, 22, 28};
    private static final String ENRAGE_TAG = "reborn_crab_enraged";

    private final RebornMobEntity crab;
    private final int[] cooldowns = new int[8];
    private final Set<Integer> hits = new HashSet<>();
    private int skillTicks;
    private int gap = 30;
    private Vec3 rushDir = Vec3.ZERO;
    private Vec3 rushFacing = Vec3.ZERO;
    private int clientSkill;
    private int clientTicks;

    CrabCombat(RebornMobEntity crab) {
        this.crab = crab;
    }

    static String getAnimation(int skill) {
        if (skill <= 0 || skill >= ANIMATIONS.length) {
            return "";
        }
        return "animation.salt_crab_king." + ANIMATIONS[skill];
    }

    void save(CompoundTag tag) {
        tag.putBoolean(ENRAGE_TAG, this.crab.isCrabEnraged());
    }

    void load(CompoundTag tag) {
        this.crab.setBossEnraged(tag.getBoolean(ENRAGE_TAG));
    }

    void stop() {
        this.skillTicks = 0;
        this.crab.setBossSkill(0);
    }

    private boolean enraged() {
        return this.crab.isCrabEnraged();
    }

    private Vec3 forward() {
        float yaw = this.crab.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    private DamageSource source() {
        return this.crab.damageSources().mobAttack(this.crab);
    }

    private double ground(double x, double z, double fromY) {
        return this.crab.findGroundY(x, fromY + 4.0D, z, 10.0D, fromY);
    }

    // 服务端每 tick：冷却、移动、选技能与执行时间轴
    void tick(LivingEntity target) {
        for (int i = 1; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        int skill = this.crab.getCrabSkill();
        if (skill == 0) {
            tickIdle(target);
            return;
        }
        this.crab.getNavigation().stop();
        this.skillTicks++;
        if (this.crab.level() instanceof ServerLevel server) {
            switch (skill) {
                case PINCER -> tickPincer(target);
                case SLAM -> tickSlam(server, target);
                case RUSH -> tickRush(server, target);
                case BUBBLES -> tickBubbles(server, target);
                case GEYSER -> tickGeyser(server, target);
                case TIDAL -> tickTidal(server, target);
                default -> tickEnrage(server);
            }
        }
        if (this.skillTicks >= DURATION[skill]) {
            stop();
            this.gap = 16;
            if (enraged()) {
                this.gap = 9;
            }
        }
    }

    private void tickIdle(LivingEntity target) {
        double distance = this.crab.distanceTo(target);
        this.crab.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 追近目标
        if (distance > 3.5D) {
            if (this.crab.tickCount % 8 == 0 || this.crab.getNavigation().isDone()) {
                this.crab.getNavigation().moveTo(target, 1.1D);
            }
        } else {
            this.crab.getNavigation().stop();
            this.crab.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        Vec3 toward = target.position().subtract(this.crab.position());
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        double front = 1.0D;
        if (flat.lengthSqr() > 1.0E-4D) {
            front = flat.normalize().dot(forward());
        }
        int chosen = 0;
        if (!enraged() && this.crab.getHealth() <= this.crab.getMaxHealth() * 0.5F) {
            // 半血狂暴：盐潮怒号
            chosen = ENRAGE;
            this.crab.setBossEnraged(true);
        } else {
            List<Integer> ready = new ArrayList<>();
            if (distance <= 4.5D && front > 0.3D && this.cooldowns[PINCER] <= 0) {
                ready.add(PINCER);
                ready.add(PINCER);
                ready.add(PINCER);
            }
            if (distance <= 6.5D && this.cooldowns[SLAM] <= 0) {
                ready.add(SLAM);
            }
            if (distance >= 5.0D && distance <= 16.0D && this.cooldowns[RUSH] <= 0) {
                ready.add(RUSH);
                ready.add(RUSH);
            }
            if (distance >= 6.0D && this.cooldowns[BUBBLES] <= 0) {
                ready.add(BUBBLES);
            }
            if (distance >= 5.0D && this.cooldowns[GEYSER] <= 0) {
                ready.add(GEYSER);
            }
            if (distance >= 4.0D && distance <= 15.0D && front > 0.5D && this.cooldowns[TIDAL] <= 0) {
                ready.add(TIDAL);
            }
            if (!ready.isEmpty()) {
                chosen = ready.get(this.crab.getRandom().nextInt(ready.size()));
            }
        }
        if (chosen == 0) {
            return;
        }
        int cooldown = COOLDOWN[chosen];
        if (enraged()) {
            cooldown = Math.round(cooldown * 0.65F);
        }
        this.cooldowns[chosen] = cooldown;
        this.skillTicks = 0;
        this.hits.clear();
        this.crab.getNavigation().stop();
        this.crab.setBossSkill(chosen);
    }

    // 钳击连剪：第 9 tick 左钳、第 18 tick 右钳向前剪合
    private void tickPincer(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 6 || (t >= 11 && t <= 15)) {
            this.crab.faceDragonTowards(target.getX(), target.getZ(), 12.0F);
        }
        if (t == 9 || t == 18) {
            Vec3 forward = forward();
            boolean landed = false;
            AABB box = this.crab.getBoundingBox().inflate(4.5D, 1.5D, 4.5D);
            for (LivingEntity living : this.crab.level().getEntitiesOfClass(LivingEntity.class, box, this.crab::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(this.crab.position());
                Vec3 flat = new Vec3(offset.x, 0.0D, offset.z);
                double distance = flat.length();
                if (distance > 4.8D || (distance > 1.0D && flat.normalize().dot(forward) < 0.35D)) {
                    continue;
                }
                if (living.hurt(source(), 12.0F)) {
                    landed = true;
                    RebornMobEntity.applyBossKnockback(living, new Vec3(forward.x * 0.35D, 0.15D, forward.z * 0.35D));
                    living.hurtMarked = true;
                }
            }
            ModSounds.play(this.crab.level(), "crab_snap", this.crab.getX(), this.crab.getY() + 1.0D, this.crab.getZ(), 1.5F, 1.0F);
            if (landed) {
                ModSounds.play(this.crab.level(), "crab_snap_hit", this.crab.getX(), this.crab.getY() + 1.0D, this.crab.getZ(), 1.5F, 1.0F);
            }
            byte event = EVENT_PINCER_LEFT;
            if (t == 18) {
                event = EVENT_PINCER_RIGHT;
            }
            this.crab.level().broadcastEntityEvent(this.crab, event);
        }
    }

    // 双钳震地：第 18 tick 砸地，随后两圈盐晶依次破土
    private void tickSlam(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 12) {
            this.crab.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (t == 18) {
            Vec3 center = this.crab.position().add(forward().scale(2.6D));
            AABB box = new AABB(center, center).inflate(4.0D, 2.0D, 4.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.crab::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(center);
                if (offset.x * offset.x + offset.z * offset.z <= 16.0D && living.hurt(source(), 15.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.4D, 0.0D));
                    living.hurtMarked = true;
                }
            }
            ModSounds.play(server, "crab_slam", center.x, center.y, center.z, 3.0F, 1.0F);
            server.broadcastEntityEvent(this.crab, EVENT_SLAM);
            double[] radii = {4.0D, 6.5D};
            if (enraged()) {
                radii = new double[]{4.0D, 6.5D, 9.0D};
            }
            for (int k = 0; k < radii.length; k++) {
                int count = 8 + k * 4;
                double offset = this.crab.getRandom().nextDouble() * Math.PI;
                for (int i = 0; i < count; i++) {
                    double angle = offset + Math.PI * 2.0D * i / count;
                    double x = center.x + Math.cos(angle) * radii[k];
                    double z = center.z + Math.sin(angle) * radii[k];
                    CrabSkillEntity.spawn(server, this.crab, CrabSkillEntity.SALT, new Vec3(x, ground(x, z, center.y), z), Vec3.ZERO, 0.0F, 3 + k * 5, null, i != 0);
                }
            }
        }
    }

    // 横行冲撞：0-10 转成侧对目标，12-30 横向疾冲穿过目标所在位置
    private void tickRush(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 10) {
            // 让目标位于身体右侧：朝向为目标方向逆时针转 90 度
            Vec3 toward = target.position().subtract(this.crab.position());
            Vec3 dir = new Vec3(toward.x, 0.0D, toward.z).normalize();
            this.rushDir = dir;
            this.rushFacing = new Vec3(dir.z, 0.0D, -dir.x);
            this.crab.faceDragonTowards(this.crab.getX() + this.rushFacing.x * 5.0D, this.crab.getZ() + this.rushFacing.z * 5.0D, 18.0F);
        } else {
            this.crab.faceDragonTowards(this.crab.getX() + this.rushFacing.x * 5.0D, this.crab.getZ() + this.rushFacing.z * 5.0D, 30.0F);
        }
        if (t == 4) {
            ModSounds.play(server, "crab_snap", this.crab.getX(), this.crab.getY() + 1.0D, this.crab.getZ(), 1.5F, 0.8F);
        }
        if (t >= RUSH_START && t < RUSH_END) {
            this.crab.move(MoverType.SELF, new Vec3(this.rushDir.x * 0.85D, -0.3D, this.rushDir.z * 0.85D));
            if (t % 3 == 0) {
                ModSounds.play(server, "crab_scuttle", this.crab.getX(), this.crab.getY(), this.crab.getZ(), 1.5F, 1.2F);
            }
            AABB box = this.crab.getBoundingBox().inflate(0.8D, 0.5D, 0.8D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.crab::canDragonSkillHit)) {
                if (!this.hits.add(living.getId())) {
                    continue;
                }
                if (living.hurt(source(), 13.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(this.rushDir.x * 0.7D, 0.225D, this.rushDir.z * 0.7D));
                    living.hurtMarked = true;
                    ModSounds.play(server, "crab_snap_hit", living.getX(), living.getY(), living.getZ(), 1.5F, 0.8F);
                    server.broadcastEntityEvent(this.crab, EVENT_RUSH_HIT);
                }
            }
            if (this.crab.horizontalCollision) {
                this.skillTicks = RUSH_END;
            }
        }
        if (t == RUSH_END) {
            this.crab.setDeltaMovement(Vec3.ZERO);
        }
    }

    // 泡沫弹幕：14-32 tick 每 3 tick 扇形吐出泡泡；狂暴后每 2 tick 一发
    private void tickBubbles(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.crab.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        int interval = 3;
        if (enraged()) {
            interval = 2;
        }
        if (t >= 14 && t <= 32 && (t - 14) % interval == 0) {
            Vec3 forward = forward();
            Vec3 mouth = this.crab.position().add(forward.scale(1.6D)).add(0.0D, 1.8D, 0.0D);
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(mouth).normalize();
            // 扇形散射：左右来回摆动
            double spread = Math.toRadians(Mth.sin((t - 14) * 0.7F) * 30.0F);
            double cos = Math.cos(spread);
            double sin = Math.sin(spread);
            Vec3 dir = new Vec3(aim.x * cos - aim.z * sin, aim.y, aim.x * sin + aim.z * cos).normalize();
            Vec3 velocity = dir.scale(0.38D).add(0.0D, 0.08D, 0.0D);
            CrabSkillEntity.spawn(server, this.crab, CrabSkillEntity.BUBBLE, mouth, velocity, 0.0F, 0, target, false);
            ModSounds.play(server, "crab_spit", mouth.x, mouth.y, mouth.z, 1.0F, 1.0F + this.crab.getRandom().nextFloat() * 0.3F);
        }
    }

    // 盐泉喷涌：双钳四次敲地，每次在目标附近召出一道盐泉，16 tick 后喷发
    private void tickGeyser(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.crab.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        for (int k = 0; k < DRUM_TICKS.length; k++) {
            if (t != DRUM_TICKS[k]) {
                continue;
            }
            server.broadcastEntityEvent(this.crab, EVENT_DRUM);
            ModSounds.play(server, "crab_slam", this.crab.getX(), this.crab.getY(), this.crab.getZ(), 1.2F, 1.3F);
            int count = 1;
            if (enraged()) {
                count = 2;
            }
            RandomSource random = this.crab.getRandom();
            for (int i = 0; i < count; i++) {
                // 第一道落在目标移动方向前方，其余散落在目标周围
                Vec3 lead = target.getDeltaMovement().multiply(12.0D, 0.0D, 12.0D);
                double x = target.getX() + lead.x;
                double z = target.getZ() + lead.z;
                if (k > 0 || i > 0) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double radius = 1.5D + random.nextDouble() * 3.0D;
                    x = target.getX() + Math.cos(angle) * radius;
                    z = target.getZ() + Math.sin(angle) * radius;
                }
                CrabSkillEntity.spawn(server, this.crab, CrabSkillEntity.GEYSER, new Vec3(x, ground(x, z, target.getY()), z), Vec3.ZERO, 0.0F, 16, null, i != 0);
            }
        }
    }

    // 潮汐巨浪：第 24 tick 双钳合砸，向前推出一道浪墙；狂暴后左右再各一道斜浪
    private void tickTidal(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 18) {
            this.crab.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (t == 24) {
            Vec3 forward = forward();
            Vec3 start = this.crab.position().add(forward.scale(2.5D));
            float yaw = this.crab.yBodyRot;
            float[] yaws = {yaw};
            if (enraged()) {
                yaws = new float[]{yaw - 35.0F, yaw, yaw + 35.0F};
            }
            for (float waveYaw : yaws) {
                CrabSkillEntity.spawn(server, this.crab, CrabSkillEntity.WAVE, start, Vec3.ZERO, waveYaw, 0, null, false);
            }
            ModSounds.play(server, "crab_slam", start.x, start.y, start.z, 2.5F, 0.9F);
            ModSounds.play(server, "crab_wave", start.x, start.y, start.z, 3.0F, 0.8F);
            server.broadcastEntityEvent(this.crab, EVENT_TIDAL);
        }
    }

    // 盐潮怒号：第 20 tick 怒吼震退，召唤三只盐蟹
    private void tickEnrage(ServerLevel server) {
        if (this.skillTicks == 4) {
            ModSounds.play(server, "crab_roar", this.crab.getX(), this.crab.getY() + 1.5D, this.crab.getZ(), 4.0F, 1.0F);
        }
        if (this.skillTicks == 20) {
            AABB box = this.crab.getBoundingBox().inflate(7.0D, 3.0D, 7.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.crab::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.crab.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.7D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.25D, push.z));
                living.hurtMarked = true;
            }
            for (int i = 0; i < 3; i++) {
                double angle = Math.toRadians(this.crab.yBodyRot) + Math.PI * 2.0D * i / 3.0D;
                double x = this.crab.getX() + Math.cos(angle) * 3.0D;
                double z = this.crab.getZ() + Math.sin(angle) * 3.0D;
                RebornMobEntity minion = ModEntities.MOBS.get("saltcrab").get().create(server);
                if (minion != null) {
                    minion.moveTo(x, ground(x, z, this.crab.getY()), z, this.crab.getYRot(), 0.0F);
                    server.addFreshEntity(minion);
                }
                CrabSkillEntity.spawn(server, this.crab, CrabSkillEntity.GEYSER, new Vec3(x, ground(x, z, this.crab.getY()), z), Vec3.ZERO, 0.0F, 0, null, true);
            }
            server.broadcastEntityEvent(this.crab, EVENT_ENRAGE);
        }
    }

    static boolean isEvent(byte id) {
        return id >= EVENT_PINCER_LEFT && id <= EVENT_ENRAGE;
    }

    // 客户端每 tick：冲撞预警、拖尾与狂暴水汽
    void clientTick() {
        int skill = this.crab.getCrabSkill();
        if (skill != this.clientSkill) {
            this.clientSkill = skill;
            this.clientTicks = 0;
        }
        this.clientTicks++;
        int t = this.clientTicks;
        Level level = this.crab.level();
        RandomSource random = this.crab.getRandom();
        Vec3 pos = this.crab.position();
        Vec3 forward = forward();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        if (skill == RUSH) {
            if (t >= 8 && t <= RUSH_START && t % 2 == 0) {
                // 侧对目标后：右侧冲锋路线预警
                for (double d = 3.0D; d <= 15.0D; d += 3.0D) {
                    level.addAlwaysVisibleParticle(ModParticles.SALT_WARNING.get(), true, pos.x + right.x * d, pos.y + 0.1D, pos.z + right.z * d, 1.4D, 5.0D, 0.0D);
                }
            }
            if (t > RUSH_START && t <= RUSH_END) {
                // 横冲：身后扬起盐尘与碎盐
                Vec3 back = pos.subtract(right.scale(1.5D));
                level.addParticle(ParticleTypes.CLOUD, back.x + forward.x * 1.2D, pos.y + 0.2D, back.z + forward.z * 1.2D, -right.x * 0.1D, 0.03D, -right.z * 0.1D);
                level.addParticle(ParticleTypes.CLOUD, back.x - forward.x * 1.2D, pos.y + 0.2D, back.z - forward.z * 1.2D, -right.x * 0.1D, 0.03D, -right.z * 0.1D);
                level.addParticle(ModParticles.SALT_SHARD.get(), pos.x, pos.y + 0.4D, pos.z, random.nextGaussian() * 0.1D, 0.25D, random.nextGaussian() * 0.1D);
            }
        }
        if (skill == SLAM && t < 15 && t % 2 == 0) {
            // 举钳蓄力：水汽向双钳汇聚
            for (int side = -1; side <= 1; side += 2) {
                Vec3 claw = pos.add(right.scale(1.6D * side)).add(0.0D, 3.6D, 0.0D);
                Vec3 from = claw.add(random.nextGaussian() * 1.5D, random.nextGaussian() * 1.5D, random.nextGaussian() * 1.5D);
                Vec3 velocity = claw.subtract(from).scale(0.18D);
                level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
            }
        }
        if (skill == TIDAL && t < 22) {
            // 蓄浪：身前地面涌出盐沫与光点
            Vec3 at = pos.add(forward.scale(2.0D + random.nextDouble() * 2.0D)).add(right.scale(random.nextGaussian() * 2.5D));
            level.addParticle(ModParticles.SALT_SPRAY.get(), at.x, pos.y + 0.2D, at.z, 0.0D, 0.15D, 0.0D);
            level.addParticle(ParticleTypes.END_ROD, at.x, pos.y + 0.1D, at.z, 0.0D, 0.1D, 0.0D);
        }
        if (skill == GEYSER && t % 3 == 0) {
            // 王冠上浮旋转：盐粒绕冠
            Vec3 crown = pos.add(0.0D, 3.0D, 0.0D);
            double angle = t * 0.4D;
            level.addParticle(ModParticles.SALT_SHARD.get(), crown.x + Math.cos(angle) * 1.2D, crown.y, crown.z + Math.sin(angle) * 1.2D, 0.0D, 0.05D, 0.0D);
        }
        if (this.crab.isCrabEnraged() && this.crab.tickCount % 3 == 0) {
            // 狂暴：身周持续冒出光点与盐沫
            level.addParticle(ParticleTypes.END_ROD, pos.x + random.nextGaussian() * 1.5D, pos.y + 0.5D + random.nextDouble() * 1.5D, pos.z + random.nextGaussian() * 1.5D, 0.0D, 0.05D, 0.0D);
            level.addParticle(ModParticles.SALT_SPRAY.get(), pos.x + random.nextGaussian() * 1.5D, pos.y + 0.3D, pos.z + random.nextGaussian() * 1.5D, 0.0D, 0.1D, 0.0D);
        }
    }

    // 客户端实体事件：命中瞬间的特效
    void handleEvent(byte id) {
        Level level = this.crab.level();
        RandomSource random = this.crab.getRandom();
        Vec3 pos = this.crab.position();
        Vec3 forward = forward();
        float yaw = this.crab.yBodyRot;
        if (id == EVENT_PINCER_LEFT || id == EVENT_PINCER_RIGHT) {
            // 钳夹：一对盐白钳齿左右合拢 + 盐沫
            double side = 1.0D;
            if (id == EVENT_PINCER_RIGHT) {
                side = -1.0D;
            }
            Vec3 center = pos.add(forward.scale(2.4D)).add(0.0D, 1.2D, 0.0D);
            // 钳夹位置偏向出钳的一侧
            Vec3 claw = center.add(-forward.z * 0.6D * side, 0.0D, forward.x * 0.6D * side);
            level.addAlwaysVisibleParticle(ModParticles.CRAB_PINCH.get(), true, claw.x, claw.y, claw.z, 1.1D, yaw, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.CRAB_PINCH.get(), true, claw.x, claw.y, claw.z, 1.1D, yaw, 1.0D);
            for (int i = 0; i < 10; i++) {
                level.addParticle(ModParticles.SALT_SPRAY.get(), center.x, center.y, center.z, random.nextGaussian() * 0.2D, random.nextDouble() * 0.25D, random.nextGaussian() * 0.2D);
                level.addParticle(ModParticles.SALT_SHARD.get(), center.x, center.y, center.z, random.nextGaussian() * 0.15D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.15D);
            }
            return;
        }
        if (id == EVENT_SLAM) {
            // 震地：双层冲击环 + 震起地面 + 盐沫与盐粒冲天
            Vec3 center = pos.add(forward.scale(2.6D));
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, center.x, center.y + 0.15D, center.z, 7.0D, 14.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, center.x, center.y + 0.2D, center.z, 4.0D, 10.0D, 0.0D);
            DragonSkillEntity.spawnQuakeRing(level, center.x, center.y, center.z, 0.0D, 3.2D, 0.38D, random);
            for (int i = 0; i < 24; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ModParticles.SALT_SPRAY.get(), center.x + Math.cos(angle) * 1.5D, center.y + 0.3D, center.z + Math.sin(angle) * 1.5D, Math.cos(angle) * 0.3D, 0.4D, Math.sin(angle) * 0.3D);
                level.addParticle(ModParticles.SALT_SHARD.get(), center.x, center.y + 0.3D, center.z, Math.cos(angle) * 0.35D, 0.4D + random.nextDouble() * 0.3D, Math.sin(angle) * 0.35D);
            }
            return;
        }
        if (id == EVENT_RUSH_HIT) {
            Vec3 at = pos.add(0.0D, 1.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, at.x, at.y, at.z, 3.0D, 8.0D, 0.0D);
            for (int i = 0; i < 12; i++) {
                level.addParticle(ModParticles.SALT_SHARD.get(), at.x, at.y, at.z, random.nextGaussian() * 0.25D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
            }
            return;
        }
        if (id == EVENT_DRUM) {
            // 敲地：钳下盐沫
            Vec3 at = pos.add(forward.scale(2.4D));
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, at.x, at.y + 0.15D, at.z, 2.5D, 8.0D, 0.0D);
            for (int i = 0; i < 8; i++) {
                level.addParticle(ModParticles.SALT_SPRAY.get(), at.x + random.nextGaussian(), at.y + 0.2D, at.z + random.nextGaussian(), 0.0D, 0.3D, 0.0D);
            }
            return;
        }
        if (id == EVENT_TIDAL) {
            // 合砸：身前盐沫爆开
            Vec3 at = pos.add(forward.scale(2.5D));
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, at.x, at.y + 0.15D, at.z, 5.0D, 10.0D, 0.0D);
            for (int i = 0; i < 30; i++) {
                level.addParticle(ModParticles.SALT_SPRAY.get(), at.x + random.nextGaussian() * 2.0D, at.y + 0.3D, at.z + random.nextGaussian() * 2.0D, forward.x * 0.3D, 0.4D, forward.z * 0.3D);
            }
            return;
        }
        if (id == EVENT_ENRAGE) {
            // 盐潮怒号：三层冲击环 + 盐沫与盐粒爆发
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 9.0D, 16.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, pos.x, pos.y + 1.5D, pos.z, 6.0D, 12.0D, 0.0D);
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ModParticles.SALT_SPRAY.get(), pos.x, pos.y + 1.0D, pos.z, Math.cos(angle) * 0.4D, 0.4D, Math.sin(angle) * 0.4D);
                level.addParticle(ModParticles.SALT_SHARD.get(), pos.x, pos.y + 1.5D, pos.z, Math.cos(angle) * 0.3D, 0.3D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.3D);
            }
        }
    }

    // 战斗目标：有目标时接管移动与技能
    static final class CombatGoal extends Goal {
        private final RebornMobEntity crab;

        CombatGoal(RebornMobEntity crab) {
            this.crab = crab;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.crab.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.crab.getCrabCombat().stop();
            this.crab.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.crab.getCrabCombat().tick(this.crab.getTarget());
        }
    }
}
