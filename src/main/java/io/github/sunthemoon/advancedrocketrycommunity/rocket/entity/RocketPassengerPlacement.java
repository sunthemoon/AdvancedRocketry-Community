package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketEntityExtent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSnapshot;
import java.util.List;

/** Entity-owned presentation cache, with no packet, roster or persistent state of its own. */
final class RocketPassengerPlacement {
    private RocketVisualSnapshot cachedVisual;
    private List<RocketPosition> visualAnchors = List.of();

    RocketPassengerPosition position(RocketEntity rocket, int seat, int capacity) {
        if (!rocket.level().isClientSide) {
            return rocket.snapshot().map(snapshot -> RocketPassengerPosition.forSeat(
                    seat, capacity, snapshot.passengerAnchors(), snapshot.bounds()))
                    .orElseGet(() -> RocketPassengerPosition.forSeat(seat, capacity));
        }
        return forVisual(RocketVisualClientCache.snapshot(rocket.getUUID()).orElse(null), seat, capacity);
    }

    RocketPassengerPosition forVisual(RocketVisualSnapshot visual, int seat, int capacity) {
        refresh(visual);
        // Incomplete recognition must not shift a later seat into an earlier assigned slot.
        return visual == null || visualAnchors.size() != capacity ? RocketPassengerPosition.forSeat(seat, capacity)
                : RocketPassengerPosition.forSeat(seat, capacity, visualAnchors, visual.bounds());
    }

    /**
     * W311: the structure and seat extent of the entity box, from the same sources as rider placement: the server's
     * snapshot, or the client's synced visual blocks. Null while neither is known, which keeps the type's fixed box.
     */
    RocketEntityExtent extent(RocketEntity rocket) {
        if (!rocket.level().isClientSide) {
            return rocket.snapshot().map(snapshot -> RocketEntityExtent.of(snapshot.bounds(), snapshot.passengerAnchors()))
                    .orElse(null);
        }
        RocketVisualSnapshot visual = RocketVisualClientCache.snapshot(rocket.getUUID()).orElse(null);
        refresh(visual);
        return visual == null ? null : RocketEntityExtent.of(visual.bounds(), visualAnchors);
    }

    private void refresh(RocketVisualSnapshot visual) {
        if (visual != cachedVisual) {
            cachedVisual = visual;
            // Visual blocks use the same position ordering as the authoritative passenger anchors.
            // Resolve only once per immutable snapshot, never once per rider per tick.
            visualAnchors = visual == null ? List.of() : visual.blocks().stream()
                    .filter(block -> RocketBlockStateAdapter.restore(block.state())
                            .filter(state -> state.is(ModBlockTags.ROCKET_SEATS)).isPresent())
                    .map(block -> block.position())
                    .limit(RocketFlightLimits.MAX_PASSENGERS)
                    .toList();
        }
    }
}
