package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;

/** Physical port roles; concrete process channels come from the local pattern cell. */
public enum PrecisionAssemblerPortType {
    ITEM_INPUT("precision_assembler_item_input_port", "item_input",
            ProcessPortKind.ITEM, ProcessPortMode.INPUT,
            PrecisionAssemblerPortLayout.Role.ITEM_INPUT),
    ITEM_OUTPUT("precision_assembler_item_output_port", "item_output",
            ProcessPortKind.ITEM, ProcessPortMode.OUTPUT,
            PrecisionAssemblerPortLayout.Role.ITEM_OUTPUT),
    ENERGY_INPUT("precision_assembler_energy_input_port", "energy_input",
            ProcessPortKind.ENERGY, ProcessPortMode.INPUT,
            PrecisionAssemblerPortLayout.Role.ENERGY_INPUT);

    private final String registryPath;
    private final String patternChannel;
    private final ProcessPortKind kind;
    private final ProcessPortMode mode;
    private final PrecisionAssemblerPortLayout.Role layoutRole;

    PrecisionAssemblerPortType(
            String registryPath,
            String patternChannel,
            ProcessPortKind kind,
            ProcessPortMode mode,
            PrecisionAssemblerPortLayout.Role layoutRole
    ) {
        this.registryPath = registryPath;
        this.patternChannel = patternChannel;
        this.kind = kind;
        this.mode = mode;
        this.layoutRole = layoutRole;
    }

    public String registryPath() {
        return registryPath;
    }

    public String patternChannel() {
        return patternChannel;
    }

    public ProcessPortKind kind() {
        return kind;
    }

    public ProcessPortMode mode() {
        return mode;
    }

    public boolean accepts(PrecisionAssemblerPortLayout.Assignment assignment) {
        return layoutRole == assignment.role();
    }
}
