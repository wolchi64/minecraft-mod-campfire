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
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CampfireFootprintBlock extends Block
{
    public static final IntegerProperty MASTER_OFFSET_X = IntegerProperty.create("master_offset_x", 0, 4);
    public static final IntegerProperty MASTER_OFFSET_Z = IntegerProperty.create("master_offset_z", 0, 4);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 0, 3);

    private static final VoxelShape EMPTY = Shapes.empty();
    private static final VoxelShape FULL_BLOCK = Block.box(0, 0, 0, 16, 16, 16);
    public CampfireFootprintBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UPPER, false)
                .setValue(LAYER, 0)
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
        builder.add(UPPER, LAYER, MASTER_OFFSET_X, MASTER_OFFSET_Z);
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
        int layer = state.getValue(LAYER);
        if (layer == 0 && state.getValue(UPPER))
        {
            layer = 1;
        }
        return pos.offset(
                decodeMasterOffset(state.getValue(MASTER_OFFSET_X)),
                -layer,
                decodeMasterOffset(state.getValue(MASTER_OFFSET_Z))
        );
    }

    public static int encodeMasterOffset(int offset)
    {
        return switch (offset)
        {
            case -2 -> 3;
            case -1 -> 0;
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 4;
            default -> throw new IllegalArgumentException("Unsupported master offset: " + offset);
        };
    }

    public static int decodeMasterOffset(int encodedOffset)
    {
        return switch (encodedOffset)
        {
            case 3 -> -2;
            case 4 -> 2;
            default -> encodedOffset - 1;
        };
    }

    private static VoxelShape getFootprintShape(BlockState state)
    {
        int layer = state.getValue(LAYER);
        if (layer == 0 && state.getValue(UPPER))
        {
            layer = 1;
        }
        int dxToMaster = decodeMasterOffset(state.getValue(MASTER_OFFSET_X));
        int dzToMaster = decodeMasterOffset(state.getValue(MASTER_OFFSET_Z));

        boolean center = dxToMaster == 0 && dzToMaster == 0;
        int ring = Math.max(Math.abs(dxToMaster), Math.abs(dzToMaster));
        boolean levelFour = ring >= 2 || layer >= 2;

        if (!levelFour)
        {
            return getCompactShape(layer, dxToMaster, dzToMaster, center);
        }
        return getLargeShape(layer, dxToMaster, dzToMaster, center, ring);
    }

    private static VoxelShape getCompactShape(int layer, int dxToMaster, int dzToMaster, boolean center)
    {
        if (layer == 0)
        {
            if (center)
            {
                return EMPTY;
            }
            if (dxToMaster != 0 && dzToMaster != 0)
            {
                return createInwardCornerStep(dxToMaster > 0, dzToMaster > 0, 8.0D, 16.0D);
            }
            return FULL_BLOCK;
        }

        if (center)
        {
            return FULL_BLOCK;
        }
        if (dxToMaster != 0 && dzToMaster != 0)
        {
            return createInwardQuadrant(dxToMaster > 0, dzToMaster > 0, 8.0D);
        }
        return Block.box(0, 0, 0, 16, 8, 16);
    }

    private static VoxelShape getLargeShape(int layer, int dxToMaster, int dzToMaster, boolean center, int ring)
    {
        boolean towardEast = dxToMaster > 0;
        boolean towardSouth = dzToMaster > 0;
        boolean corner = dxToMaster != 0 && dzToMaster != 0;
        boolean outerCorner = Math.abs(dxToMaster) == 2 && Math.abs(dzToMaster) == 2;

        return switch (layer)
        {
            case 0 ->
            {
                if (center)
                {
                    yield EMPTY;
                }
                if (ring == 2)
                {
                    yield outerCorner
                          ? createInwardQuadrant(towardEast, towardSouth, 8.0D)
                          : createInwardStair(dxToMaster, dzToMaster);
                }
                yield corner
                      ? createInwardCornerStep(towardEast, towardSouth, 8.0D, 16.0D)
                      : FULL_BLOCK;
            }
            case 1 ->
            {
                if (ring > 1)
                {
                    yield EMPTY;
                }
                yield FULL_BLOCK;
            }
            case 2, 3 -> EMPTY;
            default -> EMPTY;
        };
    }

    private static VoxelShape createInwardCornerStep(boolean towardEast, boolean towardSouth, double baseHeight, double innerHeight)
    {
        return Shapes.or(
                Block.box(0, 0, 0, 16, baseHeight, 16),
                createInwardQuadrant(towardEast, towardSouth, innerHeight)
        );
    }

    private static VoxelShape createInwardQuadrant(boolean towardEast, boolean towardSouth, double height)
    {
        double minX = towardEast ? 8.0D : 0.0D;
        double maxX = towardEast ? 16.0D : 8.0D;
        double minZ = towardSouth ? 8.0D : 0.0D;
        double maxZ = towardSouth ? 16.0D : 8.0D;
        return Block.box(minX, 0, minZ, maxX, height, maxZ);
    }

    private static VoxelShape createEdgeStrip(int dxToMaster, int dzToMaster, double height)
    {
        if (dxToMaster == 0)
        {
            return dzToMaster > 0
                   ? Block.box(0, 0, 8, 16, height, 16)
                   : Block.box(0, 0, 0, 16, height, 8);
        }

        return dxToMaster > 0
               ? Block.box(8, 0, 0, 16, height, 16)
               : Block.box(0, 0, 0, 8, height, 16);
    }

    private static VoxelShape createInwardStair(int dxToMaster, int dzToMaster)
    {
        VoxelShape base = Block.box(0, 0, 0, 16, 8, 16);
        if (Math.abs(dzToMaster) >= Math.abs(dxToMaster))
        {
            return dzToMaster > 0
                   ? Shapes.or(base, Block.box(0, 8, 8, 16, 16, 16))
                   : Shapes.or(base, Block.box(0, 8, 0, 16, 16, 8));
        }

        return dxToMaster > 0
               ? Shapes.or(base, Block.box(8, 8, 0, 16, 16, 16))
               : Shapes.or(base, Block.box(0, 8, 0, 8, 16, 16));
    }
}
