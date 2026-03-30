package com.frozenhearth.frostfire.compat.coldsweat;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.momosoftworks.coldsweat.api.event.core.registry.BlockTempRegisterEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ColdSweatCompat
{
    @SubscribeEvent
    public void onRegisterBlockTemps(BlockTempRegisterEvent event)
    {
        event.register(new SurvivalCampfireBlockTemp());
    }

    @SubscribeEvent
    public void onRegisterTempModifiers(TempModifierRegisterEvent event)
    {
        event.register(ResourceLocation.fromNamespaceAndPath(FrostfireCampfireMod.MOD_ID, "survival_campfire_aura"), SurvivalCampfireAuraModifier::new);
    }
}
