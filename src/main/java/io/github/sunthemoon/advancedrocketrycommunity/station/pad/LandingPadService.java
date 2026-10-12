package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-authoritative local registration and loaded-identity admission. */
public final class LandingPadService {
    private LandingPadService() { }

    public static LandingPadEntry register(ServerLevel level, BlockPos position, UUID actor, boolean operator, String name) {
        StationState station = localAuthority(level, position, actor, operator);
        LandingPadBlockEntity pad = loadedPad(level, position);
        if (pad == null || !pad.operational() || pad.reference() != null) { throw new IllegalArgumentException("Pad is unavailable or registered"); }
        LandingPadEntry entry = LandingPadIndex.get(level.getServer()).register(station.stationId(), station.ownerId(),
                pad.ensurePhysicalId(), name, new StationPosition(position.getX() - station.landingPad().x(),
                        position.getY(), position.getZ() - station.landingPad().z()));
        pad.bind(entry); return entry;
    }

    public static boolean remove(ServerLevel level, BlockPos position, UUID actor, boolean operator) {
        StationState station = localAuthority(level, position, actor, operator);
        LandingPadBlockEntity pad = loadedPad(level, position);
        if (pad == null || !pad.operational() || !LandingPadIndex.get(level.getServer()).operational()) { return false; }
        if (pad.reference() == null) {
            // Repair a local index/BE torn-save mismatch only under station management authority.
            for (var entry : LandingPadIndex.get(level.getServer()).entries(station.stationId())) {
                if (entry.position(station).equals(position)) {
                    LandingPadIndex.get(level.getServer()).remove(station.stationId(), entry.reference(), entry.physicalId());
                }
            }
            return true;
        }
        boolean removed = LandingPadIndex.get(level.getServer()).remove(station.stationId(), pad.reference(), pad.physicalId());
        // A torn save can leave a harmless obsolete BE binding; local authority may clear it.
        if (removed || LandingPadIndex.get(level.getServer()).find(station.stationId(), pad.reference()).isEmpty()) {
            pad.unbind(); return true;
        }
        return false;
    }

    public static StationState localAuthority(ServerLevel level, BlockPos position, UUID actor, boolean operator) {
        if (!level.getServer().isSameThread() || !level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            throw new IllegalArgumentException("Pads require the server Space Level");
        }
        StationState station = StationRegistrySavedData.get(level.getServer()).findAt(position.getX(), position.getZ()).orElseThrow(
                () -> new IllegalArgumentException("Pad requires a committed station"));
        if (!new StationAccessService().allowed(station, actor, operator, StationAccessAction.MANAGE_STATION)) {
            throw new IllegalArgumentException("Only station owner or operator may register/remove a pad");
        }
        return station;
    }

    public static LandingPadBlockEntity loadedPad(ServerLevel level, BlockPos position) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(position) instanceof LandingPadBlockEntity pad && !pad.isRemoved() ? pad : null;
    }

    public static LandingPadEntry resolve(MinecraftServer server, UUID stationId, LandingPadReference reference) {
        if (!server.isSameThread()) { return null; }
        var station = StationRegistrySavedData.get(server).find(stationId).orElse(null);
        var index = LandingPadIndex.get(server);
        LandingPadEntry entry = index.find(stationId, reference).orElse(null);
        var space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (station == null || entry == null || space == null || !entry.ownerId().equals(station.ownerId())) { return null; }
        BlockPos position = entry.position(station);
        if (!station.region().contains(position.getX(), position.getZ())) { return null; }
        LandingPadBlockEntity pad = loadedPad(space, position);
        if (pad == null || !pad.operational() || !entry.matches(pad.reference(), pad.physicalId())
                || !stationId.equals(pad.stationId())) { return null; }
        return entry;
    }

    /** Reconcile a claim whose journal was not durably published, without scanning the world/index. */
    public static boolean claim(MinecraftServer server, LandingPadEntry entry, UUID transfer) {
        var journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) { return false; }
        var index = LandingPadIndex.get(server);
        if (entry.claim() != null) {
            var previous = journal.find(entry.claim()).orElse(null);
            var plan = previous == null ? null : previous.sourceFlightData().plan().orElseThrow();
            if (previous == null || previous.phase() == RocketTransferPhase.COMMITTED
                    || !entry.reference().equals(plan.landingPad()) || !entry.stationId().equals(plan.destinationStationId())) {
                index.release(entry.stationId(), entry.reference(), entry.claim());
            }
        }
        return index.claim(entry.stationId(), entry.reference(), entry.physicalId(), transfer);
    }

    public static void release(MinecraftServer server, RocketTransferRecord record) {
        release(server, record.sourceFlightData().plan().orElseThrow());
    }
    public static void release(MinecraftServer server, RocketFlightPlan plan) {
        if (plan.landingPad() != null) {
            LandingPadIndex.get(server).release(plan.destinationStationId(), plan.landingPad(), plan.requestId());
        }
    }
}
