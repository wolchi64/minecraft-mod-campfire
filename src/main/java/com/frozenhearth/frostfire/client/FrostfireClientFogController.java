package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostfireClientFogController
{
    private static final float MIN_CLEAR_FAR_DISTANCE = 512.0F;
    private static final float MAX_CLEAR_FAR_DISTANCE = 8192.0F;
    private static final float VISUAL_SUPPRESSION_RATE = 1.2F;

    private static long lastVisualUpdateMillis = -1L;
    private static float visualSuppressionStrength = 0.0F;
    private static Vec3 visualZoneCenter = Vec3.ZERO;
    private static double visualZoneRadius = 0.0D;

    private FrostfireClientFogController() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        FrostfireClientWeatherCache.WeatherSuppressionSample suppression =
                sampleVisualWeatherSuppression(event.getCamera().getPosition());
        if (suppression.strength() <= 0.0F)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        FogRenderer.FogMode mode = event.getMode();
        if (mode != FogRenderer.FogMode.FOG_TERRAIN && mode != FogRenderer.FogMode.FOG_SKY)
        {
            return;
        }

        float renderDistanceBlocks = minecraft.options.getEffectiveRenderDistance() * 16.0F;
        float targetFarDistance = Mth.clamp(renderDistanceBlocks * 8.0F, MIN_CLEAR_FAR_DISTANCE, MAX_CLEAR_FAR_DISTANCE);
        float clearDistance = Mth.lerp(suppression.strength(), renderDistanceBlocks, targetFarDistance);
        float nearDistance = mode == FogRenderer.FogMode.FOG_SKY
                             ? 0.0F
                             : Mth.lerp(suppression.strength(), 4.0F, -64.0F);

        event.setNearPlaneDistance(nearDistance);
        event.setFarPlaneDistance(clearDistance);
        event.setFogShape(FogShape.CYLINDER);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event)
    {
        FrostfireClientWeatherCache.WeatherSuppressionSample suppression =
                sampleVisualWeatherSuppression(event.getCamera().getPosition());
        if (suppression.strength() <= 0.0F)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null)
        {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        Vec3 skyColor = level.getSkyColor(cameraPos, (float) event.getPartialTick());
        float blend = suppression.strength();
        event.setRed(Mth.lerp(blend, event.getRed(), (float) skyColor.x));
        event.setGreen(Mth.lerp(blend, event.getGreen(), (float) skyColor.y));
        event.setBlue(Mth.lerp(blend, event.getBlue(), (float) skyColor.z));
    }

    private static FrostfireClientWeatherCache.WeatherSuppressionSample sampleVisualWeatherSuppression(Vec3 cameraPos)
    {
        Minecraft minecraft = Minecraft.getInstance();
        FrostfireClientWeatherCache.WeatherSuppressionSample rawSuppression =
                FrostfireClientWeatherCache.sampleWeatherSuppression(cameraPos);
        if (minecraft.level == null)
        {
            resetVisualSuppression();
            return rawSuppression;
        }

        if (rawSuppression.strength() > 0.0F)
        {
            visualZoneCenter = rawSuppression.zoneCenter();
            visualZoneRadius = rawSuppression.zoneRadius();
        }

        long now = Util.getMillis();
        if (lastVisualUpdateMillis < 0L)
        {
            visualSuppressionStrength = 0.0F;
        }
        else
        {
            float deltaSeconds = Mth.clamp((now - lastVisualUpdateMillis) / 1000.0F, 0.0F, 0.25F);
            visualSuppressionStrength = Mth.approach(visualSuppressionStrength, rawSuppression.strength(),
                    VISUAL_SUPPRESSION_RATE * deltaSeconds);
        }
        lastVisualUpdateMillis = now;

        float visualBlend = (float) Mth.smoothstep(visualSuppressionStrength);

        if (visualBlend <= 0.0F)
        {
            if (rawSuppression.strength() <= 0.0F)
            {
                resetVisualSuppression();
            }
            return new FrostfireClientWeatherCache.WeatherSuppressionSample(0.0F, rawSuppression.insideDistance(), visualZoneCenter, visualZoneRadius);
        }

        return new FrostfireClientWeatherCache.WeatherSuppressionSample(
                visualBlend,
                rawSuppression.insideDistance(),
                visualZoneCenter,
                visualZoneRadius);
    }

    private static void resetVisualSuppression()
    {
        lastVisualUpdateMillis = -1L;
        visualSuppressionStrength = 0.0F;
        visualZoneCenter = Vec3.ZERO;
        visualZoneRadius = 0.0D;
    }
}
