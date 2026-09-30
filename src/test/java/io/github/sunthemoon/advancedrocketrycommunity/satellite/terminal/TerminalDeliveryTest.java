package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-051 section 5: the terminal delivery section, its bounds and the chunk-tag persistence signal. */
final class TerminalDeliveryTest {
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron_ore");
    private static final ResourceLocation STONE = new ResourceLocation("minecraft", "cobblestone");

    @Test
    void theSectionRoundTripsAndEverythingReadStartsUnpersisted() {
        TerminalDelivery delivery = TerminalDelivery.create();
        UUID mission = UUID.randomUUID();
        delivery.pay(mission, List.of(new RewardEntry(IRON, 30), new RewardEntry(STONE, 34)));
        delivery.observed(delivery.terminalId(), Set.of(mission));
        assertTrue(delivery.idPersisted());
        assertEquals(DeliveryReconciliation.ReceiptView.PERSISTED, delivery.receipt(mission));
        assertFalse(delivery.unpersisted());

        CompoundTag data = new CompoundTag();
        delivery.write(data);
        TerminalDelivery read = TerminalDelivery.read(data);
        assertEquals(delivery.terminalId(), read.terminalId());
        assertEquals(delivery.buffer(), read.buffer());
        assertEquals(Set.of(mission), read.receiptIds());
        assertFalse(read.idPersisted(), "a loaded root is never persisted by itself (carry, /data, movers)");
        assertEquals(DeliveryReconciliation.ReceiptView.UNPERSISTED, read.receipt(mission));
        assertTrue(read.unpersisted());
        read.observed(UUID.randomUUID(), Set.of(mission));
        assertFalse(read.idPersisted(), "another terminal's tag marks nothing");
    }

    @Test
    void roomTakeAndReceiptsFollowTheBounds() {
        TerminalDelivery delivery = TerminalDelivery.create();
        assertNull(delivery.room(List.of(new RewardEntry(IRON, TerminalDelivery.MAX_BUFFER_ITEMS))));
        assertEquals(SatelliteOperationCode.DELIVERY_BUFFER_FULL,
                delivery.room(List.of(new RewardEntry(IRON, TerminalDelivery.MAX_BUFFER_ITEMS + 1))));
        for (int index = 0; index < TerminalDelivery.MAX_BUFFER_ENTRIES; index++) {
            delivery.pay(new UUID(1L, index), List.of(new RewardEntry(new ResourceLocation("test", "item_" + index), 1)));
        }
        assertNull(delivery.room(List.of(new RewardEntry(new ResourceLocation("test", "item_0"), 5))),
                "an item already buffered adds no entry");
        assertEquals(SatelliteOperationCode.DELIVERY_BUFFER_FULL,
                delivery.room(List.of(new RewardEntry(IRON, 1))), "a 33rd distinct entry is refused");
        assertEquals(1, delivery.take(new ResourceLocation("test", "item_0"), 64));
        assertEquals(0, delivery.take(new ResourceLocation("test", "item_0"), 64));

        TerminalDelivery receipts = TerminalDelivery.create();
        for (int index = 0; index < TerminalDelivery.MAX_RECEIPTS; index++) {
            receipts.pay(new UUID(2L, index), List.of(new RewardEntry(IRON, 1)));
        }
        assertEquals(SatelliteOperationCode.TERMINAL_RECEIPTS_FULL, receipts.room(List.of(new RewardEntry(IRON, 1))));
        assertThrows(IllegalStateException.class, () -> receipts.pay(UUID.randomUUID(),
                List.of(new RewardEntry(IRON, 1))));
        receipts.dropReceipt(new UUID(2L, 0));
        assertNull(receipts.room(List.of(new RewardEntry(IRON, 1))));
        assertTrue(receipts.firstAudit(new UUID(2L, 1)));
        assertFalse(receipts.firstAudit(new UUID(2L, 1)), "a one-off audit is reported once per receipt");
    }

    @Test
    void theDecoderRejectsEveryOutOfBoundSection() {
        CompoundTag valid = new CompoundTag();
        TerminalDelivery.create().write(valid);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(new CompoundTag()));

