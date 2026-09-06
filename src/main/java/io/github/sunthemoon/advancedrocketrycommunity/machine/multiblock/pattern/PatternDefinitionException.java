package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

/** Bounded, reader-facing rejection for an invalid pattern definition. */
public final class PatternDefinitionException extends IllegalArgumentException {
    public PatternDefinitionException(String message) {
        super(message);
    }

    public PatternDefinitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
