package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLedgerView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-054 section 11 on the server. The END-tick passes (section 7): arrival (at most 64 records per tick), stub
 * drops, and reconciliation of the loaded ledger endpoints in ID order, round-robin, within 64 records per tick, with
 * at most 32 registrations. It routes persistence observations to the endpoints they show and prunes stubs once their
 * paid endpoint's chunk saved the move; it admits escrows within the limits and the root's growth bound, settles an
 * endpoint's removal from its live state (section 9.1) and returns claims at a retirement without live state.
 */
public final class TransitLedger implements TransitLedgerView {
    public static final int ARRIVALS_PER_TICK = 64;
    public static final int RECONCILIATIONS_PER_TICK = 64;
    private static final int MAX_PENDING_OBSERVATIONS = 1024;
    private static final String SYSTEM = "endgame";

    private final EndgameService service;
    private final Supplier<TransitLimits> limits;
    private final TreeMap<String, TransitEndpoint> loaded = new TreeMap<>();
    private final Map<UUID, Pending> pending = new LinkedHashMap<>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Pending> eldest) {
            return size() > MAX_PENDING_OBSERVATIONS;
        }
    };
    private final Map<TransitKey, Long> acknowledgedAt = new HashMap<>();
    @Nullable
    private String cursor;
    private int registrationsLeft = EndgameLimits.REGISTRATIONS_PER_TICK;
    private int reconciliationsLeft = RECONCILIATIONS_PER_TICK;
    private long now;
    private boolean settlementPending;

    /** An observation of an endpoint that was not loaded yet when it was drained. */
    private record Pending(TransitTags.Shown shown, long tick) {
    }

    TransitLedger(EndgameService service, Supplier<TransitLimits> limits) {
        this.service = Objects.requireNonNull(service, "service");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    // ---- Loaded endpoints -------------------------------------------------------------------------------------

    /** A ledger endpoint's block entity loaded on the server; an observation drained before is applied now. */
    public void attach(TransitEndpoint endpoint) {
        UUID id = endpoint.endpointId();
        loaded.put(id.toString(), endpoint);
        Pending observed = pending.remove(id);
        if (observed != null) {
            apply(endpoint, observed.shown(), observed.tick());
        }
    }

    public void detach(UUID id) {
        loaded.remove(id.toString());
    }

    /** An unloading block entity detaches only itself, never another block entity attached under its ID. */
    public void detach(UUID id, TransitEndpoint endpoint) {
        loaded.remove(id.toString(), endpoint);
    }

    public Optional<TransitEndpoint> loaded(UUID id) {
        return Optional.ofNullable(loaded.get(id.toString()));
    }

    private static void apply(TransitEndpoint endpoint, TransitTags.Shown shown, long tick) {
        endpoint.source().observed(shown.outbox(), tick);
        endpoint.destination().observed(shown.incoming(), shown.receipts(), tick);
    }

    // ---- Observations (sections 2 and 11) ---------------------------------------------------------------------

    /**
     * A chunk tag showed these endpoints' transit sections: they count as persisted from now. Stubs paid at an endpoint
     * of this chunk are pruned once the tag, at least 40 ticks after the acknowledgement, holds no incoming payload for
     * them; an unreadable endgame block entity at the endpoint's position counts as holding it (review R3-L1).
     */
    void observed(EndpointChunkIndex.ChunkKey chunk, Map<Long, Optional<UUID>> scan,
                  Map<UUID, TransitTags.Shown> shown, long tick) {
        shown.forEach((id, sections) -> {
            TransitEndpoint endpoint = loaded.get(id.toString());
            if (endpoint != null) {
                apply(endpoint, sections, tick);
            } else {
                pending.put(id, new Pending(sections, tick));
            }
        });
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return;
        }
        EndgameRoot root = view.get();
        List<TransitKey> prunable = new ArrayList<>();
        for (TransitRecord record : root.transits().records()) {
            UUID paid = record.paidEndpoint();
            if (paid == null || !record.acknowledged() || tick - acknowledgedAt.getOrDefault(record.key(),
                    Long.MIN_VALUE / 2) < EndgameLimits.PERSISTENCE_AGE_TICKS) {
                continue;
            }
            Optional<Long> pos = position(root, paid, chunk);
            if (pos.isEmpty()) {
                continue;
            }
            TransitTags.Shown atPaid = shown.get(paid);
            boolean holds = atPaid != null ? atPaid.incoming().contains(record.key())
                    : EndpointObservations.present(scan, paid, pos.get());
            if (TransitRules.prunes(record.facts(paid, root.saveEpoch()), holds)) {
                prunable.add(record.key());
            }
        }
        if (!prunable.isEmpty()) {
            service.coalesced(r -> {
                prunable.forEach(key -> {
                    r.transits().remove(key);
                    acknowledgedAt.remove(key);
                });
                return null;
            });
            service.audit().count(SYSTEM, "transit_pruned", prunable.size());
        }
    }

    /** The paid endpoint's recorded position when it lies in this chunk. */
    private static Optional<Long> position(EndgameRoot root, UUID id, EndpointChunkIndex.ChunkKey chunk) {
        Optional<EndpointRecord> record = root.endpoint(id);
        if (record.isPresent()) {
            return EndpointChunkIndex.ChunkKey.exact(record.get().level(),
                    EndpointChunkIndex.chunkOf(record.get().pos())).equals(chunk)
                    ? Optional.of(record.get().pos()) : Optional.empty();
        }
        return root.tombstone(id).filter(tombstone -> tombstone.levelHash() == chunk.levelHash()
                        && EndpointChunkIndex.chunkOf(tombstone.pos()) == chunk.chunk()
                        && (!(tombstone instanceof Tombstone.Young young) || young.level().equals(chunk.level())))
                .map(Tombstone::pos);
    }

    // ---- END-tick passes (section 7) ----------------------------------------------------------------------------

    void tick(long gameTime) {
        now = gameTime;
        registrationsLeft = EndgameLimits.REGISTRATIONS_PER_TICK;
        reconciliationsLeft = RECONCILIATIONS_PER_TICK;
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return;
        }
        arrive(view.get());
        dropPayloadsOfDurableAcknowledgements(view.get());
        reconcileLoaded(view.get());
    }

    private void arrive(EndgameRoot root) {
        List<TransitRecord> due = root.transits().records().stream()
                .filter(record -> record.state() == TransitRecord.State.IN_TRANSIT && record.arriveAt() <= now)
                .sorted(Comparator.comparingLong(TransitRecord::arriveAt).thenComparing(TransitRecord::key))
                .limit(ARRIVALS_PER_TICK).toList();
        if (!due.isEmpty()) {
            service.coalesced(r -> {
                due.forEach(record -> r.transits().replace(record.arrived()));
                return null;
            });
            service.audit().count(SYSTEM, "transit_arrived", due.size());
        }
    }

    /** A durably acknowledged record keeps no payload: it is a stub until its paid endpoint's move is saved. */
    private void dropPayloadsOfDurableAcknowledgements(EndgameRoot root) {
        List<TransitRecord> stubs = root.transits().records().stream()
                .filter(record -> !record.stub() && record.ackDurable(root.saveEpoch())).toList();
        if (!stubs.isEmpty()) {
            service.coalesced(r -> {
                stubs.forEach(record -> r.transits().replace(record.asStub()));
                return null;
            });
        }
    }

    private void reconcileLoaded(EndgameRoot root) {
        if (loaded.isEmpty()) {
            return;
        }
        List<String> order = new ArrayList<>();
        order.addAll(cursor == null ? loaded.keySet() : loaded.tailMap(cursor, false).keySet());
        if (cursor != null) {
            order.addAll(loaded.headMap(cursor, true).keySet());
        }
        for (String id : order) {
            TransitEndpoint endpoint = loaded.get(id);
            UUID endpointId = endpoint.endpointId();
            if (endpoint.source().anythingUnpersisted() || endpoint.destination().anythingUnpersisted()) {
                endpoint.transitChanged(); // Section 2: while anything is unpersisted the chunk stays dirty.
            }
            if (endpoint.transitFrozen() || endpoint.endpointOwner().isEmpty() || root.endpoint(endpointId)
                    .filter(record -> record.state() == EndpointRecord.State.ACTIVE).isEmpty()) {
                cursor = id;
                continue;
            }
            UUID owner = endpoint.endpointOwner().get();
            boolean changed = endpoint.source().reconcile(this, endpointId, owner, now).changed();
            changed |= endpoint.destination().reconcile(this, endpointId, owner, endpoint.receiveBuffer(), now);
            if (changed) {
                endpoint.transitChanged();
            }
            cursor = id; // This endpoint had its turn: the next tick starts at the next one (review C12R-M1).
            if (reconciliationsLeft <= 0) {
                return;
            }
        }
    }

    /** Before an escrow at a source (section 11 "when S loads and before each escrow"). */
    public TransitRulesPass reconcileSource(TransitEndpoint endpoint, long gameTime) {
        now = gameTime;
        if (endpoint.transitFrozen() || endpoint.endpointOwner().isEmpty() || service.root().isEmpty()) {
            return new TransitRulesPass(false, true);
        }
        var pass = endpoint.source().reconcile(this, endpoint.endpointId(), endpoint.endpointOwner().get(), now);
        if (pass.changed()) {
            endpoint.transitChanged();
        }
        return new TransitRulesPass(pass.changed(), pass.escrowBlocked());
    }

    /** The outcome of a source reconciliation run before an escrow. */
    public record TransitRulesPass(boolean changed, boolean escrowBlocked) {
    }

    // ---- TransitLedgerView ---------------------------------------------------------------------------------------

    private EndgameRoot root() {
        return service.root().orElseThrow(() -> new IllegalStateException("The endgame root is not operational"));
    }

    @Override
    public long saveEpoch() {
        return root().saveEpoch();
    }

    @Override
    public long dispatchedThrough(UUID source) {
        return root().dispatchedThrough(source);
    }

    @Override
    public Optional<TransitRecord> record(TransitKey key) {
        return root().transits().record(key);
    }

    @Override
    public List<TransitRecord> forEndpoint(UUID endpoint) {
        return root().transits().forEndpoint(endpoint);
    }

    @Override
    public boolean registrationDurable(UUID endpoint) {
        return root().registrationDurable(endpoint);
    }

    /**
     * Step 2, at most 32 per tick. A registration of an escrowed payload is admitted up to the 4 MiB bound and the 256
     * records the root can hold, and otherwise waits with {@code ROOT_FULL} (section 10).
     */
    @Override
    public EndgameCode register(UUID source, UUID owner, OutboxEntry entry, long gameTime) {
        if (registrationsLeft <= 0) {
            return EndgameCode.RATE_LIMITED;
        }
        EndgameRoot root = root();
        if (root.transits().size() >= EndgameLimits.MAX_TRANSIT_RECORDS
                || root.accountedBytes() + EndgameLimits.TRANSIT_RECORD_BYTES > EndgameLimits.MAX_ROOT_BYTES) {
            return EndgameCode.ROOT_FULL;
        }
        registrationsLeft--;
        long dispatched = root.dispatchedThrough(source);
        if (TransitRules.sequenceGap(entry.seq(), dispatched)) {
            service.audit().line(gameTime, SYSTEM, "SEQUENCE_GAP", "OK", source, owner, null, "seq=" + entry.seq()
                    + " dispatched_through=" + dispatched);
        }
        service.coalesced(r -> {
            r.registerTransit(TransitRecord.registered(source, entry, owner, r.saveEpoch(), gameTime));
            return null;
        });
        service.audit().count(SYSTEM, "transit_registered", 1);
        return EndgameCode.OK;
    }

    @Override
    public boolean takeReconciliation() {
        if (reconciliationsLeft <= 0) {
            return false;
        }
        reconciliationsLeft--;
        return true;
    }

    @Override
    public void claim(TransitKey key, UUID endpoint) {
        replace(key, record -> record.claimed(endpoint));
        service.audit().count(SYSTEM, "transit_claimed", 1);
    }

    @Override
    public void recover(TransitKey key, UUID endpoint) {
        replace(key, record -> record.claimed(endpoint));
    }

    @Override
    public void acknowledge(TransitKey key) {
        replace(key, record -> record.acknowledged(service.root().orElseThrow().saveEpoch()));
        acknowledgedAt.put(key, now);
    }

    @Override
    public void quarantine(TransitKey key) {
        replace(key, TransitRecord::quarantined);
    }

    private void replace(TransitKey key, java.util.function.UnaryOperator<TransitRecord> change) {
        service.coalesced(r -> {
            r.transits().replace(change.apply(r.transits().record(key).orElseThrow()));
            return null;
        });
    }

    @Override
    public void audit(String action, String result, UUID endpoint, @Nullable UUID owner, String fields) {
        service.audit().line(now, SYSTEM, action, result, endpoint, owner, null, fields);
    }

    // ---- Escrow admission (section 11 limits, section 10 growth) ----------------------------------------------

    /**
     * Whether a source may escrow now: the root is operational and its own registration durable (review R3-H1), the
     * server and the owner are under their transit limits (records, stubs and the outbox entries known to the server),
     * and the root's accounted size stays within 3 MiB.
     */
    public EndgameCode admitEscrow(UUID source, UUID owner) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        EndgameRoot root = view.get();
        if (!root.registrationDurable(source)) {
            return EndgameCode.AWAITING_WORLD_SAVE;
        }
        TransitLimits current = limits.get();
        long known = knownOutbox(root, null);
        if (root.transits().size() + known >= current.records()
                || root.transits().live(owner) + knownOutbox(root, owner) >= current.perOwner()) {
            return EndgameCode.TRANSIT_LIMIT;
        }
        if (root.accountedBytes() + (known + 1) * EndgameLimits.TRANSIT_RECORD_BYTES
                > EndgameLimits.GROWTH_ADMISSION_BYTES) {
            return EndgameCode.ROOT_FULL;
        }
        return EndgameCode.OK;
    }

    /** Live outbox entries of loaded sources that have no record yet (optionally of one owner). */
    private long knownOutbox(EndgameRoot root, @Nullable UUID owner) {
        long count = 0;
        for (TransitEndpoint endpoint : loaded.values()) {
            if (owner != null && endpoint.endpointOwner().filter(owner::equals).isEmpty()) {
                continue;
            }
            long dispatched = root.dispatchedThrough(endpoint.endpointId());
            for (OutboxEntry entry : endpoint.source().outbox()) {
                count += entry.seq() > dispatched ? 1 : 0;
            }
        }
        return count;
    }

    // ---- Removal by any cause and retirement without live state (sections 9 and 9.1) --------------------------

    /**
     * Section 9.1 for a ledger endpoint whose block changed to another block: settles its records from its live state
     * and removes its index record (retiring its ID) in one write, a barrier flush when anything had to be settled.
     * Returns the items to drop with the local buffers: incoming payloads that are its own content (acknowledged here
     * or pruned). Unregistered outbox entries are destroyed with an audit line; incoming payloads naming another
     * endpoint are voided. A retired endpoint is resolved first (section 9), its resolved items dropping too.
     */
    public List<ItemStack> settleRemoval(TransitEndpoint endpoint, long gameTime) {
        now = gameTime;
        UUID id = endpoint.endpointId();
        UUID owner = endpoint.endpointOwner().orElse(null);
        detach(id);
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return List.of();
        }
        if (endpoint.transitFrozen()) {
            List<ItemStack> drops = new ArrayList<>();
            service.transitOperations().resolve(endpoint, null, gameTime, drops);
            service.forgetCandidate(id);
            return drops;
        }
        EndgameRoot root = view.get();
        long epoch = root.saveEpoch();
        boolean barrier = false;
        long dispatched = root.dispatchedThrough(id);
        for (OutboxEntry entry : endpoint.source().outbox()) {
            Optional<TransitRecord> record = root.transits().record(new TransitKey(id, entry.seq()));
            if (entry.seq() <= dispatched) {
                barrier |= record.isPresent() && !record.get().durable(epoch);
            } else {
                audit("OUTBOX_LOST_ON_REMOVAL", "OK", id, owner, "seq=" + entry.seq() + " payload="
                        + entry.payload().hash());
            }
        }
        List<TransitRecord> settled = new ArrayList<>();
        for (TransitRecord record : root.transits().forEndpoint(id)) {
            TransitKey key = record.key();
            switch (TransitRules.settleRemoval(record.facts(id, epoch), endpoint.destination().receipts().contains(key),
                    endpoint.destination().incoming().containsKey(key))) {
                case MOVED -> {
                    settled.add((record.state() == TransitRecord.State.CLAIMED ? record : record.claimed(id))
                            .acknowledged(epoch));
                    acknowledgedAt.put(key, now);
                    audit("REMOVAL_SETTLED", "moved", id, owner, "transfer=" + key);
                }
                case INCOMING -> {
                    if (record.state() == TransitRecord.State.CLAIMED) {
                        settled.add(record.returned());
                        audit("REMOVAL_SETTLED", "incoming", id, owner, "transfer=" + key);
                    }
                }
                default -> {
                }
            }
        }
        List<ItemStack> own = new ArrayList<>();
        endpoint.destination().incoming().forEach((key, payload) -> {
            Optional<TransitRecord> record = root.transits().record(key);
            if (record.isEmpty() || record.get().acknowledged() && id.equals(record.get().paidEndpoint())) {
                payload.decode().ifPresentOrElse(own::addAll, () -> audit("INCOMING_VOIDED", "undecodable", id,
                        owner, "transfer=" + key + " payload=" + payload.hash()));
            } else if (!record.get().destination().equals(id) && !id.equals(record.get().paidEndpoint())) {
                audit("INCOMING_VOIDED", "voided", id, owner, "transfer=" + key + " payload=" + payload.hash());
            }
        });
        barrier |= !settled.isEmpty();
        java.util.function.Function<EndgameRoot, Boolean> removal = r -> {
            settled.forEach(record -> r.transits().replace(record));
            return r.remove(id);
        };
        if (barrier) {
            service.barrier(removal);
            if (service.writePending() && !settlementPending) {
                settlementPending = true;
                AdvancedRocketryCommunity.LOGGER.error("ARCE_ENDGAME REMOVAL_SETTLEMENT_PENDING endpoint={}", id);
            }
        } else {
            service.coalesced(removal);
        }
        service.forgetCandidate(id);
        audit("endpoint_removed", "OK", id, owner, "settled=" + settled.size());
        return own;
    }

    /** A successful root write clears the pending-settlement flag (shown in the status until then). */
    void written() {
        settlementPending = false;
    }

    /**
     * Retirement without live state ({@code MISSING}, {@code endpoint retire}; review R3-M1), inside the caller's root
     * update: every claim paid at the endpoint and not acknowledged returns to arrived; {@code endpoint retire} also
     * prunes the stubs paid there, because the endpoint's chunk may never load again to show the saved move.
     */
    public static int retireWithoutLiveState(EndgameRoot root, UUID id, boolean pruneStubs) {
        int changed = 0;
        long epoch = root.saveEpoch();
        for (TransitRecord record : root.transits().forEndpoint(id)) {
            if (record.state() == TransitRecord.State.CLAIMED && id.equals(record.paidEndpoint())
                    && TransitRules.returnsOnRetirement(record.facts(id, epoch))) {
                root.transits().replace(record.returned());
                changed++;
            } else if (pruneStubs && record.stub() && id.equals(record.paidEndpoint())) {
                root.transits().remove(record.key());
                changed++;
            }
        }
        return changed;
    }

    // ---- Diagnostics -------------------------------------------------------------------------------------------

    String status() {
        Optional<EndgameRoot> view = service.root();
        int records = view.map(root -> root.transits().size()).orElse(0);
        int stubs = view.map(root -> root.transits().stubs()).orElse(0);
        return "transits=" + records + " stubs=" + stubs + " loaded_ledger_endpoints=" + loaded.size()
                + (settlementPending ? " REMOVAL_SETTLEMENT_PENDING" : "");
    }

    void clear() {
        loaded.clear();
        pending.clear();
        acknowledgedAt.clear();
        cursor = null;
        settlementPending = false;
    }
}
