package com.frozenhearth.frostfire.mixin.server;

import com.frozenhearth.frostfire.util.ActiveCampfireTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public class ServerLevelMixin
{
    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean frostfire$preventFreezingInsideCampfireRadius(Biome biome, net.minecraft.world.level.LevelReader level, BlockPos pos)
    {
        if (ActiveCampfireTracker.isWeatherSuppressed((ServerLevel) (Object) this, pos))
        {
            return false;
        }
        return biome.shouldFreeze(level, pos);
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean frostfire$preventSnowPlacementInsideCampfireRadius(Biome biome, net.minecraft.world.level.LevelReader level, BlockPos pos)
    {
        if (ActiveCampfireTracker.isWeatherSuppressed((ServerLevel) (Object) this, pos))
        {
            return false;
        }
        return biome.shouldSnow(level, pos);
    }

    @Redirect(
            method = "tickChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation frostfire$suppressPrecipitationInsideCampfireRadius(Biome biome, BlockPos pos)
    {
        if (ActiveCampfireTracker.isWeatherSuppressed((ServerLevel) (Object) this, pos))
        {
            return Biome.Precipitation.NONE;
        }
        return biome.getPrecipitationAt(pos);
    }
}
