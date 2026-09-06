package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;
import java.util.Optional;

/** Bounded diagnostic with actual block data only for an already-loaded position. */
public record PatternDiagnostic(
        PatternDiagnosticReason reason,
        PatternPosition localPosition,
        PatternPosition worldPosition,
        String expected,
        Optional<String> actualBlockId
) {
    public PatternDiagnostic {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(localPosition, "localPosition");
        Objects.requireNonNull(worldPosition, "worldPosition");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actualBlockId, "actualBlockId");
        if (expected.length() > PatternIds.MAX_ID_CHARS + 16) {
            throw new IllegalArgumentException("expected matcher summary exceeds the diagnostic limit");
        }
        actualBlockId.ifPresent(id -> PatternIds.requireResourceId("actualBlockId", id));
        if (reason == PatternDiagnosticReason.CHUNK_NOT_LOADED && actualBlockId.isPresent()) {
            throw new IllegalArgumentException("unloaded diagnostics cannot contain an actual block ID");
        }
    }
}
