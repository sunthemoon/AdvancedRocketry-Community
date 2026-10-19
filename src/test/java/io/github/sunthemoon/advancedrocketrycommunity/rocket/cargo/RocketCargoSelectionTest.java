package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketCargoSelectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void compatibleNonemptyTankPrecedesEarlierEmptyAndPartialRoomIsExact() {
        var before = snapshot(FluidStack.EMPTY, new FluidStack(Fluids.WATER, 63_251));
        var result = RocketCargoSelection.select(before, new FluidStack(Fluids.WATER, 1000), true, 64_000, d -> ELIGIBLE);
        assertEquals(ELIGIBLE, result.status()); assertEquals(1, result.delta().cursor().x());
        assertEquals(749, result.delta().fluid().getInt("Amount"));
        var empty = RocketCargoSelection.select(before, new FluidStack(Fluids.LAVA, 1000), true, 64_000, d -> ELIGIBLE);
        assertEquals(0, empty.delta().cursor().x());
    }
    @Test void unloaderUsesCanonicalAcceptedNonemptyTankAndDeviceRoom() {
        var before = snapshot(new FluidStack(Fluids.LAVA, 1000), new FluidStack(Fluids.WATER, 1000), new FluidStack(Fluids.WATER, 800));
        var result = RocketCargoSelection.select(before, new FluidStack(Fluids.WATER, 15_999), false, 64_000, d -> ELIGIBLE);
        assertEquals(1, result.delta().cursor().x()); assertEquals(1, result.delta().fluid().getInt("Amount"));
    }
    @Test void refusalRunsBeforeSelectionAndDoesNotConsumeEitherEndpoint() {
        var before = snapshot(new FluidStack(Fluids.WATER, 2), new FluidStack(Fluids.WATER, 2));
        var device = new FluidStack(Fluids.WATER, 1000); var calls = new java.util.ArrayList<Integer>();
        var result = RocketCargoSelection.select(before, device, true, 64_000, d -> {
            calls.add(d.cursor().x()); return d.cursor().x() == 0 ? CARGO_TOO_LARGE : ELIGIBLE;
        });
        assertEquals(java.util.List.of(0, 1), calls); assertEquals(1, result.delta().cursor().x());
        assertEquals(1000, device.getAmount()); assertEquals(2, tank(before, 0).getAmount());
        assertEquals(CARGO_TOO_LARGE, RocketCargoSelection.select(before, device, true, 64_000, d -> CARGO_TOO_LARGE).status());
    }
    @Test void overCapacityTankAndIncompatibleUnloaderAreNotSelected() {
        var before = snapshot(new FluidStack(Fluids.WATER, 64_001));
        assertEquals(NO_CARGO_STORAGE, RocketCargoSelection.select(before, new FluidStack(Fluids.WATER, 1), true, 64_000, d -> ELIGIBLE).status());
        assertEquals(NO_CARGO_STORAGE, RocketCargoSelection.select(before, new FluidStack(Fluids.LAVA, 1), false, 64_000, d -> ELIGIBLE).status());
    }
}
