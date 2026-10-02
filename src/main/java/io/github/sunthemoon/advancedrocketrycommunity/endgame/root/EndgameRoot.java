package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPairTable;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameIdOrder;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The cross-device state of ADR-054 section 10 that C11 owns: the endpoint index (section 9), tombstones (section 11,
 * review R3-M2), {@code dispatched_through} values and protected zones (section 6), with the save epoch of ADR-050
 * section 2, the transit ledger (section 11) and the elevator pairs (ADR-059 section 1). Every collection iterates in
 * ADR-054 ID order, so encoding is deterministic. Mutations only change memory; the caller decides the write class
 * (section 10).
 */
public final class EndgameRoot {
    private static final Comparator<Tombstone.Settled> OLDEST = Comparator.comparingInt(Tombstone.Settled::order)
            .thenComparing(Tombstone.Settled::id, EndgameIdOrder.ORDER);

    private final Map<UUID, EndpointRecord> endpoints = new TreeMap<>(EndgameIdOrder.ORDER);
    private final Map<UUID, Tombstone.Young> young = new TreeMap<>(EndgameIdOrder.ORDER);
    private final Map<UUID, Tombstone.Settled> settled = new TreeMap<>(EndgameIdOrder.ORDER);
    private final Map<UUID, Long> dispatchedThrough = new TreeMap<>(EndgameIdOrder.ORDER);
    private final Map<String, ProtectedZone> zones = new TreeMap<>();
    private final TransitTable transits = new TransitTable(() -> changedSinceEpoch = true);
    private final ElevatorPairTable pairs = new ElevatorPairTable(() -> changedSinceEpoch = true);
    private final Set<UUID> touched = new HashSet<>();
    private long saveEpoch;
    private boolean changedSinceEpoch;
    private int nextSettleOrder;

    private EndgameRoot(long saveEpoch, int nextSettleOrder) {
        if (saveEpoch < 1L) {
            throw new IllegalArgumentException("The save epoch starts at 1");
        }
        if (nextSettleOrder < 0) {
            throw new IllegalArgumentException("The settlement order is not negative");
        }
        this.saveEpoch = saveEpoch;
        this.nextSettleOrder = nextSettleOrder;
    }

    public static EndgameRoot create() {
        return new EndgameRoot(1L, 0);
    }

    // ---- Queries ----------------------------------------------------------------------------------------------

    public Optional<EndpointRecord> endpoint(UUID id) {
        return Optional.ofNullable(endpoints.get(id));
    }

    public Collection<EndpointRecord> endpoints() {
        return List.copyOf(endpoints.values());
    }

    public Collection<Tombstone.Young> youngTombstones() {
        return List.copyOf(young.values());
    }

    public Collection<Tombstone.Settled> settledTombstones() {
        return List.copyOf(settled.values());
    }

    /** Young and settled tombstones, counted without copying either collection. */
    public int tombstoneCount() {
        return young.size() + settled.size();
    }

    /**
     * The IDs whose endpoint record or tombstone changed since the last call, so an index follows each mutation
     * without a full rebuild (review C11R-M5).
     */
    public Set<UUID> drainTouched() {
        if (touched.isEmpty()) {
            return Set.of();
        }
        Set<UUID> drained = Set.copyOf(touched);
        touched.clear();
        return drained;
    }

    private void changed(UUID id) {
        changedSinceEpoch = true;
        touched.add(id);
    }

    public Optional<Tombstone> tombstone(UUID id) {
        Tombstone value = young.get(id);
        return Optional.ofNullable(value != null ? value : settled.get(id));
    }

    public Map<UUID, Long> dispatchedThrough() {
        return Map.copyOf(dispatchedThrough);
    }

    /** {@code dispatched_through[id]}: 0 from registration, so absent means 0 (review R4-L2). */
    public long dispatchedThrough(UUID id) {
        return dispatchedThrough.getOrDefault(id, 0L);
    }

    public Collection<ProtectedZone> zones() {
        return List.copyOf(zones.values());
    }

    public Optional<ProtectedZone> zone(String name) {
        return Optional.ofNullable(zones.get(name));
    }

    /** A retired ID: a tombstone of either age, or a {@code MISSING} index record (section 9). */
    public boolean retired(UUID id) {
        EndpointRecord record = endpoints.get(id);
        return young.containsKey(id) || settled.containsKey(id)
                || record != null && record.state() == EndpointRecord.State.MISSING;
    }

