package com.frozenhearth.frostfire.registry;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, FrostfireCampfireMod.MOD_ID);

    public static final RegistryObject<Item> SURVIVAL_CAMPFIRE_ITEM = REGISTER.register("survival_campfire",
            () -> new BlockItem(ModBlocks.SURVIVAL_CAMPFIRE.get(), new Item.Properties()));

    private ModItems() {}
}
