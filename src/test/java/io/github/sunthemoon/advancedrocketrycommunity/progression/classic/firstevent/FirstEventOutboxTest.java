package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W44 A0: the source outbox shape and its forward-only slot (sections 1.1 and 2.1, invariant I4). */
class FirstEventOutboxTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void emptyElectedAndDeliveredEncodeExactly() {
        CompoundTag empty = FirstEventOutbox.empty().encode(FirstEventKind.FIRST_STATION_WARP);
        assertEquals(Set.of("schema_version", "state"), empty.getAllKeys());
        assertEquals("EMPTY", empty.getString("state"));
        assertEquals(FirstEventOutbox.empty(), FirstEventOutbox.decode(empty, FirstEventKind.FIRST_MOON_LANDING));
        var event = moon(1, List.of(id(5)));
        var elected = FirstEventOutbox.elected(event);
        var delivered = elected.delivered();
        for (FirstEventOutbox outbox : List.of(elected, delivered)) {
            CompoundTag tag = outbox.encode(FirstEventKind.FIRST_MOON_LANDING);
            assertEquals(Set.of("schema_version", "state", "event"), tag.getAllKeys());
            assertEquals(outbox, FirstEventOutbox.decode(tag, FirstEventKind.FIRST_MOON_LANDING));
            assertThrows(IllegalArgumentException.class, () -> FirstEventOutbox.decode(tag, FirstEventKind.FIRST_STATION_WARP));
        }
        assertThrows(IllegalArgumentException.class, () -> elected.encode(FirstEventKind.FIRST_STATION_WARP));
    }

    @Test void malformedSlotsAreRefused() {
        var elected = FirstEventOutbox.elected(warp(1, List.of(id(4)))).encode(FirstEventKind.FIRST_STATION_WARP);
        refuse(elected, tag -> tag.putInt("schema_version", 2));
        refuse(elected, tag -> tag.putString("state", "LANDED"));
        refuse(elected, tag -> tag.remove("event"));
        refuse(elected, tag -> tag.putInt("extra", 1));
        refuse(elected, tag -> tag.put("event", IntTag.valueOf(1)));
        CompoundTag empty = FirstEventOutbox.empty().encode(FirstEventKind.FIRST_STATION_WARP);
        refuse(empty, tag -> tag.put("event", elected.getCompound("event").copy()));
        assertThrows(IllegalArgumentException.class, () -> FirstEventOutbox.decode(IntTag.valueOf(1),
                FirstEventKind.FIRST_STATION_WARP));
        assertThrows(IllegalArgumentException.class, () -> new FirstEventOutbox(FirstEventOutbox.State.ELECTED, null));
        assertThrows(IllegalArgumentException.class, () -> new FirstEventOutbox(FirstEventOutbox.State.EMPTY,
                warp(1, List.of())));
    }

    @Test void theSlotOnlyMovesForwardAndKeepsItsEvent() {
        var first = FirstEventOutbox.elected(warp(1, List.of()));
        var other = FirstEventOutbox.elected(warp(2, List.of()));
        assertTrue(FirstEventOutbox.empty().precedesOrEquals(first));
        assertTrue(first.precedesOrEquals(first.delivered()));
        assertFalse(first.delivered().precedesOrEquals(first));
        assertFalse(first.precedesOrEquals(other), "a second winner is never a forward move");
        assertFalse(first.precedesOrEquals(FirstEventOutbox.empty()));
        assertThrows(IllegalStateException.class, () -> first.delivered().delivered());
        assertThrows(IllegalStateException.class, () -> FirstEventOutbox.empty().delivered());
    }

    private static void refuse(CompoundTag valid, java.util.function.Consumer<CompoundTag> mutation) {
        CompoundTag copy = valid.copy();
        mutation.accept(copy);
        assertThrows(IllegalArgumentException.class, () -> FirstEventOutbox.decode(copy, FirstEventKind.FIRST_STATION_WARP));
    }
}
