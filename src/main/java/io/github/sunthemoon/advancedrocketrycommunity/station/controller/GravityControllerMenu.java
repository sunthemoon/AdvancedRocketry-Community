package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationGravityService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** {@code station_gravity_controller}: gravity steps of -10, -1, +1 and +10 percent through the gravity service. */
public final class GravityControllerMenu extends StationControllerMenu {
    public static final int[] STEPS = {-10, -1, 1, 10};
    static final List<Button> BUTTONS = List.of(
            new Button(0, KEY + "minus_ten", 8, 20, 38), new Button(1, KEY + "minus_one", 48, 20, 38),
            new Button(2, KEY + "plus_one", 88, 20, 38), new Button(3, KEY + "plus_ten", 128, 20, 38));

    GravityControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open,
                          @Nullable ServerPlayer viewer) {
        super(ModMenuTypes.STATION_GRAVITY_CONTROLLER.get(), StationControllerKind.GRAVITY, id, level, position, block,
                open, viewer);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    @Override
    public List<Component> lines(ClassicControlViewPacket view) {
        return List.of(Component.translatable(KEY + "gravity", view.value(0)));
    }

    @Override
    protected List<Integer> viewValues(StationState station) {
        return List.of(station.environment().gravityMilli() / 10);
    }

    @Override
    protected StationManagementCode apply(ClassicControlIntentPacket intent, ServerPlayer player,
                                          StationManager stations, StationActorContext context,
                                          Optional<StationState> expected) {
        if (expected.isEmpty()) {
            return StationManagementCode.NOT_IN_STATION;
        }
        int current = expected.orElseThrow().environment().gravityMilli() / 10;
        int target = Math.max(0, Math.min(StationGravityService.MAX_PERCENT, current + STEPS[intent.action()]));
        return stations.setGravity(player, context, expected, target).code();
    }
}
