package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteLaunchService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * W170's D2 and D3 (W27A-DESIGN sections 3 and 4; ADR-074 item 11). D2 revalidates and registers X under the session's
 * permit for (op, X), replay first; D3 requests one registry checkpoint with the claim's watches and waits for the first
 * published completion with s >= m. FAILED, UNKNOWN or a refused request counts one failure and asks again after 200
 * ticks; from the third failure the claim reason is REGISTRY_UNPROVED. Nothing rejects after a RAM registration.
 * Watches, tickets and proving epochs are RAM of this session only. Server thread only.
 */
final class BayRegistrationSteps {
    /** W27A section 4's D3 pacing; the registry writer's own backoff after a non-COMMITTED outcome is also 200 ticks. */
    static final int RETRY_TICKS = 200, UNPROVED_FAILURES = 3;
    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final BayDeploymentChecks checks;
    private final Map<UUID, SatelliteRegistryProof.Watch> watches = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();
    private final Map<UUID, SatelliteRegistryProof.Checkpoint> tickets = new HashMap<>();

    BayRegistrationSteps(MinecraftServer server, BayDeploymentSession session, BayDeploymentChecks checks) {
        this.server = server; this.session = session; this.checks = checks;
    }

    /**
     * D2 for one acknowledged INTENT: empty when X is registered or replayed with an equal identity (the member is then
     * REGISTERING with its watch); otherwise the refusal's code, which becomes REJECTED (B9). An F2 mismatch of a fresh
     * launch result holds the claim for repair; the RAM registration stays.
     */
    Optional<String> register(BayClaim claim, BayMember member, BayReceipt intent) {
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        var bay = record == null ? null : checks.bay(record.sourceSnapshot(), intent.bay()).orElse(null);
        SatelliteIdentity identity = bay == null ? null : bay.identity().orElse(null);
        if (identity == null || !identity.satelliteId().equals(intent.satellite())) { return Optional.of("INVALID_PACKAGE"); }
        SatelliteLaunchService service = SatelliteRuntime.launchService().orElse(null);
        if (service == null) { return Optional.of("SERVER_ERROR"); }
        var registry = SatelliteMissionSavedData.get(server);
        if (!registry.operational()) { return Optional.of("REGISTRY_BLOCKED"); }
        ResourceLocation body = ResourceLocation.tryParse(intent.orbit());
        var branch = new SatelliteLaunchService.Branch[1];
        SatelliteOperationResult result = registry.registerWithPermit(session.permit(intent.op(), intent.satellite()), () -> {
            var validation = service.validate(server, identity.ownerId(), identity, body);
            if (validation.request().isEmpty()) { return validation.result(); }
            branch[0] = validation.request().get().branch();
            return service.register(server, validation.request().get(), server.overworld().getGameTime());
        });
        SatelliteState registered = registry.satellite(intent.satellite()).orElse(null);
        if (branch[0] == null || !result.success() || registered == null || !equal(registered, intent)) {
            return Optional.of(result.success() ? "REGISTRY_SIGNATURE_CONFLICT" : result.code().name());
        }
        if (branch[0] == SatelliteLaunchService.Branch.FRESH && !freshResultMatches(result, identity, body)) {
            member.registered(); watches.put(intent.op(), registry.watch(intent.satellite()));
            claim.repair("REGISTRY_SIGNATURE_CONFLICT");
            return Optional.empty();
        }
        member.registered();
        watches.put(intent.op(), registry.watch(intent.satellite()));
        return Optional.empty();
    }

    /** W170's restart promotion: an INTENT whose X is present with an equal identity is watched as an idempotent D2. */
    void promote(BayMember member, BayReceipt intent) {
        member.registered();
        watches.put(intent.op(), SatelliteMissionSavedData.get(server).watch(intent.satellite()));
    }

