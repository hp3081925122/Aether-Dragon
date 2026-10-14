package com.aetherdragon.entity;

import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// 远古潜影贝 boss 战斗：原地炮台型，技能选择、服务端时间轴与客户端特效
final class ShulkerCombat {
    static final int BARRAGE = 1;
    static final int BEAM = 2;
    static final int VINE = 3;
    static final int PHASE = 4;
    static final int FIELD = 5;
    static final int GUARD = 6;
    static final int ENRAGE = 7;
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] DURATION = {0, 60, 130, 50, 70, 50, 60, 50};
    private static final int[] COOLDOWN = {0, 90, 280, 150, 260, 220, 200, 0};
    private static final String[] ANIMATIONS = {"", "skill_barrage", "skill_beam", "skill_vine", "skill_phase", "skill_field", "skill_guard", "enrage"};
    static final byte EVENT_OPEN = (byte) 160;
    static final byte EVENT_VANISH = (byte) 161;
    static final byte EVENT_IMPACT = (byte) 162;
    static final byte EVENT_PULSE = (byte) 163;
    static final byte EVENT_CRASH = (byte) 164;
    static final byte EVENT_BURST = (byte) 165;
    static final byte EVENT_ENRAGE = (byte) 166;
    // 光束：开始与结束 tick、长度、发射点高度
    private static final int BEAM_START = 20;
    private static final int BEAM_END = 120;
    private static final int BEAM_SWEEP_TICKS = BEAM_END - BEAM_START;
    private static final float BEAM_TOTAL_DEGREES = 1080.0F;
    private static final float BEAM_FINAL_SPEED_MULTIPLIER = 2.08F;
    private static final float BEAM_INITIAL_STEP = BEAM_TOTAL_DEGREES
            / (BEAM_SWEEP_TICKS * (1.0F + (BEAM_FINAL_SPEED_MULTIPLIER - 1.0F) * 0.5F));
    private static final double BEAM_RANGE = 18.0D;
    private static final double MOUTH_HEIGHT = 2.5D;
    // 相位跃迁：消失、转移、砸落 tick
    private static final int PHASE_VANISH = 14;
    private static final int PHASE_MOVE = 16;
    private static final int PHASE_IMPACT = 40;
    private static final String ENRAGE_TAG = "reborn_shulker_enraged";

    private final RebornMobEntity shulker;
    private final int[] cooldowns = new int[8];
    private int skillTicks;
    private int gap = 30;
    private int lastHurtTick = -1000;
    private int clientSkill;
    private int clientTicks;
    private int beamCandidates;
    private int beamHits;

    ShulkerCombat(RebornMobEntity shulker) {
        this.shulker = shulker;
    }

    static String getAnimation(int skill) {
        if (skill <= 0 || skill >= ANIMATIONS.length) {
            return "";
        }
        return "animation.elder_shulker." + ANIMATIONS[skill];
    }

    void save(CompoundTag tag) {
        tag.putBoolean(ENRAGE_TAG, this.shulker.isShulkerEnraged());
    }

    void load(CompoundTag tag) {
        this.shulker.setBossEnraged(tag.getBoolean(ENRAGE_TAG));
    }

    void stop() {
        this.skillTicks = 0;
        this.shulker.setBossSkill(0);
        this.shulker.setBossBeam(false);
    }

    private boolean enraged() {
        return this.shulker.isShulkerEnraged();
    }

    // 闭壳期间受到的伤害降为两成
    boolean isGuarding() {
        return this.shulker.getShulkerSkill() == GUARD && this.skillTicks >= 6 && this.skillTicks < 48;
    }

    // 相位跃迁隐身期间无敌
    boolean isPhased() {
        return this.shulker.getShulkerSkill() == PHASE && this.skillTicks >= PHASE_VANISH && this.skillTicks < PHASE_IMPACT;
    }

    void onHurt() {
        this.lastHurtTick = this.shulker.tickCount;
    }

    private Vec3 forward() {
        float yaw = this.shulker.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    // 嘴部位置：头部正前方 1.3 格、离地 2.5 格
    private Vec3 mouth() {
        return this.shulker.position().add(forward().scale(1.3D)).add(0.0D, MOUTH_HEIGHT, 0.0D);
    }

    private double ground(double x, double z, double fromY) {
        return this.shulker.findGroundY(x, fromY + 4.0D, z, 12.0D, fromY);
    }

    // 服务端每 tick：冷却、转向、选技能与执行时间轴
    void tick(LivingEntity target) {
        for (int i = 1; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        int skill = this.shulker.getShulkerSkill();
        if (skill == 0) {
            tickIdle(target);
            return;
        }
        this.skillTicks++;
        if (this.shulker.level() instanceof ServerLevel server) {
            switch (skill) {
                case BARRAGE -> tickBarrage(server, target);
                case BEAM -> tickBeam(server, target);
                case VINE -> tickVine(server, target);
                case PHASE -> tickPhase(server, target);
                case FIELD -> tickField(server);
                case GUARD -> tickGuard(server);
                default -> tickEnrage(server);
            }
        }
        if (this.skillTicks >= DURATION[skill]) {
            stop();
            this.gap = 20;
            if (enraged()) {
                this.gap = 12;
            }
        }
    }

    private void tickIdle(LivingEntity target) {
        // 原地炮台：缓慢转向目标
        this.shulker.faceDragonTowards(target.getX(), target.getZ(), 4.0F);
        this.shulker.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        double distance = this.shulker.distanceTo(target);
        int chosen = 0;
        if (!enraged() && this.shulker.getHealth() <= this.shulker.getMaxHealth() * 0.5F) {
            chosen = ENRAGE;
            this.shulker.setBossEnraged(true);
        } else {
            List<Integer> ready = new ArrayList<>();
            if (distance >= 4.0D && this.cooldowns[BARRAGE] <= 0) {
                ready.add(BARRAGE);
                ready.add(BARRAGE);
            }
            if (distance <= BEAM_RANGE && this.cooldowns[BEAM] <= 0) {
                ready.add(BEAM);
            }
            if (distance >= 4.0D && distance <= 24.0D && this.cooldowns[VINE] <= 0) {
                ready.add(VINE);
            }
            if (distance >= 10.0D && this.cooldowns[PHASE] <= 0) {
                ready.add(PHASE);
                ready.add(PHASE);
            }
            if (distance <= 12.0D && this.cooldowns[FIELD] <= 0) {
                ready.add(FIELD);
            }
            // 近期挨打时倾向闭壳反击
            if (this.cooldowns[GUARD] <= 0 && this.shulker.tickCount - this.lastHurtTick < 40) {
                ready.add(GUARD);
                ready.add(GUARD);
            }
            if (!ready.isEmpty()) {
                chosen = ready.get(this.shulker.getRandom().nextInt(ready.size()));
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
        this.shulker.setBossSkill(chosen);
    }

    // 潜影弹幕：14-38 tick 连续向四周抛出潜影弹，之后自动追踪目标
    private void tickBarrage(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.shulker.faceDragonTowards(target.getX(), target.getZ(), 4.0F);
        if (t == 2) {
            ModSounds.play(server, "shulker_open", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 2.0F, 1.0F);
        }
        int interval = 3;
        if (enraged()) {
            interval = 2;
        }
        if (t >= 14 && t <= 38 && (t - 14) % interval == 0) {
            RandomSource random = this.shulker.getRandom();
            Vec3 from = this.shulker.position().add(0.0D, 2.4D, 0.0D).add(forward().scale(1.0D));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 velocity = new Vec3(Math.cos(angle) * 0.3D, 0.25D + random.nextDouble() * 0.15D, Math.sin(angle) * 0.3D);
            ShulkerSkillEntity.spawn(server, this.shulker, ShulkerSkillEntity.ORB, from, velocity, 0, target, false);
            ModSounds.play(server, "shulker_fire", from.x, from.y, from.z, 1.0F, 0.9F + random.nextFloat() * 0.3F);
        }
    }

    // 回旋光束：0-19 蓄力锁定，20-120 光束斜向扫地并加速旋转三圈，跳起可躲
    private void tickBeam(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t < BEAM_START) {
            this.shulker.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        }
        if (t == 2) {
            ModSounds.play(server, "shulker_beam_charge", this.shulker.getX(), this.shulker.getY() + 2.5D, this.shulker.getZ(), 2.0F, 1.0F);
        }
        if (t == BEAM_START) {
            this.shulker.setBossBeam(true);
            this.beamCandidates = 0;
            this.beamHits = 0;
            ModSounds.play(server, "shulker_beam_fire", this.shulker.getX(), this.shulker.getY() + 2.5D, this.shulker.getZ(), 3.0F, 1.0F);
        }
        if (t >= BEAM_START && t < BEAM_END) {
            int sweepTick = t - BEAM_START;
            float progress = sweepTick / (float) (BEAM_SWEEP_TICKS - 1);
            float step = BEAM_INITIAL_STEP * (1.0F + (BEAM_FINAL_SPEED_MULTIPLIER - 1.0F) * progress);
            float yaw = this.shulker.getYRot() + step;
            this.shulker.setYRot(yaw);
            this.shulker.yBodyRot = yaw;
            this.shulker.yHeadRot = yaw;
            Vec3 start = mouth();
            Vec3 end = start.add(forward().scale(BEAM_RANGE)).subtract(0.0D, MOUTH_HEIGHT, 0.0D);
            BlockHitResult hit = server.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.shulker));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
            }
            this.shulker.setBossSkillPoint(end);
            damageBeam(start, end);
            if (t % 10 == 0) {
                ModSounds.play(server, "shulker_beam_loop", end.x, end.y, end.z, 1.5F, 1.0F);
            }
            if (t == BEAM_END - 1) {
            }
        }
        if (t == BEAM_END) {
            this.shulker.setBossBeam(false);
        }
    }

    // 光束伤害：光束斜向下扫，目标身体与光束所在高度重叠才算命中
    private void damageBeam(Vec3 start, Vec3 end) {
        Vec3 flat = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
        double length = flat.length();
        if (length < 0.5D) {
            return;
        }
        Vec3 dir = flat.scale(1.0D / length);
        AABB box = new AABB(start, end).inflate(1.5D);
        for (LivingEntity living : this.shulker.level().getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
            this.beamCandidates++;
            Vec3 offset = living.getBoundingBox().getCenter().subtract(start);
            double along = offset.x * dir.x + offset.z * dir.z;
            double lateral = Math.abs(offset.x * dir.z - offset.z * dir.x);
            if (along < 0.0D || along > length || lateral > 0.9D + living.getBbWidth() * 0.5D) {
                continue;
            }
            double beamY = Mth.lerp(along / length, start.y, end.y);
            if (beamY < living.getY() - 0.2D || beamY > living.getY() + living.getBbHeight() + 0.2D) {
                continue;
            }
            if (living.hurt(this.shulker.damageSources().indirectMagic(this.shulker, this.shulker), 6.0F)) {
                this.beamHits++;
                living.setSecondsOnFire(2);
            }
        }
    }

    // 缠藤禁锢：第 14 tick 藤蔓从脚下一路冲向目标，最后在目标周围合围
    private void tickVine(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.shulker.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        if (t == 14) {
            Vec3 lead = target.getDeltaMovement().multiply(10.0D, 0.0D, 10.0D);
            Vec3 center = target.position().add(lead);
            Vec3 from = this.shulker.position();
            Vec3 path = new Vec3(center.x - from.x, 0.0D, center.z - from.z);
            double length = path.length();
            Vec3 dir = path.scale(1.0D / Math.max(0.01D, length));
            int steps = 0;
            // 冲向目标的一路藤蔓
            for (double d = 3.0D; d < length - 2.0D; d += 2.0D) {
                double x = from.x + dir.x * d;
                double z = from.z + dir.z * d;
                ShulkerSkillEntity.spawn(server, this.shulker, ShulkerSkillEntity.VINE, new Vec3(x, ground(x, z, from.y), z), Vec3.ZERO, 4 + steps, null, steps % 3 != 0);
                steps++;
            }
            // 目标周围合围：中心 + 一圈；狂暴后多一圈
            int delay = 6 + steps;
            ShulkerSkillEntity.spawn(server, this.shulker, ShulkerSkillEntity.VINE, new Vec3(center.x, ground(center.x, center.z, target.getY()), center.z), Vec3.ZERO, delay, null, false);
            double[] radii = {2.2D};
            if (enraged()) {
                radii = new double[]{2.2D, 4.2D};
            }
            for (double radius : radii) {
                int count = (int) Math.round(radius * 2.5D);
                for (int i = 0; i < count; i++) {
                    double angle = Math.PI * 2.0D * i / count;
                    double x = center.x + Math.cos(angle) * radius;
                    double z = center.z + Math.sin(angle) * radius;
                    ShulkerSkillEntity.spawn(server, this.shulker, ShulkerSkillEntity.VINE, new Vec3(x, ground(x, z, target.getY()), z), Vec3.ZERO, delay, null, true);
                }
            }
            server.broadcastEntityEvent(this.shulker, EVENT_OPEN);
        }
    }

    // 相位跃迁：第 14 tick 消失，第 16 tick 转移到目标位置上空，第 40 tick 砸落
    private void tickPhase(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t == 2) {
            ModSounds.play(server, "shulker_close", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 2.0F, 1.0F);
        }
        if (t == PHASE_VANISH) {
            ModSounds.play(server, "shulker_vanish", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 2.5F, 1.0F);
            server.broadcastEntityEvent(this.shulker, EVENT_VANISH);
        }
        if (t == PHASE_MOVE) {
            // 落点：目标脚下；落点有阻挡时原地砸落
            double x = target.getX();
            double z = target.getZ();
            double y = ground(x, z, target.getY());
            AABB box = this.shulker.getBoundingBox().move(x - this.shulker.getX(), y - this.shulker.getY(), z - this.shulker.getZ());
            if (server.noCollision(this.shulker, box)) {
                this.shulker.teleportTo(x, y, z);
            }
            this.shulker.faceDragonTowards(target.getX() + 0.01D, target.getZ() + 1.0D, 360.0F);
        }
        if (t == PHASE_IMPACT) {
            Vec3 center = this.shulker.position();
            AABB box = new AABB(center, center).inflate(4.5D, 3.0D, 4.5D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(center);
                if (offset.x * offset.x + offset.z * offset.z <= 4.5D * 4.5D && living.hurt(this.shulker.damageSources().mobAttack(this.shulker), 14.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(offset.x * 0.075D, 0.5D, offset.z * 0.075D));
                    living.hurtMarked = true;
                }
            }
            ModSounds.play(server, "shulker_impact", center.x, center.y, center.z, 3.0F, 1.0F);
            server.broadcastEntityEvent(this.shulker, EVENT_IMPACT);
        }
    }

    // 浮空力场：第 18 tick 脉冲让周围目标浮空，第 40 tick 壳片合拢把仍在空中的目标狠狠拍落
    private void tickField(ServerLevel server) {
        int t = this.skillTicks;
        if (t == 2) {
            ModSounds.play(server, "shulker_open", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 2.0F, 0.8F);
        }
        if (t == 18) {
            AABB box = this.shulker.getBoundingBox().inflate(12.0D, 4.0D, 12.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
                if (living.distanceTo(this.shulker) > 12.0F) {
                    continue;
                }
                living.hurt(this.shulker.damageSources().indirectMagic(this.shulker, this.shulker), 4.0F);
                living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1));
            }
            ModSounds.play(server, "shulker_pulse", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 3.0F, 1.0F);
            server.broadcastEntityEvent(this.shulker, EVENT_PULSE);
        }
        if (t == 40) {
            AABB box = this.shulker.getBoundingBox().inflate(16.0D, 12.0D, 16.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
                if (living.onGround()) {
                    continue;
                }
                // 拍落：解除浮空并高速下坠，落地按摔落结算
                living.removeEffect(MobEffects.LEVITATION);
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, -1.1D, 0.0D));
            }
            ModSounds.play(server, "shulker_close", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 3.0F, 0.7F);
            server.broadcastEntityEvent(this.shulker, EVENT_CRASH);
        }
    }

    // 闭壳反击：6-47 tick 闭壳减伤，第 48 tick 壳片爆开向四周射出碎刃
    private void tickGuard(ServerLevel server) {
        int t = this.skillTicks;
        if (t == 2) {
            ModSounds.play(server, "shulker_close", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 2.0F, 1.0F);
        }
        if (t == 48) {
            int ring = 16;
            if (enraged()) {
                ring = 24;
            }
            Vec3 center = this.shulker.position().add(0.0D, 2.0D, 0.0D);
            for (int layer = 0; layer < 2; layer++) {
                // 水平一圈 + 斜向上一圈
                double rise = 0.0D;
                int count = ring;
                double phase = 0.0D;
                if (layer == 1) {
                    rise = 0.45D;
                    count = ring / 2;
                    phase = Math.PI / count;
                }
                for (int i = 0; i < count; i++) {
                    double angle = phase + Math.PI * 2.0D * i / count;
                    Vec3 velocity = new Vec3(Math.cos(angle) * 0.9D, rise, Math.sin(angle) * 0.9D);
                    ShulkerSkillEntity.spawn(server, this.shulker, ShulkerSkillEntity.SHARD, center.add(velocity.normalize().scale(2.0D)), velocity, 0, null, true);
                }
            }
            AABB box = this.shulker.getBoundingBox().inflate(5.0D, 2.0D, 5.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.shulker.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.65D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.2D, push.z));
            }
            ModSounds.play(server, "shulker_burst", center.x, center.y, center.z, 3.0F, 1.0F);
            server.broadcastEntityEvent(this.shulker, EVENT_BURST);
        }
    }

    // 狂暴：第 12 tick 怒吼，震退近身目标
    private void tickEnrage(ServerLevel server) {
        if (this.skillTicks == 4) {
            ModSounds.play(server, "shulker_enrage", this.shulker.getX(), this.shulker.getY() + 2.0D, this.shulker.getZ(), 4.0F, 1.0F);
        }
        if (this.skillTicks == 12) {
            AABB box = this.shulker.getBoundingBox().inflate(7.0D, 3.0D, 7.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.shulker::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.shulker.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.7D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.25D, push.z));
            }
            server.broadcastEntityEvent(this.shulker, EVENT_ENRAGE);
        }
    }

    static boolean isEvent(byte id) {
        return id >= EVENT_OPEN && id <= EVENT_ENRAGE;
    }

    // 客户端每 tick：蓄力、跃迁预警、光束落点与狂暴光环
    void clientTick() {
        int skill = this.shulker.getShulkerSkill();
        if (skill != this.clientSkill) {
            this.clientSkill = skill;
            this.clientTicks = 0;
        }
        this.clientTicks++;
        int t = this.clientTicks;
        Level level = this.shulker.level();
        RandomSource random = this.shulker.getRandom();
        Vec3 pos = this.shulker.position();
        if (skill == BEAM && t < BEAM_START) {
            // 光束蓄力：金光向嘴部汇聚
            Vec3 mouth = mouth();
            for (int i = 0; i < 3; i++) {
                Vec3 from = mouth.add(random.nextGaussian() * 2.0D, random.nextGaussian() * 2.0D, random.nextGaussian() * 2.0D);
                Vec3 velocity = mouth.subtract(from).scale(0.18D);
                level.addParticle(ModParticles.SHULKER_SPARK.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
            }
        }
        if (skill == BEAM && this.shulker.isShulkerBeamActive()) {
            // 光束落点：犁过地面的金色火花与灼痕
            Vec3 end = this.shulker.getBossSkillPoint();
            for (int i = 0; i < 2; i++) {
                level.addParticle(ModParticles.SHULKER_SPARK.get(), end.x, end.y + 0.2D, end.z, random.nextGaussian() * 0.2D, 0.3D + random.nextDouble() * 0.2D, random.nextGaussian() * 0.2D);
            }
            level.addParticle(ParticleTypes.SMALL_FLAME, end.x, end.y + 0.1D, end.z, 0.0D, 0.02D, 0.0D);
            if (t % 4 == 0) {
                level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, end.x, end.y + 0.1D, end.z, 2.0D, 8.0D, 0.0D);
            }
        }
        if (skill == PHASE && t == PHASE_MOVE + 4) {
            // 跃迁落点：收缩预警环，收到最小时砸落
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_WARNING.get(), true, pos.x, pos.y + 0.1D, pos.z, 4.5D, PHASE_IMPACT - PHASE_MOVE - 4, 0.0D);
        }
        if (skill == PHASE && t > PHASE_MOVE && t < PHASE_IMPACT && t % 2 == 0) {
            // 落点上空汇聚的传送粒子
            level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.x + random.nextGaussian() * 1.5D, pos.y + 6.0D + random.nextDouble() * 2.0D, pos.z + random.nextGaussian() * 1.5D, 0.0D, -0.2D, 0.0D);
        }
        if (skill == FIELD && t > 18 && t < 40 && t % 2 == 0) {
            // 力场持续：地面向上飘升的光点
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = random.nextDouble() * 12.0D;
            level.addParticle(ParticleTypes.END_ROD, pos.x + Math.cos(angle) * radius, pos.y + 0.2D, pos.z + Math.sin(angle) * radius, 0.0D, 0.12D, 0.0D);
        }
        if (skill == GUARD && t > 30 && t < 48) {
            // 爆壳前：壳缝溢出金光
            level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x + random.nextGaussian() * 1.6D, pos.y + 1.0D + random.nextDouble() * 3.0D, pos.z + random.nextGaussian() * 1.6D, 0.0D, 0.05D, 0.0D);
        }
        if (this.shulker.isShulkerEnraged() && this.shulker.tickCount % 2 == 0) {
            // 狂暴：金色碎光环绕
            double angle = this.shulker.tickCount * 0.12D;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x + Math.cos(a) * 3.2D, pos.y + 0.5D + random.nextDouble() * 3.5D, pos.z + Math.sin(a) * 3.2D, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    // 客户端实体事件
    void handleEvent(byte id) {
        Level level = this.shulker.level();
        RandomSource random = this.shulker.getRandom();
        Vec3 pos = this.shulker.position();
        if (id == EVENT_OPEN) {
            // 召唤缠藤：壳缝喷出草叶
            for (int i = 0; i < 16; i++) {
                level.addParticle(ParticleTypes.COMPOSTER, pos.x + random.nextGaussian() * 2.0D, pos.y + 1.0D + random.nextDouble() * 3.0D, pos.z + random.nextGaussian() * 2.0D, 0.0D, 0.05D, 0.0D);
            }
            return;
        }
        if (id == EVENT_VANISH) {
            // 消失：传送粒子向内收缩
            for (int i = 0; i < 40; i++) {
                level.addParticle(ParticleTypes.REVERSE_PORTAL, pos.x + random.nextGaussian() * 2.0D, pos.y + 2.0D + random.nextGaussian() * 1.5D, pos.z + random.nextGaussian() * 2.0D, 0.0D, 0.0D, 0.0D);
            }
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 4.0D, 10.0D, 0.0D);
            return;
        }
        if (id == EVENT_IMPACT) {
            // 砸落：双层冲击环 + 大范围震起地面 + 金色碎光冲天
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 7.0D, 14.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.2D, pos.z, 4.0D, 10.0D, 0.0D);
            DragonSkillEntity.spawnQuakeRing(level, pos.x, pos.y, pos.z, 1.5D, 4.5D, 0.4D, random);
            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x + Math.cos(angle) * 2.5D, pos.y + 0.3D, pos.z + Math.sin(angle) * 2.5D, Math.cos(angle) * 0.35D, 0.4D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.35D);
            }
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0D, pos.z, 0.0D, 0.0D, 0.0D);
            return;
        }
        if (id == EVENT_PULSE) {
            // 浮空脉冲：三层环从脚下扩散到 12 格
            for (int i = 0; i < 3; i++) {
                level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.15D + i * 1.5D, pos.z, 12.0D - i * 2.0D, 16.0D, 0.0D);
            }
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 2.0D, pos.z, Math.cos(angle) * 0.6D, 0.1D, Math.sin(angle) * 0.6D);
            }
            return;
        }
        if (id == EVENT_CRASH) {
            // 拍落：向下压的冲击环
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 14.0D, 12.0D, 0.0D);
            for (int i = 0; i < 24; i++) {
                level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x + random.nextGaussian() * 5.0D, pos.y + 5.0D, pos.z + random.nextGaussian() * 5.0D, 0.0D, -0.6D, 0.0D);
            }
            return;
        }
        if (id == EVENT_BURST) {
            // 爆壳：中心冲击环 + 金光迸射
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 2.0D, pos.z, 6.0D, 10.0D, 0.0D);
            for (int i = 0; i < 30; i++) {
                level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x, pos.y + 2.0D, pos.z, random.nextGaussian() * 0.4D, random.nextGaussian() * 0.3D, random.nextGaussian() * 0.4D);
            }
            level.addParticle(ParticleTypes.FLASH, pos.x, pos.y + 2.0D, pos.z, 0.0D, 0.0D, 0.0D);
            return;
        }
        if (id == EVENT_ENRAGE) {
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 9.0D, 16.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, pos.x, pos.y + 2.5D, pos.z, 6.0D, 12.0D, 0.0D);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ModParticles.SHULKER_SPARK.get(), pos.x, pos.y + 2.5D, pos.z, random.nextGaussian() * 0.35D, 0.3D + random.nextDouble() * 0.4D, random.nextGaussian() * 0.35D);
            }
        }
    }

    // 战斗目标：有目标时接管转向与技能
    static final class CombatGoal extends Goal {
        private final RebornMobEntity shulker;

        CombatGoal(RebornMobEntity shulker) {
            this.shulker = shulker;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.shulker.getTarget();
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
            this.shulker.getShulkerCombat().stop();
        }

        @Override
        public void tick() {
            this.shulker.getShulkerCombat().tick(this.shulker.getTarget());
        }
    }
}
