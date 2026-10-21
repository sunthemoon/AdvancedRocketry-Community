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
    private final boolean load, recovery;
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile boolean initialized, done;
    private ResourceEscrowSavedData data;
    private RocketFluidCargoBlockEntity device;
    private RocketEntity rocket;
    private long began;
    private int step;
    private volatile String selected = "";

    EscrowCargoReconciliationProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        name = System.getProperty("advancedrocketrycommunity.escrowCase");
        load = name.startsWith("FC_LOAD_");
        EscrowTags.require(load || name.startsWith("FC_UNLOAD_"), "Unknown fluid cargo case");
        cut = name.substring(load ? 8 : 10);
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
    }
    void checkpoint(String label) {
        if (recovery || !initialized || done || paused.get()) { return; }
        if (label.equals("CARGO_STORE_SOURCE_SELECTED")) { selected = "SOURCE"; }
        if (label.equals("CARGO_STORE_DESTINATION_SELECTED")) { selected = "DESTINATION"; }
        if ((cut.equals("SOURCE_ONLY_STORE") || cut.equals("DESTINATION_ONLY_STORE")) && label.equals("BARRIER_FORCE_AFTER")) {
            EscrowTags.require(cut.equals(selected + "_ONLY_STORE"), "Cargo forced the wrong selected endpoint");
            pause(label); return;
        }
        if (cut.equals(label) || cut.equals("L1_BEFORE") && label.equals("K1")
                || cut.equals("L1_REPLACED") && label.equals("K2")
                || cut.equals("L2_REPLACED") && label.equals("K9")
                || cut.equals("FIRST_STORE") && label.equals("BARRIER_FORCE_AFTER")) { pause(label); }
    }
    /** Select only a probe-owned endpoint; normal runs and recovery retain the production ordering. */
    boolean permitsStore(EscrowOperation op, EscrowEndpoint endpoint) {
        if (!initialized || done || device == null || rocket == null || !op.kind().equals(kind())
                || !op.device().identity().equals(device.root().orElseThrow().device())
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
        if (existing != null && !existing.ledger().records().isEmpty()) {
            EscrowCargoSwitches.forTests(server, kind(), device.root().orElseThrow().device(), false);
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
            var snapshot = EscrowCargoPreparationGameTests.snapshot(level.dimension().location(), POSITION.offset(4, 0, 0), false,
                    List.of(load ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000)));
            rocket = ModEntities.ROCKET.get().create(level);
            EscrowTags.require(rocket != null, "Cargo rocket fixture creation failed");
            rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
            EscrowTags.require(level.addFreshEntity(rocket), "Cargo rocket fixture publication failed");
            level.setBlockAndUpdate(POSITION, (load ? ModBlocks.ROCKET_FLUID_LOADER : ModBlocks.ROCKET_FLUID_UNLOADER).get().defaultBlockState());
            device = (RocketFluidCargoBlockEntity) level.getBlockEntity(POSITION);
            var root = RocketFluidCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD,
                    load ? LoaderTank.of(new FluidStack(Fluids.WATER, 5_000)) : LoaderTank.EMPTY)
                    .withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
            var tag = new CompoundTag(); tag.put(RocketFluidCargoRoot.KEY, root.encode()); device.load(tag);
            EscrowTags.require(server.saveEverything(false, true, true), "Cargo BEFORE native baseline refused");
            initialized = true;
            EscrowCargoSwitches.forTests(server, kind(), root.device(), true); step = 1;
        }
        if (cut.equals("PREPARING") && EscrowRuntime.of(server).cargoPreparing(rocket.assemblyTransactionId().orElseThrow())) { pause("PREPARING"); }
        EscrowTags.require(data.ledger().records().stream().noneMatch(op -> op.reason().manual()), "Native cargo entered repair");
    }
    private EscrowOperation.Kind kind() { return load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD; }
    private void recover() {
        var level = server.overworld(); var coordinator = data.bind(server);
        if (rocket == null) {
            var found = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(POSITION).inflate(12));
            if (found.isEmpty()) { return; }
            EscrowTags.require(found.size() == 1, "Cargo restart duplicated its rocket"); rocket = found.get(0);
            EscrowTags.require(level.getBlockEntity(POSITION) instanceof RocketFluidCargoBlockEntity, "Cargo restart lost its device");
            device = (RocketFluidCargoBlockEntity) level.getBlockEntity(POSITION);
        }
        EscrowTags.require(data.ledger().records().stream().noneMatch(op -> op.reason().manual()), "Restart cargo entered repair");
        if (server.getTickCount() - began < 100 || !data.ledger().records().isEmpty() || !coordinator.writerIdle()) { return; }
        boolean before = cut.equals("PREPARING") || cut.equals("L1_BEFORE"); int epoch = before ? 0 : 1;
        var root = device.root().orElseThrow(); var section = rocket.escrowSection();
        var payload = rocket.snapshot().orElseThrow().blocks().stream().map(block -> block.blockEntityPayload().orElse(null))
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        int amount = PressurizedTankRocketAdapter.fluid(payload.data()).getAmount();
        EscrowTags.require(amount == (load ? epoch * 1_000 : 5_000 - epoch * 1_000)
                && root.tank().amount() + amount == 5_000 && section.epoch() == epoch && root.escrow().epoch() == epoch
                && section.markers().isEmpty() && root.escrow().markers().isEmpty()
                && !coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow())
                && data.ledger().issuedThrough() == epoch && data.ledger().floors().isEmpty() && data.ledger().tombstones().isEmpty()
                && RocketTransferSavedData.get(server).entries().isEmpty(), "Restart violated cargo conservation, epoch, identity or pruning");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_RESULT cut={} cargo={} device={} epoch={} records=0", name, amount, root.tank().amount(), epoch);
        done = true;
    }
}
