package com.aetherdragon.client;

import com.aetherdragon.entity.TamedAetherDragonEntity;
import com.aetherdragon.network.DragonMountNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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

    public static void onClientTick(ClientTickEvent.Post event) {
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
            ClientPacketDistributor.sendToServer(new DragonMountNetwork.LaserPayload(firing));
        }
    }

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && isRidingDragon(Minecraft.getInstance())) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static void renderHint(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || hintTicks <= 0 || !isRidingDragon(minecraft)) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float visible = Math.min(1.0F, (hintTicks - partialTick) / HINT_FADE_TICKS);
        int alpha = Math.max(8, Math.round(visible * 255.0F));
        Options options = minecraft.options;
        Component hint = Component.translatable("hud.aether_dragon.mount.hint",
                options.keySprint.getTranslatedKeyMessage(),
                options.keyShift.getTranslatedKeyMessage(),
                options.keyJump.getTranslatedKeyMessage(),
                options.keyAttack.getTranslatedKeyMessage());
        int y = graphics.guiHeight() - Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight) - 10;
        graphics.text(minecraft.font, hint, (graphics.guiWidth() - minecraft.font.width(hint)) / 2, y, (alpha << 24) | 0xFFFFFF, true);
    }
}
