package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * ADR-059 section 2 and ADR-054 sections 9 and 9.1: the elevator anchor controller or the elevator terminal. Like every
 * endpoint it resists explosions (1,200), cannot be pushed and is in the wither, dragon and block-mover exclusion
 * tags. A player who is not an operator cannot break one that holds cargo state or that a pair names
 * ({@code ENDPOINT_BUSY}); any removal settles its ledger state from its live state and drops its buffers, and a pair
 * that names it stays, invalid, until it is unbound.
 */
public final class ElevatorEndpointBlock extends EndgameDeviceBlock {
    private final boolean anchor;

    public ElevatorEndpointBlock(Properties properties, boolean anchor) {
        super(properties);
        this.anchor = anchor;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
                || !(level.getBlockEntity(position) instanceof ElevatorEndpointBlockEntity endpoint)) {
            return InteractionResult.PASS;
        }
        EndgameCode refusal = EndgameRuntime.devices().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : endpoint.quarantined() ? EndgameCode.DEVICE_QUARANTINED
                : EndgameDeviceMenu.openRefusal((ServerLevel) level, player, endpoint);
        if (refusal != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, refusal);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, endpoint, buffer -> ElevatorMenu.writeOpenData(buffer, endpoint));
        return InteractionResult.CONSUME;
    }

    /** Section 9: a busy endpoint, or one a pair names, is broken only by an operator. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof ElevatorEndpointBlockEntity endpoint
                && !player.hasPermissions(2) && EndgameRuntime.operational().flatMap(EndgameService::root)
                .map(endpoint::busy).orElse(endpoint.busy())) {
            EndgameIntentGuard.statusLine(player, EndgameCode.ENDPOINT_BUSY);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    /** As the railgun: the ledger settles from the live state; a copy at another position settles nothing. */
    @Override
    protected void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device) {
        if (!(device instanceof ElevatorEndpointBlockEntity endpoint)) {
            return;
        }
        List<ItemStack> settled = endpoint.deviceId().isEmpty() ? List.of() : EndgameRuntime.operational()
                .filter(service -> service.endpointStatus(endpoint.endpointId(), level.dimension().location(),
                        position.asLong()) != EndgameCode.ENDPOINT_POSITION_CONFLICT)
                .map(service -> service.transits().settleRemoval(endpoint, level.getGameTime())).orElse(List.of());
        for (ItemStack stack : settled) {
            Containers.dropItemStack(level, position.getX(), position.getY(), position.getZ(), stack);
        }
        endpoint.storage().drop(level, position);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return anchor ? new ElevatorAnchorBlockEntity(position, state) : new ElevatorTerminalBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return anchor ? createTickerHelper(type, ModBlockEntities.ELEVATOR_ANCHOR.get(),
                ElevatorEndpointBlockEntity::serverTick)
                : createTickerHelper(type, ModBlockEntities.ELEVATOR_TERMINAL.get(),
                ElevatorEndpointBlockEntity::serverTick);
    }
}
