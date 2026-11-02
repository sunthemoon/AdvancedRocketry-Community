package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitStateTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void tAThenBThenAAndEveryLiveCleanupRetainsBothDeadlines() {
        var state = new BayPassengerExitState();
        UUID player = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), logical = UUID.randomUUID();
        assertEquals(210, state.admit(player, a, logical, 10).deadline());
        state.issued(player, a);
        assertEquals(220, state.admit(player, b, logical, 20).deadline());
        for (String cut : new String[]{"landing", "fluid", "expiry", "teleport", "transfer", "remount", "logout",
                "death", "clone", "level", "failed-placement", "lost-authority", "live-release"}) {
            state.live.clear();
            state.clean((transfer, id) -> false);
            assertNull(state.admit(player, a, logical, 300), cut);
            assertEquals(210, state.history(player, a).deadline(), cut);
            assertTrue(state.history(player, a).issued(), cut);
            assertFalse(state.history(player, b).issued(), cut);
            assertEquals(2, state.histories(), cut);
        }
    }
    @Test void firstSafePlacementAlsoClosesOfferAndExpiryIsNotGarbageCollection() {
        var state = new BayPassengerExitState();
        UUID player = UUID.randomUUID(), transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        var row = state.admit(player, transfer, logical, 40);
        assertFalse(row.issued());
        assertEquals(240, row.deadline());
        assertTrue(239 < row.deadline()); assertFalse(240 < row.deadline());
        assertNull(state.admit(player, transfer, logical, 240));
        assertSame(row, state.history(player, transfer));
    }
    @Test void uuidHistoryDoesNotDependOnThePlayerIncarnation() {
        var state = new BayPassengerExitState();
        UUID id = UUID.randomUUID(), transfer = UUID.randomUUID();
        var old = UnstartedServer.allocate(ServerPlayer.class);
        var replacement = UnstartedServer.allocate(ServerPlayer.class);
        state.admit(id, transfer, UUID.randomUUID(), 1);
        state.live.put(old, null); state.live.remove(old); state.live.put(replacement, null);
        assertNull(state.admit(id, transfer, UUID.randomUUID(), 2));
        assertEquals(201, state.history(id, transfer).deadline());
    }
    @Test void fullHistoryNeverEvictsAnOlderRow() {
        var state = new BayPassengerExitState();
        UUID[] transfers = new UUID[64];
        UUID first = new UUID(0, 1);
        for (int t = 0; t < 64; t++) {
            transfers[t] = UUID.randomUUID();
            for (int p = 0; p < 16; p++) {
                assertNotNull(state.admit(new UUID(t, p + 1), transfers[t], new UUID(1, t), 7));
            }
        }
        var retained = state.history(first, transfers[0]);
        assertEquals(1024, state.histories());
        assertNull(state.admit(UUID.randomUUID(), transfers[0], UUID.randomUUID(), 10));
        assertNull(state.admit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10));
        assertSame(retained, state.history(first, transfers[0]));
        assertEquals(1024, state.histories());
    }
    @Test void tBucketCapRefusesWithoutEvictionAndAcknowledgedEndReclaimsOnlyThatT() {
        var state = new BayPassengerExitState();
        UUID player = UUID.randomUUID(), first = UUID.randomUUID(), logical = UUID.randomUUID();
        state.admit(player, first, logical, 0);
        for (int i = 1; i < 64; i++) { state.admit(player, UUID.randomUUID(), UUID.randomUUID(), i); }
        UUID next = UUID.randomUUID();
        assertNull(state.admit(player, next, logical, 100));
        state.clean((transfer, id) -> transfer.equals(first) && id.equals(logical));
        assertNull(state.history(player, first)); assertEquals(63, state.histories());
        assertNotNull(state.admit(player, next, logical, 100));
    }
    @Test void nestedInternalScopesRevokeBeforeBodyAndNeverRestoreOnFailure() {
        var state = new BayPassengerExitState();
        var player = UnstartedServer.allocate(ServerPlayer.class);
        int[] revocations = {0};
        try (var outer = state.enter(player, () -> revocations[0]++)) {
            assertTrue(state.internal(player)); assertEquals(1, revocations[0]);
            assertThrows(IllegalArgumentException.class, () -> {
                try (var inner = state.enter(player, () -> revocations[0]++)) {
                    assertTrue(state.internal(player)); throw new IllegalArgumentException("failed mount");
                }
            });
            assertTrue(state.internal(player));
        }
        assertFalse(state.internal(player)); assertEquals(2, revocations[0]);
    }
    @Test void ownerThreadIsEnforcedAndStopClearsRamHistory() throws Exception {
        var state = new BayPassengerExitState();
        var error = new AtomicReference<Throwable>();
        Thread thread = new Thread(() -> { try { state.admit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0); }
            catch (Throwable failure) { error.set(failure); } });
        thread.start(); thread.join(); assertInstanceOf(IllegalStateException.class, error.get());
        state.admit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 0);
        state.clear(); assertEquals(0, state.histories()); assertTrue(state.live.isEmpty());
    }
    @Test void hintsAreCappedAndCannotSurviveEndpointHashOrLevelChanges() {
        var state = new BayPassengerExitState();
        Object level = new Object(); UUID transfer = UUID.randomUUID(), endpoint = UUID.randomUUID();
        var old = new BayPassengerExitState.Hint(transfer, endpoint, "old", level);
        var position = new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position("test", 0, 2, 0);
        state.hint(old, position);
        var changed = new BayPassengerExitState.Hint(transfer, endpoint, "changed", level); state.hint(changed, position);
        assertTrue(state.hint(old).isEmpty()); assertEquals(position, state.hint(changed).orElseThrow());
        var otherLevel = new BayPassengerExitState.Hint(transfer, endpoint, "changed", new Object()); state.hint(otherLevel, position);
        assertTrue(state.hint(changed).isEmpty());
        for (int i = 1; i < 128; i++) { state.hint(new BayPassengerExitState.Hint(transfer, new UUID(0, i), "x", level), position); }
        var overflow = new BayPassengerExitState.Hint(transfer, UUID.randomUUID(), "x", level); state.hint(overflow, position);
        assertTrue(state.hint(overflow).isEmpty()); assertTrue(state.hint(otherLevel).isPresent());
        state.clean((t, logical) -> false); assertTrue(state.hint(otherLevel).isPresent());
    }
}
