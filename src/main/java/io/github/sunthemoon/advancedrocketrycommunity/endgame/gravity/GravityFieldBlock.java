package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

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

/** ADR-058 section 2: the area gravity field controller block. It holds no items; its energy is lost on removal. */
public final class GravityFieldBlock extends EndgameDeviceBlock {
    public GravityFieldBlock(Properties properties) {
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
                || !(level.getBlockEntity(position) instanceof GravityFieldBlockEntity device)) {
            return InteractionResult.PASS;
        }
        EndgameCode refusal = EndgameRuntime.devices().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : device.quarantined() ? EndgameCode.DEVICE_QUARANTINED
                : EndgameDeviceMenu.viewAuthority((ServerLevel) level, player, device).refusal();
        if (refusal != EndgameCode.OK) {
            EndgameIntentGuard.statusLine(player, refusal);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen(serverPlayer, device, buffer -> GravityFieldMenu.writeOpenData(buffer, device));
        return InteractionResult.CONSUME;
    }

    @Override
    protected void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device) {
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new GravityFieldBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.GRAVITY_FIELD_CONTROLLER.get(), level.isClientSide
                ? GravityFieldBlockEntity::clientTick : GravityFieldBlockEntity::serverTick);
    }
}
