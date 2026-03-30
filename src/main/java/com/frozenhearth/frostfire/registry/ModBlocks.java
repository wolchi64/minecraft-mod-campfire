package com.frozenhearth.frostfire.registry;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.frozenhearth.frostfire.block.CampfireFootprintBlock;
import com.frozenhearth.frostfire.block.SurvivalCampfireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks
{
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, FrostfireCampfireMod.MOD_ID);

    public static final RegistryObject<Block> SURVIVAL_CAMPFIRE = REGISTER.register("survival_campfire",
            () -> new SurvivalCampfireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .strength(2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> SURVIVAL_CAMPFIRE_FOOTPRINT = REGISTER.register("survival_campfire_footprint",
            () -> new CampfireFootprintBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .replaceable()));

    private ModBlocks() {}
}
