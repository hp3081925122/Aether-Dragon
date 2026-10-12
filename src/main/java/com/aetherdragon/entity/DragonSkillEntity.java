package com.aetherdragon.entity;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

// 艾瑟龙技能实体：羽刃、陨晶、雷击标记共用
public class DragonSkillEntity extends Entity {
    public static final int FEATHER = 1;
    public static final int CRYSTAL = 2;
    public static final int THUNDER = 3;
    public static final int SPIKE = 4;
    // 晶棘：突起后停留与收回的时间
    public static final int SPIKE_HOLD = 14;
    public static final int SPIKE_END = 22;
    // 雷击：预警时长与雷柱持续时间
    public static final int THUNDER_DELAY = 24;
    public static final int THUNDER_FLASH = 8;
    public static final double CRYSTAL_FALL_SPEED = 1.0D;
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(DragonSkillEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> GROUND_Y = SynchedEntityData.defineId(DragonSkillEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DELAY = SynchedEntityData.defineId(DragonSkillEntity.class, EntityDataSerializers.INT);
    private int ownerId = -1;
    private int targetId = -1;

    public DragonSkillEntity(EntityType<? extends DragonSkillEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // 生成技能实体并加入世界
    public static DragonSkillEntity spawn(ServerLevel level, LivingEntity owner, int kind, Vec3 pos, Vec3 velocity, double groundY, int delay, LivingEntity target) {
        DragonSkillEntity entity = ModEntities.DRAGON_SKILL.get().create(level);
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
        entity.faceVelocity();
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, FEATHER);
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
        switch (getKind()) {
            case FEATHER -> tickFeather();
            case CRYSTAL -> tickCrystal();
            case SPIKE -> tickSpike();
            default -> tickThunder();
        }
    }

    private void tickFeather() {
        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            // 前 15 tick 轻微追踪目标
            Entity target = level().getEntity(this.targetId);
            if (this.tickCount < 15 && target instanceof LivingEntity living && living.isAlive()) {
                Vec3 want = living.getEyePosition().subtract(position()).normalize().scale(velocity.length());
                velocity = velocity.lerp(want, 0.08D);
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
                hit.getEntity().hurt(skillDamage(), 6.0F);
                burst(hit.getLocation());
                return;
            }
            if (block.getType() != HitResult.Type.MISS || this.tickCount > 80) {
                burst(end);
                return;
            }
        } else if (this.tickCount % 2 == 0) {
            // 客户端拖尾
            level().addParticle(ModParticles.DRAGON_SHARD.get(), getX(), getY(), getZ(), velocity.x * -0.05D, 0.02D, velocity.z * -0.05D);
        }
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        faceVelocity();
    }

    private void tickCrystal() {
        if (level().isClientSide) {
            // 落点收缩预警环，寿命等于下落时间
            if (this.tickCount == 1) {
                int fallTicks = Math.max(4, Mth.ceil((getY() - getGroundY()) / CRYSTAL_FALL_SPEED));
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_WARNING.get(), true, getX(), getGroundY() + 0.1D, getZ(), 3.5D, fallTicks, 0.0D);
            }
            level().addParticle(ModParticles.DRAGON_SHARD.get(), getX() + (this.random.nextDouble() - 0.5D), getY() + 2.5D, getZ() + (this.random.nextDouble() - 0.5D), 0.0D, 0.15D, 0.0D);
        }
        setPos(getX(), getY() - CRYSTAL_FALL_SPEED, getZ());
        if (!level().isClientSide && getY() <= getGroundY()) {
            // 落地爆裂
            damageArea(new Vec3(getX(), getGroundY(), getZ()), 3.0D, 10.0F);
            ModSounds.play(level(), "dragon_crystal_impact", getX(), getGroundY(), getZ(), 3.0F, 1.0F);
            level().broadcastEntityEvent(this, (byte) 1);
            discard();
        }
    }

