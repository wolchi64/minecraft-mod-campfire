package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostfireClientFogController
{
    private FrostfireClientFogController() {}

    @SubscribeEvent(receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        FrostfireClientWeatherCache.WeatherSuppressionSample suppression =
                FrostfireClientWeatherCache.sampleWeatherSuppression(event.getCamera().getPosition());
        float visualBlend = FrostfireFogBlend.computeVisualBlend(suppression.insideDistance());
        if (visualBlend <= 0.0F)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        FogRenderer.FogMode mode = event.getMode();
        if (mode != FogRenderer.FogMode.FOG_TERRAIN && mode != FogRenderer.FogMode.FOG_SKY)
        {
            return;
        }

        float targetFarDistance = minecraft.gameRenderer.getDepthFar();
        float baseFarDistance = event.getFarPlaneDistance();
        float baseNearDistance = event.getNearPlaneDistance();
        float targetNearDistance = mode == FogRenderer.FogMode.FOG_SKY
                                   ? 0.0F
                                   : FrostfireFogBlend.CLEAR_TERRAIN_NEAR_DISTANCE;
        float clearDistance = Mth.lerp(visualBlend, baseFarDistance, targetFarDistance);
        float nearDistance = Mth.lerp(visualBlend, baseNearDistance, targetNearDistance);

        event.setNearPlaneDistance(nearDistance);
        event.setFarPlaneDistance(clearDistance);
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event)
    {
        FrostfireClientWeatherCache.WeatherSuppressionSample suppression =
                FrostfireClientWeatherCache.sampleWeatherSuppression(event.getCamera().getPosition());
        float visualBlend = FrostfireFogBlend.computeVisualBlend(suppression.insideDistance());
        if (visualBlend <= 0.0F)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null)
        {
            return;
        }

        Vec3 clearFogColor = computeClearWeatherFogColor(level, event.getCamera().getPosition(), (float) event.getPartialTick());
        event.setRed(Mth.lerp(visualBlend, event.getRed(), (float) clearFogColor.x));
        event.setGreen(Mth.lerp(visualBlend, event.getGreen(), (float) clearFogColor.y));
        event.setBlue(Mth.lerp(visualBlend, event.getBlue(), (float) clearFogColor.z));
    }

    private static Vec3 computeClearWeatherFogColor(ClientLevel level, Vec3 cameraPos, float partialTick)
    {
        Vec3 clearSkyColor = computeClearWeatherSkyColor(level, cameraPos, partialTick);
        return level.effects().getBrightnessDependentFogColor(clearSkyColor, computeClearSkyDarken(level, partialTick));
    }

    private static Vec3 computeClearWeatherSkyColor(ClientLevel level, Vec3 cameraPos, float partialTick)
    {
        float timeOfDay = level.getTimeOfDay(partialTick);
        Vec3 samplePos = cameraPos.subtract(2.0D, 2.0D, 2.0D).scale(0.25D);
        BiomeManager biomeManager = level.getBiomeManager();
        Vec3 baseSkyColor = CubicSampler.gaussianSampleVec3(
                samplePos,
                (quartX, quartY, quartZ) -> Vec3.fromRGB24(biomeManager.getNoiseBiomeAtQuart(quartX, quartY, quartZ).value().getSkyColor()));

        float skyBrightness = Mth.clamp(Mth.cos(timeOfDay * ((float) Math.PI * 2.0F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        Vec3 clearSkyColor = baseSkyColor.scale(skyBrightness);
        return applySkyFlash(level, clearSkyColor, partialTick);
    }

    private static float computeClearSkyDarken(ClientLevel level, float partialTick)
    {
        float timeOfDay = level.getTimeOfDay(partialTick);
        float skyDarken = 1.0F - (Mth.cos(timeOfDay * ((float) Math.PI * 2.0F)) * 2.0F + 0.2F);
        skyDarken = Mth.clamp(skyDarken, 0.0F, 1.0F);
        skyDarken = 1.0F - skyDarken;
        return (skyDarken * 0.8F) + 0.2F;
    }

    private static Vec3 applySkyFlash(ClientLevel level, Vec3 skyColor, float partialTick)
    {
        int skyFlashTime = level.getSkyFlashTime();
        if (skyFlashTime <= 0)
        {
            return skyColor;
        }

        float flashStrength = Math.min(skyFlashTime - partialTick, 1.0F) * 0.45F;
        double red = (skyColor.x * (1.0F - flashStrength)) + (0.8F * flashStrength);
        double green = (skyColor.y * (1.0F - flashStrength)) + (0.8F * flashStrength);
        double blue = (skyColor.z * (1.0F - flashStrength)) + flashStrength;
        return new Vec3(red, green, blue);
    }
}
