package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

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
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Overworld-lifetime service handle. Static members are event adapters/capability identity, never world state. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class EscrowRuntime {
    public static final Capability<EscrowRuntime> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() { });
    private final EscrowSaveMonitor monitor = new EscrowSaveMonitor();
    private final Map<ResourceKey<Level>, EscrowChunkStore> stores = new HashMap<>();
    private EscrowBarrier barrier;
    private boolean stopping;
    private EscrowReleaseProbe probe;
    private EscrowChainReleaseProbe chainProbe;
    private EscrowRecoveryReleaseProbe recoveryProbe;
    private EscrowBenchmarkProbe benchmark;
    EscrowTimingSink timings() { return benchmark == null ? EscrowTimingSink.NONE : benchmark; }
    private EscrowReconciliationScheduler scheduler;
    private EscrowTestReconciliation reconciliation;
    boolean stopping() { return stopping; }
    EscrowTestReconciliation reconcileTests(EscrowCoordinator coordinator, java.util.List<EscrowTestEndpoints.Fixture> fixtures,
                                          java.util.function.Predicate<EscrowEndpoint> veto) {
        if (reconciliation != null) { reconciliation.close(); }
        reconciliation = new EscrowTestReconciliation(coordinator, this, fixtures, veto);
        scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition, reconciliation);
        return reconciliation;
    }
    public void checkpoint(String cut) { if (probe != null) { probe.checkpoint(cut); } }
    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.Committer ledgerCommitter(MinecraftServer server) {
        String testCase = System.getProperty("advancedrocketrycommunity.escrowCase", "");
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && "start".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))
                && (testCase.equals("E4L1_UNKNOWN") || testCase.equals("E4L2_UNKNOWN"))) {
            EscrowTestEndpoints.requireEnabled(server);
            return (staged, target) -> {
                var type = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW;
                var payload = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
                boolean empty = payload.getList("records", 10).isEmpty();
                if (payload.getLong("writer_generation") > 1 && (testCase.equals("E4L2_UNKNOWN") == empty)) {
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
    public EscrowSaveMonitor monitor() { return monitor; }
    public EscrowBarrier barrier() {
        EscrowTags.require(!stopping, "Escrow runtime is stopping");
        if (barrier == null) { barrier = new EscrowBarrier(this::checkpoint, new java.util.concurrent.atomic.AtomicBoolean(true), timings()); } return barrier;
    }
    public EscrowChunkStore store(ServerLevel level) {
        EscrowTags.require(!stopping && (stores.containsKey(level.dimension()) || stores.size() < 64), "Escrow Level service limit exceeded");
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
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(event.getServer());
        if (data != null) {
            var coordinator = data.bind(event.getServer());
            if (runtime.scheduler == null) {
                runtime.scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition,
                        (op, records, completion) -> completion.accept(new EscrowReconciliationScheduler.Result(EscrowReason.WAITING_UNLOADED, false)));
            }
            data.tick(event.getServer().getTickCount());
        }
        if (runtime.barrier != null) { runtime.barrier.tick(event.getServer().getTickCount()); }
        if (runtime.scheduler != null && data != null) {
            runtime.scheduler.tick(event.getServer().getTickCount()); data.bind(event.getServer()).persistTransitions();
        }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowScenario") != null) {
            String testCase = System.getProperty("advancedrocketrycommunity.escrowCase", "");
            if (testCase.equals("BENCH")) {
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
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void benchmarkStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            var benchmark = of(event.getServer()).benchmark; if (benchmark != null) { benchmark.startTick(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void benchmarkEnd(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            var benchmark = of(event.getServer()).benchmark; if (benchmark != null) { benchmark.endTick(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void stopping(ServerStoppingEvent event) {
        EscrowRuntime runtime = of(event.getServer()); runtime.stopping = true;
        if (runtime.barrier != null) { runtime.barrier.quiesce(); }
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(event.getServer());
        if (data != null) { data.stop(); }
        if (runtime.barrier != null) { runtime.barrier.close(); }
        if (runtime.reconciliation != null) { runtime.reconciliation.close(); }
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
        if (runtime.barrier != null) { runtime.barrier.close(); } // Discards unfinished barriers; no new synchronization.
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(server);
        if (data != null) { data.abort(); }
        if (runtime.reconciliation != null) { runtime.reconciliation.close(); }
    }
    private static final class Provider implements ICapabilityProvider {
        private final EscrowRuntime runtime = new EscrowRuntime();
        private final LazyOptional<EscrowRuntime> value = LazyOptional.of(() -> runtime);
        private void invalidate() { runtime.stopping = true; runtime.monitor.clear(); runtime.stores.clear(); value.invalidate(); }
        @Override @NotNull public <T> LazyOptional<T> getCapability(@NotNull Capability<T> requested, @Nullable Direction side) {
            return CAPABILITY.orEmpty(requested, value);
        }
    }
}
