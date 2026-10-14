package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.RebornMobEntity;
import com.aetherdragon.entity.DragonSkillEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(AetherDragon.MOD_ID);
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<RebornMobEntity>>> MOBS = new LinkedHashMap<>();
    public static final DeferredHolder<EntityType<?>, EntityType<DragonSkillEntity>> DRAGON_SKILL = ENTITIES.registerEntityType("dragon_skill", DragonSkillEntity::new, MobCategory.MISC,
            builder -> builder.sized(0.8F, 0.8F).clientTrackingRange(8).updateInterval(1));

    static {
        MOBS.put("aetherdragon", ENTITIES.registerEntityType("aetherdragon", RebornMobEntity::new, MobCategory.MONSTER,
                builder -> builder.sized(5.0F, 4.0F).clientTrackingRange(64).updateInterval(3)));
    }

    private ModEntities() {
    }
}
