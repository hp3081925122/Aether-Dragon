package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.DragonSkillEntity;
import com.aetherdragon.entity.RebornMobEntity;
import com.aetherdragon.entity.CrabSkillEntity;
import com.aetherdragon.entity.GolemSkillEntity;
import com.aetherdragon.entity.NagaSkillEntity;
import com.aetherdragon.entity.NagaEntity;
import com.aetherdragon.entity.RebornProjectileEntity;
import com.aetherdragon.entity.ShulkerSkillEntity;
import com.aetherdragon.entity.SpiritSkillEntity;
import com.aetherdragon.entity.TamedAetherDragonEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, AetherDragon.MOD_ID);
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<RebornMobEntity>>> MOBS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<RebornProjectileEntity>>> PROJECTILES = new LinkedHashMap<>();
    // 艾瑟龙技能实体：羽刃、陨晶、雷击标记
    public static final DeferredHolder<EntityType<?>, EntityType<DragonSkillEntity>> DRAGON_SKILL = ENTITIES.register("dragon_skill", () -> EntityType.Builder.<DragonSkillEntity>of(DragonSkillEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("dragon_skill"));
    // 灵魂技能实体：圣环符文、天剑
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritSkillEntity>> SPIRIT_SKILL = ENTITIES.register("spirit_skill", () -> EntityType.Builder.<SpiritSkillEntity>of(SpiritSkillEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("spirit_skill"));
    // 天境娜迦技能实体：石柱、巨石
    public static final DeferredHolder<EntityType<?>, EntityType<NagaSkillEntity>> NAGA_SKILL = ENTITIES.register("naga_skill", () -> EntityType.Builder.<NagaSkillEntity>of(NagaSkillEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("naga_skill"));
    // 水晶石傀儡技能实体：晶刺、晶笼、晶核
    public static final DeferredHolder<EntityType<?>, EntityType<GolemSkillEntity>> GOLEM_SKILL = ENTITIES.register("golem_skill", () -> EntityType.Builder.<GolemSkillEntity>of(GolemSkillEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("golem_skill"));
    // 盐蟹王技能实体：泡泡、盐泉、浪墙、盐晶
    public static final DeferredHolder<EntityType<?>, EntityType<CrabSkillEntity>> CRAB_SKILL = ENTITIES.register("crab_skill", () -> EntityType.Builder.<CrabSkillEntity>of(CrabSkillEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("crab_skill"));
    // 远古潜影贝技能实体：潜影弹、碎刃、缠藤
    public static final DeferredHolder<EntityType<?>, EntityType<ShulkerSkillEntity>> SHULKER_SKILL = ENTITIES.register("shulker_skill", () -> EntityType.Builder.<ShulkerSkillEntity>of(ShulkerSkillEntity::new, MobCategory.MISC)
            .sized(0.6F, 0.6F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build("shulker_skill"));
    public static final DeferredHolder<EntityType<?>, EntityType<TamedAetherDragonEntity>> TAMED_DRAGON = ENTITIES.register("tamed_aetherdragon", () -> EntityType.Builder.<TamedAetherDragonEntity>of(TamedAetherDragonEntity::new, MobCategory.CREATURE)
            .sized(5.0F, 4.0F)
            .clientTrackingRange(16)
            .updateInterval(1)
            .build("tamed_aetherdragon"));

    static {
        registerMob("aetherdragon", MobCategory.MONSTER, 5.0F, 4.0F, 3);
    }

    private static void registerMob(String id, MobCategory category, float width, float height, int updateInterval) {
        EntityType.EntityFactory<RebornMobEntity> factory = id.equals("aether_naga") ? NagaEntity::new : RebornMobEntity::new;
        MOBS.put(id, ENTITIES.register(id, () -> EntityType.Builder.of(factory, category)
                .sized(width, height)
                .clientTrackingRange(64)
                .updateInterval(updateInterval)
                .build(id)));
    }

    private static void registerProjectile(String id) {
        PROJECTILES.put(id, ENTITIES.register(id, () -> EntityType.Builder.of(RebornProjectileEntity::new, MobCategory.MISC)
                .sized(0.5F, 0.5F)
                .clientTrackingRange(64)
                .updateInterval(1)
                .build(id)));
    }

    private ModEntities() {
    }
}
