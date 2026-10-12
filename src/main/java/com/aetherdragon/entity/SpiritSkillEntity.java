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

// 灵魂技能实体：圣环符文弹、天剑
public class SpiritSkillEntity extends Entity {
    public static final int RUNE = 1;
    public static final int SWORD = 2;
    // 天剑：悬停高度、下落速度、落地后停留时间
    public static final double SWORD_HEIGHT = 14.0D;
    public static final double SWORD_FALL_SPEED = 1.6D;
    public static final int SWORD_STAY = 24;
    public static final double SWORD_SINK = 1.0D;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SpiritSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> GROUND_Y = SynchedEntityData.defineId(SpiritSkillEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(SpiritSkillEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;
    private int targetId = -1;

    public SpiritSkillEntity(EntityType<? extends SpiritSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成技能实体并加入世界
    public static SpiritSkillEntity spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, Vec3 velocity, double groundY, int delay, LivingEntity target) {
        SpiritSkillEntity entity = ModEntities.SPIRIT_SKILL.get().create(level);
        if (entity == null) {
            return null;
        }
        entity.entityData.set(KIND, kind);
        entity.entityData.set(GROUND_Y, (float) groundY);
        entity.entityData.set(DELAY, delay);
        entity.ownerId = owner.getId();
        if (target != null) {
            entity.targetId = target.getId();
        }
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setDeltaMovement(velocity);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, RUNE);
        builder.define(GROUND_Y, 0.0F);
        builder.define(DELAY, 0);
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public float getGroundY() {
        return this.entityData.get(GROUND_Y);
    }

    public int getDelay() {
        return this.entityData.get(DELAY);
    }

    @Override
    public void tick() {
        super.tick();
        if (getKind() == SWORD) {
            tickSword();
        } else {
            tickRune();
        }
    }

    private void tickRune() {
        int delay = getDelay();
        Vec3 velocity = getDeltaMovement();
        if (this.tickCount < delay) {
            // 浮现阶段：从光环向外散开后减速悬停
            velocity = velocity.scale(0.82D);
            setDeltaMovement(velocity);
            setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
            if (level().isClientSide && this.tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (this.random.nextDouble() - 0.5D) * 0.6D, getY() + (this.random.nextDouble() - 0.5D) * 0.6D, getZ() + (this.random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (!level().isClientSide) {
            Entity target = level().getEntity(this.targetId);
            if (this.tickCount == delay) {
                // 射出：直指目标胸口
                Vec3 aim = position().add(0.0D, 0.0D, 1.0D);
                if (target instanceof LivingEntity living && living.isAlive()) {
                    aim = living.position().add(0.0D, living.getBbHeight() * 0.6D, 0.0D);
                }
                velocity = aim.subtract(position()).normalize().scale(0.95D);
                setDeltaMovement(velocity);
                ModSounds.play(level(), "spirit_rune_launch", getX(), getY(), getZ(), 1.2F, 1.0F);
            } else if (this.tickCount < delay + 22 && target instanceof LivingEntity living && living.isAlive()) {
                // 射出后 22 tick 内轻微追踪
                Vec3 want = living.position().add(0.0D, living.getBbHeight() * 0.6D, 0.0D).subtract(position()).normalize().scale(velocity.length());
                velocity = velocity.lerp(want, 0.1D);
                setDeltaMovement(velocity);
            }
            Vec3 start = position();
            Vec3 end = start.add(velocity);
            // 先判定方块，再判定实体
            BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, start, end, getBoundingBox().expandTowards(velocity).inflate(0.6D), this::canHit);
            if (hit != null) {
                hit.getEntity().hurt(skillDamage(), 7.0F);
                burst(hit.getLocation());
                return;
            }
            if (block.getType() != HitResult.Type.MISS || this.tickCount > delay + 80) {
                burst(end);
                return;
            }
        } else {
            // 客户端拖尾：魂焰 + 碎光
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), velocity.x * -0.05D, 0.0D, velocity.z * -0.05D);
            if (this.tickCount % 2 == 0) {
                level().addParticle(ModParticles.SPIRIT_SHARD.get(), getX(), getY(), getZ(), velocity.x * -0.1D, 0.05D, velocity.z * -0.1D);
            }
        }
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }

    private void tickSword() {
        int delay = getDelay();
        int landTick = getSwordLandTick();
        // 位置由时间决定，客户端与服务端一致
        setPos(getX(), getSwordY(this.tickCount), getZ());
        if (level().isClientSide) {
            if (this.tickCount == 1) {
                // 落点收缩预警环，收到最小时天剑落地
                level().addAlwaysVisibleParticle(ModParticles.SPIRIT_WARNING.get(), true, getX(), getGroundY() + 0.1D, getZ(), 3.2D, landTick - 1, 0.0D);
            }
            if (this.tickCount < landTick && this.tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX() + (this.random.nextDouble() - 0.5D), getY() + 4.0D, getZ() + (this.random.nextDouble() - 0.5D), 0.0D, 0.05D, 0.0D);
            }
            if (this.tickCount > landTick && this.tickCount < landTick + SWORD_STAY - 6 && this.tickCount % 4 == 0) {
                // 插地后剑身周围飘散魂火
                level().addParticle(ParticleTypes.SOUL, getX() + (this.random.nextDouble() - 0.5D) * 1.4D, getGroundY() + 0.2D, getZ() + (this.random.nextDouble() - 0.5D) * 1.4D, 0.0D, 0.04D, 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            ModSounds.play(level(), "spirit_sword_fall", getX(), getGroundY() + SWORD_HEIGHT, getZ(), 2.0F, 1.0F);
        }
        if (this.tickCount == landTick) {
            // 天剑插地：范围伤害并轻微挑起
            Vec3 center = new Vec3(getX(), getGroundY(), getZ());
            AABB box = new AABB(center, center).inflate(3.2D, 3.0D, 3.2D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                if (living.position().distanceToSqr(center) <= 3.4D * 3.4D) {
                    living.hurt(skillDamage(), 12.0F);
                    RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.25D, 0.0D));
                    living.hurtMarked = true;
                }
            }
            ModSounds.play(level(), "spirit_sword_impact", getX(), getGroundY(), getZ(), 2.5F, 1.0F);
            level().broadcastEntityEvent(this, (byte) 1);
        }
        if (this.tickCount > landTick + SWORD_STAY) {
            discard();
        }
    }

    // 天剑落地 tick
    public int getSwordLandTick() {
        return getDelay() + Mth.ceil(SWORD_HEIGHT / SWORD_FALL_SPEED);
    }

    // 天剑尖端高度：悬停 → 匀速下落 → 插入地面
    public double getSwordY(float time) {
        float fall = time - getDelay();
        if (fall <= 0.0F) {
            return getGroundY() + SWORD_HEIGHT;
        }
        return Math.max(getGroundY() - SWORD_SINK, getGroundY() + SWORD_HEIGHT - fall * SWORD_FALL_SPEED);
    }

    // 天剑透明度：浮现淡入，停留末段淡出
    public float getSwordAlpha(float partialTicks) {
        float age = this.tickCount + partialTicks;
        float end = getSwordLandTick() + SWORD_STAY;
        if (age < 6.0F) {
            return age / 6.0F;
        }
        if (age > end - 8.0F) {
            return Mth.clamp((end - age) / 8.0F, 0.0F, 1.0F);
        }
        return 1.0F;
    }

    private void burst(Vec3 pos) {
        setPos(pos.x, pos.y, pos.z);
        ModSounds.play(level(), "spirit_rune_hit", pos.x, pos.y, pos.z, 1.5F, 1.0F);
        level().broadcastEntityEvent(this, (byte) 2);
        discard();
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.isPickable() && entity.getId() != this.ownerId && !(entity instanceof RebornMobEntity);
    }

    private DamageSource skillDamage() {
        return damageSources().indirectMagic(this, level().getEntity(this.ownerId));
    }

    @Override
    public void handleEntityEvent(byte id) {
        RandomSource random = this.random;
        switch (id) {
            case 1 -> {
                // 天剑插地：双层魂焰环 + 震起地面 + 魂火迸溅
                double gy = getGroundY() + 0.15D;
                level().addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, getX(), gy, getZ(), 4.5D, 12.0D, 0.0D);
                level().addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, getX(), gy + 0.05D, getZ(), 2.5D, 8.0D, 0.0D);
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getGroundY(), getZ(), 0.8D, 2.6D, 0.3D, random);
                for (int i = 0; i < 20; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), gy + 0.3D, getZ(), Math.cos(angle) * 0.25D, 0.15D + random.nextDouble() * 0.25D, Math.sin(angle) * 0.25D);
                }
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ModParticles.SPIRIT_SHARD.get(), getX(), gy + 0.5D, getZ(), random.nextGaussian() * 0.2D, 0.4D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.2D);
                }
            }
            case 2 -> {
                // 符文碎裂：小冲击环 + 魂火
                level().addAlwaysVisibleParticle(ModParticles.SPIRIT_RING.get(), true, getX(), getY(), getZ(), 1.6D, 6.0D, 0.0D);
                for (int i = 0; i < 10; i++) {
                    level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), random.nextGaussian() * 0.12D, random.nextGaussian() * 0.12D, random.nextGaussian() * 0.12D);
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

