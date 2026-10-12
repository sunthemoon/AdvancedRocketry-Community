package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls.Axis;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * {@code station_orientation_controller}: per axis (EAST, UP, NORTH) rate steps of -10, -1, +1 and +10 turns per
 * 72,000 ticks, STOP, and, while stopped, fixed phase steps of 15 degrees. The server samples its own clock; the
 * screen shows whole-degree phases at the server's current logical tick.
 */
public final class OrientationControllerMenu extends StationControllerMenu {
    public static final int[] RATE_STEPS = {-10, -1, 1, 10};
    public static final int BUTTON_STOP = 12;
    /** Buttons 13..18: per axis, the phase down then up by {@link #PHASE_STEP} milli-degrees. */
    public static final int FIRST_PHASE_BUTTON = 13;
    public static final int PHASE_STEP = 15_000;
    /** View values, in the {@code STATION_ORIENTATION_CONTROLLER} layout. */
    public static final int EXHAUSTED = 0;
    public static final int RATES = 1;
    public static final int PHASES = 4;
    static final List<Button> BUTTONS = buttonLayout();

    OrientationControllerMenu(int id, Level level, BlockPos position, Block block, OpenData open,
                              @Nullable ServerPlayer viewer, @Nullable UUID station) {
        super(ModMenuTypes.STATION_ORIENTATION_CONTROLLER.get(), StationControllerKind.ORIENTATION, id, level,
                position, block, open, viewer, station);
    }

    private static List<Button> buttonLayout() {
        List<Button> buttons = new ArrayList<>();
        String[] steps = {"minus_ten", "minus_one", "plus_one", "plus_ten"};
        for (Axis axis : Axis.values()) {
            int y = 20 + axis.ordinal() * 18;
            for (int step = 0; step < steps.length; step++) {
                buttons.add(new Button(axis.ordinal() * 4 + step, KEY + steps[step], 44 + step * 28, y, 26));
            }
            buttons.add(new Button(FIRST_PHASE_BUTTON + axis.ordinal() * 2, KEY + "phase_down", 160, y, 20));
            buttons.add(new Button(FIRST_PHASE_BUTTON + axis.ordinal() * 2 + 1, KEY + "phase_up", 182, y, 20));
        }
        buttons.add(new Button(BUTTON_STOP, KEY + "stop", 8, 74, 194));
        return List.copyOf(buttons);
    }

    @Override
    public List<Button> buttons() {
        return BUTTONS;
    }

    @Override
    public List<Component> lines(ClassicControlViewPacket view) {
        List<Component> lines = new ArrayList<>();
        for (Axis axis : Axis.values()) {
            lines.add(Component.translatable(KEY + "axis", Component.translatable(KEY + "axis." + axis.name()
                    .toLowerCase(Locale.ROOT)), view.value(RATES + axis.ordinal()), view.value(PHASES + axis.ordinal())));
        }
        if (view.value(EXHAUSTED) != 0) {
            lines.add(Component.translatable(StationManagementCode.ORBITAL_CLOCK_EXHAUSTED.translationKey()));
        }
        return lines;
    }

    @Override
    protected List<Integer> viewValues(StationState station) {
        StationOrbitalControls controls = station.orbitalControls();
        StationRegistrySavedData registry = StationRegistrySavedData.get(level().getServer());
        long tick = Math.max(controls.epoch(), registry.orbitalTick());
        List<Integer> values = new ArrayList<>();
        values.add(registry.orbitalClockExhausted() ? 1 : 0);
        for (Axis axis : Axis.values()) {
            values.add(controls.rate(axis));
        }
        for (Axis axis : Axis.values()) {
            values.add(controls.phaseAt(axis, tick) / 1_000);
        }
        return values;
    }

    @Override
    protected StationManagementCode apply(ClassicControlIntentPacket intent, ServerPlayer player,
                                          StationManager stations, StationActorContext context,
                                          Optional<StationState> expected) {
        if (expected.isEmpty()) {
            return StationManagementCode.NOT_IN_STATION;
        }
        int button = intent.action();
        StationOrbitalControls controls = expected.orElseThrow().orbitalControls();
        var service = stations.orbitalControls();
        if (button == BUTTON_STOP) {
            return service.stop(player, context, expected).code();
        }
        if (button >= FIRST_PHASE_BUTTON) {
            Axis axis = Axis.values()[(button - FIRST_PHASE_BUTTON) / 2];
            int delta = (button - FIRST_PHASE_BUTTON) % 2 == 0 ? -PHASE_STEP : PHASE_STEP;
            int[] phases = {controls.eastPhase(), controls.upPhase(), controls.northPhase()};
            phases[axis.ordinal()] = Math.floorMod(phases[axis.ordinal()] + delta, StationOrbitalControls.PHASES_PER_TURN);
            return service.setPhases(player, context, expected, phases[0], phases[1], phases[2]).code();
        }
        Axis axis = Axis.values()[button / 4];
        int[] rates = {controls.eastRate(), controls.upRate(), controls.northRate()};
        rates[axis.ordinal()] = Math.max(-StationOrbitalControls.MAX_RATE, Math.min(StationOrbitalControls.MAX_RATE,
                rates[axis.ordinal()] + RATE_STEPS[button % 4]));
        return service.setRates(player, context, expected, rates[0], rates[1], rates[2]).code();
    }
}
