package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Logical identity with exact loaded UUID hints; never searches entities or loads a chunk. */
public final class RocketMonitorBinding {
    private RocketMonitorBinding() { }
    public record Result(RocketMonitorStatus status, RocketEntity rocket) { }
    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && level.areEntitiesLoaded(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }
    public static BlockPos origin(RocketFlightData flight) {
        var at = flight.currentOrigin(); return new BlockPos(at.x(), at.y(), at.z());
    }
    public static UUID station(RocketFlightData flight) {
        return flight.currentTarget().filter(TravelTarget.Station.class::isInstance)
                .map(target -> ((TravelTarget.Station) target).instanceId()).orElse(null);
    }
    public static LoaderBinding capture(RocketEntity rocket) {
        var flight = rocket.flightData().orElseThrow();
        return new LoaderBinding(flight.logicalRocketId(), rocket.level().dimension().location(), rocket.getUUID(), origin(flight), station(flight));
    }
    public static Result resolve(RocketMonitorBlockEntity monitor, boolean docked) {
        var root = monitor.root();
        if (root == null) { return new Result(RocketMonitorStatus.UNSUPPORTED_DATA, null); }
        var binding = root.binding();
        if (binding == null) { return new Result(RocketMonitorStatus.UNBOUND, null); }
        if (!(monitor.getLevel() instanceof ServerLevel level) || !binding.level().equals(level.dimension().location())) {
            return new Result(RocketMonitorStatus.STALE_BINDING, null);
        }
        // Existing schema-1 bindings already contain the stable assembly identity. Follow the journal's source
        // during an active flight and its committed replacement, never an uncommitted destination copy.
        var journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.operational()) { return new Result(RocketMonitorStatus.ROCKET_BUSY, null); }
        var transfer = journal.findByLogicalRocket(binding.logicalRocket()).orElse(null);
        if (transfer != null) {
            boolean committed = transfer.phase() == RocketTransferPhase.COMMITTED;
            var located = committed ? transfer.destinationFlightData() : transfer.sourceFlightData();
            var physical = committed ? transfer.destinationEntityId().orElse(null) : transfer.sourceEntityId();
            if (!transfer.ownerId().equals(root.owner()) || physical == null
                    || !located.currentDimension().equals(level.dimension().location())) {
                return new Result(RocketMonitorStatus.STALE_BINDING, null);
            }
            binding = new LoaderBinding(binding.logicalRocket(), located.currentDimension(), physical, origin(located), station(located));
        } else {
            // Native F8 retires the journal entry but retains the logical-to-physical authority head.
            boolean anchored = false;
            var escrow = ResourceEscrowSavedData.existing(level.getServer());
            if (escrow != null) {
                if (!escrow.operational()) { return new Result(RocketMonitorStatus.ROCKET_BUSY, null); }
                var logical = binding.logicalRocket();
                var head = escrow.ledger().rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
                if (head != null) {
                    if (!head.owner().equals(root.owner())) { return new Result(RocketMonitorStatus.NOT_OWNER, null); }
                    if (!(level.getEntity(head.physical()) instanceof RocketEntity current) || !current.operational()
                            || !current.flightData().orElseThrow().logicalRocketId().equals(logical)) {
                        return new Result(RocketMonitorStatus.STALE_BINDING, null);
                    }
                    binding = capture(current);
                    anchored = true;
                }
            }
            if (!anchored) {
                // Unanchored native flights have no retained head after F8. Use only an unambiguous loaded hint.
                var current = RocketMonitorLoadedRockets.unique(level, binding.logicalRocket());
                if (current != null && current.operational()
                        && current.flightData().orElseThrow().logicalRocketId().equals(binding.logicalRocket())) {
                    if (!current.getUUID().equals(binding.physicalRocket())) { binding = capture(current); }
                } else if (RocketMonitorLoadedRockets.ambiguous(level, binding.logicalRocket())) {
                    // A duplicate/overflow must not quietly fall back to the old physical hint.
                    return new Result(RocketMonitorStatus.STALE_BINDING, null);
                }
            }
        }
        if (!loaded(level, monitor.getBlockPos()) || !loaded(level, binding.locator())) {
            return new Result(RocketMonitorStatus.WAITING_UNLOADED, null);
        }
        if (!(level.getEntity(binding.physicalRocket()) instanceof RocketEntity rocket) || !rocket.isAlive() || !rocket.operational()) {
            return new Result(RocketMonitorStatus.STALE_BINDING, null);
        }
        if (!loaded(level, rocket.blockPosition())) { return new Result(RocketMonitorStatus.WAITING_UNLOADED, null); }
        var flight = rocket.flightData().orElseThrow();
        if (!flight.logicalRocketId().equals(binding.logicalRocket()) || !origin(flight).equals(binding.locator())
                || !Objects.equals(station(flight), binding.station())) { return new Result(RocketMonitorStatus.STALE_BINDING, null); }
        if (root.owner() == null || !rocket.ownerId().filter(root.owner()::equals).isPresent()) {
            return new Result(RocketMonitorStatus.NOT_OWNER, null);
        }
        if (docked && !docked(rocket, monitor.getBlockPos())) { return new Result(RocketMonitorStatus.ROCKET_BUSY, null); }
        return new Result(RocketMonitorStatus.READY, rocket);
    }
    public static boolean docked(RocketEntity rocket, BlockPos device) {
        var flight = rocket.flightData().orElse(null);
        return flight != null && flight.state().acceptsFuel() && flight.activeTransferId().isEmpty() && !rocket.isPassenger()
                && rocket.getDeltaMovement().lengthSqr() < 1.0e-10
                && rocket.position().distanceToSqr(new Vec3(flight.currentOrigin().x() + .5, flight.currentOrigin().y(),
                        flight.currentOrigin().z() + .5)) < 1.0e-10
                && rocket.getBoundingBox().distanceToSqr(Vec3.atCenterOf(device)) <= 36;
    }
}
