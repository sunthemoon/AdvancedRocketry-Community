package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchWorkQueueTest {
    @Test void sharedPaidBudgetAndLevelsAreFair() {
        var queue = new ResearchWorkQueue<String, String>();
        for (int i = 0; i < 40; i++) { assertTrue(queue.enqueue("earth", "e" + i)); assertTrue(queue.enqueue("moon", "m" + i)); }
        var order = new ArrayList<String>(); var first = queue.tick(entry -> { order.add(entry); return true; });
        assertEquals(new ResearchWorkQueue.Pass(32, 32, 48), first);
        assertEquals(16, order.stream().filter(entry -> entry.startsWith("e")).count());
        assertEquals(16, order.stream().filter(entry -> entry.startsWith("m")).count());
        assertEquals(List.of("e0", "m0", "e1", "m1"), order.subList(0, 4));
        order.clear(); queue.tick(entry -> { order.add(entry); return true; }); assertEquals("m16", order.get(0));
    }
    @Test void unpaidInspectionBudgetDeduplicationAndUnloadAreBounded() {
        var queue = new ResearchWorkQueue<String, Integer>();
        for (int i = 0; i < 100; i++) { assertTrue(queue.enqueue("a", i)); assertTrue(queue.enqueue("a", i)); }
        assertEquals(new ResearchWorkQueue.Pass(64, 0, 36), queue.tick(entry -> false));
        queue.unload("a"); assertEquals(new ResearchWorkQueue.Pass(0, 0, 0), queue.tick(entry -> true));
        assertTrue(queue.enqueue("b", 1)); queue.remove("b", 1); assertFalse(queue.pending("b", 1));
        for (int i = 0; i < ResearchWorkQueue.MAX_ENTRIES; i++) { assertTrue(queue.enqueue("c", i)); }
        assertFalse(queue.enqueue("d", -1)); queue.clear(); assertTrue(queue.enqueue("d", -1));
    }
}
