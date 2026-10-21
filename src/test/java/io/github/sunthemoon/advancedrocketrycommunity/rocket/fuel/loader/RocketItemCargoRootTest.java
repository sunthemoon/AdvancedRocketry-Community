package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot.Status;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A0 for the item device root: every bound just inside, at and just outside, and the closed field set. */
class RocketItemCargoRootTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static CompoundTag stone(int count) { return new ItemStack(Items.STONE, count).save(new CompoundTag()); }

    private static EscrowItemBuffer buffer(CompoundTag... stacks) {
        List<CompoundTag> slots = new ArrayList<>();
        for (int slot = 0; slot < EscrowItemBuffer.SLOTS; slot++) { slots.add(slot < stacks.length ? stacks[slot] : new CompoundTag()); }
        return new EscrowItemBuffer(slots);
    }

    private static RocketItemCargoRoot full(RocketCargoRole role) {
        var binding = new LoaderBinding(UUID.randomUUID(), ResourceLocation.tryParse("x:" + "a".repeat(126)),
                UUID.randomUUID(), new BlockPos(-30_000_000, -2_048, 29_999_999), UUID.randomUUID());
        var escrow = new RocketEscrowSection(UUID.randomUUID(), Long.MAX_VALUE,
                List.of(new EscrowPrefix.Marker(UUID.randomUUID(), Long.MAX_VALUE, role.markerRole(), Long.MAX_VALUE)));
        return new RocketItemCargoRoot(UUID.randomUUID(), UUID.randomUUID(), role, binding,
                buffer(stone(64), new CompoundTag(), new ItemStack(Items.ENDER_PEARL, 16).save(new CompoundTag()), stone(1)), escrow);
    }

    private static Status read(Tag root, RocketCargoRole role) {
        CompoundTag outer = new CompoundTag(); outer.put(RocketItemCargoRoot.KEY, root);
        return RocketItemCargoRoot.read(outer, role).status();
    }

    /** The root with slot {@code slot} replaced by a raw saved stack, bypassing the typed buffer. */
    private static CompoundTag withRawSlot(CompoundTag root, int slot, Tag stack) {
        CompoundTag copy = root.copy();
        copy.getCompound("buffer").getList("slots", Tag.TAG_COMPOUND).set(slot, stack);
        return copy;
    }

    /** A saved stone stack whose tag holds a byte list making the whole stack exactly {@code nodes} nodes. */
    private static CompoundTag stoneWithNodes(int nodes) {
        CompoundTag saved = stone(1); CompoundTag tag = new CompoundTag(); ListTag list = new ListTag();
        tag.put("n", list); saved.put("tag", tag);
        int elements = nodes - size(saved).nodes();
        for (int index = 0; index < elements; index++) { list.add(ByteTag.valueOf((byte) index)); }
        assertEquals(nodes, size(saved).nodes());
        return saved;
    }

    /** A saved stone stack whose tag holds a byte array making the whole stack exactly {@code bytes} bytes. */
    private static CompoundTag stoneWithBytes(int bytes) {
        CompoundTag saved = stone(1); CompoundTag tag = new CompoundTag(); saved.put("tag", tag);
        tag.putByteArray("p", new byte[0]);
        tag.putByteArray("p", new byte[(int) (bytes - size(saved).bytes())]);
        assertEquals(bytes, size(saved).bytes());
        return saved;
    }

    @Test void bothRolesRoundTripAndEmptySlotsAreWrittenAsEmptyCompounds() {
        assertEquals("arce_rocket_item_cargo", RocketItemCargoRoot.KEY);
        for (var role : RocketCargoRole.values()) {
            for (var root : List.of(full(role), RocketItemCargoRoot.placed(null, UUID.randomUUID(), role),
                    RocketItemCargoRoot.placed(UUID.randomUUID(), UUID.randomUUID(), role).withSlot(3, stone(5)))) {
                assertEquals(root, RocketItemCargoRoot.decode(root.encode(), role));
                assertEquals(root.encode(), RocketItemCargoRoot.decode(root.encode(), role).encode());
                assertEquals(root.signature(), root.buffer().signature(root.device()));
                assertEquals(Status.VALID, read(root.encode(), role));
            }
            var slots = RocketItemCargoRoot.placed(null, UUID.randomUUID(), role).encode().getCompound("buffer")
                    .getList("slots", Tag.TAG_COMPOUND);
            assertEquals(4, slots.size());
            for (int slot = 0; slot < 4; slot++) { assertEquals(new CompoundTag(), slots.getCompound(slot)); }
            var root = full(role);
            assertEquals(root.buffer(), root.withBinding(null).buffer());
            assertEquals(root.escrow(), root.withBuffer(RocketItemCargoRoot.EMPTY).escrow());
            assertEquals(stone(9), root.withSlot(1, stone(9)).buffer().slots().get(1));
            assertTrue(root.marked());
        }
    }

    @Test void dpFixtureFormDecodesUnchanged() {
        // EscrowItemCargoEligibilityTest and EscrowItemCargoGameTests build the device root this way (W118).
        for (var role : RocketCargoRole.values()) {
            var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("owner", UUID.randomUUID());
            root.putUUID("device", UUID.randomUUID()); root.putString("role", role.name());
            root.put("buffer", buffer(stone(20)).encode()); root.put("resource_escrow", RocketEscrowSection.neutral().encode());
            assertEquals(Status.VALID, read(root, role));
            assertEquals(root, RocketItemCargoRoot.decode(root, role).encode());
        }
    }

    @Test void rootAndEscrowEnvelopePredicatesPinAllThreeExactBounds() {
        for (var role : RocketCargoRole.values()) {
            var root = full(role);
            bounds(root.encode(), RocketItemCargoRoot::bounded, 32_768, 20, 2_048);
            for (Tag tag : List.of(padTo(root.encode(), "p", 32_769), nodesTo(root.encode(), "n", 2_049))) {
                assertThrows(IllegalArgumentException.class, () -> RocketItemCargoRoot.decode(tag, role));
            }
            for (int bytes : new int[] {32_767, 32_768, 32_769}) {
                assertEquals(bytes <= 32_768, RocketItemCargoRoot.bounded(padTo(root.encode(), "p", bytes)));
            }
            CompoundTag deep = root.encode(); deep.put("p", chain(20));
            assertEquals(Status.INVALID, read(deep, role));
        }
    }

    @Test void fourIndividuallyBoundedStacksStillMeetTheRootNodeBound() {
        for (var role : RocketCargoRole.values()) {
            var empty = full(role).withBuffer(RocketItemCargoRoot.EMPTY);
            // Four empty slots are four nodes; the rest of the root is fixed.
            int frame = size(empty.encode()).nodes() - 4;
            for (int total : new int[] {2_047, 2_048, 2_049}) {
                int stacks = total - frame;
                int[] each = {stacks / 4, stacks / 4, stacks / 4, stacks - 3 * (stacks / 4)};
                var raw = empty.encode();
                for (int slot = 0; slot < 4; slot++) {
                    assertTrue(each[slot] <= 512, "fixture stack exceeds its own bound");
                    raw = withRawSlot(raw, slot, stoneWithNodes(each[slot]));
                }
                assertEquals(total, size(raw).nodes());
                assertEquals(total <= 2_048 ? Status.VALID : Status.INVALID, read(raw, role), "nodes " + total);
                if (total <= 2_048) {
                    assertEquals(raw, RocketItemCargoRoot.decode(raw, role).encode());
                } else {
                    // The typed change that would produce it is refused before encoding, and the unchecked form says why.
                    var decodedSlots = RocketItemCargoRoot.decode(withRawSlot(raw, 3, new CompoundTag()), role);
                    var grown = decodedSlots.withSlot(3, stoneWithNodes(each[3]));
                    assertFalse(RocketItemCargoRoot.bounded(grown.encodeUnchecked()));
                    assertThrows(IllegalArgumentException.class, grown::encode);
                }
            }
        }
    }

    /**
     * W120-R2, the review probe's case: an unbound root filled close to the node bound had no room for a binding. The
     * device now accepts a buffer only when the root also fits with full()'s binding and marker, the largest a linker
     * and an escrow mark write, so every accepted fill can be bound and marked; one node more cannot.
     */
    @Test void aFillTheDeviceAcceptsLeavesRoomForTheLargestBindingAndOneMarker() {
        for (var role : RocketCargoRole.values()) {
            var placed = RocketItemCargoRoot.placed(UUID.randomUUID(), UUID.randomUUID(), role);
            int frame = size(placed.encode()).nodes() - 4;
            var largest = new RocketItemCargoRoot(placed.owner(), placed.device(), role, full(role).binding(), placed.buffer(), full(role).escrow());
            int reserve = size(largest.encode()).nodes() - size(placed.encode()).nodes();
            // A binding with a station is 9 nodes; a ledger and one marker add 6 to the escrow section.
            assertEquals(11, frame);
            assertEquals(15, reserve);
            int room = RocketItemCargoRoot.MAX_NODES - frame - reserve;
            for (int stacks : new int[] {room - 1, room, room + 1, RocketItemCargoRoot.MAX_NODES - frame - 8, RocketItemCargoRoot.MAX_NODES - frame}) {
                int[] each = {512, 512, 512, stacks - 3 * 512};
                var claimed = placed;
                var unclaimed = RocketItemCargoRoot.placed(null, UUID.randomUUID(), role);
                for (int slot = 0; slot < 4; slot++) {
                    claimed = claimed.withSlot(slot, stoneWithNodes(each[slot]));
                    unclaimed = unclaimed.withSlot(slot, stoneWithNodes(each[slot]));
                }
                // The unbound root fits its envelope at every one of these sizes; only the room for a binding differs.
                assertEquals(frame + stacks, size(claimed.encode()).nodes());
                assertEquals(stacks <= room, claimed.leavesRoomToBindAndMark(), "stacks " + stacks);
                assertEquals(stacks <= room, unclaimed.leavesRoomToBindAndMark(), "unclaimed stacks " + stacks);
                var bound = new RocketItemCargoRoot(claimed.owner(), claimed.device(), role, full(role).binding(), claimed.buffer(), full(role).escrow());
                assertEquals(stacks <= room, RocketItemCargoRoot.bounded(bound.encodeUnchecked()), "bound stacks " + stacks);
                if (stacks <= room) {
                    assertEquals(bound, RocketItemCargoRoot.decode(bound.encode(), role));
                } else {
                    assertThrows(IllegalArgumentException.class, bound::encode);
                }
            }
        }
    }

    @Test void stackBoundsSitExactlyAtSectionFiveThree() {
        for (var role : RocketCargoRole.values()) {
            var base = RocketItemCargoRoot.placed(UUID.randomUUID(), UUID.randomUUID(), role).encode();
            // Bytes: 4,096 is the stack bound; the root holds four of them well inside 32 KiB.
            assertEquals(Status.VALID, read(withRawSlot(base, 0, stoneWithBytes(4_096)), role));
            assertEquals(Status.VALID, read(withRawSlot(base, 0, stoneWithBytes(4_095)), role));
            assertEquals(Status.INVALID, read(withRawSlot(base, 0, stoneWithBytes(4_097)), role));
            var four = base;
            for (int slot = 0; slot < 4; slot++) { four = withRawSlot(four, slot, stoneWithBytes(4_096)); }
            assertEquals(Status.VALID, read(four, role));
            // Nodes: 512 per stack.
            assertEquals(Status.VALID, read(withRawSlot(base, 1, stoneWithNodes(512)), role));
            assertEquals(Status.INVALID, read(withRawSlot(base, 1, stoneWithNodes(513)), role));
            // Depth: the stack compound is depth 1 and its tag depth 2, so a 15-level tag chain reaches 16.
            for (int levels : new int[] {14, 15, 16}) {
                var saved = stone(1); saved.put("tag", chain(levels));
                assertEquals(levels <= 15 ? Status.VALID : Status.INVALID, read(withRawSlot(base, 2, saved), role), "depth " + levels);
            }
            // Counts: 1 to min(64, max stack size).
            for (int count : new int[] {0, 1, 63, 64, 65, -1}) {
                var saved = stone(1); saved.putByte("Count", (byte) count);
                assertEquals(count >= 1 && count <= 64 ? Status.VALID : Status.INVALID, read(withRawSlot(base, 3, saved), role), "count " + count);
            }
            for (int count : new int[] {15, 16, 17}) {
                var saved = new ItemStack(Items.ENDER_PEARL).save(new CompoundTag()); saved.putByte("Count", (byte) count);
                assertEquals(count <= 16 ? Status.VALID : Status.INVALID, read(withRawSlot(base, 3, saved), role), "pearls " + count);
            }
            // IDs: at most 128 characters, registered, not air.
            for (int length : new int[] {127, 128, 129}) {
                var saved = stone(1); saved.putString("id", "absentmod:" + "a".repeat(length - 10));
                assertEquals(length <= 128 ? Status.UNREGISTERED_ITEM : Status.INVALID, read(withRawSlot(base, 0, saved), role), "id " + length);
            }
            var air = stone(1); air.putString("id", "minecraft:air");
            assertEquals(Status.INVALID, read(withRawSlot(base, 0, air), role));
            // No Forge capabilities at any depth, and a string player-head owner is a deferred rewrite.
            var caps = stone(1); caps.put("ForgeCaps", new CompoundTag());
            var nestedCaps = stone(1); var tag = new CompoundTag(); tag.put("inner", new CompoundTag());
            tag.getCompound("inner").put("ForgeCaps", new CompoundTag()); nestedCaps.put("tag", tag);
            var head = new ItemStack(Items.PLAYER_HEAD).save(new CompoundTag()); var owner = new CompoundTag();
            owner.putString("SkullOwner", "someone"); head.put("tag", owner);
            for (var saved : List.of(caps, nestedCaps, head)) { assertEquals(Status.INVALID, read(withRawSlot(base, 0, saved), role)); }
        }
    }

    @Test void theFieldSetIsClosedAtTheRootTheBufferAndEachStack() {
        for (var role : RocketCargoRole.values()) {
            var base = full(role).encode();
            for (String field : base.getAllKeys()) {
                var raw = base.copy(); raw.put(field, IntTag.valueOf(-1));
                assertEquals(Status.INVALID, read(raw, role), field);
                if (!field.equals("binding")) {
                    raw = base.copy(); raw.remove(field);
                    assertEquals(Status.INVALID, read(raw, role), field);
                }
            }
            var unknown = base.copy(); unknown.putInt("extra", 1);
            assertEquals(Status.INVALID, read(unknown, role));
            var bufferField = base.copy(); bufferField.getCompound("buffer").putInt("extra", 1);
            assertEquals(Status.INVALID, read(bufferField, role));
            var stackField = withRawSlot(base, 1, stone(1)); stackField.getCompound("buffer").getList("slots", Tag.TAG_COMPOUND)
                    .getCompound(1).putInt("extra", 1);
            assertEquals(Status.INVALID, read(stackField, role));
            for (int slots : new int[] {0, 3, 5}) {
                var raw = base.copy(); var list = new ListTag();
                for (int slot = 0; slot < slots; slot++) { list.add(new CompoundTag()); }
                raw.getCompound("buffer").put("slots", list);
                assertEquals(Status.INVALID, read(raw, role), "slots " + slots);
            }
            var mistypedSlots = base.copy(); var ints = new ListTag();
            for (int slot = 0; slot < 4; slot++) { ints.add(IntTag.valueOf(slot)); }
            mistypedSlots.getCompound("buffer").put("slots", ints);
            assertEquals(Status.INVALID, read(mistypedSlots, role));
            for (int version : new int[] {-1, 0}) {
                var raw = base.copy(); raw.putInt("schema_version", version);
                assertEquals(Status.INVALID, read(raw, role));
            }
            var raw = base.copy(); raw.putLong("schema_version", 1);
            assertEquals(Status.INVALID, read(raw, role));
            var other = role == RocketCargoRole.LOAD ? RocketCargoRole.UNLOAD : RocketCargoRole.LOAD;
            assertEquals(Status.INVALID, read(base, other));
            assertEquals(Status.INVALID, read(IntTag.valueOf(1), role));
            var unboundOwner = RocketItemCargoRoot.placed(null, UUID.randomUUID(), role).encode();
            unboundOwner.put("binding", full(role).binding().encode());
            assertEquals(Status.INVALID, read(unboundOwner, role));
        }
    }

    @Test void absenceFutureNestedFutureAndMissingRegistryAreDistinct() {
        assertEquals(Status.ABSENT, RocketItemCargoRoot.read(new CompoundTag(), RocketCargoRole.LOAD).status());
        for (var role : RocketCargoRole.values()) {
            for (int version : new int[] {2, Integer.MAX_VALUE}) {
                var raw = full(role).encode(); raw.putInt("schema_version", version);
                raw.putByteArray("future", new byte[65_536]);
                assertEquals(Status.FUTURE, read(raw, role));
            }
            for (String nested : List.of("binding", "resource_escrow")) {
                var raw = full(role).encode(); raw.getCompound(nested).putInt("schema_version", 2);
                assertEquals(Status.FUTURE, read(raw, role));
            }
            var missing = withRawSlot(full(role).encode(), 1, stone(1));
            missing.getCompound("buffer").getList("slots", Tag.TAG_COMPOUND).getCompound(1).putString("id", "absentmod:crate");
            assertEquals(Status.UNREGISTERED_ITEM, read(missing, role));
        }
    }

    @Test void markersMustHaveTheFixedDirectionAndAtMostOneEntry() {
        for (var role : RocketCargoRole.values()) {
            var raw = full(role).encode();
            var markers = raw.getCompound("resource_escrow").getList("markers", Tag.TAG_COMPOUND);
            markers.getCompound(0).putString("role", role == RocketCargoRole.LOAD ? "DESTINATION" : "SOURCE");
            assertEquals(Status.INVALID, read(raw, role));
            raw = full(role).encode(); markers = raw.getCompound("resource_escrow").getList("markers", Tag.TAG_COMPOUND);
            markers.add(markers.getCompound(0).copy());
            assertEquals(Status.INVALID, read(raw, role));
            var neutral = RocketItemCargoRoot.placed(null, UUID.randomUUID(), role);
            assertFalse(neutral.marked());
            assertEquals(neutral, RocketItemCargoRoot.decode(neutral.encode(), role));
        }
    }
}
