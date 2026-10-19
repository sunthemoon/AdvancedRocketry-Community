package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Resolve a binding and check its identity, ownership, range, passengers and resource interlocks. C-A stops at
 * ADMISSION_DISABLED. Nothing here loads a chunk, chooses a payload, captures, reserves or moves resources.
 */
final class RocketCargoBindingChecks {
    /** Design section 2: the rocket is within 6 blocks of the device. */
    static final double MAX_ROCKET_DISTANCE = 6.0D;

    /** A resolved rocket, or the refusal that stopped the lookup. */
    record Resolved(RocketCargoStatus refusal, RocketEntity rocket) {
        static Resolved refused(RocketCargoStatus refusal) {
            return new Resolved(Objects.requireNonNull(refusal, "refusal"), null);
        }
    }

    private RocketCargoBindingChecks() {
    }

    /**
     * The bound rocket by its physical UUID, rechecked against the logical identity, the locator, the station
     * context, a duplicate copy, the owner and the range. The locator's chunk and entity sections must already be
     * loaded; nothing is loaded here.
     */
    static Resolved resolve(ServerLevel level, BlockPos device, RocketFluidCargoRoot root) {
        LoaderBinding binding = root.binding();
        if (binding == null) {
            return Resolved.refused(RocketCargoStatus.UNBOUND);
        }
        if (!binding.level().equals(level.dimension().location()) || !level.isInWorldBounds(binding.locator())
                || !level.getWorldBorder().isWithinBounds(binding.locator())) {
            return Resolved.refused(RocketCargoStatus.STALE_BINDING);
        }
        BlockPos at = binding.locator();
        if (!loaded(level, at)) {
            return Resolved.refused(RocketCargoStatus.WAITING_UNLOADED);
        }
        Entity entity = level.getEntity(binding.physicalRocket());
        if (!(entity instanceof RocketEntity rocket) || rocket.isRemoved() || !rocket.operational()) {
            return Resolved.refused(RocketCargoStatus.STALE_BINDING);
        }
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (!loaded(level, rocket.blockPosition()) || !flight.logicalRocketId().equals(binding.logicalRocket())
                || !flight.currentDimension().equals(binding.level()) || !origin(flight).equals(at)
                || !atOrigin(rocket)
                || !Objects.equals(station(flight), binding.station()) || duplicated(level, device, rocket)) {
            return Resolved.refused(RocketCargoStatus.STALE_BINDING);
        }
        if (root.owner() == null || !rocket.ownerId().filter(root.owner()::equals).isPresent()) {
            return Resolved.refused(RocketCargoStatus.NOT_OWNER);
        }
        if (!withinRange(rocket, device)) {
            return Resolved.refused(RocketCargoStatus.OUT_OF_RANGE);
        }
        return new Resolved(null, rocket);
    }

    /** Admission remains unwired; checking an existing binding never prepares a snapshot or transfers cargo. */
    static RocketCargoStatus check(ServerLevel level, BlockPos device, RocketFluidCargoRoot root) {
        Resolved resolved = resolve(level, device, root);
        if (resolved.refusal() != null) { return resolved.refusal(); }
        RocketEntity rocket = resolved.rocket();
        if (!stationary(rocket) || occupied(rocket)) { return RocketCargoStatus.ROCKET_BUSY; }
        if (!RocketEscrowGate.idle(rocket)) { return RocketCargoStatus.ESCROW_PENDING; }
        if (!RocketRuntime.resourceMutationAllowed(rocket)) { return RocketCargoStatus.TRANSFER_JOURNAL_UNPROVED; }
        return RocketCargoStatus.ADMISSION_DISABLED;
    }

    /** A FULL chunk with loaded entity sections; never a load. */
    static boolean loaded(ServerLevel level, BlockPos at) {
        int x = at.getX() >> 4;
        int z = at.getZ() >> 4;
        return level.getChunkSource().getChunkNow(x, z) != null && level.areEntitiesLoaded(ChunkPos.asLong(x, z));
    }

    /** ASSEMBLED, FUELED or LANDED, with no transfer in progress and not riding anything. */
    static boolean stationary(RocketEntity rocket) {
        RocketFlightData flight = rocket.flightData().orElseThrow();
        return flight.state().acceptsFuel() && flight.activeTransferId().isEmpty() && !rocket.isPassenger()
                && rocket.getDeltaMovement().lengthSqr() == 0;
    }

    static boolean atOrigin(RocketEntity rocket) {
        BlockPos origin = origin(rocket.flightData().orElseThrow());
        return Double.compare(rocket.getX(), origin.getX() + 0.5D) == 0
                && Double.compare(rocket.getY(), origin.getY()) == 0
                && Double.compare(rocket.getZ(), origin.getZ() + 0.5D) == 0;
    }

    /** Design section 3, stricter than ADR-065: actual or assigned passengers, or anything riding the rocket. */
    static boolean occupied(RocketEntity rocket) {
        return !rocket.getPassengers().isEmpty() || !rocket.flightData().orElseThrow().passengers().assignments().isEmpty();
    }

    static boolean withinRange(RocketEntity rocket, BlockPos device) {
        return rocket.getBoundingBox().distanceToSqr(Vec3.atCenterOf(device)) <= MAX_ROCKET_DISTANCE * MAX_ROCKET_DISTANCE;
    }

    /** Another loaded rocket entity carrying the same logical identity near the device or the rocket. */
    static boolean duplicated(ServerLevel level, BlockPos device, RocketEntity rocket) {
        UUID logical = rocket.flightData().orElseThrow().logicalRocketId();
        AABB around = new AABB(device).inflate(MAX_ROCKET_DISTANCE).minmax(rocket.getBoundingBox());
        return !level.getEntitiesOfClass(RocketEntity.class, around, other -> other != rocket && other.flightData()
                .filter(flight -> flight.logicalRocketId().equals(logical)).isPresent()).isEmpty();
    }

    static BlockPos origin(RocketFlightData flight) {
        return new BlockPos(flight.currentOrigin().x(), flight.currentOrigin().y(), flight.currentOrigin().z());
    }

    static UUID station(RocketFlightData flight) {
        return flight.currentTarget().filter(TravelTarget.Station.class::isInstance)
                .map(target -> ((TravelTarget.Station) target).instanceId()).orElse(null);
    }
}
