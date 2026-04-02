package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostfireFogWallWorldRenderer
{
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_LEVEL;
    private static final int MAX_ZONES = FrostfireClientWeatherCache.MAX_RENDERED_WALLS;
    private static final float CAMERA_OUTSIDE_FADE_DISTANCE = 12.0F;
    private static final float WALL_HALF_THICKNESS = 14.5F;
    private static final float WALL_BOTTOM_OFFSET = -34.0F;
    private static final float WALL_TOP_OFFSET_CLEAR = 58.0F;
    private static final float WALL_TOP_OFFSET_STORM = 74.0F;
    private static TextureTarget depthSnapshotTarget;

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
        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> zones =
                FrostfireClientWeatherCache.getNearestActiveZones(cameraPos, MAX_ZONES);
        if (zones.isEmpty())
        {
            return;
        }

        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> visibleZones = filterVisibleZones(zones, cameraPos, event.getFrustum());
        if (visibleZones.isEmpty())
        {
            return;
        }

        float weatherIntensity = FrostfireClientWeatherCache.getWallWeatherIntensity(event.getPartialTick());
        float wallTime = (minecraft.level.getGameTime() + event.getPartialTick()) * 0.05F;
        RenderTarget mainRenderTarget = minecraft.getMainRenderTarget();
        configureShader(shader, event, camera, cameraPos, visibleZones, weatherIntensity, wallTime, mainRenderTarget);
        RenderTarget depthRenderTarget = ensureDepthSnapshotTarget(mainRenderTarget);
        depthRenderTarget.copyDepthFrom(mainRenderTarget);
        renderFogVolume(shader, mainRenderTarget, depthRenderTarget);
    }

    private static List<FrostfireClientWeatherCache.WeatherZoneSnapshot> filterVisibleZones(
            List<FrostfireClientWeatherCache.WeatherZoneSnapshot> zones, Vec3 cameraPos, Frustum frustum)
    {
        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> visible = new ArrayList<>(zones.size());
        for (FrostfireClientWeatherCache.WeatherZoneSnapshot zone : zones)
        {
            if (!isCameraNearZone(zone, cameraPos))
            {
                continue;
            }

            if (frustum == null)
            {
                visible.add(zone);
                continue;
            }

            double outerRadius = zone.radius() + WALL_HALF_THICKNESS;
            Vec3 center = zone.center();
            AABB bounds = new AABB(
                    center.x - outerRadius,
                    center.y + WALL_BOTTOM_OFFSET,
                    center.z - outerRadius,
                    center.x + outerRadius,
                    center.y + WALL_TOP_OFFSET_STORM,
                    center.z + outerRadius);
            if (frustum.isVisible(bounds))
            {
                visible.add(zone);
            }
        }
        return visible;
    }

    private static boolean isCameraNearZone(FrostfireClientWeatherCache.WeatherZoneSnapshot zone, Vec3 cameraPos)
    {
        double dx = cameraPos.x - zone.center().x;
        double dz = cameraPos.z - zone.center().z;
        double fadeRadius = zone.radius() + WALL_HALF_THICKNESS + CAMERA_OUTSIDE_FADE_DISTANCE;
        return (dx * dx) + (dz * dz) < (fadeRadius * fadeRadius);
    }

    private static void configureShader(ShaderInstance shader, RenderLevelStageEvent event, Camera camera, Vec3 cameraPos,
                                        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> visibleZones,
                                        float weatherIntensity, float wallTime, RenderTarget mainRenderTarget)
    {
        Matrix4f inverseProjection = new Matrix4f(event.getProjectionMatrix()).invert();
        Quaternionf cameraRotation = new Quaternionf(camera.rotation());
        Vec3 topLeft = projectFarRay(inverseProjection, cameraRotation, -1.0F, 1.0F);
        Vec3 topRight = projectFarRay(inverseProjection, cameraRotation, 1.0F, 1.0F);
        Vec3 bottomLeft = projectFarRay(inverseProjection, cameraRotation, -1.0F, -1.0F);
        Vec3 bottomRight = projectFarRay(inverseProjection, cameraRotation, 1.0F, -1.0F);
        float wallTopOffset = Mth.lerp(weatherIntensity, WALL_TOP_OFFSET_CLEAR, WALL_TOP_OFFSET_STORM);

        shader.safeGetUniform("InverseProjMat").set(inverseProjection);
        shader.safeGetUniform("TargetSize").set((float) mainRenderTarget.width, (float) mainRenderTarget.height);
        shader.safeGetUniform("CameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        shader.safeGetUniform("FarTopLeft").set((float) topLeft.x, (float) topLeft.y, (float) topLeft.z);
        shader.safeGetUniform("FarTopRight").set((float) topRight.x, (float) topRight.y, (float) topRight.z);
        shader.safeGetUniform("FarBottomLeft").set((float) bottomLeft.x, (float) bottomLeft.y, (float) bottomLeft.z);
        shader.safeGetUniform("FarBottomRight").set((float) bottomRight.x, (float) bottomRight.y, (float) bottomRight.z);
        shader.safeGetUniform("Time").set(wallTime);
        shader.safeGetUniform("WeatherIntensity").set(weatherIntensity);
        shader.safeGetUniform("WallHalfThickness").set(WALL_HALF_THICKNESS);
        shader.safeGetUniform("WallBottomOffset").set(WALL_BOTTOM_OFFSET);
        shader.safeGetUniform("WallTopOffset").set(wallTopOffset);
        shader.safeGetUniform("FarPlaneDistance").set(Minecraft.getInstance().gameRenderer.getDepthFar());
        shader.safeGetUniform("ActiveZoneCount").set((float) visibleZones.size());

        for (int zoneIndex = 0; zoneIndex < MAX_ZONES; zoneIndex++)
        {
            String uniformName = "Zone" + zoneIndex;
            if (zoneIndex < visibleZones.size())
            {
                FrostfireClientWeatherCache.WeatherZoneSnapshot zone = visibleZones.get(zoneIndex);
                Vec3 center = zone.center();
                shader.safeGetUniform(uniformName).set((float) center.x, (float) center.y, (float) center.z, (float) zone.radius());
            }
            else
            {
                shader.safeGetUniform(uniformName).set(0.0F, 0.0F, 0.0F, 0.0F);
            }
        }
    }

    private static void renderFogVolume(ShaderInstance shader, RenderTarget mainRenderTarget, RenderTarget depthRenderTarget)
    {
        mainRenderTarget.bindWrite(false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.viewport(0, 0, mainRenderTarget.viewWidth, mainRenderTarget.viewHeight);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().identity(), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, depthRenderTarget.getDepthTextureId());

        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.vertex(-1.0D, -1.0D, 0.0D).endVertex();
        bufferBuilder.vertex(1.0D, -1.0D, 0.0D).endVertex();
        bufferBuilder.vertex(1.0D, 1.0D, 0.0D).endVertex();
        bufferBuilder.vertex(-1.0D, 1.0D, 0.0D).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());

        modelViewStack.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static RenderTarget ensureDepthSnapshotTarget(RenderTarget mainRenderTarget)
    {
        if (depthSnapshotTarget == null)
        {
            depthSnapshotTarget = new TextureTarget(mainRenderTarget.width, mainRenderTarget.height, true, Minecraft.ON_OSX);
            return depthSnapshotTarget;
        }

        if (depthSnapshotTarget.width != mainRenderTarget.width || depthSnapshotTarget.height != mainRenderTarget.height)
        {
            depthSnapshotTarget.resize(mainRenderTarget.width, mainRenderTarget.height, Minecraft.ON_OSX);
        }

        return depthSnapshotTarget;
    }

    private static Vec3 projectFarRay(Matrix4f inverseProjection, Quaternionf cameraRotation, float ndcX, float ndcY)
    {
        Vector4f viewCorner = new Vector4f(ndcX, ndcY, 1.0F, 1.0F);
        inverseProjection.transform(viewCorner);
        float inverseW = 1.0F / Math.max(viewCorner.w, 0.0001F);
        Vector3f worldDirection = new Vector3f(viewCorner.x * inverseW, viewCorner.y * inverseW, viewCorner.z * inverseW);
        cameraRotation.transform(worldDirection);
        return new Vec3(worldDirection.x, worldDirection.y, worldDirection.z);
    }
}
