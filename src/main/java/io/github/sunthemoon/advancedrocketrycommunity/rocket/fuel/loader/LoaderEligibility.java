package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLoaderAdmission;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Design section 2, step 1: the exact binding, the rocket's state and both resource layers, then the side-effect-free
 * bank selection. Nothing here loads a chunk, scans for a nearest rocket, captures, reserves or moves anything; the
 * fair queue's capture repeats the bank selection against the rocket projected through any pending prefix.
 */
final class LoaderEligibility {
    /** Design section 2: the rocket is within 6 blocks of the device. */
    static final double MAX_ROCKET_DISTANCE = 6.0D;

    /** A resolved rocket, or the refusal that stopped the lookup. */
    record Resolved(FluidFuelLoaderStatus refusal, RocketEntity rocket) {
        static Resolved refused(FluidFuelLoaderStatus refusal) {
            return new Resolved(Objects.requireNonNull(refusal, "refusal"), null);
        }
    }

    private LoaderEligibility() {
    }

    /**
     * The bound rocket by its physical UUID, rechecked against the logical identity, the locator, the station
     * context, a duplicate copy, the owner and the range. The locator's chunk and entity sections must already be
     * loaded; nothing is loaded here.
     */
    static Resolved resolve(ServerLevel level, BlockPos device, LoaderRoot root) {
        LoaderBinding binding = root.binding();
        if (binding == null) {
            return Resolved.refused(FluidFuelLoaderStatus.UNBOUND);
        }
        if (!binding.level().equals(level.dimension().location()) || !level.isInWorldBounds(binding.locator())) {
            return Resolved.refused(FluidFuelLoaderStatus.STALE_BINDING);
        }
        BlockPos at = binding.locator();
        if (!loaded(level, at)) {
            return Resolved.refused(FluidFuelLoaderStatus.WAITING_UNLOADED);
        }
        Entity entity = level.getEntity(binding.physicalRocket());
        if (!(entity instanceof RocketEntity rocket) || rocket.isRemoved() || !rocket.operational()) {
            return Resolved.refused(FluidFuelLoaderStatus.STALE_BINDING);
        }
        RocketFlightData flight = rocket.flightData().orElseThrow();
        if (!flight.logicalRocketId().equals(binding.logicalRocket()) || !origin(flight).equals(at)
                || !Objects.equals(station(flight), binding.station()) || duplicated(level, device, rocket)) {
            return Resolved.refused(FluidFuelLoaderStatus.STALE_BINDING);
        }
        if (root.owner() == null || !rocket.ownerId().filter(root.owner()::equals).isPresent()) {
            return Resolved.refused(FluidFuelLoaderStatus.NOT_OWNER);
        }
        if (!withinRange(rocket, device)) {
            return Resolved.refused(FluidFuelLoaderStatus.OUT_OF_RANGE);
        }
        return new Resolved(null, rocket);
    }

    /** One attempt's result: a refusal, or ADMISSION_DISABLED where a fill would start. */
    static FluidFuelLoaderStatus check(ServerLevel level, BlockPos device, LoaderRoot root) {
        FluidFuelLoaderStatus status = evaluate(level, device, root).refusal();
        return status == null ? FluidFuelLoaderStatus.ADMISSION_DISABLED : status;
    }

    /**
     * Every eligibility check a fill needs; a null refusal means the bound rocket is ready. A same-direction chain,
     * whose earlier markers make the rocket not idle, passes only the coordinator's prefix check and then the service
     * layer's other interlocks; nothing else is relaxed.
     */
    static Resolved evaluate(ServerLevel level, BlockPos device, LoaderRoot root) {
        Resolved resolved = resolve(level, device, root);
        if (resolved.refusal() != null) {
            return resolved;
        }
        RocketEntity rocket = resolved.rocket();
        if (!stationary(rocket) || occupied(rocket) || EscrowLoaderAdmission.cargoPending(rocket)) {
            return Resolved.refused(FluidFuelLoaderStatus.ROCKET_BUSY);
        }
        if (RocketEscrowGate.idle(rocket)) {
            if (!RocketRuntime.resourceMutationAllowed(rocket)) {
                return Resolved.refused(FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED);
            }
        } else if (!EscrowLoaderAdmission.chainable(rocket)) {
            return Resolved.refused(FluidFuelLoaderStatus.ESCROW_PENDING);
        } else if (!RocketRuntime.escrowReconciliationBoundary(rocket) || RocketTransferSavedData.get(level.getServer())
                .resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow())) {
            return Resolved.refused(FluidFuelLoaderStatus.TRANSFER_JOURNAL_UNPROVED);
        }
        BankSelection.Result plan = BankSelection.plan(rocket.flightData().orElseThrow().fuel().propulsion().orElse(null),
                RocketFluidFuelRuntime.capture(), root.tank());
        return plan.status() == FluidFuelLoaderStatus.READY ? resolved : Resolved.refused(plan.status());
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
        return flight.state().acceptsFuel() && flight.activeTransferId().isEmpty() && !rocket.isPassenger();
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
