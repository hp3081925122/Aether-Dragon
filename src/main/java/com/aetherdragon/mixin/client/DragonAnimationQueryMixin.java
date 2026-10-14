package com.aetherdragon.mixin.client;

import com.aetherdragon.client.DragonRenderState;
import com.geckolib.animation.state.AnimationPoint;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.loading.math.value.Variable;
import com.geckolib.renderer.base.GeoRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ControllerState.class, remap = false)
public abstract class DragonAnimationQueryMixin {
    @Shadow
    public abstract GeoRenderState renderState();

    @Shadow
    public abstract AnimationPoint animationPoint();

    @Inject(method = "getQueryValue", at = @At("HEAD"), cancellable = true)
    private void dragonAnimationTime(Variable variable, CallbackInfoReturnable<Double> callback) {
        if (renderState() instanceof DragonRenderState && animationPoint() != null
                && MolangQueries.ANIM_TIME.equals(variable.name())) {
            callback.setReturnValue(animationPoint().animTime());
        }
    }
}
