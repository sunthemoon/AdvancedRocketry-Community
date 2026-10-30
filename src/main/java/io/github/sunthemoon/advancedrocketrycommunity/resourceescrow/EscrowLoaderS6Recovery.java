package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;

/** Read-only per-tick observer of S6's loaded native endpoints. Never runs K7's scalar-sum recovery check. */
final class EscrowLoaderS6Recovery {
    private static final BlockPos ORIGIN = new BlockPos(64, 100, 64);
    private static final BlockPos SOURCE = new BlockPos(66, 100, 64);
    private final MinecraftServer server;
    private final UUID retiredOperation;
    private Identity identity;
    private long began = -1;
    private boolean done;
    private int observations;

    static int activeMarkers(UUID retiredOperation, UUID ledger, long generation, long issued, int records,
            UUID bound, long epoch, String role, List<EscrowPrefix.Marker> markers) {
        if (retiredOperation == null || records != 0) { return markers.size(); }
        EscrowTags.require(generation >= 3 && issued == 1 && Objects.equals(ledger, bound) && epoch == 1
                && markers.size() <= 1, "S6 retired metadata lacks exact durable retirement authority");
        EscrowTags.require(markers.isEmpty() || markers.get(0).equals(new EscrowPrefix.Marker(retiredOperation, 1, role, 1)),
                "S6 retired marker differs from the observed operation/frontier");
        return 0;
    }

    private record Identity(UUID ledger, UUID device, UUID physical, UUID logical, UUID owner,
                            UUID snapshot, String snapshotHash, Object binding) { }

    EscrowLoaderS6Recovery(MinecraftServer server) {
        this(server, null);
    }

