package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private static final String MAIN_MOD_ID = "aether_bedrock_mobs_reborn";
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AetherDragon.MOD_ID);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DRAGON_TAB;

    static {
        if (!ModList.get().isLoaded(MAIN_MOD_ID)) {
            DRAGON_TAB = TABS.register("aetherdragon", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("item_group.aether_dragon.aetherdragon"))
                            .icon(() -> ModItems.ALL.get("aetherdragon_spawn_egg").get().getDefaultInstance())
                            .displayItems((parameters, output) -> ModItems.ALL.values().stream()
                                    .map(DeferredHolder::get)
                                    .map(ItemStack::new)
                                    .forEach(output::accept))
                            .build());
        } else {
            DRAGON_TAB = null;
        }
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
        modEventBus.addListener(ModCreativeTabs::addDragonItemsToMainTabs);
    }

    private static void addDragonItemsToMainTabs(BuildCreativeModeTabContentsEvent event) {
        if (!ModList.get().isLoaded(MAIN_MOD_ID)) {
            return;
        }
        String path = event.getTabKey().location().getPath();
        String namespace = event.getTabKey().location().getNamespace();
        if (!MAIN_MOD_ID.equals(namespace) || (!"aetherbossitem".equals(path) && !"aetherboss".equals(path))) {
            return;
        }
        boolean blocks = "aetherboss".equals(path);
        ModItems.ALL.values().stream()
                .map(DeferredHolder::get)
                .filter(item -> (item instanceof net.minecraft.world.item.BlockItem) == blocks)
                .map(ItemStack::new)
                .forEach(event::accept);
    }

    private ModCreativeTabs() {
    }
}
