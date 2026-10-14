package com.aetherdragon.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class NagaHitboxes {
    private static final String[] NAMES = {"body", "body_front", "tail1", "tail2", "tail3"};
    private static final double[] DISTANCES = {2.5D, 4.1D, 5.8D, 7.5D, 9.0D};
    private static final double[] WIDTHS = {3.25D, 3.25D, 3.1D, 2.8D, 2.25D};
    private static final double[] HEIGHTS = {2.45D, 2.45D, 2.3D, 2.1D, 1.7D};

    static NagaPart[] createParts(NagaEntity parent) {
        NagaPart[] result = new NagaPart[NAMES.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new NagaPart(parent, NAMES[i]);
        }
        return result;
    }

    static void update(NagaEntity naga, NagaPart[] parts, double time) {
        float yaw = naga.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 tailDirection = forward.scale(-1.0D);
        Vec3 right = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        int skill = naga.getNagaSkill();
        double phase = time * 20.0D;
        double skillWave = skill == NagaCombat.TAIL_SWEEP ? Mth.sin((float) (phase * Math.PI / 20.0D)) * 1.45D : 0.0D;
        double movementWave = naga.getDeltaMovement().horizontalDistanceSqr() > 0.0001D ? Math.sin(time * 18.0D) * 0.18D : 0.0D;
        for (int i = 0; i < parts.length; i++) {
            double wave = skillWave * (i + 1) / parts.length + movementWave * i;
            Vec3 center = naga.position().add(tailDirection.scale(DISTANCES[i])).add(right.scale(wave));
            double height = HEIGHTS[i];
            double width = WIDTHS[i];
            parts[i].moveToBox(new AABB(center.x - width * 0.5D, naga.getY() + 0.15D,
                    center.z - width * 0.5D, center.x + width * 0.5D, naga.getY() + 0.15D + height,
                    center.z + width * 0.5D));
        }
    }

    private NagaHitboxes() {
    }
}
