package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R40: real admission and settlement encoders, without changing any persisted allowance. */
class EscrowReceiptChargeTest {
    static final List<String> LEVELS = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end",
            "advancedrocketrycommunity:moon", "advancedrocketrycommunity:mars", "advancedrocketrycommunity:space",
            "advancedrocketrycommunity:venus", "advancedrocketrycommunity:tau_ceti_f", "advancedrocketrycommunity:tau_ceti_g");
    private static final List<EscrowOperation.Kind> KINDS = List.of(EscrowOperation.Kind.ITEM_CARGO_LOAD,
            EscrowOperation.Kind.ITEM_CARGO_UNLOAD, EscrowOperation.Kind.FLUID_CARGO_LOAD,
            EscrowOperation.Kind.FLUID_CARGO_UNLOAD, EscrowOperation.Kind.FLUID_PROPULSION_FILL);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void everyRegisteredLevelAndTransferKindFitsEverySettlementChoice() {
        int worstCargo = 0, worstFuel = 0, cases = 0;
        for (String level : LEVELS) {
            for (var kind : KINDS) {
                for (boolean guarded : List.of(false, true)) {
                    int retained = kind.cargo() ? 0 : 4;
                    var op = operation(kind, level, guarded, retained);
                    assertQueued(op, retained, "case-" + cases++);
                    var repair = op.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH); var root = ledger(repair);
                    for (int missing = 0; missing <= 2; missing++) {
                        var intent = intent(root, repair, retained, missing, false, false);
                        assertSettlement(root, repair, intent, missing);
                    }
                    // Native neutral normalization has no retained markers; include both ABANDON directions.
                    var neutralOp = operation(kind, level, guarded, 0).withReason(EscrowReason.JOURNAL_GUARD_MISMATCH);
                    var neutralRoot = ledger(neutralOp);
                    for (int missing = 0; missing <= 2; missing++) {
                        var intent = intent(neutralRoot, neutralOp, 0, missing, true, false);
                        if (missing != 1) {
                            var rocket = intent.observations().stream().filter(o -> o.endpoint().equals(neutralOp.rocket())).findFirst().orElseThrow();
                            assertFalse(rocket.bound()); assertTrue(rocket.checkpointed().bound());
                            assertEquals(new RocketEscrowSection(root.id(), 0, List.of()), rocket.normalizedWrapper());
                        }
                        assertSettlement(neutralRoot, neutralOp, intent, missing);
                    }
                    // Admission conservatively uses DESTINATION for the rocket marker, including unloads (+5 bytes).
                    var sizing = intent(root, repair, retained, 0, false, true);
                    int bytes = RocketNbtSize.uncompressedBytes(EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(repair, sizing)));
                    assertTrue(bytes <= 2_048);
                    if (kind.cargo()) { worstCargo = Math.max(worstCargo, bytes); }
                    else { worstFuel = Math.max(worstFuel, bytes); }
                    System.out.println("RECEIPT_CHARGE kind=" + kind + " level=" + level + " guarded=" + guarded
                            + " markers=" + (retained + 1) + " bytes=" + bytes + " margin=" + (2_048 - bytes));
                }
            }
        }
        assertEquals(90, cases); assertEquals(1_557, worstCargo); assertEquals(1_833, worstFuel);
        assertEquals(491, 2_048 - worstCargo); assertEquals(215, 2_048 - worstFuel);
    }

    @Test void guardedFuelWithNoRetainedMarkersReallyPreparesOnEveryModLevel() {
        for (String level : LEVELS.stream().filter(value -> value.startsWith("advancedrocketrycommunity:")).toList()) {
            assertQueued(fill(100, 99, 0, 0, level, true, -1), 0, level.substring(level.indexOf(':') + 1));
        }
    }

    @Test void admittedGuardedCargoDoesNotBlockAnUnrelatedRepairFreeze() throws Exception {
        // Near the complete-operation byte edge: a five-marker shared preview cannot fit this admitted cargo.
        var op = EscrowCargoFixtures.guarded(EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                100, 99, 0, 7_600, "advancedrocketrycommunity:tau_ceti_g"));
        var unrelated = fill(1, 98, 0, 0, "minecraft:overworld", false, -1).withReason(EscrowReason.OTHER_STATE);
        var root = new EscrowLedger(id(500), 99, 0, List.of(unrelated), List.of(), List.of());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root)); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var replies = new ArrayList<Boolean>();
            assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(new EscrowNativeBinding.Admission(op,
                    RocketEscrowSection.neutral()), replies::add));
            assertTrue(coordinator.commitPreparations()); io.tickUntil(coordinator::tick, 1, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(unrelated, op), coordinator.ledger().records());
            var intent = intent(coordinator.ledger(), unrelated, 0, 0, false, false);
            var frozen = assertDoesNotThrow(() -> intent.freeze(coordinator.ledger()));
            assertEquals(op, frozen.records().get(1)); assertEquals(1, intent.ids().size());
            assertSettlement(coordinator.ledger(), unrelated, intent, 0);
        }
    }

    private void assertQueued(EscrowOperation op, int retained, String name) {
        var root = new EscrowLedger(id(500), 99, 0, List.of(), List.of(), List.of());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve(name), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareTransfer(
                    new EscrowNativeBinding.Admission(op, EscrowCargoFixtures.wrapper(retained)), ignored -> fail("No write submitted")));
            assertEquals(101, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
            assertEquals(root, coordinator.ledger()); assertTrue(data.isDirty()); assertTrue(writer.capturedOperations().isEmpty());
        }
    }

    private static void assertSettlement(EscrowLedger root, EscrowOperation op, EscrowSettlementIntent intent, int missing) {
        var o1 = intent.freeze(root); var stored = EscrowSettlementIntent.decode(o1.records().stream()
                .filter(member -> member.id().equals(op.id())).findFirst().orElseThrow().settlement());
        assertEquals(intent, stored);
        if (op.kind().cargo() && missing != 1) {
            var rocket = stored.observations().stream().filter(o -> o.endpoint().equals(op.rocket())).findFirst().orElseThrow();
            if (rocket.bound()) {
                assertEquals(1, rocket.state().markers().size()); assertEquals(op.id(), rocket.state().markers().get(0).operation());
            }
        }
        var o3 = EscrowSettlementTombstones.install(o1, intent); var o4 = EscrowSettlementTombstones.release(o3, intent, 7);
        for (var stage : List.of(o1, o3, o4)) {
            var encoded = EscrowCodec.encode(stage);
            assertEquals(stage, EscrowCodec.decode(encoded)); assertEquals(encoded, EscrowCodec.encode(EscrowCodec.decode(encoded)));
        }
        assertFalse(o3.tombstones().get(0).getBoolean("released")); assertTrue(o4.tombstones().get(0).getBoolean("released"));
        assertEquals(missing == 1 ? 0 : 1, o4.rocketAuthorities().size());
    }

    static EscrowOperation fill(long sequence, long logical, long epoch, int retained, String level, boolean guarded, int blob) {
        var payload = EscrowCargoFixtures.fluid(blob);
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, level, id(1_000 + sequence), null, EscrowAdapterIds.LOADER,
                0, 64, 0, EscrowEndpoint.Unit.TANK, 0, hash(30), hash(31));
        var rocket = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level, id(2_000 + logical), id(logical), EscrowAdapterIds.ROCKET,
                0, 0, 0, EscrowEndpoint.Unit.PRIMARY, epoch, hash(20 + epoch), hash(21 + epoch));
        var op = new EscrowOperation(id(sequence), sequence, id(99), 1, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), source, rocket,
                new EscrowGuard(id(logical), guarded ? id(800_000 + logical) : null, 1, hash(400)));
        return nativeModel(op, EscrowCargoFixtures.wrapper(retained));
    }

    private static EscrowOperation operation(EscrowOperation.Kind kind, String level, boolean guarded, int retained) {
        if (!kind.cargo()) { return fill(100, 99, retained, retained, level, guarded, -1); }
        var op = kind.itemCargo() ? EscrowItemFixtures.operation(kind, 100, 99, retained, -1, level)
                : EscrowCargoFixtures.operation(kind, 100, 99, retained, -1, level);
        return guarded ? EscrowCargoFixtures.guarded(op) : op;
    }

    private static EscrowSettlementIntent intent(EscrowLedger root, EscrowOperation op, int retained, int missing,
                                                  boolean neutral, boolean sizing) {
        var observed = op.kind().itemCargo() ? EscrowItemFixtures.observations(root, op, retained, missing, neutral)
                : EscrowCargoFixtures.observations(root, op, retained, missing, neutral);
        if (sizing) {
            observed = observed.stream().map(o -> {
                if (!o.endpoint().equals(op.rocket())) { return o; }
                var markers = o.state().markers().stream().map(m -> new EscrowPrefix.Marker(m.operation(), m.sequence(),
                        "DESTINATION", m.epochAfter())).toList();
                return new EscrowSettlementIntent.Observation(o.endpoint(), new EscrowPrefix.State(root.id(), o.state().epoch(),
                        o.state().signature(), markers), o.bound(), o.quantity(), o.signatureKind(), o.invariantDigest());
            }).toList();
        }
        var preview = EscrowSettlementIntent.preview(root, op.id(), missing == 0 ? EscrowSettlementIntent.Choice.ACCEPT_CURRENT
                : EscrowSettlementIntent.Choice.ABANDON, id(700), "~".repeat(16), 10, observed);
        return preview.confirm(preview.actor(), preview.digest(), missing == 0 ? 11 : 210, observed);
    }
}
