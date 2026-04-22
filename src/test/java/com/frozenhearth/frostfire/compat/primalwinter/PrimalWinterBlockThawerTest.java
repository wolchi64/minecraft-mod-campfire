package com.frozenhearth.frostfire.compat.primalwinter;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrimalWinterBlockThawerTest
{
    @Test
    void resolvesSnowyPrimalWinterBlocksBackToMinecraft()
    {
        ResourceLocation frozen = ResourceLocation.fromNamespaceAndPath("primalwinter", "snowy_cherry_leaves");

        ResourceLocation thawed = PrimalWinterBlockThawer.resolveVanillaBlockId(frozen);

        assertEquals(ResourceLocation.fromNamespaceAndPath("minecraft", "cherry_leaves"), thawed);
    }

    @Test
    void ignoresNonSnowyPrimalWinterBlocks()
    {
        ResourceLocation frozen = ResourceLocation.fromNamespaceAndPath("primalwinter", "cherry_leaves");

        assertNull(PrimalWinterBlockThawer.resolveVanillaBlockId(frozen));
    }

    @Test
    void ignoresBlocksOutsidePrimalWinter()
    {
        ResourceLocation frozen = ResourceLocation.fromNamespaceAndPath("minecraft", "snowy_cherry_leaves");

        assertNull(PrimalWinterBlockThawer.resolveVanillaBlockId(frozen));
    }
}
