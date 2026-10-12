package com.aetherdragon.entity;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;

// 天境娜迦技能实体：迷宫石柱、巨石
public class NagaSkillEntity extends Entity {
    public static final int PILLAR = 1;
    public static final int BOULDER = 2;
    // 石柱：升起时长、停留时长、收回时长
    public static final int PILLAR_RISE = 3;
    public static final int PILLAR_HOLD = 30;
    public static final int PILLAR_SINK = 6;
    public static final double BOULDER_GRAVITY = 0.06D;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(NagaSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(NagaSkillEntity.class, EntityDataSerializers.INT);
    // 巨石落点，用于客户端预警环
    private static final EntityDataAccessor<Float> TARGET_X = SynchedEntityData.defineId(NagaSkillEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y = SynchedEntityData.defineId(NagaSkillEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z = SynchedEntityData.defineId(NagaSkillEntity.class, EntityDataSerializers.FLOAT);
    private int ownerId = -1;

    public NagaSkillEntity(EntityType<? extends NagaSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成石柱：pos 为地面位置，delay tick 后升起
    public static void spawnPillar(ServerLevel level, LivingEntity owner, Vec3 pos, int delay) {
        NagaSkillEntity entity = ModEntities.NAGA_SKILL.get().create(level);
        if (entity == null) {
            return;
        }
        entity.entityData.set(KIND, PILLAR);
        entity.entityData.set(DELAY, delay);
        entity.ownerId = owner.getId();
        entity.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(entity);
    }

    // 生成巨石：按飞行时长反推初速度，正好落在 landing
    public static void spawnBoulder(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 landing, int flightTicks) {
        NagaSkillEntity entity = ModEntities.NAGA_SKILL.get().create(level);
        if (entity == null) {
            return;
        }
        double t = flightTicks;
        Vec3 velocity = new Vec3((landing.x - from.x) / t, (landing.y - from.y) / t + BOULDER_GRAVITY * (t + 1.0D) * 0.5D, (landing.z - from.z) / t);
        entity.entityData.set(KIND, BOULDER);
        entity.entityData.set(DELAY, flightTicks);
        entity.entityData.set(TARGET_X, (float) landing.x);
        entity.entityData.set(TARGET_Y, (float) landing.y);
        entity.entityData.set(TARGET_Z, (float) landing.z);
        entity.ownerId = owner.getId();
        entity.setPos(from.x, from.y, from.z);
        entity.setDeltaMovement(velocity);
        level.addFreshEntity(entity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, PILLAR);
        builder.define(DELAY, 0);
        builder.define(TARGET_X, 0.0F);
        builder.define(TARGET_Y, 0.0F);
        builder.define(TARGET_Z, 0.0F);
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
        if (getKind() == BOULDER) {
            tickBoulder();
        } else {
            tickPillar();
        }
    }

    private void tickPillar() {
        int delay = getDelay();
        if (level().isClientSide) {
            if (this.tickCount == 1) {
                // 预警：紫色收缩环，收到最小时石柱升起
                level().addAlwaysVisibleParticle(ModParticles.NAGA_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 1.5D, Math.max(4, delay), 0.0D);
            }
            if (this.tickCount < delay && this.tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.PORTAL, getX() + (this.random.nextDouble() - 0.5D), getY() + 0.2D, getZ() + (this.random.nextDouble() - 0.5D), 0.0D, 0.3D, 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            // 石柱升起：伤害并挑飞
            AABB box = new AABB(position(), position()).inflate(1.3D, 0.0D, 1.3D).expandTowards(0.0D, 3.0D, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                living.hurt(skillDamage(), 10.0F);
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.25D, 0.0D));
            }
            ModSounds.play(level(), "naga_pillar", getX(), getY(), getZ(), 1.2F, 0.9F + this.random.nextFloat() * 0.2F);
            level().broadcastEntityEvent(this, (byte) 1);
        }
        if (this.tickCount > delay + PILLAR_RISE + PILLAR_HOLD + PILLAR_SINK) {
            discard();
        }
    }

    // 石柱露出地面的比例：升起 → 停留 → 收回
    public float getPillarHeight(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F) {
            return 0.0F;
        }
        if (age < PILLAR_RISE) {
            return age / PILLAR_RISE;
        }
        if (age < PILLAR_RISE + PILLAR_HOLD) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - PILLAR_RISE - PILLAR_HOLD) / PILLAR_SINK);
    }

