package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;

/** Fixed typed ports used by the first representative multiblock machine. */
public enum RollingMachinePortType {
    ITEM_INPUT("rolling_machine_item_input_port", "item_input", ProcessPortKind.ITEM, ProcessPortMode.INPUT),
    FLUID_INPUT("rolling_machine_fluid_input_port", "fluid_input", ProcessPortKind.FLUID, ProcessPortMode.INPUT),
    ENERGY_INPUT("rolling_machine_energy_input_port", "energy_input", ProcessPortKind.ENERGY, ProcessPortMode.INPUT),
    ITEM_OUTPUT("rolling_machine_item_output_port", "item_output", ProcessPortKind.ITEM, ProcessPortMode.OUTPUT);

    private final String registryPath;
    private final String channel;
    private final ProcessPortKind kind;
    private final ProcessPortMode mode;

    RollingMachinePortType(
            String registryPath,
            String channel,
            ProcessPortKind kind,
            ProcessPortMode mode
    ) {
        this.registryPath = registryPath;
        this.channel = channel;
        this.kind = kind;
        this.mode = mode;
    }

    public String registryPath() {
        return registryPath;
    }

    public String channel() {
        return channel;
    }

    public ProcessPortKind kind() {
        return kind;
    }

    public ProcessPortMode mode() {
        return mode;
    }
}
