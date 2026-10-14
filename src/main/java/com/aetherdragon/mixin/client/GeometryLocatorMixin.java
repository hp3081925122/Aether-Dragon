package com.aetherdragon.mixin.client;

import com.geckolib.GeckoLibConstants;
import com.geckolib.loading.definition.geometry.Geometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(value = Geometry.class, remap = false)
public abstract class GeometryLocatorMixin {
    @Redirect(method = "lambda$bakeTopLevelBones$0", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private static Object registerLocatorOnce(Map<Object, Object> locators, Object name, Object locator) {
        if (locators.get(name) == locator) {
            GeckoLibConstants.LOGGER.debug("Skipping repeated registration of the same locator instance: {}", name);
            return null;
        }

        return locators.put(name, locator);
    }
}
