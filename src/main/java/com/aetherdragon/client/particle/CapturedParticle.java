package com.aetherdragon.client.particle;

import com.aetherdragon.client.GeometryCapture;
import net.minecraft.client.Camera;

public interface CapturedParticle {
    void extractGeometry(GeometryCapture geometry, Camera camera, float partialTick);
}
