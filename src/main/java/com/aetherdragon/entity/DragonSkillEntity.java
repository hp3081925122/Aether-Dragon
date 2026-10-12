package com.aetherdragon.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

public final class DragonSkillEntity {
    public static final int FEATHER = 1;
    public static final int CRYSTAL = 2;
    public static final int THUNDER = 3;
    public static final int SPIKE = 4;

    public static void spawn(Level level, LivingEntity owner, int kind, Vec3 pos, Vec3 velocity, double groundY, int delay, LivingEntity target) {
        if (target != null && target.isAlive() && target.distanceToSqr(pos) < 16.0D) {
            target.hurt(level.damageSources().magic(), kind == THUNDER ? 10.0F : 6.0F);
        }
        if (level instanceof ServerLevel server) server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 8, 0.4D, 0.4D, 0.4D, 0.08D);
    }

    public static void spawnQuakeRing(Level level, double x, double y, double z, double inner, double outer, double speed, RandomSource random) {
        if (level instanceof ServerLevel server) server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, x, y + 0.1D, z, 12, outer, 0.2D, outer, speed);
    }

    public static void spawnQuakeLine(Level level, double x0, double y, double z0, double x1, double z1, double width, double speed, RandomSource random) {
        if (level instanceof ServerLevel server) server.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, (x0 + x1) * 0.5D, y + 0.1D, (z0 + z1) * 0.5D, 16, width, 0.2D, width, speed);
    }

    private DragonSkillEntity() {
    }
}

