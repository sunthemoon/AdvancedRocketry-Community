package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

/** Stable channels assigned by local pattern position, never by placement order. */
public final class PrecisionAssemblerChannels {
    public static final int INPUT_COUNT = 5;
    public static final int OUTPUT_COUNT = 2;
    public static final String ENERGY_INPUT = "energy_input";

    private PrecisionAssemblerChannels() {
    }

    public static String input(int index) {
        if (index < 0 || index >= INPUT_COUNT) {
            throw new IllegalArgumentException("input port index is outside the pattern");
        }
        return "item_input_" + index;
    }

    public static String output(int index) {
        if (index < 0 || index >= OUTPUT_COUNT) {
            throw new IllegalArgumentException("output port index is outside the pattern");
        }
        return "item_output_" + index;
    }
}
