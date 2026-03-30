package com.frozenhearth.frostfire.util;

import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class ActiveCampfireTracker
{
    private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

    private ActiveCampfireTracker() {}

    public static synchronized void setActive(ServerLevel level, BlockPos pos, boolean active)
    {
        Set<BlockPos> positions = ACTIVE.computeIfAbsent(level.dimension(), key -> new HashSet<>());
        if (active)
        {
            positions.add(pos.immutable());
        }
        else
        {
            positions.remove(pos);
            if (positions.isEmpty())
            {
                ACTIVE.remove(level.dimension());
            }
        }
    }

    public static synchronized void remove(ServerLevel level, BlockPos pos)
    {
        Set<BlockPos> positions = ACTIVE.get(level.dimension());
        if (positions == null)
        {
            return;
        }
        positions.remove(pos);
        if (positions.isEmpty())
        {
            ACTIVE.remove(level.dimension());
        }
    }

    public static synchronized double computeAuraEffect(Player player)
    {
        if (!(player.level() instanceof ServerLevel level))
        {
            return 0;
        }

        Set<BlockPos> positions = ACTIVE.get(level.dimension());
        if (positions == null || positions.isEmpty())
        {
            return 0;
        }

        double strongestFloor = 0;
        Vec3 playerCenter = player.position();
        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext())
        {
            BlockPos pos = iterator.next();
            if (!level.isLoaded(pos))
            {
                iterator.remove();
                continue;
            }

            if (!(level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire) || !campfire.isActive())
            {
                iterator.remove();
                continue;
            }

            double radius = campfire.getActiveRadius();
            double distance = playerCenter.distanceTo(Vec3.atCenterOf(pos));
            if (distance > radius)
            {
                continue;
            }

            int levelIndex = campfire.getCurrentLevel();
            int innerRadius = FrostfireConfig.getInnerHeatRadiusForLevel(levelIndex);
            double targetCelsius = distance <= innerRadius
                                   ? FrostfireConfig.getInnerHeatCelsiusForLevel(levelIndex)
                                   : FrostfireConfig.getOuterHeatCelsiusForLevel(levelIndex);
            double targetMc = Temperature.convert(targetCelsius, Temperature.Units.C, Temperature.Units.MC, true);
            strongestFloor = Math.max(strongestFloor, targetMc);
        }
        return strongestFloor;
    }
}
