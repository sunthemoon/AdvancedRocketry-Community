package io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Pure loading-time classification of immutable block state, available since API 1.2.
 * The host evaluates all claimed states during queued common setup on both physical
 * sides and retains only their results. No callbacks run during scans or reloads.
 * Do not query tags, worlds, time, mutable external state, files or networks, or mutate
 * registries. Tags are not yet bound and remain the host's runtime responsibility.
 * Return a non-null result within 5 ms; the whole registration has a 1-second callback
 * budget. Checks happen after return and cannot interrupt non-returning mod code.
 */
@FunctionalInterface
public interface AtmosphereBoundaryProvider {
    AtmosphereBoundary classify(BlockState state);
}
