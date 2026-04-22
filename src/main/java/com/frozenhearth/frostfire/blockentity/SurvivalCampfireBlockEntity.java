package com.frozenhearth.frostfire.blockentity;

import com.frozenhearth.frostfire.FrostfireCampfireMod;
import com.frozenhearth.frostfire.block.CampfireFootprintBlock;
import com.frozenhearth.frostfire.block.SurvivalCampfireBlock;
import com.frozenhearth.frostfire.compat.primalwinter.PrimalWinterBlockThawer;
import com.frozenhearth.frostfire.compat.winter.WinterWeatherManager;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.frozenhearth.frostfire.fuel.CampfireFuelRegistry;
import com.frozenhearth.frostfire.registry.ModBlockEntities;
import com.frozenhearth.frostfire.registry.ModBlocks;
import com.frozenhearth.frostfire.util.ActiveCampfireTracker;
import com.frozenhearth.frostfire.util.CampfireLevel;
import com.frozenhearth.frostfire.util.TimeFormatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class SurvivalCampfireBlockEntity extends BlockEntity
{
    private static final int MAX_FOOTPRINT_RADIUS = 2;
    private static final int MAX_FOOTPRINT_LAYERS = 4;
    private static final int PRIMAL_WINTER_THAW_INTERVAL_TICKS = 100;
    private int fuelBuffer;
    private int currentLevel;
    private boolean lit;
    private boolean sheltered;
    private double currentDecayRate = FrostfireConfig.getBaseDecay();
    private double decayProgress;
    private int secondTicker;
    private int meltTicker;
    private int primalWinterThawTicker;
    private int meltRingRadius;
    private int meltRingIndex;

    public SurvivalCampfireBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.SURVIVAL_CAMPFIRE.get(), pos, state);
        this.lit = state.hasProperty(SurvivalCampfireBlock.LIT) && state.getValue(SurvivalCampfireBlock.LIT);
        this.currentLevel = state.hasProperty(SurvivalCampfireBlock.LEVEL) ? state.getValue(SurvivalCampfireBlock.LEVEL) : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SurvivalCampfireBlockEntity campfire)
    {
        if (!(level instanceof ServerLevel serverLevel))
        {
            return;
        }

        campfire.consumeNearbyFuelEntities(serverLevel);
        campfire.tickServer(serverLevel);
    }

    private void tickServer(ServerLevel level)
    {
        secondTicker++;
        boolean oldSheltered = sheltered;
        int oldLevel = currentLevel;
        boolean oldLit = lit;

        if (secondTicker >= 20)
        {
            secondTicker = 0;
            sheltered = !level.canSeeSky(worldPosition.above());
            currentDecayRate = calculateDecayRate(level);
        }

        if (lit && fuelBuffer > 0)
        {
            decayProgress += currentDecayRate / 20.0D;
            int burn = Mth.floor(decayProgress);
            if (burn > 0)
            {
                decayProgress -= burn;
                fuelBuffer = Math.max(0, fuelBuffer - burn);
            }
        }

        if (fuelBuffer <= 0)
        {
            fuelBuffer = 0;
            lit = false;
            decayProgress = 0;
        }

        currentLevel = FrostfireConfig.resolveLevel(fuelBuffer);
        boolean stateChanged = oldLevel != currentLevel || oldLit != lit;
        if (stateChanged)
        {
            resetMeltSweep();
        }

        syncFootprintLayout(level);
        if (stateChanged && isActive())
        {
            thawNearbyPrimalWinter(level);
            primalWinterThawTicker = 0;
        }
        tickMelting(level);

        if (oldSheltered != sheltered || oldLevel != currentLevel || oldLit != lit)
        {
            syncState();
        }
        else
        {
            setChanged();
        }
    }

    private void consumeNearbyFuelEntities(ServerLevel level)
    {
        int radius = getFootprintRadius();
        int layers = getFootprintLayerCount();
        AABB box = shouldHaveFootprint()
                   ? new AABB(
                           worldPosition.getX() - radius,
                           worldPosition.getY() + 0.25D,
                           worldPosition.getZ() - radius,
                           worldPosition.getX() + radius + 1.0D,
                           worldPosition.getY() + layers + 1.25D,
                           worldPosition.getZ() + radius + 1.0D
                   )
                   : new AABB(
                           worldPosition.getX(),
                           worldPosition.getY() + 0.25D,
                           worldPosition.getZ(),
                           worldPosition.getX() + 1.0D,
                           worldPosition.getY() + 1.25D,
                           worldPosition.getZ() + 1.0D
                   );
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, box, EntitySelector.ENTITY_STILL_ALIVE))
        {
            if (fuelBuffer >= FrostfireConfig.getMaxFuel())
            {
                break;
            }
            tryConsumeFuelEntity(itemEntity);
        }
    }

    private double calculateDecayRate(ServerLevel level)
    {
        double rate = FrostfireConfig.getDecayPerSecondForLevel(currentLevel);
        if (sheltered)
        {
            rate *= FrostfireConfig.getRoofProtectionMultiplier();
        }
        else if (level.isRainingAt(worldPosition.above()) && !ActiveCampfireTracker.isWeatherSuppressed(level, worldPosition.above()))
        {
            rate *= FrostfireConfig.getOpenSkySnowMultiplier();
        }
        return rate * WinterWeatherManager.getAdditionalDecayMultiplier(level, worldPosition);
    }

    private void tickMelting(ServerLevel level)
    {
        if (!isActive() || currentLevel <= 0)
        {
            resetMeltSweep();
            primalWinterThawTicker = 0;
            return;
        }

        tickPrimalWinterThaw(level);

        meltTicker++;
        if (meltTicker < FrostfireConfig.getMeltIntervalTicks())
        {
            return;
        }

        meltTicker = 0;
        meltNearbySnowAndIce(level);
    }

    private void tickPrimalWinterThaw(ServerLevel level)
    {
        if (!FrostfireCampfireMod.isPrimalWinterLoaded())
        {
            return;
        }

        primalWinterThawTicker++;
        if (primalWinterThawTicker < PRIMAL_WINTER_THAW_INTERVAL_TICKS)
        {
            return;
        }

        primalWinterThawTicker = 0;
        thawNearbyPrimalWinter(level);
    }

    private void resetMeltSweep()
    {
        meltTicker = 0;
        meltRingRadius = 0;
        meltRingIndex = 0;
    }

    private void syncFootprintLayout(ServerLevel level)
    {
        clearUnusedFootprintBlocks(level);
        if (shouldHaveFootprint())
        {
            placeFootprintBlocks(level);
        }
    }

    private boolean shouldHaveFootprint()
    {
        return isActive() && currentLevel >= 2;
    }

    private int getFootprintRadius()
    {
        if (!shouldHaveFootprint())
        {
            return 0;
        }
        return currentLevel >= 4 ? 2 : 1;
    }

    private int getFootprintLayerCount()
    {
        if (!shouldHaveFootprint())
        {
            return 0;
        }
        return currentLevel >= 4 ? 4 : 2;
    }

    private void placeFootprintBlocks(ServerLevel level)
    {
        int radius = getFootprintRadius();
        int layers = getFootprintLayerCount();
        for (int layer = 0; layer < layers; layer++)
        {
            placeFootprintLayer(level, radius, layer);
        }
    }

    private void clearUnusedFootprintBlocks(ServerLevel level)
    {
        int activeRadius = getFootprintRadius();
        int activeLayers = getFootprintLayerCount();
        for (int layer = 0; layer < MAX_FOOTPRINT_LAYERS; layer++)
        {
            for (int dx = -MAX_FOOTPRINT_RADIUS; dx <= MAX_FOOTPRINT_RADIUS; dx++)
            {
                for (int dz = -MAX_FOOTPRINT_RADIUS; dz <= MAX_FOOTPRINT_RADIUS; dz++)
                {
                    if (layer == 0 && dx == 0 && dz == 0)
                    {
                        continue;
                    }

                    BlockPos footprintPos = worldPosition.offset(dx, layer, dz);
                    BlockState footprintState = level.getBlockState(footprintPos);
                    if (!footprintState.is(ModBlocks.SURVIVAL_CAMPFIRE_FOOTPRINT.get()))
                    {
                        continue;
                    }

                    boolean belongsToThis = CampfireFootprintBlock.getMasterPos(footprintPos, footprintState).equals(worldPosition);
                    boolean shouldExist = layer < activeLayers
                                          && Math.abs(dx) <= activeRadius
                                          && Math.abs(dz) <= activeRadius
                                          && shouldPlaceFootprintAt(layer, dx, dz);
                    if (belongsToThis && !shouldExist)
                    {
                        level.removeBlock(footprintPos, false);
                    }
                }
            }
        }
    }

    private void meltNearbySnowAndIce(ServerLevel level)
    {
        int radius = getActiveRadius();
        if (radius <= 0)
        {
            return;
        }

        int columnsPerStep = FrostfireConfig.getMeltColumnsPerStep(currentLevel);
        if (columnsPerStep <= 0)
        {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int processed = 0;

        while (processed < columnsPerStep)
        {
            if (meltRingRadius > radius)
            {
                meltRingRadius = 0;
                meltRingIndex = 0;
            }

            RingOffset offset = getRingOffset(meltRingRadius, meltRingIndex++);
            if (meltRingIndex >= getRingSize(meltRingRadius))
            {
                meltRingIndex = 0;
                meltRingRadius++;
            }

            int dx = offset.dx();
            int dz = offset.dz();
            if ((dx * dx) + (dz * dz) > radius * radius)
            {
                continue;
            }
            processed++;

            int x = worldPosition.getX() + dx;
            int z = worldPosition.getZ() + dz;
            mutablePos.set(x, worldPosition.getY(), z);
            if (!level.isLoaded(mutablePos))
            {
                continue;
            }

            int startY = getColumnStartY(level, x, z);
            int endY = getColumnEndY(level, x, z, startY);

            for (int y = startY; y >= endY; y--)
            {
                mutablePos.set(x, y, z);
                if (tryMeltBlock(level, mutablePos))
                {
                    break;
                }
            }
        }
    }

    private void thawNearbyPrimalWinter(ServerLevel level)
    {
        int radius = getActiveRadius();
        if (radius <= 0 || !FrostfireCampfireMod.isPrimalWinterLoaded())
        {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if ((dx * dx) + (dz * dz) > radius * radius)
                {
                    continue;
                }

                int x = worldPosition.getX() + dx;
                int z = worldPosition.getZ() + dz;
                mutablePos.set(x, worldPosition.getY(), z);
                if (!level.isLoaded(mutablePos))
                {
                    continue;
                }

                int startY = getColumnStartY(level, x, z);
                int endY = level.getMinBuildHeight();
                for (int y = startY; y >= endY; y--)
                {
                    mutablePos.set(x, y, z);
                    PrimalWinterBlockThawer.tryThaw(level, mutablePos, level.getBlockState(mutablePos));
                }
            }
        }
    }

    public void clearFreshWeatherSnow(ServerLevel level)
    {
        int radius = getActiveRadius();
        if (radius <= 0 || !isActive() || !level.isRaining())
        {
            return;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if ((dx * dx) + (dz * dz) > radius * radius)
                {
                    continue;
                }

                int x = worldPosition.getX() + dx;
                int z = worldPosition.getZ() + dz;
                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int startY = Math.min(level.getMaxBuildHeight() - 1, surfaceY + 1);
                int endY = Math.max(level.getMinBuildHeight(), surfaceY - 1);

                for (int y = startY; y >= endY; y--)
                {
                    mutablePos.set(x, y, z);
                    if (tryMeltBlock(level, mutablePos))
                    {
                        break;
                    }
                }
            }
        }
    }

    private int getRingSize(int ring)
    {
        return ring <= 0 ? 1 : ring * 8;
    }

    private RingOffset getRingOffset(int ring, int index)
    {
        if (ring <= 0)
        {
            return new RingOffset(0, 0);
        }

        int normalizedIndex = Math.floorMod(index, getRingSize(ring));
        int topEdge = (ring * 2) + 1;
        int rightEdge = ring * 2;
        int bottomEdge = ring * 2;

        if (normalizedIndex < topEdge)
        {
            return new RingOffset(-ring + normalizedIndex, -ring);
        }

        normalizedIndex -= topEdge;
        if (normalizedIndex < rightEdge)
        {
            return new RingOffset(ring, -ring + 1 + normalizedIndex);
        }

        normalizedIndex -= rightEdge;
        if (normalizedIndex < bottomEdge)
        {
            return new RingOffset(ring - 1 - normalizedIndex, ring);
        }

        normalizedIndex -= bottomEdge;
        return new RingOffset(-ring, ring - 1 - normalizedIndex);
    }

    private boolean tryMeltBlock(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (PrimalWinterBlockThawer.tryThaw(level, pos, state))
        {
            return true;
        }

        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW))
        {
            level.removeBlock(pos, false);
            return true;
        }

        if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))
        {
            BlockState replacement = getMeltedIceState(level, pos);
            Block.pushEntitiesUp(state, replacement, level, pos);
            level.setBlockAndUpdate(pos, replacement);
            return true;
        }
        return false;
    }

    private int getColumnStartY(ServerLevel level, int x, int z)
    {
        int canopyY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return Math.min(level.getMaxBuildHeight() - 1, Math.max(worldPosition.getY() + 2, canopyY));
    }

    private int getColumnEndY(ServerLevel level, int x, int z, int startY)
    {
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.min(startY, Math.max(level.getMinBuildHeight(), groundY - FrostfireConfig.getMeltDepthBelowSurface()));
    }

    private BlockState getMeltedIceState(ServerLevel level, BlockPos pos)
    {
        if (level.dimensionType().ultraWarm())
        {
            return Blocks.AIR.defaultBlockState();
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        return belowState.blocksMotion() || belowState.liquid()
               ? Blocks.WATER.defaultBlockState()
               : Blocks.AIR.defaultBlockState();
    }

    public boolean tryConsumeFuelFromPlayer(Player player, InteractionHand hand, boolean bulkInsert)
    {
        ItemStack held = player.getItemInHand(hand);
        int attempts = bulkInsert ? held.getCount() : 1;
        boolean inserted = false;

        for (int i = 0; i < attempts; i++)
        {
            CampfireFuelRegistry.FuelResult fuel = CampfireFuelRegistry.getFuel(held);
            if (!fuel.isValid() || fuelBuffer >= FrostfireConfig.getMaxFuel())
            {
                break;
            }

            fuelBuffer = Math.min(FrostfireConfig.getMaxFuel(), fuelBuffer + fuel.fuelValue());
            currentLevel = FrostfireConfig.resolveLevel(fuelBuffer);
            inserted = true;

            if (!player.getAbilities().instabuild)
            {
                held.shrink(1);
                if (!fuel.remainder().isEmpty())
                {
                    if (held.isEmpty())
                    {
                        player.setItemInHand(hand, fuel.remainder().copy());
                        held = player.getItemInHand(hand);
                    }
                    else if (!player.addItem(fuel.remainder().copy()))
                    {
                        player.drop(fuel.remainder().copy(), false);
                    }
                }
            }

            if (fuel.ignites())
            {
                lit = true;
            }
        }

        if (inserted && level != null)
        {
            level.playSound(null, worldPosition, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F);
            syncState();
        }
        return inserted;
    }

    public boolean tryConsumeFuelEntity(ItemEntity itemEntity)
    {
        if (level == null)
        {
            return false;
        }

        ItemStack stack = itemEntity.getItem();
        boolean inserted = false;

        while (!stack.isEmpty() && fuelBuffer < FrostfireConfig.getMaxFuel())
        {
            CampfireFuelRegistry.FuelResult fuel = CampfireFuelRegistry.getFuel(stack);
            if (!fuel.isValid())
            {
                break;
            }

            fuelBuffer = Math.min(FrostfireConfig.getMaxFuel(), fuelBuffer + fuel.fuelValue());
            currentLevel = FrostfireConfig.resolveLevel(fuelBuffer);
            inserted = true;

            stack.shrink(1);
            if (!fuel.remainder().isEmpty())
            {
                ItemEntity remainder = new ItemEntity(level,
                                                     itemEntity.getX(),
                                                     itemEntity.getY() + 0.15D,
                                                     itemEntity.getZ(),
                                                     fuel.remainder().copy());
                remainder.setDeltaMovement(itemEntity.getDeltaMovement().scale(0.2D));
                level.addFreshEntity(remainder);
            }

            if (fuel.ignites())
            {
                lit = true;
            }
        }

        if (!inserted)
        {
            return false;
        }

        if (stack.isEmpty())
        {
            itemEntity.discard();
        }
        else
        {
            itemEntity.setItem(stack);
            itemEntity.setPickUpDelay(10);
        }

        level.playSound(null, worldPosition, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.0F);
        syncState();
        return true;
    }

    public boolean ignite()
    {
        if (lit || fuelBuffer <= 0)
        {
            return false;
        }

        lit = true;
        syncState();
        return true;
    }

    public void setLitState(boolean lit)
    {
        this.lit = lit;
        syncState();
    }

    public boolean isActive()
    {
        return lit && fuelBuffer > 0;
    }

    public int getActiveRadius()
    {
        return isActive() ? FrostfireConfig.getRadiusForLevel(currentLevel) : 0;
    }

    public int getCurrentLevel()
    {
        return currentLevel;
    }

    public double getHeatStrength()
    {
        return isActive() ? FrostfireConfig.getHeatStrengthForLevel(currentLevel) : 0.0D;
    }

    public double getCurrentDecayRate()
    {
        return currentDecayRate;
    }

    public double getSecondsUntilDowngrade()
    {
        if (!isActive())
        {
            return 0;
        }

        if (currentLevel <= 0)
        {
            return fuelBuffer / Math.max(0.01D, currentDecayRate);
        }

        return Math.max(0, fuelBuffer - FrostfireConfig.getMinimumFuelForLevel(currentLevel)) / Math.max(0.01D, currentDecayRate);
    }

    public int getComparatorOutput()
    {
        return Mth.floor(((float) fuelBuffer / (float) FrostfireConfig.getMaxFuel()) * 15.0F);
    }

    public Component buildStatusMessage(boolean detailed)
    {
        CampfireLevel levelName = CampfireLevel.fromIndex(currentLevel);
        int radius = getActiveRadius();
        String timerKey = currentLevel == 0 ? "message.frostfire.status.extinguishes" : "message.frostfire.status.downgrades";
        Component summary = Component.translatable("message.frostfire.status.summary",
                                                   levelName.getName(),
                                                   currentLevel,
                                                   radius,
                                                   Component.translatable(timerKey),
                                                   TimeFormatHelper.formatSeconds(getSecondsUntilDowngrade()),
                                                   String.format("%.2f/s", currentDecayRate),
                                                   Component.translatable(sheltered ? "message.frostfire.status.sheltered" : "message.frostfire.status.exposed"));
        if (!detailed)
        {
            return !lit && fuelBuffer > 0
                   ? Component.translatable("message.frostfire.status.unlit_prefix").append(" ").append(summary)
                   : summary;
        }
        return Component.translatable("message.frostfire.status.detailed",
                                      summary,
                                      lit ? Component.literal("Lit") : Component.literal("Unlit"));
    }

    public void onBroken(ServerLevel level)
    {
        ActiveCampfireTracker.remove(level, worldPosition);
        clearFootprintBlocks(level);
    }

    private void syncState()
    {
        if (level == null)
        {
            return;
        }

        currentLevel = FrostfireConfig.resolveLevel(fuelBuffer);
        BlockState state = getBlockState();
        BlockState updated = state.setValue(SurvivalCampfireBlock.LEVEL, currentLevel)
                                  .setValue(SurvivalCampfireBlock.LIT, lit && fuelBuffer > 0);
        if (!state.equals(updated))
        {
            level.setBlock(worldPosition, updated, 3);
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, state, updated, 3);
        level.updateNeighbourForOutputSignal(worldPosition, updated.getBlock());

        if (level instanceof ServerLevel serverLevel)
        {
            ActiveCampfireTracker.setActive(serverLevel, worldPosition, isActive());
            syncFootprintLayout(serverLevel);
        }
    }

    @Override
    public void onLoad()
    {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel)
        {
            ActiveCampfireTracker.setActive(serverLevel, worldPosition, isActive());
        }
    }

    @Override
    public void onChunkUnloaded()
    {
        if (level instanceof ServerLevel serverLevel)
        {
            ActiveCampfireTracker.remove(serverLevel, worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved()
    {
        if (level instanceof ServerLevel serverLevel)
        {
            ActiveCampfireTracker.remove(serverLevel, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        tag.putInt("FuelBuffer", fuelBuffer);
        tag.putInt("CurrentLevel", currentLevel);
        tag.putBoolean("Lit", lit);
        tag.putBoolean("Sheltered", sheltered);
        tag.putDouble("CurrentDecayRate", currentDecayRate);
        tag.putDouble("DecayProgress", decayProgress);
        tag.putInt("MeltRingRadius", meltRingRadius);
        tag.putInt("MeltRingIndex", meltRingIndex);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        fuelBuffer = tag.getInt("FuelBuffer");
        currentLevel = tag.getInt("CurrentLevel");
        lit = tag.getBoolean("Lit");
        sheltered = tag.getBoolean("Sheltered");
        currentDecayRate = tag.getDouble("CurrentDecayRate");
        decayProgress = tag.getDouble("DecayProgress");
        meltRingRadius = tag.getInt("MeltRingRadius");
        meltRingIndex = tag.getInt("MeltRingIndex");
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private record RingOffset(int dx, int dz) {}

    private boolean shouldPlaceFootprintAt(int layer, int dx, int dz)
    {
        if (layer == 0)
        {
            return dx != 0 || dz != 0;
        }

        if (currentLevel >= 4)
        {
            return switch (layer)
            {
                case 1 -> Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                case 2 -> Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                case 3 -> false;
                default -> false;
            };
        }

        return Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
    }

    private void placeFootprintLayer(ServerLevel level, int radius, int layer)
    {
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                if (!shouldPlaceFootprintAt(layer, dx, dz))
                {
                    continue;
                }

                BlockPos footprintPos = worldPosition.offset(dx, layer, dz);
                BlockState existingState = level.getBlockState(footprintPos);
                BlockState expectedState = ModBlocks.SURVIVAL_CAMPFIRE_FOOTPRINT.get().defaultBlockState()
                        .setValue(CampfireFootprintBlock.UPPER, layer > 0)
                        .setValue(CampfireFootprintBlock.LAYER, layer)
                        .setValue(CampfireFootprintBlock.MASTER_OFFSET_X, CampfireFootprintBlock.encodeMasterOffset(-dx))
                        .setValue(CampfireFootprintBlock.MASTER_OFFSET_Z, CampfireFootprintBlock.encodeMasterOffset(-dz));

                if (existingState.is(ModBlocks.SURVIVAL_CAMPFIRE_FOOTPRINT.get())
                    && existingState.getValue(CampfireFootprintBlock.LAYER) == layer
                    && CampfireFootprintBlock.getMasterPos(footprintPos, existingState).equals(worldPosition))
                {
                    continue;
                }

                if (existingState.canBeReplaced())
                {
                    level.setBlock(footprintPos, expectedState, 3);
                }
            }
        }
    }

    private void clearFootprintBlocks(ServerLevel level)
    {
        int previousLevel = currentLevel;
        currentLevel = 4;
        clearUnusedFootprintBlocks(level);
        currentLevel = previousLevel;
    }
}
