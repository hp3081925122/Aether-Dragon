package com.aetherdragon.entity;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;

import java.util.HashSet;
import java.util.Set;

// 盐蟹王技能实体：泡泡弹、盐泉水柱、潮汐浪墙、盐晶
public class CrabSkillEntity extends Entity {
    public static final int BUBBLE = 1;
    public static final int GEYSER = 2;
    public static final int WAVE = 3;
    public static final int SALT = 4;
    // 水柱：喷发持续时长与最大高度
    public static final int GEYSER_HOLD = 14;
    public static final float GEYSER_HEIGHT = 4.5F;
    // 浪墙：推进速度、持续时长、半宽与高度
    public static final double WAVE_SPEED = 0.55D;
    public static final int WAVE_LIFE = 28;
    public static final float WAVE_HALF_WIDTH = 4.5F;
    public static final float WAVE_HEIGHT = 2.6F;
    // 盐晶：停留时长
    public static final int SALT_HOLD = 20;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(CrabSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(CrabSkillEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;
    private int targetId = -1;
    private boolean silent;
    private final Set<Integer> waveHits = new HashSet<>();

    public CrabSkillEntity(EntityType<? extends CrabSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成技能实体；yaw 决定浪墙推进方向，silent 为真时不播放出现音效
    public static void spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, Vec3 velocity, float yaw, int delay, LivingEntity target, boolean silent) {
        CrabSkillEntity entity = ModEntities.CRAB_SKILL.get().create(level);
        if (entity == null) {
            return;
        }
        entity.entityData.set(KIND, kind);
        entity.entityData.set(DELAY, delay);
        entity.ownerId = owner.getId();
        if (target != null) {
            entity.targetId = target.getId();
        }
        entity.silent = silent;
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(velocity);
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        level.addFreshEntity(entity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, BUBBLE);
        builder.define(DELAY, 0);
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public int getDelay() {
        return this.entityData.get(DELAY);
    }

    @Override
    public void tick() {
        super.tick();
        switch (getKind()) {
            case GEYSER -> tickGeyser();
            case WAVE -> tickWave();
            case SALT -> tickSalt();
            default -> tickBubble();
        }
    }

    private void tickBubble() {
        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            // 前 30 tick 缓慢追踪目标
            Entity target = level().getEntity(this.targetId);
            if (this.tickCount < 30 && target instanceof LivingEntity living && living.isAlive()) {
                Vec3 want = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(position()).normalize().scale(velocity.length());
                velocity = velocity.lerp(want, 0.06D);
                setDeltaMovement(velocity);
            }
            Vec3 start = position();
            Vec3 end = start.add(velocity);
            BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, start, end, getBoundingBox().expandTowards(velocity).inflate(0.5D), this::canHit);
            if (hit != null && hit.getEntity() instanceof LivingEntity living) {
                // 命中：伤害并减速
                living.hurt(skillDamage(), 6.0F);
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                pop(hit.getLocation());
                return;
            }
            if (block.getType() != HitResult.Type.MISS || this.tickCount > 80) {
                pop(end);
                return;
            }
        } else if (this.tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    private void pop(Vec3 at) {
        setPos(at.x, at.y, at.z);
        ModSounds.play(level(), "crab_bubble_pop", at.x, at.y, at.z, 1.2F, 1.0F);
        level().broadcastEntityEvent(this, (byte) 1);
        discard();
    }

    private void tickGeyser() {
        int delay = getDelay();
        int end = delay + GEYSER_HOLD;
        if (level().isClientSide) {
            if (this.tickCount == 1) {
                // 预警：金色收缩环 + 地面冒出光点
                level().addAlwaysVisibleParticle(ModParticles.SALT_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 2.2D, Math.max(4, delay), 0.0D);
            }
            if (this.tickCount < delay && this.tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (this.random.nextDouble() - 0.5D) * 2.0D, getY() + 0.1D, getZ() + (this.random.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.1D, 0.0D);
                level().addParticle(ModParticles.SALT_SPRAY.get(), getX() + (this.random.nextDouble() - 0.5D) * 2.0D, getY() + 0.1D, getZ() + (this.random.nextDouble() - 0.5D) * 2.0D, 0.0D, 0.1D, 0.0D);
            }
            if (this.tickCount >= delay && this.tickCount < end) {
                // 喷发：柱顶盐沫四溅
                float height = getGeyserHeight(0.0F);
                for (int i = 0; i < 4; i++) {
                    level().addParticle(ModParticles.SALT_SPRAY.get(), getX() + this.random.nextGaussian() * 0.5D, getY() + height, getZ() + this.random.nextGaussian() * 0.5D, this.random.nextGaussian() * 0.2D, 0.3D, this.random.nextGaussian() * 0.2D);
                }
                level().addParticle(ModParticles.SALT_SPRAY.get(), getX() + this.random.nextGaussian() * 0.8D, getY() + height, getZ() + this.random.nextGaussian() * 0.8D, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            // 喷发：伤害并高高托起
            AABB box = new AABB(position(), position()).inflate(1.6D, 0.0D, 1.6D).expandTowards(0.0D, GEYSER_HEIGHT, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                living.hurt(skillDamage(), 9.0F);
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.65D, 0.0D));
                living.hurtMarked = true;
            }
            if (!this.silent) {
                ModSounds.play(level(), "crab_geyser", getX(), getY(), getZ(), 2.0F, 1.0F);
            }
            level().broadcastEntityEvent(this, (byte) 2);
        }
        if (this.tickCount > delay && this.tickCount < end && this.tickCount % 4 == 0) {
            // 水柱持续期间把柱内目标往上推
            AABB box = new AABB(position(), position()).inflate(1.3D, 0.0D, 1.3D).expandTowards(0.0D, GEYSER_HEIGHT, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.175D, 0.0D));
                living.hurtMarked = true;
            }
        }
        if (this.tickCount > end) {
            discard();
        }
    }

    // 水柱高度：3 tick 冲起，最后 4 tick 回落
    public float getGeyserHeight(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F || age >= GEYSER_HOLD) {
            return 0.0F;
        }
        if (age < 3.0F) {
            return GEYSER_HEIGHT * age / 3.0F;
        }
        if (age > GEYSER_HOLD - 4.0F) {
            return GEYSER_HEIGHT * (GEYSER_HOLD - age) / 4.0F;
        }
        return GEYSER_HEIGHT;
    }

