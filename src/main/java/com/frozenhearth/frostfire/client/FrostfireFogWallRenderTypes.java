package com.frozenhearth.frostfire.client;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class FrostfireFogWallRenderTypes extends RenderStateShard
{
    private static final ShaderStateShard CAMPFIRE_DOMAIN_WALL_SHADER =
            new ShaderStateShard(FrostfireFogWallShaderRegistry::getCampfireDomainWallShader);

    private static final RenderType CAMPFIRE_DOMAIN_WALL = RenderType.create(
            FrostfireCampfireMod.MOD_ID + ":campfire_domain_wall",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            8192,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(CAMPFIRE_DOMAIN_WALL_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(true));

    private FrostfireFogWallRenderTypes()
    {
        super("frostfire_fog_wall", () -> {}, () -> {});
    }

    public static RenderType campfireDomainWall()
    {
        return CAMPFIRE_DOMAIN_WALL;
    }
}
