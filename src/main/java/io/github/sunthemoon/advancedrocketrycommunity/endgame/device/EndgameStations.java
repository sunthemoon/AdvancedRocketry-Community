package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The station case of ADR-054 section 3 at a device position, observed on the server thread: outside the Space
 * Level no station rule applies; in it, a device inside a committed region of an operational, unquarantined
 * registry belongs to that station; anywhere else in the Space Level is unavailable.
 */
public final class EndgameStations {
    private EndgameStations() {
    }

    public static At at(ServerLevel level, BlockPos position) {
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return new At(EndgameAuthority.StationContext.outside(), Optional.empty());
        }
        StationRegistrySavedData stations = StationRegistrySavedData.get(level.getServer());
        if (!stations.updatesAvailable()) {
            return new At(EndgameAuthority.StationContext.unavailable(), Optional.empty());
        }
        Optional<StationState> station = stations.findAt(position.getX(), position.getZ());
        return station.map(state -> new At(EndgameAuthority.StationContext.committed(state.ownerId(), state.members()),
                        Optional.of(state)))
                .orElseGet(() -> new At(EndgameAuthority.StationContext.unavailable(), Optional.empty()));
    }

    /** @param station the committed station at the position, if any */
    public record At(EndgameAuthority.StationContext context, Optional<StationState> station) {
        public At {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(station, "station");
        }
    }
}
