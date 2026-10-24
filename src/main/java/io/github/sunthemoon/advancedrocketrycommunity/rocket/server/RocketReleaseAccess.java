package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Lifecycle bridge; the installed transfer service, not a static world map, owns every release. */
final class RocketReleaseAccess {
    private RocketReleaseAccess() { }
    static final class Owner implements AutoCloseable {
        final MinecraftServer server;
        final RocketReleaseCoordinator coordinator;
        final RocketReleaseWorld world;
        final RocketRejoinSavedData sidecar;
        Owner(MinecraftServer server, RocketTransferRecoveryService recovery, Set<UUID> live, Set<UUID> settled) {
            this.server = server; sidecar = RocketRejoinEvents.get(server);
            var journal = RocketTransferSavedData.get(server);
            world = new RocketReleaseWorld(server, journal, recovery, live, settled);
            coordinator = new RocketReleaseCoordinator(sidecar, journal, world);
        }
        @Override public void close() { coordinator.close(); }
    }
    private static Owner owner(MinecraftServer server) {
        if (!server.isSameThread()) { throw new IllegalStateException("Release access requires server thread"); }
        var transfers = RocketRuntime.installedTransfers();
        return transfers == null ? null : transfers.releaseOwner(server);
    }
    static boolean fenced(MinecraftServer server, UUID logical) {
        if (logical == null) { return false; }
        var owner = owner(server);
        if (owner != null) { return owner.coordinator.fenced(logical); }
        var root = RocketRejoinEvents.get(server).root();
        return root.batches().stream().anyMatch(b -> b.rocket().equals(logical) && root.entries().stream()
                .anyMatch(e -> e.release().equals(b.release()) && e.seat() == RocketRejoinRoot.Seat.PENDING));
    }
    static boolean fenced(RocketEntity rocket) {
        return !rocket.level().isClientSide && rocket.getServer() != null && fenced(rocket.getServer(), rocket.assemblyTransactionId().orElse(null));
    }
    static RocketPassengerManifest filter(MinecraftServer server, UUID logical, RocketPassengerManifest current) {
        var owner = owner(server);
        if (owner != null) { return owner.coordinator.filter(logical, current); }
        var root = RocketRejoinEvents.get(server).root();
        for (var b : root.batches()) {
            if (b.rocket().equals(logical) && root.entries().stream().anyMatch(e -> e.release().equals(b.release()) && e.seat() == RocketRejoinRoot.Seat.PENDING)) {
                current = RocketReleasePlan.filter(current, b.released());
            }
        }
        return current;
    }
    static void joined(RocketEntity rocket) {
        if (rocket.level().isClientSide || !rocket.operational()) { return; }
        var flight = rocket.flightData().orElseThrow();
        var filtered = filter(rocket.getServer(), flight.logicalRocketId(), flight.passengers());
        if (!filtered.equals(flight.passengers()) && RocketEscrowGate.idle(rocket)) { rocket.updateFlightData(flight.withPassengers(filtered)); }
    }
    static boolean retainedRiderMayLeave(RocketEntity rocket, UUID player) {
        var owner = owner(rocket.getServer()); var logical = rocket.assemblyTransactionId().orElse(null);
        return owner != null && RocketEscrowGate.idle(rocket) && owner.coordinator.fenced(logical)
                && !owner.coordinator.released(logical, player);
    }
    static Optional<UUID> admitForTests(MinecraftServer server, UUID transfer) {
        EscrowTestEndpoints.requireEnabled(server);
        var owner = owner(server); if (owner == null) { return Optional.empty(); }
        var pending = owner.coordinator.release(transfer);
        if (pending.isPresent()) { return pending; }
        var plan = owner.world.plan(owner.sidecar.root(), transfer);
        return plan != null && owner.coordinator.admit(plan) ? Optional.of(plan.batch().release()) : Optional.empty();
    }
    static void stopping() { var transfers = RocketRuntime.installedTransfers(); if (transfers != null) { transfers.closeReleases(); } }
}
