package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, AetherDragon.MOD_ID);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> RUNESEFFECT = PARTICLES.register("runeseffect", () -> new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> WHITESMOKE = PARTICLES.register("whitesmoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> FIRE = PARTICLES.register("fire", () -> new SimpleParticleType(false));
    // 艾瑟龙转阶段冲击波与碎鳞
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DRAGON_SHOCKWAVE = PARTICLES.register("dragon_shockwave", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DRAGON_SHARD = PARTICLES.register("dragon_shard", () -> new SimpleParticleType(true));
    // 艾瑟龙技能落点预警环
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DRAGON_WARNING = PARTICLES.register("dragon_warning", () -> new SimpleParticleType(true));
    // 艾瑟龙二阶段：爪痕刀光、被震起的地面方块
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> DRAGON_SLASH = PARTICLES.register("dragon_slash", () -> new SimpleParticleType(true));
    // 灵魂：魂焰冲击环、预警环、剑气
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SPIRIT_RING = PARTICLES.register("spirit_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SPIRIT_WARNING = PARTICLES.register("spirit_warning", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SPIRIT_SLASH = PARTICLES.register("spirit_slash", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SPIRIT_SHARD = PARTICLES.register("spirit_shard", () -> new SimpleParticleType(true));
    // 水晶石傀儡专属粒子
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GOLEM_RING = PARTICLES.register("golem_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GOLEM_WARNING = PARTICLES.register("golem_warning", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GOLEM_SHARD = PARTICLES.register("golem_shard", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> GOLEM_PUNCH = PARTICLES.register("golem_punch", () -> new SimpleParticleType(true));
    // 天境娜迦：秘符冲击环、预警环、咬痕
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> NAGA_RING = PARTICLES.register("naga_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> NAGA_WARNING = PARTICLES.register("naga_warning", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> NAGA_SLASH = PARTICLES.register("naga_slash", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> NAGA_BITE = PARTICLES.register("naga_bite", () -> new SimpleParticleType(true));
    // 盐蟹王：海潮冲击环、预警环、盐白刀光、盐粒
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SALT_RING = PARTICLES.register("salt_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SALT_WARNING = PARTICLES.register("salt_warning", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SALT_SHARD = PARTICLES.register("salt_shard", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SALT_SPRAY = PARTICLES.register("salt_spray", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> CRAB_PINCH = PARTICLES.register("crab_pinch", () -> new SimpleParticleType(true));
    // 远古潜影贝：金青冲击环、预警环、金色碎光
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SHULKER_RING = PARTICLES.register("shulker_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SHULKER_WARNING = PARTICLES.register("shulker_warning", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> SHULKER_SPARK = PARTICLES.register("shulker_spark", () -> new SimpleParticleType(true));
    // 燔祭长矛投射物粒子
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> PYRE_RING = PARTICLES.register("pyre_ring", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> PYRE_EMBER = PARTICLES.register("pyre_ember", () -> new SimpleParticleType(true));
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, ParticleType<BlockParticleOption>> DRAGON_QUAKE_BLOCK = PARTICLES.register("dragon_quake_block", () -> new ParticleType<BlockParticleOption>(true) {
        @Override
        public com.mojang.serialization.MapCodec<BlockParticleOption> codec() {
            return BlockParticleOption.codec(this);
        }

        @Override
        public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, BlockParticleOption> streamCodec() {
            return BlockParticleOption.streamCodec(this);
        }
    });

    private ModParticles() {
    }
}

