package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ADR-059 section 2: the elevator terminal, a single {@code elevator_terminal} endpoint block inside a committed station
 * region and not within 2 blocks horizontally of the landing-pad column ({@code TERMINAL_ON_PAD}), so landing stays
 * unobstructed. Outside a region it is {@code TERMINAL_UNAVAILABLE}; neither state loses anything.
 */
public final class ElevatorTerminalBlockEntity extends ElevatorEndpointBlockEntity {
    public static final ResourceLocation KIND = ModIdentity.id("elevator_terminal");

    private static final int PLACEMENT_CHECK_TICKS = 20;

    private EndgameCode placement = EndgameCode.TERMINAL_UNAVAILABLE;
    private long nextPlacementCheck;
    private Optional<UUID> station = Optional.empty();

    public ElevatorTerminalBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ELEVATOR_TERMINAL.get(), position, state);
    }

    @Override
    public ResourceLocation kind() {
        return KIND;
    }

    @Override
    public boolean anchor() {
        return false;
    }

    @Override
    protected boolean placementReady(ServerLevel level, BlockState state, UUID id, EndgameDevices devices, long now) {
        // C13: the tick looks the station up every 20 ticks; bind and launch look it up again at use (C13R-F9).
        if (now < nextPlacementCheck) {
            return true;
        }
        nextPlacementCheck = now + PLACEMENT_CHECK_TICKS;
        derivePlacement(level);
        return true;
    }

    @Override
    public void refreshPlacement() {
        if (level instanceof ServerLevel server) {
            derivePlacement(server);
        }
    }

    private void derivePlacement(ServerLevel level) {
        Optional<StationState> found = EndgameStations.at(level, worldPosition).station();
        station = found.map(StationState::stationId);
        placement = found.isEmpty() ? EndgameCode.TERMINAL_UNAVAILABLE
                : ElevatorTerminalBlockEntity.onPad(found.get(), worldPosition) ? EndgameCode.TERMINAL_ON_PAD
                : EndgameCode.OK;
    }

    /** Within 2 blocks horizontally of the station's landing-pad column. */
    public static boolean onPad(StationState station, BlockPos position) {
        return Math.abs(position.getX() - station.landingPad().x()) <= ElevatorRules.TERMINAL_PAD_CLEARANCE
                && Math.abs(position.getZ() - station.landingPad().z()) <= ElevatorRules.TERMINAL_PAD_CLEARANCE;
    }

    @Override
    public EndgameCode placement() {
        return placement;
    }

    /** The committed station whose region holds the terminal, as the last lookup found it. */
    public Optional<UUID> station() {
        return station;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.elevator_terminal");
    }
}