        CompoundTag entries = valid.copy();
        ListTag many = new ListTag();
        for (int index = 0; index <= TerminalDelivery.MAX_BUFFER_ENTRIES; index++) {
            many.add(entry("test:item_" + index, 1));
        }
        entries.put(TerminalDelivery.BUFFER, many);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(entries));

        CompoundTag items = valid.copy();
        ListTag heavy = new ListTag();
        heavy.add(entry("test:a", 3_000));
        heavy.add(entry("test:b", 457));
        items.put(TerminalDelivery.BUFFER, heavy);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(items));

        for (CompoundTag bad : List.of(entry("test:a", 0), entry("not an id", 1), entry("x".repeat(129) + ":a", 1))) {
            CompoundTag malformed = valid.copy();
            ListTag one = new ListTag();
            one.add(bad);
            malformed.put(TerminalDelivery.BUFFER, one);
            assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(malformed));
        }
        CompoundTag duplicate = valid.copy();
        ListTag twice = new ListTag();
        twice.add(entry("test:a", 1));
        twice.add(entry("test:a", 1));
        duplicate.put(TerminalDelivery.BUFFER, twice);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(duplicate));

        CompoundTag receipts = valid.copy();
        ListTag ids = new ListTag();
        for (int index = 0; index <= TerminalDelivery.MAX_RECEIPTS; index++) {
            ids.add(NbtUtils.createUUID(new UUID(3L, index)));
        }
        receipts.put(TerminalDelivery.RECEIPTS, ids);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(receipts));

        CompoundTag repeated = valid.copy();
        ListTag same = new ListTag();
        same.add(NbtUtils.createUUID(new UUID(4L, 4L)));
        same.add(NbtUtils.createUUID(new UUID(4L, 4L)));
        repeated.put(TerminalDelivery.RECEIPTS, same);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(repeated));

        CompoundTag wrongType = valid.copy();
        ListTag strings = new ListTag();
        strings.add(StringTag.valueOf("receipt"));
        wrongType.put(TerminalDelivery.RECEIPTS, strings);
        assertThrows(IllegalArgumentException.class, () -> TerminalDelivery.read(wrongType));
    }

    @Test
    void aPassQueuesBoundMissionsAndReceiptsOnceInIdOrder() {
        TerminalDelivery delivery = TerminalDelivery.create();
        UUID receipt = new UUID(5L, 5L);
        delivery.pay(receipt, List.of(new RewardEntry(IRON, 1)));
        List<UUID> bound = new ArrayList<>();
        for (int index = 100; index > 0; index--) {
            bound.add(new UUID(5L, index));
        }
        delivery.startPass(bound);
        List<UUID> first = delivery.nextBatch(TerminalDelivery.RECONCILE_PER_TICK);
        assertEquals(64, first.size());
        assertEquals(new UUID(5L, 1L), first.get(0));
        assertTrue(delivery.passActive());
        assertEquals(36, delivery.nextBatch(TerminalDelivery.RECONCILE_PER_TICK).size(),
                "100 bound missions including the receipt's, each once");
        assertFalse(delivery.passActive());
    }

    @Test
    void chunkTagsYieldOnlyWellFormedTerminalEntries() {
        TerminalDelivery delivery = TerminalDelivery.create();
        UUID mission = UUID.randomUUID();
        delivery.pay(mission, List.of(new RewardEntry(IRON, 3)));
        CompoundTag data = new CompoundTag();
        delivery.write(data);
        ListTag blockEntities = new ListTag();
        blockEntities.add(blockEntity("advancedrocketrycommunity:satellite_terminal", 1, 64, -3, data));
        blockEntities.add(blockEntity("minecraft:chest", 2, 64, 2, data));
        CompoundTag schemaOne = new CompoundTag();
        schemaOne.putInt("schema_version", 1);
        blockEntities.add(blockEntity("advancedrocketrycommunity:satellite_terminal", 3, 64, 3, schemaOne));
        CompoundTag chunk = new CompoundTag();
        chunk.put(TerminalChunkEvents.BLOCK_ENTITIES, blockEntities);

        List<TerminalChunkEvents.Entry> entries = TerminalChunkEvents.entries(chunk,
                "advancedrocketrycommunity:satellite_terminal");
        assertEquals(List.of(new TerminalChunkEvents.Entry(new BlockPos(1, 64, -3), delivery.terminalId(),
                Set.of(mission))), entries);
        assertEquals(List.of(), TerminalChunkEvents.entries(new CompoundTag(), "x:y"));
    }

    private static CompoundTag entry(String item, int count) {
        CompoundTag entry = new CompoundTag();
        entry.putString("item", item);
        entry.putInt("count", count);
        return entry;
    }

    private static CompoundTag blockEntity(String id, int x, int y, int z, CompoundTag data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.put(SatelliteTerminalBlockEntity.DATA_KEY, data.copy());
        return tag;
    }
}
