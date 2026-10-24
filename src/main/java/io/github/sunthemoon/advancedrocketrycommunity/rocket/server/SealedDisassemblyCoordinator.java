package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedJournalRequests;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server-lifetime RAM claims. The durable U is never removed merely because the caller, proof or server stops. */
final class SealedDisassemblyCoordinator {
    private record Work(SealedDisassemblyWorld world, SealedDisassembly machine, RocketRegionLockManager.LockToken lock,
                        Consumer<SealedDisassembly.Result> completion) { }
    private final RocketTransactionExecutor transactions;
    private final RocketOrdinaryRetirement ordinary;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private MinecraftServer server;
    private SealedWorkBudget budget;
    private SealedJournalRequests writes;
    private SealedCarrierPlacement placement;
    private int cursor;

    SealedDisassemblyCoordinator(RocketTransactionExecutor transactions, RocketOrdinaryRetirement ordinary) {
        this.transactions = transactions; this.ordinary = ordinary;
        ordinary.shareSealedCapacity(work::size, this::activeAttempts);
    }
    private void bind(MinecraftServer server) {
        if (!server.isSameThread() || this.server != null && this.server != server) {
            throw new IllegalStateException("Sealed executor requires its owning server thread");
        }
        if (this.server != null) { return; }
        this.server = server; budget = new SealedWorkBudget(); placement = new SealedCarrierPlacement();
        var data = RocketTransactionSavedData.get(server);
        writes = new SealedJournalRequests(new SealedJournalRequests.Port() {
            public long now() { return server.getTickCount(); }
            public boolean operational() { return data.operational(); }
            public boolean current() { return data.acknowledgedCurrent(); }
            public CheckedJournalCheckpoint.Outcome checkpoint() { return data.checkpoint(server); }
        });
    }
    /** The future I6 caller must keep its original actor, consent, snapshot and request-age predicate; never refresh it here. */
    UUID begin(RocketEntity rocket, BooleanSupplier authorized, Consumer<SealedDisassembly.Result> completion) {
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) { return null; }
        bind(level.getServer());
        var logical = rocket.assemblyTransactionId().orElseThrow(); var snapshot = rocket.snapshot().orElseThrow();
        var data = RocketTransactionSavedData.get(server);
        if (ordinary.pending(logical) || !ordinary.pendingRoom(logical) || !data.operational()
                || claimed(rocket) || !authorized.getAsBoolean()) { return null; }
        var id = UUID.randomUUID(); var region = RocketRegion.fromSnapshot(snapshot);
        var record = new RocketTransactionRecord(id, RocketTransactionType.SEALED_DISASSEMBLY, RocketTransactionPhase.LOCKED,
                snapshot.snapshotId(), snapshot.contentHash(), region, 0, rocket.getUUID());
        var entry = new RocketPersistedTransaction(record, snapshot, rocket.ownerId().orElseThrow());
        var lock = transactions.reserveSealed(id, region);
        if (lock == null) { return null; }
        SealedTerrain terrain = null;
        try {
            terrain = new SealedTerrain(level, entry, transactions.adapters(), placement);
            var port = new SealedDisassemblyWorld(level, entry, terrain, writes, authorized,
                    () -> activeAttempts() + ordinary.activeAttempts() < RocketOrdinaryRetirement.MAX_ATTEMPTS,
                    () -> exclusive(entry), false);
            if (port.entity() != SealedDisassembly.Entity.MATCHING) { terrain.close(); lock.close(); return null; }
            var machine = new SealedDisassembly(snapshot.blocks().size(), port);
            work.put(id, new Work(port, machine, lock, completion));
            return id;
        } catch (RuntimeException invalid) {
            if (terrain != null) { terrain.close(); }
            lock.close(); return null;
        } catch (Error fatal) { if (terrain != null) { terrain.close(); } lock.close(); throw fatal; }
    }
    private boolean exclusive(RocketPersistedTransaction own) {
        var record = own.record(); var data = RocketTransactionSavedData.get(server);
        return data.operational() && data.entries().stream().noneMatch(other -> !other.record().transactionId().equals(record.transactionId())
                && (other.record().region().overlaps(record.region()) || other.record().snapshotId().equals(record.snapshotId())
                || other.record().rocketEntityIdOptional().filter(record.rocketEntityId()::equals).isPresent()))
                && work.values().stream().noneMatch(other -> other.world.entry != own
                && other.world.entry.record().region().overlaps(record.region()));
    }
    boolean claimed(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) { return true; }
        var data = RocketTransactionSavedData.get(level.getServer()); var snapshot = rocket.snapshot().orElseThrow();
        if (!data.operational()) { return true; }
        var region = RocketRegion.fromSnapshot(snapshot);
        return java.util.stream.Stream.concat(data.entries().stream(), work.values().stream().map(item -> item.world.entry))
                .anyMatch(entry -> entry.record().region().overlaps(region) || entry.record().snapshotId().equals(snapshot.snapshotId())
                        || entry.record().rocketEntityIdOptional().filter(rocket.getUUID()::equals).isPresent());
    }
    boolean frozen(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return true; }
        if (!rocket.operational()) { return false; }
        var data = RocketTransactionSavedData.get(level.getServer()); var snapshot = rocket.snapshot().orElseThrow();
        if (!data.operational()) { return true; }
        var region = RocketRegion.fromSnapshot(snapshot);
        return java.util.stream.Stream.concat(data.entries().stream(), work.values().stream().map(item -> item.world.entry))
                .filter(entry -> entry.record().type() == RocketTransactionType.SEALED_DISASSEMBLY)
                .anyMatch(entry -> entry.record().region().overlaps(region) || entry.record().snapshotId().equals(snapshot.snapshotId())
                        || entry.record().rocketEntityIdOptional().filter(rocket.getUUID()::equals).isPresent());
    }
    List<RocketRegion> reservations(MinecraftServer server) {
        return java.util.stream.Stream.concat(RocketTransactionSavedData.get(server).entries().stream(),
                work.values().stream().map(item -> item.world.entry)).map(entry -> entry.record().region()).distinct().toList();
    }
    boolean overlaps(RocketRegion region) { return work.values().stream().anyMatch(item -> item.world.entry.record().region().overlaps(region)); }
    void onPlace(ServerLevel level, BlockPos pos) { if (server == level.getServer() && placement != null) { placement.onPlace(level, pos); } }
    private int activeAttempts() { return (int) work.values().stream().filter(item -> item.world.activeAttempt()).count(); }
    String diagnostic(UUID id) {
        var item = work.get(id);
        return item == null ? "not-tracked" : item.machine.phase() + ":" + item.world.reason() + ":" + item.world.entities.diagnostic();
    }
    void tick(MinecraftServer server) {
        bind(server); budget.beginTick(server.getTickCount());
        var ids = List.copyOf(work.keySet());
        // Round-robin first entry: a maximal footprint cannot permanently starve a later one.
        for (int offset = 0; offset < ids.size(); offset++) {
            var id = ids.get((cursor + offset) % ids.size()); var item = work.get(id);
            item.world.tickProof();
            try {
                while (!item.world.terrain.prepared() && item.world.terrain.prepareOne(budget)) { }
                while (item.machine.step(budget)) { }
            } catch (RuntimeException unavailable) {
                // An unresolved U remains claimed. Preparation before U exists has changed no world state.
                if (RocketTransactionSavedData.get(server).entries().stream().noneMatch(entry -> entry.record().transactionId().equals(id))
                        && !item.machine.restorationStarted()) { finish(id, item, SealedDisassembly.Result.REJECTED); }
                continue;
            }
            if (item.machine.result() != null) { finish(id, item, item.machine.result()); }
        }
        if (!ids.isEmpty()) { cursor = (cursor + 1) % ids.size(); }
        writes.flush();
    }
    private void finish(UUID id, Work item, SealedDisassembly.Result result) {
        // Callback observes the native proof and checked U absence while the RAM claim is still owned.
        try { item.completion.accept(result); }
        finally { work.remove(id, item); item.world.close(); item.lock.close(); }
    }
    void clear() {
        for (var item : work.values()) { item.world.close(); item.lock.close(); }
        work.clear(); if (placement != null) { placement.clear(); }
        server = null; budget = null; writes = null; placement = null; cursor = 0;
    }
}
