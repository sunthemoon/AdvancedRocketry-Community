package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Coordinator composition regression extended from the independent RecoveryBudgetProbe. */
class SealedRecoveryBudgetTest {
    @ParameterizedTest
    @CsvSource({"1,0", "2,0", "2,1", "3,0", "3,1", "3,2", "4,0", "4,1", "4,2", "4,3"})
    void readyRecordFinishesRegardlessOfSortedPositionWhileOtherRecordsStayHeld(int count, int readyIndex) {
        for (var hold : List.of(SealedDisassembly.Gate.CONFLICT, SealedDisassembly.Gate.UNLOADED)) {
            var selection = new SealedRecoverySelection();
            var budget = new SealedWorkBudget();
            int[] discovery = {0};
            var lazy = new SealedLazyWork<UUID>(new SealedLazyWork.Port<>() {
                public UUID discover() { discovery[0]++; return null; }
                public void attempt(UUID id, SealedWorkBudget allowance) { fail("No carriers in this fixture"); }
            });
            long[] now = {0};
            var ids = new ArrayList<UUID>();
            var machines = new ArrayList<SealedDisassembly>();
            int[] serviced = new int[count];
            for (int index = 0; index < count; index++) {
                ids.add(new UUID(0, index + 1));
                boolean ready = index == readyIndex;
                var port = (SealedDisassembly.Port) Proxy.newProxyInstance(SealedDisassembly.Port.class.getClassLoader(),
                        new Class<?>[] {SealedDisassembly.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "now" -> now[0];
                            case "gate" -> ready ? SealedDisassembly.Gate.READY : hold;
                            case "inspect" -> SealedDisassembly.Cell.OWNED;
                            case "entity" -> SealedDisassembly.Entity.ABSENT;
                            case "held", "removeRecord", "checkpoint", "requestProof", "elect", "retain" -> null;
                            default -> true;
                        });
                machines.add(SealedDisassembly.recover(1, port, true));
            }
            boolean lazyFirst = false;
            for (int tick = 0; tick < 20_000; tick++) {
                now[0] = tick; budget.beginTick(tick);
                var selected = selection.select(tick, ids, candidate -> true).orElseThrow();
                int index = ids.indexOf(selected);
                lazyFirst = !lazyFirst;
                if (lazyFirst) { lazy.tickBeforeRecovery(budget); }
                // Each disjoint record's preparation and state machine use the same owner allowance.
                budget.runLimitedTo(budget.remaining() / 2, () -> {
                    if (budget.take()) { serviced[index]++; }
                    while (machines.get(index).step(budget)) { }
                });
                // A competing maximal live footprint may use all visits left after selected recovery.
                while (budget.take()) { }
                if (!lazyFirst) { lazy.tick(budget); }
                assertFalse(budget.take(), "Lazy discovery must consume only the remaining shared allowance");
            }
            assertEquals(SealedDisassembly.Result.SEALED, machines.get(readyIndex).result(),
                    "Ready record starved: count=" + count + ", sorted index=" + readyIndex + ", hold=" + hold);
            assertTrue(discovery[0] > 0, "Discovery was disabled");
            for (int index = 0; index < count; index++) {
                assertTrue(serviced[index] > 0, "A held record was skipped");
                if (index != readyIndex) { assertNull(machines.get(index).result(), "Held record was discarded"); }
            }
        }
    }
}
