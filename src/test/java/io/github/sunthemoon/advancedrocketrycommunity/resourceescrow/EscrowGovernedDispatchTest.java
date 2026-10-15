package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedDispatchTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    static EscrowLedger root(RocketTransferRecord record) {
        var arm = record.nativeTransfer().orElseThrow().armed();
        return new EscrowLedger(arm.ledger(), 1, arm.generation(), List.of(), List.of(), List.of(), arm.serial(), List.of(), List.of(arm));
    }

    static EscrowLedger arm(EscrowLedger root, java.util.function.Consumer<net.minecraft.nbt.CompoundTag> change) {
        var tag = root.armedFlights().get(0).encode(); change.accept(tag);
        return new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), root.records(), root.floors(), root.tombstones(),
                Math.max(root.issuedLaunchSerial(), tag.getLong("serial")), root.rocketAuthorities(), List.of(EscrowFlightTables.Armed.decode(tag)));
    }

    @Test void exactActiveRecordRoutesWithoutReceiptButMissingArmNeverBecomesLegacy() {
        var record = GovernedTransferFixture.create().record(); var root = root(record);
        assertEquals(EscrowGovernedDispatch.Route.ACTIVE, EscrowGovernedDispatch.decide(root, record).route());
        assertEquals("GOVERNED_ARM_MISSING", EscrowGovernedDispatch.decide(EscrowLedger.empty(root.id()), record).reason());
        assertEquals("GOVERNED_LEDGER_MISMATCH", EscrowGovernedDispatch.decide(EscrowLedger.empty(GovernedTransferFixture.id(90)), record).reason());
        assertEquals("GOVERNED_ARM_MISMATCH", EscrowGovernedDispatch.decide(new EscrowLedger(root.id(), 1, 9,
                List.of(), List.of(), List.of(), 1, List.of(), root.armedFlights()), record).reason());
    }

    @Test void everyArmBindingAndSerialMustMatchNotJustTheTransferId() {
        var record = GovernedTransferFixture.create().record(); var root = root(record);
        for (String field : List.of("o", "src", "t", "d")) {
            assertEquals("GOVERNED_ARM_MISMATCH", EscrowGovernedDispatch.decide(
                    arm(root, tag -> tag.putUUID(field, GovernedTransferFixture.id(900))), record).reason(), field);
        }
        for (String field : List.of("epoch", "serial", "g")) {
            assertEquals("GOVERNED_ARM_MISMATCH", EscrowGovernedDispatch.decide(
                    arm(root, tag -> tag.putLong(field, tag.getLong(field) + 1)), record).reason(), field);
        }
        for (String field : List.of("binding", "launch", "source", "dest")) {
            assertEquals("GOVERNED_ARM_MISMATCH", EscrowGovernedDispatch.decide(
                    arm(root, tag -> tag.putByteArray(field, new byte[32])), record).reason(), field);
        }
    }

    @Test void terminalRecordsAreCleanupOnlyAndHandoffNeedsCommittedPhase() {
        var record = GovernedTransferFixture.create().record(); var root = root(record);
        var cancelled = arm(root, tag -> tag.putString("outcome", "CANCELLED"));
        assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(cancelled, record).route());
        var handedOff = arm(root, tag -> tag.putString("outcome", "HANDED_OFF"));
        assertEquals("GOVERNED_TERMINAL_PHASE_MISMATCH", EscrowGovernedDispatch.decide(handedOff, record).reason());
        var committed = committed(record);
        assertEquals(EscrowGovernedDispatch.Route.CLEANUP, EscrowGovernedDispatch.decide(handedOff, committed).route());
        assertEquals(EscrowGovernedDispatch.Route.ACTIVE, EscrowGovernedDispatch.decide(root, committed).route(),
                "COMMITTED alone is not the F7 handoff");
    }

    static RocketTransferRecord committed(RocketTransferRecord record) {
        return record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
    }

    @Test void checkedBaselineAndPhaseLeaseAreRequiredAndAReplacedPhaseInvalidatesAuthority() throws Exception {
        var record = GovernedTransferFixture.create().record(); var root = root(record);
        var journal = new RocketTransferSavedData(); journal.put(record);
        var dispatch = new EscrowGovernedDispatch();
        try {
            assertEquals("GOVERNED_JOURNAL_UNACKNOWLEDGED", dispatch.classify(() -> root, () -> true, journal, record).reason());
            assertNull(dispatch.authority(() -> root, () -> true, journal, record));
            acknowledge(journal);
            var authority = dispatch.authority(() -> root, () -> true, journal, record);
            assertNotNull(authority); assertTrue(authority.valid());
            assertSame(authority, dispatch.authority(() -> root, () -> true, journal, record));
            journal.put(record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination()));
            assertFalse(authority.valid());
            acknowledge(journal);
            assertNull(dispatch.authority(() -> root, () -> true, journal, record), "A stale callback must not use the new checked phase");
            assertNotNull(dispatch.authority(() -> root, () -> true, journal, journal.find(record.transferId()).orElseThrow()));
        } finally { dispatch.close(); journal.stop(); }
    }

    @Test void rootAndAvailabilityChangesJoinsAndShutdownInvalidateCachedViews() throws Exception {
        var record = GovernedTransferFixture.create().record(); var root = new AtomicReference<>(root(record));
        var available = new AtomicBoolean(true); var journal = new RocketTransferSavedData(); journal.put(record);
        var dispatch = new EscrowGovernedDispatch();
        try {
            acknowledge(journal);
            var authority = dispatch.authority(root::get, available::get, journal, record);
            available.set(false); assertFalse(authority.valid());
            assertEquals("GOVERNED_LEDGER_UNAVAILABLE", dispatch.classify(root::get, available::get, journal, record).reason());
            available.set(true); root.set(root.get().generation(11)); assertFalse(authority.valid());
            authority = dispatch.authority(root::get, available::get, journal, record);
            dispatch.invalidated(record.logicalRocketId()); assertFalse(authority.valid());
            authority = dispatch.authority(root::get, available::get, journal, record);
            dispatch.close(); assertFalse(authority.valid());
            assertNull(dispatch.authority(root::get, available::get, journal, record));
        } finally { dispatch.close(); journal.stop(); }
    }

    @Test void exactTransferFloorsAndReceiptGuardsRefuseEvenWhenTheyNameAnotherLogicalRocket() {
        var record = GovernedTransferFixture.create().record(); var root = root(record);
        var floor = EscrowTestFixtures.floor(1); floor.putUUID("transfer_id", record.transferId());
        var floored = new EscrowLedger(root.id(), 1, 10, List.of(), List.of(floor), List.of(), 1, List.of(), root.armedFlights());
        assertEquals("GOVERNED_EXACT_TRANSFER_FLOOR", EscrowGovernedDispatch.decide(floored, record).reason());
        var receipt = EscrowTestFixtures.receipt(1);
        receipt.getList("guards", 10).getCompound(0).putUUID("transfer_id", record.transferId());
        var retired = new EscrowLedger(root.id(), 1, 10, List.of(), List.of(), List.of(receipt), 1, List.of(), root.armedFlights());
        assertEquals("GOVERNED_RETIRED_AUTHORITY", EscrowGovernedDispatch.decide(retired, record).reason());
    }

    @Test void optionalReadyHeadRequiresItsExactFrontierAnchorAndCompletedWatermark() {
        var fixture = GovernedTransferFixture.create(); var original = fixture.record();
        var tag = original.nativeTransfer().orElseThrow().armed().encode(); tag.putUUID("a", GovernedTransferFixture.id(9)); tag.putLong("release", 9);
        var arm = EscrowFlightTables.Armed.decode(tag);
        var nativeTransfer = EscrowTransferEnvelope.capture(arm, fixture.source(), fixture.destination(), original.sourceSnapshot(),
                original.destinationSnapshot(), original.sourceFlightData(), original.destinationFlightData());
        var record = RocketTransferRecord.createGoverned(original.transferId(), original.logicalRocketId(), original.ownerId(), original.sourceEntityId(),
                original.sourceSnapshot(), original.destinationSnapshot(), original.sourceFlightData(), original.destinationFlightData(),
                original.requiredFuel(), original.createdAtGameTime(), nativeTransfer);
        var receipt = EscrowTestFixtures.receipt(1); receipt.putUUID("op_id", arm.anchor()); receipt.putUUID("anchor_op", arm.anchor());
        receipt.getList("retired", 10).getCompound(0).putUUID("op_id", arm.anchor());
        var guards = new ListTag(); guards.add(EscrowCodec.guard(new EscrowGuard(arm.logical(), null, 1, "3".repeat(64))));
        receipt.put("guards", guards); receipt.putBoolean("released", true); receipt.putLong("release_generation", 9);
        var head = new EscrowFlightTables.Head(arm.logical(), arm.owner(), arm.anchor(), 9, arm.source(), arm.epoch(),
                EscrowSignatureKind.FUEL_VECTOR_V1, record.sourceFlightData().fuel().resourceSignature(),
                EscrowInvariant.rocket(arm.source(), arm.logical(), arm.owner(), EscrowAdapterIds.ROCKET, record.sourceSnapshot()),
                1, 0, EscrowFlightTables.Outcome.READY);
        var root = new EscrowLedger(arm.ledger(), 1, 10, List.of(), List.of(), List.of(receipt), 1, List.of(head), List.of(arm));
        assertEquals(EscrowGovernedDispatch.Route.ACTIVE, EscrowGovernedDispatch.decide(root, record).route());
        for (String field : List.of("s", "i")) {
            var changed = head.encode(); changed.putByteArray(field, new byte[32]);
            var invalid = new EscrowLedger(root.id(), 1, 10, List.of(), List.of(), root.tombstones(), 1,
                    List.of(EscrowFlightTables.Head.decode(changed)), List.of(arm));
            assertEquals("GOVERNED_READY_MISMATCH", EscrowGovernedDispatch.decide(invalid, record).reason(), field);
        }
        var completed = head.encode(); completed.putLong("completed", 1);
        assertEquals("GOVERNED_READY_MISMATCH", EscrowGovernedDispatch.decide(new EscrowLedger(root.id(), 1, 10,
                List.of(), List.of(), root.tombstones(), 1, List.of(EscrowFlightTables.Head.decode(completed)), List.of(arm)), record).reason());
    }

    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
}
