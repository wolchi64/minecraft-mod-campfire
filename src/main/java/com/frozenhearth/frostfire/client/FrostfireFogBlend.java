package com.frozenhearth.frostfire.client;

final class FrostfireFogBlend
{
    static final float FULL_VISUAL_CLEAR_DISTANCE = 16.0F;
    static final float CLEAR_TERRAIN_NEAR_DISTANCE = -16.0F;

    private FrostfireFogBlend() {}

    static float computeVisualBlend(double insideDistance)
    {
        float visualT = clamp01((float) (insideDistance / FULL_VISUAL_CLEAR_DISTANCE));
        float remaining = 1.0F - visualT;
        return 1.0F - (remaining * remaining * remaining);
    }

    static float computeInteriorWallAlphaScale(double insideDistance)
    {
        return 1.0F - computeVisualBlend(insideDistance);
    }

    private static float clamp01(float value)
    {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
