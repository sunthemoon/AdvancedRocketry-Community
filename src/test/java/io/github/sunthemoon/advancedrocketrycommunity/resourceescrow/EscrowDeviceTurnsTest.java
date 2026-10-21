package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowDeviceTurnsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;
    @Test void loadRebindUnloadAndCoordinatorEndDiscardOnlyTheirOwnTurns() {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger()));
        try (var writer = new EscrowLedgerWriter(temp.resolve("turns"), 0);
                var coordinator = new EscrowCoordinator(data, writer);
                var first = new EscrowDeviceTurns(); var second = new EscrowDeviceTurns()) {
            assertTrue(first.bind(null, id(1), id(99))); first.blocked(); assertTrue(first.allows());
            assertTrue(first.bind(coordinator, id(1), id(99))); assertTrue(second.bind(coordinator, id(2), id(99)));
            first.blocked(); assertFalse(second.allows()); assertTrue(first.allows());
            assertTrue(first.bind(coordinator, id(1), id(99))); assertFalse(second.allows());
            assertTrue(first.bind(coordinator, id(1), id(100))); assertTrue(second.allows());
            second.blocked(); first.close(); assertEquals(1, coordinator.transferTurns().size());
            second.close(); assertEquals(0, coordinator.transferTurns().size());
            second.bind(coordinator, id(2), id(99)); second.blocked(); coordinator.abort();
            assertEquals(0, coordinator.transferTurns().size()); assertFalse(second.allows());
        }
    }
    @Test void duplicateRegistrationCannotStealOrDiscardTheLoadedOwnerToken() {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger()));
        try (var writer = new EscrowLedgerWriter(temp.resolve("duplicate"), 0);
                var coordinator = new EscrowCoordinator(data, writer);
                var owner = new EscrowDeviceTurns(); var duplicate = new EscrowDeviceTurns()) {
            assertTrue(owner.bind(coordinator, id(1), id(99))); owner.blocked();
            assertFalse(duplicate.bind(coordinator, id(1), id(99))); duplicate.close();
            assertEquals(1, coordinator.transferTurns().size()); assertTrue(owner.allows());
        }
    }
    @Test void nextTurnCannotPreemptAnAlreadyRunningPreparationAndIsServedAfterIt() {
        var turns = new EscrowTransferTurns();
        try (var first = turns.register(id(1), id(99)); var second = turns.register(id(2), id(99))) {
            assertTrue(turns.claim(id(1), id(99)));
            turns.blocked(id(2), id(99));
            assertTrue(turns.allows(id(1), id(99))); assertFalse(turns.allows(id(2), id(99)));
            turns.admitted(id(1), id(99));
            assertTrue(turns.allows(id(2), id(99))); assertFalse(turns.allows(id(1), id(99)));
            assertTrue(turns.claim(id(2), id(99))); assertEquals(0, turns.size()); turns.blocked(id(1), id(99));
            turns.release(id(2), id(99));
            assertTrue(turns.allows(id(1), id(99))); assertFalse(turns.allows(id(2), id(99)));
        }
        assertEquals(0, turns.size());
    }
}
