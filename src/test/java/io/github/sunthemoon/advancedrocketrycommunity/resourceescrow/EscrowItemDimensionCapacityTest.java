package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real encoder measurements; no receipt allowance or admission mapping is changed. */
class EscrowItemDimensionCapacityTest {
    private static final List<EscrowOperation.Kind> KINDS = List.of(
            EscrowOperation.Kind.ITEM_CARGO_LOAD, EscrowOperation.Kind.ITEM_CARGO_UNLOAD,
            EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD);
    private static final List<String> LEVELS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end",
            "advancedrocketrycommunity:moon", "advancedrocketrycommunity:mars", "advancedrocketrycommunity:space",
            "advancedrocketrycommunity:venus", "advancedrocketrycommunity:tau_ceti_f", "advancedrocketrycommunity:tau_ceti_g");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void oneMarkerLevelBoundariesIncludeTransferGuardsAndBothCargoKinds() {
        for (var kind : KINDS) {
            for (boolean guarded : List.of(false, true)) {
                int longest = 0;
                for (int length = 3; length <= 128; length++) {
                    try {
                        if (!fits(operation(kind, guarded, 0, false, "a:" + "b".repeat(length - 2)))) { break; }
                    } catch (IllegalArgumentException unencodable) { break; }
                    longest = length;
                }
                assertEquals(limit(kind, guarded), longest, kind + " guarded=" + guarded);
                var edge = operation(kind, guarded, 0, kind.itemCargo(), "a:" + "b".repeat(longest - 2));
                assertTrue(fits(edge));
                final int firstRefused = longest + 1;
                assertThrows(IllegalArgumentException.class,
                        () -> operation(kind, guarded, 0, kind.itemCargo(), "a:" + "b".repeat(firstRefused - 2)));
                System.out.println("CARGO_LEVEL_EDGE kind=" + kind + " guarded=" + guarded + " max_level_chars=" + longest);
                measure(edge);
            }
        }
    }

    @Test void realDimensionsAdmitCargoButRetainedMarkersStillRefuseBeforeAnySideEffect() {
        int caseNumber = 0;
        for (var kind : KINDS) {
            for (boolean guarded : List.of(false, true)) {
                for (String level : LEVELS) {
                    for (boolean maxStack : kind.itemCargo() ? List.of(false, true) : List.of(false)) {
                        for (int retained : List.of(0, 4)) {
                            var op = operation(kind, guarded, retained, maxStack, level);
                            assertTrue(fits(op), kind + " guarded=" + guarded + " level=" + level);
                            var empty = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
                            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
                            try (var writer = new EscrowLedgerWriter(temp.resolve("case-" + caseNumber++), 0);
                                    var coordinator = new EscrowCoordinator(data, writer)) {
                                var admission = new EscrowNativeBinding.Admission(op, EscrowItemFixtures.wrapper(retained));
                                var result = coordinator.prepareTransfer(admission, ignored -> fail("No write was submitted"));
                                // Sizing with retained markers grants no authority to replay unknown retired work.
                                var expectedResult = retained > 0
                                        ? EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING : EscrowCoordinator.Transfer.QUEUED;
                                boolean queued = expectedResult == EscrowCoordinator.Transfer.QUEUED;
                                assertEquals(expectedResult, result);
                                assertEquals(queued ? 101 : 100, coordinator.nextSequence());
                                assertEquals(queued ? 1 : 0, coordinator.admissionAuthority().pendingCount());
                                assertEquals(empty, coordinator.ledger()); assertEquals(queued, data.isDirty());
                                assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
                                System.out.println("CARGO_DIMENSION kind=" + kind + " guarded=" + guarded + " level=" + level
                                        + " retained=" + retained + " max_stack=" + maxStack
                                        + " payload_bytes=" + RocketNbtSize.uncompressedBytes(op.payload()) + " admission=" + result);
                            }
                            if (retained == 4 && (maxStack || !kind.itemCargo())) { measure(op); }
                        }
                    }
                }
            }
        }
    }

    private static int limit(EscrowOperation.Kind kind, boolean guarded) {
        return switch (kind) {
            case ITEM_CARGO_LOAD, ITEM_CARGO_UNLOAD -> 118;
            case FLUID_CARGO_LOAD, FLUID_CARGO_UNLOAD -> 123;
            default -> throw new AssertionError(kind);
        };
    }
    @Test void unencodableLevelStillRefusesBeforeAnyAdmissionSideEffect() {
        for (var kind : KINDS) {
            for (boolean guarded : List.of(false, true)) {
                var empty = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
                var data = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
                try (var writer = new EscrowLedgerWriter(temp.resolve("unencodable-" + kind + guarded), 0);
                        var coordinator = new EscrowCoordinator(data, writer)) {
                    assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTransfer(new EscrowNativeBinding.Admission(
                            operation(kind, guarded, 0, kind.itemCargo(), "a:" + "b".repeat(limit(kind, guarded) - 1)),
                            RocketEscrowSection.neutral()), ignored -> fail()));
                    assertEquals(100, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
                    assertEquals(empty, coordinator.ledger()); assertFalse(data.isDirty()); assertFalse(coordinator.commitPreparations());
                    assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
                }
            }
        }
    }
    private static EscrowOperation operation(EscrowOperation.Kind kind, boolean guarded, int retained, boolean maxStack, String level) {
        int blob = maxStack ? 4_096 - RocketNbtSize.uncompressedBytes(EscrowItemFixtures.stack(0)) : -1;
        var op = kind.itemCargo() ? EscrowItemFixtures.operation(kind, 100, 99, retained, blob, level)
                : EscrowCargoFixtures.operation(kind, 100, 99, retained, -1, level);
        if (maxStack) { assertEquals(4_096, RocketNbtSize.uncompressedBytes(op.payload().getCompound("stack"))); }
        return guarded ? EscrowItemFixtures.guarded(op) : op;
    }
    private static EscrowSettlementIntent intent(EscrowLedger root, EscrowOperation op, int missing) {
        int retained = (int) op.rocket().beforeEpoch();
        return op.kind().itemCargo() ? EscrowItemFixtures.intent(root, op, retained, missing, false)
                : EscrowCargoFixtures.intent(root, op, retained, missing, false);
    }
    private static boolean fits(EscrowOperation op) {
        try {
            EscrowSettlementCapacity.preflight(ledger(op), op.id());
            var repair = op.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); var root = ledger(repair);
            for (int missing = 0; missing <= 2; missing++) { intent(root, repair, missing).freeze(root); }
            return true;
        } catch (IllegalArgumentException refused) { return false; }
    }
    private static void measure(EscrowOperation original) {
        var op = original.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); var root = ledger(op);
        System.out.println("CARGO_MEASURE kind=" + op.kind() + " guarded=" + (op.guard().transfer() != null)
                + " level=" + op.rocket().level());
        EscrowItemCapacityTest.report("payload", op.payload());
        EscrowItemCapacityTest.report("unsettled_operation", EscrowCodec.operation(op));
        for (int missing = 0; missing <= 2; missing++) {
            var intent = intent(root, op, missing); var o1 = intent.freeze(root);
            var o3 = EscrowSettlementTombstones.install(o1, intent); var o4 = EscrowSettlementTombstones.release(o3, intent, 7);
            assertEquals(o4, EscrowCodec.decode(EscrowCodec.encode(o4)));
            EscrowItemCapacityTest.report("intent_" + missing, intent.encode());
            EscrowItemCapacityTest.report("operation_" + missing, EscrowCodec.operation(o1.records().get(0)));
            EscrowItemCapacityTest.report("receipt_" + missing, EscrowReceiptCodec.encode(o3.tombstones().get(0)));
            EscrowItemCapacityTest.report("O3_root_" + missing, EscrowCodec.encode(o3));
            EscrowItemCapacityTest.report("O4_root_" + missing, EscrowCodec.encode(o4));
        }
    }
}
