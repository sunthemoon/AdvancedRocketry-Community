package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Gated disposable native-server cuts. Fixtures save only the BEFORE baseline, never an applied endpoint. */
final class EscrowCargoReconciliationProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final String name, cut;
    private final boolean load, recovery, item;
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile boolean initialized, done;
    private ResourceEscrowSavedData data;
    private EscrowCargoProbeDevice device;
    private RocketEntity rocket;
    private long began;
    private int step;
    private volatile String selected = "";
    private volatile int stores;
    private final EscrowCargoStorageProbe storage;
    private final EscrowCargoContentionProbe contention;
    private final EscrowCargoShutdownProbe shutdown;
    private final EscrowCargoRecoveryProbe recoveryProbe;

    static boolean accepts(String name) { return name.startsWith("FC_") || name.startsWith("IC_"); }

    EscrowCargoReconciliationProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        name = System.getProperty("advancedrocketrycommunity.escrowCase");
        item = name.startsWith("IC_");
        load = name.startsWith(item ? "IC_LOAD_" : "FC_LOAD_");
        EscrowTags.require(load || name.startsWith(item ? "IC_UNLOAD_" : "FC_UNLOAD_"), "Unknown cargo case");
        cut = name.substring(load ? 8 : 10);
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        storage = EscrowCargoStorageProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery)
                ? new EscrowCargoStorageProbe(server, name, this::pause) : null;
        contention = EscrowCargoContentionProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name)
                ? new EscrowCargoContentionProbe(server, name, recovery, this::pause) : null;
        shutdown = EscrowCargoShutdownProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery)
                ? new EscrowCargoShutdownProbe(server, name) : null;
        recoveryProbe = EscrowCargoRecoveryProbe.selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name)
                ? new EscrowCargoRecoveryProbe(server, name, recovery, this::pause) : null;
    }
    void checkpoint(String label) {
        if (recovery || !initialized || done || paused.get()) { return; }
        if (label.equals("CARGO_STORE_SOURCE_SELECTED")) { selected = "SOURCE"; stores++; }
        if (label.equals("CARGO_STORE_DESTINATION_SELECTED")) { selected = "DESTINATION"; stores++; }
        if (contention != null) { contention.checkpoint(label); }
        if (shutdown != null) { shutdown.checkpoint(label, this::pause); }
        if (recoveryProbe != null) { recoveryProbe.checkpoint(label); }
        if (storage != null) { storage.checkpoint(label, rocket, device); }
        if (EscrowCargoStorageProbe.d2Cut(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery, stores, label)) {
            pause(label); return;
        }
        if (secondForceCut(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, recovery, stores, label)) {
            pause(label); return;
        }
        if ((cut.equals("SOURCE_ONLY_STORE") || cut.equals("DESTINATION_ONLY_STORE")) && label.equals("BARRIER_FORCE_AFTER")) {
            EscrowTags.require(cut.equals(selected + "_ONLY_STORE"), "Cargo forced the wrong selected endpoint");
            pause(label); return;
        }
        if (cut.equals(label) || cut.equals("L1_BEFORE") && label.equals("K1")
                || cut.equals("L1_REPLACED") && label.equals("K2")
                || cut.equals("L2_REPLACED") && label.equals("K9")
                || cut.equals("FIRST_STORE") && label.equals("BARRIER_FORCE_AFTER")) { pause(label); }
    }
    /** Observation only: the existing second own-store has completed, but its force has not started. */
    static boolean secondForceCut(boolean hooks, String name, boolean recovery, int stores, String label) {
        return hooks && !recovery && ("FC_LOAD_SECOND_FORCE_BEFORE".equals(name)
                || "FC_UNLOAD_SECOND_FORCE_BEFORE".equals(name))
                && stores == 2 && "BARRIER_FORCE_BEFORE".equals(label);
    }
    /** Select only a probe-owned endpoint; normal runs and recovery retain the production ordering. */
    boolean permitsStore(EscrowOperation op, EscrowEndpoint endpoint) {
        if (!initialized || done || device == null || rocket == null || !op.kind().equals(kind())
                || !op.device().identity().equals(device.id())
                || !op.rocket().identity().equals(rocket.getUUID())) { return true; }
        return permitsStore(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), recovery, cut, op, endpoint);
    }
    static boolean permitsStore(boolean hooks, boolean recovery, String cut, EscrowOperation op, EscrowEndpoint endpoint) {
        if (!hooks || recovery) { return true; }
        return switch (cut) {
            case "SOURCE_ONLY_STORE" -> endpoint.key().equals(op.source().key());
            case "DESTINATION_ONLY_STORE" -> endpoint.key().equals(op.destination().key());
            default -> true;
        };
    }
    private void pause(String label) {
        if (!paused.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_PAUSED cut={} checkpoint={} selected={}", name, label, selected);
        try { java.util.concurrent.TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Cargo forced stop did not arrive within 30 seconds");
    }
    void beforeReconciliation() {
        if (device == null || recovery || !initialized || done) { return; }
        var existing = ResourceEscrowSavedData.existing(server);
        if (recoveryProbe != null) {
            if (!recoveryProbe.admit()) { return; }
            recoveryProbe.beforeReconciliation(existing, rocket, device);
        }
        if (contention != null) { contention.beforeReconciliation(existing); }
        if (existing != null && existing.ledger().records().stream().anyMatch(op -> op.device().identity().equals(device.id()))) {
            EscrowCargoSwitches.forTests(server, kind(), device.id(), false);
            if (cut.equals("L1_ACK")) { pause("L1_ACK"); }
        }
    }
    void tick() {
        var level = server.overworld();
        if (done || paused.get() || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong()) || !level.isPositionEntityTicking(POSITION)) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); data.bind(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); data.bind(server).autosave(); }
        }
        var coordinator = data.bind(server);
        if (!RocketTransferSavedData.get(server).retryChecked(server) || !coordinator.operational()) { return; }
        EscrowTags.require(server.getTickCount() - began <= 2_000, "Cargo restart fixture exceeded its bounded wait");
        if (recovery) { recover(); return; }
        if (step == 0) {
            if (data.ledger().writerGeneration() == 0 || !coordinator.writerIdle()) { coordinator.autosave(); return; }
            if (item) {
                EscrowTags.require(!data.isDirty(), "Item baseline has no COMMITTED acknowledgement");
                EscrowLedgerBaseline.require(ResourceEscrowSavedData.file(server), data.ledger());
            }
            var snapshot = item ? ItemCargoReconciliationFixtures.snapshot(level, POSITION.offset(4, 0, 0), load)
                    : EscrowCargoPreparationGameTests.snapshot(level.dimension().location(), POSITION.offset(4, 0, 0), false,
                    List.of(load ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000)));
            rocket = ModEntities.ROCKET.get().create(level);
            EscrowTags.require(rocket != null, "Cargo rocket fixture creation failed");
            rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
            EscrowTags.require(level.addFreshEntity(rocket), "Cargo rocket fixture publication failed");
            device = EscrowCargoProbeDevice.create(level, POSITION, rocket, load, item);
            if (contention != null) { contention.place(); }
            if (recoveryProbe != null) { recoveryProbe.place(data, rocket, device); }
            EscrowTags.require(server.saveEverything(false, true, true), "Cargo BEFORE native baseline refused");
            initialized = true;
            if (recoveryProbe == null || recoveryProbe.admit()) { EscrowCargoSwitches.forTests(server, kind(), device.id(), true); }
            step = 1;
        }
        if (cut.equals("PREPARING") && EscrowRuntime.of(server).cargoPreparing(rocket.assemblyTransactionId().orElseThrow())) { pause("PREPARING"); }
        if (storage != null) { storage.tick(data); }
        if (contention != null) { contention.tick(data); }
        if (recoveryProbe != null) { recoveryProbe.tick(data, rocket, device); }
        EscrowTags.require(data.ledger().records().stream().noneMatch(op -> op.reason().manual()), "Native cargo entered repair");
    }
    private EscrowOperation.Kind kind() {
        return item ? (load ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD)
                : (load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD);
    }
    private void recover() {
        var level = server.overworld(); var coordinator = data.bind(server);
        if (rocket == null) {
            var found = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(12));
            if (found.isEmpty()) { return; }
            device = EscrowCargoProbeDevice.existing(level, EscrowCargoRecoveryProbe.devicePosition(name, true), item);
            if (recoveryProbe != null && recoveryProbe.duplicate()) {
                var physical = device.fluidEntity().root().orElseThrow().binding().physicalRocket();
                found = found.stream().filter(entity -> entity.getUUID().equals(physical)).toList();
            }
            EscrowTags.require(found.size() == 1, "Cargo restart duplicated its rocket"); rocket = found.get(0);
        }
        if (recoveryProbe != null) { done = recoveryProbe.recover(data, rocket, device, server.getTickCount() - began); return; }
        EscrowTags.require(data.ledger().records().stream().noneMatch(op -> op.reason().manual()), "Restart cargo entered repair");
        if (name.equals(EscrowCargoStorageProbe.EXTERNAL)) {
            if (server.getTickCount() - began >= 100 && EscrowRuntime.of(server).barrier().refusals() > 0) {
                EscrowCargoStorageProbe.heldRestart(server, data, rocket, device, name); done = true;
            }
            return;
        }
        if (server.getTickCount() - began < 100 || !data.ledger().records().isEmpty() || !coordinator.writerIdle()) { return; }
        boolean before = cut.equals("PREPARING") || cut.equals("L1_BEFORE") || cut.startsWith("E4L1_")
                || name.equals(EscrowCargoShutdownProbe.L1); int epoch = before ? 0 : 1;
        var section = rocket.escrowSection();
        var payload = rocket.snapshot().orElseThrow().blocks().stream().map(block -> block.blockEntityPayload().orElse(null))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        int amount = item ? ItemCargoReconciliationFixtures.amount(rocket) : PressurizedTankRocketAdapter.fluid(payload.data()).getAmount();
        int expected = item ? (load ? 60 + epoch * 4 : 10 - epoch * 4) : (load ? epoch * 1_000 : 5_000 - epoch * 1_000);
        EscrowTags.require(amount == expected
                && device.amount() + amount == (item ? 70 : 5_000) && section.epoch() == epoch && device.section().epoch() == epoch
                && section.markers().isEmpty() && device.section().markers().isEmpty()
                && !coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow())
                && data.ledger().issuedThrough() == (name.equals(EscrowCargoContentionProbe.E5) ? 2 : epoch)
                && data.ledger().floors().isEmpty() && data.ledger().tombstones().isEmpty()
                && RocketTransferSavedData.get(server).entries().isEmpty(), "Restart violated cargo conservation, epoch, identity or pruning");
        if (contention != null) { contention.verifyHealthy(); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_RESULT cut={} cargo={} device={} epoch={} records=0", name, amount, device.amount(), epoch);
        done = true;
    }
    void close() { if (storage != null) { storage.close(); } }
}
