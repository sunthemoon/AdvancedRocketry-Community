package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedJournalRequests;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Explicit diagnostic JAR only. All transitions and writes remain the installed production implementation. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class W152NativeProbe {
    private static final BlockPos ORIGIN = new BlockPos(15, 100, 15);
    private final MinecraftServer server;
    private final String stage = System.getProperty("advancedrocketrycommunity.w152Stage", "start");
    private final String boundary = System.getProperty("advancedrocketrycommunity.w152Cut", "NONE");
    private final W152NativeConsumers consumers;
    private final W152NativeFailure preparationFailure;
    private final boolean neverSaved = System.getProperty("advancedrocketrycommunity.w152InitialD", "SAVED").equals("NEVER_SAVED");
    private final boolean allHosts = System.getProperty("advancedrocketrycommunity.w152PayloadHosts", "CONTAINERS").equals("ALL");
    private final ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile int age;
    private volatile String writerPhase = "INITIAL";
    private String requestedPhase = "INITIAL";
    private volatile boolean done;
    private RocketEntity rocket;
    private UUID transaction;
    private int placed, settled;
    private boolean initialized, begun;
    private String completion;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.w152Native")) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        var observer = new W152NativeProbe(event.getServer());
        MinecraftForge.EVENT_BUS.register(observer);
    }
    private W152NativeProbe(MinecraftServer server) {
        this.server = server;
        consumers = new W152NativeConsumers(server, System.getProperty("advancedrocketrycommunity.w152Experiment", ""), this::pause);
        preparationFailure = boundary.equals("UNSEAL_PREPARE_FAILED") ? W152NativeFailure.install() : null;
        RocketRuntime.sealedNativeObserverForReleaseTest(server, this::observe);
        decorateWriter();
    }
    static UUID id(String name) { return UUID.nameUUIDFromBytes(("w152-native:" + name).getBytes(StandardCharsets.UTF_8)); }

    private void observe(String label) {
        if (done) { return; }
        if (eventCount.incrementAndGet() > 16000) { throw new IllegalStateException("Native observer event bound"); }
        events.add(Map.of("label", label, "tick", age, "thread", Thread.currentThread().getName()));
        if (server.isSameThread()) {
            if (label.equals("C1_RETAIN_AFTER")) { writerPhase = "C1"; }
            if (label.equals("C3_ELECT_AFTER")) { writerPhase = "C3"; }
            if (label.equals("C5_REMOVE_AFTER")) { writerPhase = "C5"; }
            if (label.equals("C2_STAGE_AFTER") && ++placed == 2 && boundary.startsWith("PARTIAL_C_")) {
                incrementalSave(ORIGIN.south(), "PARTIAL_C");
                if (boundary.equals("PARTIAL_C_SAVED")) { pause(boundary); }
            }
            if (label.equals("UNSEAL_PUBLISH_AFTER") && boundary.equals("MIXED_C_N_SAVED")) {
                for (var at : List.of(ORIGIN.south(), ORIGIN.east())) {
                    if (server.overworld().getBlockEntity(at) instanceof net.minecraft.world.Container) {
                        incrementalSave(at, "MIXED_C_N"); pause(boundary);
                    }
                }
            }
        }
        if (label.equals(boundary)) { pause(label); }
    }
    private void incrementalSave(BlockPos at, String prefix) {
        try { W152NativeTerrain.save(server.overworld(), at, prefix, this::observe); }
        catch (RuntimeException failure) {
            // Production correctly retains work on a callback failure. Preserve the fixture cause before that catch.
            done = true; write("FAIL", prefix + ": " + failure);
            AdvancedRocketryCommunity.LOGGER.info("W152_NATIVE_DONE status=FAIL");
            throw failure;
        }
    }
    private void pause(String label) {
        if (stage.equals("recover2") || !paused.compareAndSet(false, true)) { return; }
        if (label.equals("UNSEAL_PREPARE_FAILED")) { preparationFailure.verifyAborted(); }
        write("CUT", label);
        AdvancedRocketryCommunity.LOGGER.info("W152_NATIVE_CUT boundary={}", label);
        try { Thread.sleep(20_000); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        throw new IllegalStateException("External harness failed to kill at " + label);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void tick(TickEvent.LevelTickEvent event) {
        // Forge ServerTick START precedes tickCount++; LevelTick START follows it and precedes the END work pass.
        if (event.level != server.overworld() || event.phase != TickEvent.Phase.START || done || paused.get()) { return; }
        try {
            age++;
            if (!initialized) {
                for (int x = -1; x <= 2; x++) { for (int z = -1; z <= 2; z++) {
                    server.overworld().setChunkForced(x, z, true); server.overworld().getChunk(x, z);
                } }
                consumers.loadRecipient();
                initialized = true;
            }
            if (age < 30 || !loaded()) { return; }
            if (!begun) {
                begun = true;
                if (stage.equals("start")) {
                    if (neverSaved && !consumers.vanilla()) { server.saveEverything(true, true, true); }
                    if (consumers.vanilla()) { consumers.prepareVanilla(); }
                    else { setup(); }
                    consumers.prepareRecipient();
                    if (!neverSaved || consumers.vanilla()) { server.saveEverything(true, true, true); observe("C0_BASELINE_SAVED"); }
                    else { observe("C0_D_NEVER_SAVED"); }
                    if (consumers.vanilla()) {
                        var data = RocketTransactionSavedData.get(server);
                        if (!data.acknowledgedCurrent()) { data.requestCheckpoint(server); }
                    } else {
                        transaction = RocketRuntime.beginSealedForReleaseTest(rocket, result -> {
                            completion = result.name(); observe("COMPLETE_" + result);
                        });
                        require(transaction != null, "Native sealed admission refused: " + admissionDiagnostic());
                    }
                } else {
                    var data = RocketTransactionSavedData.get(server);
                    // W129aM (ADR-074): a request; stable() still waits for the acknowledgement it publishes later.
                    if (data.entries().isEmpty() && !data.acknowledgedCurrent()) { data.requestCheckpoint(server); }
                    observe("RECOVERY_LOADED");
                }
            }
            if (age > 1200) { throw new IllegalStateException("Native fixture deadline: " + diagnostic()); }
            if (age > 60 && stable()) {
                if (stage.equals("start") && (boundary.equals("NONE") || consumers.active())) {
                    require(consumers.vanilla() || "SEALED".equals(completion), "Normal transaction did not complete SEALED: " + completion);
                    require(server.overworld().getEntity(id("entity")) == null, "Normal completion retained D");
                    if (consumers.active()) { consumers.tick(); return; }
                }
                if (++settled >= 30) {
                    require(stage.equals("start") && boundary.equals("NONE") || !stage.equals("start"), "Cut was not reached: " + boundary);
                    server.saveEverything(true, true, true); done = true; write("STABLE", diagnostic());
                    AdvancedRocketryCommunity.LOGGER.info("W152_NATIVE_DONE status=STABLE");
                }
            } else { settled = 0; }
        } catch (RuntimeException failure) {
            done = true; write("FAIL", failure.toString());
            AdvancedRocketryCommunity.LOGGER.info("W152_NATIVE_DONE status=FAIL");
        }
    }
    private boolean loaded() {
        for (int x = 0; x <= 1; x++) { for (int z = 0; z <= 1; z++) {
            if (!server.overworld().areEntitiesLoaded(new ChunkPos(x, z).toLong())) { return false; }
        } }
        return true;
    }
    private void setup() {
        var level = server.overworld(); var blocks = new ArrayList<RocketBlock>();
        for (int x = 0; x < 2; x++) { for (int z = 0; z < 2; z++) {
            var at = ORIGIN.offset(x, 0, z); level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
            var state = x == z ? Blocks.IRON_BLOCK.defaultBlockState() : x == 1 ? Blocks.CHEST.defaultBlockState() : Blocks.BARREL.defaultBlockState();
            RocketBlockEntityPayload payload = null;
            if (x != z) {
                var container = new ChestBlockEntity(at, Blocks.CHEST.defaultBlockState());
                var item = new ItemStack(Items.DIAMOND, 7); item.getOrCreateTag().putString("W152", "original");
                container.setItem(0, item); payload = new VanillaContainerRocketAdapter().capture(container);
            }
            blocks.add(new RocketBlock(new RocketPosition(x, 0, z), RocketBlockStateAdapter.capture(state), payload));
        } }
        if (allHosts) {
            for (int z = 0; z < 2; z++) { level.setBlock(ORIGIN.offset(2, 0, z), Blocks.AIR.defaultBlockState(), 3); }
            W152NativeResourceHosts.add(blocks, id("owner"));
        }
        var snapshot = RocketStructureSnapshot.create(id("snapshot"), level.dimension().location(),
                new RocketPosition(ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ()), blocks, List.of(new RocketPosition(0, 0, 0)),
                new RocketStats(blocks.size(), blocks.size() * 100, 1000, 1000, 1, 1, 1, allHosts ? 4 : 2), level.getGameTime());
        rocket = ModEntities.ROCKET.get().create(level); require(rocket != null, "Missing rocket type");
        rocket.setUUID(id("entity")); rocket.initialize(snapshot, id("logical"), id("owner"));
        RocketTransferEntities.positionAtOrigin(rocket, snapshot.sourceOrigin());
        require(level.addFreshEntity(rocket), "D publication failed");
    }
    private boolean stable() {
        var data = RocketTransactionSavedData.get(server);
        if (!data.operational() || !data.entries().isEmpty() || !data.acknowledgedCurrent()) { return false; }
        for (int x = 0; x < (allHosts ? 3 : 2); x++) { for (int z = 0; z < 2; z++) {
            if (server.overworld().getBlockEntity(ORIGIN.offset(x, 0, z)) instanceof SealedRocketBlockEntity) { return false; }
        } }
        var survivor = server.overworld().getEntity(id("entity"));
        return !(survivor instanceof RocketEntity entity) || !RocketRuntime.sealedFrozen(entity);
    }
    private String diagnostic() {
        return transaction == null ? "recovery" : RocketRuntime.sealedDiagnosticForReleaseTest(server, transaction);
    }
    private String admissionDiagnostic() {
        try {
            var service = field(RocketRuntime.class, "service").get(null);
            var coordinator = field(RocketManager.class, "sealed").get(service);
            var budget = field(SealedDisassemblyCoordinator.class, "budget").get(coordinator);
            return "remaining=" + field(budget.getClass(), "remaining").get(budget)
                    + ",loaded=" + RocketTransferEntities.loadedEntity(rocket) + ",operational=" + rocket.operational()
                    + ",claimed=" + RocketRuntime.sealedFrozen(rocket);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private void write(String status, String detail) {
        var report = new LinkedHashMap<String, Object>();
        report.put("source", System.getProperty("advancedrocketrycommunity.w152Commit"));
        report.put("stage", stage); report.put("boundary", boundary); report.put("status", status); report.put("detail", detail);
        report.put("initial_d", neverSaved ? "NEVER_SAVED" : "SAVED");
        report.put("payload_hosts", allHosts ? "ALL" : "CONTAINERS");
        report.put("tick", age); report.put("events", List.copyOf(events)); report.put("entity", id("entity").toString());
        try { Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("w152-native.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    private void decorateWriter() {
        try {
            var service = field(RocketRuntime.class, "service").get(null);
            var coordinator = field(RocketManager.class, "sealed").get(service);
            var requests = field(SealedDisassemblyCoordinator.class, "writes").get(coordinator);
            var portField = field(SealedJournalRequests.class, "port");
            var original = (SealedJournalRequests.Port) portField.get(requests);
            portField.set(requests, new SealedJournalRequests.Port() {
                public long now() { return original.now(); }
                public boolean operational() { return original.operational(); }
                public boolean current() { return original.current(); }
                public boolean request() { return requestCheckedWrite(); }
                public boolean writePending() { return original.writePending(); }
                public CheckedJournalCheckpoint.Outcome outcome() { return original.outcome(); }
                public void written(CheckedJournalCheckpoint.Outcome outcome) { checkedWritten(outcome); original.written(outcome); }
            });
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    /**
     * W129aM (ADR-074): the journal writes off-thread, so the cut policy's committer and reader run on its IO thread and
     * the _MOVE_ cuts pause there. The outcome is published on a later owner tick, where the _UNKNOWN cut pauses.
     */
    private boolean requestCheckedWrite() {
        var type = ManagedSavedDataType.ROCKET_TRANSACTIONS; var atomic = CheckedSavedDataPolicy.atomic(type);
        String phase = writerPhase;
        boolean unknown = boundary.equals(phase + "_UNKNOWN");
        var policy = new CheckedSavedDataPolicy(type, (staged, target) -> {
            observe(phase + "_MOVE_BEFORE"); atomic.committer().commit(staged, target); observe(phase + "_MOVE_AFTER");
            if (unknown) { throw new IOException("W152 test-only post-move fault"); }
        }, (file, kind) -> {
            if (unknown) { throw new IllegalStateException("W152 test-only readback fault"); }
            return atomic.reader().read(file, kind);
        });
        try {
            var method = RocketTransactionSavedData.class.getDeclaredMethod("requestWrite", java.nio.file.Path.class, CheckedSavedDataPolicy.class);
            method.setAccessible(true);
            boolean accepted = (boolean) method.invoke(RocketTransactionSavedData.get(server), ManagedRootSupplier.dataFile(server, type), policy);
            if (accepted) { requestedPhase = phase; }
            return accepted;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private void checkedWritten(CheckedJournalCheckpoint.Outcome result) {
        String phase = requestedPhase;
        observe(phase + "_WRITE_" + result);
        if (boundary.equals(phase + "_UNKNOWN")) {
            require(result == CheckedJournalCheckpoint.Outcome.UNKNOWN, "Fault did not produce UNKNOWN"); pause(phase + "_UNKNOWN");
        }
    }
    static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        var result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (event.getServer() == server) { MinecraftForge.EVENT_BUS.unregister(this); }
    }
}
