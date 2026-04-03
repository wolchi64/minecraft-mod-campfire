package com.frozenhearth.frostfire.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrostfireFogBlendTest
{
    @Test
    void returnsZeroOutsideTheZone()
    {
        assertEquals(0.0F, FrostfireFogBlend.computeVisualBlend(-1.0D));
        assertEquals(0.0F, FrostfireFogBlend.computeVisualBlend(0.0D));
    }

    @Test
    void startsFadingImmediatelyInsideTheBoundary()
    {
        float blend = FrostfireFogBlend.computeVisualBlend(0.25D);
        assertTrue(blend > 0.0F, "Expected a visible fade immediately inside the safe zone");
        assertTrue(blend < 0.1F, "Expected the first step inside the safe zone to stay subtle");
    }

    @Test
    void increasesMonotonicallyWithDistance()
    {
        float nearEdge = FrostfireFogBlend.computeVisualBlend(1.0D);
        float midZone = FrostfireFogBlend.computeVisualBlend(4.0D);
        float deepInside = FrostfireFogBlend.computeVisualBlend(7.0D);

        assertTrue(nearEdge < midZone, "Expected the blend to keep increasing inside the zone");
        assertTrue(midZone < deepInside, "Expected the blend to keep increasing toward full clarity");
    }

    @Test
    void reachesFullClearAtEightBlocksInside()
    {
        assertEquals(1.0F, FrostfireFogBlend.computeVisualBlend(8.0D));
        assertEquals(1.0F, FrostfireFogBlend.computeVisualBlend(16.0D));
    }
}
