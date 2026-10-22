package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraftforge.fluids.FluidStack;

/** Disposable native E3/E5 fixtures. Cache failure is propagated to an own store, not a durability guarantee. */
final class EscrowCargoContentionProbe {
    static final String E3 = "FC_LOAD_E3_CACHE_CLOSE", E5 = "FC_LOAD_E5_STALLED";
    private static final BlockPos HEALTHY_DEVICE = new BlockPos(88, 100, 88);
    private final MinecraftServer server;
    private final String name;
    private final boolean recovery;
    private final Consumer<String> pause;
    private RocketEntity healthy;
    private EscrowCargoProbeDevice device;
    private boolean injected;

    static boolean selected(boolean hooks, String name) { return hooks && (E3.equals(name) || E5.equals(name)); }
    EscrowCargoContentionProbe(MinecraftServer server, String name, boolean recovery, Consumer<String> pause) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.name = name; this.recovery = recovery; this.pause = pause;
    }
    void place() {
        if (!name.equals(E5)) { return; }
        var level = server.overworld();
        healthy = ModEntities.ROCKET.get().create(level);
        EscrowTags.require(healthy != null && level.isPositionEntityTicking(HEALTHY_DEVICE), "Healthy cargo fixture chunk is unavailable");
        healthy.initialize(EscrowCargoPreparationGameTests.snapshot(level.dimension().location(), HEALTHY_DEVICE.offset(4, 0, 0), false,
                List.of(FluidStack.EMPTY)), UUID.randomUUID(), UUID.randomUUID());
        EscrowTags.require(level.addFreshEntity(healthy), "Healthy cargo fixture publication failed");
        device = EscrowCargoProbeDevice.create(level, HEALTHY_DEVICE, healthy, true, false);
    }
    void start() {
        if (name.equals(E5)) { EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, device.id(), true); return; }
        // Fixed disposable storage fixtures; no Level chunks or production endpoints are loaded for these writes.
        var worker = server.overworld().getChunkSource().chunkMap.worker;
        var writes = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 256; i++) {
            var pos = new ChunkPos((8 + i) * 32, 256);
            var root = NbtUtils.addCurrentDataVersion(new CompoundTag());
            root.putInt("xPos", pos.x); root.putInt("zPos", pos.z); root.putString("Status", "minecraft:empty");
            root.put("sections", new ListTag()); root.put("block_entities", new ListTag());
            writes.add(worker.store(pos, root));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join(); worker.synchronize(true).join();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_CACHE_SEEDED cut={} regions=256", name);
    }
    void beforeReconciliation(ResourceEscrowSavedData data) {
        if (recovery || device == null || data == null) { return; }
        if (data.ledger().records().stream().anyMatch(op -> op.device().identity().equals(device.id()))) {
            EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, device.id(), false);
        }
    }
    void checkpoint(String label) {
        if (recovery || injected || !name.equals(E3) || !label.equals("C4_SOURCE_SELECTED")) { return; }
        injected = true;
        int size = closeOldest(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), server.overworld().getChunkSource().chunkMap.worker);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_CACHE_CLOSED cut={} regions={} own_region_cached=false", name, size);
    }
    /** Closes only the oldest seeded fixture channel; the unchanged vanilla eviction and store report its failure. */
    static int closeOldest(boolean gate, IOWorker worker) {
        if (!gate) { return 0; }
        try {
            var storageField = java.util.Arrays.stream(IOWorker.class.getDeclaredFields())
                    .filter(f -> f.getType() == RegionFileStorage.class).findFirst().orElseThrow();
            storageField.setAccessible(true); var storage = storageField.get(worker);
            var cacheField = java.util.Arrays.stream(RegionFileStorage.class.getDeclaredFields())
                    .filter(f -> f.getType() == it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap.class).findFirst().orElseThrow();
            cacheField.setAccessible(true);
            var cache = (it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<?>) cacheField.get(storage);
            EscrowTags.require(cache.size() == 256 && !cache.containsKey(ChunkPos.asLong(0, 0)), "E3 cache fixture changed before its own store");
            long key = cache.lastLongKey();
            EscrowTags.require(ChunkPos.getX(key) >= 8 && ChunkPos.getX(key) < 264 && ChunkPos.getZ(key) == 8,
                    "E3 selected a non-fixture cached region");
            var channel = java.util.Arrays.stream(RegionFile.class.getDeclaredFields())
                    .filter(f -> f.getType() == java.nio.channels.FileChannel.class).findFirst().orElseThrow();
            channel.setAccessible(true); ((java.nio.channels.FileChannel) channel.get(cache.get(key))).close();
            return cache.size();
        } catch (ReflectiveOperationException | java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    void tick(ResourceEscrowSavedData data) {
        if (!name.equals(E5) || recovery || data.ledger().records().size() != 1 || !data.bind(server).writerIdle()) { return; }
        var op = data.ledger().records().get(0);
        if (op.phase() != EscrowOperation.Phase.STALLED || op.reason() != EscrowReason.BARRIER_IO) { return; }
        verifyHealthy();
        EscrowTags.require(EscrowRuntime.of(server).barrier().ioFailures() == 3 && data.ledger().issuedThrough() == 2,
                "E5 did not observe exactly three own-store failures and two admissions");
        EscrowTags.require(server.saveEverything(false, true, true), "E5 STALLED baseline save refused");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_FAIR_PROGRESS cut={} stalled=1 healthy_retired=1 io=3 issued=2", name);
        pause.accept("STALLED_RESTART");
    }
    void verifyHealthy() {
        if (!name.equals(E5)) { return; }
        if (healthy == null) {
            var found = server.overworld().getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(HEALTHY_DEVICE.offset(4, 0, 0)).inflate(2));
            EscrowTags.require(found.size() == 1, "Healthy cargo rocket missing or duplicated"); healthy = found.get(0);
            device = EscrowCargoProbeDevice.existing(server.overworld(), HEALTHY_DEVICE, false);
        }
        var payload = healthy.snapshot().orElseThrow().blocks().get(0).blockEntityPayload().orElseThrow();
        EscrowTags.require(device.amount() == 4_000 && device.section().epoch() == 1 && device.section().markers().isEmpty()
                && healthy.escrowSection().epoch() == 1 && healthy.escrowSection().markers().isEmpty()
                && io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.fluid(payload.data()).getAmount() == 1_000
                && !ResourceEscrowSavedData.existing(server).bind(server).logicalLocked(healthy.assemblyTransactionId().orElseThrow()),
                "Held cargo blocked or changed its healthy independent component");
    }
}
