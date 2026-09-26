package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** Physical Precision Assembler controller with a server-validated menu entry point. */
public final class PrecisionAssemblerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PrecisionAssemblerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (!level.hasChunkAt(position)
                || !(level.getBlockEntity(position) instanceof PrecisionAssemblerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && PrecisionAssemblerMenu.canOpen(controller, serverPlayer)) {
            NetworkHooks.openScreen(serverPlayer, controller, position);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        if (level instanceof ServerLevel serverLevel) {
            PrecisionAssemblerRuntime.markDirty(serverLevel, position);
        }
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos position,
            BlockState newState,
            boolean moved
    ) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())
                && level.getBlockEntity(position) instanceof PrecisionAssemblerBlockEntity controller) {
            if (!moved) {
                controller.dropAllItemsForRemoval();
            }
            PrecisionAssemblerRuntime.removeController(serverLevel, controller);
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos position, Explosion explosion) {
        return !PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)
                && super.canDropFromExplosion(state, level, position, explosion);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)) {
            super.onBlockExploded(state, level, position, explosion);
        }
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !PrecisionAssemblerRemovalPolicy.blocksRemoval(level, position)
                && super.canEntityDestroy(state, level, position, entity);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new PrecisionAssemblerBlockEntity(position, state);
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
        builder.add(FACING);
    }
}
