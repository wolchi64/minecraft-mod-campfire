package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostfireFogWallWorldRenderer
{
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_PARTICLES;
    private static final int CYLINDER_SEGMENTS = 96;
    private static final int FOG_SHELL_COUNT = 5;
    private static final int FOG_SLICE_COLUMNS = 18;
    private static final int FOG_SLICE_DEPTH_LAYERS = 2;
    private static final float SHELL_HALF_THICKNESS = 8.25F;
    private static final float SHELL_RADIUS_JITTER = 0.55F;
    private static final float BAND_HEIGHT_JITTER = 0.85F;
    private static final float MIN_BAND_HEIGHT = 0.75F;
    private static final float FOG_SLICE_HALF_WIDTH = 1.65F;
    private static final float FOG_SLICE_HEIGHT_PADDING = 8.0F;
    private static final float[] HEIGHT_OFFSETS =
            new float[] {-34.0F, -25.0F, -17.0F, -10.0F, -4.0F, 2.5F, 9.5F, 18.0F, 28.0F, 41.0F, 57.0F, 76.0F, 96.0F};
    private static final float[] HEIGHT_ALPHA =
            new float[] {0.12F, 0.18F, 0.30F, 0.46F, 0.64F, 0.82F, 0.96F, 1.00F, 0.92F, 0.72F, 0.50F, 0.28F, 0.12F};
    private static final int WALL_RED = 232;
    private static final int WALL_GREEN = 238;
    private static final int WALL_BLUE = 245;

    private FrostfireFogWallWorldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RENDER_STAGE)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            return;
        }

        ShaderInstance shader = FrostfireFogWallShaderRegistry.getCampfireDomainWallShader();
        if (shader == null)
        {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> visibleZones =
                FrostfireClientWeatherCache.getNearestActiveZones(cameraPos, FrostfireClientWeatherCache.MAX_RENDERED_WALLS);
        if (visibleZones.isEmpty())
        {
            return;
        }

        float weatherIntensity = FrostfireClientWeatherCache.getWallWeatherIntensity(event.getPartialTick());
        float wallTime = (minecraft.level.getGameTime() + event.getPartialTick()) * 0.05F;
        configureShader(shader, cameraPos, weatherIntensity, wallTime);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(FrostfireFogWallRenderTypes.campfireDomainWall());
        Matrix4f poseMatrix = event.getPoseStack().last().pose();
        boolean renderedAny = false;

        for (FrostfireClientWeatherCache.WeatherZoneSnapshot zone : visibleZones)
        {
            if (!isZoneVisible(event, zone))
            {
                continue;
            }

            appendZoneFogVolume(consumer, poseMatrix, cameraPos, zone, weatherIntensity);
            renderedAny = true;
        }

        if (renderedAny)
        {
            bufferSource.endBatch(FrostfireFogWallRenderTypes.campfireDomainWall());
        }
    }

    private static void configureShader(ShaderInstance shader, Vec3 cameraPos, float weatherIntensity, float wallTime)
    {
        shader.safeGetUniform("CameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        shader.safeGetUniform("Time").set(wallTime);
        shader.safeGetUniform("WeatherIntensity").set(weatherIntensity);
    }

    private static boolean isZoneVisible(RenderLevelStageEvent event, FrostfireClientWeatherCache.WeatherZoneSnapshot zone)
    {
        if (event.getFrustum() == null)
        {
            return true;
        }

        double outerRadius = zone.radius() + SHELL_HALF_THICKNESS;
        Vec3 center = zone.center();
        AABB bounds = new AABB(
                center.x - outerRadius,
                center.y + HEIGHT_OFFSETS[0],
                center.z - outerRadius,
                center.x + outerRadius,
                center.y + HEIGHT_OFFSETS[HEIGHT_OFFSETS.length - 1],
                center.z + outerRadius);
        return event.getFrustum().isVisible(bounds);
    }

    private static void appendZoneFogVolume(VertexConsumer consumer, Matrix4f poseMatrix, Vec3 cameraPos,
                                            FrostfireClientWeatherCache.WeatherZoneSnapshot zone, float weatherIntensity)
    {
        float centerRadius = (float) zone.radius();
        float innerRadius = Math.max(0.5F, centerRadius - SHELL_HALF_THICKNESS);
        float outerRadius = centerRadius + SHELL_HALF_THICKNESS;
        float weatherHeightExtension = Mth.lerp(weatherIntensity, 2.0F, 8.5F);

        for (int shell = 0; shell < FOG_SHELL_COUNT; shell++)
        {
            float shellFraction = FOG_SHELL_COUNT <= 1 ? 0.5F : (float) shell / (float) (FOG_SHELL_COUNT - 1);
            float centerWeight = 1.0F - Math.abs((shellFraction * 2.0F) - 1.0F);
            float softenedCenterWeight = smoothstep(centerWeight);
            float shellAlphaScale = Mth.lerp(softenedCenterWeight, 0.18F, Mth.lerp(weatherIntensity, 0.78F, 0.94F));
            float shellRadius = Mth.lerp(shellFraction, innerRadius, outerRadius);
            shellRadius += sampleCenteredNoise(zone.center(), shell * 2.7F, 11.0F) * SHELL_RADIUS_JITTER;
            shellRadius = Mth.clamp(shellRadius, innerRadius - 0.55F, outerRadius + 0.55F);

            appendCylinderSurface(consumer, poseMatrix, cameraPos, zone.center(), shellRadius, shellAlphaScale,
                    weatherHeightExtension, shell);
        }

        appendInteriorFogSlices(consumer, poseMatrix, cameraPos, zone.center(), innerRadius, outerRadius, weatherIntensity);
    }

    private static void appendCylinderSurface(VertexConsumer consumer, Matrix4f poseMatrix, Vec3 cameraPos, Vec3 center,
                                              float radius, float alphaScale, float weatherHeightExtension, int shellIndex)
    {
        for (int segment = 0; segment < CYLINDER_SEGMENTS; segment++)
        {
            double startAngle = (Math.PI * 2.0D * segment) / CYLINDER_SEGMENTS;
            double endAngle = (Math.PI * 2.0D * (segment + 1)) / CYLINDER_SEGMENTS;
            float startRadius = radius + radialWarp(center, shellIndex, startAngle);
            float endRadius = radius + radialWarp(center, shellIndex, endAngle);

            float startX = (float) (center.x + (Math.cos(startAngle) * startRadius) - cameraPos.x);
            float startZ = (float) (center.z + (Math.sin(startAngle) * startRadius) - cameraPos.z);
            float endX = (float) (center.x + (Math.cos(endAngle) * endRadius) - cameraPos.x);
            float endZ = (float) (center.z + (Math.sin(endAngle) * endRadius) - cameraPos.z);

            for (int band = 0; band < HEIGHT_OFFSETS.length - 1; band++)
            {
                float heightOffset0 = adjustedHeightOffset(band, weatherHeightExtension)
                        + (sampleCenteredNoise(center, (shellIndex * 13.0F) + (band * 2.1F), 29.0F) * BAND_HEIGHT_JITTER);
                float heightOffset1 = adjustedHeightOffset(band + 1, weatherHeightExtension)
                        + (sampleCenteredNoise(center, (shellIndex * 13.0F) + ((band + 1) * 2.1F), 29.0F) * BAND_HEIGHT_JITTER);
                float startLowerOffset = heightOffset0 + heightWarp(center, shellIndex, band, startAngle);
                float endLowerOffset = heightOffset0 + heightWarp(center, shellIndex, band, endAngle);
                float startUpperOffset = Math.max(heightOffset1 + heightWarp(center, shellIndex, band + 1, startAngle),
                        startLowerOffset + MIN_BAND_HEIGHT);
                float endUpperOffset = Math.max(heightOffset1 + heightWarp(center, shellIndex, band + 1, endAngle),
                        endLowerOffset + MIN_BAND_HEIGHT);
                float startY0 = (float) (center.y + startLowerOffset - cameraPos.y);
                float endY0 = (float) (center.y + endLowerOffset - cameraPos.y);
                float startY1 = (float) (center.y
                        + startUpperOffset
                        - cameraPos.y);
                float endY1 = (float) (center.y
                        + endUpperOffset
                        - cameraPos.y);

                float bandDensity0 = HEIGHT_ALPHA[band]
                        * (0.94F + (0.12F * sampleCenteredNoise(center, (band * 5.7F) + shellIndex, 53.0F)));
                float bandDensity1 = HEIGHT_ALPHA[band + 1]
                        * (0.94F + (0.12F * sampleCenteredNoise(center, ((band + 1) * 5.7F) + shellIndex, 53.0F)));
                int alpha0 = scaledAlpha(bandDensity0, alphaScale);
                int alpha1 = scaledAlpha(bandDensity1, alphaScale);

                addVertex(consumer, poseMatrix, startX, startY0, startZ, alpha0);
                addVertex(consumer, poseMatrix, endX, endY0, endZ, alpha0);
                addVertex(consumer, poseMatrix, endX, endY1, endZ, alpha1);
                addVertex(consumer, poseMatrix, startX, startY1, startZ, alpha1);
            }
        }
    }

    private static void appendInteriorFogSlices(VertexConsumer consumer, Matrix4f poseMatrix, Vec3 cameraPos, Vec3 center,
                                                float innerRadius, float outerRadius, float weatherIntensity)
    {
        float bottomY = (float) (center.y + HEIGHT_OFFSETS[1] - cameraPos.y);
        float topY = (float) (center.y + HEIGHT_OFFSETS[HEIGHT_OFFSETS.length - 2]
                + Mth.lerp(weatherIntensity, 2.5F, 7.5F) - cameraPos.y);

        for (int column = 0; column < FOG_SLICE_COLUMNS; column++)
        {
            float angleFraction = (float) column / (float) FOG_SLICE_COLUMNS;
            double angle = angleFraction * Mth.TWO_PI;
            Vec3 radial = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 tangent = new Vec3(-radial.z, 0.0D, radial.x);

            for (int depthLayer = 0; depthLayer < FOG_SLICE_DEPTH_LAYERS; depthLayer++)
            {
                float depthFraction = FOG_SLICE_DEPTH_LAYERS <= 1 ? 0.5F : (float) depthLayer / (float) (FOG_SLICE_DEPTH_LAYERS - 1);
                float depthWeight = 1.0F - Math.abs((depthFraction * 2.0F) - 1.0F);
                float softenedDepth = smoothstep(depthWeight);
                float radius = Mth.lerp(depthFraction, innerRadius, outerRadius);
                radius += sampleCenteredNoise(center, (column * 3.17F) + depthLayer, 91.0F) * 0.45F;

                Vec3 anchor = center.add(radial.scale(radius));
                float bottomJitter = sampleCenteredNoise(center, (column * 1.91F) + (depthLayer * 7.0F), 101.0F) * 2.0F;
                float topJitter = sampleCenteredNoise(center, (column * 2.33F) + (depthLayer * 9.0F), 109.0F) * 4.0F;
                float sliceBottomY = bottomY + bottomJitter + FOG_SLICE_HEIGHT_PADDING;
                float sliceTopY = topY + topJitter - FOG_SLICE_HEIGHT_PADDING;
                float halfWidth = FOG_SLICE_HALF_WIDTH + (0.65F * softenedDepth);

                int bottomAlpha = scaledAlpha(0.18F + (0.12F * softenedDepth), 0.56F + (0.16F * weatherIntensity));
                int topAlpha = scaledAlpha(0.14F + (0.10F * softenedDepth), 0.50F + (0.14F * weatherIntensity));

                addSliceQuad(consumer, poseMatrix, cameraPos, anchor, tangent, halfWidth, sliceBottomY, sliceTopY, bottomAlpha, topAlpha);
            }
        }
    }

    private static float adjustedHeightOffset(int bandIndex, float weatherHeightExtension)
    {
        float baseOffset = HEIGHT_OFFSETS[bandIndex];
        if (baseOffset <= 0.0F)
        {
            return baseOffset;
        }

        float topProgress = baseOffset / HEIGHT_OFFSETS[HEIGHT_OFFSETS.length - 1];
        return baseOffset + (weatherHeightExtension * topProgress);
    }

    private static float smoothstep(float value)
    {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - (2.0F * clamped));
    }

    private static float sampleCenteredNoise(Vec3 center, float a, float b)
    {
        double seed = (center.x * 0.173D) + (center.y * 0.117D) + (center.z * 0.197D) + (a * 12.9898D) + (b * 78.233D);
        double hashed = Math.sin(seed) * 43758.5453123D;
        return (float) (((hashed - Math.floor(hashed)) * 2.0D) - 1.0D);
    }

    private static float radialWarp(Vec3 center, int shellIndex, double angle)
    {
        double wave = Math.sin((angle * 2.0D) + (shellIndex * 0.73D) + (center.x * 0.09D))
                + (0.35D * Math.sin((angle * 5.0D) - (shellIndex * 0.41D) + (center.z * 0.07D)));
        return (float) (wave * 0.18D);
    }

    private static float heightWarp(Vec3 center, int shellIndex, int bandIndex, double angle)
    {
        double wave = Math.sin((angle * 2.0D) + (bandIndex * 0.61D) + (shellIndex * 0.29D) + (center.z * 0.06D))
                + (0.35D * Math.sin((angle * 4.0D) - (bandIndex * 0.47D) + (center.x * 0.08D)));
        return (float) (wave * 0.32D);
    }

    private static void addSliceQuad(VertexConsumer consumer, Matrix4f poseMatrix, Vec3 cameraPos, Vec3 center, Vec3 tangent,
                                     float halfWidth, float bottomY, float topY, int bottomAlpha, int topAlpha)
    {
        float leftX = (float) (center.x - (tangent.x * halfWidth) - cameraPos.x);
        float leftZ = (float) (center.z - (tangent.z * halfWidth) - cameraPos.z);
        float rightX = (float) (center.x + (tangent.x * halfWidth) - cameraPos.x);
        float rightZ = (float) (center.z + (tangent.z * halfWidth) - cameraPos.z);

        addVertex(consumer, poseMatrix, leftX, bottomY, leftZ, bottomAlpha);
        addVertex(consumer, poseMatrix, rightX, bottomY, rightZ, bottomAlpha);
        addVertex(consumer, poseMatrix, rightX, topY, rightZ, topAlpha);
        addVertex(consumer, poseMatrix, leftX, topY, leftZ, topAlpha);
    }

    private static int scaledAlpha(float baseAlpha, float alphaScale)
    {
        return Math.min(255, Math.max(0, Math.round(255.0F * baseAlpha * alphaScale)));
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f poseMatrix, float x, float y, float z, int alpha)
    {
        consumer.vertex(poseMatrix, x, y, z).color(WALL_RED, WALL_GREEN, WALL_BLUE, alpha).endVertex();
    }
}
