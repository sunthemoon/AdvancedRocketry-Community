package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

public enum PatternDiagnosticReason {
    BLOCK_MISMATCH("block_mismatch"),
    CHUNK_NOT_LOADED("chunk_not_loaded"),
    TRANSFORM_NOT_ALLOWED("transform_not_allowed"),
    POSITION_OVERFLOW("position_overflow");

    private final String code;

    PatternDiagnosticReason(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
