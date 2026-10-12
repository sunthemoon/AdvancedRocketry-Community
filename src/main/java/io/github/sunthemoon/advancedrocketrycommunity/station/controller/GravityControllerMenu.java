package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationGravityService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** {@code station_gravity_controller}: gravity steps of -10, -1, +1 and +10 percent through the gravity service. */
public final class GravityControllerMenu extends StationControllerMenu {
    public static final int[] STEPS = {-10, -1, 1, 10};
    private static final List<Button> BUTTONS = List.of(
            new Button(0, KEY + "minus_ten", 8, 20, 38), new Button(1, KEY + "minus_one", 48, 20, 38),
            new Button(2, KEY + "plus_one", 88, 20, 38), new Button(3, KEY + "plus_ten", 128, 20, 38));

    GravityControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open) {
        super(ModMenuTypes.STATION_GRAVITY_CONTROLLER.get(), StationControllerKind.GRAVITY, id, level, position, block,
                open, 1);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    @Override
    public List<Component> lines() {
        int percent = value(0);
        return List.of(percent < 0 ? Component.translatable(KEY + "no_station")
                : Component.translatable(KEY + "gravity", percent));
    }

    @Override
    protected int serverData(int index, StationState station) {
        return station == null ? -1 : station.environment().gravityMilli() / 10;
    }

    @Override
    protected StationManagementCode apply(int button, ServerPlayer player, StationManager stations,
                                          StationActorContext context, Optional<StationState> expected) {
        if (expected.isEmpty()) {
            return StationManagementCode.NOT_IN_STATION;
        }
        int current = expected.orElseThrow().environment().gravityMilli() / 10;
        int target = Math.max(0, Math.min(StationGravityService.MAX_PERCENT, current + STEPS[button]));
        return stations.setGravity(player, context, expected, target).code();
    }
}
