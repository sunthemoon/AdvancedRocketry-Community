package io.github.sunthemoon.advancedrocketrycommunity.sound;

import javax.annotation.Nullable;

/** Common-side read-only projection for the physical client's existing loop manager. */
public interface ProcessingSoundSource {
    @Nullable
    ClassicSound activeProcessingSound();

    boolean processingActive();
}
