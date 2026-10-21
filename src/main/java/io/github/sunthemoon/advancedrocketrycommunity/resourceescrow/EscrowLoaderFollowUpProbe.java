package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/** R54 observations only: preserve the retired disk checkpoint, attempt actions, then stop before another L1. */
final class EscrowLoaderFollowUpProbe {
    static final String SWITCH = "advancedrocketrycommunity.escrowLoaderFollowUps";
    private EscrowLoaderFollowUpProbe() { }

    static void observe(MinecraftServer server, String cut, RocketEntity rocket, FluidFuelLoaderBlockEntity loader) {
        if (!Boolean.getBoolean(SWITCH)) { return; }
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(Boolean.getBoolean(EscrowTestEndpoints.HOOKS) && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH)
                && (EscrowLoaderNamedProbe.selected(cut) || EscrowLoaderFloorProbe.selected(cut) && !cut.endsWith("256")),
                "Follow-up requests require a selected named fixture and per-device gates");
        var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
        var coordinator = data.bind(server); var logical = rocket.assemblyTransactionId().orElseThrow();
        EscrowTags.require(coordinator.writerIdle() && coordinator.unresolvedIds().isEmpty() && runtime.fills(server).size() == 0,
                "Follow-up requests wait for complete first-fill retirement");
        EscrowTags.require(server.saveEverything(false, true, true), "Retired follow-up checkpoint save failed");
        var before = counts(server, logical); var root = data.ledger();
        var source = loader.saveWithFullMetadata(); var destination = EscrowNativeEnvelope.rocket(rocket);
        boolean allowed = RocketRuntime.resourceMutationAllowed(rocket);
        var journal = RocketTransferSavedData.get(server);
        var guard = journal.acknowledgedGuard(logical).orElseThrow();
        // Ask before a second fill can lock the rocket; otherwise that new lock would mask launch authority.
        var launch = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_LAUNCH cut={} code={} required_fuel={} retained_transfer={} mutation_allowed={}",
                cut, launch.code(), launch.requiredFuel(), guard.transfer() != null, allowed);
        EscrowTags.require(!launch.success() && source.equals(loader.saveWithFullMetadata())
                && destination.equals(EscrowNativeEnvelope.rocket(rocket)) && before.equals(counts(server, logical)),
                "Launch request changed the conserved fixture");
        runtime.loaderAdmissionForTests(server, device(loader), true);
        FluidFuelLoaderStatus attempt, result;
        try {
            attempt = loader.attempt();
            if (attempt == FluidFuelLoaderStatus.READY) {
                runtime.fills(server).visit();
                result = loader.lastAttempt();
                EscrowTags.require(result != null && result != FluidFuelLoaderStatus.READY, "Second fill was not visited");
            } else { result = attempt; }
        } finally { runtime.loaderAdmissionForTests(server, device(loader), false); }
        boolean admitted = result == FluidFuelLoaderStatus.PREPARING;
        var after = counts(server, logical); int delta = admitted ? 1 : 0;
        EscrowTags.require(after.next() == before.next() + delta && after.pending() == before.pending() + delta
                && after.unresolved().size() == before.unresolved().size() + delta && coordinator.writerIdle()
                && data.ledger() == root && runtime.fills(server).size() == 0
                && source.equals(loader.saveWithFullMetadata()) && destination.equals(EscrowNativeEnvelope.rocket(rocket)),
                "Admission observation changed resources, started L1, or mismatched its reservation");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_SECOND_FILL cut={} attempt={} result={} admitted={} next_before={} next_after={} pending_before={} pending_after={} durable_records={} resources_unchanged=true",
                cut, attempt, result, admitted, before.next(), after.next(), before.pending(), after.pending(), root.records().size());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FOLLOWUP_PAUSED cut={} hook=AFTER_REQUESTS_BEFORE_L1", cut);
        // The harness kills this process at the marker. No tick, autosave, or shutdown flush may commit the new queue.
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
        throw new IllegalStateException("Follow-up forced stop did not arrive within 30 seconds");
    }
}
