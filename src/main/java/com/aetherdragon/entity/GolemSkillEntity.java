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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

// 水晶石傀儡技能实体：晶刺、晶笼、晶核，从地面破土后停留并碎裂
public class GolemSkillEntity extends Entity {
    public static final int SPIKE = 1;
    public static final int CAGE = 2;
    public static final int CORE = 3;
    public static final int RISE = 3;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GolemSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(GolemSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(GolemSkillEntity.class, EntityDataSerializers.FLOAT);
    private int ownerId = -1;
    private boolean silent;

    public GolemSkillEntity(EntityType<? extends GolemSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成晶体：pos 为地面位置，delay tick 后破土；silent 为真时不播放破土音效（成片生成时避免过吵）
    public static void spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, int delay, float scale, boolean silent) {
        GolemSkillEntity entity = ModEntities.GOLEM_SKILL.get().create(level);
        if (entity == null) {
            return;
        }
        entity.entityData.set(KIND, kind);
        entity.entityData.set(DELAY, delay);
        entity.entityData.set(SCALE, scale);
        entity.ownerId = owner.getId();
        entity.silent = silent;
        entity.setPos(pos.x, pos.y, pos.z);
        entity.setYRot(level.random.nextFloat() * 360.0F);
        level.addFreshEntity(entity);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(KIND, SPIKE);
        this.entityData.define(DELAY, 0);
        this.entityData.define(SCALE, 1.0F);
    }

    public int getKind() {
        return this.entityData.get(KIND);
    }

    public int getDelay() {
        return this.entityData.get(DELAY);
    }

    public float getScale() {
        return this.entityData.get(SCALE);
    }

    // 破土后停留时长：晶笼最久，晶核最短
    private int getHold() {
        int kind = getKind();
        if (kind == CAGE) {
            return 34;
        }
        if (kind == CORE) {
            return 12;
        }
        return 16;
    }

    @Override
    public void tick() {
        super.tick();
        int delay = getDelay();
        float scale = getScale();
        int end = delay + RISE + getHold();
        if (level().isClientSide) {
            if (this.tickCount == 1 && delay > 0) {
                // 预警：青色收缩环，收到最小时破土
                level().addAlwaysVisibleParticle(ModParticles.GOLEM_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 1.0D + scale * 0.8D, Math.max(4, delay), 0.0D);
            }
            if (this.tickCount < delay && this.tickCount % 3 == 0) {
                level().addParticle(ModParticles.GOLEM_SHARD.get(), getX() + (this.random.nextDouble() - 0.5D) * scale, getY() + 0.1D, getZ() + (this.random.nextDouble() - 0.5D) * scale, 0.0D, 0.15D, 0.0D);
            }
            if (getKind() == CAGE && this.tickCount > delay && this.tickCount < end && this.tickCount % 4 == 0) {
                // 晶笼：柱身向上飘散光点
                level().addParticle(ParticleTypes.END_ROD, getX() + (this.random.nextDouble() - 0.5D) * 0.6D, getY() + this.random.nextDouble() * 3.0D * scale, getZ() + (this.random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.03D, 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            erupt(scale);
        }
        if (getKind() == CAGE && this.tickCount > delay + RISE && this.tickCount < end && this.tickCount % 10 == 0) {
            // 晶笼：触碰柱身持续受伤
            damageAround(0.9D, scale, 4.0F, 0.0D);
        }
        if (this.tickCount >= end) {
            if (!this.silent || getKind() == CORE) {
                ModSounds.play(level(), "golem_shatter", getX(), getY() + 1.0D, getZ(), 0.8F + scale * 0.4F, 1.0F);
            }
            level().broadcastEntityEvent(this, (byte) 2);
            discard();
        }
    }

    private void erupt(float scale) {
        int kind = getKind();
        if (kind == CORE) {
            damageAround(3.0D, scale, 14.0F, 0.6D);
        } else if (kind == CAGE) {
            damageAround(0.9D, scale, 6.0F, 0.25D);
        } else {
            damageAround(0.8D + scale * 0.5D, scale, 10.0F, 0.4D);
        }
        if (!this.silent) {
            ModSounds.play(level(), "golem_spike", getX(), getY(), getZ(), 1.0F + scale * 0.5F, 1.2F - scale * 0.2F);
        }
        level().broadcastEntityEvent(this, (byte) 1);
    }

    // 范围伤害：水平半径 radius、高度随晶体大小
    private void damageAround(double radius, float scale, float amount, double lift) {
        AABB box = new AABB(position(), position()).inflate(radius, 0.0D, radius).expandTowards(0.0D, 3.0D * scale, 0.0D);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            double dx = living.getX() - getX();
            double dz = living.getZ() - getZ();
            if (dx * dx + dz * dz > (radius + 0.4D) * (radius + 0.4D)) {
                continue;
            }
            if (living.hurt(skillDamage(), amount) && lift > 0.0D) {
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, lift, 0.0D));
            }
        }
    }

    // 晶体露出比例：3 tick 冒出，之后保持
    public float getRise(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F) {
            return 0.0F;
        }
        return Math.min(1.0F, age / RISE);
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
        float scale = getScale();
        switch (id) {
            case 1 -> {
                // 破土：地面顶起 + 碎晶上溅
                DragonSkillEntity.spawnQuakeRing(level(), getX(), getY(), getZ(), 0.0D, 0.8D + scale * 0.8D, 0.22D + scale * 0.06D, random);
                int count = 4 + (int) (scale * 6.0F);
                for (int i = 0; i < count; i++) {
                    level().addParticle(ModParticles.GOLEM_SHARD.get(), getX(), getY() + 0.3D, getZ(), random.nextGaussian() * 0.15D, 0.35D + random.nextDouble() * 0.35D, random.nextGaussian() * 0.15D);
                }
                if (getKind() == CORE) {
                    level().addAlwaysVisibleParticle(ModParticles.GOLEM_RING.get(), true, getX(), getY() + 0.15D, getZ(), 5.0D, 12.0D, 0.0D);
                    level().addParticle(ParticleTypes.FLASH, getX(), getY() + 2.0D, getZ(), 0.0D, 0.0D, 0.0D);
                }
            }
            case 2 -> {
                // 碎裂：沿晶体高度迸散碎晶
                int count = 6 + (int) (scale * 8.0F);
                for (int i = 0; i < count; i++) {
                    double y = getY() + random.nextDouble() * 2.5D * scale;
                    level().addParticle(ModParticles.GOLEM_SHARD.get(), getX(), y, getZ(), random.nextGaussian() * 0.2D, random.nextDouble() * 0.25D, random.nextGaussian() * 0.2D);
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