    EscrowLoaderS6Recovery(MinecraftServer server, UUID retiredOperation) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server;
        this.retiredOperation = retiredOperation;
    }

    void tick() {
        if (done) { return; }
        if (began < 0) {
            began = server.getTickCount();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_BEGIN tick={}", began);
        }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 recovery exceeded the existing fixture bound");
        var level = server.overworld();
        if (level.getChunkSource().getChunkNow(4, 4) == null
                || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(SOURCE).toLong())) {
            unknown("UNLOADED"); return;
        }
        var rockets = level.getEntitiesOfClass(RocketEntity.class, new AABB(ORIGIN).inflate(8));
        if (rockets.isEmpty() || !(level.getBlockEntity(SOURCE) instanceof FluidFuelLoaderBlockEntity)) {
            unknown("ENDPOINT_NOT_OBSERVED"); return;
        }
        EscrowTags.require(rockets.size() == 1, "S6 duplicated native rocket");
        var rocket = rockets.get(0);
        var loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(SOURCE);
        EscrowTags.require(loader.root().isPresent() && rocket.operational(), "S6 UNKNOWN native root");
        var root = loader.root().orElseThrow();
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.operational(), "S6 UNKNOWN durable ledger");
        var ledger = data.ledger(); var coordinator = data.bind(server);
        var observed = new Identity(ledger.id(), root.device(), rocket.getUUID(),
                rocket.assemblyTransactionId().orElseThrow(), rocket.ownerId().orElseThrow(),
                rocket.snapshot().orElseThrow().snapshotId(), rocket.snapshot().orElseThrow().contentHash(), root.binding());
        EscrowTags.require(identity == null || identity.equals(observed), "S6 changed identity during recovery");
        identity = observed;
        EscrowTags.require(root.owner().equals(identity.owner()) && root.binding().equals(binding(rocket))
                && root.tank().fluid().equals(ROCKET_FUEL) && root.tank().tag() == null && bank(rocket, true) == 0
                && rocket.getPassengers().isEmpty() && rocket.flightData().orElseThrow().plan().isEmpty()
                && rocket.flightData().orElseThrow().activeTransferId().isEmpty()
                && ledger.issuedThrough() == 1 && ledger.records().size() <= 1,
                "S6 changed owner, binding, fluid or authority");
        var source = section(loader); var destination = rocket.escrowSection();
        Long q = null;
        boolean sourceAfter = source.epoch() == 1, destinationAfter = destination.epoch() == 1;
        if (!ledger.records().isEmpty()) {
            var op = ledger.records().get(0);
            EscrowTags.require(op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL && op.sequence() == 1
                    && op.source().identity().equals(root.device()) && op.destination().identity().equals(rocket.getUUID())
                    && op.owner().equals(root.owner()) && op.guard().rocket().equals(identity.logical())
                    && op.nativeBinding().complete() && op.nativeBinding().invariant().equals(EscrowInvariant.rocket(rocket))
                    && op.payload().equals(tank(ROCKET_FUEL, 1_000).encode()), "S6 durable record binding changed");
            q = (long) op.payload().getInt("Amount");
            sourceAfter = after(ledger.id(), op, op.source(), "SOURCE", source.ledger(), source.epoch(),
                    root.signature(), source.markers());
            destinationAfter = after(ledger.id(), op, op.destination(), "DESTINATION", destination.ledger(),
                    destination.epoch(), rocket.flightData().orElseThrow().fuel().resourceSignature(), destination.markers());
            EscrowTags.require(coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                    && coordinator.admissionAuthority().pendingCount() == 1, "S6 released an active record");
        } else {
            EscrowTags.require(ledger.id().equals(source.ledger()) && ledger.id().equals(destination.ledger()),
                    "S6 completed ledger binding changed");
        }
        int markerCount = source.markers().size() + destination.markers().size();
        int activeMarkers = activeMarkers(retiredOperation, ledger.id(), ledger.writerGeneration(), ledger.issuedThrough(),
                ledger.records().size(), source.ledger(), source.epoch(), "SOURCE", source.markers())
                + activeMarkers(retiredOperation, ledger.id(), ledger.writerGeneration(), ledger.issuedThrough(),
                ledger.records().size(), destination.ledger(), destination.epoch(), "DESTINATION", destination.markers());
        var partition = EscrowLoaderS6Accounting.classify(true, amount(loader), bank(rocket, false), q,
                sourceAfter, destinationAfter, activeMarkers);
        String retired = retiredOperation == null ? "" : " retired=" + (markerCount - activeMarkers)
                + " retired_operation=" + retiredOperation;
        observations++;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ACCOUNT tick={} known=true records={} q={} source={} destination={} source_available={} source_held={} destination_available={} destination_held={} in_transit={} spendable={}{}",
                server.getTickCount(), ledger.records().size(), q, amount(loader), bank(rocket, false),
                partition.sourceAvailable(), partition.sourceHeld(), partition.destinationAvailable(),
                partition.destinationHeld(), partition.inTransit(), q == null && markerCount == 0 ? "5000" : "UNKNOWN", retired);
        if (server.getTickCount() - began < 100 || !coordinator.writerIdle() || q != null || markerCount != 0) { return; }
        EscrowTags.require(!loader.escrowLocked() && RocketEscrowGate.idle(rocket)
                && coordinator.admissionAuthority().pendingCount() == 0, "S6 completed image still reserved");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_RESULT cut={} observations={} total=5000 records=0 markers=0 source_epoch=1 destination_epoch=1 start_tick={} end_tick={} total_observations={}",
                EscrowLoaderS6Probe.E3, observations, began, server.getTickCount(), server.getTickCount() - began + 1);
        done = true;
    }

    /** Independent exact single-record prefix check; malformed/foreign/UNKNOWN observations cannot become BEFORE. */
    static boolean after(UUID ledger, EscrowOperation op, EscrowEndpoint endpoint, String role,
            UUID boundLedger, long epoch, String signature, List<EscrowPrefix.Marker> markers) {
        boolean before = epoch == endpoint.beforeEpoch() && markers.isEmpty()
                && signature.equals(endpoint.beforeSignature()) && (boundLedger == null || ledger.equals(boundLedger));
        if (before) { return false; }
        EscrowTags.require(Objects.equals(ledger, boundLedger) && epoch == endpoint.afterEpoch()
                && signature.equals(endpoint.afterSignature())
                && markers.equals(List.of(new EscrowPrefix.Marker(op.id(), op.sequence(), role, endpoint.afterEpoch()))),
                "S6 endpoint is not an exact record-backed BEFORE/AFTER prefix");
        return true;
    }

    private void unknown(String reason) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ACCOUNT tick={} known=false reason={} raw=UNKNOWN available=UNKNOWN held=UNKNOWN",
                server.getTickCount(), reason);
    }
}
