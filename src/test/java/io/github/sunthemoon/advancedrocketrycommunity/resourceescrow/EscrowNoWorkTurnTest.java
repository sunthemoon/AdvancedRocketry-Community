package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EscrowNoWorkTurnTest {
    @BeforeAll static void bootstrap() throws Exception {
        MinecraftBootstrap.initialize(); RocketEscrowAuditDirectionTest.bootstrap(); EscrowTurnYieldBootstrap.initialize();
    }
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings = {"empty", "full", "disabled", "binding"})
    void waitingCargoNoLongerEligibleYieldsToEligibleDevice(String change) throws Exception {
        Checks.waitingCargoNoLongerEligibleYieldsToEligibleDevice(temp, change);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emptyFuelLoaderReceivesBusyTokenThenYieldsOnEmpty(boolean queueVisit) throws Exception {
        Checks.emptyFuelLoaderReceivesBusyTokenThenYieldsOnEmpty(temp, queueVisit);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closedFuelSwitchYieldsBusyTokenWithoutBypassingOccupancy(boolean queueVisit) throws Exception {
        Checks.closedFuelSwitchYieldsBusyTokenWithoutBypassingOccupancy(temp, queueVisit);
    }
    @Test void cancellingQueuedCargoBeforeTicketHandsTurnToEligibleDevice() throws Exception {
        Checks.cancellingQueuedCargoBeforeTicketHandsTurnToEligibleDevice(temp);
    }

    // Defer runtime type resolution until the capability bootstrap has defined it, as in the audit fixtures.
    private static final class Checks {
    static void waitingCargoNoLongerEligibleYieldsToEligibleDevice(Path temp, String change) throws Exception {
        try (var f = new EscrowTurnYieldFixture(temp)) {
            var role = change.equals("full") ? RocketCargoRole.UNLOAD : RocketCargoRole.LOAD;
            var first = f.cargo(role, 1000); var second = f.cargo(RocketCargoRole.LOAD, 1000);
            var secondRoot = second.root().orElseThrow();
            assertTrue(f.active.claim());
            assertEquals(RocketCargoStatus.OTHER_TRANSFER_PENDING, first.attempt());
            assertEquals(RocketCargoStatus.OTHER_TRANSFER_PENDING, second.attempt());
            f.active.release();
            assertTrue(first.transferTurn()); assertFalse(second.transferTurn());
            RocketCargoStatus expected;
            switch (change) {
                case "empty" -> { f.tank(first, 0); expected = RocketCargoStatus.DEVICE_EMPTY; }
                case "full" -> { f.tank(first, 16000); expected = RocketCargoStatus.DEVICE_FULL; }
                case "disabled" -> {
                    EscrowCargoSwitches.forTests(f.server, EscrowOperation.Kind.FLUID_CARGO_LOAD, first.root().orElseThrow().device(), false);
                    expected = RocketCargoStatus.ADMISSION_DISABLED;
                }
                default -> {
                    var root = first.root().orElseThrow();
                    var binding = f.binding();
                    UnstartedServer.set(RocketFluidCargoBlockEntity.class, first, "root", root.withBinding(new LoaderBinding(
                            binding.logicalRocket(), binding.level(), java.util.UUID.randomUUID(), binding.locator(), null)));
                    expected = RocketCargoStatus.STALE_BINDING;
                }
            }
            var firstRoot = first.root().orElseThrow();
            assertEquals(expected, first.attempt());
            assertSame(firstRoot, first.root().orElseThrow()); assertSame(secondRoot, second.root().orElseThrow());
            assertEquals(0, f.runtime.fills(f.server).size()); f.unchanged();
            assertEquals(RocketCargoStatus.READY, second.attempt());
            assertTrue(second.claimTransferTurn()); assertFalse(first.transferTurn());
        }
    }

    static void emptyFuelLoaderReceivesBusyTokenThenYieldsOnEmpty(Path temp, boolean queueVisit) throws Exception {
        try (var f = new EscrowTurnYieldFixture(temp)) {
            var first = f.fuel(0); var second = f.cargo(RocketCargoRole.LOAD, 1000);
            var root = first.root().orElseThrow();
            f.occupied(true);
            if (queueVisit) {
                assertFalse(first.admitFromQueue()); assertEquals(FluidFuelLoaderStatus.ROCKET_BUSY, first.lastAttempt());
            } else { assertEquals(FluidFuelLoaderStatus.ROCKET_BUSY, first.attempt()); }
            assertFalse(second.transferTurn());
            f.occupied(false);
            if (queueVisit) {
                assertFalse(first.admitFromQueue()); assertEquals(FluidFuelLoaderStatus.EMPTY, first.lastAttempt());
            } else { assertEquals(FluidFuelLoaderStatus.EMPTY, first.attempt()); }
            assertSame(root, first.root().orElseThrow()); f.unchanged();
            assertEquals(RocketCargoStatus.READY, second.attempt()); assertTrue(second.claimTransferTurn());
        }
    }

    static void closedFuelSwitchYieldsBusyTokenWithoutBypassingOccupancy(Path temp, boolean queueVisit) throws Exception {
        try (var f = new EscrowTurnYieldFixture(temp)) {
            var first = f.fuel(0); var second = f.cargo(RocketCargoRole.LOAD, 1000);
            f.occupied(true);
            assertEquals(FluidFuelLoaderStatus.ROCKET_BUSY, first.attempt()); assertFalse(second.transferTurn());
            f.runtime.loaderAdmissionForTests(f.server, first.root().orElseThrow().device(), false);
            if (queueVisit) {
                assertFalse(first.admitFromQueue()); assertEquals(FluidFuelLoaderStatus.ROCKET_BUSY, first.lastAttempt());
            } else { assertEquals(FluidFuelLoaderStatus.ROCKET_BUSY, first.attempt()); }
            assertTrue(second.transferTurn());
            assertEquals(RocketCargoStatus.ROCKET_BUSY, second.attempt());
            f.occupied(false);
            assertEquals(RocketCargoStatus.READY, second.attempt()); assertTrue(second.claimTransferTurn()); f.unchanged();
        }
    }

    static void cancellingQueuedCargoBeforeTicketHandsTurnToEligibleDevice(Path temp) throws Exception {
        try (var f = new EscrowTurnYieldFixture(temp)) {
            var first = f.cargo(RocketCargoRole.LOAD, 1000); var second = f.cargo(RocketCargoRole.LOAD, 1000);
            assertTrue(f.active.claim()); assertEquals(RocketCargoStatus.OTHER_TRANSFER_PENDING, first.attempt());
            f.active.release(); assertEquals(RocketCargoStatus.READY, first.attempt());
            assertFalse(second.transferTurn()); assertEquals(1, f.runtime.fills(f.server).size());
            f.runtime.fills(f.server).cancel(first.root().orElseThrow().device());
            assertEquals(0, f.runtime.fills(f.server).size()); f.unchanged();
            assertEquals(RocketCargoStatus.READY, second.attempt()); assertTrue(second.claimTransferTurn());
        }
    }
    }
}
