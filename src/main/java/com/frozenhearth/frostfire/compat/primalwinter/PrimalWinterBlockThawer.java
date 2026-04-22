package com.frozenhearth.frostfire.compat.primalwinter;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PrimalWinterBlockThawer
{
    private static final String SNOWY_PREFIX = "snowy_";
    private static final Map<Block, Block> thawedBlocks = new HashMap<>();
    private static final Set<Block> unresolvedBlocks = new HashSet<>();

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

        if (thawedBlocks.containsKey(frozenBlock))
        {
            return thawedBlocks.get(frozenBlock);
        }

        if (unresolvedBlocks.contains(frozenBlock))
        {
            return null;
        }

        Block thawedBlock = resolveThawedBlock(frozenBlock);
        if (thawedBlock != null)
        {
            thawedBlocks.put(frozenBlock, thawedBlock);
            return thawedBlock;
        }

        unresolvedBlocks.add(frozenBlock);
        return null;
    }

    @Nullable
    private static Block resolveThawedBlock(Block frozenBlock)
    {
        ResourceLocation frozenBlockId = ForgeRegistries.BLOCKS.getKey(frozenBlock);
        ResourceLocation vanillaBlockId = resolveVanillaBlockId(frozenBlockId);
        if (vanillaBlockId == null)
        {
            return null;
        }
        return ForgeRegistries.BLOCKS.getValue(vanillaBlockId);
    }

    @Nullable
    static ResourceLocation resolveVanillaBlockId(@Nullable ResourceLocation frozenBlockId)
    {
        if (frozenBlockId == null || !FrostfireCampfireMod.PRIMAL_WINTER_MOD_ID.equals(frozenBlockId.getNamespace()))
        {
            return null;
        }

        String path = frozenBlockId.getPath();
        if (!path.startsWith(SNOWY_PREFIX) || path.length() <= SNOWY_PREFIX.length())
        {
            return null;
        }

        return ResourceLocation.fromNamespaceAndPath("minecraft", path.substring(SNOWY_PREFIX.length()));
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
