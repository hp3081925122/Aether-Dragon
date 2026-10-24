package com.aetherdragon.client;

import com.aetherdragon.AetherDragon;
import com.aetherdragon.entity.TamedAetherDragonEntity;
import com.aetherdragon.network.DragonMountNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AetherDragon.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DragonMountClientEvents {
    private static final int HINT_TICKS = 100;
    private static final int HINT_FADE_TICKS = 20;
    private static boolean laserSent;
    private static boolean wasRiding;
    private static int hintTicks;

    private DragonMountClientEvents() {
    }

    private static boolean isRidingDragon(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.getVehicle() instanceof TamedAetherDragonEntity;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            laserSent = false;
            wasRiding = false;
            hintTicks = 0;
            return;
        }
        boolean riding = isRidingDragon(minecraft);
        if (riding && !wasRiding) {
            hintTicks = HINT_TICKS;
        }
        if (!riding) {
            hintTicks = 0;
        }
        if (hintTicks > 0 && !minecraft.isPaused()) {
            hintTicks--;
        }
        wasRiding = riding;
        boolean firing = riding && minecraft.screen == null && minecraft.options.keyAttack.isDown();
        if (firing != laserSent) {
            laserSent = firing;
            DragonMountNetwork.CHANNEL.sendToServer(new DragonMountNetwork.LaserMessage(firing));
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && isRidingDragon(Minecraft.getInstance())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static void renderHint(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || hintTicks <= 0 || !isRidingDragon(minecraft)) {
            return;
        }
        float visible = Math.min(1.0F, (hintTicks - partialTick) / HINT_FADE_TICKS);
        int alpha = Math.max(8, Math.round(visible * 255.0F));
        Options options = minecraft.options;
        Component hint = Component.translatable("hud.aether_dragon.mount.hint",
                options.keySprint.getTranslatedKeyMessage(),
                options.keyShift.getTranslatedKeyMessage(),
                options.keyJump.getTranslatedKeyMessage(),
                options.keyAttack.getTranslatedKeyMessage());
        int y = height - Math.max(gui.leftHeight, gui.rightHeight) - 10;
        graphics.drawString(minecraft.font, hint, (width - minecraft.font.width(hint)) / 2, y, (alpha << 24) | 0xFFFFFF, true);
    }
}
