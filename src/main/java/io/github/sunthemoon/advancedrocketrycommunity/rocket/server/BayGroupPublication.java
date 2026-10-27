package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptWriter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * W170's D4 (W27A-DESIGN section 4): once every member of a claim is resolved or proved and at least one is proved, the
 * claim freezes one group for exact (L, T) and computes S' and T' once through the runtime's ADR-065 4.1.1 budget. Every
 * REGISTERED receipt of the group then publishes in one receipt generation with the group's four hashes and its proving
 * epoch; the exact acknowledgement lifts the decommission fences. A definite failure restores INTENT and publishes again;
 * UNKNOWN waits for the writer's exact retry. Server thread only; RAM of this session only.
 */
final class BayGroupPublication {
    /** T and its consumed T' for one frozen group, kept for D5 in the same session. */
    record Prepared(RocketTransferRecord before, RocketTransferRecord after) { }

    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final BayRegistrationSteps registration;
    private final Consumer<UUID> offer;
    private final Map<UUID, BayConsumedSnapshots.Job> jobs = new HashMap<>();
    private final Map<UUID, Prepared> prepared = new HashMap<>();

    BayGroupPublication(MinecraftServer server, BayDeploymentSession session, BayRegistrationSteps registration, Consumer<UUID> offer) {
        this.server = server; this.session = session; this.registration = registration; this.offer = offer;
    }

    /** Whether the claim is ready to freeze: every member resolved or proved, and at least one proved. */
    static boolean ready(BayClaim claim) {
        return claim.stage() == BayClaim.Stage.OUTCOMES && claim.group().isEmpty()
                && claim.members().stream().allMatch(m -> m.resolved() || m.state() == BayMember.State.PROVED)
                && claim.members().stream().anyMatch(m -> m.state() == BayMember.State.PROVED);
    }

    /** D4's freeze: checked T, then S' and D' through the budget (possibly over several ticks), then the frozen group. */
    BayStepQueue.Result freeze(BayClaim claim) {
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        var proved = claim.members().stream().filter(m -> m.state() == BayMember.State.PROVED).toList();
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        BayReceipt first = proved.get(0).receipt().orElseThrow();
        if (record == null || record.phase() != RocketTransferPhase.PREPARED || !record.logicalRocketId().equals(claim.logical())
                || !record.ownerId().equals(first.owner()) || record.nativeTransfer().isPresent()
                || proved.stream().map(m -> m.receipt().orElseThrow()).anyMatch(r -> !r.srcBefore().equals(record.sourceSnapshot().contentHash())
                        || !r.dstBefore().equals(record.destinationSnapshot().contentHash()) || !r.transfer().equals(record.transferId()))) {
            jobs.remove(claim.logical());
            claim.repair("TRANSFER_AUTHORITY_CONFLICT");
            return BayStepQueue.Result.IDLE;
        }
        var job = jobs.get(claim.logical());
        if (job == null) {
            job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(), consumed(proved)).orElse(null);
            if (job == null) { claim.repair("CONSUMPTION_MISMATCH"); return BayStepQueue.Result.IDLE; }
            jobs.put(claim.logical(), job);
        }
        if (!job.step(RocketRuntime.cargoPreparations(server).budget(), server.getTickCount())) { return BayStepQueue.Result.NEXT_TICK; }
        jobs.remove(claim.logical());
        var result = job.result().orElseThrow();
        RocketTransferRecord consumed;
        try { consumed = record.withConsumedSnapshots(result.source(), result.destination()); }
        catch (RuntimeException invalid) { claim.repair("CONSUMPTION_MISMATCH"); return BayStepQueue.Result.IDLE; }
        claim.freeze(new BayGroup(claim.logical(), claim.transfer(), proved, record.sourceSnapshot().contentHash(),
                record.destinationSnapshot().contentHash(), result.source().contentHash(), result.destination().contentHash()));
        prepared.put(claim.logical(), new Prepared(record, consumed));
        return BayStepQueue.Result.AGAIN;
    }

    /** D4's publication in one generation; once acknowledged the claim consumes (in session) or resumes (restart). */
    BayStepQueue.Result publish(BayClaim claim) {
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        BayGroup group = claim.group().orElseThrow();
        if (group.members().stream().allMatch(m -> m.state() == BayMember.State.REGISTERED)) {
            claim.advance(claim.source().isPresent() ? BayClaim.Stage.CONSUME : BayClaim.Stage.RESUME);
            return BayStepQueue.Result.AGAIN;
        }
        if (group.members().stream().anyMatch(m -> m.state() == BayMember.State.REGISTERED_WRITE)) { return BayStepQueue.Result.IDLE; }
        long now = server.getTickCount();
        var receipts = new ArrayList<BayReceipt>();
        for (BayMember member : group.members()) {
            BayReceipt intent = member.receipt().orElseThrow();
            receipts.add(new BayReceipt(intent.op(), intent.seq(), intent.rocket(), intent.bay(), intent.satellite(), intent.owner(),
                    intent.definition(), intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), group.srcBefore(),
                    group.srcAfter(), group.dstBefore(), group.dstAfter(), BayReceipt.Phase.REGISTERED, "NONE", intent.preexisting(),
                    false, registration.epoch(intent.op()), intent.createdTick(), now, intent.attempts()));
        }
        var admission = session.putGroup(receipts, outcome -> completed(claim, group, outcome));
        if (!admission.queued()) { return BayStepQueue.Result.NEXT_TICK; } // BUSY or unavailable: no time limit
        for (int i = 0; i < receipts.size(); i++) { group.members().get(i).groupQueued(receipts.get(i)); }
        return BayStepQueue.Result.IDLE;
    }

    /** Completions only mark state and offer the claim; UNKNOWN waits for the writer's exact-candidate retry. */
    private void completed(BayClaim claim, BayGroup group, BayReceiptWriter.Outcome outcome) {
        if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; }
        boolean committed = outcome == BayReceiptWriter.Outcome.COMMITTED
                && group.members().stream().allMatch(m -> session.acknowledged(m.receipt().orElseThrow().op()));
        for (BayMember member : group.members()) {
            if (member.state() != BayMember.State.REGISTERED_WRITE) { continue; }
            if (committed) { member.groupAcknowledged(); }
            else if (outcome != BayReceiptWriter.Outcome.COMMITTED) {
                member.groupFailed(session.current(member.receipt().orElseThrow().op()).orElseThrow());
            }
        }
        offer.accept(claim.logical());
    }

    /** T and T' of an in-session group, for D5; empty after a restart, when the resume computes them again. */
    Optional<Prepared> prepared(UUID logical) { return Optional.ofNullable(prepared.get(logical)); }

    void forget(UUID logical) { jobs.remove(logical); prepared.remove(logical); }
    void clear() { jobs.clear(); prepared.clear(); }

    /** The group's consumed bays, each to its receipt, in bay order. */
    static Map<RocketPosition, BayReceipt> consumed(List<BayMember> members) {
        var consumed = new LinkedHashMap<RocketPosition, BayReceipt>();
        members.forEach(member -> consumed.put(member.bay(), member.receipt().orElseThrow()));
        return consumed;
    }
}
