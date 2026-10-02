package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-059 section 8 on the server thread: pending passenger rides (at most 4 per endpoint and 64 on the server), each
 * with one expiring region ticket of type {@code advancedrocketrycommunity:elevator_arrival} at the server-derived
 * arrival chunk, distance 0, 300 ticks. A countdown of 100 ticks is cancelled when the rider leaves the platform,
 * disconnects, dies or changes Level, when the pair becomes invalid or unbound, or when the system is disabled. At
 * most one ride commits per tick: the commit re-derives validity and access, needs the departing endpoint's energy and
 * a {@code FULL} arrival chunk (waiting at most 100 more ticks, never loading it synchronously), the arrival endpoint
 * with two free blocks above its platform centre, and the protection chain's bounds, zones and {@code TELEPORT} event;
 * then the rider is teleported and the departing endpoint debited once the rider stands at the arrival. No passenger
 * journal: the player file holds the rider wholly at one end. Runtime state only, cleared at server stop.
 */
public final class ElevatorRides {
    public static final TicketType<UUID> ARRIVAL = TicketType.create("advancedrocketrycommunity:elevator_arrival",
            Comparator.comparing(UUID::toString), ElevatorRules.ARRIVAL_TICKET_TICKS);

    private final Map<UUID, Ride> pending = new LinkedHashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private long committedTick = Long.MIN_VALUE;

    /** One pending ride; the arrival is derived from the pair record, never from a client value. */
    public record Ride(UUID rider, UUID pairId, UUID departure, ResourceKey<Level> departureLevel, BlockPos departurePos,
                       UUID arrival, ResourceKey<Level> arrivalLevel, BlockPos arrivalPos, long dueTick) {
        public Ride {
            Objects.requireNonNull(rider, "rider");
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(departure, "departure");
            Objects.requireNonNull(departureLevel, "departureLevel");
            departurePos = departurePos.immutable();
            Objects.requireNonNull(arrival, "arrival");
            Objects.requireNonNull(arrivalLevel, "arrivalLevel");
            arrivalPos = arrivalPos.immutable();
        }

        ChunkPos arrivalChunk() {
            return new ChunkPos(arrivalPos);
        }
    }

    public Optional<Ride> pending(UUID rider) {
        return Optional.ofNullable(pending.get(rider));
    }

    public int size() {
        return pending.size();
    }

    /** The arrival tickets held (ADR-054 section 13): one per pending ride, released when it ends. */
    public int tickets() {
        return pending.size();
    }

