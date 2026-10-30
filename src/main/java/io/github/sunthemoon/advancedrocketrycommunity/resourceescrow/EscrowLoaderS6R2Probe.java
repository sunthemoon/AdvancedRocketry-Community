package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** R2 stale-journal fixtures after one real loader fill; production alone performs or refuses recovery. */
final class EscrowLoaderS6R2Probe {
    static final String KEEP = "LF_S6_R2_KEEP_SOURCE", DESTRUCTIVE = "LF_S6_R2_DESTRUCTIVE_SOURCE",
            RECONNECT = "LF_S6_R2_DUPLICATE_RECONNECT";
    static final Set<String> CASES = Set.of(KEEP, DESTRUCTIVE, RECONNECT);
    static final UUID PASSENGER = UUID.nameUUIDFromBytes("arce:w211:r2-passenger".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64), DEVICE = ORIGIN.offset(2, 0, 0);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private RocketEntity rocket;
    private FluidFuelLoaderBlockEntity loader;
    private RocketTransferRecord record;
    private long began = -1, installedAt = -1, joinedAt = -1;
    private boolean queued, done;
    private Vec3 passengerPosition;

    static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }

    EscrowLoaderS6R2Probe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "R2 requires exact release-gated per-device selection");
        this.server = server; this.cut = cut; this.recovery = recovery;
    }

    void checkpoint(String name) {
        if (!recovery && queued && name.equals("LOADER_PREPARATIONS_QUEUED")) {
            var data = ResourceEscrowSavedData.existing(server);
            if (data != null && !data.bind(server).unresolvedNaming(device(loader)).isEmpty()) {
                EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), false);
            }
        }
    }

    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 R2 exceeded the existing fixture bound");
        var level = server.overworld(); var journal = RocketTransferSavedData.get(server);
        if (!level.isPositionEntityTicking(ORIGIN) || !journal.retryChecked(server)) { return; }
        if (rocket == null) {
            if (recovery) {
                if (!(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity found)) { return; }
                loader = found;
                var candidates = rockets().stream().filter(r -> r.getUUID().equals(loader.root().orElseThrow().binding().physicalRocket())).toList();
                EscrowTags.require(candidates.size() == 1, "S6 R2 lost its bound source");
                rocket = candidates.get(0);
                EscrowTags.require(journal.entries().size() == 1, "S6 R2 lost its retained journal");
                record = journal.entries().get(0); installedAt = server.getTickCount();
            } else {
                var owner = UUID.randomUUID(); rocket = rocket(level, ORIGIN, owner, mono(2_000));
                EscrowTags.require(level.addFreshEntity(rocket), "S6 R2 source publication failed");
                loader = loader(level, DEVICE, owner, tank(ROCKET_FUEL, 5_000), binding(rocket));
                EscrowTags.require(server.saveEverything(false, true, true), "S6 R2 pre-fill baseline refused");
                EscrowRuntime.of(server).loaderAdmissionForTests(server, device(loader), true);
                queued = true;
                EscrowTags.require(loader.attempt() == FluidFuelLoaderStatus.READY, "S6 R2 fill was not queued");
                return;
            }
        }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null || !data.bind(server).writerIdle() || !data.ledger().records().isEmpty()
                || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
        if (!recovery && record == null && data.ledger().issuedThrough() != 1) { return; }
        conserved(data);
        if (!recovery && record == null) {
            // Establish the retired-fill seed for this stale-history experiment, not a repair of a stopped cut.
            record = stale(cut, rocket.snapshot().orElseThrow(), rocket.getUUID(), rocket.assemblyTransactionId().orElseThrow(),
                    rocket.ownerId().orElseThrow(), rocket.flightData().orElseThrow().fuel());
            if (!KEEP.equals(cut)) {
                var other = java.util.Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
                other.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(), record.destinationFlightData().land(200));
                EscrowTags.require(level.addFreshEntity(other), "S6 R2 stale destination publication failed");
                if (DESTRUCTIVE.equals(cut)) {
                    record = record.destinationSpawned(other.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                            .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                }
            }
            EscrowTags.require(server.saveEverything(false, true, true), "S6 R2 native setup baseline refused");
            journal.put(record); journal.flushChecked(server); installedAt = server.getTickCount();
            return;
        }
        if (!journal.acknowledgedCurrent()) { return; }
        if (RECONNECT.equals(cut) && joinedAt < 0 && server.getTickCount() - installedAt >= 100) {
            var player = ReleaseTestPlayers.place(server, PASSENGER, "W211Passenger", level, new Vec3(66.5, 100, 66.5));
            passengerPosition = player.position(); joinedAt = server.getTickCount();
        }
        if (joinedAt >= 0) {
            ReleaseTestPlayers.pump(server);
            var player = server.getPlayerList().getPlayer(PASSENGER);
            EscrowTags.require(player != null && player.getVehicle() == null && player.position().distanceTo(passengerPosition) < 0.01,
                    "S6 R2 stale history remounted or moved the passenger");
        }
        if (server.getTickCount() - installedAt < 200 || RECONNECT.equals(cut) && server.getTickCount() - joinedAt < 100) { return; }
        var report = RocketRuntime.recoverTransfer(server, record.transferId());
        var diagnostic = String.join(" ", io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime
                .service().orElseThrow().diagnostics(server, java.util.Optional.empty()));
        EscrowTags.require(report.status() == RocketTransferRecoveryReport.Status.RETRY_LATER
                && EscrowCargoRecoveryProbe.floorConflict(diagnostic, record.transferId())
                && journal.entries().size() == 1 && journal.find(record.transferId()).orElseThrow().equals(record),
                "S6 R2 recovery was not fenced by unchanged retained authority: " + diagnostic);
        conserved(data);
        var copies = rockets();
        EscrowTags.require(copies.size() == (KEEP.equals(cut) ? 1 : 2), "S6 R2 created or discarded a physical copy");
        for (var copy : copies) {
            if (copy == rocket) { continue; }
            EscrowTags.require(copy.snapshot().orElseThrow().equals(record.destinationSnapshot())
                    && copy.assemblyTransactionId().orElseThrow().equals(record.logicalRocketId())
                    && bank(copy, false) == 0 && bank(copy, true) == 0 && copy.escrowSection().epoch() == 0
                    && copy.escrowSection().markers().isEmpty(), "S6 R2 changed its empty historical copy");
        }
        String details = "cut=" + cut + " tank=4000 bank=1000 total=5000 epochs=1,1 records=0 markers=0 journal=1 copies="
                + copies.size() + " reason=FLOOR_CONFLICT passenger=" + (joinedAt < 0 ? "none" : "unmounted");
        if (recovery) {
            done = true; AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_R2_RESULT {}", details); return;
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_R2_PAUSED {}", details);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 R2 forced stop did not arrive within 30 seconds");
    }

    private List<RocketEntity> rockets() { return server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(14)); }

    private void conserved(ResourceEscrowSavedData data) {
        EscrowTags.require(amount(loader) == 4_000 && bank(rocket, false) == 1_000 && bank(rocket, true) == 0
                && section(loader).epoch() == 1 && rocket.escrowSection().epoch() == 1 && data.ledger().issuedThrough() == 1
                && data.bind(server).admissionAuthority().pendingCount() == 0 && rocket.getX() == 64.5 && rocket.getY() == 100 && rocket.getZ() == 64.5
                && rocket.flightData().orElseThrow().plan().isEmpty() && rocket.getPassengers().isEmpty(),
                "S6 R2 changed the retired loader fill");
    }

    static RocketTransferRecord stale(String cut, RocketStructureSnapshot source, UUID physical, UUID logical, UUID owner, RocketFuelState fuel) {
        EscrowTags.require(CASES.contains(cut), "Unknown S6 R2 fixture");
        var at = source.sourceOrigin(); var dimension = source.sourceDimension();
        var destination = source.relocated(UUID.randomUUID(), dimension, new RocketPosition(at.x() + 8, at.y(), at.z()), 0);
        var transfer = UUID.randomUUID();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                dimension, dimension, 1_000, 0).withPropulsionDebit(new RocketPropulsionDebit(1_000, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W211 stale loader history")));
        var flight = RocketFlightData.initial(logical, source.stats().fuelCapacity(), source.stats().seatCount(), plan.sourceBody(), dimension, at, 0)
                .withFuel(fuel, 0).withPlan(plan);
        if (RECONNECT.equals(cut)) { flight = flight.withPassengers(flight.passengers().assign(PASSENGER).orElseThrow()); }
        flight = flight.startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), dimension, destination.sourceOrigin(), 160);
        return RocketTransferRecord.create(transfer, logical, owner, physical, source, destination, flight, arrival, 1_000, 0);
    }
}