    /** Escrow, selection as a destination and claims need a durable registration (review R3-H1). */
    public boolean registrationDurable(UUID id) {
        EndpointRecord record = endpoints.get(id);
        return record != null && record.state() == EndpointRecord.State.ACTIVE && saveEpoch > record.registeredEpoch();
    }

    /** Endpoint places in use: index records of any state plus young tombstones (review R3-M2). */
    public int places() {
        return endpoints.size() + young.size();
    }

    public int places(UUID owner) {
        int count = 0;
        for (EndpointRecord record : endpoints.values()) {
            count += record.owner().equals(owner) ? 1 : 0;
        }
        for (Tombstone.Young tombstone : young.values()) {
            count += tombstone.owner().equals(owner) ? 1 : 0;
        }
        return count;
    }

    /**
     * Section 10 growth accounting: each section's count times its worst-case record size, never an encoding of the
     * root. Young tombstones take endpoint places.
     */
    public long accountedBytes() {
        return (long) places() * EndgameLimits.ENDPOINT_RECORD_BYTES
                + (long) dispatchedThrough.size() * EndgameLimits.DISPATCHED_THROUGH_ENTRY_BYTES
                + transits.accountedBytes()
                + pairs.accountedBytes()
                + (long) settled.size() * EndgameLimits.TOMBSTONE_RECORD_BYTES
                + (long) zones.size() * EndgameLimits.ZONE_RECORD_BYTES;
    }

    // ---- Transit ledger (section 11) ---------------------------------------------------------------------------

    /** The transit records; the ledger changes them only inside a root update. */
    public TransitTable transits() {
        return transits;
    }

    /**
     * A tombstone that a transit record or an elevator pair names is pinned: never evicted by a cap, housekeeping or a
     * command.
     */
    public boolean pinned(UUID id) {
        return transits.names(id) || pairs.names(id);
    }

    /** The elevator pairs (ADR-059 section 1); binds and unbinds change them only inside a root update. */
    public ElevatorPairTable pairs() {
        return pairs;
    }

    /** Step 2: the record exists and {@code dispatched_through[S] = seq}, which never decreases. */
    public void registerTransit(TransitRecord record) {
        UUID source = record.key().source();
        if (record.key().seq() <= dispatchedThrough(source)) {
            throw new IllegalStateException("A transfer at or below dispatched_through: " + record.key());
        }
        transits.add(record);
        dispatchedThrough.put(source, record.key().seq());
        changedSinceEpoch = true;
    }

    // ---- Endpoint index (section 9) ----------------------------------------------------------------------------

    /**
     * Registration from the endpoint's persisted tag. A retired ID, or an unknown ID whose tag carries outbox entries,
     * incoming payloads, receipts or a recorded freeze, is frozen instead ({@code ENDPOINT_RETIRED}, review R3-H1,
     * R4-L3). Young tombstones take endpoint places, and the accounted size must stay within 3 MiB.
     */
    public EndgameCode register(UUID id, ResourceLocation kind, UUID owner, ResourceLocation level, long pos,
                                boolean tagFrozen, int globalLimit, int ownerLimit) {
        Objects.requireNonNull(id, "id");
        if (kind.toString().length() > EndpointRecord.MAX_KIND_LENGTH
                || level.toString().length() > EndpointRecord.MAX_LEVEL_LENGTH) {
            // A record cannot hold the key: refused, never thrown on the server tick (review C11R-L3).
            return EndgameCode.TARGET_OUT_OF_BOUNDS;
        }
        EndpointRecord existing = endpoints.get(id);
        if (existing != null) {
            if (existing.state() == EndpointRecord.State.MISSING) {
                return EndgameCode.ENDPOINT_RETIRED;
            }
            return existing.level().equals(level) && existing.pos() == pos
                    ? EndgameCode.OK : EndgameCode.ENDPOINT_POSITION_CONFLICT;
        }
        if (retired(id) || tagFrozen) {
            return EndgameCode.ENDPOINT_RETIRED;
        }
        if (places() >= globalLimit || places(owner) >= ownerLimit) {
            return EndgameCode.ENDPOINT_LIMIT;
        }
        if (accountedBytes() + EndgameLimits.ENDPOINT_RECORD_BYTES > EndgameLimits.GROWTH_ADMISSION_BYTES) {
            return EndgameCode.ROOT_FULL;
        }
        endpoints.put(id, new EndpointRecord(id, kind, owner, level, pos, EndpointRecord.State.ACTIVE, saveEpoch));
        changed(id);
        return EndgameCode.OK;
    }

