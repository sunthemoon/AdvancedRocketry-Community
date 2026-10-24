package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedJournalRequests;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedLazyWork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
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
    private static final class Work {
        final SealedDisassemblyWorld world;
        final SealedDisassembly machine;
        final RocketRegionLockManager.LockToken lock;
        final Consumer<SealedDisassembly.Result> completion;
        final boolean recovery;
        long retryAt = Long.MIN_VALUE;
        Work(SealedDisassemblyWorld world, SealedDisassembly machine, RocketRegionLockManager.LockToken lock,
                Consumer<SealedDisassembly.Result> completion, boolean recovery) {
            this.world = world; this.machine = machine; this.lock = lock; this.completion = completion; this.recovery = recovery;
        }
    }
    private final RocketTransactionExecutor transactions;
    private final RocketOrdinaryRetirement ordinary;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private MinecraftServer server;
    private SealedWorkBudget budget;
    private SealedJournalRequests writes;
    private SealedCarrierPlacement placement;
    private SealedMaterializer materializer;
    private SealedRecoverySelection recoverySelection;
    private MinecraftServer discoveryServer;
    private SealedLazyWork<SealedLoadedDiscovery.Hint> lazy;
    private boolean lazyFirst;
    private String lazyFailure;
    private int cursor;
    private Consumer<String> nativeObserver = label -> { };
    private SealedDiagnostics diagnostics;

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
        diagnostics = new SealedDiagnostics((warning, line) -> {
            if (warning) { io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(line); }
            else { io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(line); }
        });
        materializer = new SealedMaterializer(label -> nativeObserver.accept(label)); recoverySelection = new SealedRecoverySelection();
        ensureLazy(server);
        var data = RocketTransactionSavedData.get(server);
        writes = new SealedJournalRequests(new SealedJournalRequests.Port() {
            public long now() { return server.getTickCount(); }
            public boolean operational() { return data.operational(); }
            public boolean current() { return data.acknowledgedCurrent(); }
            public CheckedJournalCheckpoint.Outcome checkpoint() {
                var outcome = data.checkpoint(server);
                if (outcome != CheckedJournalCheckpoint.Outcome.COMMITTED) {
                    work.forEach((id, item) -> diagnostics.hold(id.toString(), item.machine.phase().name(), "JOURNAL_" + outcome));
                }
                return outcome;
            }
        });
    }
    /** The future I6 caller must keep its original actor, consent, snapshot and request-age predicate; never refresh it here. */
    UUID begin(RocketEntity rocket, BooleanSupplier authorized, Consumer<SealedDisassembly.Result> completion) {
        if (!(rocket.level() instanceof ServerLevel level) || !rocket.operational()) { return null; }
        bind(level.getServer()); budget.beginTick(server.getTickCount());
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
                    () -> exclusive(entry), false, budget, label -> nativeObserver.accept(label),
                    (phase, reason) -> diagnostics.hold(id.toString(), phase, reason));
            if (port.entity() != SealedDisassembly.Entity.MATCHING) { terrain.close(); lock.close(); return null; }
            var machine = new SealedDisassembly(snapshot.blocks().size(), port);
            work.put(id, new Work(port, machine, lock, completion, false));
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
    void terrainChanged(ServerLevel level, BlockPos pos) {
        if (server != level.getServer() || !server.isSameThread()) { return; }
        var position = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(pos.getX(), pos.getY(), pos.getZ());
        // Only the existing bounded RAM claims are visited; no world position is read or loaded here.
        for (var item : work.values()) {
            var region = item.world.entry.record().region();
            if (region.dimension().equals(level.dimension().location()) && region.contains(position)) { item.world.invalidateProof(); }
        }
    }
    void entityChanged(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        if (server != level.getServer() || !server.isSameThread()) { return; }
        for (var item : work.values()) {
            if (item.world.entities.names(level, entity)) { item.world.entities.invalidate(); item.world.entityChanged(); }
        }
    }
    void onPlace(ServerLevel level, BlockPos pos) {
        terrainChanged(level, pos);
        if (server == level.getServer() && placement != null) { placement.onPlace(level, pos); }
    }
    boolean onRemove(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState next) {
        terrainChanged(level, pos);
        return server == level.getServer() && materializer != null && materializer.onRemove(level, pos, next);
    }
    boolean unseal(ServerLevel level, io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity carrier) {
        return unsealResult(level, carrier) == SealedMaterializer.Result.PUBLISHED;
    }
    private SealedMaterializer.Result unsealResult(ServerLevel level, SealedRocketBlockEntity carrier) {
        bind(level.getServer()); budget.beginTick(server.getTickCount());
        var member = carrier.member().orElse(null);
        if (member == null) { return SealedMaterializer.Result.HELD; }
        var point = new RocketRegion(member.dimension(), member.position(), member.position());
        // The same lock excludes synchronous assembly/disassembly reentrancy, not just durable U entries.
        try (var lock = transactions.reserveSealed(member.transaction(), point)) {
            if (lock == null) { return SealedMaterializer.Result.HELD; }
            return materializer.unseal(level, carrier, transactions.adapters(), () -> absentForUnseal(member, point), budget);
        }
    }
    /** Safe before bind: allocating a bounded hint queue does not open or inspect either journal. */
    void hint(ServerLevel level, BlockPos position) {
        ensureLazy(level.getServer()); lazy.hint(new SealedLoadedDiscovery.Hint(level.dimension(), position));
    }
    int hintCount(MinecraftServer server) { ensureLazy(server); return lazy.size(); }
    String lazyDiagnostic(MinecraftServer server) {
        ensureLazy(server); return "queued=" + lazy.size() + ",last-failure=" + lazyFailure;
    }
    private void ensureLazy(MinecraftServer server) {
        if (!server.isSameThread() || discoveryServer != null && discoveryServer != server) {
            throw new IllegalStateException("Sealed hints require their owning server thread");
        }
        if (lazy != null) { return; }
        discoveryServer = server;
        var discovery = new SealedLoadedDiscovery(server);
        lazy = new SealedLazyWork<>(new SealedLazyWork.Port<>() {
            public SealedLoadedDiscovery.Hint discover() { return discovery.advance(); }
            public void attempt(SealedLoadedDiscovery.Hint hint, SealedWorkBudget allowance) { attemptLazy(hint); }
        });
    }
    private void attemptLazy(SealedLoadedDiscovery.Hint hint) {
        var level = server.getLevel(hint.dimension());
        if (level == null) { return; }
        var pos = hint.position(); var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntities().get(pos) instanceof SealedRocketBlockEntity carrier)
                || !carrier.unsealAttemptDue(server.getTickCount())) { return; }
        try {
            var result = unsealResult(level, carrier);
            if (result == SealedMaterializer.Result.HELD) { carrier.deferUnsealUntil(server.getTickCount() + 20L); }
            else if (result == SealedMaterializer.Result.PREPARATION_FAILED) {
                lazyFailure = "PREPARATION_FAILED"; carrier.deferUnsealUntil(server.getTickCount() + 200L);
                lazyHeld(level, "PREPARATION_FAILED");
            }
        } catch (RuntimeException failure) {
            // No cleanup/refill after a native callback failure. The next sweep considers only an actual remaining C.
            lazyFailure = failure.getClass().getSimpleName(); carrier.deferUnsealUntil(server.getTickCount() + 200L);
            lazyHeld(level, "CALLBACK_FAILED");
        }
    }
    private void lazyHeld(ServerLevel level, String reason) {
        // One remembered identity per Level/reason, not one per rediscovered carrier. Long registry names stay bounded.
        String dimension = level.dimension().location().toString();
        String identity = "lazy:" + dimension.substring(0, Math.min(96, dimension.length())) + "-"
                + UUID.nameUUIDFromBytes((dimension + ":" + reason).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        diagnostics.hold(identity, "UNSEAL", reason);
    }
    private boolean absentForUnseal(io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember member, RocketRegion point) {
        var data = RocketTransactionSavedData.get(server);
        var status = data.absenceStatus(member.transaction(), member.snapshot(), member.entity());
        if (status == RocketTransactionSavedData.AbsenceStatus.WRITE_REQUIRED) { writes.request(); }
        if (!data.acknowledgedAbsent(member.transaction(), member.snapshot(), member.entity())) { return false; }
        return java.util.stream.Stream.concat(data.entries().stream(), work.values().stream().map(item -> item.world.entry))
                .noneMatch(entry -> entry.record().transactionId().equals(member.transaction())
                || entry.record().snapshotId().equals(member.snapshot())
                || entry.record().rocketEntityIdOptional().filter(member.entity()::equals).isPresent()
                || entry.record().region().overlaps(point));
    }
    private int activeAttempts() { return (int) work.values().stream().filter(item -> item.world.activeAttempt()).count(); }
    String diagnostic(UUID id) {
        var item = work.get(id);
        return item == null ? "not-tracked" : item.machine.phase() + ":" + item.world.reason() + ":" + item.world.entities.diagnostic();
    }
    boolean recovering(UUID id) { var item = work.get(id); return item != null && item.recovery; }
    void nativeObserver(MinecraftServer server, Consumer<String> observer) {
        bind(server); nativeObserver = java.util.Objects.requireNonNull(observer);
    }

    /** A single selection owns both recovery implementations, including the post-removal RAM continuation. */
    private UUID selectRecovery(boolean allowed) {
        var data = RocketTransactionSavedData.get(server);
        if (!allowed || !data.operational()) { return null; }
        var entries = data.entries();
        var ids = java.util.stream.Stream.concat(entries.stream().map(entry -> entry.record().transactionId()),
                work.entrySet().stream().filter(entry -> entry.getValue().recovery).map(Map.Entry::getKey)).toList();
        return recoverySelection.select(server.getTickCount(), ids, id -> {
            var owned = work.get(id);
            if (owned != null) { return owned.recovery && server.getTickCount() >= owned.retryAt; }
            var entry = entries.stream().filter(candidate -> candidate.record().transactionId().equals(id)).findFirst().orElse(null);
            return entry != null && (entry.record().type() != RocketTransactionType.SEALED_DISASSEMBLY
                    || ordinary.size() + work.size() < RocketOrdinaryRetirement.MAX_PENDING);
        }).orElse(null);
    }
    private void adoptRecovery(RocketPersistedTransaction entry) {
        var id = entry.record().transactionId();
        if (work.containsKey(id) || ordinary.size() + work.size() >= RocketOrdinaryRetirement.MAX_PENDING) { return; }
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                entry.record().region().dimension()));
        if (level == null || !exclusive(entry)) { return; }
        var lock = transactions.reserveSealed(id, entry.record().region());
        if (lock == null) { return; }
        SealedTerrain terrain = null;
        try {
            terrain = new SealedTerrain(level, entry, transactions.adapters(), placement);
            var port = new SealedDisassemblyWorld(level, entry, terrain, writes, () -> true,
                    () -> activeAttempts() + ordinary.activeAttempts() < RocketOrdinaryRetirement.MAX_ATTEMPTS,
                    () -> exclusive(entry), true, budget, label -> nativeObserver.accept(label),
                    (phase, reason) -> diagnostics.hold(id.toString(), phase, reason));
            boolean elected = entry.record().phase() == RocketTransactionPhase.RESTORED
                    || entry.record().phase() == RocketTransactionPhase.COMMITTED;
            work.put(id, new Work(port, SealedDisassembly.recover(entry.snapshot().blocks().size(), port, elected),
                    lock, result -> { }, true));
        } catch (RuntimeException unavailable) {
            if (terrain != null) { terrain.close(); }
            lock.close();
            diagnostics.hold(id.toString(), "RECOVERY", "UNAVAILABLE");
        } catch (Error fatal) { if (terrain != null) { terrain.close(); } lock.close(); throw fatal; }
    }
    /** Returns only the selected legacy ID; null must never trigger an independent legacy scan. */
    UUID tick(MinecraftServer server, boolean allowRecovery) {
        bind(server); budget.beginTick(server.getTickCount());
        var selected = selectRecovery(allowRecovery);
        UUID legacy = null;
        if (selected != null && !work.containsKey(selected)) {
            var entry = RocketTransactionSavedData.get(server).entries().stream()
                    .filter(candidate -> candidate.record().transactionId().equals(selected)).findFirst().orElse(null);
            if (entry != null && entry.record().type() == RocketTransactionType.SEALED_DISASSEMBLY) { adoptRecovery(entry); }
            else if (entry != null) { legacy = selected; }
        }
        var ids = List.copyOf(work.keySet());
        // Proof deadlines still advance every tick, independently of the shared world-inspection cursor.
        for (var id : ids) {
            var item = work.get(id);
            if ((!item.recovery || id.equals(selected)) && server.getTickCount() >= item.retryAt) { item.world.tickProof(); }
            else { item.world.tickProofDeadline(); }
        }
        lazyFirst = !lazyFirst;
        if (lazyFirst) { lazy.tick(budget); }
        // Round-robin first entry: a maximal footprint cannot permanently starve a later one.
        for (int offset = 0; offset < ids.size(); offset++) {
            var id = ids.get((cursor + offset) % ids.size()); var item = work.get(id);
            if (item.recovery && !id.equals(selected) || server.getTickCount() < item.retryAt) { continue; }
            try {
                while (!item.world.terrain.prepared() && item.world.terrain.prepareOne(budget)) { }
                while (item.machine.step(budget)) { }
            } catch (RuntimeException unavailable) {
                // An unresolved U remains claimed. Preparation before U exists has changed no world state.
                if (RocketTransactionSavedData.get(server).entries().stream().noneMatch(entry -> entry.record().transactionId().equals(id))
                        && !item.machine.restorationStarted()) { finish(id, item, SealedDisassembly.Result.REJECTED); }
                else {
                    item.retryAt = server.getTickCount() + (long) RocketOrdinaryRetirement.RETRY_TICKS;
                    diagnostics.hold(id.toString(), item.machine.phase().name(), "UNAVAILABLE");
                }
                continue;
            }
            if (item.machine.result() != null) { finish(id, item, item.machine.result()); }
        }
        if (!ids.isEmpty()) { cursor = (cursor + 1) % ids.size(); }
        if (!lazyFirst) { lazy.tick(budget); }
        writes.flush();
        diagnostics.flush(server.getTickCount());
        return legacy;
    }
    private void finish(UUID id, Work item, SealedDisassembly.Result result) {
        // Callback observes the native proof and checked U absence while the RAM claim is still owned.
        try { item.completion.accept(result); }
        finally {
            work.remove(id, item); item.world.close(); item.lock.close();
            diagnostics.complete(id.toString(), result.name());
        }
    }
    void clear() {
        for (var item : work.values()) { item.world.close(); item.lock.close(); }
        work.clear(); if (placement != null) { placement.clear(); }
        if (lazy != null) { lazy.clear(); }
        if (diagnostics != null) { diagnostics.clear(); diagnostics = null; }
        server = null; budget = null; writes = null; placement = null; materializer = null; recoverySelection = null; cursor = 0;
        discoveryServer = null; lazy = null; lazyFirst = false; lazyFailure = null;
        nativeObserver = label -> { };
    }
}
