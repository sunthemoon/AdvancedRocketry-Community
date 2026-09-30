package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The terminal a resource action happens at: its ID, whether that ID is persisted, and where it is. */
public record DeliveryTerminal(UUID id, boolean persisted, ResourceKey<Level> level, BlockPos pos) {
    public DeliveryTerminal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(level, "level");
        pos = Objects.requireNonNull(pos, "pos").immutable();
    }

    /** Display-only location recorded with a bound mission (ADR-050 section 3). */
    public Optional<MissionPayload.TerminalLocation> display() {
        return Optional.of(new MissionPayload.TerminalLocation(level.location(), pos));
    }
}
