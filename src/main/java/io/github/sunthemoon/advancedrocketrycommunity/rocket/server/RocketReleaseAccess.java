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
            RocketReleaseNativeCuts.armRestored(server, sidecar.root());
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
        var result = admit(server, transfer);
        return result.accepted() ? Optional.of(result.release()) : Optional.empty();
    }
    static RocketReleaseResult admit(MinecraftServer server, UUID transfer) {
        if (!server.isSameThread()) { throw new IllegalStateException("Release admission requires server thread"); }
        var sidecar = RocketRejoinEvents.existing(server);
        var journal = RocketTransferSavedData.existing(server);
        if (sidecar == null || journal == null || !journal.operational()) { return RocketReleaseResult.unavailable(); }
        var owner = owner(server); if (owner == null) { return RocketReleaseResult.unavailable(); }
        var pending = owner.coordinator.release(transfer);
        return resolve(pending, owner.coordinator.pending(transfer), sidecar.admissionAvailable(), owner.coordinator::stage, () -> {
            var plan = owner.world.plan(owner.sidecar.root(), transfer);
            return plan != null && owner.coordinator.admit(plan) ? plan.batch().release() : null;
        });
    }
    /** Admission response policy, separated from world reads so repeats never accidentally enqueue work. */
    static RocketReleaseResult resolve(Optional<UUID> pending, boolean seatsPending, boolean available,
            java.util.function.Function<UUID, String> stage, java.util.function.Supplier<UUID> tryAdmission) {
        if (pending.isPresent() && seatsPending) {
            return new RocketReleaseResult(RocketReleaseResult.Status.PENDING, pending.get(), stage.apply(pending.get()));
        }
        // After S2, an H1 survivor can have different remaining riders become absent. A valid new
        // plan may release those riders; an ordinary repeat with no new admission only reports status.
        if (available) {
            var release = tryAdmission.get();
            if (release != null) {
                return new RocketReleaseResult(RocketReleaseResult.Status.ADMITTED, release, "B0");
            }
        } else if (pending.isEmpty()) { return RocketReleaseResult.unavailable(); }
        return pending.isPresent()
                ? new RocketReleaseResult(RocketReleaseResult.Status.PENDING, pending.get(), stage.apply(pending.get()))
                : RocketReleaseResult.refused();
    }
    static RocketRejoinInspection inspect(MinecraftServer server, UUID player) {
        if (!server.isSameThread()) { throw new IllegalStateException("Inspection requires server thread"); }
        var transfers = RocketRuntime.installedTransfers();
        var owner = transfers == null ? null : transfers.existingReleaseOwner(server);
        if (owner != null) { return owner.coordinator.inspect(player); }
        var data = RocketRejoinEvents.existing(server);
        return data == null ? null : RocketRejoinInspection.of(data.root(), java.util.List.of(),
                release -> data.root().entries().stream().anyMatch(e -> e.release().equals(release)
                        && e.seat() == RocketRejoinRoot.Seat.PENDING) ? "S1" : "PLACEMENT", player);
    }
    static void stopping() { var transfers = RocketRuntime.installedTransfers(); if (transfers != null) { transfers.closeReleases(); } }
    static java.util.List<RocketRejoinRoot.Entry> admissions(MinecraftServer server) {
        var owner = owner(server); return owner == null ? java.util.List.of() : owner.coordinator.unpublishedPlacements();
    }
    static boolean placementReady(MinecraftServer server, UUID release) {
        var owner = owner(server); return owner != null && owner.coordinator.placementReady(release);
    }
}
