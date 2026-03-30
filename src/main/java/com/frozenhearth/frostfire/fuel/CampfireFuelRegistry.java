package com.frozenhearth.frostfire.fuel;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

public final class CampfireFuelRegistry
{
    private CampfireFuelRegistry() {}

    public static void bootstrap()
    {
    }

    public static FuelResult getFuel(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return FuelResult.EMPTY;
        }

        if (stack.is(Items.LAVA_BUCKET))
        {
            return new FuelResult(ForgeHooks.getBurnTime(stack, RecipeType.SMELTING), new ItemStack(Items.BUCKET), false);
        }

        int burnTime = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        if (burnTime > 0)
        {
            return new FuelResult(burnTime, ItemStack.EMPTY, false);
        }
        return FuelResult.EMPTY;
    }

    public record FuelResult(int fuelValue, ItemStack remainder, boolean ignites)
    {
        public static final FuelResult EMPTY = new FuelResult(0, ItemStack.EMPTY, false);

        public boolean isValid()
        {
            return fuelValue > 0;
        }
    }
}
