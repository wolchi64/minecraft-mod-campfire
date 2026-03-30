package com.frozenhearth.frostfire.compat.winter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class WinterWeatherManager
{
    private static final WinterWeatherBridge BRIDGE = new VanillaWinterWeatherBridge();

    private WinterWeatherManager() {}

    public static double getAdditionalDecayMultiplier(ServerLevel level, BlockPos pos)
    {
        return BRIDGE.getAdditionalDecayMultiplier(level, pos);
    }
}
