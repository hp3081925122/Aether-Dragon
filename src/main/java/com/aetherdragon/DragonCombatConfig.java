package com.aetherdragon;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DragonCombatConfig {
    public static final double DEFAULT_ATTACK_DAMAGE = 10.0D;
    public static final double PHASE_TWO_HEALTH_RATIO = 0.5D;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue BREATH;
    public static final ModConfigSpec.DoubleValue FEATHER;
    public static final ModConfigSpec.DoubleValue CRYSTAL;
    public static final ModConfigSpec.DoubleValue THUNDER;
    public static final ModConfigSpec.DoubleValue GALE;
    public static final ModConfigSpec.DoubleValue CLAW;
    public static final ModConfigSpec.DoubleValue STOMP;
    public static final ModConfigSpec.DoubleValue CHARGE;
    public static final ModConfigSpec.DoubleValue TAIL;
    public static final ModConfigSpec.DoubleValue SPIKE;
    public static final ModConfigSpec.DoubleValue TAIL_SLAM;
    public static final ModConfigSpec.DoubleValue QUAKE_BREATH;
    public static final ModConfigSpec.DoubleValue RIPPLE;
    public static final ModConfigSpec.DoubleValue MOUNT_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue MOUNT_FLY_SPEED;
    public static final ModConfigSpec.DoubleValue MOUNT_LASER_DAMAGE;
    public static final ModConfigSpec.DoubleValue MOUNT_LASER_ATTACK_SCALE;
    public static final ModConfigSpec.DoubleValue MOUNT_LASER_RANGE;
    public static final ModConfigSpec.DoubleValue MOUNT_WALK_SPEED;
    public static final ModConfigSpec.IntValue MOUNT_RESPAWN_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("dragon");
        ATTACK_DAMAGE = builder.comment("天空巨龙基础攻击力；重启后应用 / Base attack damage; applied after restart")
                .worldRestart().defineInRange("attackDamage", DEFAULT_ATTACK_DAMAGE, 0.0D, 2048.0D);
        builder.push("skillMultipliers");
        BREATH = builder.comment("天穹龙息每次命中伤害倍率 / Damage multiplier for breath")
                .defineInRange("breath", 0.5D, 0.0D, 100.0D);
        FEATHER = builder.comment("风暴羽刃伤害倍率 / Damage multiplier for feather")
                .defineInRange("feather", 0.6D, 0.0D, 100.0D);
        CRYSTAL = builder.comment("苍穹陨晶伤害倍率 / Damage multiplier for crystal")
                .defineInRange("crystal", 1.0D, 0.0D, 100.0D);
        THUNDER = builder.comment("天雷审判与雷霆咆哮的落雷伤害倍率 / Damage multiplier for thunder")
                .defineInRange("thunder", 1.2D, 0.0D, 100.0D);
        GALE = builder.comment("龙翼飓风伤害倍率 / Damage multiplier for gale")
                .defineInRange("gale", 0.4D, 0.0D, 100.0D);
        CLAW = builder.comment("左右裂空爪击伤害倍率 / Damage multiplier for claw")
                .defineInRange("claw", 1.4D, 0.0D, 100.0D);
        STOMP = builder.comment("天坠重踏每道震波伤害倍率 / Damage multiplier for stomp")
                .defineInRange("stomp", 1.0D, 0.0D, 100.0D);
        CHARGE = builder.comment("龙首冲撞伤害倍率 / Damage multiplier for charge")
                .defineInRange("charge", 1.2D, 0.0D, 100.0D);
        TAIL = builder.comment("尾刃回旋伤害倍率 / Damage multiplier for tail")
                .defineInRange("tail", 1.0D, 0.0D, 100.0D);
        SPIKE = builder.comment("晶棘潮涌伤害倍率 / Damage multiplier for spike")
                .defineInRange("spike", 0.9D, 0.0D, 100.0D);
        TAIL_SLAM = builder.comment("天尾坠击伤害倍率 / Damage multiplier for tailSlam")
                .defineInRange("tailSlam", 1.3D, 0.0D, 100.0D);
        QUAKE_BREATH = builder.comment("天穹震息落点每次命中伤害倍率 / Damage multiplier for quakeBreath")
                .defineInRange("quakeBreath", 0.5D, 0.0D, 100.0D);
        RIPPLE = builder.comment("天穹震息每道震荡波伤害倍率 / Damage multiplier for ripple")
                .defineInRange("ripple", 0.8D, 0.0D, 100.0D);
        builder.pop();
        builder.pop();
        builder.push("mount");
        MOUNT_MAX_HEALTH = builder.comment("驯服天境龙的最大生命值 / Max health of the tamed Aether Dragon mount")
                .defineInRange("maxHealth", 300.0D, 1.0D, 4096.0D);
        MOUNT_FLY_SPEED = builder.comment("驯服天境龙的飞行速度（格/刻）/ Flying speed of the mount in blocks per tick")
                .defineInRange("flySpeed", 2.4D, 0.1D, 5.0D);
        MOUNT_LASER_DAMAGE = builder.comment("骑乘激光每 4 刻结算一次的基础伤害 / Base mount laser damage dealt every 4 ticks")
                .defineInRange("laserDamage", 5.0D, 0.0D, 2048.0D);
        MOUNT_LASER_ATTACK_SCALE = builder.comment("骑乘激光额外伤害 = 骑手攻击力 × 此倍率 / Extra laser damage = rider attack damage × this multiplier")
                .defineInRange("laserAttackScale", 0.2D, 0.0D, 100.0D);
        MOUNT_LASER_RANGE = builder.comment("骑乘激光最大射程（格）/ Maximum range of the mount laser in blocks")
                .defineInRange("laserRange", 48.0D, 4.0D, 128.0D);
        MOUNT_WALK_SPEED = builder.comment("驯服天境龙的地面移动速度 / Ground movement speed of the mount")
                .defineInRange("walkSpeed", 0.35D, 0.05D, 2.0D);
        MOUNT_RESPAWN_SECONDS = builder.comment("驯服天境龙死亡后再次召唤的冷却秒数 / Seconds before a dead mount can be summoned again")
                .defineInRange("respawnSeconds", 300, 0, 86400);
        builder.pop();
        SPEC = builder.build();
    }

    private DragonCombatConfig() {
    }
}
