package com.aetherdragon.client;

import net.minecraft.client.Minecraft;

public final class DragonMountControls {
    private DragonMountControls() {
    }

    public static boolean isAscending() {
        return Minecraft.getInstance().options.keyJump.isDown();
    }

    public static boolean isDescending() {
        return Minecraft.getInstance().options.keySprint.isDown();
    }
}
