package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RocketTransferFencesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void loadedOnlyRegionReadIsBoundedAtSixteenColumnsAndRejectsOversizeBeforeReading() {
        var calls = new AtomicInteger();
        assertTrue(RocketTransferEntities.columnsAlreadyLoaded(0, 3, 0, 3, (x, z) -> { calls.incrementAndGet(); return true; }));
        assertEquals(16, calls.get());
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(0, 16, 0, 0, (x, z) -> { fail("Oversize region was inspected"); return true; }));
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0,
                (x, z) -> { fail("Overflow region was inspected"); return true; }));
        assertTrue(RocketTransferEntities.columnsAlreadyLoaded(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                (x, z) -> x == Integer.MAX_VALUE && z == Integer.MAX_VALUE));
        assertFalse(RocketTransferEntities.columnsAlreadyLoaded(0, 3, 0, 3, (x, z) -> x != 2 || z != 2));
    }

    @Test void fencedSelectionNeverCallsTheTicketAddingPredicateAndPreservesFairness() {
        var fenced = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var healthy = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        var calls = new AtomicInteger();
        for (int attempt = 0; attempt < 1_200; attempt++) {
            var step = RocketTransferRecoveryService.step(List.of(fenced), Set.of(), Set.of(), Set.of(fenced.transferId()), 0,
                    record -> { calls.incrementAndGet(); return true; });
            assertNull(step.candidate());
        }
        assertEquals(0, calls.get());
        var step = RocketTransferRecoveryService.step(List.of(fenced, healthy), Set.of(), Set.of(), Set.of(fenced.transferId()), 0,
                record -> { calls.incrementAndGet(); return true; });
        assertSame(healthy, step.candidate()); assertEquals(1, calls.get());
    }

    @Test void retriesHaveBothTickAndTransferBoundsIncludingRepeatedOperatorAttempts() {
        var fences = new RocketTransferFences(); var reads = new AtomicInteger();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        for (var id : List.of(a, b, c)) {
            fences.reevaluate(id, 0, () -> { reads.incrementAndGet(); return "WAITING_UNLOADED"; }, live, settled);
        }
        assertEquals(RocketTransferFences.RETRIES_PER_TICK, reads.get());
        for (int attempt = 0; attempt < 100; attempt++) {
            assertFalse(fences.reevaluate(a, 1, () -> { reads.incrementAndGet(); return null; }, live, settled));
        }
        assertEquals(2, reads.get()); assertTrue(fences.ids().contains(a));
        assertFalse(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS - 1L,
                () -> { reads.incrementAndGet(); return null; }, live, settled));
        assertTrue(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS,
                () -> { reads.incrementAndGet(); return null; }, live, settled));
        assertEquals(3, reads.get()); assertFalse(fences.ids().contains(a));
        assertFalse(fences.reevaluate(a, RocketTransferFences.RETRY_TICKS + 1L, () -> null, live, settled));
    }

    /**
     * W328F (R397, review F3): a fence WARN is deduplicated against the record's previous reason, not once per record.
     * A stable reason logs once; a reason that alternates logs on each change. The reevaluation path observes, and so
     * changes the reason, at most once per record per {@link RocketTransferFences#RETRY_TICKS}.
     */
    @Test void fenceWarnsRepeatOnEachReasonChangeButNotForAStableReason() {
        var fences = new RocketTransferFences(ignored -> null); var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var id = UUID.randomUUID();
        try (var logs = new FenceLogs()) {
            for (int interval = 0; interval < 10; interval++) {
                fences.reevaluate(id, interval * (long) RocketTransferFences.RETRY_TICKS,
                        () -> "ESCROW_PENDING_OR_TOMBSTONE", live, settled);
            }
            assertEquals(List.of("ESCROW_PENDING_OR_TOMBSTONE"), logs.reasons(id), "a stable reason logs once");

            // Inside the interval the observation is not even made, so the reason cannot change there.
            fences.reevaluate(id, 9L * RocketTransferFences.RETRY_TICKS + 1L,
                    () -> fail("observed inside the per-record interval"), live, settled);
            var alternating = List.of("ENDPOINT_JOIN_PROOF_REQUIRED", "ESCROW_PENDING_OR_TOMBSTONE",
                    "ENDPOINT_JOIN_PROOF_REQUIRED", "ESCROW_PENDING_OR_TOMBSTONE");
            for (int step = 0; step < alternating.size(); step++) {
                String reason = alternating.get(step);
                fences.reevaluate(id, (10L + step) * RocketTransferFences.RETRY_TICKS, () -> reason, live, settled);
            }
            var expected = new java.util.ArrayList<>(List.of("ESCROW_PENDING_OR_TOMBSTONE")); expected.addAll(alternating);
            assertEquals(expected, logs.reasons(id), "each change logs again");
        }

        // The fence path (joins and final action guards reach the same private method) dedupes the same way. The
        // governed fixture only reaches it without a server; ordinary records reach it through joined and actionAllowed.
        var record = GovernedTransferFixture.create().record();
        try (var logs = new FenceLogs()) {
            var missing = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "GOVERNED_LEDGER_MISSING");
            var action = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "GOVERNED_ACTION_REQUIRED");
            for (int pass = 0; pass < 20; pass++) { fences.classifyGoverned(record, missing, live, settled); }
            assertEquals(List.of("GOVERNED_LEDGER_MISSING"), logs.reasons(record.transferId()));
            for (int pass = 0; pass < 4; pass++) { fences.classifyGoverned(record, pass % 2 == 0 ? action : missing, live, settled); }
            assertEquals(List.of("GOVERNED_LEDGER_MISSING", "GOVERNED_ACTION_REQUIRED", "GOVERNED_LEDGER_MISSING",
                    "GOVERNED_ACTION_REQUIRED", "GOVERNED_LEDGER_MISSING"), logs.reasons(record.transferId()));

            // Both paths compare with the same stored reason: a reevaluation that observes another reason logs too.
            fences.reevaluate(record.transferId(), 0L, () -> "GOVERNED_ACTION_REQUIRED", live, settled);
            fences.classifyGoverned(record, action, live, settled);
            assertEquals(List.of("GOVERNED_LEDGER_MISSING", "GOVERNED_ACTION_REQUIRED", "GOVERNED_LEDGER_MISSING",
                    "GOVERNED_ACTION_REQUIRED", "GOVERNED_LEDGER_MISSING", "GOVERNED_ACTION_REQUIRED"),
                    logs.reasons(record.transferId()), "the reevaluation logged its change; the same fence reason did not");
        }
    }

    /**
     * W328G: joins on either side of a due reevaluation can log three WARNs in three ticks.
     * Supplies the traced join/observation reasons through W328F's shared fence and reevaluation paths;
     * this is not a native join-scheduling test or a rolling-window rate measurement.
     */
    @Test void fenceWarnCycleBoundDoesNotCapARollingWindowAcrossTheBoundary() {
        var fences = new RocketTransferFences(ignored -> null);
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var record = GovernedTransferFixture.create().record(); var id = record.transferId();
        var escrow = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "ESCROW_PENDING_OR_TOMBSTONE");
        var joined = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "ENDPOINT_JOIN_PROOF_REQUIRED");
        var reads = new AtomicInteger();
        try (var logs = new FenceLogs()) {
            fences.classifyGoverned(record, escrow, live, settled);
            assertEquals(List.of(escrow.reason()), logs.reasons(id), "the first fence logs separately");
            assertFalse(fences.reevaluate(id, 0, () -> { reads.incrementAndGet(); return escrow.reason(); }, live, settled));
            assertEquals(List.of(escrow.reason()), logs.reasons(id), "the tick-0 observation keeps the stable reason");

            assertFalse(fences.reevaluate(id, 1_199,
                    () -> fail("observed before the reevaluation is due"), live, settled));
            fences.classifyGoverned(record, joined, live, settled);
            assertEquals(List.of(escrow.reason(), joined.reason()), logs.reasons(id), "join reason at tick 1199 logs");

            assertFalse(fences.reevaluate(id, 1_200, () -> { reads.incrementAndGet(); return escrow.reason(); }, live, settled));
            assertEquals(List.of(escrow.reason(), joined.reason(), escrow.reason()), logs.reasons(id),
                    "the due reevaluation at tick 1200 logs");

            assertFalse(fences.reevaluate(id, 1_201,
                    () -> fail("observed inside the new reevaluation cycle"), live, settled));
            fences.classifyGoverned(record, joined, live, settled);
            var expected = List.of(escrow.reason(), joined.reason(), escrow.reason(), joined.reason());
            assertEquals(expected, logs.reasons(id), "join reason at tick 1201 is the third boundary WARN");
            fences.classifyGoverned(record, joined, live, settled);
            assertEquals(expected, logs.reasons(id), "a repeated stable join reason adds no WARN");
            assertEquals(joined.reason(), fences.reason(id));
            assertEquals(2, reads.get(), "only ticks 0 and 1200 reevaluated; join reasons are not throttled");
        }
    }

    /** Captures {@code ARCE_TRANSFER_FENCED} WARNs. */
    private static final class FenceLogs extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        private final java.util.List<String> lines = new java.util.ArrayList<>();
        FenceLogs() {
            super("w328f-fence-test", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            start(); logger.addAppender(this);
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            String line = event.getMessage().getFormattedMessage();
            if (line.startsWith("ARCE_TRANSFER_FENCED ")) {
                assertEquals(org.apache.logging.log4j.Level.WARN, event.getLevel());
                synchronized (lines) { lines.add(line); }
            }
        }
        List<String> reasons(UUID id) {
            String prefix = "ARCE_TRANSFER_FENCED transfer=" + id + " reason=";
            synchronized (lines) {
                return lines.stream().filter(line -> line.startsWith(prefix))
                        .map(line -> line.substring(prefix.length(), line.indexOf(" repair=offline-whole-world"))).toList();
            }
        }
        @Override public void close() { logger.removeAppender(this); stop(); }
    }

    @Test void classificationPersistsAcrossReadOnlyObservationAndAnUnrelatedWarpStaysAvailable() {
        var record = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var fences = new RocketTransferFences(); var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        fences.reevaluate(record.transferId(), 0, () -> "WAITING_UNLOADED", live, settled);
        var unrelated = new RocketStationMotionRule.Region(record.destinationSnapshot().sourceDimension(), 1_000, 1_000, 1_511, 1_511);
        for (int tick = 1; tick <= 1_200; tick++) {
            fences.reevaluate(record.transferId(), tick, () -> {
                assertTrue(fences.ids().contains(record.transferId()), "Observation must not temporarily unclassify");
                return "WAITING_UNLOADED";
            }, live, settled);
            assertFalse(RocketStationMotionRule.blocks(true, List.of(record), fences.ids()::contains, fences.ids()::contains, tick, unrelated));
        }
        assertTrue(RocketStationMotionRule.blocks(true, List.of(record), fences.ids()::contains, fences.ids()::contains, 1_200,
                new RocketStationMotionRule.Region(record.destinationSnapshot().sourceDimension(), 0, 0, 511, 511)));
    }
}
