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
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FrostfireFogWallWorldRenderer
{
    private static final RenderLevelStageEvent.Stage RENDER_STAGE = RenderLevelStageEvent.Stage.AFTER_LEVEL;
    private static final int MAX_ZONES = FrostfireClientWeatherCache.MAX_RENDERED_WALLS;
    private static final float WALL_HALF_THICKNESS = 14.5F;
    private static final float WALL_BOTTOM_OFFSET = -34.0F;
    private static final float WALL_TOP_OFFSET_CLEAR = 58.0F;
    private static final float WALL_TOP_OFFSET_STORM = 74.0F;
    private static final float REVEAL_MASK_WORLD_RADIUS = 3.0F;  // world-space radius of the reveal soft blob
    private static TextureTarget depthSnapshotTarget;

    private FrostfireFogWallWorldRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event)
    {
        if (event.getStage() != RENDER_STAGE)
        {
            return;
        }

        if (!FrostfireCampfireMod.isPrimalWinterLoaded())
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
        FrostfireClientWeatherCache.WeatherSuppressionSample suppression =
                FrostfireClientWeatherCache.sampleWeatherSuppression(cameraPos);
        float interiorWallAlphaScale = FrostfireFogBlend.computeInteriorWallAlphaScale(suppression.insideDistance());
        if (interiorWallAlphaScale <= 0.001F)
        {
            return;
        }

        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> zones =
                FrostfireClientWeatherCache.getConnectedWallZones(cameraPos, MAX_ZONES);
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
        FrostfireClientWeatherCache.WeatherZoneSnapshot revealTarget =
                FrostfireClientWeatherCache.getNearestRevealTarget(cameraPos);
        configureShader(shader, event, camera, cameraPos, visibleZones, weatherIntensity, wallTime,
                mainRenderTarget, revealTarget, interiorWallAlphaScale);
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

    private static void configureShader(ShaderInstance shader, RenderLevelStageEvent event, Camera camera, Vec3 cameraPos,
                                        List<FrostfireClientWeatherCache.WeatherZoneSnapshot> visibleZones,
                                        float weatherIntensity, float wallTime, RenderTarget mainRenderTarget,
                                        FrostfireClientWeatherCache.WeatherZoneSnapshot revealTarget,
                                        float interiorWallAlphaScale)
    {
        Matrix4f inverseProjection = new Matrix4f(event.getProjectionMatrix()).invert();
        Vector3f lookVector = camera.getLookVector();
        Vector3f upVector = camera.getUpVector();
        Vector3f leftVector = camera.getLeftVector();
        float wallTopOffset = Mth.lerp(weatherIntensity, WALL_TOP_OFFSET_CLEAR, WALL_TOP_OFFSET_STORM);

        shader.safeGetUniform("InverseProjMat").set(inverseProjection);
        shader.safeGetUniform("TargetSize").set((float) mainRenderTarget.width, (float) mainRenderTarget.height);
        shader.safeGetUniform("CameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
        shader.safeGetUniform("CameraLook").set(lookVector.x, lookVector.y, lookVector.z);
        shader.safeGetUniform("CameraUp").set(upVector.x, upVector.y, upVector.z);
        shader.safeGetUniform("CameraLeft").set(leftVector.x, leftVector.y, leftVector.z);
        shader.safeGetUniform("Time").set(wallTime);
        shader.safeGetUniform("WeatherIntensity").set(weatherIntensity);
        shader.safeGetUniform("WallHalfThickness").set(WALL_HALF_THICKNESS);
        shader.safeGetUniform("WallBottomOffset").set(WALL_BOTTOM_OFFSET);
        shader.safeGetUniform("WallTopOffset").set(wallTopOffset);
        shader.safeGetUniform("FarPlaneDistance").set(Minecraft.getInstance().gameRenderer.getDepthFar());
        shader.safeGetUniform("InteriorWallAlphaScale").set(interiorWallAlphaScale);
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
        setRevealMaskUniforms(shader, revealTarget, cameraPos, lookVector, upVector, leftVector, event.getProjectionMatrix());
    }

    /**
     * Projects the nearest reveal campfire to screen UV and passes RevealMaskCenter / RevealMaskRadius
     * to the fog shader.  When no target exists the mask is disabled (radius 0).
     *
     * Coordinate derivation mirrors the GLSL worldRayDirection() in the fragment shader:
     *   worldDir = CameraLeft*(-vx) + CameraUp*(vy) + CameraLook*(-vz)
     * Inverse: vx = -dot(world, left), vy = dot(world, up), vz = -dot(world, look)
     */
    private static void setRevealMaskUniforms(
            ShaderInstance shader,
            FrostfireClientWeatherCache.WeatherZoneSnapshot revealTarget,
            Vec3 cameraPos, Vector3f look, Vector3f up, Vector3f left,
            Matrix4f projMat)
    {
        if (revealTarget == null)
        {
            shader.safeGetUniform("RevealMaskCenter").set(-2.0F, -2.0F);
            shader.safeGetUniform("RevealMaskRadius").set(0.0F);
            return;
        }

        // Elevate to flame/smoke area
        Vec3 campPos = revealTarget.center().add(0.0D, 1.5D, 0.0D);
        double dx = campPos.x - cameraPos.x;
        double dy = campPos.y - cameraPos.y;
        double dz = campPos.z - cameraPos.z;

        // View-space coords
        float vx = -(float) (dx * left.x  + dy * left.y  + dz * left.z);
        float vy =  (float) (dx * up.x    + dy * up.y    + dz * up.z);
        float vz = -(float) (dx * look.x  + dy * look.y  + dz * look.z);

        // vz < 0 means in front of camera (standard OpenGL view space)
        if (vz >= -0.5F)
        {
            shader.safeGetUniform("RevealMaskCenter").set(-2.0F, -2.0F);
            shader.safeGetUniform("RevealMaskRadius").set(0.0F);
            return;
        }

        // Project to clip space
        org.joml.Vector4f clipPos = projMat.transform(
                new org.joml.Vector4f(vx, vy, vz, 1.0F), new org.joml.Vector4f());
        if (clipPos.w <= 0.0F)
        {
            shader.safeGetUniform("RevealMaskCenter").set(-2.0F, -2.0F);
            shader.safeGetUniform("RevealMaskRadius").set(0.0F);
            return;
        }

        float uvX = (clipPos.x / clipPos.w) * 0.5F + 0.5F;
        float uvY = (clipPos.y / clipPos.w) * 0.5F + 0.5F;

        // Screen-space radius in UV-Y units (m11 = Y focal length of perspective matrix)
        float screenRadius = projMat.m11() * REVEAL_MASK_WORLD_RADIUS / (2.0F * (-vz));
        screenRadius = Mth.clamp(screenRadius, 0.025F, 0.30F);

        shader.safeGetUniform("RevealMaskCenter").set(uvX, uvY);
        shader.safeGetUniform("RevealMaskRadius").set(screenRadius);
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
}
