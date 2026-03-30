package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.frozenhearth.frostfire.registry.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrostfireClientSetup
{
    private FrostfireClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.SURVIVAL_CAMPFIRE.get(), RenderType.cutout()));
    }
}
