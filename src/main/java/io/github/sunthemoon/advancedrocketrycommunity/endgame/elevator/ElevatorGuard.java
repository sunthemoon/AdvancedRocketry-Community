package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-059 section 5 from the endgame side: a warp is refused while a pair names the station, and a deletion also
 * while a transit record's source or destination stands in the station's region; both fail closed while the endgame
 * root is not operational. It reads the live root on the server thread and never depends on the elevator switch.
 */
public final class ElevatorGuard implements ElevatorStationGuard {
    private final EndgameService service;

    public ElevatorGuard(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Decision warp(MinecraftServer server, StationState station) {
        Optional<EndgameRoot> root = service.root();
        return decision(ElevatorRules.warp(root.isPresent(), root.isPresent()
                && root.get().pairs().forStation(station.stationId()).isPresent()));
    }

    @Override
    public Decision delete(MinecraftServer server, StationState station) {
        Optional<EndgameRoot> root = service.root();
        if (root.isEmpty()) {
            return Decision.UNAVAILABLE;
        }
        boolean bound = root.get().pairs().forStation(station.stationId()).isPresent();
        boolean transit = false;
        for (TransitRecord record : root.get().transits().records()) {
            if (inRegion(root.get(), station, record.key().source())
                    || inRegion(root.get(), station, record.destination())) {
                transit = true;
                break;
            }
        }
        return decision(ElevatorRules.delete(true, bound, transit));
    }

    private static Decision decision(ElevatorRules.Guard guard) {
        return switch (guard) {
            case ALLOWED -> Decision.ALLOWED;
            case BOUND -> Decision.BOUND;
            case UNAVAILABLE -> Decision.UNAVAILABLE;
        };
    }

    /** Whether the endpoint, or its tombstone, stands in the Space Level inside the station's region. */
    private static boolean inRegion(EndgameRoot root, StationState station, UUID id) {
        Optional<EndpointRecord> record = root.endpoint(id);
        long pos;
        boolean space;
        if (record.isPresent()) {
            pos = record.get().pos();
            space = record.get().level().equals(CelestialIds.SPACE_LEVEL.location());
        } else {
            Optional<Tombstone> tombstone = root.tombstone(id);
            if (tombstone.isEmpty()) {
                return false;
            }
            pos = tombstone.get().pos();
            space = tombstone.get() instanceof Tombstone.Young young
                    ? young.level().equals(CelestialIds.SPACE_LEVEL.location())
                    : tombstone.get().levelHash() == EndgameService.levelHash(CelestialIds.SPACE_LEVEL.location());
        }
        BlockPos at = BlockPos.of(pos);
        return space && at.getX() >= station.region().minimumX() && at.getX() <= station.region().maximumX()
                && at.getZ() >= station.region().minimumZ() && at.getZ() <= station.region().maximumZ();
    }
}
