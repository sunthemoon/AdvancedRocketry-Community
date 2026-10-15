package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowPruningBusyTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void laterFrozenDecisionQueuedCapturedAndFrozenFrontiersAllPreventPruningWithoutAStickySkip() {
        var old = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(old); var observations = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, old.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
        var later = EscrowTestFixtures.operation(2, 1, 1).withReason(EscrowReason.OTHER_STATE);
        var extended = released.append(List.of(later)); var current = EscrowSettlementIntentTest.observations(extended, false);
        var nextPreview = EscrowSettlementIntent.preview(extended, later.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 20, current);
        var frozen = nextPreview.confirm(nextPreview.actor(), nextPreview.digest(), 21, current).freeze(extended);
        var key = later.destination().key();
        assertTrue(EscrowSettlementPruning.busy(key, frozen.records(), List.of(), List.of(), Set.of()));
        assertTrue(EscrowSettlementPruning.busy(key, List.of(), List.of(later), List.of(), Set.of()));
        assertTrue(EscrowSettlementPruning.busy(key, List.of(), List.of(), List.of(later), Set.of()));
        assertTrue(EscrowSettlementPruning.busy(key, List.of(), List.of(), List.of(), Set.of(key)));
        assertFalse(EscrowSettlementPruning.busy(key, extended.records(), List.of(), List.of(), Set.of()));
        assertFalse(EscrowSettlementPruning.busy(EscrowTestFixtures.operation(99).destination().key(), frozen.records(), List.of(later), List.of(later), Set.of(key)));
        var busy = new AtomicBoolean(true); var cursor = new EscrowSettlementPruning.Cursor();
        for (int i = 0; i < 4; i++) { assertNull(cursor.next(released, Set.of(), ignored -> busy.get())); }
        busy.set(false); assertNotNull(cursor.next(released, Set.of(), ignored -> busy.get()));
        assertEquals(nextPreview.digest(), EscrowSettlementIntent.decode(frozen.records().get(0).settlement()).digest());
    }
}
