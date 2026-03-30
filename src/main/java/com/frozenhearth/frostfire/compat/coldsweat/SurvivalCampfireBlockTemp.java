package com.frozenhearth.frostfire.compat.coldsweat;

import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.frozenhearth.frostfire.registry.ModBlocks;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class SurvivalCampfireBlockTemp extends BlockTemp
{
    public SurvivalCampfireBlockTemp()
    {
        super(0,
              Temperature.convert(FrostfireConfig.getInnerHeatCelsiusForLevel(4), Temperature.Units.C, Temperature.Units.MC, true),
              Double.NEGATIVE_INFINITY,
              Double.POSITIVE_INFINITY,
              FrostfireConfig.getBlockTempRadiusCap(), true, ModBlocks.SURVIVAL_CAMPFIRE.get());
    }

    @Override
    public double getTemperature(Level level, @Nullable LivingEntity entity, BlockState state, BlockPos pos, double distance)
    {
        return 0;
    }

    @Override
    public boolean isValid(Level level, BlockPos pos, BlockState state)
    {
        return level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire && campfire.isActive();
    }
}