    private void tickThunder() {
        if (level().isClientSide) {
            // 预警：收缩金环 + 上升符文
            if (this.tickCount == 1) {
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 3.0D, THUNDER_DELAY, 1.0D);
            }
            if (this.tickCount < THUNDER_DELAY && this.tickCount % 3 == 0) {
                double angle = this.random.nextDouble() * Math.PI * 2.0D;
                level().addParticle(ModParticles.RUNESEFFECT.get(), getX() + Math.cos(angle) * 2.5D, getY() + 0.2D, getZ() + Math.sin(angle) * 2.5D, 0.0D, 0.06D, 0.0D);
            }
            return;
        }
        if (this.tickCount == THUNDER_DELAY) {
            // 雷击落下
            damageArea(position(), 2.5D, 12.0F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 1.5F);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0F, 1.0F);
            level().broadcastEntityEvent(this, (byte) 2);
        }
        if (this.tickCount > THUNDER_DELAY + THUNDER_FLASH + 2) {
            discard();
        }
    }

    private void tickSpike() {
        int delay = getDelay();
        if (level().isClientSide) {
            // 预警：小范围橙红收缩环，收到最小即突起
            if (this.tickCount == 1) {
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_WARNING.get(), true, getX(), getY() + 0.1D, getZ(), 1.6D, Math.max(4, delay), 0.0D);
            }
            return;
        }
        if (this.tickCount == delay) {
            // 晶棘突起：伤害并挑飞
            AABB box = new AABB(position(), position()).inflate(1.5D, 3.0D, 1.5D);
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
                living.hurt(skillDamage(), 9.0F);
                RebornMobEntity.applyBossKnockback(living, new Vec3(0.0D, 0.45D, 0.0D));
                living.hurtMarked = true;
            }
            ModSounds.play(level(), "dragon_spike_erupt", getX(), getY(), getZ(), 2.0F, 1.0F);
            level().broadcastEntityEvent(this, (byte) 4);
        }
        if (this.tickCount > delay + SPIKE_END) {
            discard();
        }
    }

    // 震起环带内的地面方块（客户端调用）：只取顶部露天的完整方块
    public static void spawnQuakeRing(Level level, double cx, double cy, double cz, double inner, double outer, double lift, RandomSource random) {
        int range = Mth.ceil(outer);
        int baseY = Mth.floor(cy - 0.5D);
        int bx0 = Mth.floor(cx);
        int bz0 = Mth.floor(cz);
        for (int bx = bx0 - range; bx <= bx0 + range; bx++) {
            for (int bz = bz0 - range; bz <= bz0 + range; bz++) {
                double dx = bx + 0.5D - cx;
                double dz = bz + 0.5D - cz;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < inner || distance > outer) {
                    continue;
                }
                quakeColumn(level, bx, baseY, bz, lift, random);
            }
        }
    }

    // 震起一条线段两侧 halfWidth 范围内的地面方块（客户端调用）
    public static void spawnQuakeLine(Level level, double x0, double y, double z0, double x1, double z1, double halfWidth, double lift, RandomSource random) {
        int baseY = Mth.floor(y - 0.5D);
        int minX = Mth.floor(Math.min(x0, x1) - halfWidth);
        int maxX = Mth.floor(Math.max(x0, x1) + halfWidth);
        int minZ = Mth.floor(Math.min(z0, z1) - halfWidth);
        int maxZ = Mth.floor(Math.max(z0, z1) + halfWidth);
        double sx = x1 - x0;
        double sz = z1 - z0;
        double lengthSqr = Math.max(1.0E-4D, sx * sx + sz * sz);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                double px = bx + 0.5D - x0;
                double pz = bz + 0.5D - z0;
                double t = Mth.clamp((px * sx + pz * sz) / lengthSqr, 0.0D, 1.0D);
                double dx = px - sx * t;
                double dz = pz - sz * t;
                if (dx * dx + dz * dz > halfWidth * halfWidth) {
                    continue;
                }
                // 越靠尾尖弹得越高
                quakeColumn(level, bx, baseY, bz, lift * (0.7D + 0.5D * t), random);
            }
        }
    }

    // 在一列中找到顶部露天的完整方块并震起
    private static void quakeColumn(Level level, int bx, int baseY, int bz, double lift, RandomSource random) {
        for (int y = baseY + 1; y >= baseY - 2; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.isCollisionShapeFullBlock(level, pos)) {
                continue;
            }
            if (level.getBlockState(pos.above()).isCollisionShapeFullBlock(level, pos.above())) {
                return;
            }
            level.addParticle(new BlockParticleOption(ModParticles.DRAGON_QUAKE_BLOCK.get(), state), bx, y, bz, 0.0D, lift * (0.8D + random.nextDouble() * 0.4D), 0.0D);
            return;
        }
    }

    // 晶棘高度比例：3 tick 冒出，停留后收回
    public float getSpikeHeight(float partialTicks) {
        float age = this.tickCount + partialTicks - getDelay();
        if (age <= 0.0F) {
            return 0.0F;
        }
        if (age < 3.0F) {
            return age / 3.0F;
        }
        if (age < SPIKE_HOLD) {
            return 1.0F;
        }
        return Math.max(0.0F, 1.0F - (age - SPIKE_HOLD) / (SPIKE_END - SPIKE_HOLD));
    }

    // 雷柱渲染进度：0 未落下，1 刚落下，逐渐衰减
    public float getThunderFlash(float partialTicks) {
        float age = this.tickCount + partialTicks - THUNDER_DELAY;
        if (age < 0.0F || age > THUNDER_FLASH) {
            return 0.0F;
        }
        return 1.0F - age / THUNDER_FLASH;
    }

    private void burst(Vec3 pos) {
        setPos(pos.x, pos.y, pos.z);
        ModSounds.play(level(), "dragon_feather_hit", pos.x, pos.y, pos.z, 1.5F, 1.0F);
        level().broadcastEntityEvent(this, (byte) 3);
        discard();
    }

    private void damageArea(Vec3 center, double radius, float amount) {
        AABB box = new AABB(center, center).inflate(radius, radius + 1.0D, radius);
        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            if (living.position().distanceToSqr(center) <= (radius + 0.5D) * (radius + 0.5D)) {
                living.hurt(skillDamage(), amount);
            }
        }
    }

    private boolean canHit(Entity entity) {
        return entity.isAlive() && entity.isPickable() && entity.getId() != this.ownerId && !(entity instanceof RebornMobEntity);
    }

    private DamageSource skillDamage() {
        return damageSources().indirectMagic(this, level().getEntity(this.ownerId));
    }

    private void faceVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6D) {
            return;
        }
        setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        this.yRotO = getYRot();
        this.xRotO = getXRot();
    }

    @Override
    public void handleEntityEvent(byte id) {
        RandomSource random = this.random;
        switch (id) {
            case 1 -> {
                // 陨晶落地：双层冲击波 + 碎晶
                double gy = getGroundY() + 0.15D;
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), gy, getZ(), 4.5D, 12.0D, 0.0D);
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), gy + 0.05D, getZ(), 2.5D, 8.0D, 0.0D);
                for (int i = 0; i < 24; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), getX(), gy + 0.3D, getZ(), Math.cos(angle) * 0.35D, 0.35D + random.nextDouble() * 0.45D, Math.sin(angle) * 0.35D);
                }
                level().addParticle(ParticleTypes.EXPLOSION, getX(), gy + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
            }
            case 2 -> {
                // 雷击：冲击波 + 向上溅射的金屑
                level().addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() + 0.15D, getZ(), 4.0D, 10.0D, 0.0D);
                for (int i = 0; i < 18; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), getX(), getY() + 0.3D, getZ(), Math.cos(angle) * 0.25D, 0.5D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.25D);
                }
            }
            case 4 -> {
                // 晶棘突起：周围地面方块被顶起 + 碎晶上溅
                spawnQuakeRing(level(), getX(), getY(), getZ(), 0.0D, 1.6D, 0.25D, random);
                for (int i = 0; i < 8; i++) {
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), getX(), getY() + 0.5D, getZ(), random.nextGaussian() * 0.15D, 0.4D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.15D);
                }
            }
            case 3 -> {
                // 羽刃命中：小范围碎鳞
                for (int i = 0; i < 8; i++) {
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), getX(), getY(), getZ(), random.nextGaussian() * 0.15D, 0.1D + random.nextDouble() * 0.2D, random.nextGaussian() * 0.15D);
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

