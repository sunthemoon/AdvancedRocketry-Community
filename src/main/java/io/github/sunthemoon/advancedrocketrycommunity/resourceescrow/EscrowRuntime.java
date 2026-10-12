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
        if (barrier == null) { barrier = new EscrowBarrier(this::checkpoint); } return barrier;
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
        if (data != null) { data.tick(event.getServer().getTickCount()); }
        if (runtime.barrier != null) { runtime.barrier.tick(event.getServer().getTickCount()); }
        if (Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowScenario") != null) {
            String testCase = System.getProperty("advancedrocketrycommunity.escrowCase", "");
            if (testCase.equals("C1") || testCase.equals("C2")) {
                if (runtime.chainProbe == null) { runtime.chainProbe = new EscrowChainReleaseProbe(event.getServer()); }
                runtime.chainProbe.tick();
            } else {
                if (runtime.probe == null) { runtime.probe = new EscrowReleaseProbe(event.getServer()); }
                runtime.probe.tick();
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void stopping(ServerStoppingEvent event) {
        EscrowRuntime runtime = of(event.getServer()); runtime.stopping = true;
        if (runtime.barrier != null) { runtime.barrier.close(); }
        ResourceEscrowSavedData data = ResourceEscrowSavedData.existing(event.getServer());
        if (data != null) { data.stop(); }
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
