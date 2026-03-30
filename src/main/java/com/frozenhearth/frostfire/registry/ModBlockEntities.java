package com.frozenhearth.frostfire.registry;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, FrostfireCampfireMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<SurvivalCampfireBlockEntity>> SURVIVAL_CAMPFIRE = REGISTER.register("survival_campfire",
            () -> BlockEntityType.Builder.of(SurvivalCampfireBlockEntity::new, ModBlocks.SURVIVAL_CAMPFIRE.get()).build(null));

    private ModBlockEntities() {}
}
