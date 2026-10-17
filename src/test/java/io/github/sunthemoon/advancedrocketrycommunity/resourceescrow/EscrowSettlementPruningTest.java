package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowSettlementPruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactCEnqueueAndIndependentVisitsDoNotDependOnPermanentlyHeldB() {
        var a = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var b = EscrowTestFixtures.operation(2).withReason(EscrowReason.OTHER_STATE);
        var c = EscrowTestFixtures.operation(3).withReason(EscrowReason.OTHER_STATE);
        var ra = released(a); var rc = released(c);
        var cReceipt = rc.tombstones().get(0); cReceipt.putLong("release_generation", 7);
        var root = new EscrowLedger(ra.id(), 3, 7, List.of(b), List.of(),
                List.of(ra.tombstones().get(0), cReceipt));
        var cursor = new EscrowSettlementPruning.Cursor();
        String digest = rc.tombstones().get(0).getString("decision_digest");
        cursor.enqueue(root, new EscrowCoordinator.SettlementAcknowledgement(root.id(), digest, Set.of(c.id()), 7, true));
        assertEquals(Set.of(c.id()), cursor.next(root, Set.of(b.id())).group().members());
        var visited = new java.util.HashSet<java.util.UUID>();
        for (int i = 0; i < 8; i++) { visited.addAll(cursor.next(root, Set.of(b.id())).group().members()); }
        assertEquals(Set.of(a.id(), c.id()), visited);
        assertEquals(List.of(b), root.records());
        // A restart rebuilds the same work even when the O4 callback never ran.
        var restart = new EscrowSettlementPruning.Cursor();
        assertNotNull(restart.next(EscrowCodec.decode(EscrowCodec.encode(root)), Set.of(b.id())));
        assertThrows(IllegalArgumentException.class, () -> cursor.enqueue(root,
                new EscrowCoordinator.SettlementAcknowledgement(root.id(), digest, Set.of(a.id()), 7, true)));
        assertThrows(IllegalArgumentException.class, () -> cursor.enqueue(root,
                new EscrowCoordinator.SettlementAcknowledgement(root.id(), digest, Set.of(c.id()), 6, true)));
        assertThrows(IllegalArgumentException.class, () -> cursor.enqueue(root,
                new EscrowCoordinator.SettlementAcknowledgement(root.id(), digest, Set.of(c.id()), 7, false)));
    }

    @Test void laterGroupWaitsForEarlierGroupAndLiveOrUnknownMarkersArePreserved() {
        var a = new EscrowPrefix.Marker(EscrowTestFixtures.id(1), 1, "DESTINATION", 1);
        var c = new EscrowPrefix.Marker(EscrowTestFixtures.id(3), 3, "DESTINATION", 2);
        var live = new EscrowPrefix.Marker(EscrowTestFixtures.id(4), 4, "DESTINATION", 3);
        var before = List.of(a, c, live);
        assertEquals(before, EscrowSettlementPruning.prune(before, Set.of(c.operation()), Set.of()));
        var afterA = EscrowSettlementPruning.prune(before, Set.of(a.operation()), Set.of());
        assertEquals(List.of(c, live), afterA);
        assertDoesNotThrow(() -> new RocketEscrowSection(EscrowTestFixtures.id(500), 3, afterA));
        var afterC = EscrowSettlementPruning.prune(afterA, Set.of(c.operation()), Set.of());
        assertEquals(List.of(live), afterC);
        assertDoesNotThrow(() -> new RocketEscrowSection(EscrowTestFixtures.id(500), 3, afterC));
        assertEquals(afterA, EscrowSettlementPruning.prune(afterA, Set.of(c.operation()), Set.of(c.operation())));
        assertEquals(before, EscrowSettlementPruning.prune(before, Set.of(a.operation(), live.operation()), Set.of()));
    }

    @Test void aBusyGroupDoesNotPreventTheCursorFromVisitingAnotherGroup() {
        var a = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var c = EscrowTestFixtures.operation(3).withReason(EscrowReason.OTHER_STATE);
        var ra = released(a); var rc = released(c);
        var root = new EscrowLedger(ra.id(), 3, 0, List.of(), List.of(),
                List.of(ra.tombstones().get(0), rc.tombstones().get(0)));
        var cursor = new EscrowSettlementPruning.Cursor();
        assertNull(cursor.next(root, Set.of(a.id())));
        assertEquals(Set.of(c.id()), cursor.next(root, Set.of(a.id())).group().members());
        // A skipped/unloaded endpoint advances its endpoint cursor, not its authority.
        var first = cursor.next(root, Set.of());
        cursor.next(root, Set.of());
        var later = cursor.next(root, Set.of());
        assertEquals(first.group(), later.group());
        assertNotEquals(first.endpoint().reference().key(), later.endpoint().reference().key());
    }

    @Test void refusedPruningLogsOneBoundedWarningPerReasonTransitionNeverPerVisit() {
        var root = released(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE));
        var cursor = new EscrowSettlementPruning.Cursor();
        var first = cursor.next(root, Set.of()); var second = cursor.next(root, Set.of());
        String digest = first.group().digest();
        var keys = List.of(first.endpoint().reference().key(), second.endpoint().reference().key());
        assertNotEquals(keys.get(0), keys.get(1));
        assertEquals("UNVISITED", cursor.summary(digest, keys)); assertEquals("NONE", cursor.summary(digest, List.of()));
        var frontier = EscrowSettlementPruning.State.refused("frontier\n" + "x".repeat(300));
        assertEquals(160, frontier.reason().length()); assertFalse(frontier.reason().contains("\n"));
        try (var logs = new Logs()) {
            for (int visit = 0; visit < 5; visit++) { cursor.observe(first, frontier); }
            assertEquals(1, logs.warnings().size(), "One WARN per (group, endpoint, reason), never per visit");
            assertTrue(logs.warnings().get(0).contains("digest=" + digest) && logs.warnings().get(0).contains("markers=retained")
                    && logs.warnings().get(0).length() < 600);
            assertEquals(frontier.text(), cursor.summary(digest, keys));
            // A wait and the same reason again do not log; a different reason is a new transition.
            cursor.observe(first, EscrowSettlementPruning.State.WAITING_EARLIER_GROUP);
            assertEquals("WAITING_EARLIER_GROUP", cursor.summary(digest, keys));
            cursor.observe(first, frontier); assertEquals(1, logs.warnings().size());
            var placement = EscrowSettlementPruning.State.refused("placement RELOCATED_ENDPOINT");
            cursor.observe(first, placement); cursor.observe(first, placement);
            assertEquals(2, logs.warnings().size()); assertTrue(logs.warnings().get(1).contains("reason=placement RELOCATED_ENDPOINT"));
            // The other endpoint has its own transition, and an unvisited endpoint keeps the group short of DONE.
            cursor.observe(second, placement); assertEquals(3, logs.warnings().size());
            cursor.observe(first, EscrowSettlementPruning.State.DONE); cursor.observe(first, EscrowSettlementPruning.State.DONE);
            assertEquals(1, logs.infos().size()); assertEquals(placement.text(), cursor.summary(digest, keys));
            assertEquals("DONE", cursor.summary(digest, List.of(keys.get(0))));
            cursor.observe(second, EscrowSettlementPruning.State.DONE);
            assertEquals("DONE", cursor.summary(digest, keys)); assertEquals(2, logs.infos().size()); assertEquals(3, logs.warnings().size());
        }
        // States follow the released groups of the acknowledged root and stay bounded by them.
        cursor.released(new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), List.of(), List.of(), List.of()));
        assertEquals("UNVISITED", cursor.summary(digest, keys));
    }

    static final class Logs extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        private final java.util.List<org.apache.logging.log4j.core.LogEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        Logs() {
            super("settlement-pruning-diagnostic", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            start(); logger.addAppender(this);
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            if (event.getMessage().getFormattedMessage().startsWith("ARCE_ESCROW_PRUNING_")) { events.add(event.toImmutable()); }
        }
        List<String> warnings() { return lines(org.apache.logging.log4j.Level.WARN); }
        List<String> infos() { return lines(org.apache.logging.log4j.Level.INFO); }
        private List<String> lines(org.apache.logging.log4j.Level level) {
            return events.stream().filter(event -> event.getLevel() == level).map(event -> event.getMessage().getFormattedMessage()).toList();
        }
        @Override public void close() { logger.removeAppender(this); stop(); }
    }

    private static EscrowLedger released(EscrowOperation op) {
        var root = EscrowTestFixtures.ledger(op);
        var observations = EscrowSettlementIntentTest.observations(root, false);
        var actor = EscrowTestFixtures.id(800);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                actor, "operator", 10, observations);
        var intent = preview.confirm(actor, preview.digest(), 11, observations);
        return EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
    }
    @Test void exactWriterGenerationCannotAcknowledgeAReceiptReleasedInAnotherGeneration() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = released(op).generation(7);
        var receipt = root.tombstones().get(0); var cursor = new EscrowSettlementPruning.Cursor();
        assertEquals(1, receipt.getLong("release_generation"));
        var ack = new EscrowCoordinator.SettlementAcknowledgement(root.id(), receipt.getString("decision_digest"), Set.of(op.id()), 7, true);
        assertThrows(IllegalArgumentException.class, () -> cursor.enqueue(root, ack));
        receipt.putLong("release_generation", 7);
        var exact = root.replace(root.records(), root.floors(), List.of(receipt));
        assertDoesNotThrow(() -> cursor.enqueue(exact, ack));
        assertNotNull(cursor.next(exact, Set.of()));
    }
    @Test void emptyMarkersReturnBeforeRuntimeAccessAndReleasedGroupsAreCachedPerAcknowledgedRoot() {
        var empty = RocketEscrowSection.neutral();
        assertSame(empty, EscrowSettlementPruning.rocket(null, empty));
        assertSame(empty, EscrowSettlementPruning.rocket(null, empty, "unused", Set.of()));
        var root = released(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE));
        var cursor = new EscrowSettlementPruning.Cursor(); var groups = cursor.released(root);
        for (int i = 0; i < 100; i++) {
            assertSame(groups, cursor.released(root)); assertNotNull(cursor.next(root, Set.of()));
        }
        assertNotSame(groups, cursor.released(root.generation(1)));
        var endpoint = groups.get(0).endpoints().get(0); var survivor = endpoint.survivor();
        var current = new EscrowPrefix.State(root.id(), survivor.getLong("epoch"), survivor.getString("signature"), List.of());
        assertTrue(EscrowSettlementPruning.frontier(root, groups, endpoint.reference(), current, survivor.getLong("quantity"), current.signature(), Set.of()));
        assertFalse(EscrowSettlementPruning.frontier(root, List.of(), endpoint.reference(), current, survivor.getLong("quantity"), current.signature(), Set.of()));
    }
    @Test void sameFloorPruningNeedsTheFrozenMarkerSuffixNotOnlyMatchingFuel() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var marker = new EscrowPrefix.Marker(op.id(), op.sequence(), "DESTINATION", 1);
        var observed = List.of(new EscrowSettlementIntent.Observation(op.source(), new EscrowPrefix.State(root.id(), 0,
                        op.source().beforeSignature(), List.of()), true, 5_000),
                new EscrowSettlementIntent.Observation(op.destination(), new EscrowPrefix.State(root.id(), 1,
                        op.destination().afterSignature(), List.of(marker)), true, 1_000));
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(800), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
        var current = observed.get(1).state();
        assertTrue(EscrowSettlementPruning.frontier(released, op.destination(), current, 1_000, current.signature(), Set.of()));
        var forged = new EscrowPrefix.State(root.id(), 1, current.signature(), List.of(new EscrowPrefix.Marker(op.id(), 99, "DESTINATION", 1)));
        assertFalse(EscrowSettlementPruning.frontier(released, op.destination(), forged, 1_000, current.signature(), Set.of()));
        var pruned = new EscrowPrefix.State(root.id(), 1, current.signature(), List.of());
        assertTrue(EscrowSettlementPruning.frontier(released, op.destination(), pruned, 1_000, current.signature(), Set.of()));
        assertFalse(EscrowSettlementPruning.frontier(released, op.destination(), pruned, 1_000, current.signature(), Set.of(op.id())));
        var next = EscrowTestFixtures.operation(2, 1, 1); var extended = released.append(List.of(next));
        var later = new EscrowPrefix.Link(next, next.destination(), "DESTINATION").marker();
        var advanced = new EscrowPrefix.State(root.id(), 2, next.destination().afterSignature(), List.of(marker, later));
        assertTrue(EscrowSettlementPruning.frontier(extended, op.destination(), advanced, 2_000, advanced.signature(), Set.of(next.id())));
        var falsePrefix = new EscrowPrefix.State(root.id(), 2, advanced.signature(), List.of(forged.markers().get(0), later));
        assertFalse(EscrowSettlementPruning.frontier(extended, op.destination(), falsePrefix, 2_000, falsePrefix.signature(), Set.of(next.id())));
    }
}
