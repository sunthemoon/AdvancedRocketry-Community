package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationLocalActor;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/**
 * ADR-065 section 5.1: a station controller. It stores nothing and has no BlockEntity; its screen is a front end
 * to the checked station services. Only the owner, a member or an operator of the station whose region holds the
 * block may open it, and opening alone changes nothing.
 */
public final class StationControllerBlock extends Block {
    private final StationControllerKind kind;

    public StationControllerBlock(StationControllerKind kind, Properties properties) {
        super(properties);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public StationControllerKind kind() {
        return kind;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) {
            return InteractionResult.PASS;
        }
        Optional<StationState> station = StationLocalActor.visibleStationAt(new StationAccessService(), serverPlayer,
                position);
        if (station.isEmpty()) {
            boolean inRegion = serverPlayer.getServer() != null && StationRegistrySavedData.get(serverPlayer.getServer())
                    .findAt(position.getX(), position.getZ()).isPresent();
            StationManagementCode code = inRegion ? StationManagementCode.UNAUTHORIZED
                    : StationManagementCode.NOT_IN_STATION;
            serverPlayer.displayClientMessage(Component.translatable(code.translationKey()), true);
            return InteractionResult.CONSUME;
        }
        StationControllerMenu.OpenData open = StationControllerMenu.openData(kind, serverPlayer, position,
                station.orElseThrow());
        UUID stationId = station.orElseThrow().stationId();
        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, ignored) ->
                        StationControllerMenu.server(kind, id, inventory, level, position, this, open, stationId),
                Component.translatable(getDescriptionId())), open::write);
        return InteractionResult.CONSUME;
    }
}