    private void tickBoulder() {
        Vec3 velocity = getDeltaMovement();
        if (level().isClientSide) {
            if (this.tickCount == 1) {
                // 落点预警，寿命等于飞行时长
                level().addAlwaysVisibleParticle(ModParticles.NAGA_WARNING.get(), true, this.entityData.get(TARGET_X), this.entityData.get(TARGET_Y) + 0.1D, this.entityData.get(TARGET_Z), 3.0D, Math.max(4, getDelay()), 0.0D);
            }
            if (this.tickCount % 2 == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState()), getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
        } else {
            Vec3 start = position();
            Vec3 end = start.add(velocity);
            BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            boolean entityHit = !level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(velocity).inflate(0.4D), this::canHit).isEmpty();
            if (block.getType() != HitResult.Type.MISS || entityHit || this.tickCount > getDelay() + 40) {
                Vec3 at = end;
                if (block.getType() != HitResult.Type.MISS) {
                    at = block.getLocation();
                }
                impact(at);
                return;
            }
        }
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        setDeltaMovement(velocity.subtract(0.0D, BOULDER_GRAVITY, 0.0D));
    }

    private void impact(Vec3 at) {
        setPos(at.x, at.y, at.z);
        AABB box = new AABB(at, at).inflate(2.8D, 2.0D, 2.8D);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            if (living.position().distanceToSqr(at) <= 3.0D * 3.0D) {
                living.hurt(skillDamage(), 10.0F);
                Vec3 push = living.position().subtract(at);
                push = new Vec3(push.x, 0.0D, push.z).normalize().scale(0.3D);
                RebornMobEntity.applyBossKnockback(living, new Vec3(push.x, 0.2D, push.z));
            }
        }
        ModSounds.play(level(), "naga_boulder_impact", at.x, at.y, at.z, 2.5F, 1.0F);
        level().broadcastEntityEvent(this, (byte) 2);
        discard();
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.getId() != this.ownerId && !(entity instanceof RebornMobEntity) && !entity.isSpectator();
    }

    private DamageSource skillDamage() {
        return damageSources().indirectMagic(this, level().getEntity(this.ownerId));
    }

    @Override
    public void handleEntityEvent(byte id) {
        RandomSource random = this.random;
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        switch (id) {
            case 1 -> {
                // 石柱破土：周围地面被顶起 + 碎石
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.9D, 1.8D, 0.25D, random);
                for (int i = 0; i < 12; i++) {
                    level().addParticle(stone, getX() + random.nextGaussian() * 0.5D, getY() + 0.3D, getZ() + random.nextGaussian() * 0.5D, random.nextGaussian() * 0.15D, 0.3D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.15D);
                }
                level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.3D, getZ(), 0.0D, 0.05D, 0.0D);
            }
            case 2 -> {
                // 巨石砸落：秘符冲击环 + 震起地面 + 大量碎石
                level().addAlwaysVisibleParticle(ModParticles.NAGA_RING.get(), true, getX(), getY() + 0.15D, getZ(), 4.0D, 12.0D, 0.0D);
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.0D, 2.6D, 0.3D, random);
                for (int i = 0; i < 30; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level().addParticle(stone, getX(), getY() + 0.5D, getZ(), Math.cos(angle) * 0.35D, 0.3D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.35D);
                }
                level().addParticle(ParticleTypes.EXPLOSION, getX(), getY() + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
            }
            default -> super.handleEntityEvent(id);
        }
    }

    // 巨石自转角度
    public float getSpin(float partialTicks) {
        return (this.tickCount + partialTicks) * 18.0F;
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

