package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

/** Native named-empty-root bytes, depth and node count; not Tag.sizeInBytes(). */
record ClassicNbtLimits(int bytes, int depth, int nodes) {
    ClassicNbtLimits {
        ClassicValueChecks.require(bytes >= 3 && depth >= 1 && nodes >= 1, "Invalid NBT limits");
    }

    static final ClassicNbtLimits HATCH = new ClassicNbtLimits(4_096, 12, 256);
    static final ClassicNbtLimits RESOURCES = new ClassicNbtLimits(32_768, 16, 8_192);
    static final ClassicNbtLimits MACHINE = new ClassicNbtLimits(65_536, 24, 16_384);
    static final ClassicNbtLimits JOURNAL = new ClassicNbtLimits(65_536, 20, 4_096);
    static final ClassicNbtLimits MARKER = new ClassicNbtLimits(1_024, 16, 256);
    static final ClassicNbtLimits CONTROLLER = new ClassicNbtLimits(164_951, 25, 28_929);
    static final ClassicNbtLimits REJECTED = new ClassicNbtLimits(1_873_117, 25, 86_529);
}
