package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Permanent W117-R1 review probe. No server, world, gate, or durable operation is fabricated. */
class W117TurnCancellationProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void cancellingCargoBeforeItClaimsItsTurnDoesNotStrandTheOtherLoadedDevice() {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID())));
        var rocket = UUID.randomUUID(); var firstId = UUID.randomUUID(); var secondId = UUID.randomUUID();
        try (var writer = new EscrowLedgerWriter(temp.resolve("idle"), 0);
                var coordinator = new EscrowCoordinator(data, writer);
                var first = new EscrowDeviceTurns(); var second = new EscrowDeviceTurns()) {
            assertTrue(first.bind(coordinator, firstId, rocket));
            assertTrue(second.bind(coordinator, secondId, rocket));
            first.blocked();
            assertTrue(first.allows()); assertFalse(second.allows());
            var device = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "turns", first);
            var admission = new EscrowFluidCargoAdmission(device);
            assertFalse(admission.active());
            // Calls production close/discard with its real, loaded registration and no claimed ticket.
            admission.close();
            assertTrue(second.allows(), "A cancelled pre-ticket cargo attempt retained the rocket's next turn");
        }
    }

    @Test void cancellingAnActiveCargoClaimDoesHandTheTurnToTheOtherLoadedDevice() {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID())));
        var rocket = UUID.randomUUID(); var firstId = UUID.randomUUID(); var secondId = UUID.randomUUID();
        try (var writer = new EscrowLedgerWriter(temp.resolve("active"), 0);
                var coordinator = new EscrowCoordinator(data, writer);
                var first = new EscrowDeviceTurns(); var second = new EscrowDeviceTurns()) {
            assertTrue(first.bind(coordinator, firstId, rocket));
            assertTrue(second.bind(coordinator, secondId, rocket));
            assertTrue(first.claim()); second.blocked();
            assertFalse(second.allows());
            var device = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, device, "turns", first);
            var admission = new EscrowFluidCargoAdmission(device);
            UnstartedServer.set(EscrowFluidCargoAdmission.class, admission, "turnClaimed", true);
            admission.close();
            assertTrue(second.allows()); assertFalse(first.allows());
        }
    }
}
