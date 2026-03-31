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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CampfireFootprintBlock extends Block
{
    public static final IntegerProperty MASTER_OFFSET_X = IntegerProperty.create("master_offset_x", 0, 2);
    public static final IntegerProperty MASTER_OFFSET_Z = IntegerProperty.create("master_offset_z", 0, 2);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    private static final VoxelShape LOWER_EDGE_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER_EDGE_SHAPE = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape UPPER_CENTER_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape LOWER_CORNER_NORTH_WEST_SHAPE = createLowerCornerShape(false, false);
    private static final VoxelShape LOWER_CORNER_NORTH_EAST_SHAPE = createLowerCornerShape(true, false);
    private static final VoxelShape LOWER_CORNER_SOUTH_WEST_SHAPE = createLowerCornerShape(false, true);
    private static final VoxelShape LOWER_CORNER_SOUTH_EAST_SHAPE = createLowerCornerShape(true, true);
    private static final VoxelShape UPPER_CORNER_NORTH_WEST_SHAPE = createUpperCornerShape(false, false);
    private static final VoxelShape UPPER_CORNER_NORTH_EAST_SHAPE = createUpperCornerShape(true, false);
    private static final VoxelShape UPPER_CORNER_SOUTH_WEST_SHAPE = createUpperCornerShape(false, true);
    private static final VoxelShape UPPER_CORNER_SOUTH_EAST_SHAPE = createUpperCornerShape(true, true);

    public CampfireFootprintBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UPPER, false)
                .setValue(MASTER_OFFSET_X, encodeMasterOffset(0))
                .setValue(MASTER_OFFSET_Z, encodeMasterOffset(0)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getFootprintShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return getFootprintShape(state);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return getFootprintShape(state);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return null;
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext useContext)
    {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(UPPER, MASTER_OFFSET_X, MASTER_OFFSET_Z);
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
        int dyToMaster = state.getValue(UPPER) ? -1 : 0;
        return pos.offset(decodeMasterOffset(state.getValue(MASTER_OFFSET_X)), dyToMaster, decodeMasterOffset(state.getValue(MASTER_OFFSET_Z)));
    }

    public static int encodeMasterOffset(int offset)
    {
        return offset + 1;
    }

    public static int decodeMasterOffset(int encodedOffset)
    {
        return encodedOffset - 1;
    }

    private static VoxelShape getFootprintShape(BlockState state)
    {
        boolean upper = state.getValue(UPPER);
        int dxToMaster = decodeMasterOffset(state.getValue(MASTER_OFFSET_X));
        int dzToMaster = decodeMasterOffset(state.getValue(MASTER_OFFSET_Z));
        if (upper && dxToMaster == 0 && dzToMaster == 0)
        {
            return UPPER_CENTER_SHAPE;
        }

        boolean isCorner = dxToMaster != 0 && dzToMaster != 0;
        if (!isCorner)
        {
            return upper ? UPPER_EDGE_SHAPE : LOWER_EDGE_SHAPE;
        }

        boolean masterIsEast = dxToMaster > 0;
        boolean masterIsSouth = dzToMaster > 0;
        if (masterIsEast)
        {
            return upper
                   ? (masterIsSouth ? UPPER_CORNER_SOUTH_EAST_SHAPE : UPPER_CORNER_NORTH_EAST_SHAPE)
                   : (masterIsSouth ? LOWER_CORNER_SOUTH_EAST_SHAPE : LOWER_CORNER_NORTH_EAST_SHAPE);
        }
        return upper
               ? (masterIsSouth ? UPPER_CORNER_SOUTH_WEST_SHAPE : UPPER_CORNER_NORTH_WEST_SHAPE)
               : (masterIsSouth ? LOWER_CORNER_SOUTH_WEST_SHAPE : LOWER_CORNER_NORTH_WEST_SHAPE);
    }

    private static VoxelShape createLowerCornerShape(boolean towardEast, boolean towardSouth)
    {
        double minX = towardEast ? 8.0D : 0.0D;
        double maxX = towardEast ? 16.0D : 8.0D;
        double minZ = towardSouth ? 8.0D : 0.0D;
        double maxZ = towardSouth ? 16.0D : 8.0D;

        return Shapes.or(
                Block.box(0, 0, 0, 16, 8, 16),
                Block.box(minX, 0, 0, maxX, 16, 16),
                Block.box(0, 0, minZ, 16, 16, maxZ),
                Block.box(minX, 0, minZ, maxX, 16, maxZ)
        );
    }

    private static VoxelShape createUpperCornerShape(boolean towardEast, boolean towardSouth)
    {
        double minX = towardEast ? 8.0D : 0.0D;
        double maxX = towardEast ? 16.0D : 8.0D;
        double minZ = towardSouth ? 8.0D : 0.0D;
        double maxZ = towardSouth ? 16.0D : 8.0D;
        return Block.box(minX, 0, minZ, maxX, 8, maxZ);
    }
}
