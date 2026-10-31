package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6RecoveryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactPrefixesAreCheckedAgainstTheirOwnRecord() {
        var op = EscrowTestFixtures.operation(1); var ledger = EscrowTestFixtures.ledger(op).id();
        for (var endpoint : List.of(op.source(), op.destination())) {
            String role = endpoint == op.source() ? "SOURCE" : "DESTINATION";
            assertFalse(EscrowLoaderS6Recovery.after(ledger, op, endpoint, role, null, endpoint.beforeEpoch(),
                    endpoint.beforeSignature(), List.of()));
            assertTrue(EscrowLoaderS6Recovery.after(ledger, op, endpoint, role, ledger, endpoint.afterEpoch(),
                    endpoint.afterSignature(), List.of(new EscrowPrefix.Marker(op.id(), 1, role, endpoint.afterEpoch()))));
        }
    }

    @Test void changedMarkerSignatureEpochOrLedgerCannotBeAnAfterPrefix() {
        var op = EscrowTestFixtures.operation(1); var ledger = EscrowTestFixtures.ledger(op).id();
        var endpoint = op.destination();
        var marker = new EscrowPrefix.Marker(op.id(), 1, "DESTINATION", 1);
        for (var markers : List.of(List.<EscrowPrefix.Marker>of(),
                List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(98), 1, "DESTINATION", 1)),
                List.of(new EscrowPrefix.Marker(op.id(), 2, "DESTINATION", 1)),
                List.of(new EscrowPrefix.Marker(op.id(), 1, "SOURCE", 1)), List.of(marker, marker))) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Recovery.after(ledger, op, endpoint,
                    "DESTINATION", ledger, 1, endpoint.afterSignature(), markers));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Recovery.after(ledger, op, endpoint,
                "DESTINATION", ledger, 1, endpoint.beforeSignature(), List.of(marker)));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Recovery.after(ledger, op, endpoint,
                "DESTINATION", ledger, 2, endpoint.afterSignature(), List.of(marker)));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Recovery.after(ledger, op, endpoint,
                "DESTINATION", EscrowTestFixtures.id(98), 1, endpoint.afterSignature(), List.of(marker)));
    }
}
