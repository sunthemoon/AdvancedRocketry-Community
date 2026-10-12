package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-065 section 11.2: each controller's buttons are exactly its {@code classic_controls} kind's actions. */
final class StationControllerKindTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyControllerUsesItsOwnKindAndEveryActionIsOneButton() {
        Map<StationControllerKind, List<StationControllerMenu.Button>> layouts = Map.of(
                StationControllerKind.WARP, WarpControllerMenu.BUTTONS,
                StationControllerKind.GRAVITY, GravityControllerMenu.BUTTONS,
                StationControllerKind.ALTITUDE, AltitudeControllerMenu.BUTTONS,
                StationControllerKind.ORIENTATION, OrientationControllerMenu.BUTTONS);
        EnumSet<ClassicControlKind> used = EnumSet.noneOf(ClassicControlKind.class);
        for (StationControllerKind kind : StationControllerKind.values()) {
            ClassicControlKind wire = kind.wire();
            assertTrue(used.add(wire), "Two controllers share " + wire);
            assertEquals(IntStream.range(0, wire.actions()).boxed().toList(),
                    layouts.get(kind).stream().map(StationControllerMenu.Button::id).sorted().toList(), kind.id());
        }
        assertEquals(EnumSet.allOf(ClassicControlKind.class), used);
        assertEquals(WarpControllerMenu.BUTTON_REQUEST, ClassicControlKind.WARP_CONTROLLER.valueAction());
        assertEquals(WarpControllerMenu.BUTTON_CONFIRM, ClassicControlKind.WARP_CONTROLLER.tokenAction());
        assertEquals(StationControllerMenu.MAX_NAMES - 2, ClassicControlKind.WARP_CONTROLLER.valueMaximum());
        assertEquals(StationControllerMenu.MAX_NAMES - 1,
                ClassicControlKind.WARP_CONTROLLER.maximum(WarpControllerMenu.ORBIT));
    }
}
