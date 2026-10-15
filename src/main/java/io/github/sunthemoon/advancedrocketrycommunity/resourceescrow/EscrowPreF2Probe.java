package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

/** Empty-server-only S2 fixture for the F1 checkpoint and Root's pre-F2 cancellation rule. */
final class EscrowPreF2Probe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketTransferRecord prepared;
    private EscrowFlightAuthority.Launch launch;
    private CompoundTag sourceEnvelope;
    private CompoundTag destinationEnvelope;
    private volatile boolean armWriting;
    private boolean waiting;
    private boolean done;
    private long began;
    private int step;

    EscrowPreF2Probe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(List.of("PF_ARM_BEFORE", "PF_ARM_REPLACED", "PF_ARM_ACK", "PF_T_ACK", "PF_GOVERNED_T_ACK").contains(cut), "Unknown pre-F2 cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
    }

    void checkpoint(String name) {
        if (recovery || done) { return; }
        if (name.equals("F1_ARM_CAPTURE")) { armWriting = true; }
        if (armWriting && (cut.equals("PF_ARM_BEFORE") && name.equals("K8")
                || cut.equals("PF_ARM_REPLACED") && name.equals("K9"))
                || cut.equals("PF_ARM_ACK") && name.equals("F1_ARM_ACK")) { pause(); }
    }

    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Pre-F2 forced stop did not arrive within 30 seconds");
    }

    void tick() {
        var level = server.overworld();
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong()) || !level.isPositionEntityTicking(POSITION)) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); data.bind(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); data.bind(server).autosave(); }
        }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (recovery) {
            var rockets = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(8));
            if (rockets.isEmpty()) { return; }
            EscrowTags.require(rockets.size() == 1, "Duplicate F1 source"); rocket = rockets.get(0);
            var arms = data.ledger().armedFlights(); boolean before = cut.equals("PF_ARM_BEFORE"), hasT = hasTransfer();
            if (!before && (arms.isEmpty() || !hasT && arms.get(0).outcome() != EscrowFlightTables.Outcome.CANCELLED)) { return; }
            // Let repeated scheduler visits try the negative case; a single early observation is insufficient.
            if (hasT && server.getTickCount() - began < 100) { return; }
            EscrowTags.require(before ? arms.isEmpty() && data.ledger().issuedLaunchSerial() == 0
                    : arms.size() == 1 && arms.get(0).outcome() == (hasT ? EscrowFlightTables.Outcome.ARMED : EscrowFlightTables.Outcome.CANCELLED)
                    && arms.get(0).serial() == 1 && data.ledger().issuedLaunchSerial() == 1
                    && arms.get(0).sourceEnvelope().equals(EscrowNativeEnvelope.digest(EscrowNativeEnvelope.rocket(rocket))), "Wrong restarted F1 outcome or E");
            EscrowTags.require(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000 && rocket.escrowSection().epoch() == 1
                    && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty()
                    && rocket.flightData().orElseThrow().passengers().assignments().size() == 1
                    && rocket.flightData().orElseThrow().state().acceptsFuel() && journal.entries().size() == (hasT ? 1 : 0), "F1 changed fuel, manifest, epoch or journal");
            if (cut.equals("PF_GOVERNED_T_ACK")) {
                var record = journal.entries().get(0); var envelope = record.nativeTransfer().orElseThrow();
                EscrowTags.require(envelope.armed().equals(arms.get(0))
                        && envelope.sourceEnvelope(record).equals(EscrowNativeEnvelope.rocket(rocket)), "Restart changed governed T or checkpoint source");
            }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_RESULT cut={} outcome={} source={} fuel=1000 epoch=1 manifest=1 debits=0",
                    cut, before ? "UNARMED" : arms.get(0).outcome(), rocket.getUUID()); done = true; return;
        }
        if (step == 0) {
            // Give the before-replacement cut an actual checked empty ledger to retain.
            if (data.ledger().writerGeneration() == 0 || !data.bind(server).writerIdle()) {
                data.bind(server).autosave(); return;
            }
            // Establish world metadata before creating/refuelling the source. No fixture save follows.
            EscrowTags.require(server.saveEverything(false, true, true), "World metadata baseline refused");
            rocket = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
            rocket.load(parent); EscrowTags.require(level.addFreshEntity(rocket), "F1 source publication refused");
            var flight = rocket.flightData().orElseThrow();
            var fuel = flight.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
            rocket.updateFlightData(flight.withFuel(fuel, level.getGameTime()).withPassengers(flight.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var frozen = new EscrowFlightSource(rocket); var logical = rocket.assemblyTransactionId().orElseThrow();
            var transfer = UUID.randomUUID(); var destination = UUID.randomUUID();
            prepared = prepared(rocket, transfer);
            sourceEnvelope = frozen.envelope.copy();
            if (cut.equals("PF_GOVERNED_T_ACK")) {
                // Create an unregistered native observation only. Nothing publishes or saves a destination.
                var candidate = io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(level);
                EscrowTags.require(candidate != null, "Missing native destination adapter"); candidate.setUUID(destination);
                candidate.initializeTransferred(prepared.destinationSnapshot(), logical, rocket.ownerId().orElseThrow(), prepared.destinationFlightData());
                var body = candidate.saveWithoutId(new CompoundTag());
                body.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
                candidate.load(body); destinationEnvelope = EscrowNativeEnvelope.rocket(candidate);
            }
            var guard = journal.acknowledgedGuard(logical).orElseThrow();
            launch = new EscrowFlightAuthority.Launch(logical, rocket.ownerId().orElseThrow(), rocket.getUUID(), rocket.escrowSection(),
                    frozen.signature, frozen.invariant, transfer, destination, frozen.invariant, prepared.checksum(), frozen.digest,
                    destinationEnvelope == null ? RocketNativeResources.hash("pre-F2 fixture destination:" + destination)
                            : EscrowNativeEnvelope.digest(destinationEnvelope),
                    new EscrowGuard(logical, null, Math.max(1, guard.sequence()), guard.rootDigest()), Set.of());
            step = 1;
        }
        if (step == 1) {
            waiting = EscrowRuntime.of(server).flightCheckpoint().begin(rocket, launch, arm -> {
                waiting = false; EscrowTags.require(arm != null, "Native F1 checkpoint/ARMED failed");
                if (hasTransfer()) {
                    if (cut.equals("PF_GOVERNED_T_ACK")) {
                        var envelope = EscrowTransferEnvelope.capture(arm, sourceEnvelope, destinationEnvelope,
                                prepared.sourceSnapshot(), prepared.destinationSnapshot(), prepared.sourceFlightData(), prepared.destinationFlightData());
                        prepared = RocketTransferRecord.createGoverned(prepared.transferId(), prepared.logicalRocketId(), prepared.ownerId(),
                                prepared.sourceEntityId(), prepared.sourceSnapshot(), prepared.destinationSnapshot(), prepared.sourceFlightData(),
                                prepared.destinationFlightData(), prepared.requiredFuel(), prepared.createdAtGameTime(), envelope);
                    }
                    journal.put(prepared); journal.flushChecked(server); step = 2;
                }
            });
        } else if (step == 2 && journal.acknowledgedCurrent()) { pause(); }
    }

    private boolean hasTransfer() { return cut.equals("PF_T_ACK") || cut.equals("PF_GOVERNED_T_ACK"); }

    private static RocketTransferRecord prepared(RocketEntity rocket, UUID transfer) {
        var from = rocket.snapshot().orElseThrow(); var fuel = rocket.flightData().orElseThrow().fuel();
        var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), 160);
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                from.sourceDimension(), from.sourceDimension(), 500, 0).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("pre-F2 fixture catalog")));
        var flight = rocket.flightData().orElseThrow().withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), to.sourceOrigin(), 160);
        return RocketTransferRecord.create(transfer, flight.logicalRocketId(), rocket.ownerId().orElseThrow(), rocket.getUUID(), from, to, flight, arrival, 500, 0);
    }
}
