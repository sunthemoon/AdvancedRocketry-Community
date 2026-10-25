package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SealedRecoveryAtomicBudgetTest {
    @Test void aReadyRecordCanStillUseTheExistingAllowanceForANonResumableEntityCensus() {
        var budget = new SealedWorkBudget();
        var selection = new SealedRecoverySelection();
        var ids = List.of(new UUID(0, 1), new UUID(0, 2));
        var lazy = new SealedLazyWork<UUID>(new SealedLazyWork.Port<>() {
            public UUID discover() { return null; }
            public void attempt(UUID hint, SealedWorkBudget allowance) { fail(); }
        });
        long[] now = {0};
        var port = (SealedDisassembly.Port) Proxy.newProxyInstance(SealedDisassembly.Port.class.getClassLoader(),
                new Class<?>[] {SealedDisassembly.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "now" -> now[0];
                    case "gate" -> SealedDisassembly.Gate.READY;
                    case "inspect" -> SealedDisassembly.Cell.OWNED;
                    case "entity" -> {
                        boolean complete = true;
                        for (int visit = 0; visit < 240; visit++) { complete &= budget.take(); }
                        yield complete ? SealedDisassembly.Entity.ABSENT : SealedDisassembly.Entity.UNLOADED;
                    }
                    case "held", "removeRecord", "checkpoint", "requestProof", "elect", "retain" -> null;
                    default -> true;
                });
        var machine = SealedDisassembly.recover(1, port, true);
        for (int tick = 0; tick < 20_000; tick++) {
            now[0] = tick; budget.beginTick(tick);
            var selected = selection.select(tick, ids, id -> true).orElseThrow();
            boolean lazyFirst = (tick & 1) == 0;
            if (lazyFirst) { lazy.tick(budget); }
            if (budget.remaining() > 0) {
                selection.serviced(selected);
                if (selected.equals(ids.get(0))) { while (machine.step(budget)) { } }
            }
            if (!lazyFirst) { lazy.tick(budget); }
        }
        assertEquals(SealedDisassembly.Result.SEALED, machine.result());
    }
}
