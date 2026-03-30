package com.frozenhearth.frostfire.compat.winter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface WinterWeatherBridge
{
    double getAdditionalDecayMultiplier(ServerLevel level, BlockPos pos);
}
