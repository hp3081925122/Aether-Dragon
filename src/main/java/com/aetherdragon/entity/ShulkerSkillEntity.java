package com.aetherdragon.entity;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraftforge.network.NetworkHooks;

// 远古潜影贝技能实体：追踪潜影弹、壳片碎刃、缠藤
public class ShulkerSkillEntity extends Entity {
    public static final int ORB = 1;
    public static final int SHARD = 2;
    public static final int VINE = 3;
    // 缠藤：破土后停留时长与高度
    public static final int VINE_HOLD = 40;
    public static final float VINE_HEIGHT = 3.0F;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ShulkerSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(ShulkerSkillEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;
    private int targetId = -1;
    private boolean silent;

    public ShulkerSkillEntity(EntityType<? extends ShulkerSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成技能实体；silent 为真时不播放出现音效
    public static void spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, Vec3 velocity, int delay, LivingEntity target, boolean silent) {
        ShulkerSkillEntity entity = ModEntities.SHULKER_SKILL.get().create(level);
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
        entity.faceVelocity();
        level.addFreshEntity(entity);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, ORB);
        this.entityData.define(DELAY, 0);
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
        if (getKind() == VINE) {
            tickVine();
        } else {
            tickProjectile();
        }
    }

    private void tickProjectile() {
        boolean orb = getKind() == ORB;
        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            Entity target = level().getEntity(this.targetId);
            if (orb && this.tickCount > 6 && this.tickCount < 70 && target instanceof LivingEntity living && living.isAlive()) {
                // 潜影弹：先向外飞散，再缓慢转向目标
                Vec3 want = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(position()).normalize().scale(0.42D);
                velocity = velocity.lerp(want, 0.08D);
                setDeltaMovement(velocity);
            }
            if (!orb) {
                // 碎刃：受轻微重力
                velocity = velocity.subtract(0.0D, 0.015D, 0.0D);
                setDeltaMovement(velocity);
            }
            Vec3 start = position();
            Vec3 end = start.add(velocity);
            BlockHitResult block = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (block.getType() != HitResult.Type.MISS) {
                end = block.getLocation();
            }
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, start, end, getBoundingBox().expandTowards(velocity).inflate(0.4D), this::canHit);
            if (hit != null && hit.getEntity() instanceof LivingEntity living) {
                if (orb) {
                    // 潜影弹命中：伤害并短暂浮空
                    living.hurt(skillDamage(), 5.0F);
                    living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 0));
                } else {
                    living.hurt(skillDamage(), 6.0F);
                }
                burst(hit.getLocation());
                return;
            }
            if (block.getType() != HitResult.Type.MISS || this.tickCount > 100) {
                burst(end);
                return;
            }
        } else {
            // 客户端拖尾
            if (orb) {
                level().addParticle(ModParticles.SHULKER_SPARK.get(), getX(), getY(), getZ(), velocity.x * -0.1D, 0.0D, velocity.z * -0.1D);
                if (this.tickCount % 2 == 0) {
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
                }
            } else if (this.tickCount % 2 == 0) {
                level().addParticle(ModParticles.SHULKER_SPARK.get(), getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            }
        }
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        faceVelocity();
    }

    private void burst(Vec3 at) {
        setPos(at.x, at.y, at.z);
        if (getKind() == ORB || this.random.nextInt(3) == 0) {
            ModSounds.play(level(), "shulker_orb_hit", at.x, at.y, at.z, 1.0F, 1.0F);
        }
        level().broadcastEntityEvent(this, (byte) 1);
        discard();
    }

    private void tickVine() {
        int delay = getDelay();
        int end = delay + VINE_HOLD;
        if (level().isClientSide) {
            if (this.tickCount == 1) {
                // 预警：金青收缩环 + 地面草屑
                level().addAlwaysVisibleParticle(ModParticles.SHULKER_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 1.6D, Math.max(4, delay), 0.0D);
            }
            if (this.tickCount < delay && this.tickCount % 3 == 0) {
                level().addParticle(ParticleTypes.COMPOSTER, getX() + (this.random.nextDouble() - 0.5D) * 1.5D, getY() + 0.2D, getZ() + (this.random.nextDouble() - 0.5D) * 1.5D, 0.0D, 0.05D, 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            // 缠藤破土：伤害并缠住（强力减速）
            AABB box = new AABB(position(), position()).inflate(1.4D, 0.0D, 1.4D).expandTowards(0.0D, VINE_HEIGHT, 0.0D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                living.hurt(skillDamage(), 7.0F);
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3));
                living.setDeltaMovement(Vec3.ZERO);
                living.hurtMarked = true;
            }
            if (!this.silent) {
                ModSounds.play(level(), "shulker_vine", getX(), getY(), getZ(), 1.5F, 1.0F);
            }
            level().broadcastEntityEvent(this, (byte) 2);
        }
        if (this.tickCount > end) {
            discard();
        }
    }

    // 缠藤高度：4 tick 冒出，最后 6 tick 缩回
    public float getVineHeight(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F || age >= VINE_HOLD) {
            return 0.0F;
        }
        if (age < 4.0F) {
            return VINE_HEIGHT * age / 4.0F;
        }
        if (age > VINE_HOLD - 6.0F) {
            return VINE_HEIGHT * (VINE_HOLD - age) / 6.0F;
        }
        return VINE_HEIGHT;
    }

    private void faceVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6D) {
            return;
        }
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
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
                // 弹体碎裂：小冲击环 + 金色碎光
                level().addAlwaysVisibleParticle(ModParticles.SHULKER_RING.get(), true, getX(), getY(), getZ(), 1.5D, 6.0D, 0.0D);
                for (int i = 0; i < 8; i++) {
                    level().addParticle(ModParticles.SHULKER_SPARK.get(), getX(), getY(), getZ(), random.nextGaussian() * 0.15D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.15D);
                }
            }
            case 2 -> {
                // 缠藤破土：地面翻起 + 草叶飞散
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.0D, 1.5D, 0.22D, random);
                for (int i = 0; i < 12; i++) {
                    level().addParticle(ParticleTypes.COMPOSTER, getX() + random.nextGaussian() * 0.6D, getY() + random.nextDouble() * 2.5D, getZ() + random.nextGaussian() * 0.6D, 0.0D, 0.05D, 0.0D);
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
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
