package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void roundTripAndEveryMutableBoundaryIsDetached() {
        var ledger = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1));
        var encoded = EscrowCodec.encode(ledger); assertEquals(ledger, EscrowCodec.decode(encoded));
        encoded.getList("records", 10).getCompound(0).getCompound("payload").putInt("Amount", 0);
        assertEquals(1_000, ledger.records().get(0).payload().getInt("Amount"));
        var returned = ledger.records().get(0).payload(); returned.putInt("Amount", 0);
        assertEquals(1_000, ledger.records().get(0).payload().getInt("Amount"));
    }
    @Test void allUnsupportedFieldsTypesKindsHashesAndEpochsRefuse() {
        var root = EscrowCodec.encode(EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)));
        for (java.util.function.Consumer<CompoundTag> mutation : List.<java.util.function.Consumer<CompoundTag>>of(
                tag -> tag.putInt("schema_version", 3), tag -> tag.putInt("schema_version", 1), tag -> tag.putString("format_epoch", "other"),
                tag -> tag.putString("issued_through", "1"), tag -> tag.putLong("issued_through", 0),
                tag -> tag.putInt("unknown", 0), tag -> tag.remove("ledger_id"),
                tag -> record(tag).putString("kind", "ITEM_CARGO_LOAD"),
                tag -> record(tag).putString("payload_hash", EscrowTestFixtures.hash(300)),
                tag -> record(tag).getCompound("destination").putLong("after_epoch", 3),
                tag -> record(tag).getCompound("payload").putInt("Amount", 1_001),
                tag -> record(tag).getCompound("payload").putInt("Amount", 0),
                tag -> record(tag).putLong("seq", 0), tag -> record(tag).putLong("created_tick", -1),
                tag -> record(tag).putString("reason", "MISSING_ENDPOINT"))) {
            CompoundTag bad = root.copy(); mutation.accept(bad); assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(bad));
        }
    }
    @Test void operationRocketDeviceFloorAndTombstoneCountsAreHardLimits() {
        var records = new ArrayList<EscrowOperation>(); for (int i = 1; i <= 64; i++) { records.add(EscrowTestFixtures.operation(i)); }
        var floors = new ArrayList<CompoundTag>(); for (int i = 1; i <= 256; i++) { floors.add(EscrowTestFixtures.floor(i)); }
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 64, 0, records, floors, List.of());
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        records.add(EscrowTestFixtures.operation(65)); assertThrows(IllegalArgumentException.class,
                () -> new EscrowLedger(root.id(), 65, 0, records, floors, List.of())); records.remove(64);
        floors.add(EscrowTestFixtures.floor(257)); assertThrows(IllegalArgumentException.class,
                () -> new EscrowLedger(root.id(), 64, 0, records, floors, List.of()));
        var chain = new ArrayList<EscrowOperation>(); for (int i = 1; i <= 8; i++) { chain.add(EscrowTestFixtures.operation(i, 99, i - 1)); }
        assertEquals(8, new EscrowLedger(root.id(), 8, 0, chain, List.of(), List.of()).records().size());
        chain.add(EscrowTestFixtures.operation(9, 99, 8)); assertThrows(IllegalArgumentException.class,
                () -> new EscrowLedger(root.id(), 9, 0, chain, List.of(), List.of()));
        var op = EscrowTestFixtures.operation(1); assertThrows(IllegalArgumentException.class, () -> EscrowTestFixtures.ledger(op, op));
        ListTag oversized = new ListTag(); for (int i = 0; i < 65; i++) { oversized.add(new CompoundTag()); }
        CompoundTag bad = EscrowCodec.encode(EscrowTestFixtures.ledger()); bad.put("tombstones", oversized);
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.decode(bad));
        var tombstones = new ArrayList<CompoundTag>();
        for (int i = 1; i <= 64; i++) {
            tombstones.add(EscrowTestFixtures.receipt(i));
        }
        var full = new EscrowLedger(root.id(), 64, 0, root.records(), root.floors(), tombstones);
        assertEquals(full, EscrowCodec.decode(EscrowCodec.encode(full)));
        tombstones.add(tombstones.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(root.id(), 64, 0,
                root.records(), root.floors(), tombstones));
    }
    /** Review W11b1 finding 8: one record per source device is accepted; a second, distinct operation is refused. */
    @Test void oneOperationPerDeviceIsTheLimitForDistinctOperations() {
        var first = EscrowTestFixtures.operation(1); var other = EscrowTestFixtures.operation(2);
        var source = first.source(); // Same device identity at another position, so the endpoint chains stay separate.
        var moved = new EscrowEndpoint(source.kind(), source.level(), source.identity(), null, source.type(), source.x() + 32,
                source.y(), source.z(), source.unit(), 0, other.source().beforeSignature(), other.source().afterSignature());
        var second = new EscrowOperation(other.id(), other.sequence(), other.owner(), other.createdTick(), other.phase(), other.reason(),
                other.payload(), other.payloadHash(), moved, other.destination(), other.guard());
        assertNotEquals(first.id(), second.id()); assertNotEquals(first.source().key(), second.source().key());
        assertEquals(List.of(first, other), EscrowTestFixtures.ledger(first, other).records());
        assertEquals(List.of(second), EscrowTestFixtures.ledger(second).records());
        var refused = assertThrows(IllegalArgumentException.class, () -> EscrowTestFixtures.ledger(first, second));
        assertEquals("Multiple operations for a device", refused.getMessage());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(first)));
        var writer = new EscrowLedgerWriter(java.nio.file.Path.of("unused-" + java.util.UUID.randomUUID()), 0);
        try (var coordinator = new EscrowCoordinator(data, writer)) {
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(second, result -> fail("Refused admission called back")));
            assertFalse(coordinator.admitting(second.source().key())); assertEquals(2, coordinator.nextSequence());
        }
    }
    @Test void everyByteEnvelopeAcceptsItsExactLimitAndRefusesTheNextByte() {
        for (int limit : new int[] {256, 512, 1_024, 2_048, 4_096, 8_192, 12_288, 32_768, 1_048_576}) {
            CompoundTag tag = new CompoundTag(); tag.putByteArray("x", new byte[limit - 12]);
            assertEquals(limit, RocketNbtSize.uncompressedBytes(tag)); assertDoesNotThrow(() -> EscrowTags.bound(tag, limit, 16, 512));
            tag.putByteArray("x", new byte[limit - 11]); assertThrows(IllegalArgumentException.class, () -> EscrowTags.bound(tag, limit, 16, 512));
        }
    }
    @Test void depthAndNodeBoundsRejectBeforeRecursiveCopy() {
        CompoundTag root = new CompoundTag(), cursor = root;
        for (int i = 1; i < 16; i++) { CompoundTag child = new CompoundTag(); cursor.put("x", child); cursor = child; }
        assertDoesNotThrow(() -> EscrowTags.bound(root, 12_288, 16, 512)); cursor.put("x", new CompoundTag());
        assertThrows(IllegalArgumentException.class, () -> EscrowTags.bound(root, 12_288, 16, 512));
        var nodes = new CompoundTag(); for (int i = 0; i < 511; i++) { nodes.putByte("n" + i, (byte) 1); }
        assertDoesNotThrow(() -> EscrowTags.bound(nodes, 12_288, 16, 512)); nodes.putByte("extra", (byte) 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowTags.bound(nodes, 12_288, 16, 512));
    }
    @Test void idsAreExplicitAsciiCanonicalAndBounded() {
        assertDoesNotThrow(() -> EscrowTags.id("x:" + "a".repeat(126)));
        for (String invalid : List.of("water", "Minecraft:water", "x:" + "a".repeat(127), "x:喵", "x:a b")) {
            assertThrows(IllegalArgumentException.class, () -> EscrowTags.id(invalid));
        }
    }
    @Test void nativeFixtureCoordinatesCannotDefaultMissingOrWrongTypesToZero() {
        var identity = EscrowTestFixtures.id(7); var ledger = EscrowTestFixtures.id(500);
        CompoundTag persistent = new CompoundTag(), fixture = new CompoundTag(), section = new CompoundTag();
        fixture.putUUID("identity", identity); fixture.putInt("amount", 5_000); fixture.putInt("capacity", 16_000);
        persistent.put("arce_escrow_fixture", fixture);
        section.putInt("schema_version", 1); section.putUUID("ledger_id", ledger); section.putLong("resource_epoch", 0);
        section.put("markers", new ListTag()); persistent.put("resource_escrow", section);
        var state = EscrowTestEndpoints.state(persistent, ledger, false);
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", identity, null,
                "minecraft:chest", 0, 0, 0, EscrowEndpoint.Unit.TANK, 0, state.signature(), EscrowTestFixtures.hash(9));
        CompoundTag root = new CompoundTag(), block = new CompoundTag(); root.putInt("xPos", 0); root.putInt("zPos", 0);
        block.putString("id", "minecraft:chest"); block.putInt("x", 0); block.putInt("y", 0); block.putInt("z", 0);
        block.put("ForgeData", persistent); ListTag blocks = new ListTag(); blocks.add(block); root.put("block_entities", blocks);
        var validator = EscrowTestEndpoints.validator(endpoint, state); assertTrue(validator.test(root));
        for (String key : List.of("xPos", "zPos")) {
            var absent = root.copy(); absent.remove(key); assertFalse(validator.test(absent));
            var wrong = root.copy(); wrong.putString(key, "0"); assertFalse(validator.test(wrong));
        }
        for (String key : List.of("x", "y", "z")) {
            var absent = root.copy(); absent.getList("block_entities", 10).getCompound(0).remove(key); assertFalse(validator.test(absent));
            var wrong = root.copy(); wrong.getList("block_entities", 10).getCompound(0).putString(key, "0"); assertFalse(validator.test(wrong));
        }
    }
    private static CompoundTag record(CompoundTag root) { return root.getList("records", 10).getCompound(0); }
}
