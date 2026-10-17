package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One owner-thread authority for the unique pending union, not a separate quota per store or writer. The server's
 * authority is runtime-owned ({@link EscrowRuntime}); each store's ledger-owned coordinator attaches to a delegate
 * that the runtime registered, and never registers one itself.
 */
public final class EscrowAdmissionAuthority {
    public enum Consumer { PROPULSION, BAY }
    public enum Result { RESERVED, ALREADY_HELD, IDENTITY_CONFLICT, SERVER_FULL, ROCKET_FULL, UNAVAILABLE }
    public record Pending(UUID op, UUID owner, UUID rocket, Consumer consumer) {
        public Pending { Objects.requireNonNull(op); Objects.requireNonNull(owner); Objects.requireNonNull(rocket); Objects.requireNonNull(consumer); }
        static Pending of(EscrowOperation op) { return new Pending(op.id(), op.owner(), op.guard().rocket(), Consumer.PROPULSION); }
    }
    /** Includes every unresolved durable/queued/captured/unknown state, even REPAIR or released flight holds. */
    public record Index(boolean loaded, Collection<Pending> pending) {
        public Index { pending = java.util.List.copyOf(pending); }
    }
    /** A store's coordinator as its delegate sees it: its complete RAM index, and whether it still holds work. */
    interface Store {
        Index index();
        /** Queued, captured or UNKNOWN work, whose reservations would disappear with a detached store. */
        boolean holdsWork();
    }
    /**
     * One consumer's registered index (W11B3-DESIGN section 2, in the terms of W27A section 2). NOT_LOADED lasts from
     * registration until the startup lookup and reports an unloaded index; NO_FILE is loaded and known to be empty;
     * LOADED reads the attached store. A refresh reads only the held reference and never touches storage.
     */
    public static final class Delegate implements Supplier<Index> {
        public enum State { NOT_LOADED, NO_FILE, LOADED }
        private final EscrowAdmissionAuthority authority;
        private final Consumer consumer;
        private State state = State.NOT_LOADED;
        private Store store;
        private Delegate(EscrowAdmissionAuthority authority, Consumer consumer) { this.authority = authority; this.consumer = consumer; }
        public Consumer consumer() { return consumer; }
        public State state() { authority.owner(); return state; }
        EscrowAdmissionAuthority authority() { return authority; }
        /** The startup lookup found no file. A store that attached earlier keeps the delegate LOADED. */
        void noFile() {
            authority.owner(); if (state == State.NOT_LOADED) { state = State.NO_FILE; authority.refresh(); }
        }
        boolean holdsWork() { authority.owner(); return store != null && store.holdsWork(); }
        /**
         * Attaches a store, or with null returns to NO_FILE. Refuses, changing nothing, while a different attached store
         * still holds queued, captured or UNKNOWN work, so no reservation disappears with it.
         */
        boolean retarget(Store next) {
            authority.owner();
            if (next == store && (next != null || state == State.NO_FILE)) { return true; }
            if (store != null && store != next && store.holdsWork()) { return false; }
            store = next; state = next == null ? State.NO_FILE : State.LOADED; authority.refresh();
            return true;
        }
        @Override public Index get() {
            return switch (state) {
                case NOT_LOADED -> new Index(false, java.util.List.of());
                case NO_FILE -> new Index(true, java.util.List.of());
                case LOADED -> store.index();
            };
        }
    }
    private final Thread owner = Thread.currentThread();
    private final Map<Consumer, Supplier<Index>> indexes = new EnumMap<>(Consumer.class);
    private final Map<UUID, Pending> queuedReservations = new HashMap<>();
    private Map<UUID, Pending> union = Map.of();
    private Set<UUID> conflicts = Set.of();
    private boolean available;

    /** Registers a NOT_LOADED delegate for the consumer. A second registration of one consumer still throws. */
    public Delegate register(Consumer consumer) {
        owner(); var delegate = new Delegate(this, Objects.requireNonNull(consumer)); registerIndex(consumer, delegate);
        return delegate;
    }
    /** W27c1 adds BAY before admission, initially with NOT_LOADED, and then exposes its startup RAM index. */
    public void registerIndex(Consumer consumer, Supplier<Index> index) {
        owner();
        if (indexes.putIfAbsent(Objects.requireNonNull(consumer), Objects.requireNonNull(index)) != null) {
            throw new IllegalStateException("Admission index already registered");
        }
        refresh();
    }
    public Result tryReserve(UUID op, UUID owner, UUID rocket, Consumer consumer) {
        owner(); refresh();
        Pending request = new Pending(op, owner, rocket, consumer);
        Pending existing = union.get(op);
        if (conflicts.contains(op) || existing != null && !request.equals(existing)) { return Result.IDENTITY_CONFLICT; }
        if (!available || !indexes.containsKey(consumer) || !conflicts.isEmpty()) { return Result.UNAVAILABLE; }
        if (existing != null) { return Result.ALREADY_HELD; }
        if (union.size() >= EscrowLimits.OPERATIONS) { return Result.SERVER_FULL; }
        if (union.values().stream().filter(entry -> rocket.equals(entry.rocket())).count() >= EscrowLimits.PER_ROCKET) {
            return Result.ROCKET_FULL;
        }
        queuedReservations.put(op, request);
        var next = new HashMap<>(union); next.put(op, request); union = Map.copyOf(next);
        return Result.RESERVED;
    }
    /** Recompute from the complete RAM indexes. There is no operation-ID release or timeout eviction. */
    public void refresh() {
        owner(); var next = new HashMap<UUID, Pending>(); var collision = new HashSet<UUID>();
        boolean loaded = !indexes.isEmpty();
        for (var entry : indexes.entrySet()) {
            Index index = Objects.requireNonNull(entry.getValue().get()); loaded &= index.loaded();
            for (Pending pending : index.pending()) {
                if (pending.consumer() != entry.getKey()) { throw new IllegalArgumentException("Foreign admission index identity"); }
                Pending previous = next.putIfAbsent(pending.op(), pending);
                if (previous != null && !previous.equals(pending)) { collision.add(pending.op()); }
            }
        }
        // Once a reservation has entered its consumer's queue, that index owns its whole unresolved lifecycle.
        // Consumers remove it only after exact durable resolution, including every captured/unknown candidate.
        queuedReservations.entrySet().removeIf(entry -> entry.getValue().equals(next.get(entry.getKey())));
        for (Pending pending : queuedReservations.values()) {
            Pending previous = next.putIfAbsent(pending.op(), pending);
            if (previous != null && !previous.equals(pending)) { collision.add(pending.op()); }
        }
        union = Map.copyOf(next); conflicts = Set.copyOf(collision); available = loaded;
    }
    public int pendingCount() { owner(); refresh(); return union.size(); }
    public long pendingFor(UUID rocket) { owner(); refresh(); return union.values().stream().filter(entry -> rocket.equals(entry.rocket())).count(); }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Admission authority requires its owner thread"); }
    }
}
