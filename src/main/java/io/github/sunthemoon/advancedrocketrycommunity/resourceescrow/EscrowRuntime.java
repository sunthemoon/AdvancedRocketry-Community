package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-owned escrow services: a capability of the Overworld ServerLevel, so it lives as long as the running server.
 * It owns the one admission authority for fills and the bay; each ledger-owned coordinator attaches to its PROPULSION
 * delegate. Static members are event adapters/capability identity, never world state.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class EscrowRuntime {
    public static final Capability<EscrowRuntime> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private EscrowAdmissionAuthority admission;
    private EscrowAdmissionAuthority.Delegate propulsion;
    private final EscrowSaveMonitor monitor = new EscrowSaveMonitor();
    private final Map<ResourceKey<Level>, EscrowChunkStore> stores = new HashMap<>();
    private EscrowBarrier barrier;
    private boolean stopping;
    private EscrowReleaseProbe probe;
    private EscrowChainReleaseProbe chainProbe;
    private EscrowRecoveryReleaseProbe recoveryProbe;
    private EscrowBenchmarkProbe benchmark;
    private volatile EscrowFlightBenchmarkProbe flightBenchmark;
    private EscrowTimingSink consumerTimings = EscrowTimingSink.NONE;
    private boolean consumerTimingSelected;
    private EscrowRocketAuditProbe rocketAuditProbe;
    private EscrowSettlementProbe settlementProbe;
    private EscrowSettlementCompletionProbe settlementCompletionProbe;
    private EscrowPreF2Probe preF2Probe;
    private volatile EscrowReconciliationProbe reconciliationProbe;
    private EscrowSourceClockProbe sourceClockProbe;
    private EscrowDestinationProbe destinationProbe;
    private EscrowPhaseProbe phaseProbe;
    private volatile EscrowTerminalProbe terminalProbe;
    private volatile EscrowLoaderReleaseProbe loaderProbe;
    private volatile java.util.function.Consumer<String> terminalTestCut;
    void terminalCutForTests(MinecraftServer server, java.util.function.Consumer<String> cut) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(server.isSameThread() && (cut == null || terminalTestCut == null), "Terminal cut requires exclusive owner-thread setup");
        terminalTestCut = cut;
    }
    private EscrowReturnProbe returnProbe;
    private final EscrowSettlementOperator settlement = new EscrowSettlementOperator();
    private final EscrowSettlementRecovery settlements = new EscrowSettlementRecovery();
    private final EscrowPreF2Cancellation preF2Cancellation = new EscrowPreF2Cancellation();
    private final EscrowFlightCheckpoint flightCheckpoint = new EscrowFlightCheckpoint();
    private final EscrowGovernedDispatch governedFlights = new EscrowGovernedDispatch();
    private final EscrowGovernedExecutorHolds executorHolds = new EscrowGovernedExecutorHolds();
    public EscrowGovernedExecutorHolds executorHolds() { return executorHolds; }
    private final EscrowGovernedSourceFlight sourceFlights = new EscrowGovernedSourceFlight();
    private final EscrowGovernedDestination destinations = new EscrowGovernedDestination();
    private final EscrowGovernedPhases flightPhases = new EscrowGovernedPhases();
    private final EscrowGovernedArrival arrivals = new EscrowGovernedArrival();
    private final EscrowGovernedReturn returns = new EscrowGovernedReturn();
    private final EscrowGovernedTerminal terminal = new EscrowGovernedTerminal();
    public EscrowGovernedTerminal terminal() { return terminal; }
    public EscrowGovernedReturn returns() { return returns; }
    public EscrowGovernedArrival arrivals() { return arrivals; }
    public EscrowGovernedPhases flightPhases() { return flightPhases; }
    // The explicitly enabled DP_* cut fixture observes F4 only, including its asynchronous own-store.
    // Do not let a newly implemented later executor race that fixture's historical cut boundary.
    boolean destinationCutActive() { return destinationProbe != null; }
    boolean phaseCutActive() { return phaseProbe != null && phaseProbe.holdsArrival(); }
    // Historical native return cuts stop at F6; live returns continue through the terminal executor.
    boolean returnCutActive() { return returnProbe != null; }
    public EscrowGovernedDestination destinations() { return destinations; }
    public EscrowGovernedSourceFlight sourceFlights() { return sourceFlights; }
    public EscrowGovernedDispatch governedFlights() { return governedFlights; }
    EscrowFlightCheckpoint flightCheckpoint() { return flightCheckpoint; }
    void observeFlight(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket) {
        sourceFlights.observe(rocket);
        destinations.observe(rocket);
        flightPhases.observe(rocket);
        arrivals.observe(rocket);
        returns.observe(rocket);
        terminal.observe(rocket);
        flightCheckpoint.observe(rocket); preF2Cancellation.observe(rocket);
    }
    void invalidateFlight(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket) {
        flightCheckpoint.invalidated(rocket); preF2Cancellation.invalidated(rocket);
        terminal.invalidated(rocket);
        rocket.assemblyTransactionId().ifPresent(governedFlights::invalidated);
    }
    EscrowSettlementOperator settlement() { return settlement; }
    EscrowSettlementRecovery settlementRecovery() { return settlements; }
    EscrowReconciliationScheduler reconciliationScheduler() { return scheduler; }
    String settlementPhase() { return settlements.phase(); }
    EscrowTimingSink timings() { return flightBenchmark != null ? flightBenchmark.metrics : benchmark == null ? consumerTimings : benchmark; }
    java.util.List<EscrowSettlementPruning.Group> settlementGroups(EscrowLedger root) { return settlements.pruningGroups(root); }
    private EscrowReconciliationScheduler scheduler;
    /** The coordinator the scheduler drives; a test hook's ledger replacement makes the tick rebuild it. */
    private EscrowCoordinator scheduled;
    private EscrowTestReconciliation reconciliation;
    private EscrowFillQueue fills;
    /** GameTest half of the gated switch: admission for exactly these devices, never a production enable. */
    private final java.util.Set<java.util.UUID> admissionDevices = new java.util.HashSet<>();
    boolean stopping() { return stopping; }
    EscrowTestReconciliation reconcileTests(EscrowCoordinator coordinator, java.util.List<EscrowTestEndpoints.Fixture> fixtures,
                                          java.util.function.Predicate<EscrowEndpoint> veto) {
        if (reconciliation != null) { reconciliation.close(); }
        reconciliation = new EscrowTestReconciliation(coordinator, this, fixtures, veto);
        scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition, reconciliation);
        scheduled = coordinator;
        return reconciliation;
    }
    /** The loaders' fair queue, created on the server thread. */
    EscrowFillQueue fills(MinecraftServer server) {
        if (fills == null) {
            EscrowTags.require(server.isSameThread(), "The fill queue lives on the server thread");
            fills = new EscrowFillQueue();
        }
        return fills;
    }
    /**
     * The gated switch for GameTests (W11B3-DESIGN section 2): opens or closes admission for one loader device. It
     * needs the release-test gate; production admission stays disabled.
     */
    public void loaderAdmissionForTests(MinecraftServer server, java.util.UUID device, boolean allowed) {
        EscrowTestEndpoints.requireEnabled(server);
        if (!allowed) { admissionDevices.remove(device); return; }
        EscrowTags.require(admissionDevices.contains(device) || admissionDevices.size() < EscrowLimits.OPERATIONS,
                "Too many gated loader devices");
        admissionDevices.add(device);
    }
    boolean loaderAdmissionAllowed(java.util.UUID device) { return admissionDevices.contains(device); }
    public void checkpoint(String cut) {
        var measured = flightBenchmark; if (measured != null) { measured.metrics.checkpoint(cut); }
        var ordinary = reconciliationProbe; if (ordinary != null) { ordinary.checkpoint(cut); }
        var terminalCut = terminalTestCut; if (terminalCut != null) { terminalCut.accept(cut); }
        var nativeTerminal = terminalProbe; if (nativeTerminal != null) { nativeTerminal.checkpoint(cut); }
        var loader = loaderProbe; if (loader != null) { loader.checkpoint(cut); }
        if (returnProbe != null) { returnProbe.checkpoint(cut); }
        if (phaseProbe != null) { phaseProbe.checkpoint(cut); }
        if (destinationProbe != null) { destinationProbe.checkpoint(cut); }
        if (probe != null) { probe.checkpoint(cut); }
        if (rocketAuditProbe != null) { rocketAuditProbe.checkpoint(cut); }
        if (settlementProbe != null) { settlementProbe.checkpoint(cut); }
        if (settlementCompletionProbe != null) { settlementCompletionProbe.checkpoint(cut); }
        if (preF2Probe != null) { preF2Probe.checkpoint(cut); }
        if (sourceClockProbe != null) { sourceClockProbe.checkpoint(cut); }
    }
    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.Committer ledgerCommitter(MinecraftServer server) {
        String testCase = EscrowLoaderStorageProbe.writerCase(System.getProperty("advancedrocketrycommunity.escrowCase", ""));
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && "start".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))
                && (testCase.equals("E4L1_UNKNOWN") || testCase.equals("E4L2_UNKNOWN") || testCase.equals("E4L2_RESAVE")
                    || testCase.equals("LF_M_N2_UNKNOWN"))) {
            EscrowTestEndpoints.requireEnabled(server);
            var first = new java.util.concurrent.atomic.AtomicBoolean(true);
            return (staged, target) -> {
                var type = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW;
                var payload = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
                boolean empty = payload.getList("records", 10).isEmpty();
                // E4L2_RESAVE: only the first L2 move is ambiguous; a later move would succeed if a guard ever allowed it.
                // The generation-1 baseline is also empty, so it must not consume that single injection.
                boolean ambiguous = testCase.equals("LF_M_N2_UNKNOWN")
                        ? EscrowLoaderCutProbe.baselineCandidate(payload)
                        : payload.getLong("writer_generation") > 1 && (testCase.equals("E4L2_RESAVE")
                        ? empty && first.getAndSet(false) : testCase.equals("E4L2_UNKNOWN") == empty);
                if (ambiguous) {
                    throw new java.io.IOException("Release-test ambiguous replacement outcome");
                }
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
            };
        }
        return io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile::atomicMove;
    }
    public static EscrowRuntime of(MinecraftServer server) {
        return server.overworld().getCapability(CAPABILITY).orElseThrow(() -> new IllegalStateException("Missing escrow lifetime service"));
    }
    /**
     * The one PROPULSION delegate, registered on the server thread at ServerStartingEvent. A ledger bound earlier, while
     * the Levels prepare, registers it at that bind instead; there is never a second registration.
     */
    EscrowAdmissionAuthority.Delegate propulsion(MinecraftServer server) {
        if (propulsion == null) {
            EscrowTags.require(server.isSameThread(), "Escrow admission registers on the server thread");
            admission = new EscrowAdmissionAuthority();
            propulsion = admission.register(EscrowAdmissionAuthority.Consumer.PROPULSION);
        }
        return propulsion;
    }
    /**
     * The only path that replaces the ledger SavedData, for test hooks. It retargets the PROPULSION delegate on the
     * owner thread and returns false, changing nothing and throwing nothing, while the attached coordinator still holds
     * queued, captured or UNKNOWN work. A null ledger returns the delegate to NO_FILE; a replaced coordinator stays open
     * and attaches again if its ledger is restored.
     */
    public boolean replaceLedgerForTests(MinecraftServer server, @Nullable ResourceEscrowSavedData next) {
        EscrowTestEndpoints.requireEnabled(server);
        var delegate = propulsion(server);
        if (ResourceEscrowSavedData.existing(server) != next && delegate.holdsWork()) { return false; }
        server.overworld().getDataStorage().set(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration
                .ManagedSavedDataType.RESOURCE_ESCROW.dataName(), next);
        return next == null ? delegate.retarget(null) : next.bind(server).attachAdmission();
    }
    /**
     * Startup lookup, before the first tick: the load-only {@code existing} never creates a file. An existing ledger
     * binds at once, so its coordinator's index is LOADED before any admission; no file means NO_FILE. A file that did
     * not load (the pre-start check normally refuses startup first) keeps the delegate NOT_LOADED, so admission refuses.
     */
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        var server = event.getServer(); var delegate = of(server).propulsion(server);
        if (delegate.state() != EscrowAdmissionAuthority.Delegate.State.NOT_LOADED) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data != null) { data.bind(server); }
        else if (!java.nio.file.Files.exists(ResourceEscrowSavedData.file(server))) { delegate.noFile(); }
        else { LOGGER.warn("ARCE_ESCROW_LEDGER_NOT_LOADED; the ledger file did not load, so escrow admission stays refused"); }
    }
    public EscrowSaveMonitor monitor() { return monitor; }
    public EscrowBarrier barrier() {
        EscrowTags.require(!stopping, "Escrow runtime is stopping");
        if (barrier == null) { barrier = new EscrowBarrier(this::checkpoint, new java.util.concurrent.atomic.AtomicBoolean(true), timings()); } return barrier;
    }
    public EscrowChunkStore store(ServerLevel level) {
        EscrowReconciliationView.requireStoreCapacity(stopping, stores.containsKey(level.dimension()), stores.size());
        return stores.computeIfAbsent(level.dimension(), key -> new EscrowChunkStore(level, monitor));
    }
    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterCapabilitiesEvent event) { event.register(EscrowRuntime.class); }
        private Registration() { }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            Provider provider = new Provider(); event.addCapability(ModIdentity.id("resource_escrow_runtime"), provider);
            event.addListener(provider::invalidate);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void saveAttempt(ChunkDataEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) { of(level.getServer()).monitor.observed(event); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        var runtime = of(event.getServer()); if (runtime.stopping) { return; }
        if (!runtime.consumerTimingSelected) {
            runtime.consumerTimings = EscrowConsumerTimingProbe.select(Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                    Boolean.getBoolean(EscrowConsumerTimingProbe.PROPERTY), () -> EscrowTestEndpoints.requireEnabled(event.getServer()));
            runtime.consumerTimingSelected = true;
        }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && EscrowFlightBenchmarkProbe.CASE.equals(System.getProperty("advancedrocketrycommunity.escrowCase"))
                && System.getProperty("advancedrocketrycommunity.escrowScenario") != null && runtime.flightBenchmark == null) {
            runtime.flightBenchmark = new EscrowFlightBenchmarkProbe(event.getServer());
        }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowCase", "").startsWith("SR_")) {
            if (runtime.settlementCompletionProbe == null) { runtime.settlementCompletionProbe = new EscrowSettlementCompletionProbe(event.getServer()); }
            runtime.settlementCompletionProbe.tick();
        }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowScenario") != null
                && System.getProperty("advancedrocketrycommunity.escrowCase", "").startsWith("SO_")) {
            // Observe retained O1 before recovery can publish normalization or advance O2.
            if (runtime.settlementProbe == null) { runtime.settlementProbe = new EscrowSettlementProbe(event.getServer()); }
            runtime.settlementProbe.tick();
        }
        // The loaders' fair queue first: at most one capture per tick, possibly a world's first fill and its ledger.
        runtime.fills(event.getServer()).tick(event.getServer());
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(event.getServer());
        if (data != null) {
            var coordinator = data.bind(event.getServer());
            if (!data.operational()) { return; }
            if (runtime.scheduler == null || runtime.scheduled != coordinator) {
                runtime.scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition,
                        (op, records, completion) -> RocketEscrowAudit.attempt(coordinator, event.getServer(), op, records, completion));
                runtime.scheduled = coordinator;
            }
            data.tick(event.getServer().getTickCount());
            if (data.ledger().floors().stream().anyMatch(floor -> floor.hasUUID("transfer_id"))) {
                var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(event.getServer());
                if (journal.retryChecked(event.getServer())) { coordinator.collectFloors(journal); }
            }
        }
        if (runtime.barrier != null) { runtime.barrier.tick(event.getServer().getTickCount()); }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowCase", "").startsWith("CR_")) {
            if (runtime.reconciliationProbe == null) { runtime.reconciliationProbe = new EscrowReconciliationProbe(event.getServer()); }
            runtime.reconciliationProbe.beforeReconciliation();
        }
        if (runtime.scheduler != null && data != null) {
            runtime.scheduler.tick(event.getServer().getTickCount()); data.bind(event.getServer()).persistTransitions();
        }
        if (data != null) { runtime.settlements.tick(event.getServer(), data.bind(event.getServer()), runtime); }
        if (data != null) { runtime.preF2Cancellation.tick(event.getServer(), data.bind(event.getServer()), runtime); }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowScenario") != null) {
            String testCase = System.getProperty("advancedrocketrycommunity.escrowCase", "");
            if (testCase.equals(EscrowFlightBenchmarkProbe.CASE)) {
                runtime.flightBenchmark.tick();
            } else if (testCase.startsWith("CR_")) {
                if (runtime.reconciliationProbe == null) { runtime.reconciliationProbe = new EscrowReconciliationProbe(event.getServer()); }
                runtime.reconciliationProbe.tick();
            } else if (testCase.startsWith(EscrowLoaderReleaseProbe.PREFIX)) {
                if (runtime.loaderProbe == null) { runtime.loaderProbe = new EscrowLoaderReleaseProbe(event.getServer()); }
                runtime.loaderProbe.tick();
            } else if (testCase.startsWith("FT_")) {
                if (runtime.terminalProbe == null) { runtime.terminalProbe = new EscrowTerminalProbe(event.getServer()); }
                runtime.terminalProbe.tick();
            } else if (testCase.startsWith("RT_")) {
                if (runtime.returnProbe == null) { runtime.returnProbe = new EscrowReturnProbe(event.getServer()); }
                runtime.returnProbe.tick();
            } else if (testCase.startsWith("FP_") || testCase.startsWith("FD_")) {
                if (runtime.phaseProbe == null) { runtime.phaseProbe = new EscrowPhaseProbe(event.getServer()); }
                runtime.phaseProbe.tick();
            } else if (testCase.startsWith("DP_")) {
                if (runtime.destinationProbe == null) { runtime.destinationProbe = new EscrowDestinationProbe(event.getServer()); }
                runtime.destinationProbe.tick();
            } else if (testCase.startsWith("SC_")) {
                if (runtime.sourceClockProbe == null) { runtime.sourceClockProbe = new EscrowSourceClockProbe(event.getServer()); }
                runtime.sourceClockProbe.tick();
            } else if (testCase.startsWith("PF_")) {
                if (runtime.preF2Probe == null) { runtime.preF2Probe = new EscrowPreF2Probe(event.getServer()); }
                runtime.preF2Probe.tick();
            } else if (testCase.startsWith("SR_")) {
                // This probe runs before settlement recovery so its deliberate veto precedes every O2 attempt.
            } else if (testCase.startsWith("SO_")) {
                // The O1 probe observes before recovery above, then follows eventual O2-O4.
            } else if (testCase.startsWith("RA_")) {
                if (runtime.rocketAuditProbe == null) { runtime.rocketAuditProbe = new EscrowRocketAuditProbe(event.getServer()); }
                runtime.rocketAuditProbe.tick();
            } else if (testCase.equals("BENCH")) {
                if (runtime.benchmark == null) { runtime.benchmark = new EscrowBenchmarkProbe(event.getServer(), runtime); }
                runtime.benchmark.tick();
            } else if (testCase.equals("E5")) {
                if (runtime.recoveryProbe == null) { runtime.recoveryProbe = new EscrowRecoveryReleaseProbe(event.getServer()); }
                runtime.recoveryProbe.tick();
            } else if (testCase.equals("C1") || testCase.equals("C2") || testCase.equals("C3")) {
                if (runtime.chainProbe == null) { runtime.chainProbe = new EscrowChainReleaseProbe(event.getServer()); }
                runtime.chainProbe.tick();
            } else {
                if (runtime.probe == null) { runtime.probe = new EscrowReleaseProbe(event.getServer()); }
                runtime.probe.tick();
            }
        }
    }
    /** W95's native restart fixture inspects the START of the first tick, before level ticking can bind a ledger lazily. */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void loaderProbeStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || System.getProperty("advancedrocketrycommunity.escrowScenario") == null
                || !System.getProperty("advancedrocketrycommunity.escrowCase", "").startsWith(EscrowLoaderReleaseProbe.PREFIX)) { return; }
        var runtime = of(event.getServer()); if (runtime.stopping) { return; }
        if (runtime.loaderProbe == null) { runtime.loaderProbe = new EscrowLoaderReleaseProbe(event.getServer()); }
        runtime.loaderProbe.startTick();
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void benchmarkStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var benchmark = of(event.getServer()).benchmark; if (benchmark != null) { benchmark.startTick(); }
            var flights = of(event.getServer()).flightBenchmark; if (flights != null) { flights.startTick(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void benchmarkEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var benchmark = of(event.getServer()).benchmark; if (benchmark != null) { benchmark.endTick(); }
            var flights = of(event.getServer()).flightBenchmark; if (flights != null) { flights.endTick(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void stopping(ServerStoppingEvent event) {
        EscrowRuntime runtime = of(event.getServer()); runtime.stopping = true;
        if (runtime.fills != null) { runtime.fills.clear(); }
        runtime.settlement.close();
        if (runtime.barrier != null) { runtime.barrier.quiesce(); }
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(event.getServer());
        if (data != null) { data.stop(); }
        runtime.settlements.close();
        runtime.preF2Cancellation.close();
        runtime.flightCheckpoint.close();
        runtime.terminal.close();
        runtime.governedFlights.close(); runtime.destinations.close();
        runtime.executorHolds.clear(); runtime.sourceFlights.clear(); runtime.flightPhases.clear(); runtime.arrivals.clear(); runtime.returns.close();
        if (runtime.barrier != null) { runtime.barrier.close(); }
        if (runtime.reconciliation != null) { runtime.reconciliation.close(); }
        if (runtime.consumerTimings instanceof EscrowConsumerTimingProbe consumer) { consumer.write(event.getServer().getServerDirectory().toPath()); }
    }
    /**
     * Crash path: runServer calls stopServer without ServerStoppingEvent. stopServer still unloads the overworld after
     * saveAllChunks and before LevelStorageAccess.close(), so the writer is joined while world storage is owned.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) { crashStop(level.getServer(), level); }
    }
    /** Backstop when stopServer failed before the overworld unloaded; ownership may already be released. */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void stopped(ServerStoppedEvent event) {
        crashStop(event.getServer(), event.getServer().overworld());
    }
    private static void crashStop(MinecraftServer server, ServerLevel overworld) {
        if (overworld == null) { return; }
        EscrowRuntime runtime = overworld.getCapability(CAPABILITY).resolve().orElse(null);
        if (runtime == null || runtime.stopping) { return; }
        runtime.stopping = true;
        if (runtime.fills != null && server.isSameThread()) { runtime.fills.clear(); }
        runtime.settlement.close();
        if (runtime.barrier != null) { runtime.barrier.close(); } // Discards unfinished barriers; no new synchronization.
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(server);
        if (data != null) { data.abort(); }
        runtime.settlements.close();
        runtime.preF2Cancellation.close();
        runtime.flightCheckpoint.close();
        runtime.terminal.close();
        runtime.governedFlights.close(); runtime.destinations.close();
        runtime.executorHolds.clear(); runtime.sourceFlights.clear(); runtime.flightPhases.clear(); runtime.arrivals.clear(); runtime.returns.close();
        if (runtime.reconciliation != null) { runtime.reconciliation.close(); }
    }
    private static final class Provider implements ICapabilityProvider {
        private final EscrowRuntime runtime = new EscrowRuntime();
        private final LazyOptional<EscrowRuntime> value = LazyOptional.of(() -> runtime);
        private void invalidate() { runtime.stopping = true; runtime.terminal.close(); runtime.governedFlights.close(); runtime.destinations.close(); runtime.executorHolds.clear(); runtime.sourceFlights.clear(); runtime.flightPhases.clear(); runtime.arrivals.clear(); runtime.monitor.clear(); runtime.stores.clear(); value.invalidate(); }
        @Override @NotNull public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requested, value);
        }
    }
}
