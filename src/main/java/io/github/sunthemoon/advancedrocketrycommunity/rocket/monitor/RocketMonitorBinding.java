package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Exact loaded UUID resolution; telemetry may observe ascent, but never searches or loads a chunk. */
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
