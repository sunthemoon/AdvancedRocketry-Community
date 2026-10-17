package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightAuthorityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static EscrowFlightAuthority.Launch launch(EscrowLedger root, int index) {
        UUID logical = id(200 + index);
        return new EscrowFlightAuthority.Launch(logical, id(99), id(300 + index), new RocketEscrowSection(root.id(), 2, List.of()),
                hash(2), hash(3), id(400 + index), id(500 + index), hash(4), hash(5), hash(6), hash(7),
                new EscrowGuard(logical, null, 1, hash(8)), Set.of());
    }

    @Test void ordinaryCancellationConsumesItsSerialAndCleanupCannotRearmOldAuthority() {
        var root = ledger(); var launch = launch(root, 1);
        var armed = EscrowFlightAuthority.arm(root, launch, 1);
        var exact = armed.armedFlights().get(0);
        assertEquals(1, exact.serial()); assertEquals(1, armed.issuedLaunchSerial());
        assertTrue(armed.rocketAuthorities().isEmpty()); assertTrue(armed.tombstones().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.prune(armed, exact));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(armed, launch, 1));
        var cancelled = EscrowFlightAuthority.finish(armed, exact, EscrowFlightTables.Outcome.CANCELLED,
                launch.source(), 2, launch.signature(), launch.invariant());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.finish(cancelled, exact,
                EscrowFlightTables.Outcome.CANCELLED, launch.source(), 2, launch.signature(), launch.invariant()));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.finish(cancelled, cancelled.armedFlights().get(0),
                EscrowFlightTables.Outcome.HANDED_OFF, launch.destination(), 0, hash(9), hash(10)));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(cancelled, launch(cancelled, 1), 1));
        var cleaned = EscrowFlightAuthority.prune(cancelled, cancelled.armedFlights().get(0));
        assertEquals(1, cleaned.issuedLaunchSerial()); assertTrue(cleaned.armedFlights().isEmpty());
        assertEquals(2, EscrowFlightAuthority.arm(cleaned, launch(cleaned, 2), 1).armedFlights().get(0).serial());
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        assertEquals(cancelled, EscrowCodec.decode(EscrowCodec.encode(cancelled)));
    }

    @Test void readyHandoffReplacesThePositiveIdentityWithoutEditingAnyReceipt() {
        var receipt = receipt(1); receipt.putBoolean("released", true); receipt.putLong("release_generation", 1);
        UUID logical = receipt.getList("guards", 10).getCompound(0).getUUID("rocket_id");
        var head = new EscrowFlightTables.Head(logical, id(99), receipt.getUUID("anchor_op"), 1, id(301), 2,
                EscrowSignatureKind.FUEL_VECTOR_V1, hash(2), hash(3), 0, 0, EscrowFlightTables.Outcome.READY);
        var root = new EscrowLedger(id(500), 0, 1, List.of(), List.of(), List.of(receipt), 0, List.of(head), List.of());
        var launch = new EscrowFlightAuthority.Launch(logical, id(99), id(301), new RocketEscrowSection(root.id(), 2, List.of()),
                hash(2), hash(3), id(401), id(501), hash(4), hash(5), hash(6), hash(7), new EscrowGuard(logical, null, 1, hash(8)), Set.of());
        var armed = EscrowFlightAuthority.arm(root, launch, 2); var exact = armed.armedFlights().get(0);
        assertEquals(head.anchor(), exact.anchor()); assertEquals(1, exact.releaseGeneration());
        assertEquals(1, armed.rocketAuthorities().get(0).issued()); assertEquals(0, armed.rocketAuthorities().get(0).completed());
        var finished = EscrowFlightAuthority.finish(armed, exact, EscrowFlightTables.Outcome.HANDED_OFF, id(501), 0, hash(9), hash(10));
        var successor = finished.rocketAuthorities().get(0);
        assertEquals(id(501), successor.physical()); assertEquals(0, successor.epoch());
        assertEquals(1, successor.issued()); assertEquals(1, successor.completed());
        assertEquals(root.tombstones(), finished.tombstones());
        assertTrue(EscrowSettlementTombstones.identityLocked(finished, logical, id(301)));
        assertFalse(EscrowSettlementTombstones.identityLocked(finished, logical, id(501)));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.finish(armed, exact,
                EscrowFlightTables.Outcome.HANDED_OFF, id(501), 1, hash(9), hash(10)));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.finish(armed, exact,
                EscrowFlightTables.Outcome.CANCELLED, id(301), 3, hash(2), hash(3)));
        var cleaned = EscrowFlightAuthority.prune(finished, finished.armedFlights().get(0));
        var refuelled = new EscrowFlightAuthority.Launch(logical, id(99), id(501), new RocketEscrowSection(root.id(), 1, List.of()),
                hash(11), hash(10), id(402), id(502), hash(12), hash(13), hash(14), hash(15), new EscrowGuard(logical, null, 2, hash(16)), Set.of(id(401)));
        assertEquals(2, EscrowFlightAuthority.arm(cleaned, refuelled, 2).armedFlights().get(0).serial());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(cleaned, launch, 2));
    }

    @Test void replayExclusionCoversEveryPersistedTransferReferenceAndCurrentDebit() {
        var root = ledger(); var launch = launch(root, 1);
        var debited = new EscrowFlightAuthority.Launch(launch.logical(), launch.owner(), launch.source(), launch.wrapper(),
                launch.signature(), launch.invariant(), launch.transfer(), launch.destination(), launch.binding(), launch.launch(),
                launch.sourceEnvelope(), launch.destinationEnvelope(), launch.journal(), Set.of(launch.transfer()));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(root, debited, 1));
        var op = operation(1); var protectedGuard = new EscrowGuard(op.guard().rocket(), launch.transfer(), 1, hash(50));
        var named = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(),
                op.payloadHash(), op.source(), op.destination(), protectedGuard);
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(ledger(named), launch, 1));
        var floor = floor(10); floor.putUUID("transfer_id", launch.transfer());
        var floored = new EscrowLedger(root.id(), 1, 0, List.of(), List.of(floor), List.of());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(floored, launch, 1));
        var receipt = receipt(1); var guards = receipt.getList("guards", 10);
        guards.getCompound(0).putUUID("transfer_id", launch.transfer());
        var retired = new EscrowLedger(root.id(), 0, 0, List.of(), List.of(), List.of(receipt));
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(retired, launch, 1));
        var other = launch(root, 2);
        var duplicateTransfer = new EscrowFlightAuthority.Launch(other.logical(), other.owner(), other.source(), other.wrapper(),
                other.signature(), other.invariant(), launch.transfer(), other.destination(), other.binding(), other.launch(),
                other.sourceEnvelope(), other.destinationEnvelope(), other.journal(), Set.of());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(
                EscrowFlightAuthority.arm(root, launch, 1), duplicateTransfer, 1));
    }

    @Test void sixtyFourRetainedEntriesAndSerialOverflowRefuseBeforeAnyMutation() {
        EscrowLedger root = ledger();
        for (int i = 1; i <= 64; i++) { root = EscrowFlightAuthority.arm(root, launch(root, i), 1); }
        var full = root; var before = EscrowCodec.encode(full);
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(full, launch(full, 65), 1));
        assertEquals(before, EscrowCodec.encode(full)); assertEquals(64, full.armedFlights().size());
        assertEquals(full, EscrowCodec.decode(before));
        var overflow = new EscrowLedger(full.id(), 0, 0, List.of(), List.of(), List.of(), Long.MAX_VALUE, List.of(), List.of());
        assertThrows(ArithmeticException.class, () -> EscrowFlightAuthority.arm(overflow, launch(overflow, 1), 1));
        assertEquals(Long.MAX_VALUE, overflow.issuedLaunchSerial()); assertTrue(overflow.armedFlights().isEmpty());
    }

    @Test void foreignNeutralMarkedAndUncleanedJournalSourcesCannotBeArmed() {
        var root = ledger(); var launch = launch(root, 1);
        for (var wrapper : List.of(RocketEscrowSection.neutral(), new RocketEscrowSection(id(999), 2, List.of()),
                new RocketEscrowSection(root.id(), 2, List.of(new EscrowPrefix.Marker(id(9), 1, "DESTINATION", 2))))) {
            var invalid = copy(launch, wrapper, launch.journal(), Set.of());
            assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(root, invalid, 1));
        }
        var named = copy(launch, launch.wrapper(), new EscrowGuard(launch.logical(), id(888), 1, hash(9)), Set.of());
        assertThrows(IllegalArgumentException.class, () -> EscrowFlightAuthority.arm(root, named, 1));
        assertEquals(0, root.issuedLaunchSerial()); assertTrue(root.armedFlights().isEmpty());
    }

    @Test void nativeDebitHistoryBoundIsAppliedBeforeAnyReservationOrWriterRequest() {
        var root = ledger(); var launch = launch(root, 1);
        var debits = java.util.stream.LongStream.range(10_000, 10_064).mapToObj(EscrowTestFixtures::id).collect(java.util.stream.Collectors.toSet());
        assertDoesNotThrow(() -> EscrowFlightAuthority.arm(root, copy(launch, launch.wrapper(), launch.journal(), debits), 1));
        debits.add(id(10_064));
        assertThrows(IllegalArgumentException.class, () -> copy(launch, launch.wrapper(), launch.journal(), debits));
        assertEquals(0, root.issuedLaunchSerial());
    }

    @Test void genericRecoveryAndTransactionsCannotConsumeAnArmedOrTerminalFlight() {
        var root = ledger(); var launch = launch(root, 1); var armed = EscrowFlightAuthority.arm(root, launch, 1);
        var cancelled = EscrowFlightAuthority.finish(armed, armed.armedFlights().get(0), EscrowFlightTables.Outcome.CANCELLED,
                launch.source(), 2, launch.signature(), launch.invariant());
        var source = new EscrowRecoveryFence.Rocket(launch.source(), 2, false, true);
        for (var held : List.of(armed, cancelled)) {
            assertTrue(EscrowRecoveryFence.known(held, launch.logical(), launch.transfer()));
            for (var action : io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.values()) {
                assertEquals("ARMED_AUTHORITY_REQUIRED", EscrowRecoveryFence.reason(held, launch.logical(), launch.transfer(), action, List.of(source), List.of()));
            }
            assertTrue(EscrowRecoveryFence.transaction(held, launch.logical(), false, List.of(source)));
            assertFalse(EscrowRecoveryFence.known(held, id(999), id(998)));
            assertNull(EscrowRecoveryFence.reason(held, id(999), id(998),
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction.KEEP_SOURCE, List.of(), List.of()));
        }
    }

    private static EscrowFlightAuthority.Launch copy(EscrowFlightAuthority.Launch value, RocketEscrowSection wrapper,
                                                    EscrowGuard journal, Set<UUID> debits) {
        return new EscrowFlightAuthority.Launch(value.logical(), value.owner(), value.source(), wrapper, value.signature(), value.invariant(),
                value.transfer(), value.destination(), value.binding(), value.launch(), value.sourceEnvelope(), value.destinationEnvelope(), journal, debits);
    }
}
