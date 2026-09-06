package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProcessPortPolicyTest {
    @Test
    void modeAndLockRulesAreExplicitForEveryKind() {
        ProcessPortDefinition itemInput = port(ProcessPortKind.ITEM, ProcessPortMode.INPUT, "item");
        ProcessPortDefinition itemOutput = port(ProcessPortKind.ITEM, ProcessPortMode.OUTPUT, "output");
        ProcessPortDefinition itemBoth = port(ProcessPortKind.ITEM, ProcessPortMode.BIDIRECTIONAL, "both");
        ProcessPortDefinition energyInput = port(ProcessPortKind.ENERGY, ProcessPortMode.INPUT, "energy");

        assertTrue(itemInput.canInsert(false));
        assertFalse(itemInput.canExtract(false));
        assertFalse(itemInput.canInsert(true));
        assertFalse(itemOutput.canInsert(false));
        assertTrue(itemOutput.canExtract(true));
        assertTrue(itemBoth.canInsert(false));
        assertTrue(itemBoth.canExtract(false));
        assertFalse(itemBoth.canInsert(true));
        assertFalse(itemBoth.canExtract(true));
        assertTrue(energyInput.canInsert(true));
    }

    @Test
    void policyRejectsSixtyFifthPortAndDuplicateIdentity() {
        List<ProcessPortDefinition> ports = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            ports.add(port(ProcessPortKind.ITEM, ProcessPortMode.INPUT, "port" + index));
        }
        assertEquals(64, new ProcessPortPolicy(ports).ports().size());
        ports.add(port(ProcessPortKind.ITEM, ProcessPortMode.INPUT, "overflow"));
        assertThrows(IllegalArgumentException.class, () -> new ProcessPortPolicy(ports));

        ProcessPortDefinition duplicate = port(ProcessPortKind.FLUID, ProcessPortMode.INPUT, "fluid");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessPortPolicy(List.of(duplicate, duplicate))
        );
        ProcessPortDefinition disjoint = new ProcessPortDefinition(
                "fluid",
                ProcessPortKind.FLUID,
                ProcessPortMode.INPUT,
                Set.of(ProcessPortSide.TOP),
                new ProcessPortRange(1, 1),
                ProcessPortFilter.any()
        );
        assertEquals(2, new ProcessPortPolicy(List.of(duplicate, disjoint)).ports().size());
    }

    @Test
    void rangeFilterAndUnsideBoundsAreEnforced() {
        assertEquals(64, new ProcessPortRange(63, 1).endExclusive());
        assertThrows(IllegalArgumentException.class, () -> new ProcessPortRange(63, 2));
        assertThrows(IllegalArgumentException.class, () -> new ProcessPortRange(0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessPortDefinition(
                        "item",
                        ProcessPortKind.ITEM,
                        ProcessPortMode.INPUT,
                        Set.of(ProcessPortSide.UNSIDED, ProcessPortSide.TOP),
                        new ProcessPortRange(0, 1),
                        ProcessPortFilter.any()
                )
        );
        assertTrue(ProcessPortFilter.exact(Set.of("minecraft:iron_ingot")).allows("minecraft:iron_ingot"));
        assertFalse(ProcessPortFilter.exact(Set.of("minecraft:iron_ingot")).allows("minecraft:gold_ingot"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessPortFilter(true, Set.of("minecraft:iron_ingot"))
        );
    }

    @Test
    void revisionAdvancesOnlyWhenExplicitlyRecorded() {
        AtomicInteger callbacks = new AtomicInteger();
        ProcessPortRevision revision = new ProcessPortRevision(9, callbacks::incrementAndGet);

        revision.recordMutation();

        assertEquals(10, revision.value());
        assertEquals(1, callbacks.get());
    }

    private static ProcessPortDefinition port(ProcessPortKind kind, ProcessPortMode mode, String channel) {
        return new ProcessPortDefinition(
                channel,
                kind,
                mode,
                Set.of(ProcessPortSide.TOP),
                new ProcessPortRange(0, 1),
                ProcessPortFilter.any()
        );
    }
}
