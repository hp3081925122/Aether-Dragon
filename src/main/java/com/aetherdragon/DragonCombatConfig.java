package com.aetherdragon;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DragonCombatConfig {
    public static final double DEFAULT_ATTACK_DAMAGE = 10.0D;
    public static final double PHASE_TWO_HEALTH_RATIO = 0.5D;
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue ATTACK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BREATH;
    public static final ForgeConfigSpec.DoubleValue FEATHER;
    public static final ForgeConfigSpec.DoubleValue CRYSTAL;
    public static final ForgeConfigSpec.DoubleValue THUNDER;
    public static final ForgeConfigSpec.DoubleValue GALE;
    public static final ForgeConfigSpec.DoubleValue CLAW;
    public static final ForgeConfigSpec.DoubleValue STOMP;
    public static final ForgeConfigSpec.DoubleValue CHARGE;
    public static final ForgeConfigSpec.DoubleValue TAIL;
    public static final ForgeConfigSpec.DoubleValue SPIKE;
    public static final ForgeConfigSpec.DoubleValue TAIL_SLAM;
    public static final ForgeConfigSpec.DoubleValue QUAKE_BREATH;
    public static final ForgeConfigSpec.DoubleValue RIPPLE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
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
        SPEC = builder.build();
    }

    private DragonCombatConfig() {
    }
}
