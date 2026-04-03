package com.frozenhearth.frostfire;

import com.frozenhearth.frostfire.compat.coldsweat.ColdSweatCompat;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.frozenhearth.frostfire.fuel.CampfireFuelRegistry;
import com.frozenhearth.frostfire.registry.ModBlockEntities;
import com.frozenhearth.frostfire.registry.ModBlocks;
import com.frozenhearth.frostfire.registry.ModItems;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(FrostfireCampfireMod.MOD_ID)
public class FrostfireCampfireMod
{
    public static final String MOD_ID = "frostfire";
    public static final String PRIMAL_WINTER_MOD_ID = "primalwinter";

    public FrostfireCampfireMod(FMLJavaModLoadingContext context)
    {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.REGISTER.register(modBus);
        ModItems.REGISTER.register(modBus);
        ModBlockEntities.REGISTER.register(modBus);
        modBus.addListener(this::commonSetup);

        context.registerConfig(ModConfig.Type.COMMON, FrostfireConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(FrostfireEvents.class);
        if (ModList.get().isLoaded("cold_sweat"))
        {
            MinecraftForge.EVENT_BUS.register(new ColdSweatCompat());
        }
    }

    public static boolean isPrimalWinterLoaded()
    {
        return ModList.get().isLoaded(PRIMAL_WINTER_MOD_ID);
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(CampfireFuelRegistry::bootstrap);
    }
}
