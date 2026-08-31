package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.NetworkHooks;

public final class ElectrolyzerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ElectrolyzerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(POWERED, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos position,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(position);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(position, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos position,
            Block neighbor,
            BlockPos neighborPosition,
            boolean movedByPiston
    ) {
        if (!level.isClientSide) {
            boolean powered = level.hasNeighborSignal(position);
            if (state.getValue(POWERED) != powered) {
                level.setBlock(position, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof ElectrolyzerBlockEntity electrolyzer)) {
            return InteractionResult.PASS;
        }

        boolean heldFluidContainer = FluidUtil.getFluidHandler(player.getItemInHand(hand)).isPresent();
        if (heldFluidContainer) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (electrolyzer.fillFromPlayer(player, hand, hit.getDirection())) {
                return InteractionResult.CONSUME;
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, electrolyzer, position);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof ElectrolyzerBlockEntity electrolyzer) {
                SimpleContainer drops = new SimpleContainer(ElectrolyzerBlockEntity.SLOT_COUNT);
                electrolyzer.copyInventoryTo(drops);
                Containers.dropContents(level, position, drops);
            }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new ElectrolyzerBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide
                ? null
                : createTickerHelper(type, ModBlockEntities.ELECTROLYZER.get(), ElectrolyzerBlockEntity::serverTick);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, POWERED);
    }
}
