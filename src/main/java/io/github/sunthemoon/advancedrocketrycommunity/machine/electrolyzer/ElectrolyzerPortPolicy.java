package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortFilter;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import java.util.List;
import java.util.Set;

/** Frozen v0.2 side behavior expressed through the shared bounded port contract. */
final class ElectrolyzerPortPolicy {
    static final String ITEM_INPUT_CHANNEL = "item_input";
    static final String CHARGE_INPUT_CHANNEL = "charge_input";
    static final String ITEM_OUTPUT_CHANNEL = "item_output";
    static final String FLUID_INPUT_CHANNEL = "fluid_input";
    static final String ENERGY_INPUT_CHANNEL = "energy_input";

    private static final Set<ProcessPortSide> HORIZONTAL = Set.of(
            ProcessPortSide.FRONT,
            ProcessPortSide.BACK,
            ProcessPortSide.LEFT,
            ProcessPortSide.RIGHT
    );

    static final ProcessPortDefinition UNSIDED_ITEMS = port(
            "inventory",
            ProcessPortKind.ITEM,
            ProcessPortMode.BIDIRECTIONAL,
            Set.of(ProcessPortSide.UNSIDED),
            0,
            ElectrolyzerBlockEntity.SLOT_COUNT,
            ProcessPortFilter.any()
    );
    static final ProcessPortDefinition TOP_INPUT = port(
            ITEM_INPUT_CHANNEL,
            ProcessPortKind.ITEM,
            ProcessPortMode.BIDIRECTIONAL,
            Set.of(ProcessPortSide.TOP),
            ElectrolyzerBlockEntity.SLOT_INPUT,
            1,
            ProcessPortFilter.exact(Set.of("advancedrocketrycommunity:empty_canister"))
    );
    static final ProcessPortDefinition SIDE_CHARGE = port(
            CHARGE_INPUT_CHANNEL,
            ProcessPortKind.ITEM,
            ProcessPortMode.INPUT,
            HORIZONTAL,
            ElectrolyzerBlockEntity.SLOT_CHARGE,
            1,
            ProcessPortFilter.exact(Set.of("minecraft:redstone"))
    );
    static final ProcessPortDefinition BOTTOM_OUTPUT = port(
            ITEM_OUTPUT_CHANNEL,
            ProcessPortKind.ITEM,
            ProcessPortMode.OUTPUT,
            Set.of(ProcessPortSide.BOTTOM),
            ElectrolyzerBlockEntity.SLOT_HYDROGEN,
            2,
            ProcessPortFilter.exact(Set.of(
                    "advancedrocketrycommunity:hydrogen_canister",
                    "advancedrocketrycommunity:oxygen_canister"
            ))
    );
    static final ProcessPortDefinition UNSIDED_FLUID = port(
            FLUID_INPUT_CHANNEL,
            ProcessPortKind.FLUID,
            ProcessPortMode.INPUT,
            Set.of(ProcessPortSide.UNSIDED),
            0,
            1,
            ProcessPortFilter.exact(Set.of("minecraft:water"))
    );
    static final ProcessPortDefinition SIDE_FLUID = port(
            FLUID_INPUT_CHANNEL,
            ProcessPortKind.FLUID,
            ProcessPortMode.INPUT,
            HORIZONTAL,
            0,
            1,
            ProcessPortFilter.exact(Set.of("minecraft:water"))
    );
    static final ProcessPortDefinition UNSIDED_ENERGY = port(
            ENERGY_INPUT_CHANNEL,
            ProcessPortKind.ENERGY,
            ProcessPortMode.INPUT,
            Set.of(ProcessPortSide.UNSIDED),
            0,
            1,
            ProcessPortFilter.any()
    );
    static final ProcessPortDefinition SIDE_ENERGY = port(
            ENERGY_INPUT_CHANNEL,
            ProcessPortKind.ENERGY,
            ProcessPortMode.INPUT,
            HORIZONTAL,
            0,
            1,
            ProcessPortFilter.any()
    );

    static final ProcessPortPolicy POLICY = new ProcessPortPolicy(List.of(
            UNSIDED_ITEMS,
            TOP_INPUT,
            SIDE_CHARGE,
            BOTTOM_OUTPUT,
            UNSIDED_FLUID,
            SIDE_FLUID,
            UNSIDED_ENERGY,
            SIDE_ENERGY
    ));

    private ElectrolyzerPortPolicy() {
    }

    private static ProcessPortDefinition port(
            String channel,
            ProcessPortKind kind,
            ProcessPortMode mode,
            Set<ProcessPortSide> sides,
            int first,
            int count,
            ProcessPortFilter filter
    ) {
        return new ProcessPortDefinition(
                channel,
                kind,
                mode,
                sides,
                new ProcessPortRange(first, count),
                filter
        );
    }
}
