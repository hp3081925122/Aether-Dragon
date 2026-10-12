package com.aetherdragon.entity;

import com.aetherdragon.registry.ModItems;
import com.aetherdragon.registry.ModSounds;
import com.aetherdragon.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib.animation.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib.util.GeckoLibUtil;

public class RebornMobEntity extends PathfinderMob implements GeoEntity {
    static void applyBossKnockback(LivingEntity target, Vec3 impulse) {
        var resistance = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        double scale = resistance == null ? 1.0D : 1.0D - Mth.clamp(resistance.getValue(), 0.0D, 1.0D);
        if (scale <= 0.0D) {
            return;
        }
        target.setDeltaMovement(target.getDeltaMovement().add(impulse.scale(scale)));
        target.hurtMarked = true;
    }

    private static final String BUTTERFLY_VARIANT_TAG = "reborn_butterfly_variant";
    private static final String[] BUTTERFLY_TEXTURES = {"black", "blue", "cloud", "cyan", "dark", "dark_blue", "gold", "green", "orange", "purple", "red_large", "yellow"};
    private static final EntityDataAccessor<Integer> BUTTERFLY_VARIANT = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DRAGON_PHASE_TWO = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.BOOLEAN);
    private static final String DRAGON_PHASE_TAG = "reborn_dragon_phase_two";
    // 转阶段状态：同步给客户端，期间无敌且不执行 AI
    private static final EntityDataAccessor<Boolean> DRAGON_TRANSITION = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.BOOLEAN);
    // 转阶段时间轴（tick），与 phase_transition 动画一致
    private static final int TRANSITION_TICKS = 215;
    private static final int TRANSITION_DIVE_START = 35;
    private static final int TRANSITION_DIVE_END = 108;
    private int transitionTicks;
    private double transitionStartY;
    private double transitionGroundY;
    // 盐蟹受伤音效冷却，避免连续受伤时反复播放跳跃音效
    private int saltCrabHurtSoundCooldown;
    // 一阶段技能：当前技能、龙息开关与落点，同步给客户端做表现
    private static final EntityDataAccessor<Integer> DRAGON_SKILL = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DRAGON_BEAM = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DRAGON_BEAM_X = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRAGON_BEAM_Y = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DRAGON_BEAM_Z = SynchedEntityData.defineId(RebornMobEntity.class, EntityDataSerializers.FLOAT);
    private static final int SKILL_BREATH = 1;
    private static final int SKILL_FEATHER = 2;
    private static final int SKILL_METEOR = 3;
    private static final int SKILL_THUNDER = 4;
    private static final int SKILL_GALE = 5;
    // 二阶段技能
    private static final int SKILL_CLAW_RIGHT = 6;
    private static final int SKILL_CLAW_LEFT = 7;
    private static final int SKILL_STOMP = 8;
    private static final int SKILL_CHARGE = 9;
    private static final int SKILL_TAIL = 10;
    private static final int SKILL_SPIKE = 11;
    private static final int SKILL_ROAR = 12;
    private static final int SKILL_TAIL_SLAM = 13;
    private static final int SKILL_QUAKE_BREATH = 14;
    // 震息：五道震荡波的外半径
    private static final double[] RIPPLE_RADII = {3.0D, 6.0D, 9.0D, 12.0D, 15.0D};
    // 重踏三圈地震波的内外半径
    private static final double[] STOMP_WAVES = {0.0D, 5.0D, 9.5D, 14.0D};
    // 技能时长与动画一致；冷却以 tick 计
    private static final int[] SKILL_DURATION = {0, 80, 50, 60, 60, 40, 35, 35, 85, 50, 30, 32, 60, 44, 72};
    private static final int[] SKILL_COOLDOWN = {0, 220, 70, 280, 180, 120, 30, 30, 200, 160, 90, 140, 360, 120, 260};
    private static final byte EVENT_FEATHER = 90;
    private static final byte EVENT_METEOR = 91;
    private static final byte EVENT_GALE = 92;
    private static final byte EVENT_CLAW_RIGHT = 93;
    private static final byte EVENT_CLAW_LEFT = 94;
    private static final byte EVENT_STOMP = 95;
    private static final byte EVENT_TAIL = 98;
    private static final byte EVENT_ROAR = 99;
    private static final byte EVENT_SPIKE_SLAM = 100;
    private static final byte EVENT_CHARGE_HIT = 101;
    private static final byte EVENT_TAIL_SLAM = 102;
    private static final byte EVENT_RIPPLE = 103;
    private final int[] skillCooldowns = new int[15];
    private final Set<Integer> chargeHits = new HashSet<>();
    private Vec3 chargeDir = Vec3.ZERO;
    private Vec3 quakeCenter = Vec3.ZERO;
    private int clientSkill;
    private int clientSkillTicks;
    private int skillTicks;
    private int skillGap = 40;
    private double orbitAngle;
    private Vec3 beamBase = Vec3.ZERO;
    private Vec3 beamSide = Vec3.ZERO;
    // 灵魂战斗状态，首次使用时创建
    private SpiritCombat spiritCombat;
    // 天境娜迦战斗状态，首次使用时创建
    private NagaCombat nagaCombat;
    // 水晶石傀儡战斗状态，首次使用时创建
    private GolemCombat golemCombat;
    // 盐蟹王战斗状态，首次使用时创建
    private CrabCombat crabCombat;
    // 远古潜影贝战斗状态，首次使用时创建
    private ShulkerCombat shulkerCombat;
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final ServerBossEvent bossInfo = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
    private float lastBossBarProgress = -1.0F;

    public RebornMobEntity(EntityType<? extends RebornMobEntity> type, Level level) {
        super(type, level);
        if (getIdPath().equals("buterfly") && !level.isClientSide) {
            this.entityData.set(BUTTERFLY_VARIANT, this.random.nextInt(BUTTERFLY_TEXTURES.length));
        }
        if (getIdPath().equals("crystalitegolem")) {
        }
        if (isFlyingEntity()) {
            this.moveControl = new FlyingMoveControl(this, 10, true);
        }
    }

    @Override
    protected void registerGoals() {
        if (getIdPath().equals("aetherdragon")) {
            this.goalSelector.addGoal(5, new DragonFlightStrollGoal(this, 0.8D, 20));
            this.goalSelector.addGoal(5, new DragonGroundStrollGoal(this, 1.0D));
        } else {
            if (isFlyingEntity()) {
                this.goalSelector.addGoal(1, new PanicGoal(this, 1.1D));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.8D));
            } else {
                this.goalSelector.addGoal(0, new FloatGoal(this));
                this.goalSelector.addGoal(5, new RandomStrollGoal(this, 1.0D));
            }
        }
        if (getIdPath().equals("aetherdragon")) {
            this.goalSelector.addGoal(7, new FloatGoal(this));
        }
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, getIdPath().equals("aetherdragon") ? 180.0F : 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        if (this.getType().getCategory() == MobCategory.MONSTER || getIdPath().equals("saltcrab")) {
            if (getIdPath().equals("aetherdragon")) {
                this.goalSelector.addGoal(2, new DragonSkyCombatGoal(this));
                this.goalSelector.addGoal(2, new DragonGroundCombatGoal(this));
            } else if (getIdPath().equals("eldershulker")) {
                // 远古潜影贝：技能战斗，取代原有远程射击
                this.goalSelector.addGoal(2, new ShulkerCombat.CombatGoal(this));
            } else if (getIdPath().equals("spirit")) {
                // 灵魂：技能战斗，取代原有普攻与扑跳
                this.goalSelector.addGoal(2, new SpiritCombat.CombatGoal(this));
            } else if (getIdPath().equals("aether_naga")) {
                // 天境娜迦：技能战斗，取代原有普攻与扑跳
                this.goalSelector.addGoal(2, new NagaCombat.CombatGoal(this));
            } else if (getIdPath().equals("crystalitegolem")) {
                // 水晶石傀儡：技能战斗，取代原有普攻
                this.goalSelector.addGoal(2, new GolemCombat.CombatGoal(this));
            } else if (getIdPath().equals("saltcrabking")) {
                // 盐蟹王：技能战斗，取代原有普攻
                this.goalSelector.addGoal(2, new CrabCombat.CombatGoal(this));
            } else {
                this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
            }
            this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        if (!isFlyingEntity()) {
            return super.createNavigation(level);
        }
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(false);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public boolean isNoGravity() {
        return isFlyingEntity() || isDragonTransitioning() || super.isNoGravity();
    }

    @Override
    protected boolean isImmobile() {
        // 转阶段期间停止 AI 与移动输入
        return isDragonTransitioning() || super.isImmobile();
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 转阶段期间无敌，/kill 之类的绕过无敌伤害除外
        if (isDragonTransitioning() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // 远古潜影贝相位跃迁期间无敌
        if (getIdPath().equals("eldershulker") && !level().isClientSide && getShulkerCombat().isPhased() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        // 天境娜迦潜地期间无敌
        if (getIdPath().equals("aether_naga") && !level().isClientSide && getNagaCombat().isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable() {
        return !getIdPath().equals("aetherdragon") && super.isPushable();
    }

    @Override
    protected void doPush(Entity entity) {
        if (!getIdPath().equals("aetherdragon")) {
            super.doPush(entity);
        }
    }

    @Override
    protected void pushEntities() {
        if (!getIdPath().equals("aetherdragon")) {
            super.pushEntities();
        }
    }

    @Override
    protected void checkFallDamage(double distance, boolean onGround, BlockState state, BlockPos pos) {
        if (!isFlyingEntity()) {
            super.checkFallDamage(distance, onGround, state, pos);
        }
    }

    @Override
    public void setNoGravity(boolean noGravity) {
        super.setNoGravity(isDragonPhaseOne() || noGravity);
    }

    @Override
    public void aiStep() {
        if (this.saltCrabHurtSoundCooldown > 0) {
            this.saltCrabHurtSoundCooldown--;
        }
        if (!level().isClientSide && getIdPath().equals("buterfly") && this.entityData.get(BUTTERFLY_VARIANT) < 0) {
            this.entityData.set(BUTTERFLY_VARIANT, this.random.nextInt(BUTTERFLY_TEXTURES.length));
        }
        super.aiStep();
        if (isDragonPhaseOne()) {
            setNoGravity(true);
            if (!level().isClientSide && isAlive() && getHealth() <= 300.0F) {
                enterDragonPhaseTwo();
            }
        }
        if (level().isClientSide && getIdPath().equals("spirit")) {
            getSpiritCombat().clientTick();
        }
        if (level().isClientSide && getIdPath().equals("aether_naga")) {
            getNagaCombat().clientTick();
        }
        if (level().isClientSide && getIdPath().equals("crystalitegolem")) {
            getGolemCombat().clientTick();
        }
        if (level().isClientSide && getIdPath().equals("saltcrabking")) {
            getCrabCombat().clientTick();
        }
        if (level().isClientSide && getIdPath().equals("eldershulker")) {
            getShulkerCombat().clientTick();
        }
        if (level().isClientSide) {
            // 客户端技能计时，用于蓄力与冲锋表现
            int current = getDragonSkill();
            if (current != this.clientSkill) {
                this.clientSkill = current;
                this.clientSkillTicks = 0;
            }
            this.clientSkillTicks++;
            RandomSource random = getRandom();
            float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            if (current == SKILL_CHARGE && this.clientSkillTicks <= 16 && this.clientSkillTicks % 4 == 0) {
                // 冲撞蓄力：前方冲锋路线预警
                for (double d = 4.0D; d <= 18.0D; d += 3.5D) {
                    level().addAlwaysVisibleParticle(ModParticles.DRAGON_WARNING.get(), true, getX() + fx * d, getY() + 0.1D, getZ() + fz * d, 1.4D, 6.0D, 0.0D);
                }
            }
            if (current == SKILL_CHARGE && this.clientSkillTicks > 16 && this.clientSkillTicks <= 38) {
                // 冲锋：两侧扬尘与碎鳞
                double sx = -fz * 2.0D;
                double sz = fx * 2.0D;
                level().addParticle(ModParticles.WHITESMOKE.get(), getX() + sx, getY() + 0.3D, getZ() + sz, -fx * 0.2D, 0.05D, -fz * 0.2D);
                level().addParticle(ModParticles.WHITESMOKE.get(), getX() - sx, getY() + 0.3D, getZ() - sz, -fx * 0.2D, 0.05D, -fz * 0.2D);
                level().addParticle(ModParticles.DRAGON_SHARD.get(), getX() + fx * 3.0D, getY() + 1.5D, getZ() + fz * 3.0D, random.nextGaussian() * 0.1D, 0.2D, random.nextGaussian() * 0.1D);
            }
            if (current == SKILL_STOMP && this.clientSkillTicks >= 20 && this.clientSkillTicks < 62 && this.clientSkillTicks % 2 == 0) {
                // 重踏蓄力：碎光向高举的前爪汇聚
                Vec3 claw = position().add(fx * 3.0D, 5.0D, fz * 3.0D);
                for (int i = 0; i < 2; i++) {
                    Vec3 from = claw.add(random.nextGaussian() * 2.5D, random.nextGaussian() * 2.0D, random.nextGaussian() * 2.5D);
                    Vec3 velocity = claw.subtract(from).scale(0.18D);
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
                }
            }
        }
        if (level().isClientSide && (getDragonSkill() == SKILL_BREATH || getDragonSkill() == SKILL_QUAKE_BREATH)) {
            RandomSource random = getRandom();
            if (isDragonBeamActive()) {
                // 光束落点：碎光迸溅与小冲击环
                Vec3 end = getDragonBeamEnd();
                for (int i = 0; i < 2; i++) {
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), end.x, end.y + 0.2D, end.z, random.nextGaussian() * 0.2D, 0.3D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.2D);
                }
                level().addParticle(ModParticles.WHITESMOKE.get(), end.x, end.y + 0.3D, end.z, 0.0D, 0.05D, 0.0D);
                if (this.tickCount % 5 == 0) {
                    level().addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, end.x, end.y + 0.1D, end.z, 3.0D, 8.0D, 0.0D);
                }
            } else {
                // 蓄力：碎光向口部汇聚；震息为低头姿态，口部在前 3.2 格、上 3.3 格
                Vec3 mouth = getDragonMouth();
                if (getDragonSkill() == SKILL_QUAKE_BREATH) {
                    float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
                    mouth = position().add(-Mth.sin(yaw) * 3.2D, 3.3D, Mth.cos(yaw) * 3.2D);
                }
                for (int i = 0; i < 3; i++) {
                    Vec3 from = mouth.add(random.nextGaussian() * 2.5D, random.nextGaussian() * 2.5D, random.nextGaussian() * 2.5D);
                    Vec3 velocity = mouth.subtract(from).scale(0.18D);
                    level().addParticle(ModParticles.DRAGON_SHARD.get(), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
                }
            }
        }
        if (!level().isClientSide && isDragonTransitioning()) {
            this.transitionTicks++;
            // 俯冲段：按缓动曲线把龙从空中带到地面
            if (this.transitionTicks >= TRANSITION_DIVE_START && this.transitionTicks <= TRANSITION_DIVE_END) {
                float progress = (float) (this.transitionTicks - TRANSITION_DIVE_START) / (TRANSITION_DIVE_END - TRANSITION_DIVE_START);
                float eased = progress * progress * (3.0F - 2.0F * progress);
                double targetY = Mth.lerp(eased, this.transitionStartY, this.transitionGroundY);
                move(MoverType.SELF, new Vec3(0.0D, targetY - getY(), 0.0D));
            }
            setDeltaMovement(Vec3.ZERO);
            // 动画结束：解除无敌，恢复重力
            if (this.transitionTicks >= TRANSITION_TICKS) {
                this.entityData.set(DRAGON_TRANSITION, false);
                super.setNoGravity(false);
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        if (!level().isClientSide && isDragonPhaseOne() && amount >= getHealth() + getAbsorptionAmount()) {
            float safeDamage = Math.max(0.001F, getHealth() - 0.001F);
            boolean hurt = super.hurt(source, safeDamage);
            if (hurt && isDragonPhaseOne()) {
                enterDragonPhaseTwo();
            }
            return hurt;
        }
        // 远古潜影贝：记录受击时间，闭壳期间伤害降为两成
        if (!level().isClientSide && getIdPath().equals("eldershulker")) {
            getShulkerCombat().onHurt();
            if (getShulkerCombat().isGuarding()) {
                amount *= 0.2F;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isDragonPhaseOne()) {
            enterDragonPhaseTwo();
            return;
        }
        super.die(source);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DRAGON_PHASE_TWO, false);
        builder.define(DRAGON_TRANSITION, false);
        builder.define(DRAGON_SKILL, 0);
        builder.define(DRAGON_BEAM, false);
        builder.define(DRAGON_BEAM_X, 0.0F);
        builder.define(DRAGON_BEAM_Y, 0.0F);
        builder.define(DRAGON_BEAM_Z, 0.0F);
        builder.define(BUTTERFLY_VARIANT, -1);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isBossEntity()) {
            updateBossBarProgress();
            bossInfo.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (isBossEntity()) {
            bossInfo.removePlayer(player);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        updateBossBarProgress();
    }

    @Override
    public void setHealth(float health) {
        super.setHealth(health);
        updateBossBarProgress();
    }

    private void updateBossBarProgress() {
        if (bossInfo == null || level().isClientSide || !isBossEntity()) {
            return;
        }
        float maxHealth = getMaxHealth();
        float progress = maxHealth > 0.0F ? Mth.clamp(getHealth() / maxHealth, 0.0F, 1.0F) : 0.0F;
        if (Math.abs(progress - lastBossBarProgress) > 1.0E-6F) {
            bossInfo.setProgress(progress);
            lastBossBarProgress = progress;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getSound("ambient");
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return getSound("hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return getSound("death");
    }

    @Override
    public int getAmbientSoundInterval() {
        return getIdPath().equals("saltcrab") ? 600 : super.getAmbientSoundInterval();
    }

    public void triggerSaltCrabHurtSound() {
        if (this.saltCrabHurtSoundCooldown > 0) {
            return;
        }
        this.saltCrabHurtSoundCooldown = 40;
        triggerAction("jump");
    }
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel server, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(server, source, recentlyHit);
        ItemLike drop = switch (getIdPath()) {
            case "aether_naga" -> ModItems.ALL.get("dungeondagger").get();
            case "crystalitegolem" -> ModItems.ALL.get("crystalitesword").get();
            case "crystaliteminion" -> ModItems.ALL.get("crystalite").get();
            case "eldershulker" -> ModItems.ALL.get("pyrespear").get();
            case "mutatedcritter" -> Items.ROTTEN_FLESH;
            case "percipitus" -> Items.BONE;
            case "saltcrab" -> ModItems.ALL.get("crabmeat").get();
            case "saltcrabking" -> ModItems.ALL.get("saltinstrument").get();
            case "shimaenaga" -> Items.FEATHER;
            case "spirit" -> ModItems.ALL.get("spiritsword").get();
            default -> null;
        };
        if (drop != null) {
            this.spawnAtLocation(new ItemStack(drop));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("reborn_texture", getTextureName());
        if (getIdPath().equals("buterfly")) {
            tag.putInt(BUTTERFLY_VARIANT_TAG, this.entityData.get(BUTTERFLY_VARIANT));
        }
        if (getIdPath().equals("aetherdragon")) {
            tag.putBoolean(DRAGON_PHASE_TAG, isDragonPhaseTwo());
        }
        if (getIdPath().equals("spirit")) {
            getSpiritCombat().save(tag);
        }
        if (getIdPath().equals("aether_naga")) {
            getNagaCombat().save(tag);
        }
        if (getIdPath().equals("crystalitegolem")) {
            getGolemCombat().save(tag);
        }
        if (getIdPath().equals("saltcrabking")) {
            getCrabCombat().save(tag);
        }
        if (getIdPath().equals("eldershulker")) {
            getShulkerCombat().save(tag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (getIdPath().equals("buterfly") && tag.contains(BUTTERFLY_VARIANT_TAG)) {
            this.entityData.set(BUTTERFLY_VARIANT, Mth.clamp(tag.getInt(BUTTERFLY_VARIANT_TAG), 0, BUTTERFLY_TEXTURES.length - 1));
        }
        if (getIdPath().equals("spirit")) {
            getSpiritCombat().load(tag);
        }
        if (getIdPath().equals("aether_naga")) {
            getNagaCombat().load(tag);
        }
        if (getIdPath().equals("crystalitegolem")) {
            getGolemCombat().load(tag);
        }
        if (getIdPath().equals("saltcrabking")) {
            getCrabCombat().load(tag);
        }
        if (getIdPath().equals("eldershulker")) {
            getShulkerCombat().load(tag);
        }
        if (getIdPath().equals("aetherdragon") && tag.getBoolean(DRAGON_PHASE_TAG)) {
            this.entityData.set(DRAGON_PHASE_TWO, true);
            applyDragonPhaseTwoAttributes();
            this.navigation = new GroundPathNavigation(this, level());
            this.moveControl = new MoveControl(this);
            super.setNoGravity(false);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<RebornMobEntity> movement = new AnimationController<>(this, "movement", 4, this::movementPredicate);
        movement.setParticleKeyframeHandler(this::particleKeyframe);
        movement.setSoundKeyframeHandler(this::soundKeyframe);
        controllers.add(movement);
        AnimationController<RebornMobEntity> action = new AnimationController<>(this, "action", 0, state -> PlayState.STOP);
        action.setParticleKeyframeHandler(this::particleKeyframe);
        action.setSoundKeyframeHandler(this::soundKeyframe);
        registerActionAnimations(action);
        controllers.add(action);
    }

    private void particleKeyframe(ParticleKeyframeEvent<RebornMobEntity> event) {
        String effect = event.getKeyframeData().getEffect();
        if (effect.startsWith("phase_")) {
            spawnDragonPhaseEffect(effect);
            return;
        }
        ParticleOptions particle = switch (effect) {
            case "summoning", "activate" -> ModParticles.RUNESEFFECT.get();
            case "death", "smash", "groundslam" -> ParticleTypes.EXPLOSION;
            case "hurt", "angry", "roar" -> ModParticles.WHITESMOKE.get();
            case "trail" -> ModParticles.FIRE.get();
            case "dig" -> ParticleTypes.POOF;
            default -> null;
        };
        if (particle != null) {
            level().addParticle(particle, getX(), getY() + getBbHeight() * 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnDragonPhaseEffect(String effect) {
        RandomSource random = getRandom();
        Level level = level();
        switch (effect) {
            case "phase_break" -> {
                // 受创：躯干处碎鳞四散，伴随一圈收束的冲击环
                double cy = getY() + 2.5D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), cy, getZ(), 7.0D, 14.0D, 0.0D);
                for (int i = 0; i < 36; i++) {
                    double yaw = random.nextDouble() * Math.PI * 2.0D;
                    double pitch = (random.nextDouble() - 0.3D) * Math.PI * 0.6D;
                    double speed = 0.35D + random.nextDouble() * 0.35D;
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), getX(), cy, getZ(), Math.cos(yaw) * Math.cos(pitch) * speed, Math.sin(pitch) * speed + 0.15D, Math.sin(yaw) * Math.cos(pitch) * speed);
                }
                for (int i = 0; i < 6; i++) {
                    level.addParticle(ModParticles.WHITESMOKE.get(), getX() + random.nextGaussian(), cy + random.nextGaussian(), getZ() + random.nextGaussian(), 0.0D, 0.02D, 0.0D);
                }
            }
            case "phase_impact" -> {
                // 落地：贴地双层冲击波 + 从地面崩起的碎鳞与尘烟
                double gy = getY() + 0.15D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), gy, getZ(), 14.0D, 22.0D, 0.0D);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), gy + 0.05D, getZ(), 8.0D, 14.0D, 0.0D);
                for (int i = 0; i < 40; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double dx = Math.cos(angle);
                    double dz = Math.sin(angle);
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), getX() + dx * 2.5D, gy + 0.3D, getZ() + dz * 2.5D, dx * (0.3D + random.nextDouble() * 0.3D), 0.4D + random.nextDouble() * 0.4D, dz * (0.3D + random.nextDouble() * 0.3D));
                }
                for (int i = 0; i < 20; i++) {
                    double angle = Math.PI * 2.0D * i / 20.0D;
                    level.addParticle(ModParticles.WHITESMOKE.get(), getX() + Math.cos(angle) * 3.0D, gy + 0.4D, getZ() + Math.sin(angle) * 3.0D, Math.cos(angle) * 0.25D, 0.03D, Math.sin(angle) * 0.25D);
                }
            }
            case "phase_roar" -> {
                // 咆哮：口部高度的水平声浪环，向前喷出金色碎屑
                float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
                double fx = -Mth.sin(yaw);
                double fz = Mth.cos(yaw);
                double hx = getX() + fx * 4.0D;
                double hy = getY() + 5.5D;
                double hz = getZ() + fz * 4.0D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, hx, hy, hz, 11.0D, 18.0D, 0.0D);
                for (int i = 0; i < 10; i++) {
                    double spread = (random.nextDouble() - 0.5D) * 0.5D;
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), hx, hy, hz, (fx + spread * fz) * 0.6D, 0.1D + random.nextDouble() * 0.2D, (fz - spread * fx) * 0.6D);
                }
            }
            default -> {
            }
        }
    }

    private void soundKeyframe(SoundKeyframeEvent<RebornMobEntity> event) {
        if (!level().isClientSide) {
            return;
        }
        String soundId = switch (getIdPath()) {
            case "aetherdragon" -> switch (event.getKeyframeData().getSound()) {
                case "flying.idle" -> "drw";
                case "landing" -> "dri";
                case "attack" -> "drf";
                // 转阶段：受创惨叫与落地后咆哮
                case "phase_hurt" -> "drh";
                case "phase_roar" -> "drw";
                case "phase_slam" -> "dragon_ground_slam";
                default -> null;
            };
            case "crystalitegolem" -> switch (event.getKeyframeData().getSound()) {
                case "groundslam" -> "cgmd";
                case "roar" -> "cgmh";
                case "swing" -> "cgmi";
                case "summoning", "activate" -> "cgmw";
                case "death" -> "cgd";
                default -> null;
            };
            case "crystaliteminion" -> event.getKeyframeData().getSound().equals("attack") ? "cgi" : null;
            case "aether_naga" -> event.getKeyframeData().getSound().equals("scream") ? "nad" : null;
            case "saltcrab" -> switch (event.getKeyframeData().getSound()) {
                case "jump" -> "sabw";
                case "ree" -> "sah";
                default -> null;
            };
            case "mutatedcritter" -> switch (event.getKeyframeData().getSound()) {
                case "pre_attack" -> "mui";
                case "angry" -> "muh";
                default -> null;
            };
            default -> null;
        };
        if (soundId != null) {
            var sound = ModSounds.ALL.get(soundId);
            if (sound != null) {
                // 咆哮压低音调、放大音量
                float volume = 1.0F;
                float pitch = 1.0F;
                if (event.getKeyframeData().getSound().equals("phase_roar")) {
                    volume = 4.0F;
                    pitch = 0.6F;
                }
                if (event.getKeyframeData().getSound().equals("phase_slam")) {
                    volume = 4.0F;
                }
                // 客户端需用本地播放，playSound(null, ...) 在客户端不会出声
                level().playLocalSound(getX(), getY(), getZ(), sound.get(), SoundSource.HOSTILE, volume, pitch, false);
            }
        }
    }

    private void registerActionAnimations(AnimationController<RebornMobEntity> controller) {
        for (String action : new String[]{"attack", "hurt", "death", "summoning", "roar", "groundslam", "activate", "jump", "spawn", "phase"}) {
            String animation = getActionAnimation(action);
            if (!animation.isEmpty()) {
                controller.triggerableAnim(action, RawAnimation.begin().thenPlay(animation));
            }
        }
    }

    private String getActionAnimation(String action) {
        return switch (getIdPath()) {
            case "crystalitegolem" -> switch (action) {
                case "death", "activate" -> "animation.crystalite_golem." + action;
                default -> "";
            };
            case "crystaliteminion" -> switch (action) {
                case "spawn", "attack" -> "animation.crystalite_minion." + action;
                default -> "";
            };
            case "aetherdragon" -> switch (action) {
                // 一阶段转二阶段的完整演出
                case "phase" -> "animation.aether_dragon.phase_transition";
                default -> "";
            };
            case "aether_naga" -> switch (action) {
                case "death" -> "animation.slider." + action;
                default -> "";
            };
            case "guardianangel" -> action.equals("attack") ? "animation.guardian_angel.attack" : "";
            case "saltcrab" -> switch (action) {
                case "summon", "jump", "attack" -> "animation.salt_crab." + action;
                default -> "";
            };
            case "mutatedcritter" -> switch (action) {
                case "hurt", "angry", "attack", "jump", "death" -> "animation.mutated_critter." + action;
                default -> "";
            };
            default -> "";
        };
    }

    public void triggerAction(String action) {
        if (!getActionAnimation(action).isEmpty()) {
            triggerAnim("action", action);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        if (isFlyingEntity()) {
            if (!level().isClientSide && getIdPath().equals("buterfly") && distance > 3.0F) {
            }
            return false;
        }
        return super.causeFallDamage(distance, multiplier, source);
    }

    private PlayState movementPredicate(AnimationState<RebornMobEntity> state) {
        // 灵魂技能动画同样以待机为底，放在移动控制器里
        String spiritSkill = SpiritCombat.getAnimation(getSpiritSkill());
        if (!spiritSkill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(spiritSkill));
        }
        // 天境娜迦技能动画
        String nagaSkill = NagaCombat.getAnimation(getNagaSkill());
        if (!nagaSkill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(nagaSkill));
        }
        // 水晶石傀儡技能动画
        String golemSkill = GolemCombat.getAnimation(getGolemSkill());
        if (!golemSkill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(golemSkill));
        }
        // 盐蟹王技能动画
        String crabSkill = CrabCombat.getAnimation(getCrabSkill());
        if (!crabSkill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(crabSkill));
        }
        // 远古潜影贝技能动画
        String shulkerSkill = ShulkerCombat.getAnimation(getShulkerSkill());
        if (!shulkerSkill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(shulkerSkill));
        }
        // 一阶段技能动画以飞行待机为底，放在移动控制器里与待机平滑过渡
        String skill = switch (getDragonSkill()) {
            case SKILL_BREATH -> "animation.aether_dragon.skill_breath";
            case SKILL_FEATHER -> "animation.aether_dragon.skill_feather";
            case SKILL_METEOR -> "animation.aether_dragon.skill_meteor";
            case SKILL_THUNDER -> "animation.aether_dragon.skill_thunder";
            case SKILL_GALE -> "animation.aether_dragon.skill_gale";
            case SKILL_CLAW_RIGHT -> "animation.aether_dragon.standing_attack_right";
            case SKILL_CLAW_LEFT -> "animation.aether_dragon.standing_attack_left";
            case SKILL_STOMP -> "animation.aether_dragon.attack.land";
            case SKILL_CHARGE -> "animation.aether_dragon.skill_charge";
            case SKILL_TAIL -> "animation.aether_dragon.skill_tail_spin";
            case SKILL_SPIKE -> "animation.aether_dragon.skill_spike_slam";
            case SKILL_ROAR -> "animation.aether_dragon.standing_roar";
            case SKILL_TAIL_SLAM -> "animation.aether_dragon.skill_tail_slam";
            case SKILL_QUAKE_BREATH -> "animation.aether_dragon.skill_quake_breath";
            default -> "";
        };
        if (!skill.isEmpty()) {
            return state.setAndContinue(RawAnimation.begin().thenPlay(skill));
        }
        String animation = getMovementAnimation(state.isMoving());
        return animation.isEmpty() ? PlayState.STOP : state.setAndContinue(RawAnimation.begin().thenLoop(animation));
    }

    private String getMovementAnimation(boolean moving) {
        return switch (getIdPath()) {
            case "aetherdragon" -> isDragonPhaseTwo() ? (moving ? "animation.aether_dragon.walk" : "animation.aether_dragon.idle") : "animation.aether_dragon.idle.flying";
            case "aether_naga" -> moving ? "animation.slider.move" : "animation.slider.idle";
            case "saltcrabking" -> moving ? "animation.salt_crab_king.walk" : "animation.salt_crab_king.idle";
            case "eldershulker" -> "animation.elder_shulker.idle";
            case "spirit" -> moving ? "animation.spirit.move" : "animation.spirit.idle";
            case "crystalitegolem" -> moving ? "animation.crystalite_golem.walk" : "animation.crystalite_golem.idle1";
            case "crystaliteminion" -> moving ? "animation.crystalite_minion.walk" : "animation.crystalite_minion.idle";
            case "guardianangel" -> moving ? "animation.guardian_angel.move" : "animation.guardian_angel.idle";
            case "mutatedcritter" -> moving ? "animation.mutated_critter.walk" : "animation.mutated_critter.idle";
            case "percipitus" -> moving ? "animation.percipitus.fly" : "animation.percipitus.idle";
            case "saltcrab" -> moving ? "animation.salt_crab.walk" : "animation.salt_crab.idle2";
            case "shimaenaga" -> moving ? "animation.shima_enaga.walk" : "animation.shima_enaga.idle2";
            case "buterfly", "twinkler" -> moving ? "animation.twinkler.fly" : "animation.twinkler.idle";
            default -> "";
        };
    }

    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    public String getIdPath() {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(this.getType());
        return key == null ? "missing" : key.getPath();
    }

    public String getTextureName() {
        return switch (getIdPath()) {
            case "aetherdragon" -> "dragon";
            case "aether_naga" -> "slider";
            case "buterfly" -> BUTTERFLY_TEXTURES[Mth.clamp(this.entityData.get(BUTTERFLY_VARIANT), 0, BUTTERFLY_TEXTURES.length - 1)];
            case "crystalitegolem" -> "crystalite_golem";
            case "crystaliteminion" -> "crystalite_minion";
            case "eldershulker" -> "elder_shulker";
            case "guardianangel" -> "ggguar";
            case "mutatedcritter" -> "mutated_critter";
            case "percipitus" -> "percipitus";
            case "saltcrab" -> "salt_crab";
            case "saltcrabking" -> "salt_crab_king";
            case "shimaenaga" -> "shima_enaga";
            case "twinkler" -> "aurora_quartz";
            default -> getIdPath();
        };
    }

    private boolean isBossEntity() {
        return switch (getIdPath()) {
            case "aether_naga", "aetherdragon", "crystalitegolem", "eldershulker", "saltcrabking", "spirit" -> true;
            default -> false;
        };
    }

    private SoundEvent getSound(String kind) {
        String id = getIdPath();
        if (id.equals("percipitus")) {
            return switch (kind) {
                case "ambient" -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
                case "hurt" -> SoundEvents.ALLAY_HURT;
                default -> SoundEvents.ALLAY_DEATH;
            };
        }
        String sound = switch (id) {
            case "aether_naga" -> kind.equals("ambient") ? "naw" : kind.equals("hurt") ? "nah" : "nad";
            case "crystalitegolem" -> kind.equals("ambient") ? "cgw" : kind.equals("hurt") ? "cgh" : "cgd";
            case "guardianangel" -> kind.equals("ambient") ? "gaw" : kind.equals("hurt") ? "gah" : "gad";
            case "mutatedcritter" -> kind.equals("ambient") ? null : kind.equals("hurt") ? "muh" : "mud";
            case "saltcrab" -> kind.equals("ambient") ? "saw" : "sah";
            case "spirit" -> kind.equals("ambient") ? "spw" : kind.equals("hurt") ? "sph" : "spd";
            case "aetherdragon" -> kind.equals("ambient") ? "drw" : kind.equals("hurt") ? "drh" : "drd";
            case "eldershulker" -> kind.equals("ambient") ? "shi" : kind.equals("hurt") ? "shh" : "shd";
            default -> null;
        };
        return sound == null ? null : ModSounds.ALL.get(sound).get();
    }
    private boolean isFlyingEntity() {
        String id = getIdPath();
        return isDragonPhaseOne() || id.equals("percipitus") || id.equals("shimaenaga") || id.equals("twinkler") || id.equals("buterfly");
    }

    public static AttributeSupplier.Builder createAttributes(String id) {
        double speed = 0.3D;
        double health = 10.0D;
        double armor = 0.0D;
        double knockbackResistance = 0.0D;
        double attackKnockback = 0.0D;
        double damage = 3.0D;
        double followRange = 16.0D;
        double flyingSpeed = 0.0D;
        if (id.equals("aetherdragon")) {
            speed = 0.0D;
            health = 600.0D;
            armor = 0.2D;
            damage = 3.0D;
            followRange = 50.0D;
            knockbackResistance = 2.5D;
        } else if (id.equals("aether_naga")) {
            health = 350.0D;
            armor = 0.1D;
            damage = 6.0D;
            followRange = 32.0D;
            knockbackResistance = 0.8D;
            attackKnockback = 0.1D;
        } else if (id.equals("spirit")) {
            speed = 0.4D;
            health = 300.0D;
            armor = 0.3D;
            knockbackResistance = 0.8D;
            damage = 11.0D;
            followRange = 30.0D;
            attackKnockback = 0.2D;
        } else if (id.equals("saltcrabking")) {
            speed = 0.23D;
            health = 250.0D;
            armor = 0.2D;
            damage = 15.0D;
            followRange = 35.0D;
            attackKnockback = 0.2D;
            knockbackResistance = 1.0D;
        } else if (id.equals("eldershulker")) {
            speed = 0.0D;
            health = 400.0D;
            armor = 1.5D;
            knockbackResistance = 1.0D;
            damage = 5.0D;
            followRange = 40.0D;
            attackKnockback = 0.2D;
        } else if (id.equals("crystalitegolem")) {
            speed = 0.2D;
            health = 300.0D;
            armor = 0.2D;
            damage = 8.0D;
            followRange = 32.0D;
            attackKnockback = 0.3D;
            knockbackResistance = 1.0D;
        } else if (id.equals("crystaliteminion")) {
            speed = 0.2D;
            health = 20.0D;
            damage = 5.0D;
        } else if (id.equals("mutatedcritter")) {
            health = 12.0D;
            damage = 5.0D;
        } else if (id.equals("saltcrab")) {
            health = 6.0D;
            damage = 3.0D;
            knockbackResistance = 2.0D;
        } else if (id.equals("guardianangel")) {
            health = 7.0D;
        } else if (id.equals("percipitus") || id.equals("shimaenaga")) {
            health = 10.0D;
            flyingSpeed = 0.3D;
        } else if (id.equals("buterfly") || id.equals("twinkler")) {
            health = 1.0D;
            flyingSpeed = 0.3D;
        }
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ARMOR, armor)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.ATTACK_KNOCKBACK, attackKnockback)
                .add(Attributes.FOLLOW_RANGE, followRange)
                .add(Attributes.KNOCKBACK_RESISTANCE, knockbackResistance)
                .add(Attributes.FLYING_SPEED, flyingSpeed);
    }

    public boolean isDragonPhaseTwo() {
        return getIdPath().equals("aetherdragon") && this.entityData.get(DRAGON_PHASE_TWO);
    }

    private boolean isDragonPhaseOne() {
        return getIdPath().equals("aetherdragon") && !isDragonPhaseTwo();
    }

    private void enterDragonPhaseTwo() {
        if (!getIdPath().equals("aetherdragon") || isDragonPhaseTwo()) {
            return;
        }
        this.entityData.set(DRAGON_PHASE_TWO, true);
        stopDragonSkill();
        applyDragonPhaseTwoAttributes();
        setHealth(300.0F);
        setTarget(null);
        this.navigation.stop();
        this.navigation = new GroundPathNavigation(this, level());
        this.moveControl = new MoveControl(this);
        // 进入转阶段：记录俯冲起点与下方地面，保持无重力直到动画结束
        this.entityData.set(DRAGON_TRANSITION, true);
        this.transitionTicks = 0;
        this.transitionStartY = getY();
        this.transitionGroundY = getY() - 64.0D;
        Vec3 from = position();
        BlockHitResult ground = level().clip(new ClipContext(from, from.subtract(0.0D, 64.0D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (ground.getType() == HitResult.Type.BLOCK) {
            this.transitionGroundY = ground.getLocation().y;
        }
        setDeltaMovement(Vec3.ZERO);
        super.setNoGravity(true);
        triggerAction("phase");
    }

    public boolean isDragonTransitioning() {
        return getIdPath().equals("aetherdragon") && this.entityData.get(DRAGON_TRANSITION);
    }

    private void applyDragonPhaseTwoAttributes() {
        // 体型巨大，可直接跨过 1 格高的地形
        if (getAttribute(Attributes.STEP_HEIGHT) != null) {
            getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1.5D);
        }
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D);
        }
        if (getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D);
        }
        if (getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null) {
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.2D);
        }
    }

    // 灵魂技能复用技能同步字段
    public int getSpiritSkill() {
        if (!getIdPath().equals("spirit")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    void setBossSkill(int skill) {
        this.entityData.set(DRAGON_SKILL, skill);
    }

    // 灵魂技能记录点（突进起点），复用龙息落点同步字段
    void setBossSkillPoint(Vec3 point) {
        this.entityData.set(DRAGON_BEAM_X, (float) point.x);
        this.entityData.set(DRAGON_BEAM_Y, (float) point.y);
        this.entityData.set(DRAGON_BEAM_Z, (float) point.z);
    }

    Vec3 getBossSkillPoint() {
        return getDragonBeamEnd();
    }

    // 天境娜迦技能复用技能同步字段
    public int getNagaSkill() {
        if (!getIdPath().equals("aether_naga")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    // 天境娜迦光束开关，复用龙息光束同步字段
    public boolean isNagaBeamActive() {
        return getIdPath().equals("aether_naga") && this.entityData.get(DRAGON_BEAM);
    }

    void setBossBeam(boolean active) {
        this.entityData.set(DRAGON_BEAM, active);
    }

    // 水晶石傀儡技能复用技能同步字段
    public int getGolemSkill() {
        if (!getIdPath().equals("crystalitegolem")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    // 水晶石傀儡狂暴状态复用二阶段同步字段，客户端据此显示光环
    public boolean isGolemEnraged() {
        return getIdPath().equals("crystalitegolem") && this.entityData.get(DRAGON_PHASE_TWO);
    }

    void setBossEnraged(boolean enraged) {
        this.entityData.set(DRAGON_PHASE_TWO, enraged);
    }

    // 盐蟹王技能复用技能同步字段
    public int getCrabSkill() {
        if (!getIdPath().equals("saltcrabking")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    // 盐蟹王狂暴状态复用二阶段同步字段
    public boolean isCrabEnraged() {
        return getIdPath().equals("saltcrabking") && this.entityData.get(DRAGON_PHASE_TWO);
    }

    // 远古潜影贝技能复用技能同步字段
    public int getShulkerSkill() {
        if (!getIdPath().equals("eldershulker")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    // 远古潜影贝狂暴状态复用二阶段同步字段
    public boolean isShulkerEnraged() {
        return getIdPath().equals("eldershulker") && this.entityData.get(DRAGON_PHASE_TWO);
    }

    // 远古潜影贝光束开关，复用龙息光束同步字段
    public boolean isShulkerBeamActive() {
        return getIdPath().equals("eldershulker") && this.entityData.get(DRAGON_BEAM);
    }

    ShulkerCombat getShulkerCombat() {
        if (this.shulkerCombat == null) {
            this.shulkerCombat = new ShulkerCombat(this);
        }
        return this.shulkerCombat;
    }

    CrabCombat getCrabCombat() {
        if (this.crabCombat == null) {
            this.crabCombat = new CrabCombat(this);
        }
        return this.crabCombat;
    }

    GolemCombat getGolemCombat() {
        if (this.golemCombat == null) {
            this.golemCombat = new GolemCombat(this);
        }
        return this.golemCombat;
    }

    NagaCombat getNagaCombat() {
        if (this.nagaCombat == null) {
            this.nagaCombat = new NagaCombat(this);
        }
        return this.nagaCombat;
    }

    SpiritCombat getSpiritCombat() {
        if (this.spiritCombat == null) {
            this.spiritCombat = new SpiritCombat(this);
        }
        return this.spiritCombat;
    }

    public int getDragonSkill() {
        if (!getIdPath().equals("aetherdragon")) {
            return 0;
        }
        return this.entityData.get(DRAGON_SKILL);
    }

    public boolean isDragonBeamActive() {
        return getIdPath().equals("aetherdragon") && this.entityData.get(DRAGON_BEAM);
    }

    public Vec3 getDragonBeamEnd() {
        return new Vec3(this.entityData.get(DRAGON_BEAM_X), this.entityData.get(DRAGON_BEAM_Y), this.entityData.get(DRAGON_BEAM_Z));
    }

    // 口部大致位置：前 5 格、上 3.2 格（来自 BB 中龙息姿态的下颚坐标）
    private Vec3 getDragonMouth() {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 5.0D, 3.2D, Mth.cos(yaw) * 5.0D);
    }

    void faceDragonTowards(double x, double z, float maxStep) {
        float yaw = (float) (Mth.atan2(z - getZ(), x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, maxStep));
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
    }

    // 向下寻找地面高度，找不到时返回 fallback
    double findGroundY(double x, double fromY, double z, double depth, double fallback) {
        Vec3 from = new Vec3(x, fromY, z);
        BlockHitResult hit = level().clip(new ClipContext(from, from.subtract(0.0D, depth, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().y;
        }
        return fallback;
    }

    private void stopDragonSkill() {
        this.skillTicks = 0;
        this.entityData.set(DRAGON_SKILL, 0);
        this.entityData.set(DRAGON_BEAM, false);
    }

    boolean canDragonSkillHit(LivingEntity entity) {
        return entity != this && entity.isAlive() && !(entity instanceof RebornMobEntity);
    }

    // 一阶段空战：绕目标盘旋，按冷却挑选技能并执行时间轴
    private void tickSkyCombat(LivingEntity target) {
        // 目标可能在 Goal 校验与本 tick 执行之间失效，先终止当前技能时间轴
        if (target == null || !target.isAlive()) {
            stopDragonSkill();
            return;
        }
        for (int i = 1; i < this.skillCooldowns.length; i++) {
            if (this.skillCooldowns[i] > 0) {
                this.skillCooldowns[i]--;
            }
        }
        int skill = getDragonSkill();
        if (skill != SKILL_BREATH) {
            faceDragonTowards(target.getX(), target.getZ(), 8.0F);
        }
        if (skill == 0) {
            // 盘旋：目标上方 10 格、半径 16 格
            this.orbitAngle += 0.012D;
            Vec3 hover = new Vec3(target.getX() + Math.cos(this.orbitAngle) * 16.0D, target.getY() + 10.0D, target.getZ() + Math.sin(this.orbitAngle) * 16.0D);
            Vec3 want = hover.subtract(position());
            double distance = want.length();
            Vec3 velocity = Vec3.ZERO;
            if (distance > 0.5D) {
                velocity = want.scale(Math.min(0.45D, distance * 0.05D) / distance);
            }
            setDeltaMovement(getDeltaMovement().lerp(velocity, 0.12D));
            if (this.skillGap > 0) {
                this.skillGap--;
                return;
            }
            // 选技能：贴脸优先飓风，其余在就绪技能中随机
            int chosen = 0;
            if (distanceTo(target) < 10.0D && this.skillCooldowns[SKILL_GALE] <= 0) {
                chosen = SKILL_GALE;
            } else {
                List<Integer> ready = new ArrayList<>();
                for (int i = SKILL_BREATH; i <= SKILL_THUNDER; i++) {
                    if (this.skillCooldowns[i] <= 0) {
                        ready.add(i);
                    }
                }
                if (!ready.isEmpty()) {
                    chosen = ready.get(getRandom().nextInt(ready.size()));
                }
            }
            if (chosen == 0) {
                return;
            }
            this.skillCooldowns[chosen] = SKILL_COOLDOWN[chosen];
            this.skillTicks = 0;
            this.entityData.set(DRAGON_SKILL, chosen);
            if (chosen == SKILL_BREATH) {
                // 龙息扫射基准：目标脚下，横扫方向垂直于龙到目标的连线
                this.beamBase = target.position().add(0.0D, 0.5D, 0.0D);
                Vec3 toward = target.position().subtract(position());
                this.beamSide = new Vec3(-toward.z, 0.0D, toward.x).normalize();
            }
            return;
        }
        // 施法时减速悬停
        setDeltaMovement(getDeltaMovement().scale(0.8D));
        this.skillTicks++;
        playSkillCue(skill, this.skillTicks);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        switch (skill) {
            case SKILL_BREATH -> {
                // 天穹龙息：0-20 蓄力，20-68 横扫光束
                if (this.skillTicks < 20) {
                    faceDragonTowards(target.getX(), target.getZ(), 8.0F);
                }
                if (this.skillTicks == 20) {
                    this.entityData.set(DRAGON_BEAM, true);
                }
                if (this.skillTicks >= 20 && this.skillTicks <= 68) {
                    this.beamBase = this.beamBase.lerp(target.position().add(0.0D, 0.5D, 0.0D), 0.06D);
                    double sweep = (this.skillTicks - 20) / 48.0D * 2.0D - 1.0D;
                    Vec3 aim = this.beamBase.add(this.beamSide.scale(sweep * 7.0D));
                    faceDragonTowards(aim.x, aim.z, 4.0F);
                    Vec3 mouth = getDragonMouth();
                    Vec3 far = mouth.add(aim.subtract(mouth).scale(1.6D));
                    BlockHitResult hit = level().clip(new ClipContext(mouth, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                    Vec3 end = far;
                    if (hit.getType() != HitResult.Type.MISS) {
                        end = hit.getLocation();
                    }
                    this.entityData.set(DRAGON_BEAM_X, (float) end.x);
                    this.entityData.set(DRAGON_BEAM_Y, (float) end.y);
                    this.entityData.set(DRAGON_BEAM_Z, (float) end.z);
                    // 每 4 tick 结算一次光束沿线伤害
                    if (this.skillTicks % 4 == 0) {
                        Vec3 segment = end.subtract(mouth);
                        double lengthSqr = Math.max(1.0E-4D, segment.lengthSqr());
                        for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(mouth, end).inflate(1.6D), this::canDragonSkillHit)) {
                            Vec3 center = living.getBoundingBox().getCenter();
                            double t = Mth.clamp(center.subtract(mouth).dot(segment) / lengthSqr, 0.0D, 1.0D);
                            if (mouth.add(segment.scale(t)).distanceToSqr(center) < 2.6D) {
                                living.hurt(damageSources().indirectMagic(this, this), 5.0F);
                            }
                        }
                    }
                    if (this.skillTicks % 12 == 0) {
                        server.playSound(null, end.x, end.y, end.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.7F);
                    }
                }
                if (this.skillTicks == 69) {
                    this.entityData.set(DRAGON_BEAM, false);
                }
            }
            case SKILL_FEATHER -> {
                // 风暴羽刃：双翼前扫时射出 9 片扇形羽刃
                if (this.skillTicks == 18) {
                    Vec3 origin = position().add(0.0D, 2.5D, 0.0D).add(forward.scale(2.5D));
                    Vec3 aim = target.getEyePosition().subtract(origin).normalize();
                    for (int i = -4; i <= 4; i++) {
                        Vec3 velocity = aim.yRot(i * 8.0F * Mth.DEG_TO_RAD).scale(1.3D);
                        DragonSkillEntity.spawn(server, this, DragonSkillEntity.FEATHER, origin, velocity, 0.0D, 0, target);
                    }
                    ModSounds.play(level(), "dragon_feather_launch", getX(), getY(), getZ(), 4.0F, 1.0F);
                    level().broadcastEntityEvent(this, EVENT_FEATHER);
                }
            }
            case SKILL_METEOR -> {
                // 苍穹陨晶：咆哮后在目标周围依次落下 7 根晶矛
                if (this.skillTicks == 12) {
                    level().broadcastEntityEvent(this, EVENT_METEOR);
                }
                if (this.skillTicks >= 20 && this.skillTicks <= 56 && (this.skillTicks - 20) % 6 == 0) {
                    double x = target.getX() + target.getDeltaMovement().x * 10.0D;
                    double z = target.getZ() + target.getDeltaMovement().z * 10.0D;
                    if (this.skillTicks > 20) {
                        x = target.getX() + (getRandom().nextDouble() * 2.0D - 1.0D) * 7.0D;
                        z = target.getZ() + (getRandom().nextDouble() * 2.0D - 1.0D) * 7.0D;
                    }
                    double groundY = findGroundY(x, target.getY() + 6.0D, z, 30.0D, target.getY());
                    DragonSkillEntity.spawn(server, this, DragonSkillEntity.CRYSTAL, new Vec3(x, groundY + 26.0D, z), Vec3.ZERO, groundY, 0, target);
                    ModSounds.play(level(), "dragon_crystal_fall", x, groundY + 16.0D, z, 3.0F, 1.0F);
                }
            }
            case SKILL_THUNDER -> {
                // 天雷审判：连续三次标记目标当前位置
                if (this.skillTicks == 12 || this.skillTicks == 24 || this.skillTicks == 36) {
                    double groundY = findGroundY(target.getX(), target.getY() + 1.0D, target.getZ(), 12.0D, target.getY());
                    DragonSkillEntity.spawn(server, this, DragonSkillEntity.THUNDER, new Vec3(target.getX(), groundY, target.getZ()), Vec3.ZERO, groundY, 0, target);
                    ModSounds.play(level(), "dragon_thunder_mark", target.getX(), groundY, target.getZ(), 2.0F, 1.0F);
                }
            }
            case SKILL_GALE -> {
                // 龙翼飓风：下拍瞬间击退并造成少量伤害
                if (this.skillTicks == 14) {
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0D), this::canDragonSkillHit)) {
                        Vec3 push = living.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                        if (push.lengthSqr() < 1.0E-4D || living.distanceToSqr(this) > 144.0D) {
                            continue;
                        }
                        push = push.normalize();
                        living.hurt(damageSources().mobAttack(this), 4.0F);
                        applyBossKnockback(living, new Vec3(push.x * 1.1D, 0.35D, push.z * 1.1D));
                        living.hurtMarked = true;
                    }
                    ModSounds.play(level(), "dragon_gale_blast", getX(), getY(), getZ(), 5.0F, 1.0F);
                    level().broadcastEntityEvent(this, EVENT_GALE);
                }
            }
            default -> {
            }
        }
        if (this.skillTicks >= SKILL_DURATION[skill]) {
            stopDragonSkill();
            this.skillGap = 25;
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (getIdPath().equals("spirit") && SpiritCombat.isEvent(id)) {
            getSpiritCombat().handleEvent(id);
            return;
        }
        if (getIdPath().equals("aether_naga") && NagaCombat.isEvent(id)) {
            getNagaCombat().handleEvent(id);
            return;
        }
        if (getIdPath().equals("crystalitegolem") && GolemCombat.isEvent(id)) {
            getGolemCombat().handleEvent(id);
            return;
        }
        if (getIdPath().equals("saltcrabking") && CrabCombat.isEvent(id)) {
            getCrabCombat().handleEvent(id);
            return;
        }
        if (getIdPath().equals("eldershulker") && ShulkerCombat.isEvent(id)) {
            getShulkerCombat().handleEvent(id);
            return;
        }
        if (id < EVENT_FEATHER || id > EVENT_RIPPLE + RIPPLE_RADII.length - 1) {
            super.handleEntityEvent(id);
            return;
        }
        RandomSource random = getRandom();
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        Level level = level();
        switch (id) {
            case EVENT_FEATHER -> {
                // 羽刃齐射：胸前冲击环 + 向前喷散的碎鳞
                double cx = getX() + fx * 2.5D;
                double cy = getY() + 2.5D;
                double cz = getZ() + fz * 2.5D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, cx, cy, cz, 5.0D, 10.0D, 0.0D);
                for (int i = 0; i < 20; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), cx, cy, cz, fx * 0.6D + random.nextGaussian() * 0.2D, 0.1D + random.nextDouble() * 0.2D, fz * 0.6D + random.nextGaussian() * 0.2D);
                }
            }
            case EVENT_METEOR -> {
                // 陨晶召唤：头顶天空展开三层金环，碎光冲天
                for (int i = 0; i < 3; i++) {
                    level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() + 10.0D + i * 5.0D, getZ(), 8.0D + i * 4.0D, 20.0D + i * 6.0D, 0.0D);
                }
                for (int i = 0; i < 24; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), getX() + random.nextGaussian(), getY() + 6.0D, getZ() + random.nextGaussian(), random.nextGaussian() * 0.15D, 0.9D + random.nextDouble() * 0.5D, random.nextGaussian() * 0.15D);
                }
            }
            case EVENT_GALE -> {
                // 龙翼飓风：三层冲击波 + 径向风痕
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() - 1.5D, getZ(), 14.0D, 16.0D, 0.0D);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() + 1.0D, getZ(), 12.0D, 14.0D, 0.0D);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() + 3.5D, getZ(), 10.0D, 12.0D, 0.0D);
                for (int i = 0; i < 40; i++) {
                    double angle = Math.PI * 2.0D * i / 40.0D;
                    double dx = Math.cos(angle);
                    double dz = Math.sin(angle);
                    level.addParticle(ModParticles.WHITESMOKE.get(), getX() + dx * 2.0D, getY() + 1.0D + random.nextDouble() * 2.0D, getZ() + dz * 2.0D, dx * 0.9D, 0.0D, dz * 0.9D);
                }
            }
            case EVENT_CLAW_RIGHT, EVENT_CLAW_LEFT -> {
                // 爪击：前方贴地三道爪痕刀光 + 前喷碎鳞
                double mirror = 0.0D;
                if (id == EVENT_CLAW_LEFT) {
                    mirror = 1.0D;
                }
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SLASH.get(), true, getX() + fx * 3.5D, getY() + 1.2D, getZ() + fz * 3.5D, 4.5D, this.yBodyRot, mirror);
                for (int i = 0; i < 12; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), getX() + fx * 4.0D, getY() + 1.0D, getZ() + fz * 4.0D, fx * 0.5D + random.nextGaussian() * 0.2D, 0.2D + random.nextDouble() * 0.2D, fz * 0.5D + random.nextGaussian() * 0.2D);
                }
            }
            case EVENT_STOMP, EVENT_STOMP + 1, EVENT_STOMP + 2 -> {
                // 重踏地震波：每圈冲击环 + 环带上的地面方块被震起 + 少量方块碎屑
                int wave = id - EVENT_STOMP;
                double radius = STOMP_WAVES[wave + 1];
                double gy = getY() + 0.15D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), gy, getZ(), radius + 1.0D, 10.0D, 0.0D);
                DragonSkillEntity.spawnQuakeRing(level, getX(), getY(), getZ(), radius - 1.3D, radius, 0.36D - wave * 0.04D, random);
                for (int i = 0; i < 8; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double x = getX() + Math.cos(angle) * radius;
                    double z = getZ() + Math.sin(angle) * radius;
                    BlockState ground = level.getBlockState(BlockPos.containing(x, getY() - 0.5D, z));
                    if (!ground.isAir()) {
                        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, gy + 0.2D, z, 0.0D, 0.4D, 0.0D);
                    }
                }
                if (wave == 0) {
                    DragonSkillEntity.spawnQuakeRing(level, getX() + fx * 3.0D, getY(), getZ() + fz * 3.0D, 0.0D, 2.5D, 0.45D, random);
                    for (int i = 0; i < 20; i++) {
                        double angle = random.nextDouble() * Math.PI * 2.0D;
                        level.addParticle(ModParticles.DRAGON_SHARD.get(), getX() + fx * 3.0D, gy + 0.3D, getZ() + fz * 3.0D, Math.cos(angle) * 0.3D, 0.5D + random.nextDouble() * 0.4D, Math.sin(angle) * 0.3D);
                    }
                }
            }
            case EVENT_TAIL -> {
                // 尾刃回旋：两道相对的大刀光拼成一整圈 + 旋涡风痕
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SLASH.get(), true, getX(), getY() + 1.0D, getZ(), 8.5D, this.yBodyRot, 0.0D);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SLASH.get(), true, getX(), getY() + 1.0D, getZ(), 8.5D, this.yBodyRot + 180.0F, 0.0D);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, getX(), getY() + 0.15D, getZ(), 9.0D, 10.0D, 0.0D);
                for (int i = 0; i < 24; i++) {
                    double angle = Math.PI * 2.0D * i / 24.0D;
                    double dx = Math.cos(angle);
                    double dz = Math.sin(angle);
                    level.addParticle(ModParticles.WHITESMOKE.get(), getX() + dx * 6.0D, getY() + 1.0D, getZ() + dz * 6.0D, -dz * 0.6D + dx * 0.2D, 0.02D, dx * 0.6D + dz * 0.2D);
                }
            }
            case EVENT_ROAR -> spawnDragonPhaseEffect("phase_roar");
            case EVENT_SPIKE_SLAM -> {
                // 晶棘拍地：前爪落点震起方块 + 冲击环
                double cx = getX() + fx * 4.5D;
                double cz = getZ() + fz * 4.5D;
                DragonSkillEntity.spawnQuakeRing(level, cx, getY(), cz, 0.0D, 2.2D, 0.35D, random);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, cx, getY() + 0.15D, cz, 4.0D, 8.0D, 0.0D);
                for (int i = 0; i < 12; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), cx, getY() + 0.4D, cz, random.nextGaussian() * 0.2D, 0.4D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.2D);
                }
            }
            case EVENT_CHARGE_HIT -> {
                // 冲撞命中：龙首前方碎鳞爆散 + 小冲击环
                double cx = getX() + fx * 4.0D;
                double cz = getZ() + fz * 4.0D;
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, cx, getY() + 1.5D, cz, 3.0D, 8.0D, 0.0D);
                for (int i = 0; i < 16; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), cx, getY() + 1.5D, cz, random.nextGaussian() * 0.3D, 0.2D + random.nextDouble() * 0.3D, random.nextGaussian() * 0.3D);
                }
            }
            case EVENT_TAIL_SLAM -> {
                // 尾击：尾巴落线上的方块整排震起，尾尖冲击环 + 碎鳞 + 扬尘
                double x0 = getX() - fx * 2.0D;
                double z0 = getZ() - fz * 2.0D;
                double x1 = getX() - fx * 9.0D;
                double z1 = getZ() - fz * 9.0D;
                DragonSkillEntity.spawnQuakeLine(level, x0, getY(), z0, x1, z1, 1.6D, 0.42D, random);
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, x1, getY() + 0.15D, z1, 4.5D, 10.0D, 0.0D);
                for (int i = 0; i < 18; i++) {
                    level.addParticle(ModParticles.DRAGON_SHARD.get(), x1, getY() + 0.5D, z1, random.nextGaussian() * 0.25D, 0.4D + random.nextDouble() * 0.4D, random.nextGaussian() * 0.25D);
                }
                for (int i = 0; i < 10; i++) {
                    double t = i / 9.0D;
                    level.addParticle(ModParticles.WHITESMOKE.get(), Mth.lerp(t, x0, x1), getY() + 0.4D, Mth.lerp(t, z0, z1), -fz * 0.15D, 0.04D, fx * 0.15D);
                    level.addParticle(ModParticles.WHITESMOKE.get(), Mth.lerp(t, x0, x1), getY() + 0.4D, Mth.lerp(t, z0, z1), fz * 0.15D, 0.04D, -fx * 0.15D);
                }
            }
            case EVENT_RIPPLE, EVENT_RIPPLE + 1, EVENT_RIPPLE + 2, EVENT_RIPPLE + 3, EVENT_RIPPLE + 4 -> {
                // 震荡波：以吐息落点为圆心，一圈冲击环 + 一圈震起的方块；第一道带中心爆发
                int ring = id - EVENT_RIPPLE;
                Vec3 center = getDragonBeamEnd();
                double outer = RIPPLE_RADII[ring];
                level.addAlwaysVisibleParticle(ModParticles.DRAGON_SHOCKWAVE.get(), true, center.x, center.y + 0.15D, center.z, outer + 0.5D, 9.0D, 0.0D);
                DragonSkillEntity.spawnQuakeRing(level, center.x, center.y, center.z, outer - 1.2D, outer, 0.4D - ring * 0.04D, random);
                if (ring == 0) {
                    for (int i = 0; i < 24; i++) {
                        double angle = random.nextDouble() * Math.PI * 2.0D;
                        level.addParticle(ModParticles.DRAGON_SHARD.get(), center.x, center.y + 0.3D, center.z, Math.cos(angle) * 0.3D, 0.6D + random.nextDouble() * 0.5D, Math.sin(angle) * 0.3D);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static final class DragonSkyCombatGoal extends Goal {
        private final RebornMobEntity dragon;

        private DragonSkyCombatGoal(RebornMobEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = dragon.getTarget();
            return dragon.isDragonPhaseOne() && !dragon.isDragonTransitioning() && target != null && target.isAlive();
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
            dragon.stopDragonSkill();
        }

        @Override
        public void tick() {
            dragon.tickSkyCombat(dragon.getTarget());
        }
    }

    // 二阶段地面战：靠近目标，按距离、方位与冷却挑选技能并执行时间轴
    private void tickGroundCombat(LivingEntity target) {
        for (int i = 1; i < this.skillCooldowns.length; i++) {
            if (this.skillCooldowns[i] > 0) {
                this.skillCooldowns[i]--;
            }
        }
        int skill = getDragonSkill();
        double distance = distanceTo(target);
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
        Vec3 toTarget = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (toTarget.lengthSqr() > 1.0E-4D) {
            toTarget = toTarget.normalize();
        }
        if (skill == 0) {
            // 追击：超出 6 格就寻路靠近
            faceDragonTowards(target.getX(), target.getZ(), 10.0F);
            if (distance > 6.0D) {
                if (this.tickCount % 10 == 0) {
                    getNavigation().moveTo(target, 1.0D);
                }
            } else {
                getNavigation().stop();
            }
            if (this.skillGap > 0) {
                this.skillGap--;
                return;
            }
            double front = toTarget.dot(forward);
            int chosen = 0;
            if (distance < 6.5D && front < -0.3D && this.skillCooldowns[SKILL_TAIL] <= 0) {
                // 被贴背优先甩尾
                chosen = SKILL_TAIL;
            } else if (distance >= 5.0D && distance <= 12.0D && front < -0.2D && this.skillCooldowns[SKILL_TAIL_SLAM] <= 0) {
                // 身后稍远处：转身用尾巴砸
                chosen = SKILL_TAIL_SLAM;
            } else {
                List<Integer> ready = new ArrayList<>();
                if (distance <= 7.5D && front > 0.5D && this.skillCooldowns[SKILL_CLAW_RIGHT] <= 0) {
                    ready.add(SKILL_CLAW_RIGHT);
                    ready.add(SKILL_CLAW_RIGHT);
                    ready.add(SKILL_CLAW_RIGHT);
                }
                if (distance <= 10.0D && this.skillCooldowns[SKILL_STOMP] <= 0) {
                    ready.add(SKILL_STOMP);
                }
                if (distance >= 8.0D && distance <= 24.0D && this.skillCooldowns[SKILL_CHARGE] <= 0) {
                    ready.add(SKILL_CHARGE);
                }
                if (distance >= 11.0D && this.skillCooldowns[SKILL_SPIKE] <= 0) {
                    ready.add(SKILL_SPIKE);
                }
                if (distance >= 6.0D && distance <= 16.0D && this.skillCooldowns[SKILL_QUAKE_BREATH] <= 0) {
                    ready.add(SKILL_QUAKE_BREATH);
                }
                if (distance >= 6.0D && this.skillCooldowns[SKILL_ROAR] <= 0) {
                    ready.add(SKILL_ROAR);
                }
                if (!ready.isEmpty()) {
                    chosen = ready.get(getRandom().nextInt(ready.size()));
                }
            }
            if (chosen == 0) {
                return;
            }
            // 爪击按目标所在一侧选择左右爪，两侧共用冷却
            if (chosen == SKILL_CLAW_RIGHT) {
                this.skillCooldowns[SKILL_CLAW_LEFT] = SKILL_COOLDOWN[SKILL_CLAW_LEFT];
                if (toTarget.dot(right) < 0.0D) {
                    chosen = SKILL_CLAW_LEFT;
                }
            }
            this.skillCooldowns[chosen] = SKILL_COOLDOWN[chosen];
            this.skillCooldowns[SKILL_CLAW_RIGHT] = Math.max(this.skillCooldowns[SKILL_CLAW_RIGHT], this.skillCooldowns[SKILL_CLAW_LEFT]);
            this.skillTicks = 0;
            this.chargeHits.clear();
            getNavigation().stop();
            this.entityData.set(DRAGON_SKILL, chosen);
            return;
        }
        getNavigation().stop();
        this.skillTicks++;
        playSkillCue(skill, this.skillTicks);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        switch (skill) {
            case SKILL_CLAW_RIGHT, SKILL_CLAW_LEFT -> {
                // 裂空爪击：后仰蓄力，1.15 秒扑下，前方 120° 扇形
                if (this.skillTicks < 18) {
                    faceDragonTowards(target.getX(), target.getZ(), 6.0F);
                }
                if (this.skillTicks == 23) {
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7.5D, 3.0D, 7.5D), this::canDragonSkillHit)) {
                        Vec3 to = living.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                        double reach = to.length();
                        if (reach > 8.0D) {
                            continue;
                        }
                        if (reach > 3.0D && to.normalize().dot(forward) < 0.5D) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 14.0F);
                        Vec3 push = forward;
                        if (reach > 0.1D) {
                            push = to.normalize();
                        }
                        applyBossKnockback(living, new Vec3(push.x * 0.6D, 0.2D, push.z * 0.6D));
                        living.hurtMarked = true;
                    }
                    ModSounds.play(level(), "dragon_claw_hit", getX(), getY(), getZ(), 3.5F, 1.0F);
                    if (skill == SKILL_CLAW_RIGHT) {
                        level().broadcastEntityEvent(this, EVENT_CLAW_RIGHT);
                    } else {
                        level().broadcastEntityEvent(this, EVENT_CLAW_LEFT);
                    }
                }
            }
            case SKILL_STOMP -> {
                // 天坠重踏：人立 3 秒后砸地，三圈地震波依次外扩，只命中地面目标
                if (this.skillTicks < 60) {
                    faceDragonTowards(target.getX(), target.getZ(), 3.0F);
                }
                int wave = -1;
                if (this.skillTicks == 64) {
                    wave = 0;
                } else if (this.skillTicks == 70) {
                    wave = 1;
                } else if (this.skillTicks == 76) {
                    wave = 2;
                }
                if (wave >= 0) {
                    double inner = STOMP_WAVES[wave];
                    double outer = STOMP_WAVES[wave + 1];
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(outer + 1.0D, 3.0D, outer + 1.0D), this::canDragonSkillHit)) {
                        Vec3 to = living.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                        double reach = to.length();
                        if (!living.onGround() || reach < inner - 1.0D || reach > outer + 1.0D) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 10.0F);
                        Vec3 push = Vec3.ZERO;
                        if (reach > 0.1D) {
                            push = to.normalize().scale(0.6D);
                        }
                        applyBossKnockback(living, new Vec3(push.x * 0.5D, 0.4D, push.z * 0.5D));
                        living.hurtMarked = true;
                    }
                    if (wave == 0) {
                        ModSounds.play(level(), "dragon_ground_slam", getX(), getY(), getZ(), 4.5F, 1.0F);
                    } else {
                        ModSounds.play(level(), "dragon_quake_wave", getX(), getY(), getZ(), 3.0F - wave * 0.5F, 1.0F + wave * 0.05F);
                    }
                    level().broadcastEntityEvent(this, (byte) (EVENT_STOMP + wave));
                }
            }
            case SKILL_CHARGE -> {
                // 龙首冲撞：0-16 蓄力瞄准，17-38 直线冲锋，撞到墙提前收招
                if (this.skillTicks <= 16) {
                    faceDragonTowards(target.getX(), target.getZ(), 10.0F);
                }
                if (this.skillTicks == 16) {
                    this.chargeDir = forward;
                }
                if (this.skillTicks > 16 && this.skillTicks <= 38) {
                    setDeltaMovement(this.chargeDir.x * 0.8D, getDeltaMovement().y, this.chargeDir.z * 0.8D);
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.2D), this::canDragonSkillHit)) {
                        if (!this.chargeHits.add(living.getId())) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 12.0F);
                        applyBossKnockback(living, new Vec3(this.chargeDir.x * 0.9D, 0.3D, this.chargeDir.z * 0.9D));
                        living.hurtMarked = true;
                        ModSounds.play(level(), "dragon_charge_impact", living.getX(), living.getY(), living.getZ(), 3.0F, 1.0F);
                        level().broadcastEntityEvent(this, EVENT_CHARGE_HIT);
                    }
                    if (this.horizontalCollision && this.skillTicks > 19) {
                        this.skillTicks = 38;
                        ModSounds.play(level(), "dragon_ground_slam", getX(), getY(), getZ(), 2.5F, 1.2F);
                        level().broadcastEntityEvent(this, EVENT_CHARGE_HIT);
                    }
                }
            }
            case SKILL_TAIL -> {
                // 尾刃回旋：0.7 秒尾巴扫过一整圈
                if (this.skillTicks == 14) {
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8.5D, 2.0D, 8.5D), this::canDragonSkillHit)) {
                        Vec3 to = living.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
                        if (to.length() > 9.0D) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 10.0F);
                        Vec3 push = forward;
                        if (to.lengthSqr() > 0.01D) {
                            push = to.normalize();
                        }
                        applyBossKnockback(living, new Vec3(push.x * 0.8D, 0.25D, push.z * 0.8D));
                        living.hurtMarked = true;
                    }
                    ModSounds.play(level(), "dragon_claw_hit", getX(), getY(), getZ(), 2.5F, 0.8F);
                    level().broadcastEntityEvent(this, EVENT_TAIL);
                }
            }
            case SKILL_SPIKE -> {
                // 晶棘潮涌：拍地后朝目标方向依次冒出 14 根晶棘
                if (this.skillTicks < 15) {
                    faceDragonTowards(target.getX(), target.getZ(), 8.0F);
                }
                if (this.skillTicks == 15) {
                    Vec3 dir = toTarget;
                    if (dir.lengthSqr() < 0.5D) {
                        dir = forward;
                    }
                    Vec3 start = position().add(dir.scale(4.0D));
                    for (int i = 0; i < 14; i++) {
                        Vec3 p = start.add(dir.scale(1.7D * i));
                        double groundY = findGroundY(p.x, getY() + 4.0D, p.z, 10.0D, getY());
                        DragonSkillEntity.spawn(server, this, DragonSkillEntity.SPIKE, new Vec3(p.x, groundY, p.z), Vec3.ZERO, groundY, 8 + i * 2, target);
                    }
                    ModSounds.play(level(), "dragon_ground_slam", getX(), getY(), getZ(), 2.5F, 1.1F);
                    ModSounds.play(level(), "dragon_crystal_summon", getX(), getY(), getZ(), 2.5F, 1.0F);
                    level().broadcastEntityEvent(this, EVENT_SPIKE_SLAM);
                }
            }
            case SKILL_ROAR -> {
                // 雷霆咆哮：咆哮后在身边召唤内外两圈落雷，远处目标脚下追加一道
                if (this.skillTicks == 18) {
                    level().broadcastEntityEvent(this, EVENT_ROAR);
                }
                if (this.skillTicks == 22 || this.skillTicks == 34) {
                    ModSounds.play(level(), "dragon_thunder_mark", getX(), getY(), getZ(), 3.0F, 1.0F);
                    int count = 6;
                    double radius = 7.0D;
                    double offset = 0.0D;
                    if (this.skillTicks == 34) {
                        count = 10;
                        radius = 13.0D;
                        offset = Math.PI / 10.0D;
                    }
                    for (int i = 0; i < count; i++) {
                        double angle = offset + Math.PI * 2.0D * i / count;
                        double x = getX() + Math.cos(angle) * radius;
                        double z = getZ() + Math.sin(angle) * radius;
                        double groundY = findGroundY(x, getY() + 4.0D, z, 12.0D, getY());
                        DragonSkillEntity.spawn(server, this, DragonSkillEntity.THUNDER, new Vec3(x, groundY, z), Vec3.ZERO, groundY, 0, target);
                    }
                    if (this.skillTicks == 34 && distance > 15.0D) {
                        double groundY = findGroundY(target.getX(), target.getY() + 1.0D, target.getZ(), 12.0D, target.getY());
                        DragonSkillEntity.spawn(server, this, DragonSkillEntity.THUNDER, new Vec3(target.getX(), groundY, target.getZ()), Vec3.ZERO, groundY, 0, target);
                    }
                }
            }
            case SKILL_TAIL_SLAM -> {
                // 天尾坠击：转身让尾巴对准目标，0.95 秒整条尾巴砸下，命中身后 2~9 格的直线
                if (this.skillTicks <= 15) {
                    faceDragonTowards(getX() * 2.0D - target.getX(), getZ() * 2.0D - target.getZ(), 9.0F);
                }
                if (this.skillTicks == 19) {
                    Vec3 from = position().subtract(forward.scale(2.0D));
                    Vec3 to = position().subtract(forward.scale(9.0D));
                    Vec3 segment = to.subtract(from);
                    double lengthSqr = segment.lengthSqr();
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(2.5D, 3.0D, 2.5D), this::canDragonSkillHit)) {
                        Vec3 flat = new Vec3(living.getX(), from.y, living.getZ());
                        double t = Mth.clamp(flat.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
                        Vec3 nearest = from.add(segment.scale(t));
                        if (nearest.distanceToSqr(flat) > 2.2D * 2.2D) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 13.0F);
                        Vec3 push = flat.subtract(nearest);
                        if (push.lengthSqr() > 0.01D) {
                            push = push.normalize();
                        }
                        applyBossKnockback(living, new Vec3(push.x * 0.4D, 0.3D, push.z * 0.4D));
                        living.hurtMarked = true;
                    }
                    ModSounds.play(level(), "dragon_ground_slam", to.x, to.y, to.z, 4.0F, 0.9F);
                    ModSounds.play(level(), "dragon_quake_wave", to.x, to.y, to.z, 3.0F, 1.0F);
                    level().broadcastEntityEvent(this, EVENT_TAIL_SLAM);
                }
            }
            case SKILL_QUAKE_BREATH -> {
                // 天穹震息：1.0~2.2 秒向前下方地面喷吐，2.3 秒起从落点外扩五道震荡波
                if (this.skillTicks < 20) {
                    faceDragonTowards(target.getX(), target.getZ(), 8.0F);
                }
                if (this.skillTicks == 20) {
                    double px = getX() + forward.x * 3.5D;
                    double pz = getZ() + forward.z * 3.5D;
                    this.quakeCenter = new Vec3(px, findGroundY(px, getY() + 3.0D, pz, 8.0D, getY()), pz);
                    this.entityData.set(DRAGON_BEAM_X, (float) this.quakeCenter.x);
                    this.entityData.set(DRAGON_BEAM_Y, (float) this.quakeCenter.y);
                    this.entityData.set(DRAGON_BEAM_Z, (float) this.quakeCenter.z);
                    this.entityData.set(DRAGON_BEAM, true);
                }
                if (this.skillTicks > 20 && this.skillTicks <= 44 && this.skillTicks % 4 == 0) {
                    // 吐息落点持续伤害
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(this.quakeCenter, this.quakeCenter).inflate(3.0D, 3.0D, 3.0D), this::canDragonSkillHit)) {
                        if (living.position().distanceToSqr(this.quakeCenter) <= 9.0D) {
                            living.hurt(damageSources().indirectMagic(this, this), 5.0F);
                        }
                    }
                    server.playSound(null, this.quakeCenter.x, this.quakeCenter.y, this.quakeCenter.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.0F, 0.5F);
                }
                if (this.skillTicks == 45) {
                    this.entityData.set(DRAGON_BEAM, false);
                }
                int ring = (this.skillTicks - 46) / 4;
                if (this.skillTicks >= 46 && (this.skillTicks - 46) % 4 == 0 && ring < RIPPLE_RADII.length) {
                    // 震荡波：命中当前环带内站在地面的目标
                    double outer = RIPPLE_RADII[ring];
                    double inner = outer - 3.0D;
                    for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, new AABB(this.quakeCenter, this.quakeCenter).inflate(outer + 1.0D, 3.0D, outer + 1.0D), this::canDragonSkillHit)) {
                        Vec3 to = living.position().subtract(this.quakeCenter).multiply(1.0D, 0.0D, 1.0D);
                        double reach = to.length();
                        if (!living.onGround() || reach < inner - 0.5D || reach > outer + 0.8D) {
                            continue;
                        }
                        living.hurt(damageSources().mobAttack(this), 8.0F);
                        Vec3 push = Vec3.ZERO;
                        if (reach > 0.1D) {
                            push = to.normalize().scale(0.4D);
                        }
                        applyBossKnockback(living, new Vec3(push.x * 0.5D, 0.35D, push.z * 0.5D));
                        living.hurtMarked = true;
                    }
                    if (ring == 0) {
                        ModSounds.play(level(), "dragon_ground_slam", this.quakeCenter.x, this.quakeCenter.y, this.quakeCenter.z, 4.5F, 0.9F);
                    } else {
                        ModSounds.play(level(), "dragon_quake_wave", this.quakeCenter.x, this.quakeCenter.y, this.quakeCenter.z, 3.2F - ring * 0.4F, 0.9F + ring * 0.05F);
                    }
                    level().broadcastEntityEvent(this, (byte) (EVENT_RIPPLE + ring));
                }
            }
            default -> {
            }
        }
        if (this.skillTicks >= SKILL_DURATION[skill]) {
            stopDragonSkill();
            this.skillGap = 15;
        }
    }

    // 技能提示音时间轴：蓄力、低吼、挥动、持续音、脚步等与命中无关的声音
    private void playSkillCue(int skill, int tick) {
        switch (skill) {
            case SKILL_BREATH, SKILL_QUAKE_BREATH -> {
                int beamEnd = 68;
                if (skill == SKILL_QUAKE_BREATH) {
                    beamEnd = 44;
                }
                if (tick == 1) {
                    playCue("dragon_breath_charge", 3.0F, 1.0F);
                }
                if (tick == 20) {
                    playCue("dragon_breath_fire", 4.0F, 1.0F);
                    playCue("dragon_roar", 3.0F, 1.1F);
                }
                if (tick > 20 && tick <= beamEnd && tick % 10 == 0) {
                    playCue("dragon_breath_loop", 3.0F, 1.0F);
                }
            }
            case SKILL_FEATHER -> {
                if (tick == 8) {
                    playCue("dragon_whoosh", 3.0F, 1.0F);
                }
            }
            case SKILL_METEOR -> {
                if (tick == 12) {
                    playCue("dragon_roar", 4.0F, 0.9F);
                }
                if (tick == 16) {
                    playCue("dragon_crystal_summon", 4.0F, 1.0F);
                }
            }
            case SKILL_THUNDER -> {
                if (tick == 4) {
                    playCue("dragon_vocal_attack", 2.5F, 1.1F);
                }
            }
            case SKILL_GALE -> {
                if (tick == 6) {
                    playCue("dragon_whoosh", 3.0F, 0.9F);
                }
            }
            case SKILL_CLAW_RIGHT, SKILL_CLAW_LEFT -> {
                if (tick == 2) {
                    playCue("dragon_vocal_attack", 3.0F, 1.0F);
                }
                if (tick == 20) {
                    playCue("dragon_whoosh", 3.0F, 1.0F);
                }
            }
            case SKILL_STOMP -> {
                if (tick == 2) {
                    playCue("dragon_roar", 4.0F, 0.8F);
                }
                if (tick >= 22 && tick <= 58 && (tick - 22) % 9 == 0) {
                    playCue("dragon_heavy_step", 2.5F, 1.0F);
                }
                if (tick == 60) {
                    playCue("dragon_whoosh", 3.0F, 0.7F);
                }
            }
            case SKILL_CHARGE -> {
                if (tick == 2) {
                    playCue("dragon_vocal_attack", 3.0F, 0.9F);
                }
                if (tick == 16) {
                    playCue("dragon_roar", 4.0F, 1.0F);
                }
                if (tick > 16 && tick <= 38 && tick % 4 == 0) {
                    playCue("dragon_heavy_step", 3.0F, 1.0F);
                }
            }
            case SKILL_TAIL -> {
                if (tick == 4) {
                    playCue("dragon_vocal_attack", 3.0F, 1.0F);
                }
                if (tick == 10) {
                    playCue("dragon_tail_whip", 3.5F, 1.0F);
                }
            }
            case SKILL_SPIKE -> {
                if (tick == 2) {
                    playCue("dragon_vocal_attack", 3.0F, 1.0F);
                }
                if (tick == 12) {
                    playCue("dragon_whoosh", 2.5F, 1.0F);
                }
            }
            case SKILL_ROAR -> {
                if (tick == 18) {
                    playCue("dragon_roar", 5.0F, 0.6F);
                    playCue("dragon_roar", 3.0F, 0.8F);
                }
            }
            case SKILL_TAIL_SLAM -> {
                if (tick == 2) {
                    playCue("dragon_vocal_attack", 3.0F, 1.0F);
                }
                if (tick == 8) {
                    playCue("dragon_tail_whip", 3.0F, 0.8F);
                }
                if (tick == 16) {
                    playCue("dragon_whoosh", 3.0F, 0.6F);
                }
            }
            default -> {
            }
        }
    }

    private void playCue(String id, float volume, float pitch) {
        ModSounds.play(level(), id, getX(), getY(), getZ(), volume, pitch);
    }

    private static final class DragonGroundCombatGoal extends Goal {
        private final RebornMobEntity dragon;

        private DragonGroundCombatGoal(RebornMobEntity dragon) {
            this.dragon = dragon;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = dragon.getTarget();
            return dragon.isDragonPhaseTwo() && !dragon.isDragonTransitioning() && target != null && target.isAlive();
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
            dragon.stopDragonSkill();
            dragon.getNavigation().stop();
        }

        @Override
        public void tick() {
            dragon.tickGroundCombat(dragon.getTarget());
        }
    }

    private static final class DragonFlightStrollGoal extends RandomStrollGoal {
        private final RebornMobEntity dragon;

        private DragonFlightStrollGoal(RebornMobEntity dragon, double speed, int interval) {
            super(dragon, speed, interval);
            this.dragon = dragon;
        }

        @Override
        public boolean canUse() {
            return dragon.isDragonPhaseOne() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return dragon.isDragonPhaseOne() && super.canContinueToUse();
        }

        @Override
        protected Vec3 getPosition() {
            RandomSource random = dragon.getRandom();
            double x = dragon.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            double y = dragon.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            double z = dragon.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            return new Vec3(x, y, z);
        }
    }

    private static final class DragonGroundStrollGoal extends RandomStrollGoal {
        private final RebornMobEntity dragon;

        private DragonGroundStrollGoal(RebornMobEntity dragon, double speed) {
            super(dragon, speed);
            this.dragon = dragon;
        }

        @Override
        public boolean canUse() {
            return dragon.isDragonPhaseTwo() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return dragon.isDragonPhaseTwo() && super.canContinueToUse();
        }
    }
}






