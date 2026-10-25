package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** I6 entry decisions, separate from transfer dispatch and its lifecycle. */
final class RocketI6Entry {
    private final RocketTransferRecoveryService recovery;
    private final RocketLandedDisassemblies landed;
    private final Predicate<RocketEntity> sealedClaim;
    private final BiFunction<MinecraftServer, RocketTransferSavedData, RocketLandedDisassemblies.Port> ports;

    RocketI6Entry(RocketTransferRecoveryService recovery, RocketLandedDisassemblies landed,
            Predicate<RocketEntity> sealedClaim,
            BiFunction<MinecraftServer, RocketTransferSavedData, RocketLandedDisassemblies.Port> ports) {
        this.recovery = recovery; this.landed = landed; this.sealedClaim = sealedClaim; this.ports = ports;
    }

    /** FM12a's internal hook; no terrain restoration or player-disassembly claim. */
    boolean releasePlain(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()
                || RocketReleaseAccess.fenced(rocket) || sealedClaim.test(rocket)) { return false; }
        var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
        var logical = rocket.assemblyTransactionId().orElseThrow(); var retirements = recovery.retirements();
        if (!journal.operational() || retirements.pending(logical) || landed.pending(logical)) { return false; }
        var record = journal.findByLogicalRocket(logical).orElse(null);
        if (record == null) { return !journal.resourceMutationUnproved(logical); }
        if (!RocketLandedDisassemblyRules.landedRecordAdmitted(record.schemaVersion(), record.nativeTransfer().isPresent())
                || !RocketTransferEntities.isReplaceableLandedAuthority(rocket, record) || !retirements.pendingRoom(logical)) { return false; }
        var proof = recovery.i6Port(server, journal, record, rocket);
        try (var held = RocketOrdinaryRetirement.hold(journal, logical)) {
            if (held.token() == null || !proof.requestCurrent() || !proof.escrowAllows(journal, record, held.token())) { return false; }
            var port = ports.apply(server, journal);
            retirements.begin(proof, journal, RocketOrdinaryRetirement.Site.BEFORE_RELEASE, record, rocket.getUUID(),
                    List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> {
                        if (port.bayAllowsRemoval(record)) { port.released(record, rocket.getUUID()); }
                        return true;
                    }));
        }
        return false;
    }

    RocketValidationCode begin(RocketEntity rocket, Supplier<Optional<RocketValidationCode>> preflight,
            RocketLandedDisassemblies.Request request) {
        if (RocketReleaseAccess.fenced(rocket)) { return RocketValidationCode.REGION_BUSY; }
        if (sealedClaim.test(rocket) || recovery.retirements().replacing(rocket.assemblyTransactionId().orElse(null))) {
            return RocketValidationCode.REGION_BUSY;
        }
        // Only the isolated historical GameTest adapter still exercises the original direct M1 contract.
        if (!request.retainedExecutor()) { return landed.begin(rocket, preflight, request, ports); }
        if (!(rocket.level() instanceof ServerLevel level)) { return RocketValidationCode.ENTITY_STATE_INVALID; }
        try {
            if (new SealedPlanAdmission(rocket.snapshot().orElseThrow()).refuseNew(rocket.getUUID(),
                    io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER::info) != null) {
                return RocketValidationCode.SCAN_BUDGET_EXCEEDED;
            }
        } catch (IllegalArgumentException invalid) { return RocketValidationCode.SCAN_BUDGET_EXCEEDED; }
        var journal = RocketTransferSavedData.get(level.getServer()); var logical = rocket.assemblyTransactionId().orElseThrow();
        var retirements = recovery.retirements();
        if (landed.pending(logical) || retirements.plainReleasePending(logical)) { return RocketValidationCode.DISASSEMBLY_PENDING; }
        var record = journal.findByLogicalRocket(logical).orElse(null); var port = ports.apply(level.getServer(), journal);
        var decision = landed.admission(rocket, record, journal, port, preflight, true);
        if (decision.admission() != RocketLandedDisassemblyRules.Admission.RELEASE) { return decision.code(); }
        if (!retirements.pendingRoom(logical) || !request.authorized()) { return RocketValidationCode.REGION_BUSY; }
        if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(level.getServer(), record)) {
            return RocketValidationCode.REGION_BUSY; // A new preparation demand and fresh consent are required.
        }
        var proof = recovery.i6Port(level.getServer(), journal, record, rocket);
        var handoff = new RocketLandedDisassemblies.Pending[1];
        var refusal = new RocketValidationCode[] {RocketValidationCode.REGION_BUSY};
        proof.handoff(request::authorized, (data, actual, own) -> {
            if (!request.authorized() || !request.restorable()) { return false; }
            var issue = preflight.get();
            if (issue.isPresent()) { refusal[0] = issue.orElseThrow(); return false; }
            handoff[0] = landed.prepareRelease(data, actual, rocket.getUUID(), request, port, own);
            return handoff[0] != null;
        }, port);
        try (var held = RocketOrdinaryRetirement.hold(journal, logical)) {
            if (held.token() == null || !proof.requestCurrent() || !proof.escrowAllows(journal, record, held.token())) {
                return RocketValidationCode.REGION_BUSY;
            }
            retirements.begin(proof, journal, RocketOrdinaryRetirement.Site.BEFORE_RELEASE, record, rocket.getUUID(),
                    List.of(), false, held, new RocketOrdinaryRetirement.Effects(
                            () -> handoff[0] != null && landed.removalAcknowledged(handoff[0]),
                            () -> { if (handoff[0] != null) { landed.removalDiscarded(handoff[0]); }
                                    else { request.finished(refusal[0], null); } }));
        }
        return RocketValidationCode.DISASSEMBLY_PENDING;
    }

    /** A cold-site click only loads the recorded origin; it cannot consume a destructive consent or create P. */
    RocketValidationCode prepare(RocketEntity rocket, BooleanSupplier authorized) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()
                || RocketReleaseAccess.fenced(rocket) || sealedClaim.test(rocket)) { return RocketValidationCode.REGION_BUSY; }
        var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
        var logical = rocket.assemblyTransactionId().orElseThrow(); var retirements = recovery.retirements();
        if (!journal.operational() || landed.pending(logical)) { return RocketValidationCode.REGION_BUSY; }
        if (retirements.plainReleasePending(logical)) { return RocketValidationCode.DISASSEMBLY_PENDING; }
        var record = journal.findByLogicalRocket(logical).orElse(null);
        if (record == null || RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)) { return RocketValidationCode.SUCCESS; }
        if (retirements.pending(logical) || !retirements.pendingRoom(logical)
                || !RocketLandedDisassemblyRules.landedRecordAdmitted(record.schemaVersion(), record.nativeTransfer().isPresent())
                || !RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)) { return RocketValidationCode.REGION_BUSY; }
        var proof = recovery.i6Port(server, journal, record, rocket); proof.prepareOnly(authorized);
        try (var held = RocketOrdinaryRetirement.hold(journal, logical)) {
            if (held.token() == null || !proof.requestCurrent() || !proof.escrowAllows(journal, record, held.token())) {
                return RocketValidationCode.REGION_BUSY;
            }
            retirements.begin(proof, journal, RocketOrdinaryRetirement.Site.BEFORE_RELEASE, record, rocket.getUUID(),
                    List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
        }
        return RocketValidationCode.DISASSEMBLY_PENDING;
    }

    /** The existing mutation interlock, including I6's pending owner. */
    boolean resourceMutationAllowed(RocketEntity rocket, Set<UUID> live, Set<UUID> settled) {
        if (RocketReleaseAccess.fenced(rocket)) { return false; }
        if (sealedClaim.test(rocket)) { return false; }
        if (!io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.resourcesIdle(rocket)) { return false; }
        if (landed.pending(rocket.assemblyTransactionId().orElse(null))) { return false; }
        if (recovery.retirements().replacing(rocket.assemblyTransactionId().orElse(null))
                || recovery.retirements().plainReleasePending(rocket.assemblyTransactionId().orElse(null))) { return false; }
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || rocket.flightData().orElseThrow().activeTransferId().isPresent()) {
            return false;
        }
        var journal = RocketTransferSavedData.get(level.getServer());
        if (journal.resourceMutationUnproved(rocket.assemblyTransactionId().orElseThrow())) { return false; }
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        if (record != null && record.nativeTransfer().isPresent()) {
            return io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.terminalSuccessor(rocket);
        }
        return RocketTransferService.resourceMutationAllowed(journal.operational(), record == null ? null : record.phase(),
                record != null && settled.contains(record.transferId()) && !live.contains(record.transferId()),
                record != null && RocketTransferEntities.isReplaceableLandedAuthority(rocket, record));
    }
}
