package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.block.CrystaliteCapstoneBlock;
import com.aetherdragon.block.SpecialSpawnerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AetherDragon.MOD_ID);
    public static final Map<String, DeferredHolder<Block, Block>> ALL = new LinkedHashMap<>();

    static {
        String[] ids = {
                "aureatebluebeam", "aureatebluebeambrick", "aureatebluehead", "aureatebluepillar",
                "aureatebricks", "aureatechiseled", "aureatecobblestone", "aureatekeystone",
                "aureateleaves", "aureatepillar", "aureatepillarbeam", "aureatepillarhead",
                "aureatepillarheadbrick", "aureatescript", "aureatetilebricks", "aureatetiles",
                "aurorastone", "dragonspawner"
        };
        for (String id : ids) {
            ALL.put(id, BLOCKS.register(id, () -> id.equals("crystalitecapstone")
                    ? new CrystaliteCapstoneBlock()
                    : isSpecialSpawner(id)
                    ? new SpecialSpawnerBlock(getSpawnerAction(id))
                    : id.equals("aureateleaves") || id.equals("labyrinthleaves")
                    ? new Block(BlockBehaviour.Properties.of().mapColor(mapColor(id)).strength(1.5F).noOcclusion())
                    : new Block(BlockBehaviour.Properties.of().mapColor(mapColor(id)).strength(1.5F))));
        }
    }

    private static MapColor mapColor(String id) {
        if (id.equals("aureateleaves") || id.equals("labyrinthleaves")) {
            return MapColor.PLANT;
        }
        if (id.contains("blue")) {
            return MapColor.COLOR_BLUE;
        }
        if (id.startsWith("crystalite")) {
            return MapColor.DIAMOND;
        }
        if (id.startsWith("labyrinth")) {
            return id.equals("labyrinthleaves") ? MapColor.PLANT : MapColor.DEEPSLATE;
        }
        if (id.contains("salt")) {
            return MapColor.COLOR_LIGHT_GRAY;
        }
        if (id.equals("deepdirt") || id.equals("deepgrass") || id.equals("grassydeadrock")) {
            return id.equals("deepgrass") ? MapColor.GRASS : MapColor.DIRT;
        }
        if (id.equals("redglazedmosaic")) {
            return MapColor.COLOR_RED;
        }
        if (id.startsWith("aureate") || id.equals("goldcity")) {
            return MapColor.GOLD;
        }
        return MapColor.STONE;
    }

    private static boolean isSpecialSpawner(String id) {
        return id.equals("dragonspawner");
    }

    private static String getSpawnerAction(String id) {
        return "dragon";
    }

    private ModBlocks() {
    }
}
