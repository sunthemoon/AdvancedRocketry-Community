package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SealedLazyWorkTest {
    @Test void hintsAreBoundedDeduplicatedAndOverflowIsRediscovered() throws Exception {
        var attempted = new HashSet<Integer>(); var next = new AtomicInteger();
        var queue = queue(next, attempted);
        for (int index = 0; index < 64; index++) { assertTrue(hint(queue, index)); }
        assertTrue(hint(queue, 3)); assertEquals(64, size(queue)); assertFalse(hint(queue, 64));
        var budget = new SealedWorkBudget();
        for (int tick = 0; tick < 4; tick++) { budget.beginTick(tick); run(queue, budget); assertTrue(size(queue) <= 64); }
        for (int index = 0; index < 70; index++) { assertTrue(attempted.contains(index), "Missing candidate " + index); }
    }

    @Test void discoveryAndConsumptionShareTheCallersRemainingAllowance() throws Exception {
        var attempted = new HashSet<Integer>(); var next = new AtomicInteger(); var queue = queue(next, attempted);
        var budget = new SealedWorkBudget(); budget.beginTick(1);
        for (int i = 0; i < 250; i++) { assertTrue(budget.take()); }
        run(queue, budget);
        assertEquals(3, next.get()); assertEquals(Set.of(0, 1, 2), attempted); assertFalse(budget.take());
        budget.beginTick(1); run(queue, budget); assertEquals(3, next.get());
        budget.beginTick(2); run(queue, budget); assertTrue(next.get() > 3);
    }

    @Test void heldCandidatesDoNotOccupyEveryHintForever() throws Exception {
        var attempted = new HashSet<Integer>(); var next = new AtomicInteger(); var queue = queue(next, attempted);
        for (int index = 1_000; index < 1_064; index++) { assertTrue(hint(queue, index)); }
        var budget = new SealedWorkBudget(); budget.beginTick(1); run(queue, budget);
        assertTrue(attempted.contains(1_063));
        budget.beginTick(2); run(queue, budget); assertTrue(attempted.contains(0));
        queue.getClass().getMethod("clear").invoke(queue); assertEquals(0, size(queue));
    }

    private static Object queue(AtomicInteger next, Set<Integer> attempted) throws Exception {
        var type = Class.forName(SealedLazyWorkTest.class.getPackageName() + ".SealedLazyWork");
        var port = Class.forName(type.getName() + "$Port");
        var calls = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[] {port}, (proxy, method, arguments) -> {
            return switch (method.getName()) {
                case "discover" -> next.getAndIncrement() % 70;
                case "attempt" -> { attempted.add((Integer) arguments[0]); yield null; }
                default -> throw new AssertionError(method.getName());
            };
        });
        return type.getConstructor(port).newInstance(calls);
    }
    private static boolean hint(Object queue, int value) throws Exception {
        return (boolean) queue.getClass().getMethod("hint", Object.class).invoke(queue, value);
    }
    private static int size(Object queue) throws Exception { return (int) queue.getClass().getMethod("size").invoke(queue); }
    private static void run(Object queue, SealedWorkBudget budget) throws Exception {
        queue.getClass().getMethod("tick", SealedWorkBudget.class).invoke(queue, budget);
    }
}
