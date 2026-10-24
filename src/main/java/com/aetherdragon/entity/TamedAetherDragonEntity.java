package com.aetherdragon.entity;

import com.aetherdragon.DragonCombatConfig;
import com.aetherdragon.client.DragonMountControls;
import com.aetherdragon.item.AetherDragonFluteItem;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class TamedAetherDragonEntity extends PathfinderMob implements GeoEntity {
    private static final System.Logger LOGGER = System.getLogger(TamedAetherDragonEntity.class.getName());
    public static final String TOKEN_TAG = "AetherDragonMountToken";
    public static final String RESPAWN_TAG = "AetherDragonMountRespawnAt";
    private static final String OWNER_TAG = "DragonOwner";
    private static final String SAVED_TOKEN_TAG = "DragonSummonToken";
    private static final double SEAT_HEIGHT_AIR = 2.38D;
    private static final double SEAT_HEIGHT_GROUND = 2.17D;
    private static final double SEAT_FORWARD = 0.28D;
    private static final double RIDER_OFFSET = -0.35D;
    private static final EntityDataAccessor<Boolean> LASER = SynchedEntityData.defineId(TamedAetherDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LASER_X = SynchedEntityData.defineId(TamedAetherDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LASER_Y = SynchedEntityData.defineId(TamedAetherDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LASER_Z = SynchedEntityData.defineId(TamedAetherDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final RawAnimation RIDING = RawAnimation.begin().thenLoop("animation.aether_dragon.riding");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.aether_dragon.walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.aether_dragon.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private long summonToken;
    private boolean flying;

    public TamedAetherDragonEntity(EntityType<? extends TamedAetherDragonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LASER, false);
        builder.define(LASER_X, 0.0F);
        builder.define(LASER_Y, 0.0F);
        builder.define(LASER_Z, 0.0F);
    }

    public boolean isLaserActive() {
        return this.entityData.get(LASER);
    }

    public Vec3 getLaserEnd() {
        return new Vec3(this.entityData.get(LASER_X), this.entityData.get(LASER_Y), this.entityData.get(LASER_Z));
    }

    public void setLaserFiring(boolean firing) {
        if (isLaserActive() != firing) {
            LOGGER.log(System.Logger.Level.DEBUG, "Tamed Aether Dragon {0} laser firing set to {1}", getUUID(), firing);
        }
        this.entityData.set(LASER, firing);
    }

    private Vec3 getMouthPosition() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 5.0D, 3.2D, Mth.cos(yaw) * 5.0D);
    }

    private void tickLaser(ServerLevel server, Player rider) {
        Vec3 mouth = getMouthPosition();
        Vec3 far = mouth.add(rider.getLookAngle().scale(DragonCombatConfig.MOUNT_LASER_RANGE.get()));
        BlockHitResult hit = server.clip(new ClipContext(mouth, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = far;
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getLocation();
        }
        this.entityData.set(LASER_X, (float) end.x);
        this.entityData.set(LASER_Y, (float) end.y);
        this.entityData.set(LASER_Z, (float) end.z);
        if (this.tickCount % 4 == 0) {
            Vec3 segment = end.subtract(mouth);
            double lengthSqr = Math.max(1.0E-4D, segment.lengthSqr());
            float damage = (float) (DragonCombatConfig.MOUNT_LASER_DAMAGE.get() + rider.getAttributeValue(Attributes.ATTACK_DAMAGE) * DragonCombatConfig.MOUNT_LASER_ATTACK_SCALE.get());
            for (LivingEntity living : server.getEntitiesOfClass(LivingEntity.class, new AABB(mouth, end).inflate(1.6D), living -> canLaserHit(living, rider))) {
                Vec3 center = living.getBoundingBox().getCenter();
                double t = Mth.clamp(center.subtract(mouth).dot(segment) / lengthSqr, 0.0D, 1.0D);
                if (mouth.add(segment.scale(t)).distanceToSqr(center) < 2.6D) {
                    living.hurtServer(server, damageSources().indirectMagic(this, rider), damage);
                }
            }
        }
        if (this.tickCount % 12 == 0) {
            server.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0F, 0.7F);
        }
    }

    private boolean canLaserHit(LivingEntity living, Player rider) {
        if (living == this || living == rider || !living.isAlive() || hasPassenger(living)) {
            return false;
        }
        return this.ownerId == null || !this.ownerId.equals(living.getUUID());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    public static CompoundTag getPersistedData(Player player) {
        CompoundTag data = player.getPersistentData();
        Optional<CompoundTag> existing = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (existing.isPresent()) {
            return existing.get();
        }
        CompoundTag created = new CompoundTag();
        data.put(Player.PERSISTED_NBT_TAG, created);
        return created;
    }

    public void bindOwner(ServerPlayer owner, long token) {
        this.ownerId = owner.getUUID();
        this.summonToken = token;
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(DragonCombatConfig.MOUNT_MAX_HEALTH.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(DragonCombatConfig.MOUNT_WALK_SPEED.get());
        setHealth(getMaxHealth());
    }

    public boolean isOwnedBy(Player player) {
        return this.ownerId != null && this.ownerId.equals(player.getUUID());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof AetherDragonFluteItem) {
            return InteractionResult.PASS;
        }
        if (isVehicle() || player.isSecondaryUseActive() || !isOwnedBy(player)) {
            return super.mobInteract(player, hand);
        }
        if (!level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (getFirstPassenger() instanceof Player player) {
            return player;
        }
        return super.getControllingPassenger();
    }

    @Override
    public boolean isNoGravity() {
        return getControllingPassenger() instanceof Player || super.isNoGravity();
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        super.tickRidden(player, input);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        this.yRotO = getYRot();
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 input) {
        float forward = player.zza;
        if (forward < 0.0F) {
            forward *= 0.4F;
        }
        return new Vec3(player.xxa * 0.6F, 0.0D, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public void travel(Vec3 input) {
        if (!(getControllingPassenger() instanceof Player player) || !canSimulateMovement()) {
            super.travel(input);
            return;
        }
        boolean ascending = false;
        boolean descending = false;
        if (level().isClientSide()) {
            ascending = DragonMountControls.isAscending();
            descending = DragonMountControls.isDescending();
        }
        Vec3 look = player.getLookAngle();
        if (ascending || (input.z > 0.0D && look.y > 0.35D)) {
            this.flying = true;
        }
        if (!this.flying) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.08D, 0.0D));
            super.travel(input);
            return;
        }
        double speed = DragonCombatConfig.MOUNT_FLY_SPEED.get();
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        Vec3 heading = look;
        if (heading.y < 0.0D) {
            heading = new Vec3(heading.x, 0.0D, heading.z).normalize();
        }
        Vec3 target = heading.scale(input.z * speed).add(left.scale(input.x * speed));
        if (ascending) {
            target = target.add(0.0D, speed * 0.6D, 0.0D);
        }
        if (descending) {
            target = target.add(0.0D, -speed * 0.6D, 0.0D);
        }
        setDeltaMovement(getDeltaMovement().lerp(target, 0.15D));
        move(MoverType.SELF, getDeltaMovement());
        if (onGround() && getDeltaMovement().y <= 0.0D && !ascending) {
            this.flying = false;
        }
        calculateEntityAnimation(true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (isVehicle()) {
            return;
        }
        this.flying = false;
        Vec3 motion = getDeltaMovement();
        if (!onGround() && motion.y < -0.3D) {
            setDeltaMovement(motion.x, -0.3D, motion.z);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            if (isLaserActive()) {
                Vec3 end = getLaserEnd();
                for (int i = 0; i < 2; i++) {
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), end.x, end.y + 0.2D, end.z, getRandom().nextGaussian() * 0.2D, 0.3D + getRandom().nextDouble() * 0.3D, getRandom().nextGaussian() * 0.2D);
                }
                level().addParticle(ModParticles.WHITESMOKE.get(), end.x, end.y + 0.3D, end.z, 0.0D, 0.05D, 0.0D);
                if (this.tickCount % 5 == 0) {
                    level().addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, end.x, end.y + 0.1D, end.z, 3.0D, 8.0D, 0.0D);
                }
            }
            return;
        }
        if (isLaserActive()) {
            if (getControllingPassenger() instanceof Player rider) {
                tickLaser(server, rider);
            } else {
                setLaserFiring(false);
            }
        }
        if (this.tickCount % 20 != 0 || this.ownerId == null) {
            return;
        }
        ServerPlayer owner = server.getServer().getPlayerList().getPlayer(this.ownerId);
        if (owner != null && getPersistedData(owner).getLongOr(TOKEN_TAG, 0L) != this.summonToken) {
            LOGGER.log(System.Logger.Level.DEBUG, "Tamed Aether Dragon {0} discarded because its summon token is outdated", getUUID());
            ejectPassengers();
            discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server) || this.ownerId == null) {
            return;
        }
        MinecraftServer minecraftServer = server.getServer();
        ServerPlayer owner = minecraftServer.getPlayerList().getPlayer(this.ownerId);
        if (owner == null) {
            return;
        }
        CompoundTag persisted = getPersistedData(owner);
        if (persisted.getLongOr(TOKEN_TAG, 0L) != this.summonToken) {
            return;
        }
        int seconds = DragonCombatConfig.MOUNT_RESPAWN_SECONDS.get();
        persisted.putLong(RESPAWN_TAG, minecraftServer.overworld().getGameTime() + seconds * 20L);
        owner.sendSystemMessage(Component.translatable("message.aether_dragon.mount.died", seconds).withStyle(ChatFormatting.RED));
        LOGGER.log(System.Logger.Level.DEBUG, "Tamed Aether Dragon {0} died, owner {1} respawn cooldown {2}s", getUUID(), owner.getName().getString(), seconds);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!hasPassenger(passenger)) {
            return;
        }
        double height = SEAT_HEIGHT_AIR;
        if (onGround()) {
            height = SEAT_HEIGHT_GROUND;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        moveFunction.accept(passenger, getX() - Mth.sin(yaw) * SEAT_FORWARD, getY() + height + RIDER_OFFSET, getZ() + Mth.cos(yaw) * SEAT_FORWARD);
    }

    @Override
    public boolean hurtServer(ServerLevel server, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (hasPassenger(attacker) || (this.ownerId != null && this.ownerId.equals(attacker.getUUID())))) {
            return false;
        }
        return super.hurtServer(server, source, amount);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 600;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ALL.get("drw").get();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.ALL.get("drh").get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ALL.get("drd").get();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (this.ownerId != null) {
            output.store(OWNER_TAG, UUIDUtil.CODEC, this.ownerId);
        }
        output.putLong(SAVED_TOKEN_TAG, this.summonToken);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.ownerId = input.read(OWNER_TAG, UUIDUtil.CODEC).orElse(null);
        this.summonToken = input.getLongOr(SAVED_TOKEN_TAG, 0L);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<TamedAetherDragonEntity>("movement", 6, state -> {
            if (!onGround() && !isInWater()) {
                return state.setAndContinue(RIDING);
            }
            if (state.isMoving()) {
                return state.setAndContinue(WALK);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
