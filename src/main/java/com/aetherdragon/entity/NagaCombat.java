package com.aetherdragon.entity;

import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// 天境娜迦 boss 战斗：技能选择、服务端时间轴与客户端特效
final class NagaCombat {
    static final int BITE = 1;
    static final int TAIL_SWEEP = 2;
    static final int BURROW = 3;
    static final int BEAM = 4;
    static final int PILLARS = 5;
    static final int BOULDER = 6;
    static final int ENRAGE = 7;
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] DURATION = {0, 30, 40, 80, 70, 50, 44, 40};
    private static final int[] COOLDOWN = {0, 50, 90, 320, 260, 180, 160, 0};
    private static final String[] ANIMATIONS = {"", "skill_bite", "skill_tail_sweep", "skill_burrow", "skill_beam", "skill_pillars", "skill_boulder", "enrage"};
    static final byte EVENT_BITE = (byte) 130;
    static final byte EVENT_SWEEP = (byte) 131;
    static final byte EVENT_DIVE = (byte) 132;
    static final byte EVENT_ERUPT = (byte) 133;
    static final byte EVENT_RESURFACE = (byte) 134;
    static final byte EVENT_SLAM = (byte) 135;
    static final byte EVENT_THROW = (byte) 136;
    static final byte EVENT_ENRAGE = (byte) 137;
    // 潜地：入地、追踪、锁定、破土、浮出的 tick
    private static final int BURROW_HIDE = 15;
    private static final int BURROW_LOCK = 36;
    private static final int BURROW_ERUPT = 46;
    private static final int BURROW_RESURFACE = 67;
    // 光束：开始与结束 tick、最大长度
    private static final int BEAM_START = 20;
    private static final int BEAM_END = 60;
    private static final double BEAM_RANGE = 24.0D;
    private static final String ENRAGE_TAG = "reborn_naga_enraged";

    private final RebornMobEntity naga;
    private final int[] cooldowns = new int[8];
    private int skillTicks;
    private int gap = 30;
    private boolean enraged;
    private Vec3 burrowTarget = Vec3.ZERO;
    // 客户端计时
    private int clientSkill;
    private int clientTicks;

    NagaCombat(RebornMobEntity naga) {
        this.naga = naga;
    }

    static String getAnimation(int skill) {
        if (skill <= 0 || skill >= ANIMATIONS.length) {
            return "";
        }
        return "animation.slider." + ANIMATIONS[skill];
    }

    int getSkillTicks() {
        return this.skillTicks;
    }

    void save(CompoundTag tag) {
        tag.putBoolean(ENRAGE_TAG, this.enraged);
    }

    void load(CompoundTag tag) {
        this.enraged = tag.getBoolean(ENRAGE_TAG);
    }

    void stop() {
        this.skillTicks = 0;
        this.naga.setBossSkill(0);
        this.naga.setBossBeam(false);
    }

    // 潜地期间（入地后到破土前）无敌
    boolean isBurrowed() {
        return this.naga.getNagaSkill() == BURROW && this.skillTicks >= 10 && this.skillTicks < BURROW_ERUPT;
    }

    private Vec3 forward() {
        float yaw = this.naga.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    private DamageSource source() {
        return this.naga.damageSources().mobAttack(this.naga);
    }

    // 嘴部位置：光束姿态下前 0.75 格、上 2.25 格（BB 中实测）
    private Vec3 mouth() {
        return this.naga.position().add(forward().scale(0.75D)).add(0.0D, 2.25D, 0.0D);
    }

    // 服务端每 tick：冷却、移动、选技能与执行时间轴
    void tick(LivingEntity target) {
        for (int i = 1; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        int skill = this.naga.getNagaSkill();
        if (skill == 0) {
            tickIdle(target);
            return;
        }
        this.naga.getNavigation().stop();
        this.skillTicks++;
        if (this.naga.level() instanceof ServerLevel server) {
            switch (skill) {
                case BITE -> tickBite(target);
                case TAIL_SWEEP -> tickSweep();
                case BURROW -> tickBurrow(target);
                case BEAM -> tickBeam(target);
                case PILLARS -> tickPillars(server, target);
                case BOULDER -> tickBoulder(server, target);
                default -> tickEnrage(server);
            }
        }
        if (this.skillTicks >= DURATION[skill]) {
            stop();
            this.gap = 18;
            if (this.enraged) {
                this.gap = 10;
            }
        }
    }

    private void tickIdle(LivingEntity target) {
        double distance = this.naga.distanceTo(target);
        this.naga.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 追近目标
        if (distance > 5.0D) {
            if (this.naga.tickCount % 8 == 0 || this.naga.getNavigation().isDone()) {
                this.naga.getNavigation().moveTo(target, 1.0D);
            }
        } else {
            this.naga.getNavigation().stop();
            this.naga.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        }
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        Vec3 toward = target.position().subtract(this.naga.position());
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        double front = 1.0D;
        if (flat.lengthSqr() > 1.0E-4D) {
            front = flat.normalize().dot(forward());
        }
        int chosen = 0;
        if (!this.enraged && this.naga.getHealth() <= this.naga.getMaxHealth() * 0.5F) {
            // 半血狂暴：此后冷却缩短，石柱变为三列
            chosen = ENRAGE;
            this.enraged = true;
        } else if (distance <= 11.0D && front < -0.1D && this.cooldowns[TAIL_SWEEP] <= 0) {
            // 目标绕到身侧或身后：优先甩尾
            chosen = TAIL_SWEEP;
        } else {
            List<Integer> ready = new ArrayList<>();
            if (distance <= 6.5D && front > 0.4D && this.cooldowns[BITE] <= 0) {
                ready.add(BITE);
                ready.add(BITE);
                ready.add(BITE);
            }
            if (distance <= 11.0D && front < 0.3D && this.cooldowns[TAIL_SWEEP] <= 0) {
                ready.add(TAIL_SWEEP);
            }
            if (distance >= 5.0D && distance <= 22.0D && this.cooldowns[PILLARS] <= 0) {
                ready.add(PILLARS);
            }
            if (distance >= 6.0D && distance <= BEAM_RANGE && this.cooldowns[BEAM] <= 0) {
                ready.add(BEAM);
            }
            if (distance >= 8.0D && this.cooldowns[BOULDER] <= 0) {
                ready.add(BOULDER);
            }
            if (distance >= 9.0D && this.cooldowns[BURROW] <= 0) {
                ready.add(BURROW);
            }
            if (!ready.isEmpty()) {
                chosen = ready.get(this.naga.getRandom().nextInt(ready.size()));
            }
        }
        if (chosen == 0) {
            return;
        }
        int cooldown = COOLDOWN[chosen];
        if (this.enraged) {
            cooldown = Math.round(cooldown * 0.65F);
        }
        this.cooldowns[chosen] = cooldown;
        this.skillTicks = 0;
        this.naga.getNavigation().stop();
        this.naga.setBossSkill(chosen);
    }

    // 噬咬猛扑：0-9 后仰锁定，10-13 前冲 4 格，第 13 tick 咬合
    private void tickBite(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 9) {
            this.naga.faceDragonTowards(target.getX(), target.getZ(), 12.0F);
        }
        if (t == 2) {
            ModSounds.play(this.naga.level(), "naga_hiss", this.naga.getX(), this.naga.getY() + 1.0D, this.naga.getZ(), 1.5F, 1.0F);
        }
        if (t >= 10 && t <= 13) {
            Vec3 forward = forward();
            this.naga.move(MoverType.SELF, new Vec3(forward.x, -0.2D, forward.z));
        }
        if (t == 13) {
            Vec3 center = this.naga.position().add(forward().scale(1.6D)).add(0.0D, 0.8D, 0.0D);
            boolean landed = false;
            AABB box = new AABB(center, center).inflate(2.8D, 2.0D, 2.8D);
            for (LivingEntity living : this.naga.level().getEntitiesOfClass(LivingEntity.class, box, this.naga::canDragonSkillHit)) {
                if (living.position().distanceToSqr(center) > 3.2D * 3.2D) {
                    continue;
                }
                if (living.hurt(source(), 12.0F)) {
                    landed = true;
                    Vec3 push = forward().scale(0.4D);
                    RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.15D, push.z));
                }
            }
            ModSounds.play(this.naga.level(), "naga_bite", center.x, center.y, center.z, 2.0F, 1.0F);
            if (landed) {
                ModSounds.play(this.naga.level(), "naga_bite_hit", center.x, center.y, center.z, 1.5F, 1.0F);
            }
            this.naga.level().broadcastEntityEvent(this.naga, EVENT_BITE);
        }
    }

    // 巨尾横扫：0-15 尾巴蓄向右侧，第 18 tick 扫过身侧与身后
    private void tickSweep() {
        int t = this.skillTicks;
        if (t == 2) {
            ModSounds.play(this.naga.level(), "naga_hiss", this.naga.getX(), this.naga.getY() + 1.0D, this.naga.getZ(), 1.5F, 0.8F);
        }
        if (t == 18) {
            Vec3 forward = forward();
            Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);
            Vec3 body = this.naga.position().subtract(forward.scale(3.5D));
            AABB box = new AABB(body, body).inflate(8.0D, 2.5D, 8.0D);
            for (LivingEntity living : this.naga.level().getEntitiesOfClass(LivingEntity.class, box, this.naga::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(body);
                double distance = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
                Vec3 fromHead = living.position().subtract(this.naga.position());
                Vec3 flat = new Vec3(fromHead.x, 0.0D, fromHead.z);
                // 正前方由噬咬负责，尾扫只打身侧与身后
                if (distance > 8.0D || (flat.lengthSqr() > 1.0E-4D && flat.normalize().dot(forward) > 0.5D)) {
                    continue;
                }
                if (living.hurt(source(), 12.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(left.x * 0.65D, 0.225D, left.z * 0.65D));
                }
            }
            ModSounds.play(this.naga.level(), "naga_tail_sweep", body.x, body.y + 1.0D, body.z, 2.5F, 1.0F);
            this.naga.level().broadcastEntityEvent(this.naga, EVENT_SWEEP);
        }
    }

    // 潜地突袭：入地后沿地表追踪目标，第 36 tick 锁定落点，第 46 tick 破土挑飞，第 67 tick 平着浮出
    private void tickBurrow(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 8) {
            this.naga.faceDragonTowards(target.getX(), target.getZ(), 15.0F);
        }
        if (t == 8) {
            ModSounds.play(this.naga.level(), "naga_burrow", this.naga.getX(), this.naga.getY(), this.naga.getZ(), 2.5F, 1.0F);
            this.naga.level().broadcastEntityEvent(this.naga, EVENT_DIVE);
        }
        if (t >= BURROW_HIDE && t < BURROW_ERUPT) {
            if (t <= BURROW_LOCK) {
                this.burrowTarget = target.position();
            }
            // 地下潜行：直接贴着地表移动，模型此时完全在地下
            Vec3 want = this.burrowTarget.subtract(this.naga.position());
            Vec3 flat = new Vec3(want.x, 0.0D, want.z);
            double step = Math.min(0.9D, flat.length());
            if (step > 0.01D) {
                Vec3 move = flat.normalize().scale(step);
                double nx = this.naga.getX() + move.x;
                double nz = this.naga.getZ() + move.z;
                double ny = this.naga.findGroundY(nx, this.naga.getY() + 3.0D, nz, 8.0D, this.naga.getY());
                this.naga.setPos(nx, ny, nz);
                this.naga.faceDragonTowards(this.burrowTarget.x, this.burrowTarget.z, 30.0F);
            }
            this.naga.setDeltaMovement(Vec3.ZERO);
            if (t % 6 == 0) {
                ModSounds.play(this.naga.level(), "naga_rumble", this.naga.getX(), this.naga.getY(), this.naga.getZ(), 1.5F, 1.0F);
            }
        }
        if (t == BURROW_ERUPT) {
            // 破土：脚下范围大幅挑飞
            Vec3 center = this.naga.position();
            AABB box = new AABB(center, center).inflate(4.5D, 3.0D, 4.5D);
            for (LivingEntity living : this.naga.level().getEntitiesOfClass(LivingEntity.class, box, this.naga::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(center);
                if (offset.x * offset.x + offset.z * offset.z > 4.5D * 4.5D) {
                    continue;
                }
                if (living.hurt(source(), 15.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(offset.x * 0.06D, 0.325D, offset.z * 0.06D));
                }
            }
            ModSounds.play(this.naga.level(), "naga_erupt", center.x, center.y, center.z, 3.0F, 1.0F);
            ModSounds.play(this.naga.level(), "naga_hiss", center.x, center.y + 3.0D, center.z, 2.0F, 0.7F);
            this.naga.level().broadcastEntityEvent(this.naga, EVENT_ERUPT);
        }
        if (t == BURROW_RESURFACE) {
            ModSounds.play(this.naga.level(), "naga_burrow", this.naga.getX(), this.naga.getY(), this.naga.getZ(), 2.0F, 1.2F);
            this.naga.level().broadcastEntityEvent(this.naga, EVENT_RESURFACE);
        }
    }

    // 秘符光束：0-19 蓄力，20-60 光束缓慢追向目标，命中处每 4 tick 结算一次
    private void tickBeam(LivingEntity target) {
        int t = this.skillTicks;
        float turn = 2.2F;
        if (this.enraged) {
            turn = 3.2F;
        }
        if (t < BEAM_START) {
            this.naga.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        } else {
            this.naga.faceDragonTowards(target.getX(), target.getZ(), turn);
        }
        if (t == 2) {
            ModSounds.play(this.naga.level(), "naga_beam_charge", this.naga.getX(), this.naga.getY() + 2.0D, this.naga.getZ(), 2.0F, 1.0F);
        }
        if (t == BEAM_START) {
            this.naga.setBossBeam(true);
            ModSounds.play(this.naga.level(), "naga_beam_fire", this.naga.getX(), this.naga.getY() + 2.0D, this.naga.getZ(), 3.0F, 1.0F);
        }
        if (t >= BEAM_START && t < BEAM_END) {
            // 水平方向跟随身体朝向，俯仰直接对准目标胸口
            Vec3 start = mouth();
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            double horizontal = Math.max(0.5D, new Vec3(aim.x - start.x, 0.0D, aim.z - start.z).length());
            double pitch = Math.atan2(aim.y - start.y, horizontal);
            Vec3 forward = forward();
            Vec3 dir = new Vec3(forward.x * Math.cos(pitch), Math.sin(pitch), forward.z * Math.cos(pitch));
            Vec3 end = start.add(dir.scale(BEAM_RANGE));
            BlockHitResult hit = this.naga.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.naga));
            if (hit.getType() != HitResult.Type.MISS) {
                end = hit.getLocation();
            }
            this.naga.setBossSkillPoint(end);
            if (t % 4 == 0) {
                damageBeam(start, end);
            }
            if (t % 10 == 0) {
                ModSounds.play(this.naga.level(), "naga_beam_loop", end.x, end.y, end.z, 1.5F, 1.0F);
            }
        }
        if (t == BEAM_END) {
            this.naga.setBossBeam(false);
        }
    }

    // 光束伤害：到线段距离 1.2 格以内
    private void damageBeam(Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = Math.max(1.0E-4D, segment.lengthSqr());
        AABB box = new AABB(start, end).inflate(1.5D);
        for (LivingEntity living : this.naga.level().getEntitiesOfClass(LivingEntity.class, box, this.naga::canDragonSkillHit)) {
            Vec3 center = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D);
            double along = Mth.clamp(center.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
            Vec3 closest = start.add(segment.scale(along));
            if (closest.distanceToSqr(center) <= 1.2D * 1.2D + living.getBbWidth() * living.getBbWidth()) {
                living.hurt(this.naga.damageSources().indirectMagic(this.naga, this.naga), 4.0F);
            }
        }
    }

    // 迷宫石柱：第 17 tick 头砸地面，石柱成列依次涌向目标；狂暴后变为三列扇形
    private void tickPillars(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 13) {
            this.naga.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        }
        if (t == 2) {
            ModSounds.play(server, "naga_hiss", this.naga.getX(), this.naga.getY() + 1.5D, this.naga.getZ(), 1.5F, 1.1F);
        }
        if (t == 17) {
            Vec3 forward = forward();
            Vec3 head = this.naga.position().add(forward.scale(1.0D));
            ModSounds.play(server, "naga_stone_slam", head.x, head.y, head.z, 3.0F, 1.0F);
            server.broadcastEntityEvent(this.naga, EVENT_SLAM);
            double baseYaw = Math.atan2(target.getZ() - head.z, target.getX() - head.x);
            double[] spreads = {0.0D};
            if (this.enraged) {
                spreads = new double[]{-0.4D, 0.0D, 0.4D};
            }
            for (double spread : spreads) {
                double yaw = baseYaw + spread;
                Vec3 dir = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
                for (int k = 1; k <= 12; k++) {
                    Vec3 at = head.add(dir.scale(1.5D + k * 1.6D));
                    double groundY = this.naga.findGroundY(at.x, head.y + 4.0D, at.z, 10.0D, head.y);
                    NagaSkillEntity.spawnPillar(server, this.naga, new Vec3(at.x, groundY, at.z), 3 + k * 2);
                }
            }
        }
    }

    // 甩尾掷岩：第 17 tick 尾尖甩出三块巨石，落点在目标及其移动方向附近
    private void tickBoulder(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.naga.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        if (t == 17) {
            Vec3 forward = forward();
            Vec3 tail = this.naga.position().subtract(forward.scale(6.9D)).add(0.0D, 3.0D, 0.0D);
            RandomSource random = this.naga.getRandom();
            Vec3 lead = target.getDeltaMovement().multiply(10.0D, 0.0D, 10.0D);
            for (int i = 0; i < 3; i++) {
                double x = target.getX() + lead.x;
                double z = target.getZ() + lead.z;
                if (i > 0) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    x += Math.cos(angle) * (2.5D + random.nextDouble() * 2.0D);
                    z += Math.sin(angle) * (2.5D + random.nextDouble() * 2.0D);
                }
                double groundY = this.naga.findGroundY(x, target.getY() + 6.0D, z, 16.0D, target.getY());
                NagaSkillEntity.spawnBoulder(server, this.naga, tail, new Vec3(x, groundY, z), 22 + i * 3);
            }
            ModSounds.play(server, "naga_boulder_throw", tail.x, tail.y, tail.z, 2.5F, 1.0F);
            server.broadcastEntityEvent(this.naga, EVENT_THROW);
        }
    }

    // 狂暴：第 10 tick 怒吼震退，周身升起一圈石柱
    private void tickEnrage(ServerLevel server) {
        if (this.skillTicks == 10) {
            AABB box = this.naga.getBoundingBox().inflate(7.0D, 3.0D, 7.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.naga::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.naga.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.7D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.25D, push.z));
            }
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2.0D * i / 10.0D;
                double x = this.naga.getX() + Math.cos(angle) * 5.0D;
                double z = this.naga.getZ() + Math.sin(angle) * 5.0D;
                double groundY = this.naga.findGroundY(x, this.naga.getY() + 4.0D, z, 10.0D, this.naga.getY());
                NagaSkillEntity.spawnPillar(server, this.naga, new Vec3(x, groundY, z), 4);
            }
            ModSounds.play(server, "naga_enrage", this.naga.getX(), this.naga.getY() + 2.0D, this.naga.getZ(), 4.0F, 1.0F);
            server.broadcastEntityEvent(this.naga, EVENT_ENRAGE);
        }
    }

    static boolean isEvent(byte id) {
        return id >= EVENT_BITE && id <= EVENT_ENRAGE;
    }

    // 客户端每 tick：技能计时与持续特效
    void clientTick() {
        int skill = this.naga.getNagaSkill();
        if (skill != this.clientSkill) {
            this.clientSkill = skill;
            this.clientTicks = 0;
        }
        this.clientTicks++;
        int t = this.clientTicks;
        Level level = this.naga.level();
        RandomSource random = this.naga.getRandom();
        Vec3 pos = this.naga.position();
        Vec3 forward = forward();
        switch (skill) {
            case BURROW -> {
                if (t >= BURROW_HIDE && t < BURROW_ERUPT) {
                    // 地下潜行：地面鼓包翻起，碎石与尘土
                    if (t % 2 == 0) {
                        DragonSkillEntity.spawnQuakeRing(level, pos.x, pos.y, pos.z, 0.0D, 1.6D, 0.22D, random);
                    }
                    BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(this.naga.blockPosition().below()));
                    for (int i = 0; i < 4; i++) {
                        level.addParticle(dirt, pos.x + random.nextGaussian() * 0.8D, pos.y + 0.2D, pos.z + random.nextGaussian() * 0.8D, 0.0D, 0.25D, 0.0D);
                    }
                }
                if (t == BURROW_LOCK) {
                    // 锁定落点：收缩预警环
                    level.addAlwaysVisibleParticle(ModParticles.NAGA_WARNING.get(), true, pos.x, pos.y + 0.1D, pos.z, 4.5D, BURROW_ERUPT - BURROW_LOCK, 0.0D);
                }
            }
            case BEAM -> {
                if (t < BEAM_START) {
                    // 蓄力：秘符能量向嘴部汇聚
                    Vec3 mouth = mouth();
                    for (int i = 0; i < 3; i++) {
                        Vec3 from = mouth.add(random.nextGaussian() * 2.0D, random.nextGaussian() * 2.0D, random.nextGaussian() * 2.0D);
                        Vec3 velocity = mouth.subtract(from).scale(0.18D);
                        level.addParticle(ParticleTypes.WITCH, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
                    }
                } else if (this.naga.isNagaBeamActive()) {
                    // 光束落点：碎石迸溅、秘符小冲击环
                    Vec3 end = this.naga.getBossSkillPoint();
                    BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
                    for (int i = 0; i < 2; i++) {
                        level.addParticle(stone, end.x, end.y + 0.2D, end.z, random.nextGaussian() * 0.2D, 0.3D + random.nextDouble() * 0.2D, random.nextGaussian() * 0.2D);
                    }
                    level.addParticle(ParticleTypes.WITCH, end.x, end.y + 0.3D, end.z, 0.0D, 0.1D, 0.0D);
                    if (t % 5 == 0) {
                        level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, end.x, end.y + 0.1D, end.z, 2.5D, 8.0D, 0.0D);
                    }
                }
            }
            case PILLARS -> {
                if (t < 14 && t % 2 == 0) {
                    level.addParticle(ParticleTypes.PORTAL, pos.x + forward.x + random.nextGaussian() * 0.5D, pos.y + 2.5D, pos.z + forward.z + random.nextGaussian() * 0.5D, 0.0D, -0.3D, 0.0D);
                }
            }
            default -> {
            }
        }
    }

    // 客户端实体事件：命中瞬间的特效
    void handleEvent(byte id) {
        Level level = this.naga.level();
        RandomSource random = this.naga.getRandom();
        Vec3 pos = this.naga.position();
        Vec3 forward = forward();
        float yaw = this.naga.yBodyRot;
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        if (id == EVENT_BITE) {
            // 噬咬：上下两排紫色獠牙猛然合拢
            Vec3 center = pos.add(forward.scale(2.2D)).add(0.0D, 0.8D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_BITE.get(), true, center.x, center.y, center.z, 1.4D, yaw, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_BITE.get(), true, center.x, center.y, center.z, 1.4D, yaw, 1.0D);
            for (int i = 0; i < 10; i++) {
                level.addParticle(ParticleTypes.WITCH, center.x, center.y, center.z, random.nextGaussian() * 0.2D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.2D);
            }
            return;
        }
        if (id == EVENT_SWEEP) {
            // 尾扫：身后大弧刀光 + 弧线上的尘土与震起方块
            Vec3 body = pos.subtract(forward.scale(3.5D));
            level.addAlwaysVisibleParticle(ModParticles.NAGA_SLASH.get(), true, body.x, body.y + 0.8D, body.z, 7.0D, yaw + 180.0F, 1.0D);
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            Vec3 previous = null;
            for (int i = 0; i <= 10; i++) {
                double angle = Math.toRadians(i * 18.0D);
                Vec3 dir = right.scale(Math.cos(angle)).subtract(forward.scale(Math.sin(angle)));
                Vec3 at = body.add(dir.scale(6.0D));
                if (previous != null) {
                    DragonSkillEntity.spawnQuakeLine(level, previous.x, pos.y, previous.z, at.x, at.z, 0.8D, 0.25D, random);
                }
                level.addParticle(ParticleTypes.CLOUD, at.x, pos.y + 0.3D, at.z, -right.x * 0.2D, 0.05D, -right.z * 0.2D);
                previous = at;
            }
            return;
        }
        if (id == EVENT_DIVE) {
            // 入地：头部扎入处地面炸开
            Vec3 head = pos.add(forward.scale(1.0D));
            DragonSkillEntity.spawnQuakeRing(level, head.x, pos.y, head.z, 0.0D, 2.8D, 0.35D, random);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, head.x, pos.y + 0.12D, head.z, 3.5D, 10.0D, 0.0D);
            for (int i = 0; i < 20; i++) {
                level.addParticle(stone, head.x, pos.y + 0.4D, head.z, random.nextGaussian() * 0.25D, 0.3D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
            }
            return;
        }
        if (id == EVENT_ERUPT) {
            // 破土：大范围震起地面 + 双层冲击环 + 冲天碎石
            DragonSkillEntity.spawnQuakeRing(level, pos.x, pos.y, pos.z, 0.0D, 4.5D, 0.45D, random);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 6.0D, 14.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, pos.x, pos.y + 0.2D, pos.z, 3.5D, 10.0D, 0.0D);
            for (int i = 0; i < 36; i++) {
                level.addParticle(stone, pos.x + random.nextGaussian() * 0.8D, pos.y + 0.5D, pos.z + random.nextGaussian() * 0.8D, random.nextGaussian() * 0.25D, 0.6D + random.nextDouble() * 0.5D, random.nextGaussian() * 0.25D);
            }
            level.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0D, pos.z, 0.0D, 0.0D, 0.0D);
            return;
        }
        if (id == EVENT_RESURFACE) {
            // 浮出：沿身体长度一路翻起地面
            Vec3 tail = pos.subtract(forward.scale(8.0D));
            DragonSkillEntity.spawnQuakeLine(level, pos.x, pos.y, pos.z, tail.x, tail.z, 1.4D, 0.3D, random);
            return;
        }
        if (id == EVENT_SLAM) {
            // 头砸地面：冲击环 + 震起 + 秘符碎光
            Vec3 head = pos.add(forward.scale(1.0D));
            DragonSkillEntity.spawnQuakeRing(level, head.x, pos.y, head.z, 0.0D, 2.5D, 0.35D, random);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, head.x, pos.y + 0.12D, head.z, 4.0D, 12.0D, 0.0D);
            for (int i = 0; i < 16; i++) {
                level.addParticle(ParticleTypes.WITCH, head.x, pos.y + 0.5D, head.z, random.nextGaussian() * 0.3D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.3D);
            }
            return;
        }
        if (id == EVENT_THROW) {
            // 甩尾：尾尖处碎石飞溅
            Vec3 tail = pos.subtract(forward.scale(6.9D)).add(0.0D, 3.0D, 0.0D);
            for (int i = 0; i < 16; i++) {
                level.addParticle(stone, tail.x, tail.y, tail.z, random.nextGaussian() * 0.25D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
            }
            return;
        }
        if (id == EVENT_ENRAGE) {
            // 狂暴：双层秘符冲击环 + 符文能量爆发
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 9.0D, 16.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, pos.x, pos.y + 2.0D, pos.z, 6.0D, 12.0D, 0.0D);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ParticleTypes.PORTAL, pos.x + random.nextGaussian() * 2.0D, pos.y + random.nextDouble() * 3.0D, pos.z + random.nextGaussian() * 2.0D, random.nextGaussian() * 0.5D, random.nextDouble() * 0.5D, random.nextGaussian() * 0.5D);
            }
        }
    }

    // 战斗目标：有目标时接管移动与技能
    static final class CombatGoal extends Goal {
        private final RebornMobEntity naga;

        CombatGoal(RebornMobEntity naga) {
            this.naga = naga;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.naga.getTarget();
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
            this.naga.getNagaCombat().stop();
            this.naga.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.naga.getNagaCombat().tick(this.naga.getTarget());
        }
    }
}
