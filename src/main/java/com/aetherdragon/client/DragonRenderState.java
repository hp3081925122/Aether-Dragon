package com.aetherdragon.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import java.util.HashMap;
import java.util.Map;

public final class DragonRenderState extends LivingEntityRenderState implements GeoRenderState {
    boolean beam;
    boolean debugSample;
    boolean debugPaused;
    boolean debugPhaseTwo;
    boolean debugTransitioning;
    boolean debugAlive;
    java.util.UUID debugEntity;
    int debugTick;
    int debugSkill;
    int debugSkillTicks;
    float debugHealth;
    net.minecraft.world.phys.Vec3 debugVelocity;
    String debugControllers;
    net.minecraft.world.phys.Vec3 beamEnd = net.minecraft.world.phys.Vec3.ZERO;
    private final Map<DataTicket<?>, Object> geckolibData = new HashMap<>();

    public static ControllerState alignMovementTransition(ControllerState source, int ticks) {
        return source.transitionTime() >= 0 && source.transitionTicks() != ticks
                ? new ControllerState(source.animationPoint(), source.prevAnimationPoint(), source.transitionTime(), ticks,
                        source.additive(), source.easingOverride(), source.renderState(), source.queryValues())
                : source;
    }

    @Override
    public <D> void addGeckolibData(DataTicket<D> ticket, D value) {
        geckolibData.put(ticket, value);
    }

    @Override
    public boolean hasGeckolibData(DataTicket<?> ticket) {
        return geckolibData.containsKey(ticket);
    }

    @Override
    public Map<DataTicket<?>, Object> getDataMap() {
        return geckolibData;
    }
}