    /** Removal by any cause with live state (section 9.1): the record becomes a young tombstone. */
    public boolean remove(UUID id) {
        EndpointRecord record = endpoints.get(id);
        if (record == null || record.state() != EndpointRecord.State.ACTIVE) {
            return false;
        }
        endpoints.remove(id);
        young.put(id, new Tombstone.Young(id, record.owner(), record.level(), record.pos()));
        changed(id);
        return true;
    }

    /** The block entity is gone from its loaded chunk, observed in an aged chunk tag: retired, kept as MISSING. */
    public boolean markMissing(UUID id) {
        EndpointRecord record = endpoints.get(id);
        if (record == null || record.state() != EndpointRecord.State.ACTIVE) {
            return false;
        }
        endpoints.put(id, record.withState(EndpointRecord.State.MISSING));
        changed(id);
        return true;
    }

    /** {@code endpoint forget}: an owner drops their own MISSING record; its absence is known, so it settles. */
    public Change forget(UUID id, UUID actor, boolean operator, Predicate<UUID> pinned) {
        EndpointRecord record = endpoints.get(id);
        if (record == null || record.state() != EndpointRecord.State.MISSING) {
            return Change.refused(EndgameCode.ENDPOINT_NOT_FOUND);
        }
        if (!operator && !record.owner().equals(actor)) {
            return Change.refused(EndgameCode.UNAUTHORIZED);
        }
        endpoints.remove(id);
        return settle(new Tombstone.Young(id, record.owner(), record.level(), record.pos()), pinned);
    }

    /**
     * {@code endpoint retire} of an endpoint that is lost but not MISSING. The caller refuses it while the endpoint's
     * chunk is loaded (review R4-L3). The operator asserts the chunk will not load again, so the tombstone settles at
     * once (review R4-L1).
     */
    public Change retireLost(UUID id, Predicate<UUID> pinned) {
        EndpointRecord record = endpoints.remove(id);
        if (record == null) {
            return Change.refused(EndgameCode.ENDPOINT_NOT_FOUND);
        }
        return settle(new Tombstone.Young(id, record.owner(), record.level(), record.pos()), pinned);
    }

    /** A young tombstone whose absence an aged save or load tag showed, or {@code tombstone settle} (R4-L1). */
    public Change settle(UUID id, Predicate<UUID> pinned) {
        Tombstone.Young tombstone = young.remove(id);
        if (tombstone == null) {
            return Change.refused(EndgameCode.ENDPOINT_NOT_FOUND);
        }
        return settle(tombstone, pinned);
    }

    private Change settle(Tombstone.Young tombstone, Predicate<UUID> pinned) {
        settled.put(tombstone.id(), tombstone.settle(nextSettleOrder));
        nextSettleOrder = Math.addExact(nextSettleOrder, 1);
        changed(tombstone.id());
        return Change.done(enforceCaps(tombstone.owner(), pinned));
    }

    /**
     * Review R3-M2 hard caps: at most 256 settled, unpinned tombstones per owner and 8,192 on the server; beyond a
     * cap the oldest such tombstone of that owner (or of the server) is evicted.
     */
    private List<UUID> enforceCaps(UUID owner, Predicate<UUID> pinned) {
        List<UUID> evicted = new ArrayList<>();
        List<Tombstone.Settled> ownerEligible = eligible(pinned, owner);
        for (int i = 0; ownerEligible.size() - i > EndgameLimits.MAX_SETTLED_TOMBSTONES_PER_OWNER; i++) {
            evicted.add(evict(ownerEligible.get(i).id()));
        }
        List<Tombstone.Settled> allEligible = eligible(pinned, null);
        for (int i = 0; allEligible.size() - i > EndgameLimits.MAX_SETTLED_TOMBSTONES; i++) {
            evicted.add(evict(allEligible.get(i).id()));
        }
        return evicted;
    }

