package com.frozenhearth.frostfire;

import com.frozenhearth.frostfire.compat.coldsweat.SurvivalCampfireAuraModifier;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.frozenhearth.frostfire.block.SurvivalCampfireBlock;
import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.fuel.CampfireFuelRegistry;
import com.frozenhearth.frostfire.registry.ModBlocks;
import com.frozenhearth.frostfire.registry.ModItems;
import com.frozenhearth.frostfire.util.ActiveCampfireTracker;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.Placement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class FrostfireEvents
{
    @SubscribeEvent
    public static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS)
        {
            event.accept(ModItems.SURVIVAL_CAMPFIRE_ITEM.get());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide)
        {
            return;
        }

        if (event.player.tickCount % FrostfireConfig.getAuraRefreshTicks() != 0)
        {
            return;
        }

        double targetFloor = ActiveCampfireTracker.computeAuraEffect(event.player);
        if (targetFloor > 0)
        {
            SurvivalCampfireAuraModifier modifier = new SurvivalCampfireAuraModifier(targetFloor)
                    .tickRate(FrostfireConfig.getAuraRefreshTicks())
                    .expires(FrostfireConfig.getAuraExpireTicks());
            Temperature.removeModifiers(event.player, Temperature.Trait.WORLD, mod -> mod instanceof SurvivalCampfireAuraModifier);
            Temperature.addOrReplaceModifier(event.player, modifier, Temperature.Trait.WORLD, Placement.Duplicates.BY_CLASS);
        }
        else
        {
            Temperature.removeModifiers(event.player, Temperature.Trait.WORLD, mod -> mod instanceof SurvivalCampfireAuraModifier);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || !(event.level instanceof ServerLevel serverLevel))
        {
            return;
        }

        for (ItemEntity itemEntity : serverLevel.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                                                             entity -> entity.isAlive() && CampfireFuelRegistry.getFuel(entity.getItem()).isValid()))
        {
            tryUpgradeVanillaCampfire(serverLevel, itemEntity);
        }
    }

    private static void tryUpgradeVanillaCampfire(ServerLevel level, ItemEntity itemEntity)
    {
        BlockPos[] candidates = new BlockPos[] {
                itemEntity.blockPosition(),
                itemEntity.blockPosition().below()
        };

        for (BlockPos pos : candidates)
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CAMPFIRE))
            {
                boolean lit = state.getValue(CampfireBlock.LIT);
                BlockState upgraded = ModBlocks.SURVIVAL_CAMPFIRE.get().defaultBlockState()
                        .setValue(SurvivalCampfireBlock.LIT, lit)
                        .setValue(SurvivalCampfireBlock.LEVEL, 0);
                level.setBlock(pos, upgraded, 3);
                if (level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire)
                {
                    campfire.setLitState(lit);
                    campfire.tryConsumeFuelEntity(itemEntity);
                }
                return;
            }
        }
    }
}
