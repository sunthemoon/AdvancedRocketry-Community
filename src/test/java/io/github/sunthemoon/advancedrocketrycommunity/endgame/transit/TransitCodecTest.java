package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-054 sections 10 and 11: payload bounds, strict record and outbox codecs, the root section and pins. */
final class TransitCodecTest {
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "railgun");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID SOURCE = new UUID(0L, 10L);
    private static final UUID DESTINATION = new UUID(0L, 20L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static TransitPayload payload() {
        return TransitPayload.of(List.of(new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.STICK, 3)))
                .orElseThrow();
    }

    @Test
    void payloadsHoldOneToFourStacksOfAtMost512Bytes() {
        assertTrue(TransitPayload.of(List.of()).isEmpty());
        assertTrue(TransitPayload.of(List.of(ItemStack.EMPTY)).isEmpty());
        ItemStack stack = new ItemStack(Items.COBBLESTONE, 64);
        assertEquals(4, TransitPayload.of(List.of(stack, stack, stack, stack)).orElseThrow().stacks());
        assertTrue(TransitPayload.of(List.of(stack, stack, stack, stack, stack)).isEmpty());
        ItemStack named = new ItemStack(Items.STICK);
        named.getOrCreateTag().putString("note", "x".repeat(600));
        assertFalse(TransitPayload.fits(named));
        assertTrue(TransitPayload.of(List.of(named)).isEmpty(), "a stack over 512 bytes is refused at escrow");
        TransitPayload payload = payload();
        List<ItemStack> decoded = payload.decode().orElseThrow();
        assertTrue(ItemStack.matches(decoded.get(0), new ItemStack(Items.DIAMOND, 64)));
        assertEquals(16, payload.hash().length());
        assertEquals(payload.hash(), TransitPayload.raw(payload.tag()).hash());
    }

    @Test
    void aPayloadOfARemovedItemStaysRawAndDoesNotDecode() {
        ListTag list = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "removedmod:widget");
        stack.putByte("Count", (byte) 3);
        list.add(stack);
        TransitPayload payload = TransitPayload.raw(list);
        assertTrue(payload.decode().isEmpty(), "the record is quarantined instead");
        assertEquals(list, payload.tag(), "the raw payload is kept byte-identical");
        assertThrows(IllegalArgumentException.class, () -> TransitPayload.raw(new ListTag()));
    }

    @Test
    void recordsRoundTripStrictly() {
        OutboxEntry entry = new OutboxEntry(1L, DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        assertEquals(entry, TransitCodec.decodeEntry(TransitCodec.encodeEntry(entry)));
        TransitRecord live = TransitRecord.registered(SOURCE, entry, OWNER, 3L, 1_000L);
        TransitRecord claimed = live.arrived().claimed(DESTINATION);
        TransitRecord stub = claimed.acknowledged(4L).asStub();
        for (TransitRecord record : List.of(live, claimed, stub, live.redirectedTo(new UUID(0L, 30L)),
                live.quarantined())) {
            assertEquals(record, TransitCodec.decodeRecord(TransitCodec.encodeRecord(record)));
        }
        assertEquals(1_020L, live.arriveAt());
        assertTrue(stub.stub() && stub.ackDurable(5L) && !stub.ackDurable(4L));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.putString("color", "red"));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.putString("state", "LOST"));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.putString("system", "laser_drill"));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.remove("payload"));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.putLong("seq", 0L));
        assertRejected(TransitCodec.encodeRecord(claimed), tag -> tag.remove("paid_endpoint"));
        assertRejected(TransitCodec.encodeRecord(live), tag -> tag.putString("note", "x".repeat(3000)));
        assertThrows(IllegalArgumentException.class, () -> new OutboxEntry(1L, DESTINATION, payload(), 1, 601,
                EndgameSystem.RAILGUN));
        assertThrows(IllegalArgumentException.class, () -> new OutboxEntry(1L, DESTINATION, payload(), 1, 20,
                EndgameSystem.LASER_DRILL));
    }

    @Test
    void theRootSectionRoundTripsAndPinsTheIdsARecordNames() {
        EndgameRoot root = EndgameRoot.create();
        root.register(SOURCE, KIND, OWNER, LEVEL, 0L, false, 2048, 64);
        root.register(DESTINATION, KIND, OWNER, LEVEL, 64L, false, 2048, 64);
        OutboxEntry entry = new OutboxEntry(1L, DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        root.registerTransit(TransitRecord.registered(SOURCE, entry, OWNER, root.saveEpoch(), 100L));
        assertEquals(1L, root.dispatchedThrough(SOURCE));
        assertThrows(IllegalStateException.class, () -> root.registerTransit(TransitRecord.registered(SOURCE, entry,
                OWNER, root.saveEpoch(), 100L)), "a seq at or below dispatched_through registers again");
        CompoundTag encoded = EndgameRootCodec.encode(root, new CompoundTag());
        EndgameRoot decoded = EndgameRootCodec.decode(encoded);
        assertEquals(root.transits().records(), decoded.transits().records());
        assertEquals(root.accountedBytes(), decoded.accountedBytes());

        // The destination is removed and settles; the record names it, so no cap or command evicts it.
        decoded.remove(DESTINATION);
        decoded.settle(DESTINATION, id -> false);
        assertTrue(decoded.pinned(DESTINATION));
        assertTrue(decoded.evictOwner(OWNER, id -> false).isEmpty(), "a pinned tombstone was evicted");
        assertTrue(decoded.retired(DESTINATION));

        CompoundTag orphan = encoded.copy();
        orphan.getList("transits", 10).getCompound(0).putUUID("source", new UUID(9L, 9L));
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(orphan),
                "a record whose source is no endpoint or tombstone");
        // Review C12R-H1: a destination that is gone, its tombstone evicted, is DESTINATION_MISSING, not a defect.
        CompoundTag missing = encoded.copy();
        missing.getList("transits", 10).getCompound(0).putUUID("destination", new UUID(9L, 9L));
        assertEquals(new UUID(9L, 9L), EndgameRootCodec.decode(missing).transits().records().iterator().next()
                .destination());
        CompoundTag ahead = encoded.copy();
        ahead.getList("transits", 10).getCompound(0).putLong("seq", 2L);
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(ahead), "above dispatched_through");
        CompoundTag newer = encoded.copy();
        newer.getList("transits", 10).getCompound(0).putLong("dispatch_epoch", 99L);
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(newer), "a future epoch");
    }

    /**
     * Review C12R2-I5: a record whose source or paid endpoint is neither an endpoint nor a tombstone is refused. Only the
     * restore rule refuses the unknown paid endpoint; the unknown source is also refused by the dispatched_through
     * checks, which imply the rule's source half (review C12R3-I1).
     */
    @Test
    void theRestoreRuleRefusesAnUnknownSourceOrPaidEndpoint() {
        EndgameRoot root = EndgameRoot.create();
        root.register(SOURCE, KIND, OWNER, LEVEL, 0L, false, 2048, 64);
        root.register(DESTINATION, KIND, OWNER, LEVEL, 64L, false, 2048, 64);
        OutboxEntry entry = new OutboxEntry(1L, DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        root.registerTransit(TransitRecord.registered(SOURCE, entry, OWNER, root.saveEpoch(), 100L).arrived()
                .claimed(DESTINATION));
        root.remove(SOURCE);
        CompoundTag encoded = EndgameRootCodec.encode(root, new CompoundTag());
        assertEquals(SOURCE, EndgameRootCodec.decode(encoded).transits().records().iterator().next().key().source(),
                "a source known by its tombstone decodes");

        EndgameRoot withoutSource = EndgameRoot.create();
        withoutSource.register(DESTINATION, KIND, OWNER, LEVEL, 64L, false, 2048, 64);
        CompoundTag unknownSource = encoded.copy();
        unknownSource.put(EndgameRootCodec.ENDPOINTS, EndgameRootCodec.encode(withoutSource, new CompoundTag())
                .get(EndgameRootCodec.ENDPOINTS));
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(unknownSource),
                "a source that is neither an endpoint nor a tombstone");

        CompoundTag unknownPaid = encoded.copy();
        unknownPaid.getList(EndgameRootCodec.TRANSITS, 10).getCompound(0).putUUID("paid_endpoint", new UUID(9L, 9L));
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(unknownPaid),
                "a paid endpoint that is neither an endpoint nor a tombstone");
    }

    private static void assertRejected(CompoundTag valid, Consumer<CompoundTag> change) {
        CompoundTag broken = valid.copy();
        change.accept(broken);
        assertThrows(IllegalArgumentException.class, () -> TransitCodec.decodeRecord(broken));
    }
}
