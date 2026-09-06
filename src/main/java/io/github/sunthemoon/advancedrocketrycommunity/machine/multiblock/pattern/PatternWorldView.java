package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

/** Read-only adapter that must not load a chunk while observing a position. */
@FunctionalInterface
public interface PatternWorldView {
    PatternObservation observe(PatternPosition worldPosition);
}
