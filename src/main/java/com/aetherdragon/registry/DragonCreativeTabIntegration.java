package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonCreativeTabIntegration {
    private static final String MAIN_MOD_ID = "aether_bedrock_mobs_reborn";
    private static final ResourceKey<CreativeModeTab> MAIN_ITEM_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(MAIN_MOD_ID, "aetherbossitem"));
    private static final ResourceKey<CreativeModeTab> MAIN_BLOCK_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation(MAIN_MOD_ID, "aetherboss"));

    private DragonCreativeTabIntegration() {
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void addDragonItems(BuildCreativeModeTabContentsEvent event) {
        if (!ModList.get().isLoaded(MAIN_MOD_ID)) {
            return;
        }
        ResourceKey<CreativeModeTab> tab = event.getTabKey();
        boolean blocks = tab.equals(MAIN_BLOCK_TAB);
        if (!blocks && !tab.equals(MAIN_ITEM_TAB)) {
            return;
        }
        for (RegistryObject<net.minecraft.world.item.Item> entry : ModItems.ALL.values()) {
            net.minecraft.world.item.Item item = entry.get();
            if ((item instanceof BlockItem) == blocks) {
                event.accept(new ItemStack(item));
            }
        }
    }
}
