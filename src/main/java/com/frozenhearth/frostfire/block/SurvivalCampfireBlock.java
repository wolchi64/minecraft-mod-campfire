package com.frozenhearth.frostfire.block;

import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.config.FrostfireConfig;
import com.frozenhearth.frostfire.fuel.CampfireFuelRegistry;
import com.frozenhearth.frostfire.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SurvivalCampfireBlock extends BaseEntityBlock implements EntityBlock
{
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    private static final VoxelShape LEVEL_0_SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 1, 14),
            Block.box(2, 0, 2, 6, 3, 6),
            Block.box(10, 0, 2, 14, 3, 6),
            Block.box(2, 0, 10, 6, 3, 14),
            Block.box(10, 0, 10, 14, 3, 14));
    private static final VoxelShape LEVEL_1_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(0, 0, 0, 16, 4, 4),
            Block.box(0, 0, 12, 16, 4, 16),
            Block.box(0, 0, 4, 4, 4, 12),
            Block.box(12, 0, 4, 16, 4, 12));
    private static final VoxelShape LEVEL_2_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 1, 16),
            Block.box(1, 0, 0, 15, 4, 4),
            Block.box(1, 0, 12, 15, 4, 16),
            Block.box(0, 0, 1, 4, 4, 15),
            Block.box(12, 0, 1, 16, 4, 15),
            Block.box(3, 4, 2, 13, 8, 5),
            Block.box(3, 4, 11, 13, 8, 14),
            Block.box(2, 4, 3, 5, 8, 13),
            Block.box(11, 4, 3, 14, 8, 13));

    public SurvivalCampfireBlock(BlockBehaviour.Properties properties)
    {
        super(properties.lightLevel(state -> state.getValue(LIT) ? FrostfireConfig.getLightForLevel(state.getValue(LEVEL)) : 0));
        registerDefaultState(this.stateDefinition.any().setValue(LIT, false).setValue(LEVEL, 0));
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getLevelShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getLevelShape(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return getLevelShape(state);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    private static VoxelShape getLevelShape(BlockState state)
    {
        return switch (state.getValue(LEVEL))
        {
            case 0 -> LEVEL_0_SHAPE;
            case 1 -> LEVEL_1_SHAPE;
            default -> LEVEL_2_SHAPE;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SurvivalCampfireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SURVIVAL_CAMPFIRE.get(), SurvivalCampfireBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIT, LEVEL);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire))
        {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        boolean likelyHandled = player.isShiftKeyDown()
                                || held.isEmpty()
                                || held.is(Items.FLINT_AND_STEEL)
                                || held.is(Items.FIRE_CHARGE);
        if (level.isClientSide)
        {
            return likelyHandled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }

        if (held.isEmpty() || player.isShiftKeyDown())
        {
            player.displayClientMessage(campfire.buildStatusMessage(player.isShiftKeyDown()), false);
            return InteractionResult.CONSUME;
        }

        if (held.is(Items.FLINT_AND_STEEL))
        {
            if (campfire.ignite())
            {
                held.hurtAndBreak(1, player, living -> living.broadcastBreakEvent(hand));
                level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.frostfire.no_fuel"), false);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !(entity instanceof ItemEntity itemEntity))
        {
            return;
        }

        if (level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire)
        {
            campfire.tryConsumeFuelEntity(itemEntity);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof SurvivalCampfireBlockEntity campfire)
        {
            return campfire.getComparatorOutput();
        }
        return 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(LIT))
        {
            if (random.nextFloat() < 0.1F)
            {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0D, 0.02D, 0.0D);
            }
            return;
        }

        int stage = state.getValue(LEVEL);
        for (int i = 0; i < 1 + stage; i++)
        {
            double x = pos.getX() + 0.35D + (random.nextDouble() * 0.3D);
            double z = pos.getZ() + 0.35D + (random.nextDouble() * 0.3D);
            double y = pos.getY() + 0.35D + (stage * 0.08D);
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.02D, 0.0D);
            if (random.nextFloat() < 0.35F + (stage * 0.1F))
            {
                level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 0.2D, z, 0.0D, 0.04D, 0.0D);
            }
        }

        if (random.nextInt(12) == 0)
        {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.CAMPFIRE_CRACKLE,
                                 SoundSource.BLOCKS, 0.7F + (stage * 0.1F), 0.9F + (stage * 0.05F), false);
        }
    }
}
