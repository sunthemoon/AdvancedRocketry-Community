package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RawTickTimingsTest {
    @Test void unfilledSlotsAreOmittedEvenWhenNonzeroAndRealZerosAreRetained() {
        long[] vanilla = new long[100];
        long[] post = new long[100];
        Arrays.fill(vanilla, 999);
        Arrays.fill(post, 999);
        vanilla[1] = 10; post[1] = 2;
        vanilla[2] = 0; post[2] = 0;
        vanilla[3] = 30; post[3] = 7;
        assertEquals("ticks_raw tick=4 entries=3 data=1:12,2:0,3:37",
                RawTickTimings.format(4, 3, vanilla, post));
        assertEquals("ticks_raw tick=0 entries=0 data=", RawTickTimings.format(0, 0, vanilla, post));
        assertEquals("ticks_raw tick=1 entries=0 data=", RawTickTimings.format(1, 0, vanilla, post));
    }

    @Test void everyEntryMapsAcrossTheRingBoundaryAndAddsOnlyItsOwnPostWork() {
        for (int completed : new int[] {99, 100, 101, 199, 200, 237}) {
            long[] vanilla = new long[100];
            long[] post = new long[100];
            int count = Math.min(completed, 100);
            StringBuilder expected = new StringBuilder("ticks_raw tick=").append(completed)
                    .append(" entries=").append(count).append(" data=");
            for (int tick = completed - count + 1; tick <= completed; tick++) {
                vanilla[tick % 100] = tick * 1_000L;
                post[tick % 100] = tick;
                if (tick != completed - count + 1) { expected.append(','); }
                expected.append(tick).append(':').append(tick * 1_001L);
            }
            assertEquals(expected.toString(), RawTickTimings.format(completed, completed, vanilla, post));
            assertEquals(expected.toString().replace("tick=" + completed + " ", "tick=" + (completed + 1) + " "),
                    RawTickTimings.format(completed + 1, completed, vanilla, post));
        }
    }

    @Test void maximumTickAndDurationRemainBoundedWithoutIntegerWrap() {
        long[] vanilla = new long[100];
        long[] post = new long[100];
        Arrays.fill(vanilla, Long.MAX_VALUE - 1);
        Arrays.fill(post, 1);
        String line = RawTickTimings.format(Integer.MAX_VALUE, Integer.MAX_VALUE, vanilla, post);
        assertTrue(line.startsWith("ticks_raw tick=2147483647 entries=100 data=2147483548:"));
        assertTrue(line.endsWith("2147483647:" + Long.MAX_VALUE));
        assertEquals(100, line.substring(line.indexOf("data=") + 5).split(",").length);
        assertTrue(line.length() + "ARCE_RELEASE_TEST ".length() < 4096);
    }

    @Test void invalidSnapshotsAndDurationOverflowAreRefused() {
        long[] vanilla = new long[100];
        long[] post = new long[100];
        assertThrows(IllegalArgumentException.class, () -> RawTickTimings.format(1, 2, vanilla, post));
        assertThrows(IllegalArgumentException.class, () -> RawTickTimings.format(0, -1, vanilla, post));
        assertThrows(IllegalArgumentException.class, () -> RawTickTimings.format(1, 1, new long[99], post));
        vanilla[1] = -1;
        assertThrows(IllegalArgumentException.class, () -> RawTickTimings.format(1, 1, vanilla, post));
        vanilla[1] = Long.MAX_VALUE; post[1] = 1;
        assertThrows(ArithmeticException.class, () -> RawTickTimings.format(1, 1, vanilla, post));
    }
}
