package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ElectrolyzerPortPolicyTest {
    private static final Set<ProcessPortSide> HORIZONTAL = Set.of(
            ProcessPortSide.FRONT,
            ProcessPortSide.BACK,
            ProcessPortSide.LEFT,
            ProcessPortSide.RIGHT
    );

    @Test
    void v120Elec004PolicyPreservesEveryLegacySideRangeModeAndFilter() {
        assertEquals(
                List.of(
                        ElectrolyzerPortPolicy.UNSIDED_ITEMS,
                        ElectrolyzerPortPolicy.TOP_INPUT,
                        ElectrolyzerPortPolicy.SIDE_CHARGE,
                        ElectrolyzerPortPolicy.BOTTOM_OUTPUT,
                        ElectrolyzerPortPolicy.UNSIDED_FLUID,
                        ElectrolyzerPortPolicy.SIDE_FLUID,
                        ElectrolyzerPortPolicy.UNSIDED_ENERGY,
                        ElectrolyzerPortPolicy.SIDE_ENERGY
                ),
                ElectrolyzerPortPolicy.POLICY.ports()
        );
        assertPort(ElectrolyzerPortPolicy.UNSIDED_ITEMS, "inventory", ProcessPortKind.ITEM,
                ProcessPortMode.BIDIRECTIONAL, Set.of(ProcessPortSide.UNSIDED), 0, 4);
        assertPort(ElectrolyzerPortPolicy.TOP_INPUT, "item_input", ProcessPortKind.ITEM,
                ProcessPortMode.BIDIRECTIONAL, Set.of(ProcessPortSide.TOP), 0, 1);
        assertPort(ElectrolyzerPortPolicy.SIDE_CHARGE, "charge_input", ProcessPortKind.ITEM,
                ProcessPortMode.INPUT, HORIZONTAL, 1, 1);
        assertPort(ElectrolyzerPortPolicy.BOTTOM_OUTPUT, "item_output", ProcessPortKind.ITEM,
                ProcessPortMode.OUTPUT, Set.of(ProcessPortSide.BOTTOM), 2, 2);
        assertPort(ElectrolyzerPortPolicy.UNSIDED_FLUID, "fluid_input", ProcessPortKind.FLUID,
                ProcessPortMode.INPUT, Set.of(ProcessPortSide.UNSIDED), 0, 1);
        assertPort(ElectrolyzerPortPolicy.SIDE_FLUID, "fluid_input", ProcessPortKind.FLUID,
                ProcessPortMode.INPUT, HORIZONTAL, 0, 1);
        assertPort(ElectrolyzerPortPolicy.UNSIDED_ENERGY, "energy_input", ProcessPortKind.ENERGY,
                ProcessPortMode.INPUT, Set.of(ProcessPortSide.UNSIDED), 0, 1);
        assertPort(ElectrolyzerPortPolicy.SIDE_ENERGY, "energy_input", ProcessPortKind.ENERGY,
                ProcessPortMode.INPUT, HORIZONTAL, 0, 1);

        assertTrue(ElectrolyzerPortPolicy.TOP_INPUT.filter()
                .allows("advancedrocketrycommunity:empty_canister"));
        assertFalse(ElectrolyzerPortPolicy.TOP_INPUT.filter().allows("minecraft:redstone"));
        assertTrue(ElectrolyzerPortPolicy.SIDE_CHARGE.filter().allows("minecraft:redstone"));
        assertTrue(ElectrolyzerPortPolicy.BOTTOM_OUTPUT.filter()
                .allows("advancedrocketrycommunity:hydrogen_canister"));
        assertTrue(ElectrolyzerPortPolicy.BOTTOM_OUTPUT.filter()
                .allows("advancedrocketrycommunity:oxygen_canister"));
        assertFalse(ElectrolyzerPortPolicy.BOTTOM_OUTPUT.filter()
                .allows("advancedrocketrycommunity:empty_canister"));
        assertTrue(ElectrolyzerPortPolicy.SIDE_FLUID.filter().allows("minecraft:water"));
        assertTrue(ElectrolyzerPortPolicy.SIDE_ENERGY.filter().allowAny());
    }

    private static void assertPort(
            ProcessPortDefinition port,
            String channel,
            ProcessPortKind kind,
            ProcessPortMode mode,
            Set<ProcessPortSide> sides,
            int first,
            int count
    ) {
        assertEquals(channel, port.channel());
        assertEquals(kind, port.kind());
        assertEquals(mode, port.mode());
        assertEquals(sides, port.localSides());
        assertEquals(first, port.range().first());
        assertEquals(count, port.range().count());
    }
}
