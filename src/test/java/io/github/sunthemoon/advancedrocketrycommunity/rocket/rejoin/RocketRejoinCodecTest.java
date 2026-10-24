package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRejoinCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static UUID id(long n) { return new UUID(157, n); }
    static RocketRejoinRoot.Batch batch(long n, long sequence, List<UUID> players) {
        var version = new RocketRejoinRoot.Version("a".repeat(64), "PREPARED", null);
        return new RocketRejoinRoot.Batch(id(n), sequence, id(1_000 + n), id(2_000 + n), id(3_000 + n),
                RocketRejoinRoot.Hold.H1, "minecraft:overworld", new RocketRejoinRoot.Position(0, 64, 0),
                new RocketRejoinRoot.Position(-1, 64, -1), new RocketRejoinRoot.Position(1, 66, 1),
                new RocketRejoinRoot.Resources("b".repeat(64), "c".repeat(64)), version, version, players);
    }
    static RocketRejoinRoot.Entry entry(long n, UUID player) {
        return new RocketRejoinRoot.Entry(id(n), player, 0, RocketRejoinRoot.Placement.PENDING,
                RocketRejoinRoot.Seat.PENDING, null);
    }
    static RocketRejoinRoot fixture() {
        return RocketRejoinRoot.empty().admit(batch(1, 0, List.of(id(100))), List.of(entry(1, id(100))));
    }
    static CompoundTag encoded() { return RocketRejoinCodec.encode(fixture()); }
    static CompoundTag first(CompoundTag root, String key) { return root.getList(key, 10).getCompound(0); }

    @Test void schemaOneRoundTripsEveryStateAndAttempt() {
        assertEquals(fixture(), RocketRejoinCodec.decode(encoded()));
        for (var placement : RocketRejoinRoot.Placement.values()) {
            for (var seat : RocketRejoinRoot.Seat.values()) {
                if (placement != RocketRejoinRoot.Placement.PENDING && seat == RocketRejoinRoot.Seat.VERIFIED) { continue; }
                for (var target : RocketRejoinRoot.Target.values()) {
                    var attempt = placement == RocketRejoinRoot.Placement.PENDING
                            ? new RocketRejoinRoot.Attempt(id(900), target, .5, 65, -.5) : null;
                    var entry = new RocketRejoinRoot.Entry(id(1), id(100), 15, placement, seat, attempt);
                    var root = new RocketRejoinRoot(1, fixture().batches(), List.of(entry));
                    assertEquals(root, RocketRejoinCodec.decode(RocketRejoinCodec.encode(root)));
                }
            }
        }
    }

    @Test void rejectsUnknownFieldsWrongTypesStatesReferencesAndDuplicateKeys() {
        var mutations = List.<Consumer<CompoundTag>>of(
                r -> r.putInt("schema_version", 2), r -> r.putString("format_epoch", "wrong"),
                r -> r.putInt("unknown", 1), r -> r.remove("entries"), r -> r.putInt("next_sequence", 1),
                r -> r.putLong("next_sequence", -1), r -> r.putLong("next_sequence", 0),
                r -> first(r, "entries").putString("placement", "UNKNOWN"),
                r -> first(r, "entries").putString("seat", "UNKNOWN"),
                r -> first(r, "entries").putInt("seat_index", 16),
                r -> first(r, "entries").putInt("seat_index", -1),
                r -> first(r, "entries").putUUID("release", id(900)),
                r -> first(r, "entries").putUUID("player", id(900)),
                r -> first(r, "entries").putIntArray("player", new int[] {1, 2}),
                r -> first(r, "entries").putInt("extra", 1),
                r -> r.getList("entries", 10).add(first(r, "entries").copy()),
                r -> r.getList("batches", 10).add(first(r, "batches").copy()),
                r -> first(r, "batches").putString("dimension", "overworld"),
                r -> first(r, "batches").putString("dimension", "minecraft:" + "x".repeat(119)),
                r -> first(r, "batches").putString("hold", "H3"),
                r -> first(r, "batches").putInt("extra", 1),
                r -> first(r, "batches").getCompound("before").putString("phase", "BROKEN"),
                r -> first(r, "batches").getCompound("resources").putString("fuel", "not-a-hash"),
                r -> r.put("entries", new ListTag()), r -> r.put("batches", new ListTag()));
        for (var mutation : mutations) {
            var root = encoded(); mutation.accept(root);
            assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.decode(root));
        }
    }

    @Test void countsCapacityRetiresOnlyAfterBothObligationsAndNeverReusesSequence() {
        var root = RocketRejoinRoot.empty();
        for (int i = 0; i < 256; i++) {
            root = root.admit(batch(i + 1, i, List.of(id(10_000 + i))), List.of(entry(i + 1, id(10_000 + i))));
        }
        assertEquals(root, RocketRejoinCodec.decode(RocketRejoinCodec.encode(root)));
        var full = root;
        assertThrows(IllegalArgumentException.class, () -> full.admit(batch(500, 256, List.of(id(20_000))),
                List.of(entry(500, id(20_000)))));
        for (var entry : List.copyOf(root.entries())) {
            root = root.update(new RocketRejoinRoot.Entry(entry.release(), entry.player(), entry.seatIndex(),
                    RocketRejoinRoot.Placement.VERIFIED, RocketRejoinRoot.Seat.PENDING, null));
        }
        assertEquals(256, root.entries().size());
        for (var entry : List.copyOf(root.entries())) {
            root = root.update(new RocketRejoinRoot.Entry(entry.release(), entry.player(), entry.seatIndex(),
                    entry.placement(), RocketRejoinRoot.Seat.VERIFIED, null));
        }
        assertTrue(root.batches().isEmpty()); assertTrue(root.entries().isEmpty()); assertEquals(256, root.nextSequence());
        root = root.admit(batch(600, 256, List.of(id(100))), List.of(entry(600, id(100))));
        assertEquals(257, root.nextSequence());
    }

    @Test void rejectsPlayerAndBatchCapacitySequenceOverflowAndNonFinalRetirement() {
        var root = RocketRejoinRoot.empty();
        for (int i = 1; i <= 4; i++) { root = root.admit(batch(i, i - 1, List.of(id(100))), List.of(entry(i, id(100)))); }
        var fullPlayer = root;
        assertThrows(IllegalArgumentException.class, () -> fullPlayer.admit(batch(5, 4, List.of(id(100))), List.of(entry(5, id(100)))));
        var overflow = new RocketRejoinRoot(Long.MAX_VALUE, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> overflow.admit(batch(1, Long.MAX_VALUE, List.of(id(100))), List.of(entry(1, id(100)))));
        var players = new ArrayList<UUID>(); for (int i = 0; i < 17; i++) { players.add(id(100 + i)); }
        assertThrows(IllegalArgumentException.class, () -> batch(1, 0, players));
        assertThrows(IllegalArgumentException.class, () -> fixture().admit(batch(2, 0, List.of(id(101))), List.of(entry(2, id(101)))));
        assertThrows(IllegalArgumentException.class, () -> new RocketRejoinRoot(1, fixture().batches(), List.of()));
    }

    @Test void coordinatesAttemptsAndImmutableCollectionsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new RocketRejoinRoot.Attempt(id(1), RocketRejoinRoot.Target.PAD, Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketRejoinRoot.Attempt(id(1), RocketRejoinRoot.Target.PAD, 30_000_000, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketRejoinRoot.Position(0, 2_048, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketRejoinRoot.Entry(id(1), id(100), 0,
                RocketRejoinRoot.Placement.NOT_REQUIRED, RocketRejoinRoot.Seat.PENDING,
                new RocketRejoinRoot.Attempt(id(2), RocketRejoinRoot.Target.PAD, 0, 64, 0)));
        assertThrows(UnsupportedOperationException.class, () -> fixture().batches().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixture().entries().clear());
    }

    @Test void structuralAndPerItemEnvelopesAreCheckedBeforeDecoding() {
        var depth = new CompoundTag(); var child = depth;
        for (int i = 0; i < 32; i++) { var next = new CompoundTag(); child.put("x", next); child = next; }
        assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.bounds(depth));
        var nodes = new CompoundTag(); for (int i = 0; i < 16_384; i++) { nodes.putInt("x" + i, i); }
        assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.bounds(nodes));
        var oversized = encoded(); oversized.putByteArray("large", new byte[1_048_576]);
        assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.bounds(oversized));
        var entry = encoded(); first(entry, "entries").putByteArray("large", new byte[512]);
        assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.bounds(entry));
        var batch = encoded(); first(batch, "batches").putByteArray("large", new byte[2_048]);
        assertThrows(IllegalArgumentException.class, () -> RocketRejoinCodec.bounds(batch));
    }

    @Test void everyExistingTransferPhaseCanBeComparedWithoutChangingItsIdentity() {
        for (var phase : io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.values()) {
            UUID destination = phase.destinationAuthoritative() ? id(700) : null;
            var version = new RocketRejoinRoot.Version("d".repeat(64), phase.name(), destination);
            var b = fixture().batches().get(0);
            var batch = new RocketRejoinRoot.Batch(b.release(), b.sequence(), b.transfer(), b.rocket(), b.authority(),
                    b.hold(), b.dimension(), b.pad(), b.minimum(), b.maximum(), b.resources(), version, version, b.released());
            var root = new RocketRejoinRoot(1, List.of(batch), fixture().entries());
            assertEquals(root, RocketRejoinCodec.decode(RocketRejoinCodec.encode(root)));
        }
    }
}
