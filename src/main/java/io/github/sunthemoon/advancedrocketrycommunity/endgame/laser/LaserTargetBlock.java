package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * ADR-055 section 3 and ADR-054 section 9.1: the laser target block. Placing it goes through vanilla placement, so
 * claims and station protection apply. Like every endpoint it resists explosions (1,200), cannot be pushed and is in
 * the wither, dragon and block-mover exclusion tags; any removal drops its buffer and retires its ID.
 */
public final class LaserTargetBlock extends EndgameDeviceBlock {
    public LaserTargetBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer
                || !(level.getBlockEntity(position) instanceof LaserTargetBlockEntity target)) {
            return InteractionResult.PASS;
        }
        EndgameCode refusal = EndgameRuntime.devices().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : target.quarantined() ? EndgameCode.DEVICE_QUARANTINED
                : EndgameDeviceMenu.openRefusal((ServerLevel) level, player, target);
        if (refusal != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, refusal);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, target, buffer -> LaserTargetMenu.writeOpenData(buffer, target));
        return InteractionResult.CONSUME;
    }

    /** Removal by any cause with live state retires the ID (ADR-054 section 9.1); the buffer drops. */
    @Override
    protected void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device) {
        if (device instanceof LaserTargetBlockEntity target) {
            target.dropBuffer(level, position);
            target.deviceId().ifPresent(id -> EndgameRuntime.operational()
                    .ifPresent(service -> service.endpointRemoved(id, level.getGameTime())));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new LaserTargetBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.LASER_TARGET.get(), level.isClientSide
                ? LaserTargetBlockEntity::clientTick : LaserTargetBlockEntity::serverTick);
    }
}
