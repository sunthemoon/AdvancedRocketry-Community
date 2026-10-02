package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.audit.EndgameAudit;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * The server side of the ADR-054 framework: the endgame root's lifecycle and write policy (section 10), the
 * persistence observations that make endpoints MISSING and settle tombstones (sections 9 and 11, review R3-M2),
 * tombstone housekeeping and the audit summary. Everything runs on the server thread except chunk-load scans,
 * which only enqueue what an indexed chunk's tag showed.
 */
public final class EndgameService {
    private final Supplier<EndgameSettings> settings;
    private final Supplier<Set<String>> endgameTypes;
    private final EndgameAudit audit;
    private final Queue<Observation> observations = new ConcurrentLinkedQueue<>();
    private final Map<UUID, Long> absentSince = new HashMap<>();
    private final Map<UUID, Long> readBackAt = new HashMap<>();
    private final EndpointRegistrations registrations = new EndpointRegistrations();
    private final EndpointChunkIndex index = new EndpointChunkIndex();
    private final TransitLedger transits;
    private final EndgameTimings timings = new EndgameTimings();
    private final TransitOperations transitOperations;
    private final BarrierSpacing barrierSpacing = new BarrierSpacing();
    private long nextHousekeeping;
    private MinecraftServer server;
    private EndgameSavedData data;
    private volatile boolean operational;
    private long lastCoalescedFlush = Long.MIN_VALUE / 2;
    private boolean writeFailureLogged;
    private boolean testWrites;

    public EndgameService(Supplier<EndgameSettings> settings, Supplier<Set<String>> endgameTypes) {
        this(settings, endgameTypes, () -> TransitLimits.DEFAULTS);
    }

