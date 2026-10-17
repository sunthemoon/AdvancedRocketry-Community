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
    private final int restart;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketTransferRecord prepared;
    private EscrowFlightAuthority.Launch launch;
    private EscrowGovernedLaunch governed;
    private volatile boolean armWriting;
    private boolean waiting;
    private boolean done;
    private long began;
    private int step;
    private String cancelledSource;
    private boolean observedPrune;
    private boolean observedCountdown;
    private RocketTransferRecord recoveredTransfer;
    private CompoundTag landedEnvelope;

    EscrowPreF2Probe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(List.of("PF_ARM_BEFORE", "PF_ARM_REPLACED", "PF_ARM_ACK", "PF_T_ACK", "PF_GOVERNED_T_ACK",
                "PF_LIVE_F2_ACK", "PF_LIVE_COUNTDOWN").contains(cut), "Unknown pre-F2 cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        restart = Integer.getInteger("advancedrocketrycommunity.escrowPreF2Restart", 0);
        EscrowTags.require(!recovery || !(cancellationCut() || governedTransfer()) || restart == 1 || restart == 2,
                "Pre-F2 recovery requires an explicit first or second restart");
    }

    void checkpoint(String name) {
        EscrowTags.require(!recovery || !governedTransfer() || !name.equals("F1_CANCELLED"),
                "Pre-F2 cancellation acted while governed T exists");
        if (recovery && cancellationCut() && !done) {
            if (name.equals("F1_CANCELLED")) { observeCancelled(); }
            if (name.equals("F8_PRUNE_ACK")) {
                EscrowTags.require(restart == 1 && cancelledSource != null, "Pre-F2 prune preceded observed cancellation");
                observedPrune = true;
            }
        }
        if (recovery || done) { return; }
        if (name.equals("F1_ARM_CAPTURE")) { armWriting = true; }
        if (armWriting && (cut.equals("PF_ARM_BEFORE") && name.equals("K8")
                || cut.equals("PF_ARM_REPLACED") && name.equals("K9"))
                || cut.equals("PF_ARM_ACK") && name.equals("F1_ARM_ACK")
                || cut.equals("PF_LIVE_F2_ACK") && name.equals("F2_PREPARED_ACK")
                || cut.equals("PF_LIVE_COUNTDOWN") && name.equals("F2_COUNTDOWN")) { pause(); }
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
        if (recovery && cancellationCut() && restart == 2) { requireCleanedRoot(journal); }
        if (!journal.retryChecked(server)) { return; }
        if (recovery && governedTransfer()) { recoverGoverned(journal); return; }
        if (recovery) {
            var rockets = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(8));
            if (rockets.isEmpty()) { return; }
            EscrowTags.require(rockets.size() == 1, "Duplicate F1 source"); rocket = rockets.get(0);
            if (cancellationCut()) { recoverCancelled(journal); return; }
            if (EscrowCrewedProbe.enabled()) { EscrowCrewedProbe.reconnect(rocket); }
            var arms = data.ledger().armedFlights(); boolean before = cut.equals("PF_ARM_BEFORE"), hasT = hasTransfer();
            if (!before && (arms.isEmpty() || !hasT && arms.get(0).outcome() != EscrowFlightTables.Outcome.CANCELLED)) { return; }
            // Let repeated scheduler visits try the negative case; a single early observation is insufficient.
            if (hasT && server.getTickCount() - began < 100) { return; }
            EscrowTags.require(before ? arms.isEmpty() && data.ledger().issuedLaunchSerial() == 0
                    : arms.size() == 1 && arms.get(0).outcome() == (hasT ? EscrowFlightTables.Outcome.ARMED : EscrowFlightTables.Outcome.CANCELLED)
                    && arms.get(0).serial() == 1 && data.ledger().issuedLaunchSerial() == 1
                    && arms.get(0).sourceEnvelope().equals(EscrowGovernedEnvelope.digest(EscrowNativeEnvelope.rocket(rocket))), "Wrong restarted F1 outcome or E");
            EscrowTags.require(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000 && rocket.escrowSection().epoch() == 1
                    && rocket.flightData().orElseThrow().fuel().committedDebits().isEmpty()
                    && rocket.flightData().orElseThrow().passengers().assignments().size() == 1
                    && rocket.flightData().orElseThrow().state().acceptsFuel() && journal.entries().size() == (hasT ? 1 : 0), "F1 changed fuel, manifest, epoch or journal");
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
            EscrowCrewedProbe.mount(rocket, cut);
            var frozen = new EscrowFlightSource(rocket); var logical = rocket.assemblyTransactionId().orElseThrow();
            var transfer = UUID.randomUUID(); var destination = UUID.randomUUID();
            prepared = prepared(rocket, transfer);
            var guard = journal.acknowledgedGuard(logical).orElseThrow();
            var checked = new EscrowGuard(logical, null, Math.max(1, guard.sequence()), guard.rootDigest());
            if (cut.equals("PF_GOVERNED_T_ACK")) {
                var countdown = rocket.flightData().orElseThrow().withPlan(prepared.sourceFlightData().plan().orElseThrow())
                        .startCountdown(level.getGameTime());
                governed = EscrowGovernedLaunch.prepare(data.ledger(), frozen.envelope, prepared.sourceSnapshot(), countdown,
                        prepared.destinationSnapshot(), destination, checked);
            } else {
                launch = new EscrowFlightAuthority.Launch(logical, rocket.ownerId().orElseThrow(), rocket.getUUID(), rocket.escrowSection(),
                        frozen.signature, frozen.invariant, transfer, destination, frozen.invariant, prepared.checksum(), frozen.digest,
                        RocketNativeResources.hash("pre-F2 fixture destination:" + destination), checked, Set.of());
            }
            step = 1;
        }
        if (step == 1) {
            if (livePreparation()) {
                var countdown = rocket.flightData().orElseThrow().withPlan(prepared.sourceFlightData().plan().orElseThrow())
                        .startCountdown(level.getGameTime());
                waiting = EscrowGovernedPreparation.begin(rocket, countdown, prepared.destinationSnapshot(), record -> true,
                        record -> { prepared = record; waiting = false; step = 2; },
                        () -> { throw new IllegalStateException("Native live F1/F2 preparation was discarded"); });
                return;
            }
            if (governed != null) {
                waiting = EscrowRuntime.of(server).flightCheckpoint().begin(rocket, governed, record -> {
                    waiting = false; EscrowTags.require(record != null, "Native governed F1 checkpoint/ARMED failed");
                    prepared = record; journal.put(prepared); journal.flushChecked(server); step = 2;
                });
                return;
            }
            waiting = EscrowRuntime.of(server).flightCheckpoint().begin(rocket, launch, arm -> {
                waiting = false; EscrowTags.require(arm != null, "Native F1 checkpoint/ARMED failed");
                if (hasTransfer()) {
                    journal.put(prepared); journal.flushChecked(server); step = 2;
                }
            });
        } else if (step == 2 && journal.acknowledgedCurrent()) { pause(); }
    }

    private boolean cancellationCut() {
        return cut.equals("PF_ARM_REPLACED") || cut.equals("PF_ARM_ACK");
    }

    private void recoverGoverned(RocketTransferSavedData journal) {
        var root = data.ledger(); var arms = root.armedFlights();
        EscrowTags.require(arms.size() == 1 && root.issuedLaunchSerial() == 1 && root.records().isEmpty()
                && root.floors().isEmpty() && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty()
                && journal.entries().size() == 1, "Governed recovery changed its arm, serial or T count");
        var arm = arms.get(0); var record = journal.entries().get(0);
        EscrowTags.require(arm.outcome() == EscrowFlightTables.Outcome.ARMED && arm.serial() == 1
                && arm.transfer().equals(record.transferId()) && arm.equals(record.nativeTransfer().orElseThrow().armed())
                && journal.acknowledgedGuard(arm.logical()).orElseThrow().transfer().equals(arm.transfer()),
                "Governed recovery cancelled or replaced its checked T");
        if (recoveredTransfer == null) { recoveredTransfer = record; }
        EscrowTags.require(record.nativeTransfer().equals(recoveredTransfer.nativeTransfer())
                && record.transferId().equals(recoveredTransfer.transferId())
                && record.sourceFlightData().equals(recoveredTransfer.sourceFlightData())
                && record.destinationFlightData().equals(recoveredTransfer.destinationFlightData()), "Governed recovery changed its frozen flight");
        if (restart == 2) {
            EscrowTags.require(record.phase() == RocketTransferPhase.COMMITTED, "Second recovery replayed its completed flight");
        }
        var candidates = server.overworld().getEntitiesOfClass(RocketEntity.class,
                new net.minecraft.world.phys.AABB(63, 90, 63, 96, 160, 80));
        EscrowTags.require(candidates.size() <= 2 && candidates.stream().allMatch(entity ->
                entity.assemblyTransactionId().filter(arm.logical()::equals).isPresent()
                        && (entity.getUUID().equals(arm.source()) || entity.getUUID().equals(arm.destination()))),
                "Governed recovery has a duplicate or foreign rocket");
        var sources = candidates.stream().filter(entity -> entity.getUUID().equals(arm.source())).toList();
        var destinations = candidates.stream().filter(entity -> entity.getUUID().equals(arm.destination())).toList();
        EscrowTags.require(sources.size() <= 1 && destinations.size() <= 1, "Governed recovery duplicated an endpoint");
        if (!sources.isEmpty()) {
            var source = sources.get(0); var flight = source.flightData().orElseThrow();
            EscrowTags.require(restart == 1 && EscrowGovernedPhaseAction.sourceMatches(record,
                    EscrowGovernedEnvelope.capture(source), source.level().getGameTime()), "Governed recovery changed its complete source progression");
            if (!observedCountdown && flight.state() == RocketFlightState.COUNTDOWN) {
                var checkpointDigest = countdownCheckpoint(record, EscrowNativeEnvelope.rocket(source), source.level().getGameTime());
                observedCountdown = true;
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_COUNTDOWN cut={} transfer={} serial=1 issued=1 checkpoint_digest={} fuel=1000 epoch=1 manifest=1 debits=0",
                        cut, record.transferId(), checkpointDigest);
            }
        }
        if (destinations.isEmpty()) { return; }
        var destination = destinations.get(0); var flight = destination.flightData().orElseThrow();
        var observed = EscrowGovernedEnvelope.capture(destination);
        EscrowTags.require(EscrowGovernedPhaseAction.destinationMatches(record, observed), "Governed recovery changed its destination progression");
        if (record.phase() != RocketTransferPhase.COMMITTED || flight.state() != RocketFlightState.LANDED) { return; }
        EscrowTags.require(sources.isEmpty() && candidates.size() == 1 && (restart == 2 || observedCountdown)
                && flight.fuel().totalAmount() == 500 && flight.fuel().committedDebits().equals(List.of(arm.transfer()))
                && flight.passengers().equals(record.sourceFlightData().passengers())
                && flight.passengers().assignments().size() == 1
                && flight.passengers().assignments().stream().allMatch(assignment -> server.getPlayerList().getPlayer(assignment.passengerId()) == null)
                && destination.escrowSection().epoch() == 0 && destination.escrowSection().markers().isEmpty()
                && !RocketEscrowGate.idle(destination), "Governed recovery lacks its stable F6 passenger wait");
        if (landedEnvelope == null) { landedEnvelope = observed; }
        EscrowTags.require(landedEnvelope.equals(observed), "Governed recovery changed its stable landed survivor");
        if (server.getTickCount() - began < 100) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_RESULT cut={} outcome=RESUMED phase=COMMITTED state=LANDED transfer={} serial=1 issued=1 fuel=500 epoch=0 manifest=1 debits=1 restart={}",
                cut, record.transferId(), restart); done = true;
    }

    /** Validate every COUNTDOWN field before projecting only its permitted flight transition back to F1. */
    static String countdownCheckpoint(RocketTransferRecord record, CompoundTag observed, long levelTime) {
        var flight = EscrowGovernedArrivalState.flight(observed);
        var clock = EscrowGovernedSourceClock.resume(record, observed, levelTime);
        EscrowTags.require(record.phase() == RocketTransferPhase.PREPARED && flight.state() == RocketFlightState.COUNTDOWN
                && flight.fuel().totalAmount() == 1_000 && flight.fuel().committedDebits().isEmpty()
                && flight.passengers().assignments().size() == 1
                && EscrowGovernedSourceRecovery.sourceMatches(record, observed, clock.at(levelTime)),
                "First COUNTDOWN differs from its complete permitted progression");
        var comparison = EscrowGovernedEnvelope.comparison(observed);
        comparison.put("flight", record.nativeTransfer().orElseThrow().sourceEnvelope(record).getCompound("flight").copy());
        var digest = EscrowGovernedEnvelope.digest(comparison);
        EscrowTags.require(record.nativeTransfer().orElseThrow().armed().sourceEnvelope().equals(digest)
                && RocketEscrowSection.decode(comparison.getCompound("wrapper")).epoch() == 1,
                "First COUNTDOWN changed the R18 checkpoint digest or epoch");
        return digest;
    }

    private void observeCancelled() {
        EscrowTags.require(restart == 1 && cancelledSource == null, "Unexpected repeated pre-F2 cancellation");
        var root = data.ledger(); var arms = root.armedFlights();
        EscrowTags.require(arms.size() == 1, "Pre-F2 cancellation lost its acknowledged arm");
        var arm = arms.get(0);
        var entity = server.overworld().getEntity(arm.source());
        EscrowTags.require(entity instanceof RocketEntity, "Pre-F2 cancellation lost its source");
        rocket = (RocketEntity) entity;
        EscrowTags.require(arm.outcome() == EscrowFlightTables.Outcome.CANCELLED && arm.serial() == 1
                && root.issuedLaunchSerial() == 1 && arm.logical().equals(rocket.assemblyTransactionId().orElseThrow())
                && arm.sourceEnvelope().equals(EscrowGovernedEnvelope.digest(EscrowNativeEnvelope.rocket(rocket)))
                && EscrowFlightJournal.noTransfer(RocketTransferSavedData.get(server), arm.logical(), arm.transfer(), arm.serial()).getAsBoolean(),
                "Wrong acknowledged pre-F2 cancellation, source digest or checked no-T");
        cancelledSource = arm.sourceEnvelope();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_CANCELLED cut={} source={} serial=1 issued=1 outcome=CANCELLED digest={}",
                cut, rocket.getUUID(), cancelledSource);
    }

    private void requireCleanedRoot(RocketTransferSavedData journal) {
        var root = data.ledger();
        EscrowTags.require(root.armedFlights().isEmpty() && root.records().isEmpty() && root.floors().isEmpty()
                && root.tombstones().isEmpty() && root.rocketAuthorities().isEmpty()
                && root.issuedLaunchSerial() == 1 && journal.entries().isEmpty(), "No-T pre-F2 root is not cleaned");
    }

    private void recoverCancelled(RocketTransferSavedData journal) {
        if (EscrowCrewedProbe.enabled()) {
            EscrowTags.require(EscrowCrewedProbe.reconnect(rocket).getVehicle() == null, "Cancelled restart re-seated its passenger");
        }
        var flight = rocket.flightData().orElseThrow();
        var digest = EscrowGovernedEnvelope.digest(EscrowNativeEnvelope.rocket(rocket));
        if (restart == 2 && cancelledSource == null) { cancelledSource = digest; }
        EscrowTags.require(rocket.getPassengers().isEmpty()
                && flight.fuel().totalAmount() == 1_000 && flight.fuel().committedDebits().isEmpty()
                && flight.passengers().assignments().size() == 1 && flight.state().acceptsFuel()
                && flight.plan().isEmpty() && flight.activeTransferId().isEmpty()
                && rocket.escrowSection().epoch() == 1 && rocket.escrowSection().markers().isEmpty()
                && data.ledger().id().equals(rocket.escrowSection().ledger())
                && rocket.getX() == 64.5 && rocket.getY() == 100 && rocket.getZ() == 64.5
                && (cancelledSource == null || cancelledSource.equals(digest))
                && journal.entries().isEmpty(), "No-T pre-F2 recovery changed source E or journal");
        if (restart == 1) {
            if (!data.ledger().armedFlights().isEmpty()) { return; }
            EscrowTags.require(cancelledSource != null && observedPrune, "No-T pre-F2 recovery skipped checked cancellation/prune");
        }
        requireCleanedRoot(journal);
        if (!data.bind(server).writerIdle() || !journal.actionAuthorityAvailable()) { return; }
        EscrowTags.require(journal.acknowledgedGuard(rocket.assemblyTransactionId().orElseThrow()).orElseThrow().transfer() == null
                && RocketEscrowGate.idle(rocket), "No-T pre-F2 cleanup lacks checked no-T or idle source");
        // The second recovery must remain cleaned throughout the existing negative-case observation window.
        if (restart == 2 && server.getTickCount() - began < 100) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PRE_F2_RESULT cut={} outcome=CANCELLED_CLEANED source={} checked=true fuel=1000 epoch=1 manifest=1 debits=0 restart={}",
                cut, rocket.getUUID(), restart); done = true;
    }

    private boolean hasTransfer() { return cut.equals("PF_T_ACK") || governedTransfer(); }
    private boolean governedTransfer() { return cut.equals("PF_GOVERNED_T_ACK") || livePreparation(); }
    private boolean livePreparation() { return cut.equals("PF_LIVE_F2_ACK") || cut.equals("PF_LIVE_COUNTDOWN"); }

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
