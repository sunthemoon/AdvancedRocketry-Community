package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptWriter;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * The bay deployment coordinator for one server session (W127-PLAN; W128's one-page design and its resumption under
 * ADR-074; W170's for the registered half): the section 8.1 gate, D0 at the ascent-complete checkpoint, D1's INTENT or
 * re-arm through the real receipt store, D2's registration under a registry permit, D3's checkpoint, D4's group
 * publication, D5's consumption, D6's source proof, D7's guarded retirement, the exits, and the restart rows with the
 * source resume and W185's bounded copy/absence census. Released flight belongs to W27d4. Admission is gated (only the GameTest server and
 * gated release-test hooks start D0); the restart rows always run. Completions only mark state and enqueue; every
 * write is a queued step on the server thread.
 */
final class BayTransferRecovery implements RocketTransferBayRecoveryGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int GROUP_STEPS = 6;
    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final BayClaimBook claims = new BayClaimBook();
    private final BayStepQueue queue = new BayStepQueue();
    private final BayDeploymentChecks checks = new BayDeploymentChecks();
    private final BayRegistrationSteps registration;
    private final BayGroupPublication publication;
    private final BayConsumptionSteps consumption;
    private final BayCopySteps copies;
    private final BaySourceProof proof;
    private final BaySourceResume resume;
    private final Map<UUID, String> audited = new HashMap<>();
    // Gated seams (W128R-R2): GameTests name single rockets; the native B10 case blocks the registry view for the session.
    private final java.util.Set<UUID> registryBlockedForTests = new java.util.HashSet<>();
    private final java.util.Set<UUID> checkpointRefusedForTests = new java.util.HashSet<>();
    @Nullable private BayDeploymentCuts cuts;
    private boolean stopping, ticked, registryBlockedSession, refuseRegistration;

    private BayTransferRecovery(MinecraftServer server, BayDeploymentSession session) {
        this.server = server; this.session = session;
        registration = new BayRegistrationSteps(server, session, checks, () -> refuseRegistration);
        publication = new BayGroupPublication(server, session, registration, this::offer,
                (claim, receipt) -> { if (cuts != null) { cuts.writing(claim, receipt); } });
        copies = new BayCopySteps(server);
        consumption = new BayConsumptionSteps(server, publication, copies);
        proof = new BaySourceProof(server, session, this::offer, (claim, op) -> { if (cuts != null) { cuts.removing(claim, op); } },
                claim -> cuts != null && cuts.stallAfterConsumption(claim), (claim, guard) -> cuts == null ? guard : cuts.guard(claim, guard));
        resume = new BaySourceResume(server);
    }

    private void offer(UUID logical) { if (!stopping) { queue.offer(logical); } }

    /** ServerStartingEvent, after the receipt index: the session handle, the claim predicate, then the restart rows. */
    static BayTransferRecovery start(MinecraftServer server) {
        var session = EscrowRuntime.of(server).bayReceipts(server).session(server);
        var coordinator = new BayTransferRecovery(server, session);
        session.claims(coordinator.claims::holds);
        coordinator.beginRestart(session.receipts());
        coordinator.cuts = BayDeploymentCuts.configured(server);
        coordinator.registryBlockedSession = BayDeploymentCuts.registryBlocked(server);
        coordinator.refuseRegistration = BayDeploymentCuts.refuseRegistration(server);
        return coordinator;
    }

    /** One claim per rocket named by a pending receipt; each INTENT row is decided by a queued step from the next tick. */
    private List<BayClaim> beginRestart(List<BayReceipt> receipts) {
        var begun = new ArrayList<BayClaim>();
        for (BayClaim claim : BayRestartRows.claims(receipts)) {
            if (claims.begin(claim)) { audit(claim); queue.offer(claim.logical()); begun.add(claim); }
        }
        return begun;
    }

    // ---- The section 8.1 gate: coordinator RAM only, no IO, no load, no ticket. ----

    @Override
    public Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        UUID logical = record.logicalRocketId();
        BayClaim claim = claims.get(logical).orElse(null);
        if (claim != null) {
            if (!claim.transfer().equals(record.transferId())) {
                claim.repair("TRANSFER_AUTHORITY_CONFLICT"); audit(claim);
                return Claim.held(State.REPAIR, claim.reason());
            }
            var handoff = claims.report(logical, record.transferId());
            if (handoff.isPresent()) {
                audited.remove(logical); forgetState(claim);
                return handoff.get() == BayClaim.Handoff.READY_SOURCE ? Claim.readySource(claim.source().orElseThrow()) : Claim.ordinary();
            }
            return Claim.held(claim.hold() == BayClaim.Hold.REPAIR ? State.REPAIR : State.OWNED_WAIT, claim.reason());
        }
        if (BayReceipts.holdsRocket(server, logical)) {
            return Claim.held(State.OWNED_WAIT, session.unavailable() ? "BAY_RECEIPTS_UNAVAILABLE" : "BAY_RECEIPT_HOLD");
        }
        return Claim.unclaimed();
    }

    /** HELD exactly when section 8's bay part holds L, so an UNCLAIMED record never meets it (N1). No permits in W27d1. */
    @Override
    public Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, Action action) {
        return BayReceipts.holdsRocket(server, record.logicalRocketId()) ? Boundary.held() : Boundary.unclaimed();
    }

    @Override
    public void escrowConflict(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        claims.get(record.logicalRocketId()).ifPresent(claim -> { claim.repair("ESCROW_AUTHORITY_CONFLICT"); audit(claim); });
    }

    /** A join or leave of L's endpoint invalidates its group's D6/D7 proof (B16); the coordinator repeats D6. */
    @Override
    public void endpointChanged(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        claims.get(record.logicalRocketId()).flatMap(BayClaim::group).ifPresent(group -> {
            group.invalidate(); offer(record.logicalRocketId());
        });
    }

    /** D0's enqueue: a HELD claim at once when T's source snapshot has a bay holding a package (R49 Q1); none, no claim. */
    @Override
    public void ascentComplete(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, RocketEntity source) {
        if (stopping || shuttingDown() || !session.admissionEnabled() || claims.holds(record.logicalRocketId())) { return; }
        var bays = checks.bays(record.sourceSnapshot());
        if (bays.isEmpty()) { return; }
        var members = new ArrayList<BayMember>();
        bays.forEach(bay -> members.add(BayMember.candidate(bay.position(), bay.satellite())));
        BayClaim claim = BayClaim.checkpoint(record.logicalRocketId(), record.transferId(), source.getUUID(), members);
        if (!claims.begin(claim)) {
            members.forEach(member -> { if (member.state() == BayMember.State.CANDIDATE) { member.refuse("DEPLOYMENT_CAPACITY"); } });
            members.forEach(member -> remember(claim, member));
            return;
        }
        members.forEach(member -> remember(claim, member));
        audit(claim);
        queue.offer(claim.logical());
    }

    // ---- The session's steps: at most 32 per tick, round-robin by L, after the held claims' ticket renewal. ----

    void tick() {
        if (stopping) { return; }
        if (shuttingDown()) { stop(); return; }
        if (!ticked) { ticked = true; BayDeploymentCuts.firstTick(server); }
        renewTickets(); proof.observe(); // W170-R22: a watched source that changed invalidates its D6/D7 token first
        queue.run(this::step);
    }

    /** W170-R22: a rocket's position write or Level removal, from the entity itself; a watched L's proof is invalidated. */
    void sourceChanged(RocketEntity rocket) {
        if (stopping) { return; }
        proof.changed(rocket);
        if (rocket.isAddedToWorld()) { endpointSeen(rocket); }
    }

    void endpointSeen(RocketEntity rocket) { if (!stopping) { copies.seen(rocket, claims, this::offer); } }
    boolean mountAllowed(net.minecraft.world.entity.Entity player, RocketEntity source) { return copies.mountAllowed(player, source); }

    /** ServerStoppingEvent, or the first observed shutdown: discard every waiting step and run no further effect. */
    void stop() {
        if (stopping) { return; }
        stopping = true; queue.close(); checks.clear();
        registration.clear(); publication.clear(); consumption.clear(); proof.clear();
        LOGGER.info("ARCE_BAY_STOPPED claims={} unresolved={}", claims.size(),
                claims.claims().stream().filter(claim -> claim.stage() != BayClaim.Stage.ENDED).count());
    }

    /**
     * While held, an in-session claim renews T's recorded flight tickets on both ends, and from CONSUME on each admissible
     * end's census columns (W185-R2, R150), without a load; a healthy restart group renews the existing tickets on T's
     * census columns of both ends (R1). REPAIR adds no ticket.
     */
    private void renewTickets() {
        var journal = RocketTransferSavedData.get(server);
        for (BayClaim claim : claims.claims()) {
            RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
            if (record == null) { continue; }
            if (claim.origin() != BayClaim.Origin.CHECKPOINT) {
                if (claim.group().isPresent() && claim.hold() != BayClaim.Hold.REPAIR && claim.stage() != BayClaim.Stage.ENDED
                        && claim.stage() != BayClaim.Stage.PUBLISH) { resume.renew(claim); } // W170-R23: checked T first
                continue;
            }
            var source = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
            if (source != null) { RocketTransferEntities.refreshOriginTicket(source, record.sourceSnapshot(), record.transferId()); }
            if (BaySourceProof.ticketsCensus(claim)) { BayCensusColumns.renewInSession(server, record); }
            var destination = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
            if (destination != null) {
                RocketTransferEntities.refreshDestinationTickets(destination, record.destinationSnapshot(), record.transferId());
            }
        }
    }

    private BayStepQueue.Result step(UUID logical) {
        BayClaim claim = claims.get(logical).orElse(null);
        if (claim == null || shuttingDown()) { return BayStepQueue.Result.IDLE; }
        if (claim.stage() == BayClaim.Stage.ENDED) { audit(claim); return BayStepQueue.Result.IDLE; } // D7 ends it in a completion
        BayStepQueue.Result result = switch (claim.stage()) {
            case GROUP -> groupStep(claim);
            case BAYS -> bayStep(claim);
            case OUTCOMES -> outcomeStep(claim);
            case PUBLISH -> publication.publish(claim);
            case RESUME -> resume.resume(claim);
            case CONSUME -> consumption.consume(claim);
            case PROVE -> consumption.prove(claim, proof);
            case RETIRE -> proof.retire(claim);
            case ENDED -> BayStepQueue.Result.IDLE;
        };
        audit(claim);
        return result;
    }

    /** D0's group checks, one per step: checked T, the live source, the neutral wrapper, availability, F5, then B14. */
    private BayStepQueue.Result groupStep(BayClaim claim) {
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        if (record == null || record.phase() != RocketTransferPhase.PREPARED || !record.logicalRocketId().equals(claim.logical())) {
            return reject(claim, "SOURCE_UNAVAILABLE", false);
        }
        switch (claim.groupStep()) {
            case 0 -> {
                var guard = journal.logicalGuard(claim.logical()).orElse(null);
                if (!journal.actionable(claim.logical()) || guard == null || !record.transferId().equals(guard.transfer())
                        || guard.recordDigest() == null) {
                    return BayStepQueue.Result.NEXT_TICK; // checked T: wait for the exact acknowledgement
                }
            }
            case 1 -> {
                if (BayDeploymentChecks.liveSource(server, record, claim.source().orElseThrow()) == null) {
                    return reject(claim, "SOURCE_UNAVAILABLE", false); // the exit then hands T to ordinary recovery
                }
            }
            case 2 -> {
                RocketEntity source = BayDeploymentChecks.liveSource(server, record, claim.source().orElseThrow());
                if (source == null) { return reject(claim, "SOURCE_UNAVAILABLE", false); }
                if (!RocketEscrowGate.bayNeutral(source, record.transferId())) { return reject(claim, "BAY_SOURCE_PROTECTED", true); }
            }
            case 3 -> {
                if (session.unavailable()) { return reject(claim, "BAY_RECEIPTS_UNAVAILABLE", true); }
                if (!registryOperational(claim.logical())) { return reject(claim, "REGISTRY_BLOCKED", true); }
                if (!RocketTransactionSavedData.get(server).operational()) { return reject(claim, "ASSEMBLY_JOURNAL_UNPROVED", true); }
            }
            case 4 -> {
                var refusal = BayDeploymentChecks.orbitRefusal(plannedBody(record));
                if (refusal.isPresent()) { return reject(claim, refusal.get(), true); }
            }
            default -> {
                return assemblyJournal(claim, record);
            }
        }
        claim.groupPassed(GROUP_STEPS);
        return BayStepQueue.Result.AGAIN;
    }

    /** B14 under ADR-074 item 11: request on the first UNPROVED or WRITE_REQUIRED, wait on WRITE_PENDING, no time limit. */
    private BayStepQueue.Result assemblyJournal(BayClaim claim, RocketTransferRecord record) {
        var transactions = RocketTransactionSavedData.get(server);
        var status = transactions.absenceStatus(claim.logical(), record.sourceSnapshot().snapshotId(), record.sourceEntityId());
        switch (BayJournalCheckpoint.decide(BayJournalCheckpoint.Answer.valueOf(status.name()), claim.checkpointRequested())) {
            case CONTINUE -> { claim.groupPassed(GROUP_STEPS); return BayStepQueue.Result.AGAIN; }
            case REJECT_PENDING -> { return reject(claim, "ASSEMBLY_JOURNAL_PENDING", true); }
            case REJECT_UNPROVED -> { return reject(claim, "ASSEMBLY_JOURNAL_UNPROVED", true); }
            case WAIT -> { return BayStepQueue.Result.NEXT_TICK; }
            case REQUEST -> {
                var request = checkpointRefusedForTests.contains(claim.logical())
                        ? RocketTransactionSavedData.CheckpointRequest.REFUSED : transactions.requestCheckpoint(server);
                if (request == RocketTransactionSavedData.CheckpointRequest.REFUSED) {
                    return reject(claim, "ASSEMBLY_JOURNAL_UNPROVED", true); // the writer is unavailable
                }
                claim.markCheckpointRequested();
                return BayStepQueue.Result.NEXT_TICK;
            }
        }
        throw new IllegalStateException("Unreachable B14 action");
    }

    /** One bay per step, in position order: its package and owners, X, its receipt, replay-first validation, then D1. */
    private BayStepQueue.Result bayStep(BayClaim claim) {
        BayMember member = claim.nextCandidate().orElse(null);
        if (member == null) { return BayStepQueue.Result.AGAIN; }
        // W170-R44: a claim held for repair writes no further INTENT; each remaining candidate holds without a write.
        if (claim.hold() == BayClaim.Hold.REPAIR) { member.hold(claim.reason()); return BayStepQueue.Result.AGAIN; }
        RocketTransferRecord record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        var bay = record == null ? null : checks.bay(record.sourceSnapshot(), member.bay()).orElse(null);
        if (bay == null) { refuse(claim, member, "INVALID_PACKAGE"); return BayStepQueue.Result.AGAIN; }
        var registry = SatelliteMissionSavedData.get(server);
        var refusal = BayDeploymentChecks.bayRefusal(bay, record);
        if (refusal.isPresent()) { refuse(claim, member, refusal.get()); return BayStepQueue.Result.AGAIN; }
        var identity = bay.identity().orElseThrow();
        String signature = RocketNativeResources.signature(bay.pkg());
        BayReceipt previous = session.find(identity.satelliteId()).orElse(null);
        if (previous != null) {
            if (previous.blocking() || previous.released() || !previous.packageSig().equals(signature)) {
                refuse(claim, member, "BAY_CONFLICT"); return BayStepQueue.Result.AGAIN;
            }
            if (!session.acknowledged(previous.op())) { refuse(claim, member, "RECEIPT_PENDING"); return BayStepQueue.Result.AGAIN; }
        }
        ResourceLocation body = plannedBody(record);
        var service = SatelliteRuntime.launchService().orElse(null);
        var validation = service == null ? null : service.validate(server, identity.ownerId(), identity, body);
        if (validation == null || validation.request().isEmpty()) {
            refuse(claim, member, validation == null ? "SERVER_ERROR" : validation.result().code().name());
            return BayStepQueue.Result.AGAIN;
        }
        long now = server.getTickCount();
        boolean preexisting = registry.satellite(identity.satelliteId()).isPresent(); // sampled again at every D1 (W27A section 3)
        BayReceipt intent = previous == null
                ? new BayReceipt(UUID.randomUUID(), session.nextSequence(), claim.logical(), member.bay(), identity.satelliteId(),
                        identity.ownerId(), identity.definitionId().toString(), identity.kind(), body.toString(), signature,
                        record.transferId(), record.sourceSnapshot().contentHash(), null, record.destinationSnapshot().contentHash(),
                        null, BayReceipt.Phase.INTENT, "NONE", preexisting, false, 0, now, now, 0)
                : new BayReceipt(previous.op(), previous.seq(), claim.logical(), member.bay(), previous.satellite(), previous.owner(),
                        previous.definition(), previous.kind(), body.toString(), previous.packageSig(), record.transferId(),
                        record.sourceSnapshot().contentHash(), null, record.destinationSnapshot().contentHash(), null,
                        BayReceipt.Phase.INTENT, "NONE", preexisting, false, previous.registryEpoch(),
                        previous.createdTick(), now, Math.min(BayReceipt.MAX_ATTEMPTS, previous.attempts() + 1));
        if (cuts != null) { cuts.writing(claim, intent); }
        BayReceiptStore.Admission admission = session.put(intent, completion(claim, member, intent, true));
        if (admission.queued()) {
            member.admitted(intent);
            session.rejections().remove(identity.satelliteId());
        } else {
            refuse(claim, member, BayDeploymentReasons.admission(admission));
        }
        return BayStepQueue.Result.AGAIN;
    }

    /**
     * The outcome steps: D2 or a restart row per INTENT member; D3 once no member waits for its D1 or D2; D4's freeze once
     * every member is resolved or proved; else the exit once every member is resolved.
     */
    private BayStepQueue.Result outcomeStep(BayClaim claim) {
        BayMember member = claim.members().stream().filter(candidate -> candidate.state() == BayMember.State.INTENT)
                .findFirst().orElse(null);
        if (member != null) { return outcome(claim, member); }
        if (claim.members().stream().anyMatch(candidate -> candidate.state() == BayMember.State.REGISTERING)) {
            boolean d1 = claim.members().stream().anyMatch(candidate -> candidate.state() == BayMember.State.INTENT_WRITE);
            return d1 ? BayStepQueue.Result.IDLE : registration.prove(claim); // the INTENT completion offers the claim
        }
        if (BayGroupPublication.ready(claim)) { return publication.freeze(claim); }
        if (!claim.finished()) { return BayStepQueue.Result.IDLE; } // completions offer the claim again
        var store = session.receipts().stream().anyMatch(receipt -> receipt.rocket().equals(claim.logical()) && receipt.pending());
        if (store) { return BayStepQueue.Result.NEXT_TICK; } // RELEASED_TO_ORDINARY only once no blocking receipt names L
        claim.end(claim.origin() == BayClaim.Origin.CHECKPOINT && sourceCurrent(claim)
                ? BayClaim.Handoff.READY_SOURCE : BayClaim.Handoff.ORDINARY);
        return BayStepQueue.Result.IDLE;
    }

    private BayStepQueue.Result outcome(BayClaim claim, BayMember member) {
        BayReceipt intent = member.receipt().orElseThrow();
        long now = server.getTickCount();
        BayReceipt next;
        if (claim.origin() == BayClaim.Origin.CHECKPOINT) {
            // W170-R4: a claim held for repair cannot proceed this session, so D2 registers nothing more under it.
            if (claim.hold() == BayClaim.Hold.REPAIR) { member.hold(claim.reason()); return BayStepQueue.Result.AGAIN; }
            var refusal = registryOperational(claim.logical()) ? registration.register(claim, member, intent) : java.util.Optional.of("REGISTRY_BLOCKED");
            if (refusal.isEmpty()) { return BayStepQueue.Result.AGAIN; } // REGISTERING: D3 follows once every member passed D2
            next = intent.withPhase(BayReceipt.Phase.REJECTED, refusal.get(), now);
        } else {
            switch (BayRestartRows.row(server, intent, registryOperational(claim.logical()))) {
                case REJECT_INTERRUPTED -> next = intent.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", now);
                case REPAIR_SIGNATURE -> next = intent.withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", now);
                case PROMOTE -> { registration.promote(member, intent); return BayStepQueue.Result.AGAIN; }
                case HOLD_BLOCKED -> { member.hold("REGISTRY_BLOCKED"); claim.repair("REGISTRY_BLOCKED"); return BayStepQueue.Result.AGAIN; }
                default -> {
                    member.hold("TRANSFER_AUTHORITY_CONFLICT"); claim.repair("TRANSFER_AUTHORITY_CONFLICT");
                    return BayStepQueue.Result.AGAIN;
                }
            }
        }
        if (cuts != null) { cuts.writing(claim, next); }
        BayReceiptStore.Admission admission = session.put(next, completion(claim, member, intent, false));
        if (!admission.queued()) { return BayStepQueue.Result.NEXT_TICK; } // retryable: the TRANSIT wait has no time limit
        member.outcomeQueued(next);
        if (next.phase() == BayReceipt.Phase.REPAIR) { claim.repair(next.reason()); }
        return BayStepQueue.Result.AGAIN;
    }

    /** Completions only mark state and enqueue; they never write. UNKNOWN waits while the writer retries the exact bytes. */
    private Consumer<BayReceiptWriter.Outcome> completion(BayClaim claim, BayMember member, BayReceipt intent, boolean d1) {
        return outcome -> {
            if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; }
            boolean committed = outcome == BayReceiptWriter.Outcome.COMMITTED && session.acknowledged(intent.op());
            if (d1 && member.state() == BayMember.State.INTENT_WRITE) {
                if (committed) { member.intentAcknowledged(); }
                else if (outcome != BayReceiptWriter.Outcome.COMMITTED) { refuse(claim, member, "RECEIPT_WRITE_FAILED"); }
            } else if (!d1 && (member.state() == BayMember.State.REJECT_WRITE || member.state() == BayMember.State.REPAIR_WRITE)) {
                if (committed) { member.outcomeAcknowledged(); }
                else if (outcome != BayReceiptWriter.Outcome.COMMITTED) { member.outcomeFailed(intent); }
            }
            if (!stopping) { queue.offer(claim.logical()); }
        };
    }

    private BayStepQueue.Result reject(BayClaim claim, String reason, boolean remember) {
        claim.rejectAll(reason);
        if (remember) { claim.members().forEach(member -> remember(claim, member)); }
        return BayStepQueue.Result.AGAIN;
    }

    private void refuse(BayClaim claim, BayMember member, String reason) {
        member.refuse(reason);
        remember(claim, member);
    }

    /** The runtime rejection map keeps each refused bay's reason for its menu status. */
    private void remember(BayClaim claim, BayMember member) {
        if (member.state() == BayMember.State.REFUSED && member.satellite().isPresent()) {
            session.rejections().put(member.satellite().get(), member.reason(), claim.logical(), server.getTickCount());
        }
    }

    /** READY_SOURCE needs this session's live source, still loaded, current and at altitude in TRANSIT. */
    private boolean sourceCurrent(BayClaim claim) {
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        return record != null && claim.source().isPresent()
                && BayDeploymentChecks.liveSource(server, record, claim.source().get()) != null;
    }

    @Nullable
    static ResourceLocation plannedBody(@Nullable RocketTransferRecord record) {
        return record == null ? null : record.sourceFlightData().plan().map(plan -> plan.sourceBody()).orElse(null);
    }

    /** One {@code ARCE_BAY_CLAIM} line per claim transition: INFO, or WARN while it holds for offline repair. */
    private void audit(BayClaim claim) {
        String state = claim.stage() == BayClaim.Stage.ENDED
                ? (claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE ? "READY_SOURCE" : "RELEASED_TO_ORDINARY")
                : claim.hold().name();
        String line = state + ' ' + claim.reason();
        if (line.equals(audited.put(claim.logical(), line))) { return; }
        if (claim.hold() == BayClaim.Hold.REPAIR && claim.stage() != BayClaim.Stage.ENDED) {
            LOGGER.warn("ARCE_BAY_CLAIM transfer={} logical={} state={} reason={}", claim.transfer(), claim.logical(), state, claim.reason());
        } else {
            LOGGER.info("ARCE_BAY_CLAIM transfer={} logical={} state={} reason={}", claim.transfer(), claim.logical(), state, claim.reason());
        }
    }

    private boolean shuttingDown() { return server.isStopped() || !server.isRunning(); }

    /** The ended (or forgotten) claim's RAM proof state in every step class. */
    private void forgetState(BayClaim claim) {
        registration.forget(claim); publication.forget(claim.logical()); consumption.forget(claim.logical());
        proof.forget(claim.logical()); resume.forget(claim.logical());
    }

    /** The loaded registry, unless a gated seam blocks this coordinator's view of it for the session or for L. */
    private boolean registryOperational(UUID logical) {
        return !registryBlockedSession && !registryBlockedForTests.contains(logical)
                && SatelliteMissionSavedData.get(server).operational();
    }

    // ---- Gated test access (GameTests and the release-test harness); never reached in production. ----

    Optional<BayClaim> claimForTests(UUID logical) { return claims.get(logical); }

    BayGroupPublication.Prepared preparedForTests(UUID logical) {
        EscrowTestEndpoints.requireEnabled(server); return publication.prepared(logical).orElseThrow();
    }
    BayDeploymentSession sessionForTests() { return session; }

    /** The restart rows for receipts already in the store, as ServerStartingEvent builds them; they run from the next step. */
    List<BayClaim> restartForTests(List<BayReceipt> receipts) {
        EscrowTestEndpoints.requireEnabled(server);
        return beginRestart(receipts);
    }

    /** From now on this coordinator sees the registry as blocked for L only (D0's group check and the restart row). */
    void blockRegistryForTests(UUID logical) {
        EscrowTestEndpoints.requireEnabled(server);
        registryBlockedForTests.add(logical);
    }

    /** L's B14 request is answered REFUSED, as an unavailable transaction writer answers it. */
    void refuseCheckpointForTests(UUID logical) {
        EscrowTestEndpoints.requireEnabled(server);
        checkpointRefusedForTests.add(logical);
    }

    /** Cleanup of a claim that this slice holds until offline repair; it reports no handoff. */
    void forgetForTests(UUID logical) {
        EscrowTestEndpoints.requireEnabled(server);
        claims.get(logical).ifPresent(this::forgetState);
        claims.remove(logical); audited.remove(logical);
        registryBlockedForTests.remove(logical); checkpointRefusedForTests.remove(logical);
    }
}
