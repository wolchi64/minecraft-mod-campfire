package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
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
    private static final double CLIENT_ZONE_RADIUS_INSET = 2.0D;

    private static ClientLevel cachedLevel;
    private static long lastRefreshTick = Long.MIN_VALUE;
    private static int lastCenterChunkX = Integer.MIN_VALUE;
    private static int lastCenterChunkZ = Integer.MIN_VALUE;
    private static final List<WeatherZone> ACTIVE_ZONES = new ArrayList<>();

    private FrostfireClientWeatherCache() {}

    public static boolean isWeatherSuppressed(Vec3 weatherPos)
    {
        return sampleWeatherSuppression(weatherPos).insideDistance() > 0.0D;
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
        if (!FrostfireCampfireMod.isPrimalWinterLoaded())
        {
            return List.of();
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null)
        {
            ACTIVE_ZONES.clear();
            cachedLevel = null;
            return List.of();
        }

        refreshZones(minecraft.level, focus);
        return ACTIVE_ZONES.stream()
                .filter(WeatherZone::rendersFogWall)
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

    public static List<WeatherZoneSnapshot> getConnectedWallZones(Vec3 focus, int maxCount)
    {
        List<WeatherZoneSnapshot> wallZones = getActiveZones(focus);
        if (wallZones.isEmpty())
        {
            return List.of();
        }

        List<Integer> seedIndexes = new ArrayList<>();
        for (int zoneIndex = 0; zoneIndex < wallZones.size(); zoneIndex++)
        {
            WeatherZoneSnapshot zone = wallZones.get(zoneIndex);
            if (isInsideZone(zone, focus))
            {
                seedIndexes.add(zoneIndex);
            }
        }

        if (seedIndexes.isEmpty())
        {
            return wallZones.stream()
                    .limit(maxCount)
                    .toList();
        }

        boolean[] visited = new boolean[wallZones.size()];
        List<WeatherZoneSnapshot> connectedZones = new ArrayList<>();
        ArrayList<Integer> frontier = new ArrayList<>(seedIndexes);
        int frontierIndex = 0;
        while (frontierIndex < frontier.size())
        {
            int currentIndex = frontier.get(frontierIndex++);
            if (visited[currentIndex])
            {
                continue;
            }

            visited[currentIndex] = true;
            WeatherZoneSnapshot currentZone = wallZones.get(currentIndex);
            connectedZones.add(currentZone);

            for (int candidateIndex = 0; candidateIndex < wallZones.size(); candidateIndex++)
            {
                if (visited[candidateIndex])
                {
                    continue;
                }

                WeatherZoneSnapshot candidateZone = wallZones.get(candidateIndex);
                if (zonesOverlap(currentZone, candidateZone))
                {
                    frontier.add(candidateIndex);
                }
            }
        }

        return connectedZones.stream()
                .sorted(Comparator.comparingDouble(zone -> zone.center().distanceToSqr(focus)))
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
                        double clientRadius = Math.max(0.0D, campfire.getActiveRadius() - CLIENT_ZONE_RADIUS_INSET);
                        ACTIVE_ZONES.add(new WeatherZone(Vec3.atCenterOf(blockEntity.getBlockPos()), clientRadius, campfire.getCurrentLevel()));
                    }
                });
            }
        }
    }

    private record WeatherZone(Vec3 center, double radius, int level)
    {
        private boolean rendersFogWall()
        {
            return level >= 3;
        }

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

    private static boolean isInsideZone(WeatherZoneSnapshot zone, Vec3 pos)
    {
        return zone.center().distanceToSqr(pos.x, zone.center().y, pos.z) <= zone.radius() * zone.radius();
    }

    private static boolean zonesOverlap(WeatherZoneSnapshot firstZone, WeatherZoneSnapshot secondZone)
    {
        double maxDistance = firstZone.radius() + secondZone.radius();
        return firstZone.center().distanceToSqr(secondZone.center()) <= maxDistance * maxDistance;
    }

    public record WeatherSuppressionSample(float strength, double insideDistance, Vec3 zoneCenter, double zoneRadius)
    {
        private static final WeatherSuppressionSample NONE = new WeatherSuppressionSample(0.0F, 0.0D, Vec3.ZERO, 0.0D);
    }

    public record WeatherZoneSnapshot(Vec3 center, double radius) {}
}
