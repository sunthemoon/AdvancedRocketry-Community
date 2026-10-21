package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowTurnYieldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void unloadedCargoAttemptYieldsItsWaitingToken() {
        Checks.unloadedCargoAttemptYieldsItsWaitingToken(temp);
    }
    @Test void refusedFuelAttemptAndQueueVisitYieldTheirWaitingToken() {
        Checks.refusedFuelAttemptAndQueueVisitYieldTheirWaitingToken(temp);
    }
    @Test void noWorkRefusalCannotReleaseAnActiveClaimOrAnotherDevicesNextToken() {
        Checks.noWorkRefusalCannotReleaseAnActiveClaimOrAnotherDevicesNextToken(temp);
    }

    private static final class Checks {
    static void unloadedCargoAttemptYieldsItsWaitingToken(Path temp) {
        try (var fixture = new Turns(temp)) {
            fixture.first.blocked();
            var device = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "turns", fixture.first);
            var admission = new EscrowFluidCargoAdmission(device);
            assertEquals(RocketCargoStatus.WAITING_UNLOADED, admission.attempt());
            assertTrue(fixture.second.claim());
            fixture.unchanged();
        }
    }

    static void refusedFuelAttemptAndQueueVisitYieldTheirWaitingToken(Path temp) {
        try (var fixture = new Turns(temp)) {
            var device = UnstartedServer.allocate(FluidFuelLoaderBlockEntity.class);
            UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "turns", fixture.first);
            UnstartedServer.set(FluidFuelLoaderBlockEntity.class, device, "status", LoaderRoot.Status.INVALID);
            fixture.first.blocked();
            assertEquals(FluidFuelLoaderStatus.INVALID_DATA, device.attempt());
            assertTrue(fixture.second.claim()); fixture.second.release();
            fixture.first.blocked();
            assertFalse(device.admitFromQueue());
            assertEquals(FluidFuelLoaderStatus.INVALID_DATA, device.lastAttempt());
            assertTrue(fixture.second.claim());
            fixture.unchanged();
        }
    }

    static void noWorkRefusalCannotReleaseAnActiveClaimOrAnotherDevicesNextToken(Path temp) {
        try (var fixture = new Turns(temp)) {
            var device = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "turns", fixture.first);
            var admission = new EscrowFluidCargoAdmission(device);
            assertTrue(fixture.first.claim()); fixture.second.blocked();
            assertEquals(RocketCargoStatus.WAITING_UNLOADED, admission.attempt());
            assertTrue(fixture.first.allows()); assertFalse(fixture.second.allows());
            fixture.first.release();
            assertTrue(fixture.second.allows()); assertFalse(fixture.first.allows());
            fixture.unchanged();
        }
    }

    }

    private static final class Turns implements AutoCloseable {
        final ResourceEscrowSavedData data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID())));
        final EscrowLedger before = data.ledger();
        final EscrowLedgerWriter writer;
        final EscrowCoordinator coordinator;
        final EscrowDeviceTurns first = new EscrowDeviceTurns(), second = new EscrowDeviceTurns();
        Turns(Path directory) {
            writer = new EscrowLedgerWriter(directory.resolve("ledger"), 0);
            coordinator = new EscrowCoordinator(data, writer);
            var rocket = UUID.randomUUID();
            assertTrue(first.bind(coordinator, UUID.randomUUID(), rocket));
            assertTrue(second.bind(coordinator, UUID.randomUUID(), rocket));
        }
        void unchanged() {
            assertSame(before, data.ledger()); assertFalse(data.isDirty());
            assertEquals(1, coordinator.nextSequence()); assertTrue(coordinator.pendingAdmissions().isEmpty());
            assertTrue(writer.idle());
        }
        @Override public void close() { first.close(); second.close(); coordinator.close(); writer.close(); }
    }
}
