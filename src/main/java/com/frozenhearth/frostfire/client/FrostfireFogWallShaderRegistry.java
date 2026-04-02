package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

@Mod.EventBusSubscriber(modid = FrostfireCampfireMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FrostfireFogWallShaderRegistry
{
    private static ShaderInstance campfireDomainWallShader;

    private FrostfireFogWallShaderRegistry() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException
    {
        ResourceProvider resourceProvider = event.getResourceProvider();
        ShaderInstance shader = new ShaderInstance(resourceProvider,
                new ResourceLocation(FrostfireCampfireMod.MOD_ID, "campfire_domain_wall"),
                DefaultVertexFormat.POSITION);
        event.registerShader(shader, loadedShader -> campfireDomainWallShader = loadedShader);
    }

    public static ShaderInstance getCampfireDomainWallShader()
    {
        return campfireDomainWallShader;
    }
}