    public EndgameService(Supplier<EndgameSettings> settings, Supplier<Set<String>> endgameTypes,
                          Supplier<TransitLimits> transitLimits) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.endgameTypes = Objects.requireNonNull(endgameTypes, "endgameTypes");
        this.audit = new EndgameAudit(AdvancedRocketryCommunity.LOGGER::info);
        this.transits = new TransitLedger(this, transitLimits);
        this.transitOperations = new TransitOperations(this);
    }

    /** The running server, for lookups that need it outside a level (the railgun's redirect rule). */
    public Optional<MinecraftServer> server() {
        return Optional.ofNullable(server);
    }

    /** Endgame work per tick by place, and the root flushes (ADR-054 section 7). */
    public EndgameTimings timings() {
        return timings;
    }

    /** Section 7: the spacing and per-station cooldown of player-triggered barrier flushes. */
    public BarrierSpacing barrierSpacing() {
        return barrierSpacing;
    }

    /** Operator and owner actions on the ledger (redirect, purge, resettle, resolve). */
    public TransitOperations transitOperations() {
        return transitOperations;
    }

    /** The transit ledger of section 11. */
    public TransitLedger transits() {
        return transits;
    }

    // ---- Lifecycle -------------------------------------------------------------------------------------------

    public void onServerStarted(ServerStartedEvent event) {
        start(event.getServer());
    }

    void start(MinecraftServer started) {
        server = started;
        data = EndgameSavedData.get(started);
        operational = data.operational();
        if (!operational) {
            AdvancedRocketryCommunity.LOGGER.error("ARCE_ENDGAME_ROOT_BLOCKED file={}", EndgameSavedData.DATA_NAME);
        }
        rebuildIndex();
    }

    /** Unit tests: run the service over a root without a server; writes are then skipped. */
    void startForTest(EndgameSavedData loaded) {
        startForTest(loaded, false);
    }

    /** Unit tests: as above; with {@code writesSucceed} every flush counts as a successful root write. */
    void startForTest(EndgameSavedData loaded, boolean writesSucceed) {
        server = null;
        data = loaded;
        operational = loaded.operational();
        testWrites = writesSucceed;
        rebuildIndex();
    }

    /** After ServerStoppingEvent every endgame action is refused (section 10 "operational"). */
    public void onServerStopping(ServerStoppingEvent event) {
        operational = false;
    }

    public void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    public void clear() {
        operational = false;
        server = null;
        data = null;
        observations.clear();
        absentSince.clear();
        readBackAt.clear();
        registrations.clear();
        transits.clear();
        index.clear();
        nextHousekeeping = 0L;
        audit.clear();
        timings.clear();
        barrierSpacing.clear();
        writeFailureLogged = false;
        lastCoalescedFlush = Long.MIN_VALUE / 2;
    }

    public boolean operational() {
        return operational && data != null && data.operational();
    }

    public EndgameAudit audit() {
        return audit;
    }

    public EndgameSettings settings() {
        return settings.get();
    }

    /** Read access to the root for queries; empty while the endgame is not operational. */
    public Optional<EndgameRoot> root() {
        return operational() ? Optional.of(data.view()) : Optional.empty();
    }

    // ---- Writes (section 10 write policy) --------------------------------------------------------------------

    /** A flush-pending mutation: written by the next coalesced flush, at most one per 100 ticks. */
    public <T> T coalesced(Function<EndgameRoot, T> operation) {
        requireOperational();
        T result = data.update(operation);
        updateIndex();
        return result;
    }

    /**
     * A barrier: the mutation is written before the caller reports success; a failed write stays pending. Every barrier
     * but a spaced player request ({@link #spacedBarrier}) is exempt from the section 7 spacing and counted.
     */
    public <T> T barrier(Function<EndgameRoot, T> operation) {
        return barrier(operation, false);
    }

    /** A barrier of a player request that {@link BarrierSpacing#admit} let through. */
    public <T> T spacedBarrier(Function<EndgameRoot, T> operation) {
        return barrier(operation, true);
    }

    private <T> T barrier(Function<EndgameRoot, T> operation, boolean spaced) {
        requireOperational();
        T result = data.update(operation);
        updateIndex();
        if (data.isDirty()) {
            if (!spaced) {
                barrierSpacing.exemptFlush();
            }
            flush();
        }
        return result;
    }

    /** Whether the last write left the root dirty (a barrier that could not reach disk yet). */
    public boolean writePending() {
        return data != null && data.isDirty();
    }

    private void flush() {
        if (server == null) {
            if (testWrites) {
                data.view().markPersisted();
                data.setDirty(false);
                transits.written();
            }
            return;
        }
        long start = System.nanoTime();
        try {
            data.flush(server);
            timings.flushed(System.nanoTime() - start);
            writeFailureLogged = false;
            if (!data.isDirty()) {
                transits.written();
            }
        } catch (RuntimeException exception) {
            if (!writeFailureLogged) {
                AdvancedRocketryCommunity.LOGGER.error("ARCE_ENDGAME_ROOT_WRITE_FAILED; retained dirty state", exception);
                writeFailureLogged = true;
            }
        }
    }

    // ---- Persistence observations (sections 9 and 11) --------------------------------------------------------

    public void onChunkSave(ChunkDataEvent.Save event) {
        observe(event.getLevel(), event.getChunk().getPos(), event.getData(), false);
    }

    public void onChunkLoad(ChunkDataEvent.Load event) {
        observe(event.getLevel(), event.getChunk().getPos(), event.getData(), true);
    }

    private void observe(LevelAccessor accessor, ChunkPos chunk, CompoundTag tag, boolean load) {
        if (accessor instanceof Level level) {
            observe(level.dimension().location(), chunk.toLong(), tag, load);
        }
    }

    /** Scans an indexed chunk's tag and queues what it showed; safe on chunk-load worker threads. */
    void observe(ResourceLocation level, long chunk, CompoundTag tag, boolean load) {
        if (!operational) {
            return;
        }
        EndpointChunkIndex.ChunkKey key = EndpointChunkIndex.ChunkKey.exact(level, chunk);
        registrations.observe(key, tag, endgameTypes.get());
        if (!index.watches(key)) {
            return;
        }
        observations.add(new Observation(key, EndpointObservations.scan(tag, endgameTypes.get()),
                EndpointObservations.transit(tag, endgameTypes.get()), load));
    }

    // ---- Endpoint registration and removal (sections 9 and 9.1) ----------------------------------------------

    /**
     * A loaded endpoint without an index record waits for a chunk tag that shows its ID persisted (section 2);
     * a frozen endpoint never asks.
     */
    public void awaitRegistration(UUID id, ResourceLocation kind, UUID owner, ResourceLocation level, long pos) {
        if (operational() && data.view().endpoint(id).isEmpty() && !data.view().retired(id)) {
            registrations.await(new EndpointRegistrations.Candidate(id, kind, owner, level, pos));
        }
    }

    /** The endpoint unloaded or was removed: it stops waiting. */
    public void forgetCandidate(UUID id) {
        registrations.forget(id);
    }

    /**
     * Section 9 status of an endpoint block entity at a position: {@code OK} when registered there, otherwise why
     * not ({@code AWAITING_WORLD_SAVE}, {@code ENDPOINT_LIMIT}, {@code ROOT_FULL}, {@code ENDPOINT_RETIRED} or
     * {@code ENDPOINT_POSITION_CONFLICT}).
     */
    public EndgameCode endpointStatus(UUID id, ResourceLocation level, long pos) {
        if (!operational()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        EndgameRoot root = data.view();
        Optional<EndpointRecord> record = root.endpoint(id);
        if (record.isPresent()) {
            if (record.get().state() == EndpointRecord.State.MISSING) {
                return EndgameCode.ENDPOINT_RETIRED;
            }
            return record.get().level().equals(level) && record.get().pos() == pos ? EndgameCode.OK
                    : EndgameCode.ENDPOINT_POSITION_CONFLICT;
        }
        if (root.retired(id)) {
            return EndgameCode.ENDPOINT_RETIRED;
        }
        return registrations.result(id).filter(code -> code != EndgameCode.OK)
                .orElse(EndgameCode.AWAITING_WORLD_SAVE);
    }

    /**
     * Removal by any cause with live state (section 9.1): the record becomes a young tombstone, written by the next
     * coalesced flush; an endpoint without outbox, payloads or receipts has nothing to settle in a barrier.
     */
    public void endpointRemoved(UUID id, long gameTime) {
        registrations.forget(id);
        if (!operational()) {
            return;
        }
        Optional<EndpointRecord> record = data.view().endpoint(id);
        if (record.isPresent() && coalesced(root -> root.remove(id))) {
            audit.line(gameTime, "endgame", "endpoint_removed", "OK", id, record.get().owner(), null,
                    "kind=" + record.get().kind());
        }
    }

    private void drainRegistrations(long now) {
        EndgameSettings current = settings.get();
        for (EndpointRegistrations.Result result : registrations.drain((candidate, frozen) -> coalesced(root ->
                root.register(candidate.id(), candidate.kind(), candidate.owner(), candidate.level(), candidate.pos(),
                        frozen, current.endpointsGlobal(), current.endpointsPerOwner())))) {
            audit.line(now, "endgame", "endpoint_register", result.code().name(), result.candidate().id(),
                    result.candidate().owner(), null, "kind=" + result.candidate().kind());
        }
    }

    int pendingObservations() {
        return observations.size();
    }

    int registrationResultsForTest() {
        return registrations.results();
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && operational()) {
            tick(server.overworld().getGameTime());
        }
    }

    void tick(long now) {
        long start = System.nanoTime();
        drainRegistrations(now);
        drainObservations(now);
        settleAgedAbsences(now);
        housekeep(now);
        long ledger = System.nanoTime();
        timings.add(EndgameTimings.Place.INDEX, ledger - start);
        ledgerPasses(now);
        timings.add(EndgameTimings.Place.LEDGER, System.nanoTime() - ledger);
        if (data.flushPending() && now - lastCoalescedFlush >= EndgameLimits.COALESCED_FLUSH_INTERVAL_TICKS) {
            lastCoalescedFlush = now;
            flush();
        }
        audit.summarizeIfDue(now);
    }

    /** Section 7: the ledger passes run in the same END handler, after observations and settlements. */
    private void ledgerPasses(long now) {
        transits.tick(now);
    }

    private void drainObservations(long now) {
        EndgameRoot root = data.view();
        for (Observation observation; (observation = observations.poll()) != null; ) {
            for (UUID id : index.idsAt(observation.key())) {
                Optional<Long> recorded = position(root, id);
                if (recorded.isEmpty()) {
                    continue;
                }
                boolean present = EndpointObservations.present(observation.scan(), id, recorded.get());
                if (root.tombstone(id).orElse(null) instanceof Tombstone.Settled) {
                    if (present) {
                        readBackAt.remove(id);
                    } else if (observation.load()) {
                        readBackAt.putIfAbsent(id, now);
                    }
                } else if (present) {
                    absentSince.remove(id);
                } else {
                    absentSince.putIfAbsent(id, now);
                }
            }
            transits.observed(observation.key(), observation.scan(), observation.transit(), now);
        }
    }

    private void settleAgedAbsences(long now) {
        Iterator<Map.Entry<UUID, Long>> entries = absentSince.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Long> entry = entries.next();
            if (now - entry.getValue() < EndgameLimits.PERSISTENCE_AGE_TICKS) {
                continue;
            }
            UUID id = entry.getKey();
            entries.remove();
            EndgameRoot root = data.view();
            Optional<EndpointRecord> record = root.endpoint(id);
            if (record.isPresent() && record.get().state() == EndpointRecord.State.ACTIVE) {
                // Retirement without live state (review R3-M1): its unacknowledged claims return to ARRIVED.
                coalesced(r -> r.markMissing(id) | TransitLedger.retireWithoutLiveState(r, id, false) > 0);
                audit.line(now, "endgame", "endpoint_missing", EndgameCode.ENDPOINT_RETIRED.name(), id,
                        record.get().owner(), null, "kind=" + record.get().kind());
            } else if (root.tombstone(id).orElse(null) instanceof Tombstone.Young young) {
                EndgameRoot.Change change = coalesced(r -> r.settle(id, r::pinned));
                audit.line(now, "endgame", "tombstone_settled", change.code().name(), id, young.owner(), null, "");
                auditEvictions(now, change.evicted());
            }
        }
    }

    /** At most once per 200 ticks, and only above the threshold (review C11R-M5). */
    private void housekeep(long now) {
        if (now < nextHousekeeping) {
            return;
        }
        nextHousekeeping = now + EndgameLimits.TOMBSTONE_HOUSEKEEPING_INTERVAL_TICKS;
        if (data.view().tombstoneCount() <= EndgameLimits.TOMBSTONE_HOUSEKEEPING_THRESHOLD) {
            return;
        }
        List<UUID> evicted = coalesced(r -> r.housekeep(id -> readBackAt.containsKey(id)
                && now - readBackAt.get(id) >= EndgameLimits.TOMBSTONE_HOUSEKEEPING_AGE_TICKS, r::pinned));
        evicted.forEach(readBackAt::remove);
        auditEvictions(now, evicted);
    }

    public void auditEvictions(long now, List<UUID> evicted) {
        for (UUID id : evicted) {
            readBackAt.remove(id);
            audit.line(now, "endgame", "tombstone_evicted", "TOMBSTONE_EVICTED", id, null, null, "");
        }
    }

    private static Optional<Long> position(EndgameRoot root, UUID id) {
        Optional<EndpointRecord> record = root.endpoint(id);
        if (record.isPresent()) {
            return record.get().state() == EndpointRecord.State.ACTIVE ? Optional.of(record.get().pos())
                    : Optional.empty();
        }
        return root.tombstone(id).map(Tombstone::pos);
    }

    /** The whole index, built when the root is loaded. */
    private void rebuildIndex() {
        if (data == null || !data.operational()) {
            index.clear();
        } else {
            index.rebuild(data.view());
        }
    }

    /** After a mutation only the IDs it touched move (review C11R-M5). */
    private void updateIndex() {
        if (data == null || !data.operational()) {
            index.clear();
        } else {
            index.update(data.view());
        }
    }

    /** Tests: the incremental index and a full rebuild of the current root. */
    Map<EndpointChunkIndex.ChunkKey, Set<UUID>> indexForTest() {
        return index.snapshot();
    }

    Map<EndpointChunkIndex.ChunkKey, Set<UUID>> rebuiltIndexForTest() {
        rebuildIndex();
        return index.snapshot();
    }

    // ---- Diagnostics (section 13) ----------------------------------------------------------------------------

    public String status() {
        EndgameSettings current = settings.get();
        StringBuilder text = new StringBuilder("endgame switches: laser_drill=").append(current.laserDrill())
                .append(" physical_mining=").append(current.laserPhysicalMining())
                .append(" railgun=").append(current.railgun())
                .append(" black_hole_generator=").append(current.blackHoleGenerator())
                .append(" gravity_field=").append(current.gravityField())
                .append(" space_elevator=").append(current.spaceElevator());
        if (!operational()) {
            return text.append("; root: ").append(data == null ? "not loaded" : "BLOCKED").toString();
        }
        EndgameRoot root = data.view();
        long missing = root.endpoints().stream().filter(r -> r.state() == EndpointRecord.State.MISSING).count();
        return text.append("; root: operational save_epoch=").append(root.saveEpoch())
                .append(" accounted_bytes=").append(root.accountedBytes())
                .append(" write_pending=").append(data.isDirty())
                .append(" exempt_barriers=").append(barrierSpacing.exemptFlushes())
                .append("; endpoints=").append(root.endpoints().size() - missing).append(" missing=").append(missing)
                .append(" young_tombstones=").append(root.youngTombstones().size())
                .append(" settled_tombstones=").append(root.settledTombstones().size())
                .append(" zones=").append(root.zones().size())
                .append(" awaiting_registration=").append(registrations.waiting())
                .append("; ").append(transits.status())
                .append("; audit_ring=").append(audit.ringSize()).toString();
    }

    private void requireOperational() {
        if (!operational()) {
            throw new IllegalStateException("The endgame root is not operational");
        }
    }

    record Observation(EndpointChunkIndex.ChunkKey key, Map<Long, Optional<UUID>> scan,
                       List<EndpointObservations.ShownAt> transit, boolean load) {
    }

    /** For commands and tests: the Level key hash a settled tombstone stores. */
    public static int levelHash(ResourceLocation level) {
        return Tombstone.hash(level);
    }
}
