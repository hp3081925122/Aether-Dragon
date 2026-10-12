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

// 水晶石傀儡 boss 战斗：技能选择、服务端时间轴与客户端特效
final class GolemCombat {
    static final int COMBO = 1;
    static final int SLAM = 2;
    static final int CHARGE = 3;
    static final int FISSURE = 4;
    static final int PRISON = 5;
    static final int SUMMON = 6;
    static final int ENRAGE = 7;
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] DURATION = {0, 36, 75, 48, 44, 48, 70, 62};
    private static final int[] COOLDOWN = {0, 45, 200, 160, 140, 220, 600, 0};
    // 重锤、召唤、狂暴沿用原有的 groundslam、summoning、roar 动画
    private static final String[] ANIMATIONS = {"", "skill_combo", "groundslam", "skill_charge", "skill_fissure", "skill_prison", "summoning", "roar"};
    static final byte EVENT_PUNCH_RIGHT = (byte) 140;
    static final byte EVENT_PUNCH_LEFT = (byte) 141;
    static final byte EVENT_SLAM = (byte) 142;
    static final byte EVENT_FISSURE = (byte) 143;
    static final byte EVENT_CHARGE_HIT = (byte) 144;
    static final byte EVENT_SUMMON = (byte) 145;
    static final byte EVENT_ENRAGE = (byte) 146;
    private static final int CHARGE_START = 14;
    private static final int CHARGE_END = 32;
    private static final int MINION_LIMIT = 6;
    private static final String ENRAGE_TAG = "reborn_golem_enraged";

    private final RebornMobEntity golem;
    private final int[] cooldowns = new int[8];
    private final Set<Integer> hits = new HashSet<>();
    private int skillTicks;
    // 首次出现时等待激活动画播完
    private int gap = 110;
    private Vec3 chargeDir = Vec3.ZERO;
    private int clientSkill;
    private int clientTicks;

    GolemCombat(RebornMobEntity golem) {
        this.golem = golem;
    }

    static String getAnimation(int skill) {
        if (skill <= 0 || skill >= ANIMATIONS.length) {
            return "";
        }
        return "animation.crystalite_golem." + ANIMATIONS[skill];
    }

    void save(CompoundTag tag) {
        tag.putBoolean(ENRAGE_TAG, this.golem.isGolemEnraged());
    }

    void load(CompoundTag tag) {
        this.golem.setBossEnraged(tag.getBoolean(ENRAGE_TAG));
    }

    void stop() {
        this.skillTicks = 0;
        this.golem.setBossSkill(0);
    }

    private boolean enraged() {
        return this.golem.isGolemEnraged();
    }

    private Vec3 forward() {
        float yaw = this.golem.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    // 实体右侧方向
    private Vec3 right() {
        Vec3 forward = forward();
        return new Vec3(-forward.z, 0.0D, forward.x);
    }

    private DamageSource source() {
        return this.golem.damageSources().mobAttack(this.golem);
    }

    private double ground(double x, double z, double fromY) {
        return this.golem.findGroundY(x, fromY + 4.0D, z, 10.0D, fromY);
    }

    private void crystal(ServerLevel server, int kind, double x, double z, double fromY, int delay, float scale, boolean silent) {
        GolemSkillEntity.spawn(server, this.golem, kind, new Vec3(x, ground(x, z, fromY), z), delay, scale, silent);
    }

    // 服务端每 tick：冷却、移动、选技能与执行时间轴
    void tick(LivingEntity target) {
        for (int i = 1; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        int skill = this.golem.getGolemSkill();
        if (skill == 0) {
            tickIdle(target);
            return;
        }
        this.golem.getNavigation().stop();
        this.skillTicks++;
        if (this.golem.level() instanceof ServerLevel server) {
            switch (skill) {
                case COMBO -> tickCombo(target);
                case SLAM -> tickSlam(server, target);
                case CHARGE -> tickCharge(server, target);
                case FISSURE -> tickFissure(server, target);
                case PRISON -> tickPrison(server, target);
                case SUMMON -> tickSummon(server);
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
        double distance = this.golem.distanceTo(target);
        this.golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 追近目标
        if (distance > 4.0D) {
            if (this.golem.tickCount % 8 == 0 || this.golem.getNavigation().isDone()) {
                this.golem.getNavigation().moveTo(target, 1.2D);
            }
        } else {
            this.golem.getNavigation().stop();
            this.golem.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        Vec3 toward = target.position().subtract(this.golem.position());
        Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
        double front = 1.0D;
        if (flat.lengthSqr() > 1.0E-4D) {
            front = flat.normalize().dot(forward());
        }
        int chosen = 0;
        if (!enraged() && this.golem.getHealth() <= this.golem.getMaxHealth() * 0.5F) {
            // 半血狂暴：晶化觉醒
            chosen = ENRAGE;
            this.golem.setBossEnraged(true);
        } else {
            List<Integer> ready = new ArrayList<>();
            if (distance <= 5.5D && front > 0.3D && this.cooldowns[COMBO] <= 0) {
                ready.add(COMBO);
                ready.add(COMBO);
                ready.add(COMBO);
            }
            if (distance <= 7.0D && this.cooldowns[SLAM] <= 0) {
                ready.add(SLAM);
            }
            if (distance >= 6.0D && distance <= 20.0D && this.cooldowns[CHARGE] <= 0) {
                ready.add(CHARGE);
                ready.add(CHARGE);
            }
            if (distance >= 4.0D && distance <= 14.0D && this.cooldowns[FISSURE] <= 0) {
                ready.add(FISSURE);
            }
            if (distance >= 6.0D && this.cooldowns[PRISON] <= 0) {
                ready.add(PRISON);
            }
            if (this.cooldowns[SUMMON] <= 0 && countMinions() < MINION_LIMIT) {
                ready.add(SUMMON);
            }
            if (!ready.isEmpty()) {
                chosen = ready.get(this.golem.getRandom().nextInt(ready.size()));
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
        this.golem.getNavigation().stop();
        this.golem.setBossSkill(chosen);
    }

    // 晶拳连击：第 11 tick 右勾拳，第 22 tick 左勾拳
    private void tickCombo(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 8 || (t >= 14 && t <= 19)) {
            this.golem.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        }
        if (t == 7 || t == 18) {
            ModSounds.play(this.golem.level(), "golem_punch", this.golem.getX(), this.golem.getY() + 2.0D, this.golem.getZ(), 1.5F, 1.0F);
        }
        if (t == 11 || t == 22) {
            boolean left = t == 22;
            float amount = 11.0F;
            if (left) {
                amount = 13.0F;
            }
            Vec3 forward = forward();
            boolean landed = false;
            AABB box = this.golem.getBoundingBox().inflate(5.0D, 2.0D, 5.0D);
            for (LivingEntity living : this.golem.level().getEntitiesOfClass(LivingEntity.class, box, this.golem::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(this.golem.position());
                Vec3 flat = new Vec3(offset.x, 0.0D, offset.z);
                double distance = flat.length();
                if (distance > 5.5D || (distance > 1.0D && flat.normalize().dot(forward) < 0.3D)) {
                    continue;
                }
                if (living.hurt(source(), amount)) {
                    landed = true;
                    RebornMobEntity.applyBossKnockback(living, new Vec3(forward.x * 0.5D, 0.2D, forward.z * 0.5D));
                }
            }
            if (landed) {
                ModSounds.play(this.golem.level(), "golem_punch_hit", this.golem.getX(), this.golem.getY() + 1.5D, this.golem.getZ(), 1.5F, 1.0F);
            }
            byte event = EVENT_PUNCH_RIGHT;
            if (left) {
                event = EVENT_PUNCH_LEFT;
            }
            this.golem.level().broadcastEntityEvent(this.golem, event);
        }
    }

    // 双拳重锤（groundslam 动画）：第 35 tick 双拳落地，晶刺随后一圈圈向外破土
    private void tickSlam(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 25) {
            this.golem.faceDragonTowards(target.getX(), target.getZ(), 6.0F);
        }
        if (t == 35) {
            Vec3 center = this.golem.position().add(forward().scale(3.8D));
            AABB box = new AABB(center, center).inflate(3.5D, 2.5D, 3.5D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.golem::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(center);
                if (offset.x * offset.x + offset.z * offset.z <= 3.5D * 3.5D && living.hurt(source(), 16.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.45D, 0.0D));
                }
            }
            ModSounds.play(server, "golem_impact", center.x, center.y, center.z, 3.0F, 1.0F);
            server.broadcastEntityEvent(this.golem, EVENT_SLAM);
            int rings = 3;
            if (enraged()) {
                rings = 4;
            }
            for (int k = 1; k <= rings; k++) {
                // 第 k 圈：半径 2.6k，晶刺数随半径增加
                int count = 5 * k + 1;
                double radius = 2.6D * k;
                double offset = this.golem.getRandom().nextDouble() * Math.PI * 2.0D;
                for (int i = 0; i < count; i++) {
                    double angle = offset + Math.PI * 2.0D * i / count;
                    boolean silent = i != 0;
                    crystal(server, GolemSkillEntity.SPIKE, center.x + Math.cos(angle) * radius, center.z + Math.sin(angle) * radius, center.y, 2 + k * 5, 0.85F, silent);
                }
            }
        }
    }

    // 晶甲冲撞：0-13 蓄势锁定，14-32 冲锋并在身后留下晶刺，撞墙提前停下
    private void tickCharge(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t < CHARGE_START) {
            this.golem.faceDragonTowards(target.getX(), target.getZ(), 12.0F);
        }
        if (t == 2) {
            ModSounds.play(server, "golem_charge", this.golem.getX(), this.golem.getY() + 2.0D, this.golem.getZ(), 1.5F, 1.0F);
        }
        if (t == CHARGE_START) {
            this.chargeDir = forward();
        }
        if (t >= CHARGE_START && t < CHARGE_END) {
            this.golem.move(MoverType.SELF, new Vec3(this.chargeDir.x * 0.75D, -0.3D, this.chargeDir.z * 0.75D));
            if (t % 4 == 0) {
                ModSounds.play(server, "golem_step", this.golem.getX(), this.golem.getY(), this.golem.getZ(), 1.5F, 1.0F);
            }
            if (t % 3 == 0) {
                // 身后留下一串晶刺
                Vec3 back = this.golem.position().subtract(this.chargeDir.scale(2.5D));
                crystal(server, GolemSkillEntity.SPIKE, back.x, back.z, this.golem.getY(), 4, 0.6F, true);
            }
            AABB box = this.golem.getBoundingBox().inflate(1.0D, 0.5D, 1.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.golem::canDragonSkillHit)) {
                if (!this.hits.add(living.getId())) {
                    continue;
                }
                if (living.hurt(source(), 14.0F)) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(this.chargeDir.x * 0.75D, 0.25D, this.chargeDir.z * 0.75D));
                    ModSounds.play(server, "golem_punch_hit", living.getX(), living.getY(), living.getZ(), 1.5F, 0.8F);
                    server.broadcastEntityEvent(this.golem, EVENT_CHARGE_HIT);
                }
            }
            if (this.golem.horizontalCollision) {
                // 撞墙：急停并震出一圈晶刺
                ModSounds.play(server, "golem_impact", this.golem.getX(), this.golem.getY(), this.golem.getZ(), 2.5F, 1.1F);
                server.broadcastEntityEvent(this.golem, EVENT_CHARGE_HIT);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * 2.0D * i / 6.0D;
                    crystal(server, GolemSkillEntity.SPIKE, this.golem.getX() + Math.cos(angle) * 3.0D, this.golem.getZ() + Math.sin(angle) * 3.0D, this.golem.getY(), 2, 0.8F, i != 0);
                }
                this.skillTicks = CHARGE_END;
            }
        }
        if (t == CHARGE_END) {
            this.golem.setDeltaMovement(Vec3.ZERO);
        }
    }

    // 晶脉裂地：第 17 tick 右拳砸地，晶刺沿多条放射线依次破土，其中一条正对目标
    private void tickFissure(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 14) {
            this.golem.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (t == 17) {
            Vec3 origin = this.golem.position().add(forward().scale(2.1D)).add(right().scale(1.3D));
            ModSounds.play(server, "golem_impact", origin.x, origin.y, origin.z, 2.5F, 1.1F);
            server.broadcastEntityEvent(this.golem, EVENT_FISSURE);
            int spokes = 6;
            if (enraged()) {
                spokes = 8;
            }
            double base = Math.atan2(target.getZ() - origin.z, target.getX() - origin.x);
            for (int s = 0; s < spokes; s++) {
                double angle = base + Math.PI * 2.0D * s / spokes;
                for (int k = 1; k <= 7; k++) {
                    double distance = 1.6D * k;
                    crystal(server, GolemSkillEntity.SPIKE, origin.x + Math.cos(angle) * distance, origin.z + Math.sin(angle) * distance, origin.y, 1 + k * 2, 0.7F + k * 0.06F, s != 0 || k % 2 == 0);
                }
            }
        }
    }

    // 晶笼禁锢：第 8 tick 在目标周围预警，第 14 tick 晶笼升起，第 30 tick 中心晶核爆发
    private void tickPrison(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.golem.faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        if (t == 2) {
            ModSounds.play(server, "golem_charge", this.golem.getX(), this.golem.getY() + 2.0D, this.golem.getZ(), 1.5F, 1.2F);
        }
        if (t == 8) {
            Vec3 center = target.position();
            double radius = 3.5D;
            int count = 10;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0D * i / count;
                crystal(server, GolemSkillEntity.CAGE, center.x + Math.cos(angle) * radius, center.z + Math.sin(angle) * radius, center.y, 6, 1.0F, i != 0);
            }
            if (enraged()) {
                // 狂暴：外圈再加一层晶笼
                for (int i = 0; i < 14; i++) {
                    double angle = Math.PI * 2.0D * (i + 0.5D) / 14.0D;
                    crystal(server, GolemSkillEntity.CAGE, center.x + Math.cos(angle) * 5.5D, center.z + Math.sin(angle) * 5.5D, center.y, 10, 1.0F, true);
                }
            }
            crystal(server, GolemSkillEntity.CORE, center.x, center.z, center.y, 22, 2.0F, false);
        }
        if (t == 29) {
            ModSounds.play(server, "golem_cage", this.golem.getX(), this.golem.getY() + 2.0D, this.golem.getZ(), 2.5F, 1.0F);
        }
    }

    // 晶核召唤（summoning 动画）：第 55 tick 双臂砸下，身边晶簇碎裂放出小傀儡
    private void tickSummon(ServerLevel server) {
        int t = this.skillTicks;
        if (t == 10) {
            server.broadcastEntityEvent(this.golem, EVENT_SUMMON);
        }
        if (t == 36) {
            // 晶簇先长出，双臂砸下时恰好碎裂
            for (int i = 0; i < summonCount(); i++) {
                Vec3 at = summonPoint(i);
                crystal(server, GolemSkillEntity.SPIKE, at.x, at.z, this.golem.getY(), 0, 1.3F, i != 0);
            }
        }
        if (t == 55) {
            int count = Math.min(summonCount(), MINION_LIMIT - countMinions());
            for (int i = 0; i < count; i++) {
                Vec3 at = summonPoint(i);
                RebornMobEntity minion = ModEntities.MOBS.get("crystaliteminion").get().create(server);
                if (minion == null) {
                    continue;
                }
                minion.moveTo(at.x, ground(at.x, at.z, this.golem.getY()), at.z, this.golem.getYRot(), 0.0F);
                server.addFreshEntity(minion);
            }
            ModSounds.play(server, "golem_impact", this.golem.getX(), this.golem.getY(), this.golem.getZ(), 2.0F, 1.2F);
        }
    }

    private int summonCount() {
        if (enraged()) {
            return 3;
        }
        return 2;
    }

    // 召唤点：身体两侧与身后
    private Vec3 summonPoint(int index) {
        double angle = Math.toRadians(this.golem.yBodyRot) + Math.PI * 0.5D + index * Math.PI * 0.5D;
        return this.golem.position().add(Math.cos(angle) * 3.2D, 0.0D, Math.sin(angle) * 3.2D);
    }

    private int countMinions() {
        AABB range = this.golem.getBoundingBox().inflate(32.0D);
        return this.golem.level().getEntitiesOfClass(RebornMobEntity.class, range, candidate -> candidate.getIdPath().equals("crystaliteminion")).size();
    }

    // 晶化觉醒（roar 动画）：第 17 tick 怒吼，震退近身目标并升起一圈晶刺
    private void tickEnrage(ServerLevel server) {
        if (this.skillTicks == 17) {
            AABB box = this.golem.getBoundingBox().inflate(7.0D, 3.0D, 7.0D);
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, box, this.golem::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.golem.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.7D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.25D, push.z));
            }
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2.0D * i / 12.0D;
                crystal(server, GolemSkillEntity.SPIKE, this.golem.getX() + Math.cos(angle) * 4.5D, this.golem.getZ() + Math.sin(angle) * 4.5D, this.golem.getY(), 3, 1.1F, i != 0);
            }
            ModSounds.play(server, "golem_charge", this.golem.getX(), this.golem.getY() + 2.0D, this.golem.getZ(), 3.0F, 0.7F);
            server.broadcastEntityEvent(this.golem, EVENT_ENRAGE);
        }
    }

    static boolean isEvent(byte id) {
        return id >= EVENT_PUNCH_RIGHT && id <= EVENT_ENRAGE;
    }

    // 客户端每 tick：蓄力、冲锋与狂暴光环
    void clientTick() {
        int skill = this.golem.getGolemSkill();
        if (skill != this.clientSkill) {
            this.clientSkill = skill;
            this.clientTicks = 0;
        }
        this.clientTicks++;
        int t = this.clientTicks;
        Level level = this.golem.level();
        RandomSource random = this.golem.getRandom();
        Vec3 pos = this.golem.position();
        Vec3 forward = forward();
        if (skill == SLAM && t < 30) {
            // 双拳高举蓄力：碎晶向头顶汇聚
            gather(level, random, pos.add(forward.scale(-0.4D)).add(0.0D, 4.6D, 0.0D), 2.5D, 2);
        }
        if (skill == FISSURE && t < 15) {
            gather(level, random, pos.add(right().scale(1.3D)).add(0.0D, 4.5D, 0.0D), 2.0D, 2);
        }
        if (skill == PRISON && t > 8 && t < 30) {
            gather(level, random, pos.add(forward.scale(2.0D)).add(0.0D, 2.8D, 0.0D), 2.0D, 1);
        }
        if (skill == CHARGE) {
            if (t <= CHARGE_START && t % 4 == 0) {
                // 蓄势：前方冲锋路线预警
                for (double d = 3.0D; d <= 15.0D; d += 3.0D) {
                    level.addAlwaysVisibleParticle(ModParticles.GOLEM_WARNING.get(), true, pos.x + forward.x * d, pos.y + 0.1D, pos.z + forward.z * d, 1.6D, 6.0D, 0.0D);
                }
            }
            if (t > CHARGE_START && t <= CHARGE_END) {
                // 冲锋：两侧扬尘与碎晶
                Vec3 side = right().scale(1.8D);
                level.addParticle(ParticleTypes.CLOUD, pos.x + side.x, pos.y + 0.2D, pos.z + side.z, -forward.x * 0.15D, 0.03D, -forward.z * 0.15D);
                level.addParticle(ParticleTypes.CLOUD, pos.x - side.x, pos.y + 0.2D, pos.z - side.z, -forward.x * 0.15D, 0.03D, -forward.z * 0.15D);
                level.addParticle(ModParticles.GOLEM_SHARD.get(), pos.x + forward.x * 2.0D, pos.y + 1.5D, pos.z + forward.z * 2.0D, random.nextGaussian() * 0.1D, 0.2D, random.nextGaussian() * 0.1D);
            }
        }
        if (this.golem.isGolemEnraged() && this.golem.tickCount % 2 == 0) {
            // 狂暴光环：碎晶环绕身体缓慢旋转
            double angle = this.golem.tickCount * 0.15D;
            for (int i = 0; i < 2; i++) {
                double a = angle + i * Math.PI;
                level.addParticle(ModParticles.GOLEM_SHARD.get(), pos.x + Math.cos(a) * 2.6D, pos.y + 1.0D + random.nextDouble() * 2.5D, pos.z + Math.sin(a) * 2.6D, 0.0D, 0.02D, 0.0D);
            }
        }
    }

    // 碎晶向某点汇聚
    private static void gather(Level level, RandomSource random, Vec3 center, double spread, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 from = center.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread);
            Vec3 velocity = center.subtract(from).scale(0.18D);
            level.addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }

    // 客户端实体事件：命中瞬间的特效
    void handleEvent(byte id) {
        Level level = this.golem.level();
        RandomSource random = this.golem.getRandom();
        Vec3 pos = this.golem.position();
        Vec3 forward = forward();
        float yaw = this.golem.yBodyRot;
        if (id == EVENT_PUNCH_RIGHT || id == EVENT_PUNCH_LEFT) {
            // 勾拳：拳头前方两道向前推出的晶环拳压 + 碎晶迸溅
            double side = 0.6D;
            if (id == EVENT_PUNCH_LEFT) {
                side = -0.6D;
            }
            Vec3 fist = pos.add(forward.scale(2.4D)).add(right().scale(side)).add(0.0D, 2.2D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_PUNCH.get(), true, fist.x, fist.y, fist.z, 1.8D, yaw, 0.35D);
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_PUNCH.get(), true, fist.x, fist.y, fist.z, 1.1D, yaw, 0.6D);
            for (int i = 0; i < 12; i++) {
                level.addParticle(ModParticles.GOLEM_SHARD.get(), fist.x, fist.y, fist.z, forward.x * 0.3D + random.nextGaussian() * 0.15D, random.nextDouble() * 0.25D, forward.z * 0.3D + random.nextGaussian() * 0.15D);
            }
            return;
        }
        if (id == EVENT_SLAM) {
            // 重锤落地：双层冲击环 + 大范围震起地面 + 碎晶冲天
            Vec3 center = pos.add(forward.scale(3.8D));
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, center.x, center.y + 0.15D, center.z, 7.0D, 14.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, center.x, center.y + 0.2D, center.z, 4.0D, 10.0D, 0.0D);
            DragonSkillEntity.spawnQuakeRing(level, center.x, center.y, center.z, 0.0D, 3.5D, 0.4D, random);
            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ModParticles.GOLEM_SHARD.get(), center.x, center.y + 0.3D, center.z, Math.cos(angle) * 0.35D, 0.4D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.35D);
            }
            return;
        }
        if (id == EVENT_FISSURE) {
            // 裂地：拳落点冲击环 + 震起
            Vec3 origin = pos.add(forward.scale(2.1D)).add(right().scale(1.3D));
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, origin.x, origin.y + 0.15D, origin.z, 4.0D, 10.0D, 0.0D);
            DragonSkillEntity.spawnQuakeRing(level, origin.x, origin.y, origin.z, 0.0D, 2.2D, 0.35D, random);
            for (int i = 0; i < 16; i++) {
                level.addParticle(ModParticles.GOLEM_SHARD.get(), origin.x, origin.y + 0.3D, origin.z, random.nextGaussian() * 0.2D, 0.4D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.2D);
            }
            return;
        }
        if (id == EVENT_CHARGE_HIT) {
            // 冲撞命中：前方冲击环 + 碎晶
            Vec3 front = pos.add(forward.scale(2.0D));
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, front.x, front.y + 1.2D, front.z, 3.0D, 8.0D, 0.0D);
            for (int i = 0; i < 14; i++) {
                level.addParticle(ModParticles.GOLEM_SHARD.get(), front.x, front.y + 1.5D, front.z, random.nextGaussian() * 0.25D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
            }
            return;
        }
        if (id == EVENT_SUMMON) {
            // 召唤：符文从地面升起
            for (int i = 0; i < 12; i++) {
                level.addParticle(ModParticles.RUNESEFFECT.get(), pos.x + random.nextGaussian() * 2.0D, pos.y + 0.2D, pos.z + random.nextGaussian() * 2.0D, 0.0D, 0.08D, 0.0D);
            }
            return;
        }
        if (id == EVENT_ENRAGE) {
            // 晶化觉醒：三层冲击环 + 碎晶爆发
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 9.0D, 16.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, pos.x, pos.y + 2.0D, pos.z, 6.0D, 12.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, pos.x, pos.y + 3.8D, pos.z, 4.0D, 10.0D, 0.0D);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ModParticles.GOLEM_SHARD.get(), pos.x, pos.y + 2.5D, pos.z, random.nextGaussian() * 0.35D, 0.3D + random.nextDouble() * 0.4D, random.nextGaussian() * 0.35D);
            }
        }
    }

    // 战斗目标：有目标时接管移动与技能
    static final class CombatGoal extends Goal {
        private final RebornMobEntity golem;

        CombatGoal(RebornMobEntity golem) {
            this.golem = golem;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.golem.getTarget();
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
            this.golem.getGolemCombat().stop();
            this.golem.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.golem.getGolemCombat().tick(this.golem.getTarget());
        }
    }
}
