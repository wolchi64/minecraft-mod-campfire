package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FrostfireClientWeatherCache
{
    public static final int MAX_RENDERED_WALLS = 8;
    private static final int ZONE_SEARCH_PADDING = 64;
    private static final int CACHE_REFRESH_INTERVAL = 10;
    private static final double WEATHER_TRANSITION_BLOCKS = 6.0D;

    private static ClientLevel cachedLevel;
    private static long lastRefreshTick = Long.MIN_VALUE;
    private static int lastCenterChunkX = Integer.MIN_VALUE;
    private static int lastCenterChunkZ = Integer.MIN_VALUE;
    private static final List<WeatherZone> ACTIVE_ZONES = new ArrayList<>();

    private FrostfireClientWeatherCache() {}

    public static boolean isWeatherSuppressed(Vec3 weatherPos)
    {
        return sampleWeatherSuppression(weatherPos).strength() > 0.0F;
    }

    public static WeatherSuppressionSample sampleWeatherSuppression(Vec3 weatherPos)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            ACTIVE_ZONES.clear();
            cachedLevel = null;
            return WeatherSuppressionSample.NONE;
        }

        refreshZones(minecraft.level, minecraft.player.position());
        float strongestStrength = 0.0F;
        double deepestInsideDistance = 0.0D;
        Vec3 selectedCenter = Vec3.ZERO;
        double selectedRadius = 0.0D;
        for (WeatherZone zone : ACTIVE_ZONES)
        {
            double edgeDistance = zone.edgeDistance(weatherPos.x, weatherPos.z);
            float strength = Mth.clamp((float) (edgeDistance / WEATHER_TRANSITION_BLOCKS), 0.0F, 1.0F);
            if (strength > strongestStrength || (strength == strongestStrength && edgeDistance > deepestInsideDistance))
            {
                strongestStrength = strength;
                deepestInsideDistance = edgeDistance;
                selectedCenter = zone.center();
                selectedRadius = zone.radius();
            }
        }
        if (strongestStrength <= 0.0F)
        {
            return WeatherSuppressionSample.NONE;
        }
        return new WeatherSuppressionSample(strongestStrength, deepestInsideDistance, selectedCenter, selectedRadius);
    }

    public static List<WeatherZoneSnapshot> getActiveZones(Vec3 focus)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            ACTIVE_ZONES.clear();
            cachedLevel = null;
            return List.of();
        }

        refreshZones(minecraft.level, focus);
        return ACTIVE_ZONES.stream()
                .map(zone -> new WeatherZoneSnapshot(zone.center(), zone.radius()))
                .sorted(Comparator.comparingDouble(zone -> zone.center().distanceToSqr(focus)))
                .toList();
    }

    public static List<WeatherZoneSnapshot> getNearestActiveZones(Vec3 focus, int maxCount)
    {
        return getActiveZones(focus).stream()
                .limit(maxCount)
                .toList();
    }

    public static float getWallWeatherIntensity(float partialTick)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
        {
            return 0.0F;
        }
        return Math.max(minecraft.level.getRainLevel(partialTick), minecraft.level.getThunderLevel(partialTick) * 0.5F);
    }

    private static void refreshZones(ClientLevel level, Vec3 focus)
    {
        int centerChunkX = Mth.floor(focus.x) >> 4;
        int centerChunkZ = Mth.floor(focus.z) >> 4;
        long gameTime = level.getGameTime();

        if (level == cachedLevel
            && centerChunkX == lastCenterChunkX
            && centerChunkZ == lastCenterChunkZ
            && gameTime - lastRefreshTick < CACHE_REFRESH_INTERVAL)
        {
            return;
        }

        cachedLevel = level;
        lastCenterChunkX = centerChunkX;
        lastCenterChunkZ = centerChunkZ;
        lastRefreshTick = gameTime;
        ACTIVE_ZONES.clear();

        int searchRadiusBlocks = ZONE_SEARCH_PADDING + FrostfireConfig.getMaxCampfireRadius();
        int chunkRadius = Mth.ceil(searchRadiusBlocks / 16.0D) + 1;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++)
        {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++)
            {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null)
                {
                    continue;
                }

                chunk.getBlockEntities().values().forEach(blockEntity -> {
                    if (blockEntity instanceof SurvivalCampfireBlockEntity campfire && campfire.isActive())
                    {
                        ACTIVE_ZONES.add(new WeatherZone(Vec3.atCenterOf(blockEntity.getBlockPos()), campfire.getActiveRadius()));
                    }
                });
            }
        }
    }

    private record WeatherZone(Vec3 center, double radius)
    {
        private double distanceToCenter(double x, double z)
        {
            double dx = x - center.x;
            double dz = z - center.z;
            return Math.sqrt((dx * dx) + (dz * dz));
        }

        private double edgeDistance(double x, double z)
        {
            return radius - distanceToCenter(x, z);
        }
    }

    public record WeatherSuppressionSample(float strength, double insideDistance, Vec3 zoneCenter, double zoneRadius)
    {
        private static final WeatherSuppressionSample NONE = new WeatherSuppressionSample(0.0F, 0.0D, Vec3.ZERO, 0.0D);
    }

    public record WeatherZoneSnapshot(Vec3 center, double radius) {}
}
