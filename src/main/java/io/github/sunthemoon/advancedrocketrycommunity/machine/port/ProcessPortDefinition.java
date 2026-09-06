package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.Objects;
import java.util.Set;

public record ProcessPortDefinition(
        String channel,
        ProcessPortKind kind,
        ProcessPortMode mode,
        Set<ProcessPortSide> localSides,
        ProcessPortRange range,
        ProcessPortFilter filter
) {
    public ProcessPortDefinition {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(filter, "filter");
        channel = new ProcessResourceKey(ProcessResourceKind.ITEM, channel, "port:validation").channel();
        localSides = Set.copyOf(Objects.requireNonNull(localSides, "localSides"));
        if (localSides.isEmpty()) {
            throw new IllegalArgumentException("a port must expose at least one local side");
        }
        if (localSides.contains(ProcessPortSide.UNSIDED) && localSides.size() != 1) {
            throw new IllegalArgumentException("UNSIDED cannot be combined with directional sides");
        }
        if (kind == ProcessPortKind.ENERGY && !filter.allowAny()) {
            throw new IllegalArgumentException("energy ports cannot declare resource IDs");
        }
    }

    public boolean canInsert(boolean processLocked) {
        return mode.insertionAllowed() && (kind == ProcessPortKind.ENERGY || !processLocked);
    }

    public boolean canExtract(boolean processLocked) {
        return mode.extractionAllowed()
                && !(processLocked && mode == ProcessPortMode.BIDIRECTIONAL && kind != ProcessPortKind.ENERGY);
    }
}
