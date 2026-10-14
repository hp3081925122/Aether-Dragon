package com.aetherdragon.registry;

import com.aetherdragon.AetherDragon;
import net.minecraftforge.fml.ModList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AetherDragon.MOD_ID);
    public static final RegistryObject<CreativeModeTab> DRAGON_TAB;

    static {
        if (!ModList.get().isLoaded("aether_bedrock_mobs_reborn")) {
            DRAGON_TAB = TABS.register("aetherdragon", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("item_group.aether_dragon.aetherdragon"))
                            .icon(() -> ModItems.ALL.get("aetherdragon_spawn_egg").get().getDefaultInstance())
                            .displayItems((parameters, output) -> ModItems.ALL.values().stream()
                                    .map(RegistryObject::get)
                                    .map(ItemStack::new)
                                    .forEach(output::accept))
                            .build());
        } else {
            DRAGON_TAB = null;
        }
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