    // 浪墙推进方向
    public Vec3 getWaveDirection() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    private void tickWave() {
        Vec3 dir = getWaveDirection();
        if (level().isClientSide) {
            // 浪尖盐沫与光点
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
            for (int i = 0; i < 6; i++) {
                double l = (this.random.nextDouble() * 2.0D - 1.0D) * WAVE_HALF_WIDTH;
                double back = l * l / (WAVE_HALF_WIDTH * WAVE_HALF_WIDTH) * 1.2D;
                Vec3 at = position().add(side.scale(l)).subtract(dir.scale(back));
                level().addParticle(ModParticles.SALT_SPRAY.get(), at.x, at.y + getWaveHeight(0.0F), at.z, dir.x * 0.3D, 0.2D, dir.z * 0.3D);
                if (i == 0) {
                    // 光点存在时间长，每 tick 只冒一个
                    level().addParticle(ParticleTypes.END_ROD, at.x, at.y + 0.2D, at.z, 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            // 推进：命中浪前的目标，每个目标只结算一次
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
            AABB box = new AABB(position(), position()).inflate(WAVE_HALF_WIDTH + 1.0D, 0.0D, WAVE_HALF_WIDTH + 1.0D).expandTowards(0.0D, WAVE_HEIGHT, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                Vec3 offset = living.position().subtract(position());
                double lateral = offset.dot(side);
                double back = lateral * lateral / (WAVE_HALF_WIDTH * WAVE_HALF_WIDTH) * 1.2D;
                double along = offset.dot(dir) + back;
                if (Math.abs(lateral) > WAVE_HALF_WIDTH || along < -1.2D || along > 1.2D || offset.y > WAVE_HEIGHT) {
                    continue;
                }
                if (!this.waveHits.add(living.getId())) {
                    continue;
                }
                living.hurt(skillDamage(), 10.0F);
                RebornMobEntity.applyBossKnockback(living, new Vec3(dir.x * 0.8D, 0.25D, dir.z * 0.8D));
                living.hurtMarked = true;
            }
            if (this.tickCount % 8 == 0) {
                ModSounds.play(level(), "crab_wave", getX(), getY(), getZ(), 1.5F, 1.0F);
            }
            if (this.tickCount > WAVE_LIFE) {
                discard();
                return;
            }
        }
        // 贴着地面推进
        double nx = getX() + dir.x * WAVE_SPEED;
        double nz = getZ() + dir.z * WAVE_SPEED;
        double ny = getY();
        BlockHitResult ground = level().clip(new ClipContext(new Vec3(nx, ny + 1.5D, nz), new Vec3(nx, ny - 3.0D, nz), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (ground.getType() != HitResult.Type.MISS) {
            ny = ground.getLocation().y;
        }
        setPos(nx, ny, nz);
    }

    // 浪墙高度：4 tick 涌起，最后 6 tick 退落
    public float getWaveHeight(float partialTicks) {
        float age = this.tickCount + partialTicks;
        if (age < 4.0F) {
            return WAVE_HEIGHT * age / 4.0F;
        }
        if (age > WAVE_LIFE - 6.0F) {
            return WAVE_HEIGHT * Math.max(0.0F, WAVE_LIFE - age) / 6.0F;
        }
        return WAVE_HEIGHT;
    }

    private void tickSalt() {
        int delay = getDelay();
        int end = delay + 3 + SALT_HOLD;
        if (level().isClientSide) {
            if (this.tickCount == 1 && delay > 0) {
                level().addAlwaysVisibleParticle(ModParticles.SALT_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 1.4D, Math.max(4, delay), 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            // 盐晶破土：伤害并挑起
            AABB box = new AABB(position(), position()).inflate(1.2D, 0.0D, 1.2D).expandTowards(0.0D, 2.5D, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                living.hurt(skillDamage(), 9.0F);
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.35D, 0.0D));
                living.hurtMarked = true;
            }
            if (!this.silent) {
                ModSounds.play(level(), "crab_salt", getX(), getY(), getZ(), 1.5F, 1.0F);
            }
            level().broadcastEntityEvent(this, (byte) 4);
        }
        if (this.tickCount >= end) {
            level().broadcastEntityEvent(this, (byte) 5);
            discard();
        }
    }

    // 盐晶露出比例
    public float getSaltRise(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, age / 3.0F);
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.isPickable() && entity.getId() != this.ownerId && !(entity instanceof RebornMobEntity) && !entity.isSpectator();
    }

    private DamageSource skillDamage() {
        return damageSources().indirectMagic(this, level().getEntity(this.ownerId));
    }

    @Override
    public void handleEntityEvent(byte id) {
        RandomSource random = this.random;
        switch (id) {
            case 1 -> {
                // 泡泡破裂：盐沫 + 小冲击环
                level().addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, getX(), getY(), getZ(), 1.8D, 6.0D, 0.0D);
                for (int i = 0; i < 12; i++) {
                    level().addParticle(ModParticles.SALT_SPRAY.get(), getX(), getY(), getZ(), random.nextGaussian() * 0.25D, random.nextDouble() * 0.3D, random.nextGaussian() * 0.25D);
                }
                level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
            case 2 -> {
                // 盐泉喷发：冲击环 + 震起地面 + 盐粒
                level().addAlwaysVisibleParticle(ModParticles.SALT_RING.get(), true, getX(), getY() + 0.15D, getZ(), 3.0D, 10.0D, 0.0D);
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.6D, 2.0D, 0.28D, random);
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ModParticles.SALT_SHARD.get(), getX(), getY() + 0.5D, getZ(), random.nextGaussian() * 0.2D, 0.5D + random.nextDouble() * 0.4D, random.nextGaussian() * 0.2D);
                }
            }
            case 4 -> {
                // 盐晶破土
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.0D, 1.3D, 0.22D, random);
                for (int i = 0; i < 6; i++) {
                    level().addParticle(ModParticles.SALT_SHARD.get(), getX(), getY() + 0.3D, getZ(), random.nextGaussian() * 0.15D, 0.35D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.15D);
                }
            }
            case 5 -> {
                // 盐晶碎裂
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ModParticles.SALT_SHARD.get(), getX(), getY() + random.nextDouble() * 2.5D, getZ(), random.nextGaussian() * 0.18D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.18D);
                }
            }
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 16384.0D;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}

