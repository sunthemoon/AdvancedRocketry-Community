package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Added only to the explicitly exported diagnostic JAR. All actual effects use the installed coordinator. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class W208NativeAvailability {
    private static final BlockPos ORIGIN = new BlockPos(8, 100, 8);
    private final MinecraftServer server;
    private final RocketTransactionSavedData data;
    private final String scenario = System.getProperty("arce.w208.case");
    private final String stage = System.getProperty("arce.w208.stage", "start");
    private final W208WriterControl control;
    private final ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
    private final List<Map<String, Object>> ticks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final Map<String, io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy> policies = new HashMap<>();
    private volatile int age;
    private int requestTick = -1, settled, traffic;
    private volatile String phase = "INITIAL";
    private String lastDiagnostic = "";
    private long endStart, requestedNs, lastWrites = -1;
    private boolean loaded, begun, done, seeded, timingAttached;
    private UUID transaction;
    private String completion;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("arce.w208.enabled")) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        MinecraftForge.EVENT_BUS.register(new W208NativeAvailability(event.getServer()));
    }

    private W208NativeAvailability(MinecraftServer server) {
        this.server = server; data = RocketTransactionSavedData.get(server);
        control = new W208WriterControl(stage.equals("start") ? scenario : "small-quiet");
        RocketRuntime.sealedNativeObserverForReleaseTest(server, this::observe);
        decorate();
        report.put("case", scenario); report.put("stage", stage);
        report.put("source", System.getProperty("arce.w208.commit"));
    }

    private void observe(String label) {
        if (events.size() >= 16000) { throw new IllegalStateException("W208 event bound"); }
        var event = new LinkedHashMap<String, Object>();
        event.put("label", label); event.put("tick", age); event.put("ns", System.nanoTime());
        event.put("thread", Thread.currentThread().getName());
        if (server.isSameThread()) {
            if (label.equals("C1_RETAIN_AFTER")) { phase = "C1"; }
            if (label.equals("C3_ELECT_AFTER")) {
                phase = "C3";
                if (stage.equals("start") && scenario.contains("c3") && !seeded) { seed(); }
            }
            if (label.equals("C5_REMOVE_AFTER")) { phase = "C5"; }
            if (label.equals("C2_STAGE_BEFORE") || label.equals("C4_DISCARD_BEFORE") || label.equals("UNSEAL_PUBLISH_BEFORE")) {
                var own = data.entries().stream().filter(e -> e.record().type() == RocketTransactionType.SEALED_DISASSEMBLY).toList();
                boolean allowed = label.equals("UNSEAL_PUBLISH_BEFORE") ? own.isEmpty() && data.acknowledgedCurrent()
                        : own.size() == 1 && data.acknowledgedEntry(RocketTransactionSavedData.expectedEntry(own.get(0)))
                        && own.get(0).record().phase().name().equals(label.startsWith("C2") ? "LOCKED" : "RESTORED");
                event.put("authorized", allowed);
                if (!allowed) { report.put("safety_failure", label); }
                require(allowed, "Effect before acknowledgement: " + label);
            }
        }
        events.add(event);
    }

    private void seed() {
        report.put("near_root_bytes", W208JournalFixture.seed(data)); seeded = true;
        report.put("ballast", W208JournalFixture.BALLAST);
        report.put("padding", W208JournalFixture.padding());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void beginEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) { return; }
        endStart = System.nanoTime();
        // Admit the diagnostic request before the installed coordinator's END discovery spends the shared budget.
        try { step(); }
        catch (Exception error) {
            report.put("failure", error.toString());
            AdvancedRocketryCommunity.LOGGER.error("W208_FIXTURE_FAILED", error);
            finish("FAILED");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void end(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) { return; }
        try {
            if (requestTick >= 0) {
                var row = new LinkedHashMap<>(W208JournalFixture.observation(data)); row.put("tick", age);
                long core = server.tickTimes[server.getTickCount() % server.tickTimes.length];
                row.put("core_ns", core); row.put("end_ns", System.nanoTime() - endStart);
                row.put("tick_ns", core + System.nanoTime() - endStart); row.put("phase", phase);
                ticks.add(row);
                long writes = ((Number) row.get("writes")).longValue();
                if (writes != lastWrites) { observe("PUBLISHED_" + row.get("outcome")); lastWrites = writes; }
            }
        } catch (Exception error) {
            report.put("failure", error.toString());
            AdvancedRocketryCommunity.LOGGER.error("W208_FIXTURE_FAILED", error);
            finish("FAILED");
        }
    }

    private void step() throws Exception {
        age++;
        if (!loaded) {
            for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
                server.overworld().setChunkForced(x, z, true); server.overworld().getChunk(x, z);
            } }
            loaded = true;
        }
        if (age < 30 || !server.overworld().areEntitiesLoaded(new ChunkPos(0, 0).toLong())) { return; }
        if (!begun) {
            begun = true;
            if (stage.equals("start")) {
                if (!scenario.equals("small-quiet") && !scenario.contains("c3")) { seed(); }
                var rocket = setup();
                server.saveEverything(true, true, true);
                // Fixture setup only: prove the saved baseline before measuring the real request.
                W208JournalFixture.baseline(server);
                transaction = RocketRuntime.beginSealedForReleaseTest(rocket, result -> {
                    completion = result.name(); observe("COMPLETE_" + result);
                });
                require(transaction != null, "Sealed request refused before observation");
            } else {
                transaction = data.entries().stream().filter(e -> e.record().type() == RocketTransactionType.SEALED_DISASSEMBLY)
                        .map(e -> e.record().transactionId()).findFirst().orElse(null);
                report.put("loaded_ballast", data.entries().stream().filter(e -> e.snapshot().sourceDimension().equals(W208JournalFixture.ABSENT)).count());
                if (!data.acknowledgedCurrent()) { data.requestCheckpoint(server); }
            }
            requestTick = age; requestedNs = System.nanoTime(); observe("REQUEST");
            W208WriterTiming.attach(data, events, () -> age); timingAttached = true;
            return;
        }
        control.tick(age);
        if (control.held() && scenario.equals("near-interrupted") && stage.equals("start")) {
            finish("DRAIN_CONTROL"); return;
        }
        if (scenario.equals("near-traffic") && stage.equals("start") && phase.equals("C1") && traffic < 20
                && (age - requestTick) % 20 == 0) {
            W208JournalFixture.traffic(data, ++traffic); data.requestCheckpoint(server); observe("TRAFFIC");
        }
        String diagnostic = transaction == null ? "NO_U" : RocketRuntime.sealedDiagnosticForReleaseTest(server, transaction);
        if (!lastDiagnostic.equals(diagnostic)) {
            lastDiagnostic = diagnostic;
            events.add(Map.of("label", "DIAGNOSTIC", "tick", age, "value", diagnostic, "phase", phase));
        }
        var u = data.entries().stream().filter(e -> e.record().type() == RocketTransactionType.SEALED_DISASSEMBLY).toList();
        boolean carriers = false;
        for (int i = 0; i < 2; i++) { carriers |= server.overworld().getBlockEntity(ORIGIN.east(i)) instanceof SealedRocketBlockEntity; }
        var d = server.overworld().getEntity(W208JournalFixture.id("entity"));
        boolean stable = u.isEmpty() && !carriers && data.acknowledgedCurrent()
                && (!(d instanceof RocketEntity rocket) || !RocketRuntime.sealedFrozen(rocket));
        if (stable && trafficObserved(scenario, stage, traffic, completion)) { settled++; } else { settled = 0; }
        if (settled >= 20) { finish("COMPLETED"); }
        else if (age - requestTick >= 1200) { finish("NOT_COMPLETED"); }
    }

    private RocketEntity setup() {
        var level = server.overworld(); var chest = new ChestBlockEntity(ORIGIN, Blocks.CHEST.defaultBlockState());
        var diamonds = new ItemStack(Items.DIAMOND, 7); diamonds.getOrCreateTag().putString("W152", "w208-original"); chest.setItem(0, diamonds);
        var snapshot = RocketStructureSnapshot.create(W208JournalFixture.id("snapshot"), level.dimension().location(),
                new RocketPosition(8, 100, 8), List.of(
                        new RocketBlock(new RocketPosition(0, 0, 0), RocketBlockStateAdapter.capture(Blocks.CHEST.defaultBlockState()), new VanillaContainerRocketAdapter().capture(chest)),
                        new RocketBlock(new RocketPosition(1, 0, 0), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState()))),
                List.of(new RocketPosition(1, 0, 0)), new RocketStats(2, 200, 1000, 1000, 1, 1, 1, 1), level.getGameTime());
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level)); rocket.setUUID(W208JournalFixture.id("entity"));
        rocket.initialize(snapshot, W208JournalFixture.id("logical"), W208JournalFixture.id("owner"));
        RocketTransferEntities.positionAtOrigin(rocket, snapshot.sourceOrigin());
        require(level.addFreshEntity(rocket), "Rocket insertion failed"); return rocket;
    }

    static boolean trafficObserved(String scenario, String stage, int traffic, String completion) {
        return !stage.equals("start") || !scenario.equals("near-traffic") || traffic >= 20 || completion != null;
    }

    private void finish(String status) {
        if (done) { return; } done = true;
        report.put("status", status); report.put("completion", completion == null ? "NONE" : completion);
        report.put("request_tick", requestTick); report.put("observation_ticks", requestTick < 0 ? 0 : age - requestTick);
        report.put("observation_ns", requestTick < 0 ? 0 : System.nanoTime() - requestedNs);
        report.put("diagnostic", lastDiagnostic); report.put("traffic", traffic);
        if (!scenario.equals("near-interrupted") || !stage.equals("start")) { control.release(); }
        write(); AdvancedRocketryCommunity.LOGGER.info("W208_DONE status={}", status);
    }

    private void write() {
        report.put("events", List.copyOf(events)); report.put("ticks", ticks); report.put("journal", W208JournalFixture.observation(data));
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("w208.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void stopping(ServerStoppingEvent event) {
        if (event.getServer() != server) { return; }
        boolean interrupted = stage.equals("start") && scenario.equals("near-interrupted") && control.held();
        if (!done) { finish("NOT_COMPLETED"); }
        if (interrupted) {
            Thread release = new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                control.release();
            }, "W208-drain-release");
            release.setDaemon(true); release.start(); Thread.currentThread().interrupt();
        } else { control.release(); }
        report.put("stop_close", W208JournalFixture.close(data));
        report.put("injected_interrupt", interrupted);
        if (interrupted) {
            require(Thread.currentThread().isInterrupted(), "Close lost the injected interrupt");
            Thread.interrupted(); // Only undo the injected test interrupt after the real join has returned.
        }
        write(); MinecraftForge.EVENT_BUS.unregister(this);
    }

    private void decorate() {
        try {
            var manager = field(RocketRuntime.class, "service").get(null);
            var coordinator = field(RocketManager.class, "sealed").get(manager);
            var requests = field(SealedDisassemblyCoordinator.class, "writes").get(coordinator);
            var port = field(SealedJournalRequests.class, "port");
            var original = (SealedJournalRequests.Port) port.get(requests);
            port.set(requests, new SealedJournalRequests.Port() {
                public long now() { return original.now(); }
                public boolean operational() { return original.operational(); }
                public boolean current() { return original.current(); }
                public boolean writePending() { return original.writePending(); }
                public CheckedJournalCheckpoint.Outcome outcome() { return original.outcome(); }
                public boolean request() {
                    var policy = policies.computeIfAbsent(phase, p -> control.policy(p, () -> age));
                    long start = System.nanoTime(); boolean result = W208JournalFixture.request(server, policy);
                    events.add(Map.of("label", "WRITE_REQUEST", "tick", age, "phase", phase, "ns", System.nanoTime() - start, "accepted", result));
                    return result;
                }
                public void written(CheckedJournalCheckpoint.Outcome outcome) { observe(phase + "_WRITE_" + outcome); original.written(outcome); }
            });
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
