package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCodecIntersectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void exactNativePayloadAndReferencesRoundTripTogetherAtTheFullRootCounts() {
        var records = new ArrayList<EscrowOperation>();
        for (int i = 1; i <= 64; i++) {
            var original = EscrowTestFixtures.operation(i); var payload = original.payload();
            var nativeTag = new CompoundTag(); nativeTag.putByteArray("blob", new byte[0]); payload.put("Tag", nativeTag);
            for (int node = 0; node < 379; node++) { nativeTag.putByte("n" + node, (byte) 0); }
            nativeTag.putByteArray("blob", new byte[8_192 - RocketNbtSize.uncompressedBytes(payload)]);
            assertEquals(8_192, RocketNbtSize.uncompressedBytes(payload));
            var op = new EscrowOperation(original.id(), i, original.owner(), 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                    payload, RocketNativeResources.signature(payload), padded(original.source(), 512), padded(original.destination(), 512), original.guard());
            assertEquals(512, RocketNbtSize.uncompressedBytes(EscrowCodec.endpoint(op.source())));
            assertEquals(512, RocketNbtSize.uncompressedBytes(EscrowCodec.endpoint(op.destination())));
            assertEquals(op, EscrowCodec.operation(EscrowCodec.operation(op))); records.add(op);
            var tooMuch = payload.copy(); tooMuch.getCompound("Tag").putByteArray("blob", new byte[nativeTag.getByteArray("blob").length + 1]);
            assertThrows(IllegalArgumentException.class, () -> new EscrowOperation(original.id(), original.sequence(), original.owner(), 0,
                    EscrowOperation.Phase.PREPARED, EscrowReason.NONE, tooMuch, RocketNativeResources.signature(tooMuch), op.source(), op.destination(), op.guard()));
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.endpoint(padded(original.source(), 513)));
            var tooManyNodes = payload.copy(); tooManyNodes.getCompound("Tag").putByte("overflow", (byte) 0);
            assertThrows(IllegalArgumentException.class, () -> new EscrowOperation(original.id(), original.sequence(), original.owner(), 0,
                    EscrowOperation.Phase.PREPARED, EscrowReason.NONE, tooManyNodes, RocketNativeResources.signature(tooManyNodes), op.source(), op.destination(), op.guard()));
        }
        var floors = new ArrayList<CompoundTag>(); var tombstones = new ArrayList<CompoundTag>();
        for (int i = 1; i <= 256; i++) { floors.add(EscrowTestFixtures.floor(i)); }
        for (int i = 1; i <= 64; i++) { tombstones.add(exactTombstone(i)); }
        var ledger = new EscrowLedger(EscrowTestFixtures.id(500), 64, 1, records, floors, tombstones);
        var encoded = EscrowCodec.encode(ledger); assertEquals(ledger, EscrowCodec.decode(encoded));
        // The structural caps make a valid propulsion-only root/operation smaller than their outer envelopes.
        assertTrue(RocketNbtSize.uncompressedBytes(encoded) < EscrowLimits.ROOT_BYTES);
        assertTrue(RocketNbtSize.uncompressedBytes(EscrowCodec.operation(records.get(0))) < EscrowLimits.OP_BYTES);
        var guard = EscrowCodec.guard(records.get(0).guard()); assertEquals(records.get(0).guard(), EscrowCodec.guard(guard));
        guard.putInt("extra", 1); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.guard(guard));
    }
    @Test void tombstoneNestedCountAndByteLimitsIntersectRatherThanTruncating() {
        var exact = exactTombstone(1); assertEquals(2_048, RocketNbtSize.uncompressedBytes(exact));
        assertDoesNotThrow(() -> EscrowCodec.validateSection(exact, true));
        var missing = exact.getList("missing", 10).getCompound(0); missing.putString("type", missing.getString("type") + "a");
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(exact, true));
        for (String key : List.of("retired", "guards")) {
            var full = tombstone(2); var entries = new ListTag();
            for (int i = 0; i < 64; i++) {
                if (key.equals("guards")) { entries.add(EscrowCodec.guard(EscrowTestFixtures.operation(i + 1).guard())); }
                else { var entry = new CompoundTag(); entry.putUUID("op_id", EscrowTestFixtures.id(i + 1)); entries.add(entry); }
            }
            full.put(key, entries);
            assertEquals(64, full.getList(key, 10).size());
            // A count limit is not permission to exceed the independent 2 KiB section envelope.
            assertTrue(RocketNbtSize.uncompressedBytes(full) > 2_048);
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(full, true));
            entries.add(entries.getCompound(0).copy()); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(full, true));
        }
        var full = tombstone(3); ListTag survivors = new ListTag();
        for (int i = 0; i < 2; i++) { var entry = new CompoundTag(); entry.putUUID("identity", EscrowTestFixtures.id(i)); entry.putString("digest", EscrowTestFixtures.hash(i)); survivors.add(entry); }
        full.put("survivors", survivors); assertDoesNotThrow(() -> EscrowCodec.validateSection(full, true));
        survivors.add(survivors.getCompound(0).copy()); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(full, true));
    }
    @Test void endpointMarkerCountsAndEnvelopesHoldAtOneAndEightWithoutUnknownPadding() {
        for (boolean entity : new boolean[] {false, true}) {
            int count = entity ? 8 : 1; var persistent = markerFixture(count);
            var state = EscrowTestEndpoints.state(persistent, EscrowTestFixtures.id(500), entity);
            assertEquals(count, state.markers().size());
            assertTrue(RocketNbtSize.uncompressedBytes(persistent.getCompound("resource_escrow")) <= (entity ? 1024 : 256));
            assertThrows(IllegalArgumentException.class, () -> EscrowTestEndpoints.state(markerFixture(count + 1), EscrowTestFixtures.id(500), entity));
            var invalid = persistent.copy(); invalid.getCompound("resource_escrow").putByteArray("padding", new byte[entity ? 1024 : 256]);
            assertThrows(IllegalArgumentException.class, () -> EscrowTestEndpoints.state(invalid, EscrowTestFixtures.id(500), entity));
        }
        for (int i = 0; i < 256; i++) { assertDoesNotThrow(() -> EscrowCodec.validateSection(EscrowTestFixtures.floor(1), false)); }
        var floor = EscrowTestFixtures.floor(1); floor.putByteArray("padding", new byte[1024]);
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(floor, false));
    }
    private static CompoundTag markerFixture(int count) {
        var persistent = new CompoundTag(); var fixture = new CompoundTag(); fixture.putUUID("identity", EscrowTestFixtures.id(1));
        fixture.putInt("amount", 1000); fixture.putInt("capacity", 16000); persistent.put("arce_escrow_fixture", fixture);
        var section = new CompoundTag(); section.putInt("schema_version", 1); section.putUUID("ledger_id", EscrowTestFixtures.id(500));
        section.putLong("resource_epoch", count); var markers = new ListTag();
        for (int i = 1; i <= count; i++) { var marker = new CompoundTag(); marker.putUUID("op_id", EscrowTestFixtures.id(i));
            marker.putLong("seq", i); marker.putString("role", "DESTINATION"); marker.putLong("epoch_after", i); markers.add(marker); }
        section.put("markers", markers); persistent.put("resource_escrow", section); return persistent;
    }
    private static EscrowEndpoint padded(EscrowEndpoint endpoint, int limit) {
        endpoint = new EscrowEndpoint(endpoint.kind(), "x:" + "l".repeat(126), endpoint.identity(), endpoint.logicalRocket(), endpoint.type(),
                endpoint.x(), endpoint.y(), endpoint.z(), endpoint.unit(), endpoint.beforeEpoch(), endpoint.beforeSignature(), endpoint.afterSignature());
        int extra = limit - RocketNbtSize.uncompressedBytes(EscrowCodec.endpoint(endpoint));
        assertTrue(endpoint.type().length() + extra <= 128, "Endpoint base bytes=" + RocketNbtSize.uncompressedBytes(EscrowCodec.endpoint(endpoint)) + " padding=" + extra);
        return new EscrowEndpoint(endpoint.kind(), endpoint.level(), endpoint.identity(), endpoint.logicalRocket(), endpoint.type() + "a".repeat(extra),
                endpoint.x(), endpoint.y(), endpoint.z(), endpoint.unit(), endpoint.beforeEpoch(), endpoint.beforeSignature(), endpoint.afterSignature());
    }
    private static CompoundTag tombstone(int id) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putUUID("op_id", EscrowTestFixtures.id(id));
        tag.putInt("amount", 1_000); tag.putString("payload_hash", EscrowTestFixtures.hash(id)); tag.putLong("audited_loss", 0);
        for (String key : List.of("retired", "missing", "guards", "survivors")) { tag.put(key, new ListTag()); } return tag;
    }
    private static CompoundTag exactTombstone(int id) {
        // Search only bounded canonical combinations, not unknown-field padding.
        for (int count = 0; count <= 64; count++) {
            var tag = tombstone(id); var retired = new ListTag();
            for (int i = 0; i < count; i++) { var entry = new CompoundTag(); entry.putUUID("op_id", EscrowTestFixtures.id(i + 1)); retired.add(entry); }
            tag.put("retired", retired); var missing = new ListTag();
            missing.add(EscrowCodec.endpoint(EscrowTestFixtures.operation(1).source())); missing.add(EscrowCodec.endpoint(EscrowTestFixtures.operation(2).source())); tag.put("missing", missing);
            int extra = 2_048 - RocketNbtSize.uncompressedBytes(tag);
            var endpoint = missing.getCompound(0); String type = endpoint.getString("type");
            if (extra < 0 || type.length() + extra > 128 || RocketNbtSize.uncompressedBytes(endpoint) + extra > 512) { continue; }
            endpoint.putString("type", type + "a".repeat(extra));
            EscrowCodec.validateSection(tag, true); return tag;
        }
        throw new AssertionError("No canonical exact-limit tombstone fixture");
    }
}
