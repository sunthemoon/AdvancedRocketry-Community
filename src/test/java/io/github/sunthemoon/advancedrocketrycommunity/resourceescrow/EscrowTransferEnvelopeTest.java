package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowTransferEnvelopeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void immutableRecordExpansionHashesOnceAndAccessorsRemainCopiesAcrossEveryDescentTick() {
        var original = EscrowGovernedArrivalStateTest.record();
        var hashes = new java.util.concurrent.atomic.AtomicInteger();
        var metadata = EscrowTransferEnvelope.decode(original.nativeTransfer().orElseThrow().encode(), tag -> {
            hashes.incrementAndGet(); return EscrowNativeEnvelope.digest(tag);
        });
        var record = withMetadata(original, metadata);
        assertEquals(2, hashes.get());
        var frozen = metadata.destinationEnvelope(record);
        for (int elapsed = 0; elapsed <= RocketFlightLimits.DESCENT_TICKS; elapsed++) {
            int before = hashes.get();
            metadata.validate(record);
            var observed = EscrowGovernedArrivalStateTest.descent(record, 500, elapsed);
            assertTrue(EscrowGovernedArrivalState.matches(record, observed, 500 + elapsed));
            assertEquals(elapsed == RocketFlightLimits.DESCENT_TICKS,
                    EscrowGovernedArrival.ready(record, observed, 500 + elapsed));
            metadata.sourceEnvelope(record).getCompound("flight").putInt("external", 1);
            metadata.destinationEnvelope(record).getCompound("wrapper").putLong("resource_epoch", 99);
            assertEquals(frozen, metadata.destinationEnvelope(record));
            assertEquals(0, hashes.get() - before, "native envelope digests at descent tick " + elapsed);
        }
        var second = withMetadata(original, metadata);
        assertNotSame(record, second); assertEquals(4, hashes.get());
        metadata.validate(record); metadata.validate(second); assertEquals(4, hashes.get());
    }

    @Test void builtInDigestCounterMatchesAnInjectedCountingDigestForTheDispatcherGameTest() {
        var original = EscrowGovernedArrivalStateTest.record();
        var hashes = new java.util.concurrent.atomic.AtomicInteger();
        var metadata = EscrowTransferEnvelope.decode(original.nativeTransfer().orElseThrow().encode(), tag -> {
            hashes.incrementAndGet(); return EscrowNativeEnvelope.digest(tag);
        });
        assertEquals(0, metadata.envelopeDigests());
        var record = withMetadata(original, metadata);
        assertEquals(2, hashes.get()); assertEquals(hashes.get(), metadata.envelopeDigests());
        for (int tick = 0; tick < 10; tick++) { metadata.validate(record); metadata.destinationEnvelope(record); }
        assertEquals(2, metadata.envelopeDigests());
        withMetadata(original, metadata);
        assertEquals(4, hashes.get()); assertEquals(hashes.get(), metadata.envelopeDigests());
    }

    @Test void committedMatchingIsSkippedOnlyForCapturedIdleDestinationObservations() {
        for (var phase : RocketTransferPhase.values()) {
            for (boolean source : List.of(false, true)) {
                for (boolean captured : List.of(false, true)) {
                    for (boolean action : List.of(false, true)) {
                        assertEquals(phase != RocketTransferPhase.COMMITTED || source || !captured || action,
                                EscrowGovernedPhases.matchingRequired(phase, source, captured, action));
                    }
                }
            }
        }
    }

    private static RocketTransferRecord withMetadata(RocketTransferRecord r, EscrowTransferEnvelope metadata) {
        return RocketTransferRecord.restoreGoverned(r.transferId(), r.phase(), r.logicalRocketId(), r.ownerId(),
                r.sourceEntityId(), r.destinationEntityId().orElse(null), r.sourceSnapshot(), r.destinationSnapshot(),
                r.sourceFlightData(), r.destinationFlightData(), r.requiredFuel(), r.createdAtGameTime(), r.checksum(), metadata);
    }

    @Test void recordThreeRoundTripsCompleteEnvelopesWithIndependentJournalAndFlightSchemas() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var metadata = record.nativeTransfer().orElseThrow();
        assertEquals(3, record.schemaVersion()); assertEquals(2, record.sourceFlightData().schemaVersion());
        assertEquals(EscrowGovernedEnvelope.comparison(fixture.source()), metadata.sourceEnvelope(record));
        assertEquals(fixture.destination(), metadata.destinationEnvelope(record));
        assertEquals(7, metadata.sourceEnvelope(record).getCompound("wrapper").getLong("resource_epoch"));
        assertEquals(0, metadata.destinationEnvelope(record).getCompound("wrapper").getLong("resource_epoch"));
        var compact = metadata.encode();
        for (String side : List.of("source", "destination")) {
            assertFalse(compact.getCompound(side).contains("snapshot"));
            assertFalse(compact.getCompound(side).getCompound("flight_changes").contains("fuel"));
            assertFalse(compact.getCompound(side).getCompound("flight_changes").contains("passengers"));
        }
        for (RocketTransferPhase phase : RocketTransferPhase.values()) {
            if (phase == RocketTransferPhase.DESTINATION_SPAWNED) { record = record.destinationSpawned(metadata.armed().destination()); }
            else if (phase != RocketTransferPhase.PREPARED) { record = record.advance(phase); }
            // W44 owns the root-3 outbox; W11b2's record schema remains independent of it.
            var root = root(record); assertEquals(3, root.getInt("schema_version"));
            assertEquals(new RocketTransferSavedData().save(new CompoundTag()).getInt("schema_version"), root.getInt("schema_version"));
            var historical = root.copy(); historical.putInt("schema_version", 2);
            historical.putString("format_epoch", SavedDataSchemaMigrator.FORMAT_EPOCH);
            historical.remove(ManagedSavedDataType.FIRST_MOON_LANDING_KEY);
            assertEquals(2, historical.getInt("schema_version"));
            var migrated = RocketTransferSavedData.load(historical);
            assertTrue(migrated.operational()); assertEquals(root, migrated.save(new CompoundTag()));
            assertEquals(record.checksum(), migrated.entries().get(0).checksum());
            var loaded = RocketTransferSavedData.load(root); assertTrue(loaded.operational());
            var restored = loaded.entries().get(0);
            assertEquals(record.checksum(), restored.checksum()); assertEquals(phase, restored.phase());
            assertEquals(EscrowGovernedEnvelope.comparison(fixture.source()), restored.nativeTransfer().orElseThrow().sourceEnvelope(restored));
            assertEquals(root, loaded.save(new CompoundTag()));
            assertThrows(IllegalArgumentException.class, () -> RocketTransferRecord.restore(3,
                    restored.transferId(), restored.phase(), restored.logicalRocketId(), restored.ownerId(), restored.sourceEntityId(),
                    restored.destinationEntityId().orElse(null), restored.sourceSnapshot(), restored.destinationSnapshot(),
                    restored.sourceFlightData(), restored.destinationFlightData(), restored.requiredFuel(), restored.createdAtGameTime(), restored.checksum()));
        }
        assertThrows(IllegalArgumentException.class, () -> fixture.record().destinationSpawned(GovernedTransferFixture.id(500)));
        var spawned = fixture.record().destinationSpawned(metadata.armed().destination());
        assertThrows(IllegalArgumentException.class, () -> spawned.rebindDestination(GovernedTransferFixture.id(500)));
        assertEquals(spawned.checksum(), spawned.rebindDestination(metadata.armed().destination()).checksum());
    }

    @Test void metadataAndAllNestedAuthorityChangesPreserveTheRejectedRawJournal() {
        var fixture = GovernedTransferFixture.create();
        List<Consumer<CompoundTag>> changes = List.of(
                t -> t.putInt("schema_version", 2), t -> t.putString("signature_kind", "ROCKET_COMPOSITE_V1"),
                t -> t.getCompound("armed").putLong("serial", 9), t -> t.getCompound("armed").putLong("g", 11),
                t -> t.getCompound("armed").putUUID("ledger", GovernedTransferFixture.id(9)),
                t -> t.getCompound("armed").putUUID("d", GovernedTransferFixture.id(9)),
                t -> t.getCompound("armed").putLong("epoch", 8), t -> t.getCompound("armed").putString("outcome", "CANCELLED"),
                t -> t.getCompound("armed").putByteArray("binding", new byte[32]),
                t -> t.getCompound("armed").getCompound("j").putByteArray("journal_digest", new byte[32]),
                t -> t.getCompound("source").put("pos", GovernedTransferFixture.vector(9, 80, 8)),
                t -> t.getCompound("source").put("motion", GovernedTransferFixture.vector(0, 1, 0)),
                t -> t.getCompound("source").putBoolean("no_gravity", false),
                t -> t.getCompound("source").putBoolean("no_physics", false),
                t -> t.getCompound("source").putBoolean("invulnerable", true),
                t -> t.getCompound("source").putFloat("yaw", 10), t -> t.getCompound("source").putFloat("pitch", Float.NaN),
                t -> t.getCompound("source").put("passengers", new ListTag()),
                t -> t.getCompound("source").getCompound("flight_changes").putLong("state_started_game_time", 9),
                t -> t.getCompound("source").getCompound("flight_changes").put("fuel", new CompoundTag()),
                t -> t.getCompound("destination").putBoolean("on_ground", true),
                t -> t.getCompound("destination").getCompound("flight_changes").putString("state", "LANDED"),
                t -> t.putInt("unknown", 1));
        for (var change : changes) {
            var root = root(fixture.record()); change.accept(wire(root).getCompound("native_transfer")); assertRejected(root);
        }
        for (String key : fixture.record().nativeTransfer().orElseThrow().encode().getAllKeys()) {
            var root = root(fixture.record()); wire(root).getCompound("native_transfer").remove(key); assertRejected(root);
        }
        for (String side : List.of("source", "destination")) {
            for (String key : fixture.record().nativeTransfer().orElseThrow().encode().getCompound(side).getAllKeys()) {
                var root = root(fixture.record()); wire(root).getCompound("native_transfer").getCompound(side).remove(key); assertRejected(root);
            }
        }
        for (int schema : List.of(0, 1, 2, 4)) {
            var root = root(fixture.record()); wire(root).putInt("schema_version", schema); assertRejected(root);
        }
        var absent = root(fixture.record()); wire(absent).remove("native_transfer"); assertRejected(absent);
        var future = root(fixture.record()); future.putInt("schema_version", RocketTransferSavedData.ROOT_SCHEMA_VERSION + 1); assertRejected(future);
    }

    @Test void nativeProjectionCopiesInputsAndOutputsAndRetainsPriorIdlePlan() {
        var fixture = GovernedTransferFixture.create(1, 16, true); var record = fixture.record();
        var metadata = record.nativeTransfer().orElseThrow();
        assertEquals(EscrowGovernedEnvelope.comparison(fixture.source()), metadata.sourceEnvelope(record));
        int size = RocketNbtSize.uncompressedBytes(metadata.encode());
        assertTrue(size <= EscrowTransferEnvelope.MAX_BYTES);
        System.out.println("governed_metadata_16_passengers_prior_plan_bytes=" + size);
        var expected = metadata.encode(); fixture.source().putInt("external", 1);
        var encoded = metadata.encode(); encoded.getCompound("source").putInt("external", 1);
        metadata.sourceEnvelope(record).getCompound("flight").putInt("external", 1);
        assertEquals(expected, metadata.encode()); assertTrue(RocketTransferSavedData.load(root(record)).operational());
    }

    @Test void metadataBytePreflightRejectsOneOverTheUnchangedFourKibBoundaryBeforeCopying() {
        var metadata = GovernedTransferFixture.create().record().nativeTransfer().orElseThrow().encode();
        metadata.putByteArray("padding", new byte[0]);
        int padding = EscrowTransferEnvelope.MAX_BYTES - RocketNbtSize.uncompressedBytes(metadata);
        assertTrue(padding > 0); metadata.putByteArray("padding", new byte[padding]);
        assertEquals(EscrowTransferEnvelope.MAX_BYTES, RocketNbtSize.uncompressedBytes(metadata));
        assertEquals("Unknown escrow field", assertThrows(IllegalArgumentException.class, () -> EscrowTransferEnvelope.decode(metadata)).getMessage());
        metadata.putByteArray("padding", new byte[padding + 1]);
        assertEquals("Escrow NBT envelope exceeded", assertThrows(IllegalArgumentException.class, () -> EscrowTransferEnvelope.decode(metadata)).getMessage());
    }

    @Test void maximalIdentifiersManifestPriorPlanAndReadyAnchorFitAndHaveMeasuredFieldCosts() {
        var fixture = GovernedTransferFixture.maximal(); var record = fixture.record();
        assertEquals(8_192, RocketNbtSize.uncompressedBytes(record.sourceFlightData().fuel().propulsion().orElseThrow().primary().fluidTag()));
        assertEquals(8_192, RocketNbtSize.uncompressedBytes(record.sourceFlightData().fuel().propulsion().orElseThrow().oxidizer().fluidTag()));
        assertEquals(63, record.sourceFlightData().fuel().committedDebits().size());
        assertEquals(64, record.destinationFlightData().fuel().committedDebits().size());
        var metadata = record.nativeTransfer().orElseThrow(); var encoded = metadata.encode();
        var measured = io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt.measure(encoded, 4096, 16, 512);
        assertNotNull(measured); assertEquals(RocketNbtSize.uncompressedBytes(encoded), measured.bytes());
        long sum = 4; // Named compound root header and TAG_End.
        for (String key : encoded.getAllKeys().stream().sorted().toList()) {
            var field = new CompoundTag(); field.put(key, encoded.get(key).copy());
            int bytes = RocketNbtSize.uncompressedBytes(field) - 4; sum += bytes;
            System.out.println("governed_field=" + key + " bytes=" + bytes);
        }
        assertEquals(measured.bytes(), sum);
        System.out.println("governed_max_identifier_manifest_bytes=" + measured.bytes() + " nodes=" + measured.nodes());
        assertEquals(EscrowGovernedEnvelope.comparison(fixture.source()), metadata.sourceEnvelope(record));
        assertTrue(RocketTransferSavedData.load(root(record)).operational());
    }

    @Test void serialIsPartOfImmutableChecksumWhileNeutralRecordsKeepTheirOldWireShape() {
        var a = GovernedTransferFixture.create(1, 1, false).record();
        var b = GovernedTransferFixture.create(2, 1, false).record(); assertNotEquals(a.checksum(), b.checksum());
        var legacy = RocketTransferRecord.create(a.transferId(), a.logicalRocketId(), a.ownerId(), a.sourceEntityId(),
                a.sourceSnapshot(), a.destinationSnapshot(), a.sourceFlightData(), a.destinationFlightData(), a.requiredFuel(), a.createdAtGameTime());
        assertEquals(2, legacy.schemaVersion()); assertTrue(legacy.nativeTransfer().isEmpty());
        assertFalse(wire(root(legacy)).contains("native_transfer")); assertTrue(RocketTransferSavedData.load(root(legacy)).operational());
    }

    static CompoundTag root(RocketTransferRecord record) { var journal = new RocketTransferSavedData(); journal.put(record); return journal.save(new CompoundTag()); }
    static CompoundTag wire(CompoundTag root) { return root.getList("transfers", Tag.TAG_COMPOUND).getCompound(0); }
    private static void assertRejected(CompoundTag root) {
        var original = root.copy(); var loaded = RocketTransferSavedData.load(root);
        assertFalse(loaded.operational()); assertTrue(loaded.entries().isEmpty());
        assertEquals(original, loaded.preservedBlockedData().orElseThrow()); assertEquals(original, loaded.save(new CompoundTag()));
    }
}
