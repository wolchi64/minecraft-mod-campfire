package com.frozenhearth.frostfire.mixin.client;

import com.frozenhearth.frostfire.client.FrostfireClientWeatherCache;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class LevelRendererMixin
{
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void frostfire$hideWeatherInsideCampfireRadius(LightTexture lightTexture, float partialTick, double cameraX, double cameraY, double cameraZ, CallbackInfo ci)
    {
        if (FrostfireClientWeatherCache.isWeatherSuppressed(new Vec3(cameraX, cameraY, cameraZ)))
        {
            ci.cancel();
        }
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void frostfire$muteWeatherInsideCampfireRadius(Camera camera, CallbackInfo ci)
    {
        if (FrostfireClientWeatherCache.isWeatherSuppressed(camera.getPosition()))
        {
            ci.cancel();
        }
    }
}
