package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class ElevatorCapsuleCodecTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static CompoundTag tag(List<Entry> rides) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tether", true);
        ElevatorCapsuleCodec.encode(tag, rides);
        return tag;
    }

    @Test
    void exactTwoFieldEntriesRoundTripWithUnchangedTether() {
        List<Entry> rides = List.of(new Entry(0, 0), new Entry(Integer.MAX_VALUE, 100));
        CompoundTag update = tag(rides);
        assertEquals(rides, ElevatorCapsuleCodec.decode(update));
        assertTrue(update.getBoolean("tether"));
        assertEquals(2, update.size());
        ListTag list = (ListTag) update.get(ElevatorCapsuleCodec.KEY);
        assertEquals(2, list.getCompound(0).size());
        assertThrows(UnsupportedOperationException.class, () -> ElevatorCapsuleCodec.decode(update).clear());
    }

    @Test
    void limitUsesTheRealPerEndpointLimitAndNeverExceedsEight() {
        assertEquals(Math.min(8, ElevatorRules.PENDING_RIDES_PER_ENDPOINT), ElevatorCapsuleCodec.MAX_RIDES);
        List<Entry> rides = new ArrayList<>();
        for (int id = 0; id < ElevatorCapsuleCodec.MAX_RIDES; id++) {
            rides.add(new Entry(id, 100));
        }
        assertEquals(rides, ElevatorCapsuleCodec.decode(tag(rides)));
        rides.add(new Entry(8, 100));
        assertThrows(IllegalArgumentException.class, () -> tag(rides));
        CompoundTag oversized = tag(List.of());
        ListTag list = new ListTag();
        for (int i = 0; i < 9; i++) {
            list.add(new CompoundTag());
        }
        oversized.put(ElevatorCapsuleCodec.KEY, list);
        assertTrue(ElevatorCapsuleCodec.decode(oversized).isEmpty());
    }

    @Test
    void missingWrongTypeAndUnknownUpdateDecodeEmpty() {
        assertTrue(ElevatorCapsuleCodec.decode(null).isEmpty());
        assertTrue(ElevatorCapsuleCodec.decode(new CompoundTag()).isEmpty());
        assertTrue(ElevatorCapsuleCodec.decode(tag(List.of())).isEmpty());
        CompoundTag update = tag(List.of(new Entry(2, 100)));
        update.putInt("unknown", 1);
        assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty());
        update = tag(List.of());
        update.putInt(ElevatorCapsuleCodec.KEY, 1);
        assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty());
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(1));
        update.put(ElevatorCapsuleCodec.KEY, list);
        assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty());
    }

    @Test
    void malformedEntryRejectsWholeListIncludingItsValidPrefix() {
        List<Consumer<CompoundTag>> mutations = List.of(
                entry -> entry.remove("entity_id"), entry -> entry.remove("remaining_ticks"),
                entry -> entry.putLong("entity_id", 2), entry -> entry.putByte("remaining_ticks", (byte) 10),
                entry -> entry.putString("remaining_ticks", "10"), entry -> entry.putInt("unknown", 1),
                entry -> entry.putInt("entity_id", -1), entry -> entry.putInt("remaining_ticks", -1),
                entry -> entry.putInt("remaining_ticks", 101));
        for (Consumer<CompoundTag> mutation : mutations) {
            CompoundTag update = tag(List.of(new Entry(1, 10), new Entry(2, 100)));
            mutation.accept(((ListTag) update.get(ElevatorCapsuleCodec.KEY)).getCompound(1));
            assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty());
        }
    }

    @Test
    void duplicateRidersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> tag(List.of(new Entry(1, 10), new Entry(1, 50))));
        CompoundTag update = tag(List.of(new Entry(1, 10), new Entry(2, 50)));
        ((ListTag) update.get(ElevatorCapsuleCodec.KEY)).getCompound(1).putInt("entity_id", 1);
        assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty());
    }

    @Test
    void entryBoundsIncludeZeroAndRejectNegativeAndOverCountdown() {
        assertThrows(IllegalArgumentException.class, () -> new Entry(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new Entry(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new Entry(1, 101));
        for (int ticks = 0; ticks <= 100; ticks++) {
            assertEquals(List.of(new Entry(1, ticks)), ElevatorCapsuleCodec.decode(tag(List.of(new Entry(1, ticks)))));
        }
    }

    @Test
    void clientCountdownDropsAtZeroAndDoesNotUseServerGameTime() {
        ElevatorCapsuleCountdown state = new ElevatorCapsuleCountdown();
        state.update(List.of(new Entry(1, 100), new Entry(2, 1), new Entry(3, 0)), 20);
        assertEquals(List.of(new Entry(1, 100), new Entry(2, 1)), state.live(20));
        assertEquals(List.of(new Entry(1, 99)), state.live(21));
        assertEquals(List.of(new Entry(1, 1)), state.live(119));
        assertTrue(state.live(120).isEmpty());
        assertTrue(state.live(1000).isEmpty());
    }

    @Test
    void repeatedSnapshotsCannotExtendAnUninterruptedLeaseBeyond300Ticks() {
        ElevatorCapsuleCountdown state = new ElevatorCapsuleCountdown();
        for (int tick = 0; tick < 300; tick++) {
            state.update(List.of(new Entry(1, 100)), tick);
            assertEquals(List.of(new Entry(1, 100)), state.live(tick));
        }
        state.update(List.of(new Entry(1, 100)), 300);
        assertTrue(state.live(300).isEmpty());
        assertEquals(300, ElevatorCapsuleCountdown.LEASE_TICKS);
    }

    @Test
    void omittedMalformedCancelledAndCommittedSnapshotsClearImmediately() {
        ElevatorCapsuleCountdown state = new ElevatorCapsuleCountdown();
        state.update(List.of(new Entry(1, 100), new Entry(2, 100)), 0);
        state.update(List.of(new Entry(2, 60)), 10);
        assertEquals(List.of(new Entry(2, 60)), state.live(10));
        state.update(ElevatorCapsuleCodec.decode(new CompoundTag()), 11);
        assertTrue(state.live(11).isEmpty());
        state.update(List.of(new Entry(1, 100)), 12);
        state.update(List.of(), 13);
        assertTrue(state.live(13).isEmpty());
    }

    @Test
    void unloadClearInvalidEntityAndBackwardClockDropState() {
        ElevatorCapsuleCountdown state = new ElevatorCapsuleCountdown();
        state.update(List.of(new Entry(1, 100), new Entry(2, 100)), 20);
        state.discard(1);
        assertEquals(List.of(new Entry(2, 100)), state.live(20));
        assertTrue(state.live(19).isEmpty());
        state.update(List.of(new Entry(1, 100)), 30);
        state.clear();
        assertTrue(state.live(30).isEmpty());
    }
}
