package com.frozenhearth.frostfire.compat.coldsweat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

public class SurvivalCampfireAuraModifier extends TempModifier
{
    public SurvivalCampfireAuraModifier()
    {
        this(0.0D);
    }

    public SurvivalCampfireAuraModifier(double effect)
    {
        this.getNBT().putDouble("TargetFloor", effect);
    }

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait)
    {
        double targetFloor = this.getNBT().getDouble("TargetFloor");
        return temp -> Math.max(temp, targetFloor);
    }
}
