package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * ADR-056 section 2 and ADR-054 sections 9 and 9.1: the railgun controller; the structure extends behind its front
 * face. Like every endpoint it resists explosions (1,200), cannot be pushed and is in the wither, dragon and
 * block-mover exclusion tags. A player who is not an operator cannot break it while it holds an outbox entry, an
 * incoming payload or a receipt ({@code ENDPOINT_BUSY}); any removal settles its ledger state from its live state (a
 * retired one is resolved first) and drops its buffers.
 */
public final class RailgunBlock extends EndgameDeviceBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RailgunBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
                || !(level.getBlockEntity(position) instanceof RailgunBlockEntity railgun)) {
            return InteractionResult.PASS;
        }
        EndgameCode refusal = EndgameRuntime.devices().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : railgun.quarantined() ? EndgameCode.DEVICE_QUARANTINED
                : EndgameDeviceMenu.openRefusal((ServerLevel) level, player, railgun);
        if (refusal != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, refusal);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, railgun, buffer -> RailgunMenu.writeOpenData(buffer, railgun));
        return InteractionResult.CONSUME;
    }

    /** Section 9: a busy endpoint is broken only by an operator. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof RailgunBlockEntity railgun
                && railgun.busy() && !player.hasPermissions(2)) {
            EndgameIntentGuard.statusLine(player, EndgameCode.ENDPOINT_BUSY);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    /**
     * Section 9.1: the ledger settles the removal from the live state and retires the ID; the items it hands back
     * (payloads that are the railgun's own, or a retired railgun's resolved contents) drop with the buffers. A copy
     * whose registered ID stands at another position settles nothing, so it cannot retire the registered railgun.
     */
    @Override
    protected void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device) {
        if (!(device instanceof RailgunBlockEntity railgun)) {
            return;
        }
        List<ItemStack> settled = railgun.deviceId().isEmpty() ? List.of() : EndgameRuntime.operational()
                .filter(service -> service.endpointStatus(railgun.endpointId(), level.dimension().location(),
                        position.asLong()) != EndgameCode.ENDPOINT_POSITION_CONFLICT)
                .map(service -> service.transits().settleRemoval(railgun, level.getGameTime())).orElse(List.of());
        for (ItemStack stack : settled) {
            Containers.dropItemStack(level, position.getX(), position.getY(), position.getZ(), stack);
        }
        railgun.storage().drop(level, position);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new RailgunBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.RAILGUN.get(), level.isClientSide
                ? RailgunBlockEntity::clientTick : RailgunBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
