package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TransferFailureDiagnosticsTest {
    private static final UUID A = new UUID(1L, 2L);
    private static final UUID B = new UUID(3L, 4L);

    @Test
    void initiallyReportsUnobservedRatherThanACompletedTick() {
        var data = new TransferFailureDiagnostics();
        var fields = fields(data, A, false, false, false);
        assertEquals("NO", fields.get("flight_active"));
        assertEquals("UNCLASSIFIED", fields.get("classification"));
        assertEquals("UNOBSERVED", fields.get("service_entered_tick"));
        assertEquals("UNOBSERVED", fields.get("service_completed_tick"));
        assertEquals("NO", fields.get("tracked"));
        assertEquals("UNOBSERVED", fields.get("target_last_tick"));
    }

    @Test
    void preparationDoesNotInventDispatchOrCompletion() {
        var data = new TransferFailureDiagnostics();
        data.prepared(A);
        var fields = fields(data, A, true, true, false);
        assertEquals("YES", fields.get("tracked"));
        assertEquals("PREPARED", fields.get("target_branch"));
        assertEquals("UNOBSERVED", fields.get("target_last_tick"));
        assertEquals("UNOBSERVED", fields.get("service_completed_tick"));
    }

    @Test
    void latestPreparationReplacesOnlyTheSingleTargetSlot() {
        var data = dispatched(A, 50);
        data.branch(A, TransferFailureDiagnostics.Branch.WAIT_ENTITY_READY);
        data.completeTick();
        data.prepared(B);
        assertEquals("NO", fields(data, A, true, true, false).get("tracked"));
        var current = fields(data, B, true, true, false);
        assertEquals("PREPARED", current.get("target_branch"));
        assertEquals("UNOBSERVED", current.get("target_last_tick"));
        assertEquals("50", current.get("service_completed_tick"));
    }

    @Test
    void foreignDispatchDoesNotAttributeItsTickToWatchedRequest() {
        var data = dispatched(A, 2);
        String before = data.snapshot(A, true, true, false);
        data.dispatch(B);
        assertEquals(before, data.snapshot(A, true, true, false));
        assertEquals("UNOBSERVED", fields(data, B, true, true, false).get("target_last_tick"));
    }

    @Test
    void foreignBranchDoesNotOverwriteTheWatchedBranch() {
        var data = dispatched(A, 2);
        data.branch(A, TransferFailureDiagnostics.Branch.WAIT_ENTITY_READY);
        data.branch(B, TransferFailureDiagnostics.Branch.TICK_EXCEPTION);
        assertEquals("WAIT_ENTITY_READY", fields(data, A, true, true, false).get("target_branch"));
        assertEquals("UNOBSERVED", fields(data, B, true, true, false).get("target_branch"));
    }

    @Test
    void entryWithoutCompletionPreservesPreviousCompletionTimestamp() {
        var data = dispatched(A, 10);
        data.completeTick();
        data.enterTick(11);
        var fields = fields(data, A, true, true, false);
        assertEquals("11", fields.get("service_entered_tick"));
        assertEquals("10", fields.get("service_completed_tick"));
    }

    @Test
    void completionBeforeAnyEntryRemainsUnobserved() {
        var data = new TransferFailureDiagnostics();
        data.completeTick();
        assertEquals("UNOBSERVED", fields(data, A, true, false, false).get("service_completed_tick"));
    }

    @Test
    void dispatchBeforeAnyEntryRemainsUnobserved() {
        var data = new TransferFailureDiagnostics();
        data.prepared(A);
        data.dispatch(A);
        assertEquals("PREPARED", fields(data, A, true, true, false).get("target_branch"));
        assertEquals("UNOBSERVED", fields(data, A, true, true, false).get("target_last_tick"));
    }

    @Test
    void completionRecordsTheExactEnteredTickWithoutArithmetic() {
        var data = dispatched(A, -1);
        data.completeTick();
        assertEquals("-1", fields(data, A, true, true, false).get("service_completed_tick"));
        data.enterTick(Integer.MIN_VALUE);
        data.completeTick();
        assertEquals(Integer.toString(Integer.MIN_VALUE),
                fields(data, A, true, true, false).get("service_completed_tick"));
    }

    @Test
    void allMembershipCombinationsAreReportedWithoutReclassification() {
        var data = new TransferFailureDiagnostics();
        assertEquals("UNCLASSIFIED", fields(data, A, true, false, false).get("classification"));
        assertEquals("LIVE", fields(data, A, true, true, false).get("classification"));
        assertEquals("SETTLED", fields(data, A, true, false, true).get("classification"));
        assertEquals("OVERLAP", fields(data, A, true, true, true).get("classification"));
    }

    @Test
    void lifecycleFlagDoesNotChangeClassificationOrSlot() {
        var data = dispatched(A, 3);
        String active = data.snapshot(A, true, true, false);
        String inactive = data.snapshot(A, false, true, false);
        assertEquals(active.replace("flight_active=YES", "flight_active=NO"), inactive);
        assertEquals(active, data.snapshot(A, true, true, false));
    }

    @Test
    void everyDeclaredBranchHasFixedLiteralScalarOutput() {
        var data = dispatched(A, 3);
        for (var branch : TransferFailureDiagnostics.Branch.values()) {
            data.branch(A, branch);
            assertEquals(branch.name(), fields(data, A, true, true, false).get("target_branch"));
        }
    }

    @Test
    void clearDisposesAllObservationsAndTargetIdentity() {
        var data = dispatched(A, Integer.MAX_VALUE);
        data.completeTick();
        data.branch(A, TransferFailureDiagnostics.Branch.TICK_EXCEPTION);
        data.clear();
        assertEquals(new TransferFailureDiagnostics().snapshot(A, false, false, false),
                data.snapshot(A, false, false, false));
    }

    @Test
    void nullPreparationRemovesTheSlotWithoutInventingAnObservation() {
        var data = dispatched(A, 10);
        data.prepared(null);
        assertEquals("NO", fields(data, A, true, true, false).get("tracked"));
        assertEquals("UNOBSERVED", fields(data, A, true, true, false).get("target_branch"));
    }

    @Test
    void nullRequestedIdentityCannotBorrowAnotherClassification() {
        var data = dispatched(A, 10);
        var fields = fields(data, null, true, true, true);
        assertEquals("UNOBSERVED", fields.get("classification"));
        assertEquals("NO", fields.get("tracked"));
        assertEquals("UNOBSERVED", fields.get("target_last_tick"));
    }

    @Test
    void nullBranchIsAnExplicitUnobservedToken() {
        var data = dispatched(A, 10);
        data.branch(A, null);
        assertEquals("UNOBSERVED", fields(data, A, true, true, false).get("target_branch"));
    }

    @Test
    void repeatedSnapshotsHaveNoStateEffects() {
        var data = dispatched(A, 10);
        data.branch(A, TransferFailureDiagnostics.Branch.WAIT_ENTITY_READY);
        String before = data.snapshot(A, true, true, false);
        for (int index = 0; index < 100; index++) {
            assertEquals(before, data.snapshot(A, true, true, false));
            data.snapshot(B, false, false, true);
        }
        assertEquals(before, data.snapshot(A, true, true, false));
    }

    @Test
    void bothFixturePayloadsStayPrintableAsciiWithin512Bytes() {
        for (int tick : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            var data = dispatched(A, tick);
            data.completeTick();
            for (var branch : TransferFailureDiagnostics.Branch.values()) {
                data.branch(A, branch);
                for (String fixture : new String[]{"TAU", "PLANETARY"}) {
                    String payload = "ARCE_TRANSFER_SERVICE_FAILURE fixture=" + fixture + " transfer=" + A
                            + " current_server_tick=" + tick + " " + data.snapshot(A, false, true, true);
                    assertTrue(payload.length() <= 512, payload);
                    assertTrue(payload.chars().allMatch(value -> value >= 32 && value <= 126));
                    assertFalse(payload.contains("\n"));
                }
            }
        }
    }

    @Test
    void uuidValueEqualityDoesNotRequireTheSameObject() {
        var data = dispatched(A, 10);
        UUID equal = UUID.fromString(A.toString());
        data.branch(equal, TransferFailureDiagnostics.Branch.WAIT_ENTITY_READY);
        assertEquals("YES", fields(data, equal, true, true, false).get("tracked"));
        assertEquals("WAIT_ENTITY_READY", fields(data, equal, true, true, false).get("target_branch"));
    }

    private static TransferFailureDiagnostics dispatched(UUID id, int tick) {
        var data = new TransferFailureDiagnostics();
        data.prepared(id);
        data.enterTick(tick);
        data.dispatch(id);
        return data;
    }

    private static Map<String, String> fields(TransferFailureDiagnostics data, UUID id,
            boolean active, boolean live, boolean settled) {
        return Stream.of(data.snapshot(id, active, live, settled).split(" "))
                .map(token -> token.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
    }
}
