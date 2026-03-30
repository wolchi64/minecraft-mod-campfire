package com.frozenhearth.frostfire.compat.winter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public class VanillaWinterWeatherBridge implements WinterWeatherBridge
{
    @Override
    public double getAdditionalDecayMultiplier(ServerLevel level, BlockPos pos)
    {
        return 1.0D;
    }
}