    /** The {@code ride} intent from a player standing on the departing endpoint's platform. */
    public ElevatorRules.Check request(ServerPlayer player, ElevatorEndpointBlockEntity departure,
                                       EndgameService service, EndgameDevices devices) {
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        long now = server.overworld().getGameTime();
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.ROOT_UNAVAILABLE);
        }
        EndgameRoot root = view.get();
        UUID departureId = departure.endpointId();
        Optional<ElevatorPair> pair = root.pairs().forEndpoint(departureId);
        boolean operator = player.hasPermissions(2);
        EndgameCode access = pair.map(found -> ElevatorPairs.access(server, root, found, player.getUUID(), operator))
                .orElse(EndgameCode.NOT_BOUND);
        ElevatorRules.Check validity = pair.map(found -> ElevatorPairs.validity(server, root, devices, found))
                .orElse(ElevatorRules.Check.of(EndgameCode.NOT_BOUND));
        int here = 0;
        for (Ride ride : pending.values()) {
            here += ride.departure().equals(departureId) ? 1 : 0;
        }
        ElevatorRules.Check check = ElevatorRules.rideRequest(new ElevatorRules.RideFacts(
                devices.settings().enabled(EndgameSystem.SPACE_ELEVATOR), pair.isPresent(),
                departure.onPlatform(player), access, !player.isPassenger() && !player.isVehicle(),
                pending.containsKey(player.getUUID()), now >= cooldownUntil.getOrDefault(player.getUUID(),
                Long.MIN_VALUE), here, pending.size(), validity));
        Optional<EndpointRecord> arrival = pair.flatMap(found -> root.endpoint(found.otherEnd(departureId)));
        ServerLevel arrivalLevel = arrival.map(record -> server.getLevel(ResourceKey.create(Registries.DIMENSION,
                record.level()))).orElse(null);
        if (check.ok() && arrivalLevel == null) {
            check = ElevatorRules.Check.of(EndgameCode.ARRIVAL_UNLOADED);
        }
        if (!check.ok()) {
            ElevatorPairs.audit(service, now, "ride_request", check, departureId, player.getUUID(), "");
            return check;
        }
        Ride ride = new Ride(player.getUUID(), pair.get().pairId(), departureId, level.dimension(),
                departure.getBlockPos(), arrival.get().id(), arrivalLevel.dimension(),
                BlockPos.of(arrival.get().pos()), now + ElevatorRules.RIDE_COUNTDOWN_TICKS);
        // The pre-load: the chunk exists because the endpoint was built in it, so it is read from disk, never generated.
        arrivalLevel.getChunkSource().addRegionTicket(ARRIVAL, ride.arrivalChunk(), 0, ride.rider());
        pending.put(ride.rider(), ride);
        ElevatorPairs.audit(service, now, "ride_request", ElevatorRules.Check.OK, departureId, player.getUUID(),
                "pair=" + ride.pairId() + " arrival=" + ride.arrival());
        return ElevatorRules.Check.of(EndgameCode.RIDE_COUNTDOWN);
    }

    /** Server END tick: cancellations, then at most one commit. */
    public void tick(MinecraftServer server, EndgameService service, EndgameDevices devices) {
        long now = server.overworld().getGameTime();
        cooldownUntil.values().removeIf(until -> until <= now);
        if (pending.isEmpty()) {
            return;
        }
        boolean enabled = devices.settings().enabled(EndgameSystem.SPACE_ELEVATOR);
        Optional<EndgameRoot> view = service.root();
        List<Ride> rides = new ArrayList<>(pending.values());
        for (Ride ride : rides) {
            ServerPlayer rider = server.getPlayerList().getPlayer(ride.rider());
            Optional<ElevatorEndpointBlockEntity> departure = endpoint(server, ride.departureLevel(),
                    ride.departurePos(), ride.departure());
            Optional<ElevatorPair> pair = view.flatMap(root -> root.pairs().pair(ride.pairId()));
            EndgameCode stop = !enabled ? EndgameCode.SYSTEM_DISABLED : view.isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                    : pair.isEmpty() ? EndgameCode.NOT_BOUND
                    : rider == null || !rider.isAlive() || !rider.level().dimension().equals(ride.departureLevel())
                    || departure.isEmpty() || !departure.get().onPlatform(rider) ? EndgameCode.RIDE_CANCELLED
                    : EndgameCode.OK;
            if (stop != EndgameCode.OK) {
                cancel(server, service, ride, rider, ElevatorRules.Check.of(stop), now);
                continue;
            }
            ElevatorRules.Check validity = ElevatorPairs.validity(server, view.get(), devices, pair.get());
            if (!validity.ok()) {
                cancel(server, service, ride, rider, validity, now);
                continue;
            }
            if (now < ride.dueTick() || committedTick == now) {
                continue;
            }
            commit(server, service, devices, view.get(), pair.get(), ride, rider, departure.get(), validity, now);
        }
    }

    private void commit(MinecraftServer server, EndgameService service, EndgameDevices devices, EndgameRoot root,
                        ElevatorPair pair, Ride ride, ServerPlayer rider, ElevatorEndpointBlockEntity departure,
                        ElevatorRules.Check validity, long now) {
        ServerLevel arrivalLevel = server.getLevel(ride.arrivalLevel());
        int cost = ElevatorRules.rideCost(devices.elevatorSettings().energyPercent());
        boolean loaded = arrivalLevel != null && arrivalLevel.getChunkSource()
                .getChunkNow(ride.arrivalChunk().x, ride.arrivalChunk().z) != null;
        boolean clear = loaded && endpoint(server, ride.arrivalLevel(), ride.arrivalPos(), ride.arrival()).isPresent()
                && ElevatorEndpointBlockEntity.platformClear(arrivalLevel, ride.arrivalPos());
        EndgameCode protection = clear ? protection(arrivalLevel, root, ride, rider) : EndgameCode.OK;
        ElevatorRules.CommitDecision decision = ElevatorRules.commit(validity,
                ElevatorPairs.access(server, root, pair, rider.getUUID(), rider.hasPermissions(2)),
                departure.storage().energy().energy() >= cost, loaded,
                now >= ride.dueTick() + ElevatorRules.ARRIVAL_WAIT_TICKS, clear, protection);
        switch (decision.commit()) {
            case WAIT -> {
            }
            case CANCEL -> cancel(server, service, ride, rider, decision.check(), now);
            case TELEPORT -> {
                committedTick = now;
                Vec3 at = ElevatorEndpointBlockEntity.arrival(ride.arrivalPos());
                rider.teleportTo(arrivalLevel, at.x, at.y, at.z, rider.getYRot(), rider.getXRot());
                boolean arrived = rider.level() == arrivalLevel && rider.position().distanceToSqr(at) < 1.0D;
                if (arrived) {
                    // Debited only once the rider stands at the arrival; a teleport another mod cancelled is free.
                    departure.storage().energy().spend(cost);
                    rider.resetFallDistance();
                }
                release(server, ride);
                cooldownUntil.put(ride.rider(), now + ElevatorRules.RIDE_COOLDOWN_TICKS);
                ElevatorPairs.audit(service, now, arrived ? "ride_commit" : "ride_teleport_cancelled",
                        ElevatorRules.Check.OK, ride.departure(), ride.rider(), "pair=" + ride.pairId() + " arrival="
                                + ride.arrival() + " paid_fe=" + (arrived ? cost : 0));
            }
        }
    }

    /**
     * ADR-054 section 5 steps 2, 3 and 6 for the arrival box; step 4 is the access rule, spawn protection none. The
     * device owner is the departing endpoint's owner (section 5.1, review C12R-M2); validity has just found it ACTIVE.
     */
    private static EndgameCode protection(ServerLevel level, EndgameRoot root, Ride ride, ServerPlayer rider) {
        ForgeProtectionView forge = new ForgeProtectionView(level, root.zones());
        Optional<UUID> departingOwner = root.endpoint(ride.departure()).map(EndpointRecord::owner);
        if (departingOwner.isEmpty()) {
            return EndgameCode.TARGET_PROTECTED;
        }
        UUID owner = departingOwner.get();
        BlockPos platform = ride.arrivalPos().above();
        return EndgameProtection.check(new EndgameProtection.Batch(EndgameSystem.SPACE_ELEVATOR, EndgameEffect.TELEPORT,
                owner, Optional.of(rider.getUUID()), level.dimension(), platform.offset(-1, 0, -1),
                platform.offset(1, 2, 1), false), new EndgameProtection.View() {
                    @Override
                    public boolean chunksFull(BlockPos min, BlockPos max) {
                        return true;
                    }

                    @Override
                    public boolean insideWorld(BlockPos min, BlockPos max) {
                        return forge.insideWorld(min, max);
                    }

                    @Override
                    public Collection<ProtectedZone> zones() {
                        return forge.zones();
                    }

                    @Override
                    public boolean spaceLevel() {
                        return false;
                    }

                    @Override
                    public boolean stationsAllow(UUID who, BlockPos min, BlockPos max) {
                        return true;
                    }

                    @Override
                    public Optional<EndgameProtection.SpawnSquare> spawnSquare() {
                        return Optional.empty();
                    }

                    @Override
                    public boolean cancelled(EndgameEffectEvent event) {
                        return forge.cancelled(event);
                    }
                });
    }

    private void cancel(MinecraftServer server, EndgameService service, Ride ride, ServerPlayer rider,
                        ElevatorRules.Check reason, long now) {
        release(server, ride);
        cooldownUntil.put(ride.rider(), now + ElevatorRules.CANCEL_COOLDOWN_TICKS);
        ElevatorPairs.audit(service, now, "ride_cancel", reason, ride.departure(), ride.rider(), "pair="
                + ride.pairId());
        if (rider != null) {
            EndgameIntentGuard.statusLine(rider, reason.code());
        }
    }

    private void release(MinecraftServer server, Ride ride) {
        pending.remove(ride.rider());
        ServerLevel level = server.getLevel(ride.arrivalLevel());
        if (level != null) {
            level.getChunkSource().removeRegionTicket(ARRIVAL, ride.arrivalChunk(), 0, ride.rider());
        }
    }

    /** Unbind cancels the pair's pending rides (section 4). */
    public void cancelPair(MinecraftServer server, UUID pairId) {
        Iterator<Ride> rides = new ArrayList<>(pending.values()).iterator();
        while (rides.hasNext()) {
            Ride ride = rides.next();
            if (ride.pairId().equals(pairId)) {
                release(server, ride);
                cooldownUntil.put(ride.rider(), server.overworld().getGameTime() + ElevatorRules.CANCEL_COOLDOWN_TICKS);
                ServerPlayer rider = server.getPlayerList().getPlayer(ride.rider());
                if (rider != null) {
                    EndgameIntentGuard.statusLine(rider, EndgameCode.NOT_BOUND);
                }
            }
        }
    }

    /** The endpoint block entity with this ID at the position, when its chunk is loaded; never loads one. */
    private static Optional<ElevatorEndpointBlockEntity> endpoint(MinecraftServer server, ResourceKey<Level> levelKey,
                                                                  BlockPos pos, UUID id) {
        ServerLevel level = server.getLevel(levelKey);
        if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return Optional.empty();
        }
        return level.getBlockEntity(pos) instanceof ElevatorEndpointBlockEntity endpoint && !endpoint.isRemoved()
                && endpoint.deviceId().filter(id::equals).isPresent() ? Optional.of(endpoint) : Optional.empty();
    }

    /** Server stop: every ticket is released and every ride forgotten. */
    public void clear(MinecraftServer server) {
        for (Ride ride : new ArrayList<>(pending.values())) {
            release(server, ride);
        }
        pending.clear();
        cooldownUntil.clear();
    }
}
