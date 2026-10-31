package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.nbt.CompoundTag;
import java.util.List;
import java.util.UUID;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6PublicationProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anotherValidSingletonTransferIsNotTheSelectedFixture() {
        var selected = GovernedTransferFixture.create().record();
        var expected = EscrowLoaderS6PublicationProbe.capturedIdentity(payload(selected), selected.sourceEntityId());
        var other = differentFlight();
        assertNotEquals(selected.transferId(), other.transferId());
        assertNotEquals(selected.sourceEntityId(), other.sourceEntityId());
        assertNotEquals(selected.nativeTransfer().orElseThrow().armed().destination(),
                other.nativeTransfer().orElseThrow().armed().destination());
        for (var cut : List.of(EscrowLoaderS6PublicationProbe.RETURN, EscrowLoaderS6PublicationProbe.HELD)) {
            int round = cut.equals(EscrowLoaderS6PublicationProbe.RETURN) ? 0 : 1;
            var destination = other.nativeTransfer().orElseThrow().armed().destination();
            var row = other.destinationSpawned(destination);
            if (round == 1) { row = row.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED); }
            var journal = new RocketTransferSavedData();
            try {
                journal.put(row);
                assertEquals(List.of(row), journal.entries());
                // Exercise the listener's selector, with every release gate enabled and a valid singleton journal.
                assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false,
                        destination, expected, journal.entries()),
                        "A different valid singleton flight must not be vetoed");
                assertTrue(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false,
                        destination, EscrowLoaderS6PublicationProbe.Identity.of(other), journal.entries()),
                        "The other flight is individually eligible when it is the selected fixture");
            } finally { journal.stop(); }
        }
    }

    private static RocketTransferRecord differentFlight() {
        var fixture = GovernedTransferFixture.create(); var base = fixture.record();
        var transfer = UUID.randomUUID(); var source = UUID.randomUUID(); var destination = UUID.randomUUID();
        var sourceEnvelope = fixture.source().copy(); sourceEnvelope.putUUID("physical", source);
        var destinationEnvelope = fixture.destination().copy(); destinationEnvelope.putUUID("physical", destination);
        var sourceFlight = RocketFlightNbtCodec.encode(base.sourceFlightData());
        var destinationFlight = destinationEnvelope.getCompound("flight");
        for (var flight : List.of(sourceFlight, destinationFlight)) {
            flight.putUUID("active_transfer_id", transfer); flight.getCompound("plan").putUUID("request_id", transfer);
        }
        destinationFlight.getCompound("fuel").getList("committed_debits", 10).getCompound(0).putUUID("transaction_id", transfer);
        var arm = base.nativeTransfer().orElseThrow().armed().encode();
        arm.putUUID("t", transfer); arm.putUUID("src", source); arm.putUUID("d", destination);
        arm.putByteArray("source", java.util.HexFormat.of().parseHex(EscrowGovernedEnvelope.digest(sourceEnvelope)));
        arm.putByteArray("dest", java.util.HexFormat.of().parseHex(EscrowGovernedEnvelope.digest(destinationEnvelope)));
        var transit = RocketFlightNbtCodec.decode(sourceFlight).data().orElseThrow();
        var descent = RocketFlightNbtCodec.decode(destinationFlight).data().orElseThrow();
        var envelope = EscrowTransferEnvelope.capture(EscrowFlightTables.Armed.decode(arm), sourceEnvelope, destinationEnvelope,
                base.sourceSnapshot(), base.destinationSnapshot(), transit, descent);
        return RocketTransferRecord.createGoverned(transfer,
                base.logicalRocketId(), base.ownerId(), source, base.sourceSnapshot(), base.destinationSnapshot(),
                transit, descent, base.requiredFuel(), base.createdAtGameTime(), envelope);
    }

    private static CompoundTag payload(RocketTransferRecord row) {
        var journal = new RocketTransferSavedData();
        try { journal.put(row); return journal.save(new CompoundTag()); }
        finally { journal.stop(); }
    }

    private static String encoded(EscrowLoaderS6PublicationProbe.Identity id) {
        return id.transfer() + "," + id.source() + "," + id.destination();
    }

    @Test void everyIdentityComponentIsRequiredBeforeSelectingTheSingleton() {
        var prepared = GovernedTransferFixture.create().record();
        var expected = EscrowLoaderS6PublicationProbe.capturedIdentity(payload(prepared), prepared.sourceEntityId());
        var row = prepared.destinationSpawned(expected.destination());
        var rows = List.of(row);
        assertTrue(EscrowLoaderS6PublicationProbe.target(true, true, EscrowLoaderS6PublicationProbe.RETURN,
                0, false, expected.destination(), expected, rows));
        for (var wrong : List.of(
                new EscrowLoaderS6PublicationProbe.Identity(UUID.randomUUID(), expected.source(), expected.destination()),
                new EscrowLoaderS6PublicationProbe.Identity(expected.transfer(), UUID.randomUUID(), expected.destination()),
                new EscrowLoaderS6PublicationProbe.Identity(expected.transfer(), expected.source(), UUID.randomUUID()))) {
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, EscrowLoaderS6PublicationProbe.RETURN,
                    0, false, expected.destination(), wrong, rows));
        }
        assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, EscrowLoaderS6PublicationProbe.RETURN,
                0, false, expected.destination(), null, rows));
        for (var others : List.of(List.<RocketTransferRecord>of(), List.of(row, differentFlight()))) {
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, EscrowLoaderS6PublicationProbe.RETURN,
                    0, false, expected.destination(), expected, others));
        }
    }

    @Test void f2CaptureMustBeValidatedPreparedAndOwnedByTheSelectedLoaderSource() {
        var row = GovernedTransferFixture.create().record(); var capture = payload(row); var before = capture.copy();
        var identity = EscrowLoaderS6PublicationProbe.capturedIdentity(capture, row.sourceEntityId());
        assertEquals(EscrowLoaderS6PublicationProbe.Identity.of(row), identity);
        assertEquals(before, capture);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.capturedIdentity(capture, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.capturedIdentity(
                payload(row.destinationSpawned(identity.destination())), row.sourceEntityId()));
        var corrupt = capture.copy(); corrupt.getList("transfers", 10).getCompound(0).putString("checksum", "0".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.capturedIdentity(corrupt, row.sourceEntityId()));
        capture.getList("transfers", 10).clear();
        assertEquals(row.transferId(), identity.transfer()); // Capturing does not retain a mutable NBT reference.
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.capturedIdentity(capture, row.sourceEntityId()));
    }

    @Test void recoveryArmsOnlyWhenCarriedF2IdentityMatchesCheckedNativeJournalAndLedger() {
        var prepared = GovernedTransferFixture.create().record();
        var expected = EscrowLoaderS6PublicationProbe.capturedIdentity(payload(prepared), prepared.sourceEntityId());
        var row = prepared.destinationSpawned(expected.destination()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        var journal = payload(row); var ledger = EscrowCodec.encode(EscrowGovernedDispatchTest.root(row));
        var journalBefore = journal.copy(); var ledgerBefore = ledger.copy();
        assertEquals(expected, EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(expected), journal, ledger));
        assertEquals(journalBefore, journal); assertEquals(ledgerBefore, ledger);
        var other = differentFlight(); var otherId = EscrowLoaderS6PublicationProbe.Identity.of(other);
        var otherRow = other.destinationSpawned(otherId.destination()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(expected),
                payload(otherRow), EscrowCodec.encode(EscrowGovernedDispatchTest.root(otherRow))));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(otherId), journal, ledger));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(expected),
                journal, EscrowCodec.encode(EscrowGovernedDispatchTest.root(otherRow))));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(expected), payload(prepared), ledger));
        ledger.getList("armed_flights", 10).clear();
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.recoveryIdentity(encoded(expected), journal, ledger));
    }

    @Test void carriedIdentityIsBoundedCanonicalAndComplete() {
        var id = EscrowLoaderS6PublicationProbe.Identity.of(GovernedTransferFixture.create().record());
        assertEquals(id, EscrowLoaderS6PublicationProbe.Identity.parse(encoded(id)));
        for (var invalid : List.of("", "1,2,3", encoded(id) + ",", encoded(id).replace(',', ':'),
                encoded(id).toUpperCase(java.util.Locale.ROOT), id.transfer() + "," + id.source() + "," + id.source())) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.Identity.parse(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6PublicationProbe.Identity.parse(null));
    }

    @Test void exactPublicationCasesReachTheRealLoaderFlightOnlyWithHooksEnabled() {
        for (var cut : List.of("LF_S6_PUBLICATION_RETURN", "LF_S6_PUBLICATION_HELD")) {
            assertTrue(EscrowLoaderS6FlightProbe.selected(true, cut));
            assertTrue(EscrowLoaderS6Probe.selected(true, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6FlightProbe.selected(true, cut + "_OTHER"));
        }
    }

    @Test void vetoRequiresExactReleaseRoundIdentityPhaseAndANewJoin() {
        var id = UUID.randomUUID(); var other = UUID.randomUUID();
        for (var cut : List.of(EscrowLoaderS6PublicationProbe.RETURN, EscrowLoaderS6PublicationProbe.HELD)) {
            int round = cut.equals(EscrowLoaderS6PublicationProbe.RETURN) ? 0 : 1;
            var phase = round == 0 ? RocketTransferPhase.DESTINATION_SPAWNED : RocketTransferPhase.PASSENGERS_TRANSFERRED;
            assertTrue(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(false, true, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, false, cut, round, false, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, true, id, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, other, id, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, null, phase));
            assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut + "_OTHER", round, false, id, id, phase));
            for (int wrong : List.of(-1, 2, 3, 1 - round)) {
                assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, wrong, false, id, id, phase));
            }
            for (var wrong : RocketTransferPhase.values()) {
                if (wrong != phase) {
                    assertFalse(EscrowLoaderS6PublicationProbe.target(true, true, cut, round, false, id, id, wrong));
                }
            }
        }
    }

    @Test void registeredRocketHasNoNullableFactoryOrExperimentalFeatureRequirement() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/registry/ModEntities.java"));
        assertTrue(source.contains("EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)"));
        assertFalse(source.contains("requiredFeatures("));
        var type = net.minecraft.world.entity.EntityType.Builder
                .<io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity>of(
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity::new,
                        net.minecraft.world.entity.MobCategory.MISC).build("arce_test:w220_factory");
        assertEquals(net.minecraft.world.flag.FeatureFlags.VANILLA_SET, type.requiredFeatures());
        assertTrue(type.isEnabled(net.minecraft.world.flag.FeatureFlags.VANILLA_SET));
        assertFalse(type.isEnabled(net.minecraft.world.flag.FeatureFlagSet.of()));
    }
}
