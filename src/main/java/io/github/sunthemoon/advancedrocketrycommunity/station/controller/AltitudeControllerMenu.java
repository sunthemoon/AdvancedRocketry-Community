package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * {@code station_altitude_controller}: logical orbital distance steps of -10, -1, +1 and +10 inside 4..190, and the
 * middle of the legacy GEO gate (179). Only the distance changes; the station, its region and its orbit stay.
 */
public final class AltitudeControllerMenu extends StationControllerMenu {
    public static final int[] STEPS = {-10, -1, 1, 10};
    public static final int BUTTON_GEO = 4;
    public static final int GEO_DISTANCE = 179;
    private static final List<Button> BUTTONS = List.of(
            new Button(0, KEY + "minus_ten", 8, 20, 38), new Button(1, KEY + "minus_one", 48, 20, 38),
            new Button(2, KEY + "plus_one", 88, 20, 38), new Button(3, KEY + "plus_ten", 128, 20, 38),
            new Button(BUTTON_GEO, KEY + "geo", 8, 38, 158));

    AltitudeControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open) {
        super(ModMenuTypes.STATION_ALTITUDE_CONTROLLER.get(), StationControllerKind.ALTITUDE, id, level, position,
                block, open, 1);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    @Override
    public List<Component> lines() {
        int distance = value(0);
        if (!StationOrbitalControls.validDistance(distance)) {
            return List.of(Component.translatable(KEY + "no_station"));
        }
        long kilometres = (long) distance * StationOrbitalControls.KILOMETRES_PER_DISTANCE
                + StationOrbitalControls.KILOMETRES_OFFSET;
        boolean geo = distance >= StationOrbitalControls.GEO_MIN_DISTANCE
                && distance <= StationOrbitalControls.GEO_MAX_DISTANCE;
        return List.of(Component.translatable(KEY + "distance", distance),
                Component.translatable(KEY + "altitude", kilometres),
                Component.translatable(geo ? KEY + "geo_yes" : KEY + "geo_no"));
    }

    @Override
    protected int serverData(int index, StationState station) {
        return station == null ? -1 : station.orbitalControls().distance();
    }

    @Override
    protected StationManagementCode apply(int button, ServerPlayer player, StationManager stations,
                                          StationActorContext context, Optional<StationState> expected) {
        if (expected.isEmpty()) {
            return StationManagementCode.NOT_IN_STATION;
        }
        int current = expected.orElseThrow().orbitalControls().distance();
        int target = button == BUTTON_GEO ? GEO_DISTANCE : Math.max(StationOrbitalControls.MIN_DISTANCE,
                Math.min(StationOrbitalControls.MAX_DISTANCE, current + STEPS[button]));
        return stations.orbitalControls().setAltitude(player, context, expected, target).code();
    }
}
