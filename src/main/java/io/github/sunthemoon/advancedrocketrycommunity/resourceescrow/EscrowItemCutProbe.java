package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.*;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** W143's disposable item fixtures. No ordinary launch attaches this probe or wraps a journal writer. */
public abstract class EscrowItemCutProbe {
    static final String CASE = "advancedrocketrycommunity.escrowCase", SCENARIO = "advancedrocketrycommunity.escrowScenario";
    static final BlockPos DEVICE = new BlockPos(64, 100, 64), ROCKET = new BlockPos(68, 100, 64), PAD = new BlockPos(80, 100, 64);
    static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    static final RocketPropulsion PROPULSION = new RocketPropulsion(
            RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    private static final AtomicBoolean STOPPED = new AtomicBoolean();
    final MinecraftServer server;
    final ServerLevel level;
    final String name;
    final boolean recovery;
    boolean done;

    EscrowItemCutProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), System.getProperty(CASE)),
                "Item cut probe requires its own case");
        this.server = server; level = server.overworld();
        name = System.getProperty(CASE); recovery = "recover".equals(System.getProperty(SCENARIO));
        for (var pos : List.of(DEVICE, PAD)) {
            var chunk = new ChunkPos(pos);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z);
        }
    }
    static boolean selected(boolean hooks, String scenario, String name) {
        return hooks && ("start".equals(scenario) || "recover".equals(scenario)) && name != null
                && (EscrowItemSettlementCuts.CASES.containsKey(name) || EscrowItemRecoveryCuts.CASES.contains(name));
    }
    static EscrowItemCutProbe attach(MinecraftServer server, EscrowItemCutProbe existing) {
        if (existing != null || !selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), System.getProperty(CASE))) { return existing; }
        return System.getProperty(CASE).startsWith("IS_") ? new EscrowItemSettlementCuts(server) : new EscrowItemRecoveryCuts(server);
    }
    abstract void checkpoint(String label);
    abstract void tick();
    void beforeReconciliation() { }
    boolean permitsStore(EscrowOperation operation, EscrowEndpoint endpoint) { return true; }
    boolean stopped() { return STOPPED.get(); }
    void pause(String boundary) { hardStop(name, boundary); }
    static void hardStop(String name, String boundary) {
        if (!STOPPED.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ITEM_CUT_PAUSED cut={} boundary={}", name, boundary);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("W143 forced stop did not arrive within 30 seconds");
    }
    void result(String state, String detail) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ITEM_CUT_RESULT cut={} state={} {}", name, state, detail); done = true;
    }
    boolean loaded() {
        return List.of(DEVICE, PAD).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    void save() {
        EscrowTags.require(server.saveEverything(false, true, true), "W143 baseline save refused");
        level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
    }
    /** Requires the owner-thread COMMITTED generation and exact durable readback, not merely an idle writer. */
    static boolean baseline(ResourceEscrowSavedData data, MinecraftServer server) {
        var coordinator = data.bind(server);
        if (data.ledger().writerGeneration() == 0 || data.isDirty() || !coordinator.writerIdle()) { coordinator.autosave(); return false; }
        EscrowLedgerBaseline.require(ResourceEscrowSavedData.file(server), data.ledger()); return true;
    }
    /** A fixture may proceed only with its own admitted item operation, never an unrelated PREPARED record. */
    static EscrowOperation preparedItem(EscrowLedger ledger, RocketEntity rocket, RocketItemCargoBlockEntity device) {
        if (ledger.records().isEmpty()) { return null; }
        EscrowTags.require(ledger.records().size() == 1, "Item cut fixture has extra operations");
        var op = ledger.records().get(0);
        var kind = device.root().orElseThrow().role() == RocketCargoRole.LOAD
                ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD;
        requirePreparedItem(op, kind, id(device), rocket.getUUID(), rocket.assemblyTransactionId().orElseThrow(),
                rocket.ownerId().orElseThrow(), rocket.snapshot().orElseThrow().snapshotId());
        return op;
    }
    static void requirePreparedItem(EscrowOperation op, EscrowOperation.Kind kind, UUID device, UUID physical,
            UUID logical, UUID owner, UUID snapshot) {
        EscrowTags.require(kind.itemCargo() && op.kind() == kind && op.phase() == EscrowOperation.Phase.PREPARED
                && op.reason() == EscrowReason.NONE && op.nativeBinding().complete()
                && op.device().identity().equals(device) && op.rocket().identity().equals(physical)
                && op.rocket().logicalRocket().equals(logical) && op.owner().equals(owner)
                && snapshot.equals(op.nativeBinding().snapshot()), "Item cut fixture lacks its own native PREPARED item operation");
    }
    RocketEntity cargoRocket(int count) { return cargoRocket(level, ROCKET, count); }
    static RocketEntity cargoRocket(ServerLevel level, BlockPos origin, int count) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        var entries = new ListTag(); var stack = ItemCargoReconciliationFixtures.stack(count); stack.putByte("Slot", (byte) 0); entries.add(stack);
        var snapshot = ItemCargoReconciliationFixtures.snapshot(level, origin, entries);
        // Native flights use legal production propulsion, unlike the small admission-only fixtures' water bank.
        snapshot = RocketStructureSnapshot.create(snapshot.snapshotId(), snapshot.sourceDimension(), snapshot.sourceOrigin(), snapshot.blocks(),
                snapshot.passengerAnchors(), snapshot.stats(), snapshot.createdAtGameTime(),
                PROPULSION);
        var rocket = java.util.Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(cargoFuel(flight.fuel()), level.getGameTime()));
        EscrowTags.require(level.addFreshEntity(rocket), "Item cut fixture publication failed");
        return rocket;
    }
    static RocketFuelState cargoFuel(RocketFuelState fuel) {
        return fuel.withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000);
    }
    RocketItemCargoBlockEntity device(BlockPos at, RocketEntity rocket, RocketCargoRole role, int count) {
        level.setBlockAndUpdate(at, (role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER : ModBlocks.ROCKET_ITEM_UNLOADER).get().defaultBlockState());
        var device = (RocketItemCargoBlockEntity) java.util.Objects.requireNonNull(level.getBlockEntity(at));
        var slots = List.of(ItemCargoReconciliationFixtures.stack(count), new CompoundTag(), new CompoundTag(), new CompoundTag());
        var root = RocketItemCargoRoot.placed(rocket.ownerId().orElseThrow(), UUID.randomUUID(), role)
                .withBuffer(new EscrowItemBuffer(slots)).withBinding(FluidFuelLoaderEscrowFixtures.binding(rocket));
        var tag = new CompoundTag(); tag.put(RocketItemCargoRoot.KEY, root.encode()); device.load(tag); return device;
    }
    RocketItemCargoBlockEntity existingDevice(BlockPos at) { return level.getBlockEntity(at) instanceof RocketItemCargoBlockEntity value ? value : null; }
    List<RocketEntity> rockets() { return level.getEntitiesOfClass(RocketEntity.class, new AABB(60, 90, 56, 96, 110, 80)); }
    List<RocketEntity> allRockets() { return level.getEntitiesOfClass(RocketEntity.class, new AABB(60, level.getMinBuildHeight(), 56, 96, level.getMaxBuildHeight(), 80)); }
    static int cargo(RocketEntity rocket) { return ItemCargoReconciliationFixtures.amount(rocket); }
    static UUID id(RocketItemCargoBlockEntity device) { return device.root().orElseThrow().device(); }
    static int tank(RocketItemCargoBlockEntity device) { return ItemCargoReconciliationFixtures.amount(device.root().orElseThrow().buffer()); }
    static RocketEscrowSection section(RocketItemCargoBlockEntity device) { return device.root().orElseThrow().escrow(); }

    static boolean journalCut(boolean hooks, String scenario, String name) {
        return hooks && "start".equals(scenario) && "IS_O3J_AFTER".equals(name);
    }
    public static CheckedSavedDataPolicy journalPolicy(MinecraftServer server, CheckedSavedDataPolicy policy) {
        String name = System.getProperty(CASE);
        if (server == null || !journalCut(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), name)) { return policy; }
        EscrowTestEndpoints.requireEnabled(server);
        return policy.withCommitter((staged, target) -> {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            var previous = CheckedSavedDataFile.readPayload(target, type).orElse(null);
            var candidate = CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
            boolean removal = previous != null && !previous.getList("transfers", 10).isEmpty() && candidate.getList("transfers", 10).isEmpty();
            policy.committer().commit(staged, target);
            if (removal) { hardStop(name, "O3J_REPLACED"); }
        });
    }
}
