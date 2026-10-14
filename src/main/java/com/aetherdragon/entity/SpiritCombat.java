package com.aetherdragon.entity;

import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 灵魂 boss 战斗：技能选择、服务端时间轴与客户端特效
final class SpiritCombat {
    static final int SLASH = 1;
    static final int CLEAVE = 2;
    static final int RUNES = 3;
    static final int DASH = 4;
    static final int SKYFALL = 5;
    static final int NOVA = 6;
    static final int ENRAGE = 7;
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] DURATION = {0, 34, 46, 56, 40, 70, 64, 40};
    private static final int[] COOLDOWN = {0, 40, 130, 160, 140, 300, 240, 0};
    private static final String[] ANIMATIONS = {"", "skill_slash", "skill_cleave", "skill_runes", "skill_dash", "skill_skyfall", "skill_nova", "enrage"};
    static final byte EVENT_SLASH_1 = 110;
    static final byte EVENT_SLASH_2 = 111;
    static final byte EVENT_CLEAVE = 112;
    static final byte EVENT_RUNES = 113;
    static final byte EVENT_DASH_BURST = 114;
    static final byte EVENT_SKY_FLASH = 115;
    static final byte EVENT_NOVA = 116;
    static final byte EVENT_ENRAGE = 119;
    // 裁决劈斩：裂地长度；灵焰轮环：扩张速度与持续 tick
    private static final int CLEAVE_LENGTH = 14;
    private static final double NOVA_SPEED = 0.9D;
    private static final int NOVA_LIFE = 18;
    private static final int[] NOVA_STARTS = {20, 30, 40};
    private static final int DASH_START = 12;
    private static final int DASH_END = 20;
    private static final String ENRAGE_TAG = "reborn_spirit_enraged";

    private final RebornMobEntity spirit;
    private final int[] cooldowns = new int[8];
    private final Set<Integer> hits = new HashSet<>();
    private final List<Set<Integer>> novaHits = new ArrayList<>();
    private int skillTicks;
    private int gap = 30;
    private boolean enraged;
    private Vec3 dashDir = Vec3.ZERO;
    private double dashSpeed;
    private Vec3 cleaveOrigin = Vec3.ZERO;
    private Vec3 cleaveDir = Vec3.ZERO;
    // 客户端计时与特效状态
    private int clientSkill;
    private int clientTicks;
    private int clientCleaveStart = -100;
    private Vec3 clientCleaveOrigin = Vec3.ZERO;
    private Vec3 clientCleaveDir = Vec3.ZERO;
    private final int[] clientNovaStarts = {-100, -100, -100};

    SpiritCombat(RebornMobEntity spirit) {
        this.spirit = spirit;
        for (int i = 0; i < NOVA_STARTS.length; i++) {
            this.novaHits.add(new HashSet<>());
        }
    }

    static String getAnimation(int skill) {
        if (skill <= 0 || skill >= ANIMATIONS.length) {
            return "";
        }
        return "animation.spirit." + ANIMATIONS[skill];
    }

    void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean(ENRAGE_TAG, this.enraged);
    }

    void load(net.minecraft.nbt.CompoundTag tag) {
        this.enraged = tag.getBoolean(ENRAGE_TAG);
    }

    void stop() {
        this.skillTicks = 0;
        this.spirit.setBossSkill(0);
    }

    private Vec3 forward() {
        float yaw = this.spirit.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    private float damage(float scale) {
        return (float) this.spirit.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
    }

    private DamageSource source() {
        return this.spirit.damageSources().mobAttack(this.spirit);
    }

    // 服务端每 tick：冷却、移动、选技能与执行时间轴
    void tick(LivingEntity target) {
        for (int i = 1; i < this.cooldowns.length; i++) {
            if (this.cooldowns[i] > 0) {
                this.cooldowns[i]--;
            }
        }
        int skill = this.spirit.getSpiritSkill();
        if (skill == 0) {
            tickIdle(target);
            return;
        }
        this.spirit.getNavigation().stop();
        this.skillTicks++;
        if (this.spirit.level() instanceof ServerLevel server) {
            switch (skill) {
                case SLASH -> tickSlash(target);
                case CLEAVE -> tickCleave(target);
                case RUNES -> tickRunes(server, target);
                case DASH -> tickDash(target);
                case SKYFALL -> tickSkyfall(server, target);
                case NOVA -> tickNova();
                default -> tickEnrage();
            }
        }
        if (this.skillTicks >= DURATION[skill]) {
            stop();
            this.gap = 16;
            if (this.enraged) {
                this.gap = 9;
            }
        }
    }

    private void tickIdle(LivingEntity target) {
        double distance = this.spirit.distanceTo(target);
        this.spirit.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 追近目标，贴身时停步
        if (distance > 4.0D) {
            if (this.spirit.tickCount % 8 == 0 || this.spirit.getNavigation().isDone()) {
                this.spirit.getNavigation().moveTo(target, 0.75D);
            }
        } else {
            this.spirit.getNavigation().stop();
        }
        if (this.gap > 0) {
            this.gap--;
            return;
        }
        int chosen = 0;
        if (!this.enraged && this.spirit.getHealth() <= this.spirit.getMaxHealth() * 0.5F) {
            // 半血狂暴：此后冷却缩短
            chosen = ENRAGE;
            this.enraged = true;
        } else {
            List<Integer> ready = new ArrayList<>();
            if (distance <= 5.5D && this.cooldowns[SLASH] <= 0) {
                ready.add(SLASH);
                ready.add(SLASH);
                ready.add(SLASH);
            }
            if (distance <= 9.0D && this.cooldowns[CLEAVE] <= 0) {
                ready.add(CLEAVE);
            }
            if (distance <= 8.0D && this.cooldowns[NOVA] <= 0) {
                ready.add(NOVA);
            }
            if (distance >= 7.0D && distance <= 22.0D && this.cooldowns[DASH] <= 0) {
                ready.add(DASH);
                ready.add(DASH);
            }
            if (distance >= 5.0D && this.cooldowns[RUNES] <= 0) {
                ready.add(RUNES);
            }
            if (distance >= 4.0D && this.cooldowns[SKYFALL] <= 0) {
                ready.add(SKYFALL);
            }
            if (!ready.isEmpty()) {
                chosen = ready.get(this.spirit.getRandom().nextInt(ready.size()));
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
        this.hits.clear();
        for (Set<Integer> set : this.novaHits) {
            set.clear();
        }
        this.spirit.getNavigation().stop();
        this.spirit.setBossSkill(chosen);
    }

    // 灵刃连斩：第 11 tick 右往左横斩，第 18 tick 回身反斩
    private void tickSlash(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 9 || (t >= 13 && t <= 16)) {
            this.spirit.faceDragonTowards(target.getX(), target.getZ(), 15.0F);
        }
        if (t == 2) {
            ModSounds.play(this.spirit.level(), "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 1.0F, 1.4F);
        }
        if (t == 11 || t == 18) {
            boolean second = t == 18;
            float scale = 1.0F;
            if (second) {
                scale = 1.15F;
            }
            hitArc(6.0D, damage(scale), second);
            ModSounds.play(this.spirit.level(), "spirit_slash", this.spirit.getX(), this.spirit.getY() + 1.5D, this.spirit.getZ(), 2.0F, 1.0F);
            byte event = EVENT_SLASH_1;
            if (second) {
                event = EVENT_SLASH_2;
            }
            this.spirit.level().broadcastEntityEvent(this.spirit, event);
        }
    }

    // 前方扇形斩击，击退方向沿挥剑方向
    private void hitArc(double radius, float amount, boolean toRight) {
        Vec3 forward = forward();
        Vec3 side = new Vec3(forward.z, 0.0D, -forward.x);
        if (toRight) {
            side = side.reverse();
        }
        AABB box = this.spirit.getBoundingBox().inflate(radius, 2.0D, radius);
        boolean landed = false;
        for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
            Vec3 offset = living.position().subtract(this.spirit.position());
            Vec3 flat = new Vec3(offset.x, 0.0D, offset.z);
            double distance = flat.length();
            if (distance > radius || (distance > 1.0D && flat.normalize().dot(forward) < -0.35D)) {
                continue;
            }
            if (living.hurt(source(), amount)) {
                landed = true;
                Vec3 push = side.scale(0.3D).add(forward.scale(0.25D));
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.15D, push.z));
            }
        }
        if (landed) {
            ModSounds.play(this.spirit.level(), "spirit_slash_hit", this.spirit.getX(), this.spirit.getY() + 1.0D, this.spirit.getZ(), 1.5F, 1.0F);
        }
    }

    // 裁决劈斩：0-16 举剑并转向，第 20 tick 劈地，随后魂焰沿直线向前喷发
    private void tickCleave(LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 16) {
            this.spirit.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        }
        if (t == 2) {
            ModSounds.play(this.spirit.level(), "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 2.0F, 0.8F);
        }
        if (t == 14) {
            ModSounds.play(this.spirit.level(), "spirit_slash", this.spirit.getX(), this.spirit.getY() + 3.0D, this.spirit.getZ(), 2.0F, 0.7F);
        }
        if (t == 20) {
            Vec3 forward = forward();
            this.cleaveDir = forward;
            this.cleaveOrigin = this.spirit.position().add(forward.scale(2.5D));
            // 剑落点：范围重击
            damageCircle(this.cleaveOrigin, 3.0D, damage(1.3F), 0.2D, null);
            ModSounds.play(this.spirit.level(), "spirit_impact", this.cleaveOrigin.x, this.cleaveOrigin.y, this.cleaveOrigin.z, 3.0F, 1.0F);
            this.spirit.level().broadcastEntityEvent(this.spirit, EVENT_CLEAVE);
        }
        if (t > 20 && t <= 20 + CLEAVE_LENGTH) {
            // 裂地魂焰：每 tick 向前推进 1 格
            Vec3 point = this.cleaveOrigin.add(this.cleaveDir.scale(t - 20));
            damageCircle(point, 1.6D, damage(0.9F), 0.35D, this.hits);
            if ((t - 20) % 3 == 1) {
                ModSounds.play(this.spirit.level(), "spirit_erupt", point.x, point.y, point.z, 1.5F, 1.0F);
            }
        }
    }

    // 圆形范围伤害；hitOnce 不为空时每个目标只命中一次
    private void damageCircle(Vec3 center, double radius, float amount, double lift, Set<Integer> hitOnce) {
        AABB box = new AABB(center, center).inflate(radius, 2.5D, radius);
        for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
            Vec3 offset = living.position().subtract(center);
            if (offset.x * offset.x + offset.z * offset.z > radius * radius) {
                continue;
            }
            if (hitOnce != null && !hitOnce.add(living.getId())) {
                continue;
            }
            if (living.hurt(source(), amount)) {
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, lift, 0.0D));
            }
        }
    }

    // 圣环符弹：第 10 tick 光环散出七枚符文，24-42 tick 依次射出
    private void tickRunes(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        this.spirit.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        if (t == 10) {
            Vec3 forward = forward();
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            Vec3 halo = this.spirit.position().add(0.0D, 2.4D, 0.0D).subtract(forward.scale(0.6D));
            for (int i = 0; i < 7; i++) {
                // 七枚符文在背后展开成扇形
                double angle = Math.toRadians(15.0D + i * 25.0D);
                Vec3 offset = right.scale(Math.cos(angle) * 2.6D).add(0.0D, Math.sin(angle) * 1.8D, 0.0D).subtract(forward.scale(0.4D));
                SpiritSkillEntity.spawn(server, this.spirit, SpiritSkillEntity.RUNE, halo, offset.scale(0.19D), 0.0D, 14 + i * 3, target);
            }
            ModSounds.play(server, "spirit_rune_summon", halo.x, halo.y, halo.z, 2.0F, 1.0F);
            server.broadcastEntityEvent(this.spirit, EVENT_RUNES);
        }
    }

    // 灵影突袭：0-12 蓄势锁定方向，12-20 高速突进穿过目标，第 30 tick 路径魂焰爆发
    private void tickDash(LivingEntity target) {
        int t = this.skillTicks;
        if (t < DASH_START) {
            this.spirit.faceDragonTowards(target.getX(), target.getZ(), 20.0F);
        }
        if (t == 2) {
            ModSounds.play(this.spirit.level(), "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 1.5F, 1.2F);
        }
        if (t == DASH_START) {
            // 突进距离：穿过目标 4 格，限制在 8-20 格
            Vec3 toward = target.position().subtract(this.spirit.position());
            double length = Mth.clamp(toward.horizontalDistance() + 4.0D, 8.0D, 20.0D);
            this.dashDir = forward();
            this.dashSpeed = length / (DASH_END - DASH_START);
            this.spirit.setBossSkillPoint(this.spirit.position());
            ModSounds.play(this.spirit.level(), "spirit_dash", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 2.0F, 1.0F);
        }
        if (t >= DASH_START && t < DASH_END) {
            this.spirit.move(MoverType.SELF, new Vec3(this.dashDir.x * this.dashSpeed, -0.2D, this.dashDir.z * this.dashSpeed));
            // 突进途中撞到的目标各受一次伤害并被甩向两侧
            AABB box = this.spirit.getBoundingBox().inflate(1.5D, 0.5D, 1.5D);
            for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
                if (!this.hits.add(living.getId())) {
                    continue;
                }
                if (living.hurt(source(), damage(1.2F))) {
                    Vec3 offset = living.position().subtract(this.spirit.position());
                    Vec3 side = new Vec3(-this.dashDir.z, 0.0D, this.dashDir.x);
                    if (offset.dot(side) < 0.0D) {
                        side = side.reverse();
                    }
                    RebornMobEntity.applyBossKnockback(living, new Vec3(side.x * 0.5D, 0.2D, side.z * 0.5D));
                    living.hurtMarked = true;
                    ModSounds.play(this.spirit.level(), "spirit_slash_hit", living.getX(), living.getY(), living.getZ(), 1.5F, 0.9F);
                }
            }
        }
        if (t == DASH_END) {
            this.spirit.setDeltaMovement(Vec3.ZERO);
            this.hits.clear();
        }
        if (t == 30) {
            // 路径爆发：沿突进路线的魂焰
            Vec3 start = this.spirit.getBossSkillPoint();
            Vec3 end = this.spirit.position();
            Vec3 path = end.subtract(start);
            double lengthSqr = Math.max(1.0E-4D, path.x * path.x + path.z * path.z);
            AABB box = new AABB(start, end).inflate(2.0D, 2.5D, 2.0D);
            for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
                Vec3 offset = living.position().subtract(start);
                double along = Mth.clamp((offset.x * path.x + offset.z * path.z) / lengthSqr, 0.0D, 1.0D);
                double dx = offset.x - path.x * along;
                double dz = offset.z - path.z * along;
                if (dx * dx + dz * dz <= 2.0D * 2.0D && living.hurt(source(), damage(0.9F))) {
                    RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.35D, 0.0D));
                    living.hurtMarked = true;
                }
            }
            Vec3 middle = start.add(end).scale(0.5D);
            ModSounds.play(this.spirit.level(), "spirit_erupt", middle.x, middle.y, middle.z, 3.0F, 0.8F);
            this.spirit.level().broadcastEntityEvent(this.spirit, EVENT_DASH_BURST);
        }
    }

    // 天剑审判：0-16 举剑指天，第 18 tick 天光，22-43 tick 每 3 tick 落下一柄天剑
    private void tickSkyfall(ServerLevel server, LivingEntity target) {
        int t = this.skillTicks;
        if (t <= 16) {
            this.spirit.faceDragonTowards(target.getX(), target.getZ(), 10.0F);
        }
        if (t == 2) {
            ModSounds.play(server, "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 2.0F, 0.9F);
        }
        if (t == 18) {
            ModSounds.play(server, "spirit_sky_flash", this.spirit.getX(), this.spirit.getY() + 5.0D, this.spirit.getZ(), 3.0F, 1.0F);
            server.broadcastEntityEvent(this.spirit, EVENT_SKY_FLASH);
        }
        if (t >= 22 && t <= 43 && (t - 22) % 3 == 0) {
            int index = (t - 22) / 3;
            RandomSource random = this.spirit.getRandom();
            // 第一柄落在目标脚下，其余落在目标移动方向前方与周围
            Vec3 lead = target.getDeltaMovement().multiply(8.0D, 0.0D, 8.0D);
            double x = target.getX() + lead.x;
            double z = target.getZ() + lead.z;
            if (index > 0) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = 2.0D + random.nextDouble() * 4.5D;
                x += Math.cos(angle) * radius;
                z += Math.sin(angle) * radius;
            } else {
                x = target.getX();
                z = target.getZ();
            }
            double groundY = this.spirit.findGroundY(x, target.getY() + 6.0D, z, 16.0D, target.getY());
            SpiritSkillEntity.spawn(server, this.spirit, SpiritSkillEntity.SWORD, new Vec3(x, groundY + SpiritSkillEntity.SWORD_HEIGHT, z), Vec3.ZERO, groundY, 16, target);
        }
    }

    // 灵焰轮环：第 20/30/40 tick 各放出一道贴地扩张的焰环，跳起可躲
    private void tickNova() {
        int t = this.skillTicks;
        if (t == 2) {
            ModSounds.play(this.spirit.level(), "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 2.0F, 0.7F);
        }
        for (int i = 0; i < NOVA_STARTS.length; i++) {
            int age = t - NOVA_STARTS[i];
            if (age == 0) {
                ModSounds.play(this.spirit.level(), "spirit_nova", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 3.0F, 1.0F + i * 0.1F);
                this.spirit.level().broadcastEntityEvent(this.spirit, (byte) (EVENT_NOVA + i));
            }
            if (age < 0 || age > NOVA_LIFE) {
                continue;
            }
            double radius = age * NOVA_SPEED;
            double groundY = this.spirit.getY();
            AABB box = this.spirit.getBoundingBox().inflate(radius + 1.0D, 2.0D, radius + 1.0D);
            for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
                double dx = living.getX() - this.spirit.getX();
                double dz = living.getZ() - this.spirit.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);
                // 只命中焰环所在环带内、且贴近地面的目标
                if (Math.abs(distance - radius) > 1.0D || living.getY() - groundY > 1.2D) {
                    continue;
                }
                if (!this.novaHits.get(i).add(living.getId())) {
                    continue;
                }
                if (living.hurt(source(), damage(0.8F))) {
                    living.setSecondsOnFire(3);
                    double scale = 0.4D / Math.max(0.5D, distance);
                    RebornMobEntity.applyBossKnockback(living, new Vec3(dx * scale, 0.175D, dz * scale));
                    living.hurtMarked = true;
                }
            }
        }
    }

    // 狂暴：第 12 tick 怒吼，震退近身目标
    private void tickEnrage() {
        if (this.skillTicks == 2) {
            ModSounds.play(this.spirit.level(), "spirit_charge", this.spirit.getX(), this.spirit.getY(), this.spirit.getZ(), 2.0F, 0.6F);
        }
        if (this.skillTicks == 12) {
            AABB box = this.spirit.getBoundingBox().inflate(7.0D, 3.0D, 7.0D);
            for (LivingEntity living : this.spirit.level().getEntitiesOfClass(LivingEntity.class, box, this.spirit::canDragonSkillHit)) {
                Vec3 push = living.position().subtract(this.spirit.position());
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.7D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.25D, push.z));
            }
            ModSounds.play(this.spirit.level(), "spirit_enrage", this.spirit.getX(), this.spirit.getY() + 2.0D, this.spirit.getZ(), 4.0F, 1.0F);
            this.spirit.level().broadcastEntityEvent(this.spirit, EVENT_ENRAGE);
        }
    }

    static boolean isEvent(byte id) {
        return id >= EVENT_SLASH_1 && id <= EVENT_ENRAGE;
    }

    // 客户端每 tick：技能计时与持续特效
    void clientTick() {
        int skill = this.spirit.getSpiritSkill();
        if (skill != this.clientSkill) {
            this.clientSkill = skill;
            this.clientTicks = 0;
            this.clientCleaveStart = -100;
            for (int i = 0; i < this.clientNovaStarts.length; i++) {
                this.clientNovaStarts[i] = -100;
            }
        }
        this.clientTicks++;
        int t = this.clientTicks;
        Level level = this.spirit.level();
        RandomSource random = this.spirit.getRandom();
        Vec3 forward = forward();
        Vec3 pos = this.spirit.position();
        switch (skill) {
            case CLEAVE -> {
                if (t < 18) {
                    // 蓄力：魂火向高举的剑汇聚
                    gather(level, random, pos.add(forward.scale(-0.6D)).add(0.0D, 4.2D, 0.0D), 2.5D, 2);
                }
                int age = t - this.clientCleaveStart;
                if (age >= 1 && age <= CLEAVE_LENGTH) {
                    // 裂地魂焰：逐格向前喷发，并震起沿途地面
                    Vec3 from = this.clientCleaveOrigin.add(this.clientCleaveDir.scale(age - 1));
                    Vec3 point = this.clientCleaveOrigin.add(this.clientCleaveDir.scale(age));
                    DragonSkillEntity.spawnQuakeLine(level, from.x, point.y, from.z, point.x, point.z, 1.2D, 0.35D, random);
                    flameColumn(level, random, point, 6);
                    if (age % 2 == 0) {
                        level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, point.x, point.y + 0.1D, point.z, 1.8D, 8.0D, 0.0D);
                    }
                }
            }
            case DASH -> {
                if (t <= DASH_START && t % 4 == 0) {
                    // 蓄势：前方突进路线预警
                    for (double d = 3.0D; d <= 18.0D; d += 3.0D) {
                        level.addAlwaysVisibleParticle(ModParticles.SPIRIT_WARNING.get(), true, pos.x + forward.x * d, pos.y + 0.1D, pos.z + forward.z * d, 1.2D, 6.0D, 0.0D);
                    }
                }
                if (t > DASH_START && t <= DASH_END + 1) {
                    // 突进拖尾：残影般的魂火与碎光
                    for (int i = 0; i < 4; i++) {
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x + random.nextGaussian() * 0.4D, pos.y + 0.5D + random.nextDouble() * 2.2D, pos.z + random.nextGaussian() * 0.4D, -forward.x * 0.15D, 0.02D, -forward.z * 0.15D);
                    }
                    level.addParticle(ParticleTypes.SOUL, pos.x, pos.y + 1.5D, pos.z, 0.0D, 0.03D, 0.0D);
                    level.addParticle(ModParticles.SPIRIT_SHARD.get(), pos.x, pos.y + 1.5D, pos.z, random.nextGaussian() * 0.1D, 0.15D, random.nextGaussian() * 0.1D);
                }
            }
            case SKYFALL -> {
                if (t < 18) {
                    // 举剑：魂火向剑尖汇聚
                    gather(level, random, pos.add(forward.scale(0.3D)).add(0.0D, 5.4D, 0.0D), 2.5D, 2);
                }
            }
            case RUNES -> {
                if (t < 10) {
                    gather(level, random, pos.add(forward.scale(-0.6D)).add(0.0D, 2.4D, 0.0D), 2.0D, 2);
                }
            }
            case NOVA -> {
                if (t < 20) {
                    // 升空蓄力：魂火绕身螺旋上升
                    for (int i = 0; i < 2; i++) {
                        double angle = t * 0.5D + i * Math.PI;
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x + Math.cos(angle) * 1.8D, pos.y + t * 0.15D, pos.z + Math.sin(angle) * 1.8D, 0.0D, 0.05D, 0.0D);
                    }
                }
                for (int start : this.clientNovaStarts) {
                    int age = t - start;
                    if (age < 0 || age > NOVA_LIFE) {
                        continue;
                    }
                    // 焰环：沿当前半径一圈魂火，与判定同步扩张
                    double radius = age * NOVA_SPEED;
                    int count = Mth.clamp((int) (radius * 2.5D), 8, 40);
                    double offset = random.nextDouble();
                    for (int i = 0; i < count; i++) {
                        double angle = (i + offset) / count * Math.PI * 2.0D;
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x + Math.cos(angle) * radius, pos.y + 0.2D, pos.z + Math.sin(angle) * radius, 0.0D, 0.06D, 0.0D);
                    }
                }
            }
            default -> {
            }
        }
    }

    // 粒子向某点汇聚
    private static void gather(Level level, RandomSource random, Vec3 center, double spread, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 from = center.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread);
            Vec3 velocity = center.subtract(from).scale(0.18D);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }

    // 一束向上喷发的魂焰
    private static void flameColumn(Level level, RandomSource random, Vec3 point, int count) {
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, point.x + random.nextGaussian() * 0.3D, point.y + 0.2D, point.z + random.nextGaussian() * 0.3D, 0.0D, 0.25D + random.nextDouble() * 0.25D, 0.0D);
        }
        level.addParticle(ModParticles.SPIRIT_SHARD.get(), point.x, point.y + 0.3D, point.z, random.nextGaussian() * 0.1D, 0.45D, random.nextGaussian() * 0.1D);
    }

    // 客户端实体事件：命中瞬间的特效
    void handleEvent(byte id) {
        Level level = this.spirit.level();
        RandomSource random = this.spirit.getRandom();
        Vec3 forward = forward();
        Vec3 pos = this.spirit.position();
        float yaw = this.spirit.yBodyRot;
        if (id == EVENT_SLASH_1 || id == EVENT_SLASH_2) {
            // 横斩剑气：水平月牙刀光 + 沿弧线甩出的魂火
            double mirror = 0.0D;
            double sweep = 1.0D;
            if (id == EVENT_SLASH_2) {
                mirror = 1.0D;
                sweep = -1.0D;
            }
            Vec3 center = pos.add(forward.scale(1.0D)).add(0.0D, 1.3D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_SLASH.get(), true, center.x, center.y, center.z, 4.5D, yaw, mirror);
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_SLASH.get(), true, center.x, center.y - 0.25D, center.z, 3.8D, yaw, mirror);
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            for (int i = 0; i < 16; i++) {
                double angle = Math.toRadians(-90.0D + i * 12.0D);
                Vec3 dir = forward.scale(Math.cos(angle)).add(right.scale(Math.sin(angle) * sweep));
                Vec3 at = pos.add(dir.scale(4.5D)).add(0.0D, 1.3D, 0.0D);
                Vec3 tangent = new Vec3(-dir.z, 0.0D, dir.x).scale(-0.25D * sweep);
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, tangent.x, 0.02D, tangent.z);
            }
            return;
        }
        if (id == EVENT_CLEAVE) {
            // 劈地：冲击环 + 震起剑落点周围地面 + 魂火迸溅，记录裂地起点
            Vec3 origin = pos.add(forward.scale(2.5D));
            this.clientCleaveOrigin = origin;
            this.clientCleaveDir = forward;
            this.clientCleaveStart = this.clientTicks;
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, origin.x, origin.y + 0.15D, origin.z, 5.0D, 12.0D, 0.0D);
            DragonSkillEntity.spawnQuakeRing(level, origin.x, origin.y, origin.z, 0.0D, 2.8D, 0.35D, random);
            flameColumn(level, random, origin, 16);
            level.addParticle(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.5D, origin.z, 0.0D, 0.0D, 0.0D);
            return;
        }
        if (id == EVENT_RUNES) {
            // 圣环迸发：背后光环处魂火四溅
            Vec3 halo = pos.add(forward.scale(-0.6D)).add(0.0D, 2.4D, 0.0D);
            for (int i = 0; i < 24; i++) {
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, halo.x, halo.y, halo.z, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D, random.nextGaussian() * 0.2D);
            }
            for (int i = 0; i < 6; i++) {
                level.addParticle(ModParticles.RUNESEFFECT.get(), halo.x + random.nextGaussian(), halo.y + random.nextGaussian() * 0.5D, halo.z + random.nextGaussian(), 0.0D, 0.05D, 0.0D);
            }
            return;
        }
        if (id == EVENT_DASH_BURST) {
            // 路径爆发：起点到当前位置每隔 1.5 格一束魂焰，并震起路线地面
            Vec3 start = this.spirit.getBossSkillPoint();
            Vec3 path = pos.subtract(start);
            double length = path.horizontalDistance();
            DragonSkillEntity.spawnQuakeLine(level, start.x, pos.y, start.z, pos.x, pos.z, 1.5D, 0.4D, random);
            for (double d = 0.0D; d <= length; d += 1.5D) {
                Vec3 point = start.add(path.scale(d / Math.max(0.01D, length)));
                flameColumn(level, random, new Vec3(point.x, pos.y, point.z), 5);
                level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, point.x, pos.y + 0.12D, point.z, 2.2D, 10.0D, 0.0D);
            }
            return;
        }
        if (id == EVENT_SKY_FLASH) {
            // 天光：剑尖射向天空的光柱 + 高空与地面魂焰环
            Vec3 tip = pos.add(forward.scale(0.3D)).add(0.0D, 5.4D, 0.0D);
            for (int i = 0; i < 40; i++) {
                level.addParticle(ParticleTypes.END_ROD, tip.x + random.nextGaussian() * 0.15D, tip.y + i * 0.5D, tip.z + random.nextGaussian() * 0.15D, 0.0D, 0.3D, 0.0D);
            }
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, tip.x, tip.y + 14.0D, tip.z, 12.0D, 30.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 6.0D, 14.0D, 0.0D);
            return;
        }
        if (id >= EVENT_NOVA && id < EVENT_NOVA + NOVA_STARTS.length) {
            // 焰环出现：匀速扩张的魂焰环贴图，与判定同速
            this.clientNovaStarts[id - EVENT_NOVA] = this.clientTicks;
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, NOVA_SPEED * NOVA_LIFE, NOVA_LIFE, 1.0D);
            DragonSkillEntity.spawnQuakeRing(level, pos.x, pos.y, pos.z, 0.0D, 2.0D, 0.3D, random);
            return;
        }
        if (id == EVENT_ENRAGE) {
            // 狂暴：双层魂焰冲击环 + 全身魂火爆发
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, pos.x, pos.y + 0.15D, pos.z, 9.0D, 16.0D, 0.0D);
            level.addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, pos.x, pos.y + 2.0D, pos.z, 6.0D, 12.0D, 0.0D);
            for (int i = 0; i < 40; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D + random.nextDouble() * 2.0D, pos.z, Math.cos(angle) * 0.4D, random.nextDouble() * 0.2D, Math.sin(angle) * 0.4D);
            }
            for (int i = 0; i < 12; i++) {
                level.addParticle(ModParticles.SPIRIT_SHARD.get(), pos.x, pos.y + 2.0D, pos.z, random.nextGaussian() * 0.25D, 0.4D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
            }
        }
    }

    // 战斗目标：有目标时接管移动与技能
    static final class CombatGoal extends Goal {
        private final RebornMobEntity spirit;

        CombatGoal(RebornMobEntity spirit) {
            this.spirit = spirit;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.spirit.getTarget();
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
            this.spirit.getSpiritCombat().stop();
            this.spirit.getNavigation().stop();
        }

        @Override
        public void tick() {
            this.spirit.getSpiritCombat().tick(this.spirit.getTarget());
        }
    }
}
