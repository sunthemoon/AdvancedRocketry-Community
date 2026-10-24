package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
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
    private final ConcurrentLinkedQueue<Map<String, Object>> events = new ConcurrentLinkedQueue<>();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile int age;
    private volatile String writerPhase = "INITIAL";
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
            if (label.equals("C2_STAGE_AFTER") && ++placed == 1 && boundary.equals("PARTIAL_C_SAVED")) {
                server.saveEverything(true, true, true); pause("PARTIAL_C_SAVED");
            }
        }
        if (label.equals(boundary)) { pause(label); }
    }
    private void pause(String label) {
        if (stage.equals("recover2") || !paused.compareAndSet(false, true)) { return; }
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
                    if (consumers.vanilla()) { consumers.prepareVanilla(); }
                    else { setup(); }
                    consumers.prepareRecipient();
                    server.saveEverything(true, true, true); observe("C0_BASELINE_SAVED");
                    if (consumers.vanilla()) {
                        var data = RocketTransactionSavedData.get(server);
                        if (!data.acknowledgedCurrent()) { data.checkpoint(server); }
                    } else {
                        transaction = RocketRuntime.beginSealedForReleaseTest(rocket, result -> {
                            completion = result.name(); observe("COMPLETE_" + result);
                        });
                        require(transaction != null, "Native sealed admission refused: " + admissionDiagnostic());
                    }
                } else {
                    var data = RocketTransactionSavedData.get(server);
                    if (data.entries().isEmpty() && !data.acknowledgedCurrent()) { data.checkpoint(server); }
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
        var snapshot = RocketStructureSnapshot.create(id("snapshot"), level.dimension().location(),
                new RocketPosition(ORIGIN.getX(), ORIGIN.getY(), ORIGIN.getZ()), blocks, List.of(new RocketPosition(0, 0, 0)),
                new RocketStats(4, 400, 1000, 1000, 1, 1, 1, 2), level.getGameTime());
        rocket = ModEntities.ROCKET.get().create(level); require(rocket != null, "Missing rocket type");
        rocket.setUUID(id("entity")); rocket.initialize(snapshot, id("logical"), id("owner"));
        RocketTransferEntities.positionAtOrigin(rocket, snapshot.sourceOrigin());
        require(level.addFreshEntity(rocket), "D publication failed");
    }
    private boolean stable() {
        var data = RocketTransactionSavedData.get(server);
        if (!data.operational() || !data.entries().isEmpty() || !data.acknowledgedCurrent()) { return false; }
        for (int x = 0; x < 2; x++) { for (int z = 0; z < 2; z++) {
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
                public CheckedJournalCheckpoint.Outcome checkpoint() { return checkedWrite(); }
            });
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private CheckedJournalCheckpoint.Outcome checkedWrite() {
        var type = ManagedSavedDataType.ROCKET_TRANSACTIONS; var atomic = CheckedSavedDataPolicy.atomic(type);
        String phase = writerPhase;
        boolean unknown = boundary.equals(phase + "_UNKNOWN") && stage.equals("start");
        var policy = new CheckedSavedDataPolicy(type, (staged, target) -> {
            observe(phase + "_MOVE_BEFORE"); atomic.committer().commit(staged, target); observe(phase + "_MOVE_AFTER");
            if (unknown) { throw new IOException("W152 test-only post-move fault"); }
        }, (file, kind) -> {
            if (unknown) { throw new IllegalStateException("W152 test-only readback fault"); }
            return atomic.reader().read(file, kind);
        });
        try {
            var method = RocketTransactionSavedData.class.getDeclaredMethod("saveChecked", java.nio.file.Path.class, CheckedSavedDataPolicy.class);
            method.setAccessible(true);
            var result = (CheckedJournalCheckpoint.Outcome) method.invoke(RocketTransactionSavedData.get(server),
                    server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(type.fileName()), policy);
            observe(phase + "_WRITE_" + result);
            if (unknown) { require(result == CheckedJournalCheckpoint.Outcome.UNKNOWN, "Fault did not produce UNKNOWN"); pause(phase + "_UNKNOWN"); }
            return result;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static java.lang.reflect.Field field(Class<?> type, String name) throws NoSuchFieldException {
        var result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
    @SubscribeEvent public void stopping(ServerStoppingEvent event) {
        if (event.getServer() == server) { MinecraftForge.EVENT_BUS.unregister(this); }
    }
}
