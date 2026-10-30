package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/** W211's gated loader E3 observer; fixture/fault mechanics are existing, unchanged helpers. */
final class EscrowLoaderS6Probe {
    static final String E3 = "LF_S6_E3_CACHE_CLOSE";
    private final MinecraftServer server;
    private final boolean recovery;
    private final EscrowLoaderCutProbe fixture;
    private final EscrowLoaderS6Recovery observer;
    private final EscrowLoaderS6UnloadedProbe unloaded;
    private final EscrowLoaderS6R2Probe stale;
    private final EscrowLoaderS6ShutdownProbe shutdown;
    private final EscrowLoaderS6GuardProbe guard;
    private final EscrowLoaderS6SettlementProbe settlement;
    private final EscrowLoaderS6ChainProbe chain;
    private final EscrowLoaderS6FlightProbe flight;
    private boolean injected;

    static boolean selected(boolean hooks, String name) {
        return hooks && (E3.equals(name) || EscrowLoaderS6UnloadedProbe.CASE.equals(name) || EscrowLoaderS6R2Probe.selected(hooks, name)
                || EscrowLoaderS6ShutdownProbe.selected(hooks, name, System.getProperty(EscrowLoaderS6ShutdownProbe.PROPERTY))
                || EscrowLoaderS6GuardProbe.selected(hooks, name) || EscrowLoaderS6SettlementProbe.selected(hooks, name)
                || EscrowLoaderS6ChainProbe.selected(hooks, name) || EscrowLoaderS6FlightProbe.selected(hooks, name));
    }

    EscrowLoaderS6Probe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.recovery = recovery;
        unloaded = EscrowLoaderS6UnloadedProbe.CASE.equals(System.getProperty("advancedrocketrycommunity.escrowCase"))
                ? new EscrowLoaderS6UnloadedProbe(server, recovery) : null;
        String cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        flight = EscrowLoaderS6FlightProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6FlightProbe(server, cut, recovery) : null;
        chain = EscrowLoaderS6ChainProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6ChainProbe(server, cut, recovery) : null;
        settlement = EscrowLoaderS6SettlementProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6SettlementProbe(server, cut, recovery) : null;
        guard = EscrowLoaderS6GuardProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6GuardProbe(server, cut) : null;
        stale = EscrowLoaderS6R2Probe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                ? new EscrowLoaderS6R2Probe(server, cut, recovery) : null;
        shutdown = EscrowLoaderS6ShutdownProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut,
                System.getProperty(EscrowLoaderS6ShutdownProbe.PROPERTY)) ? new EscrowLoaderS6ShutdownProbe(server, recovery) : null;
        // K7 supplies the unchanged real-loader baseline and per-device gate, not S6 recovery assertions.
        // Its success pause is not forwarded: this cut waits for the first propagated failed proof.
        fixture = unloaded == null && stale == null && shutdown == null && guard == null && settlement == null && chain == null && flight == null ? new EscrowLoaderCutProbe(server, "LF_M_K7", recovery) : null;
        observer = unloaded == null && stale == null && shutdown == null && guard == null && settlement == null && chain == null && flight == null && recovery ? new EscrowLoaderS6Recovery(server) : null;
    }

    void startTick() { if (chain != null) { chain.startTick(); } else if (settlement != null) { settlement.startTick(); } else if (shutdown != null) { shutdown.startTick(); } else if (fixture != null) { fixture.startTick(); } }

    void checkpoint(String name) {
        if (flight != null) { flight.checkpoint(name); return; }
        if (chain != null) { chain.checkpoint(name); return; }
        if (settlement != null) { settlement.checkpoint(name); return; }
        if (guard != null) { return; }
        if (shutdown != null) { shutdown.checkpoint(name); return; }
        if (stale != null) { stale.checkpoint(name); return; }
        if (unloaded != null) { unloaded.checkpoint(name); return; }
        if (!recovery && !injected && name.equals("C4_SOURCE_SELECTED")) {
            injected = true;
            int size = EscrowCargoContentionProbe.injectCacheClose(true,
                    server.overworld().getChunkSource().chunkMap.worker);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CACHE regions={} own_region_cached=false", size);
        }
        if (!name.equals("C6_JOINT_READBACK")) { fixture.checkpoint(name); }
    }

    void tick() {
        if (flight != null) { flight.tick(); return; }
        if (chain != null) { chain.tick(); return; }
        if (settlement != null) { settlement.tick(); return; }
        if (guard != null) { guard.tick(); return; }
        if (shutdown != null) { shutdown.tick(); return; }
        if (stale != null) { stale.tick(); return; }
        if (unloaded != null) { unloaded.tick(); return; }
        if (recovery) { observer.tick(); return; }
        fixture.tick();
        if (recovery || !injected) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        var runtime = EscrowRuntime.of(server);
        if (data == null || runtime.barrier().ioFailures() == 0) { return; }
        var coordinator = data.bind(server);
        EscrowTags.require(data.ledger().records().size() == 1, "E3 lost its durable L1");
        var op = data.ledger().records().get(0);
        var view = runtime.reconciliationScheduler().view(op.id());
        boolean proof = coordinator.reconciliationCheckpointProved(op.id());
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_E3 records={} pending={} issued={} io_failures={} reason={} proof={}",
                data.ledger().records().size(), coordinator.admissionAuthority().pendingCount(), data.ledger().issuedThrough(),
                runtime.barrier().ioFailures(), view.reason(), proof);
        EscrowTags.require(coordinator.admissionAuthority().pendingCount() == 1 && data.ledger().issuedThrough() == 1
                && runtime.barrier().ioFailures() == 1 && EscrowReason.BARRIER_IO.name().equals(view.reason()) && !proof
                && coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key()),
                "E3 failed proof changed or released authority");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_PAUSED cut={} hook=PROPAGATED_CLOSE_FAILURE", E3);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 forced stop did not arrive within 30 seconds");
    }
}
