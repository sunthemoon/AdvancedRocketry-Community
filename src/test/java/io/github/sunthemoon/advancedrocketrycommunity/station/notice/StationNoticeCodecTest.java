package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The schema-1 notice root of ADR-065 section 5.3: exact round trips through the NBT file writer (a restart), strict
 * refusal of every malformed, future or over-bound root, the maximum population inside 8 MiB and depth 16, the kept
 * root of the SavedData, and no trace of a removed member in the saved root.
 */
class StationNoticeCodecTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static long payload(Tag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        tag.write(new DataOutputStream(bytes));
        return bytes.size();
    }

    private static StationNoticeBook populated() {
        StationNoticeBook book = new StationNoticeBook();
        UUID owner = new UUID(30, 1);
        UUID member = new UUID(30, 2);
        UUID offline = new UUID(30, 3);
        StationState alpha = StationNoticeBookTest.station(30, owner, List.of(member, offline));
        book.record(alpha, StationNoticeBookTest.committed(alpha.name(), 500_000), List.of(owner, member));
        StationState beta = StationNoticeBookTest.station(31, new UUID(31, 1), List.of());
        book.record(beta, StationNotice.aborted(beta.name(), StationManagementCode.WARP_CANCELLED,
                ModIdentity.id("moon")), List.of());
        book.add(new UUID(32, 0));
        return book;
    }

    private static CompoundTag mutated(Consumer<CompoundTag> mutation) {
        CompoundTag root = StationNoticeCodec.encode(populated());
        mutation.accept(root);
        return root;
    }

    private static CompoundTag entry(CompoundTag root, int index) {
        return root.getList("stations", Tag.TAG_COMPOUND).getCompound(index);
    }

    @Test
    void aRestartReadsBackTheSameSummariesAndAcknowledgements() throws IOException {
        StationNoticeSavedData saved = StationNoticeSavedData.load(StationNoticeCodec.encode(populated()));
        assertTrue(saved.operational());
        Path file = directory.resolve(StationNoticeSavedData.NAME + ".dat");
        CompoundTag written = new CompoundTag();
        written.put("data", saved.save(new CompoundTag()));
        NbtIo.writeCompressed(written, file.toFile());
        StationNoticeSavedData restarted = StationNoticeSavedData.load(
                NbtIo.readCompressed(file.toFile()).getCompound("data"));
        assertTrue(restarted.operational());
        assertEquals(3, restarted.size());
        StationNoticeBook expected = populated();
        for (Map.Entry<UUID, StationNoticeSummary> entry : expected.entries()) {
            assertEquals(entry.getValue(), restarted.summary(entry.getKey()));
        }
        StationNoticeSummary alpha = restarted.summary(new UUID(22, 30));
        assertEquals(1L, alpha.revision());
        assertEquals(OptionalLong.of(1L), alpha.acknowledged(new UUID(30, 1)));
        assertEquals(OptionalLong.empty(), alpha.acknowledged(new UUID(30, 3)));
        assertEquals(StationNoticeCodec.encode(expected), restarted.save(new CompoundTag()), "canonical encoding");
        assertEquals(StationNoticeSummary.EMPTY, restarted.summary(new UUID(32, 0)));
    }

    @Test
    void theRootAndItsEntriesUseTheExactSchemaOneShape() {
        CompoundTag root = StationNoticeCodec.encode(populated());
        assertEquals(java.util.Set.of("schema_version", "stations"), root.getAllKeys());
        assertEquals(1, root.getInt("schema_version"));
        CompoundTag alpha = entry(root, 0);
        assertEquals(java.util.Set.of("id", "rev", "acks", "outcome", "owner"), alpha.getAllKeys());
        assertEquals(java.util.Set.of("key", "args"), alpha.getCompound("outcome").getAllKeys());
        assertEquals("station.advancedrocketrycommunity.notice.warp_committed",
                alpha.getCompound("outcome").getString("key"));
        assertEquals(java.util.Set.of("id", "rev", "acks"), entry(root, 2).getAllKeys(), "an empty summary");
    }

    @Test
    void everyMalformedFutureOrOverBoundRootIsRefused() {
        List<Consumer<CompoundTag>> mutations = new ArrayList<>(List.of(
                root -> root.putInt("schema_version", 2),
                root -> root.putInt("schema_version", 0),
                root -> root.remove("schema_version"),
                root -> root.putString("schema_version", "1"),
                root -> root.putInt("extra", 1),
                root -> root.remove("stations"),
                root -> root.put("stations", new CompoundTag()),
                root -> root.getList("stations", Tag.TAG_COMPOUND).add(entry(root, 0).copy()),
                root -> entry(root, 0).putInt("extra", 1),
                root -> entry(root, 0).remove("acks"),
                root -> entry(root, 0).remove("rev"),
                root -> entry(root, 0).putInt("rev", 1),
                root -> entry(root, 0).putLong("rev", 0L),
                root -> entry(root, 0).putLong("rev", -1L),
                root -> entry(root, 0).remove("outcome"),
                root -> entry(root, 2).put("outcome", entry(root, 0).getCompound("outcome").copy()),
                root -> entry(root, 0).put("id", new IntArrayTag(new int[] {1, 2, 3})),
                root -> entry(root, 0).putString("owner", "x"),
                root -> entry(root, 0).getCompound("owner").putLong("rev", 2L),
                root -> entry(root, 0).getCompound("owner").putLong("rev", 0L),
                root -> entry(root, 0).getCompound("owner").remove("id"),
                root -> entry(root, 0).getList("acks", Tag.TAG_COMPOUND).add(
                        entry(root, 0).getList("acks", Tag.TAG_COMPOUND).getCompound(0).copy()),
                root -> entry(root, 0).getList("acks", Tag.TAG_COMPOUND).getCompound(0).putLong("rev", 5L),
                root -> entry(root, 0).getList("acks", Tag.TAG_COMPOUND).getCompound(0).put("id",
                        entry(root, 0).getCompound("owner").get("id").copy()),
                root -> entry(root, 0).getCompound("outcome").putString("key",
                        "station.advancedrocketrycommunity.notice.warp_started"),
                root -> entry(root, 0).getCompound("outcome").putString("key", "station.advancedrocketrycommunity.x"),
                root -> entry(root, 0).getCompound("outcome").putInt("extra", 1),
                root -> entry(root, 0).getCompound("outcome").getList("args", Tag.TAG_STRING).remove(6),
                root -> entry(root, 0).getCompound("outcome").getList("args", Tag.TAG_STRING).add(StringTag.valueOf("1")),
                root -> entry(root, 0).getCompound("outcome").getList("args", Tag.TAG_STRING).set(1,
                        StringTag.valueOf("WARP_CANCELLED")),
                root -> entry(root, 0).getCompound("outcome").getList("args", Tag.TAG_STRING).set(6,
                        StringTag.valueOf("-1")),
                root -> entry(root, 0).getCompound("outcome").put("args", new ListTag()),
                root -> {
                    ListTag numbers = new ListTag();
                    numbers.add(LongTag.valueOf(1L));
                    entry(root, 0).getCompound("outcome").put("args", numbers);
                }));
        // Overflow: a 33rd member acknowledgement, a 4,097th entry and an envelope over 512 bytes.
        mutations.add(root -> {
            ListTag acks = new ListTag();
            for (int index = 0; index <= StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS; index++) {
                CompoundTag ack = new CompoundTag();
                ack.putUUID("id", new UUID(33, index));
                ack.putLong("rev", 1L);
                acks.add(ack);
            }
            entry(root, 0).put("acks", acks);
        });
        mutations.add(root -> {
            ListTag stations = root.getList("stations", Tag.TAG_COMPOUND);
            for (int index = stations.size(); index <= StationNoticeLimits.MAX_STATIONS; index++) {
                CompoundTag empty = new CompoundTag();
                empty.putUUID("id", new UUID(34, index));
                empty.putLong("rev", 0L);
                empty.put("acks", new ListTag());
                stations.add(empty);
            }
        });
        mutations.add(root -> entry(root, 0).getCompound("outcome").getList("args", Tag.TAG_STRING).set(3,
                StringTag.valueOf(ModIdentity.id("b".repeat(400)).toString())));
        for (int index = 0; index < mutations.size(); index++) {
            CompoundTag root = mutated(mutations.get(index));
            int mutation = index;
            assertThrows(IllegalArgumentException.class, () -> StationNoticeCodec.decode(root),
                    () -> "mutation " + mutation + " was accepted: " + root);
        }
        assertThrows(IllegalArgumentException.class, () -> StationNoticeCodec.decode(null));
    }

    @Test
    void theRootIsPreflightedForDepthAndBytes() {
        CompoundTag deep = mutated(root -> {
            CompoundTag nested = new CompoundTag();
            CompoundTag cursor = nested;
            for (int depth = 0; depth < StationNoticeLimits.MAX_ROOT_DEPTH; depth++) {
                CompoundTag next = new CompoundTag();
                cursor.put("n", next);
                cursor = next;
            }
            entry(root, 0).put("outcome", nested);
        });
        assertFalse(BoundedNbt.fits(deep, StationNoticeLimits.MAX_ROOT_BYTES, StationNoticeLimits.MAX_ROOT_DEPTH,
                StationNoticeCodec.MAX_ROOT_NODES));
        assertThrows(IllegalArgumentException.class, () -> StationNoticeCodec.decode(deep));
        CompoundTag large = mutated(root -> root.put("padding", new net.minecraft.nbt.ByteArrayTag(
                new byte[StationNoticeLimits.MAX_ROOT_BYTES])));
        assertThrows(IllegalArgumentException.class, () -> StationNoticeCodec.decode(large));
    }

    /**
     * Overflow at full population: 4,096 stations, each with the largest admitted envelope, an owner acknowledgement
     * and 32 member acknowledgements, encode inside 8 MiB and depth 16 and decode again.
     */
    @Test
    void theLargestValidStoreFitsTheRootBound() throws IOException {
        StationNoticeBook book = new StationNoticeBook();
        String name = "空".repeat(StationLimits.MAX_NAME_LENGTH);
        // The longest target that still fits the 512-byte envelope with this name.
        StationNotice largest = null;
        for (int length = 1; ; length++) {
            StationNotice candidate = StationNotice.committed(name, ModIdentity.id("earth"),
                    ModIdentity.id("b".repeat(length)), "interstellar", 10_000_000, 10_000_000);
            if (StationNoticeCodec.envelopeBytes(candidate) > StationNoticeLimits.MAX_ENVELOPE_BYTES) {
                break;
            }
            largest = candidate;
        }
        assertEquals(StationNoticeLimits.MAX_ENVELOPE_BYTES, StationNoticeCodec.envelopeBytes(largest));
        List<UUID> told = new ArrayList<>();
        for (int station = 0; station < StationNoticeLimits.MAX_STATIONS; station++) {
            UUID owner = new UUID(-1L, station);
            List<UUID> members = new ArrayList<>();
            for (int member = 0; member < StationLimits.MAX_MEMBERS; member++) {
                members.add(new UUID(-2L - member, station));
            }
            StationState state = StationNoticeBookTest.station(station, owner, members);
            told.clear();
            told.add(owner);
            told.addAll(members);
            assertEquals(StationNoticeBook.Admission.ADMITTED, book.record(state, largest, told));
        }
        CompoundTag root = StationNoticeCodec.encode(book);
        long bytes = payload(root) + 3;
        assertTrue(bytes <= StationNoticeLimits.MAX_ROOT_BYTES, "full population is " + bytes + " bytes");
        assertTrue(BoundedNbt.fits(root, StationNoticeLimits.MAX_ROOT_BYTES, StationNoticeLimits.MAX_ROOT_DEPTH,
                StationNoticeCodec.MAX_ROOT_NODES));
        assertEquals(StationNoticeLimits.MAX_STATIONS, StationNoticeCodec.decode(root).size());
    }

    @Test
    void anUndecodableRootIsKeptVerbatimAndTheStoreIsUnavailable() {
        CompoundTag future = mutated(root -> root.putInt("schema_version", 2));
        CompoundTag snapshot = future.copy();
        StationNoticeSavedData data = StationNoticeSavedData.load(future);
        assertFalse(data.operational());
        assertSame(future, data.save(new CompoundTag()), "the original root is written back by reference");
        assertEquals(snapshot, future);
        StationState station = StationNoticeBookTest.station(30, new UUID(30, 1), List.of(new UUID(30, 3)));
        assertEquals(Optional.empty(), data.record(station, StationNoticeBookTest.committed(station.name(), 1),
                List.of(), id -> true));
        assertEquals(Optional.empty(), data.add(new UUID(40, 0), id -> true));
        assertTrue(data.pending(station, new UUID(30, 3)).isEmpty(), "an unavailable store shows nothing");
        assertFalse(data.acknowledge(station, new UUID(30, 3)) || data.reconcile(station)
                || data.remove(station.stationId()));
        assertEquals(0, data.retain(id -> false));
        assertFalse(data.isDirty());
        assertSame(future, data.save(new CompoundTag()));
        assertEquals(StationNoticeSummary.EMPTY, data.summary(station.stationId()));
    }

    @Test
    void aRemovedMemberLeavesNoTraceInTheSavedRoot() {
        UUID owner = new UUID(50, 1);
        UUID removed = new UUID(50, 2);
        StationState station = StationNoticeBookTest.station(50, owner, List.of(removed));
        StationNoticeSavedData data = new StationNoticeSavedData();
        data.record(station, StationNoticeBookTest.committed(station.name(), 1), List.of(owner, removed), id -> true);
        assertTrue(contains(data.save(new CompoundTag()), removed), "the fixture stores the member first");
        data.setDirty(false);
        assertTrue(data.reconcile(station.withoutMember(removed)));
        assertTrue(data.isDirty());
        CompoundTag saved = data.save(new CompoundTag());
        assertFalse(contains(saved, removed), "the removed member's UUID is still saved: " + saved);
        assertTrue(contains(saved, owner));
    }

    /** Whether any int array in the tree is exactly {@code player}'s UUID. */
    private static boolean contains(Tag tag, UUID player) {
        if (tag instanceof IntArrayTag array) {
            return java.util.Arrays.equals(array.getAsIntArray(), NbtUtils.createUUID(player).getAsIntArray());
        }
        if (tag instanceof CompoundTag compound) {
            return compound.getAllKeys().stream().anyMatch(key -> contains(compound.get(key), player));
        }
        if (tag instanceof ListTag list) {
            return list.stream().anyMatch(element -> contains(element, player));
        }
        return false;
    }
}
