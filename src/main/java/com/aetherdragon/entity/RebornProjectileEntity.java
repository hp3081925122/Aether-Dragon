package com.aetherdragon.entity;

import com.aetherdragon.registry.ModItems;
import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import com.aetherdragon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class RebornProjectileEntity extends AbstractArrow {
    private int lifetime;

    public RebornProjectileEntity(EntityType<? extends RebornProjectileEntity> type, Level level) {
        super(type, level);
    }

    public static RebornProjectileEntity shootShulker(Level level, LivingEntity shooter, float power) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        EntityType<RebornProjectileEntity> type = ModEntities.PROJECTILES.get("shulkercrysta").get();
        RebornProjectileEntity projectile = type.create(server);
        if (projectile == null) {
            return null;
        }
        projectile.setOwner(shooter);
        projectile.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        var direction = shooter.getViewVector(1.0F);
        projectile.shoot(direction.x, direction.y, direction.z, power * 2.0F, 0.0F);
        projectile.setNoGravity(true);
        // 不再使用暴击星星拖尾，基础伤害提高到与原暴击平均值相当
        projectile.setCritArrow(false);
        projectile.setBaseDamage(1.25D);
        projectile.pickup = AbstractArrow.Pickup.ALLOWED;
        server.addFreshEntity(projectile);
        server.playSound(null, shooter.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
        return projectile;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && getIdPath().equals("shulkercrysta")) {
            spawnPyreTrail();
        }
        if (++lifetime >= 100) {
            discard();
        }
    }

    // 燔祭长矛弹体是否已插在方块上（客户端渲染用）
    public boolean isStuck() {
        return this.inGround;
    }

    // 燔祭长矛弹体拖尾：飞行时尾部洒落火星，插地后偶尔冒火苗
    private void spawnPyreTrail() {
        if (this.inGround) {
            if (this.random.nextInt(6) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        Vec3 velocity = getDeltaMovement();
        Vec3 back = position().subtract(velocity.normalize().scale(0.9D));
        level().addParticle(ModParticles.PYRE_EMBER.get(), back.x, back.y, back.z, velocity.x * -0.05D + this.random.nextGaussian() * 0.03D, 0.05D, velocity.z * -0.05D + this.random.nextGaussian() * 0.03D);
        if (this.tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.SMALL_FLAME, back.x, back.y, back.z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return getIdPath().equals("shulkercrysta") ? new ItemStack(Items.SHULKER_SHELL) : new ItemStack(ModItems.ALL.get("pyrespear").get());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        triggerImpact();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        triggerImpact();
    }

    private void triggerImpact() {
        if (getIdPath().equals("shulkercrysta") && level() instanceof ServerLevel pyre) {
            pyreImpact(pyre);
            return;
        }
        if (!getIdPath().equals("shalkbbbb") || !(level() instanceof ServerLevel server)) {
            return;
        }
        server.explode(null, getX(), getY(), getZ(), 0.0F, Level.ExplosionInteraction.NONE);
        server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 3.0D, 3.0D, 3.0D, 1.0D);
        server.sendParticles(ModParticles.FIRE.get(), getX(), getY(), getZ(), 5, 3.0D, 3.0D, 3.0D, 1.0D);
    }

    // 燔祭长矛弹体命中：熔火冲击环 + 火星迸溅 + 火焰
    private void pyreImpact(ServerLevel server) {
        server.sendParticles(ModParticles.PYRE_RING.get(), getX(), getY() + 0.1D, getZ(), 0, 2.4D, 9.0D, 1.0D, 1.0D);
        server.sendParticles(ModParticles.PYRE_EMBER.get(), getX(), getY() + 0.2D, getZ(), 14, 0.1D, 0.1D, 0.1D, 0.25D);
        server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.2D, getZ(), 8, 0.2D, 0.2D, 0.2D, 0.06D);
        server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.2D, getZ(), 2, 0.1D, 0.1D, 0.1D, 0.0D);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
        ModSounds.play(server, "shulker_orb_hit", getX(), getY(), getZ(), 0.8F, 0.9F);
    }

    private String getIdPath() {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(getType());
        return key == null ? "missing" : key.getPath();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}