    /**
     * D3 for the claim's REGISTERING members: one checkpoint request with all their watches, then its answer. AGAIN once
     * every watch is proved, NEXT_TICK while waiting or after a failure, IDLE for a claim held for repair.
     */
    BayStepQueue.Result prove(BayClaim claim) {
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        var registering = claim.members().stream().filter(m -> m.state() == BayMember.State.REGISTERING).toList();
        if (registering.isEmpty()) { return BayStepQueue.Result.AGAIN; }
        var registry = SatelliteMissionSavedData.get(server);
        if (!registry.operational()) { claim.waiting("REGISTRY_BLOCKED"); return BayStepQueue.Result.NEXT_TICK; }
        var ticket = tickets.get(claim.logical());
        if (ticket == null) {
            if (server.getTickCount() < claim.registryRetryAt()) { return BayStepQueue.Result.NEXT_TICK; }
            var claimWatches = new ArrayList<SatelliteRegistryProof.Watch>();
            for (BayMember member : registering) { claimWatches.add(watches.get(op(member))); }
            ticket = registry.requestCheckpoint(server, claimWatches);
            tickets.put(claim.logical(), ticket);
        }
        if (ticket.discarded()) { return failed(claim); }
        var answer = ticket.answer().orElse(null);
        if (answer == null) { return BayStepQueue.Result.NEXT_TICK; }
        tickets.remove(claim.logical());
        if (answer.outcome() != SatelliteRegistryProof.Outcome.COMMITTED) { return failed(claim); }
        for (BayMember member : registering) {
            if (!answer.proves(watches.get(op(member)))) {
                claim.repair("REGISTRY_SIGNATURE_CONFLICT"); // decommission was fenced: an unproved identity is a conflict
                return BayStepQueue.Result.IDLE;
            }
        }
        long epoch = answer.acknowledgement().orElseThrow().epoch();
        for (BayMember member : registering) { member.proved(); epochs.put(op(member), epoch); watches.remove(op(member)); }
        claim.waiting(null);
        return BayStepQueue.Result.AGAIN;
    }

    private BayStepQueue.Result failed(BayClaim claim) {
        tickets.remove(claim.logical());
        claim.registryFailed(server.getTickCount() + RETRY_TICKS);
        if (claim.registryFailures() >= UNPROVED_FAILURES) { claim.waiting("REGISTRY_UNPROVED"); }
        return BayStepQueue.Result.NEXT_TICK;
    }

    /** The proving completion's carried epoch for a member's receipt (ADR-074 item 11); 0 before it is known. */
    long epoch(UUID op) { return epochs.getOrDefault(op, 0L); }

    /** A claim's end, or its gated forget: drop its RAM proof state. */
    void forget(BayClaim claim) {
        tickets.remove(claim.logical());
        claim.members().forEach(member -> member.receipt().ifPresent(receipt -> { watches.remove(receipt.op()); epochs.remove(receipt.op()); }));
    }

    /** Stopping: a waiting D3 is discarded; its ticket is never answered (W129-DESIGN section 4.6). */
    void clear() { watches.clear(); epochs.clear(); tickets.clear(); }

    private static UUID op(BayMember member) { return member.receipt().orElseThrow().op(); }

    private static boolean equal(SatelliteState state, BayReceipt intent) {
        return state.ownerId().equals(intent.owner()) && state.kind() == intent.kind() && state.definitionId().toString().equals(intent.definition());
    }

    /** F2: a fresh DATA launch returns its atomic initial mission for the receipt's body; a fresh non-data its fixed orbit. */
    private static boolean freshResultMatches(SatelliteOperationResult result, SatelliteIdentity identity, ResourceLocation body) {
        if (identity.kind() != SatelliteKind.DATA) {
            return result.satellite().flatMap(SatelliteState::orbitBody).filter(body::equals).isPresent();
        }
        var mission = result.mission().orElse(null);
        return mission != null && mission.missionId().equals(SatelliteManager.initialMissionId(identity.satelliteId()))
                && mission.satelliteId().equals(identity.satelliteId()) && mission.ownerId().equals(identity.ownerId())
                && mission.definitionId().equals(identity.definitionId()) && mission.targetBodyId().equals(body)
                && result.code() != SatelliteOperationCode.IDEMPOTENT;
    }
}
