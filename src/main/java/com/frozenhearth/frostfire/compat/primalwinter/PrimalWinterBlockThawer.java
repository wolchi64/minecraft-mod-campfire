package com.frozenhearth.frostfire.compat.primalwinter;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public final class PrimalWinterBlockThawer
{
    private static final Map<String, Block> VANILLA_REPLACEMENTS = Map.ofEntries(
            Map.entry("snowy_dirt", Blocks.DIRT),
            Map.entry("snowy_coarse_dirt", Blocks.COARSE_DIRT),
            Map.entry("snowy_sand", Blocks.SAND),
            Map.entry("snowy_red_sand", Blocks.RED_SAND),
            Map.entry("snowy_gravel", Blocks.GRAVEL),
            Map.entry("snowy_mud", Blocks.MUD),
            Map.entry("snowy_stone", Blocks.STONE),
            Map.entry("snowy_granite", Blocks.GRANITE),
            Map.entry("snowy_andesite", Blocks.ANDESITE),
            Map.entry("snowy_diorite", Blocks.DIORITE),
            Map.entry("snowy_white_terracotta", Blocks.WHITE_TERRACOTTA),
            Map.entry("snowy_orange_terracotta", Blocks.ORANGE_TERRACOTTA),
            Map.entry("snowy_terracotta", Blocks.TERRACOTTA),
            Map.entry("snowy_yellow_terracotta", Blocks.YELLOW_TERRACOTTA),
            Map.entry("snowy_brown_terracotta", Blocks.BROWN_TERRACOTTA),
            Map.entry("snowy_red_terracotta", Blocks.RED_TERRACOTTA),
            Map.entry("snowy_light_gray_terracotta", Blocks.LIGHT_GRAY_TERRACOTTA),
            Map.entry("snowy_dirt_path", Blocks.DIRT_PATH),
            Map.entry("snowy_oak_log", Blocks.OAK_LOG),
            Map.entry("snowy_birch_log", Blocks.BIRCH_LOG),
            Map.entry("snowy_spruce_log", Blocks.SPRUCE_LOG),
            Map.entry("snowy_jungle_log", Blocks.JUNGLE_LOG),
            Map.entry("snowy_dark_oak_log", Blocks.DARK_OAK_LOG),
            Map.entry("snowy_acacia_log", Blocks.ACACIA_LOG),
            Map.entry("snowy_cherry_log", Blocks.CHERRY_LOG),
            Map.entry("snowy_mangrove_log", Blocks.MANGROVE_LOG),
            Map.entry("snowy_oak_leaves", Blocks.OAK_LEAVES),
            Map.entry("snowy_birch_leaves", Blocks.BIRCH_LEAVES),
            Map.entry("snowy_spruce_leaves", Blocks.SPRUCE_LEAVES),
            Map.entry("snowy_jungle_leaves", Blocks.JUNGLE_LEAVES),
            Map.entry("snowy_dark_oak_leaves", Blocks.DARK_OAK_LEAVES),
            Map.entry("snowy_acacia_leaves", Blocks.ACACIA_LEAVES),
            Map.entry("snowy_cherry_leaves", Blocks.CHERRY_LEAVES),
            Map.entry("snowy_mangrove_leaves", Blocks.MANGROVE_LEAVES),
            Map.entry("snowy_mangrove_roots", Blocks.MANGROVE_ROOTS),
            Map.entry("snowy_muddy_mangrove_roots", Blocks.MUDDY_MANGROVE_ROOTS),
            Map.entry("snowy_vine", Blocks.VINE)
    );

    private static final Map<Block, Block> thawedBlocks = new HashMap<>();
    private static boolean initialized;

    private PrimalWinterBlockThawer() {}

    public static boolean tryThaw(ServerLevel level, BlockPos pos, BlockState frozenState)
    {
        Block replacementBlock = getThawedBlock(frozenState.getBlock());
        if (replacementBlock == null)
        {
            return false;
        }

        BlockState replacementState = copySharedProperties(frozenState, replacementBlock.defaultBlockState());
        Block.pushEntitiesUp(frozenState, replacementState, level, pos);
        level.setBlockAndUpdate(pos, replacementState);
        return true;
    }

    @Nullable
    private static Block getThawedBlock(Block frozenBlock)
    {
        if (!FrostfireCampfireMod.isPrimalWinterLoaded())
        {
            return null;
        }

        if (!initialized)
        {
            initialized = true;
            VANILLA_REPLACEMENTS.forEach(PrimalWinterBlockThawer::cacheReplacement);
        }

        return thawedBlocks.get(frozenBlock);
    }

    private static void cacheReplacement(String primalWinterBlockName, Block vanillaBlock)
    {
        Block frozenBlock = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath(FrostfireCampfireMod.PRIMAL_WINTER_MOD_ID, primalWinterBlockName));
        if (frozenBlock != null)
        {
            thawedBlocks.put(frozenBlock, vanillaBlock);
        }
    }

    private static BlockState copySharedProperties(BlockState source, BlockState target)
    {
        BlockState copied = target;
        for (Property<?> sourceProperty : source.getProperties())
        {
            copied = copySharedProperty(source, copied, sourceProperty);
        }
        return copied;
    }

    private static BlockState copySharedProperty(BlockState source, BlockState target, Property<?> sourceProperty)
    {
        Property<?> targetProperty = target.getBlock().getStateDefinition().getProperty(sourceProperty.getName());
        if (targetProperty == null)
        {
            return target;
        }

        Comparable<?> value = source.getValue(sourceProperty);
        if (!targetProperty.getPossibleValues().contains(value))
        {
            return target;
        }

        return setProperty(target, targetProperty, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setProperty(BlockState state, Property property, Comparable value)
    {
        return state.setValue(property, value);
    }
}
