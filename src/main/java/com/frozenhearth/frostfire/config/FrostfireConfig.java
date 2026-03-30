package com.frozenhearth.frostfire.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "frostfire", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FrostfireConfig
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_FUEL = BUILDER.defineInRange("maxFuelBuffer", 32000000, 1, Integer.MAX_VALUE);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LEVEL_THRESHOLDS = BUILDER
            .defineListAllowEmpty("levelThresholds", List.of(1, 1000000, 2500000, 5000000, 25000000), entry -> entry instanceof Integer value && value >= 0);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LEVEL_RADII = BUILDER
            .defineListAllowEmpty("levelRadii", List.of(3, 5, 12, 25, 50), entry -> entry instanceof Integer value && value >= 0);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HEAT_STRENGTHS = BUILDER
            .defineListAllowEmpty("heatStrengths", List.of(0.02D, 0.05D, 0.12D, 0.25D, 0.40D), entry -> entry instanceof Double || entry instanceof Integer);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> LEVEL_DECAY = BUILDER
            .defineListAllowEmpty("levelDecayPerSecond", List.of(20.0D, 20.0D, 50.0D, 150.0D, 500.0D), entry -> entry instanceof Double || entry instanceof Integer);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> INNER_HEAT_RADII = BUILDER
            .defineListAllowEmpty("innerHeatRadii", List.of(1, 5, 1, 2, 2), entry -> entry instanceof Integer value && value >= 0);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> INNER_HEAT_CELSIUS = BUILDER
            .defineListAllowEmpty("innerHeatCelsius", List.of(12.0D, 25.0D, 35.0D, 50.0D, 85.0D), entry -> entry instanceof Double || entry instanceof Integer);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Double>> OUTER_HEAT_CELSIUS = BUILDER
            .defineListAllowEmpty("outerHeatCelsius", List.of(8.0D, 25.0D, 25.0D, 25.0D, 25.0D), entry -> entry instanceof Double || entry instanceof Integer);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> LIGHT_VALUES = BUILDER
            .defineListAllowEmpty("lightValues", List.of(10, 11, 13, 14, 15), entry -> entry instanceof Integer value && value >= 0 && value <= 15);
    private static final ForgeConfigSpec.DoubleValue BASE_DECAY = BUILDER.defineInRange("baseDecayPerSecond", 20.0D, 0.01D, 1000.0D);
    private static final ForgeConfigSpec.DoubleValue OPEN_SKY_SNOW_MULT = BUILDER.defineInRange("openSkySnowMultiplier", 1.25D, 0.01D, 10.0D);
    private static final ForgeConfigSpec.DoubleValue ROOF_MULT = BUILDER.defineInRange("roofProtectionMultiplier", 0.85D, 0.01D, 10.0D);
    private static final ForgeConfigSpec.DoubleValue WINTER_STORM_MULT = BUILDER.defineInRange("winterStormMultiplier", 1.50D, 0.01D, 10.0D);
    private static final ForgeConfigSpec.IntValue BLOCK_TEMP_RADIUS_CAP = BUILDER.defineInRange("blockTempRadiusCap", 16, 1, 64);
    private static final ForgeConfigSpec.IntValue AURA_REFRESH_TICKS = BUILDER.defineInRange("auraRefreshTicks", 20, 1, 200);
    private static final ForgeConfigSpec.IntValue AURA_EXPIRE_TICKS = BUILDER.defineInRange("auraExpireTicks", 40, 1, 400);
    private static final ForgeConfigSpec.IntValue MELT_INTERVAL_TICKS = BUILDER.defineInRange("meltIntervalTicks", 10, 1, 200);
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> MELT_COLUMNS_PER_STEP = BUILDER
            .defineListAllowEmpty("meltColumnsPerStep", List.of(0, 32, 64, 128, 256), entry -> entry instanceof Integer value && value >= 0);
    private static final ForgeConfigSpec.IntValue MELT_DEPTH_BELOW_SURFACE = BUILDER.defineInRange("meltDepthBelowSurface", 2, 0, 16);
    private static final ForgeConfigSpec.DoubleValue MAX_TOTAL_HEAT = BUILDER.defineInRange("maxTotalAuraHeat", 0.55D, 0.01D, 5.0D);
    private static final ForgeConfigSpec.DoubleValue GENERIC_FUEL_MULTIPLIER = BUILDER.defineInRange("genericFuelBurnTimeMultiplier", 0.15D, 0.0D, 10.0D);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FUEL_OVERRIDES = BUILDER
            .defineListAllowEmpty("fuelOverrides", List.of(), entry -> entry instanceof String text && text.contains("="));

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static int maxFuel = 32000000;
    private static int[] thresholds = new int[] {1, 1000000, 2500000, 5000000, 25000000};
    private static int[] radii = new int[] {3, 5, 12, 25, 50};
    private static double[] heatStrengths = new double[] {0.02D, 0.05D, 0.12D, 0.25D, 0.40D};
    private static double[] levelDecay = new double[] {20.0D, 20.0D, 50.0D, 150.0D, 500.0D};
    private static int[] innerHeatRadii = new int[] {1, 5, 1, 2, 2};
    private static double[] innerHeatCelsius = new double[] {12.0D, 25.0D, 35.0D, 50.0D, 85.0D};
    private static double[] outerHeatCelsius = new double[] {8.0D, 25.0D, 25.0D, 25.0D, 25.0D};
    private static int[] lightValues = new int[] {10, 11, 13, 14, 15};
    private static double baseDecay = 20.0D;
    private static double openSkySnowMultiplier = 1.25D;
    private static double roofProtectionMultiplier = 0.85D;
    private static double winterStormMultiplier = 1.50D;
    private static int blockTempRadiusCap = 16;
    private static int auraRefreshTicks = 20;
    private static int auraExpireTicks = 40;
    private static int meltIntervalTicks = 10;
    private static int[] meltColumnsPerStep = new int[] {0, 32, 64, 128, 256};
    private static int meltDepthBelowSurface = 2;
    private static double maxTotalAuraHeat = 0.55D;
    private static double genericFuelBurnTimeMultiplier = 0.15D;
    private static Map<ResourceLocation, Integer> fuelOverrides = Map.of();

    private FrostfireConfig() {}

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent event)
    {
        maxFuel = MAX_FUEL.get();
        thresholds = sanitizeInts(LEVEL_THRESHOLDS.get(), thresholds);
        radii = sanitizeInts(LEVEL_RADII.get(), radii);
        heatStrengths = sanitizeDoubles(HEAT_STRENGTHS.get(), heatStrengths);
        levelDecay = sanitizeDoubles(LEVEL_DECAY.get(), levelDecay);
        innerHeatRadii = sanitizeInts(INNER_HEAT_RADII.get(), innerHeatRadii);
        innerHeatCelsius = sanitizeDoubles(INNER_HEAT_CELSIUS.get(), innerHeatCelsius);
        outerHeatCelsius = sanitizeDoubles(OUTER_HEAT_CELSIUS.get(), outerHeatCelsius);
        lightValues = sanitizeInts(LIGHT_VALUES.get(), lightValues);
        baseDecay = BASE_DECAY.get();
        openSkySnowMultiplier = OPEN_SKY_SNOW_MULT.get();
        roofProtectionMultiplier = ROOF_MULT.get();
        winterStormMultiplier = WINTER_STORM_MULT.get();
        blockTempRadiusCap = BLOCK_TEMP_RADIUS_CAP.get();
        auraRefreshTicks = AURA_REFRESH_TICKS.get();
        auraExpireTicks = AURA_EXPIRE_TICKS.get();
        meltIntervalTicks = MELT_INTERVAL_TICKS.get();
        meltColumnsPerStep = sanitizeInts(MELT_COLUMNS_PER_STEP.get(), meltColumnsPerStep);
        meltDepthBelowSurface = MELT_DEPTH_BELOW_SURFACE.get();
        maxTotalAuraHeat = MAX_TOTAL_HEAT.get();
        genericFuelBurnTimeMultiplier = GENERIC_FUEL_MULTIPLIER.get();
        fuelOverrides = parseFuelOverrides(FUEL_OVERRIDES.get());
    }

    public static int getMaxFuel()
    {
        return maxFuel;
    }

    public static int resolveLevel(int fuelBuffer)
    {
        if (fuelBuffer <= 0)
        {
            return 0;
        }
        for (int level = thresholds.length - 1; level >= 0; level--)
        {
            if (fuelBuffer >= thresholds[level])
            {
                return level;
            }
        }
        return 0;
    }

    public static int getMinimumFuelForLevel(int level)
    {
        return thresholds[Mth.clamp(level, 0, thresholds.length - 1)];
    }

    public static int getRadiusForLevel(int level)
    {
        return radii[Mth.clamp(level, 0, radii.length - 1)];
    }

    public static double getHeatStrengthForLevel(int level)
    {
        return heatStrengths[Mth.clamp(level, 0, heatStrengths.length - 1)];
    }

    public static double getDecayPerSecondForLevel(int level)
    {
        return levelDecay[Mth.clamp(level, 0, levelDecay.length - 1)];
    }

    public static int getInnerHeatRadiusForLevel(int level)
    {
        return innerHeatRadii[Mth.clamp(level, 0, innerHeatRadii.length - 1)];
    }

    public static double getInnerHeatCelsiusForLevel(int level)
    {
        return innerHeatCelsius[Mth.clamp(level, 0, innerHeatCelsius.length - 1)];
    }

    public static double getOuterHeatCelsiusForLevel(int level)
    {
        return outerHeatCelsius[Mth.clamp(level, 0, outerHeatCelsius.length - 1)];
    }

    public static int getLightForLevel(int level)
    {
        return lightValues[Mth.clamp(level, 0, lightValues.length - 1)];
    }

    public static double getBaseDecay()
    {
        return baseDecay;
    }

    public static double getOpenSkySnowMultiplier()
    {
        return openSkySnowMultiplier;
    }

    public static double getRoofProtectionMultiplier()
    {
        return roofProtectionMultiplier;
    }

    public static double getWinterStormMultiplier()
    {
        return winterStormMultiplier;
    }

    public static int getBlockTempRadiusCap()
    {
        return blockTempRadiusCap;
    }

    public static int getAuraRefreshTicks()
    {
        return auraRefreshTicks;
    }

    public static int getAuraExpireTicks()
    {
        return auraExpireTicks;
    }

    public static int getMeltIntervalTicks()
    {
        return meltIntervalTicks;
    }

    public static int getMeltColumnsPerStep(int level)
    {
        return meltColumnsPerStep[Mth.clamp(level, 0, meltColumnsPerStep.length - 1)];
    }

    public static int getMeltDepthBelowSurface()
    {
        return meltDepthBelowSurface;
    }

    public static double getMaxTotalAuraHeat()
    {
        return maxTotalAuraHeat;
    }

    public static double getGenericFuelBurnTimeMultiplier()
    {
        return genericFuelBurnTimeMultiplier;
    }

    public static Map<ResourceLocation, Integer> getFuelOverrides()
    {
        return fuelOverrides;
    }

    private static int[] sanitizeInts(List<? extends Integer> values, int[] fallback)
    {
        if (values.size() != fallback.length)
        {
            return fallback;
        }
        int[] result = new int[fallback.length];
        for (int i = 0; i < fallback.length; i++)
        {
            result[i] = Math.max(0, values.get(i));
        }
        return result;
    }

    private static double[] sanitizeDoubles(List<? extends Double> values, double[] fallback)
    {
        if (values.size() != fallback.length)
        {
            return fallback;
        }
        double[] result = new double[fallback.length];
        for (int i = 0; i < fallback.length; i++)
        {
            result[i] = Math.max(0, values.get(i));
        }
        return result;
    }

    private static Map<ResourceLocation, Integer> parseFuelOverrides(List<? extends String> entries)
    {
        Map<ResourceLocation, Integer> parsed = new HashMap<>();
        for (String entry : entries)
        {
            String[] split = entry.split("=", 2);
            if (split.length != 2)
            {
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(split[0].trim());
            if (id == null)
            {
                continue;
            }

            try
            {
                parsed.put(id, Integer.parseInt(split[1].trim()));
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return parsed;
    }
}
