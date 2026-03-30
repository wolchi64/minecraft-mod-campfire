package com.frozenhearth.frostfire.util;

import net.minecraft.network.chat.Component;

public enum CampfireLevel
{
    EMBERS(0, "frostfire.level.embers"),
    CAMPFIRE(1, "frostfire.level.campfire"),
    STRONG_FIRE(2, "frostfire.level.strong_fire"),
    BONFIRE(3, "frostfire.level.bonfire"),
    BEACON_BONFIRE(4, "frostfire.level.beacon_bonfire");

    private final int index;
    private final String key;

    CampfireLevel(int index, String key)
    {
        this.index = index;
        this.key = key;
    }

    public Component getName()
    {
        return Component.translatable(key);
    }

    public static CampfireLevel fromIndex(int index)
    {
        for (CampfireLevel level : values())
        {
            if (level.index == index)
            {
                return level;
            }
        }
        return EMBERS;
    }
}