    /**
     * Housekeeping once more than 4,096 tombstones exist: unpinned settled ones whose absence a chunk-load tag read
     * back after the last start at least 6,000 ticks earlier, oldest first.
     */
    public List<UUID> housekeep(Predicate<UUID> readBackAged, Predicate<UUID> pinned) {
        List<UUID> evicted = new ArrayList<>();
        for (Tombstone.Settled tombstone : eligible(pinned, null)) {
            if (young.size() + settled.size() <= EndgameLimits.TOMBSTONE_HOUSEKEEPING_THRESHOLD) {
                break;
            }
            if (readBackAged.test(tombstone.id())) {
                evicted.add(evict(tombstone.id()));
            }
        }
        return evicted;
    }

    /** {@code tombstone evict <player>}: every settled, unpinned tombstone of that owner. */
    public List<UUID> evictOwner(UUID owner, Predicate<UUID> pinned) {
        List<UUID> evicted = new ArrayList<>();
        for (Tombstone.Settled tombstone : eligible(pinned, owner)) {
            evicted.add(evict(tombstone.id()));
        }
        return evicted;
    }

    private List<Tombstone.Settled> eligible(Predicate<UUID> pinned, UUID owner) {
        return settled.values().stream()
                .filter(tombstone -> owner == null || tombstone.owner().equals(owner))
                .filter(tombstone -> !pinned.test(tombstone.id()) && !pinned(tombstone.id()))
                .sorted(OLDEST)
                .toList();
    }

    private UUID evict(UUID id) {
        settled.remove(id);
        dispatchedThrough.remove(id);
        changed(id);
        return id;
    }

    /** {@code device owner}: the index follows a device whose owner an operator changed. */
    public EndgameCode reassign(UUID id, UUID owner, int ownerLimit) {
        EndpointRecord record = endpoints.get(id);
        if (record == null) {
            return EndgameCode.ENDPOINT_NOT_FOUND;
        }
        if (record.owner().equals(owner)) {
            return EndgameCode.OK;
        }
        if (places(owner) >= ownerLimit) {
            return EndgameCode.ENDPOINT_LIMIT;
        }
        endpoints.put(id, new EndpointRecord(id, record.kind(), owner, record.level(), record.pos(), record.state(),
                record.registeredEpoch()));
        changed(id);
        return EndgameCode.OK;
    }

    // ---- Zones (section 6) -------------------------------------------------------------------------------------

    public EndgameCode addZone(ProtectedZone zone, int zoneLimit) {
        Objects.requireNonNull(zone, "zone");
        if (zones.containsKey(zone.name())) {
            return EndgameCode.ZONE_EXISTS;
        }
        if (zones.size() >= zoneLimit) {
            return EndgameCode.ZONE_LIMIT;
        }
        if (accountedBytes() + EndgameLimits.ZONE_RECORD_BYTES > EndgameLimits.GROWTH_ADMISSION_BYTES) {
            return EndgameCode.ROOT_FULL;
        }
        zones.put(zone.name(), zone);
        changedSinceEpoch = true;
        return EndgameCode.OK;
    }

    public EndgameCode removeZone(String name) {
        if (zones.remove(name) == null) {
            return EndgameCode.ZONE_NOT_FOUND;
        }
        changedSinceEpoch = true;
        return EndgameCode.OK;
    }

    // ---- Save epoch (ADR-050 section 2) ------------------------------------------------------------------------

    public long saveEpoch() {
        return saveEpoch;
    }

    /** The epoch a write carries: E + 1 when something changed since the last successful write, else E. */
    public long epochToWrite() {
        return changedSinceEpoch ? Math.addExact(saveEpoch, 1L) : saveEpoch;
    }

    public boolean changedSinceEpoch() {
        return changedSinceEpoch;
    }

    /** Called after a write of the current snapshot returned without error. */
    public void markPersisted() {
        if (changedSinceEpoch) {
            saveEpoch = Math.addExact(saveEpoch, 1L);
            changedSinceEpoch = false;
        }
    }

    int nextSettleOrder() {
        return nextSettleOrder;
    }

    // ---- Restore (codec only) ----------------------------------------------------------------------------------

    static EndgameRoot restore(long saveEpoch, int nextSettleOrder) {
        return new EndgameRoot(saveEpoch, nextSettleOrder);
    }

    void restoreEndpoint(EndpointRecord record) {
        requireNew(record.id());
        if (record.registeredEpoch() > saveEpoch) {
            throw new IllegalArgumentException("An endpoint registered after the root's save epoch");
        }
        endpoints.put(record.id(), record);
    }

    void restoreYoung(Tombstone.Young tombstone) {
        requireNew(tombstone.id());
        young.put(tombstone.id(), tombstone);
    }

