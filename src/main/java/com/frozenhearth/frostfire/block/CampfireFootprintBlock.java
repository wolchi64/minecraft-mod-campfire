package com.frozenhearth.frostfire.block;

import com.frozenhearth.frostfire.blockentity.SurvivalCampfireBlockEntity;
import com.frozenhearth.frostfire.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CampfireFootprintBlock extends Block
{
    public static final IntegerProperty MASTER_OFFSET_X = IntegerProperty.create("master_offset_x", -1, 1);
    public static final IntegerProperty MASTER_OFFSET_Z = IntegerProperty.create("master_offset_z", -1, 1);
    private static final VoxelShape FOOTPRINT_SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public CampfireFootprintBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(MASTER_OFFSET_X, 0)
                .setValue(MASTER_OFFSET_Z, 0));
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return FOOTPRINT_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return FOOTPRINT_SHAPE;
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return FOOTPRINT_SHAPE;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(MASTER_OFFSET_X, MASTER_OFFSET_Z);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        BlockPos masterPos = getMasterPos(pos, state);
        BlockState masterState = level.getBlockState(masterPos);
        if (!masterState.is(ModBlocks.SURVIVAL_CAMPFIRE.get()))
        {
            return InteractionResult.PASS;
        }
        return masterState.use(level, player, hand, new BlockHitResult(hit.getLocation(), hit.getDirection(), masterPos, hit.isInside()));
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (level.isClientSide || !(entity instanceof ItemEntity itemEntity))
        {
            return;
        }

        BlockPos masterPos = getMasterPos(pos, state);
        if (level.getBlockEntity(masterPos) instanceof SurvivalCampfireBlockEntity campfire)
        {
            campfire.tryConsumeFuelEntity(itemEntity);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        BlockPos masterPos = getMasterPos(pos, state);
        if (level.getBlockState(masterPos).is(ModBlocks.SURVIVAL_CAMPFIRE.get()))
        {
            level.destroyBlock(masterPos, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state)
    {
        return new ItemStack(ModBlocks.SURVIVAL_CAMPFIRE.get());
    }

    @Override
    public BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        BlockPos masterPos = getMasterPos(pos, state);
        return level.getBlockState(masterPos).is(ModBlocks.SURVIVAL_CAMPFIRE.get())
               ? super.updateShape(state, direction, neighborState, level, pos, neighborPos)
               : Blocks.AIR.defaultBlockState();
    }

    public static BlockPos getMasterPos(BlockPos pos, BlockState state)
    {
        return pos.offset(state.getValue(MASTER_OFFSET_X), 0, state.getValue(MASTER_OFFSET_Z));
    }
}
