package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowItemAdmissionTurnsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void itemAdmissionCancelsBothAnUnusedHintAndAnActiveClaim(@TempDir Path temp) throws Exception {
        // Reflection keeps the regression runnable before the item admission adapter exists.
        Class<?> deviceType = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity");
        Class<?> admissionType = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoAdmission");
        assertNotNull(deviceType.getMethod("attempt"));
        for (boolean claimed : new boolean[] {false, true}) {
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID())));
            try (var writer = new EscrowLedgerWriter(temp.resolve("case-" + claimed), 0);
                    var coordinator = new EscrowCoordinator(data, writer);
                    var first = new EscrowDeviceTurns(); var second = new EscrowDeviceTurns()) {
                var logical = UUID.randomUUID();
                assertTrue(first.bind(coordinator, UUID.randomUUID(), logical));
                assertTrue(second.bind(coordinator, UUID.randomUUID(), logical));
                if (claimed) { assertTrue(first.claim()); second.blocked(); }
                else { first.blocked(); }
                assertFalse(second.allows());
                Object device = UnstartedServer.allocate(deviceType);
                UnstartedServer.set(deviceType, device, "turns", first);
                var admission = (AutoCloseable) admissionType.getConstructor(deviceType).newInstance(device);
                if (claimed) { UnstartedServer.set(admissionType, admission, "turnClaimed", true); }
                admission.close();
                assertTrue(second.allows(), "Cancellation stranded the next item/fuel/cargo device");
                admission.close();
                assertTrue(second.allows(), "A repeated cancellation stole another device's turn");
                assertTrue(data.ledger().records().isEmpty()); assertEquals(1, coordinator.nextSequence());
                assertFalse(data.isDirty()); assertTrue(writer.idle());
            }
        }
    }
}