    void restoreSettled(Tombstone.Settled tombstone) {
        requireNew(tombstone.id());
        if (tombstone.order() >= nextSettleOrder) {
            throw new IllegalArgumentException("A settled tombstone is newer than the root's settlement order");
        }
        settled.put(tombstone.id(), tombstone);
    }

    void restoreDispatchedThrough(UUID id, long value) {
        if (value < 1L) {
            throw new IllegalArgumentException("Only positive dispatched_through values are stored");
        }
        if (dispatchedThrough.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Duplicate dispatched_through entry");
        }
    }

    void restoreTransit(TransitRecord record) {
        if (record.dispatchEpoch() > saveEpoch || record.ackEpoch() > saveEpoch) {
            throw new IllegalArgumentException("A transit record is newer than the root's save epoch");
        }
        transits.restore(record);
    }

    void restorePair(ElevatorPair pair) {
        pairs.restore(pair);
    }

    void restoreZone(ProtectedZone zone) {
        if (zones.putIfAbsent(zone.name(), zone) != null) {
            throw new IllegalArgumentException("Duplicate zone name");
        }
    }

    /** Cross-section checks after every section decoded; the hard maxima, not the lowered config limits, apply. */
    void finishRestore() {
        for (UUID id : dispatchedThrough.keySet()) {
            if (!endpoints.containsKey(id) && !young.containsKey(id) && !settled.containsKey(id)) {
                throw new IllegalArgumentException("A dispatched_through entry names no endpoint or tombstone");
            }
        }
        if (places() > EndgameLimits.MAX_ENDPOINTS) {
            throw new IllegalArgumentException("The endpoint index exceeds its fixed bound");
        }
        if (settled.size() > EndgameLimits.MAX_SETTLED_TOMBSTONES + EndgameLimits.MAX_PINNED_TOMBSTONES) {
            throw new IllegalArgumentException("The tombstone table exceeds its fixed bound");
        }
        if (dispatchedThrough.size() > EndgameLimits.MAX_ENDPOINTS + EndgameLimits.MAX_SETTLED_TOMBSTONES
                + EndgameLimits.MAX_PINNED_TOMBSTONES) {
            throw new IllegalArgumentException("dispatched_through exceeds its fixed bound");
        }
        if (zones.size() > EndgameLimits.MAX_ZONES) {
            throw new IllegalArgumentException("The zone list exceeds its fixed bound");
        }
        for (TransitRecord record : transits.records()) {
            // Pins keep the source and the paid endpoint a record names; dispatched_through never falls below a
            // registered seq. The destination may be unknown: section 11 step 2 registers an entry whose destination
            // was removed meanwhile, and its tombstone may have been evicted before (review C12R-H1); such a record
            // is DESTINATION_MISSING until a redirect or a purge.
            for (UUID id : new UUID[] {record.key().source(), record.paidEndpoint()}) {
                if (id != null && !endpoints.containsKey(id) && !young.containsKey(id) && !settled.containsKey(id)) {
                    throw new IllegalArgumentException("A transit record names no endpoint or tombstone");
                }
            }
            if (record.key().seq() > dispatchedThrough(record.key().source())) {
                throw new IllegalArgumentException("A transit record is above its source's dispatched_through");
            }
        }
        for (ElevatorPair pair : pairs.pairs()) {
            for (UUID id : new UUID[] {pair.anchorId(), pair.terminalId()}) {
                if (!endpoints.containsKey(id) && !young.containsKey(id) && !settled.containsKey(id)) {
                    throw new IllegalArgumentException("An elevator pair names no endpoint or tombstone");
                }
            }
        }
    }

    private void requireNew(UUID id) {
        if (endpoints.containsKey(id) || young.containsKey(id) || settled.containsKey(id)) {
            throw new IllegalArgumentException("An endpoint ID appears twice in the root");
        }
    }

    /** A mutation's outcome; {@code evicted} lists the tombstones the caps removed, each audited. */
    public record Change(EndgameCode code, List<UUID> evicted) {
        public Change {
            Objects.requireNonNull(code, "code");
            evicted = List.copyOf(evicted);
        }

        static Change done(List<UUID> evicted) {
            return new Change(EndgameCode.OK, evicted);
        }

        static Change refused(EndgameCode code) {
            return new Change(code, List.of());
        }

        public boolean ok() {
            return code == EndgameCode.OK;
        }
    }
}
