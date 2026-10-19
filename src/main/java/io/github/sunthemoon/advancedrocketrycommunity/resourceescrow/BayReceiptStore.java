package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * The owner-thread RAM authority over one receipt root (W27A-DESIGN section 2). It keeps three states: the durable
 * (acknowledged) root, the writer's captured generation (active, or an exact unknown candidate) and the RAM root, which
 * holds queued mutations and a successor's contents. Its index answers from all three, so it stays answerable while the
 * writer is unavailable. Production creates no receipt: only gated test hooks call {@link #put} and {@link #remove}.
 */
public final class BayReceiptStore implements EscrowAdmissionAuthority.Store {
    /** BUSY: the op or its satellite already has a queued or captured change; retry after its exact acknowledgement. */
    public enum Status { QUEUED, STORE_UNAVAILABLE, BUSY, INVALID, RELEASED_LOCKED, RETAIN_REFUSED, RESERVE_REFUSED }
    /** A put or removal: queued with its exact acknowledgement to follow, or refused with nothing changed. */
    public record Admission(Status status, @Nullable EscrowAdmissionAuthority.Retention retention,
                            @Nullable EscrowAdmissionAuthority.Result reservation) {
        static Admission of(Status status) { return new Admission(status, null, null); }
        public boolean queued() { return status == Status.QUEUED; }
    }
    private record Mutation(UUID op, @Nullable BayReceipt before, @Nullable BayReceipt after, Consumer<BayReceiptWriter.Outcome> completion) {
        UUID satellite() { return (after != null ? after : before).satellite(); }
    }
    /** Derived from the three states and cached until the next change. */
    private record View(EscrowAdmissionAuthority.Index index, Set<UUID> blockingSatellites, Set<UUID> heldRockets) { }
    private final Thread owner = Thread.currentThread();
    private final UUID storeId;
    private final BayReceiptWriter writer;
    private final EscrowAdmissionAuthority.Delegate bay;
    private final EscrowAdmissionAuthority authority;
    private final LinkedHashMap<UUID, BayReceipt> current = new LinkedHashMap<>();
    private final List<Mutation> queued = new ArrayList<>();
    private List<Mutation> capturedMutations = List.of();
    private BayReceiptRoot durable;
    private long issuedThrough, lastTick, recoveryAt;
    private boolean closed;
    private View view;

    BayReceiptStore(BayReceiptRoot durable, BayReceiptWriter writer, EscrowAdmissionAuthority.Delegate bay) {
        EscrowTags.require(bay.consumer() == EscrowAdmissionAuthority.Consumer.BAY, "Receipt store needs the BAY delegate");
        durable.validate();
        this.storeId = durable.storeId(); this.durable = durable; this.writer = writer; this.bay = bay;
        this.authority = bay.authority(); issuedThrough = durable.issuedThrough();
        durable.receipts().forEach(receipt -> current.put(receipt.op(), receipt));
    }

    /** Attaches this store to its BAY delegate (LOADED); false, with nothing changed, while another store holds work. */
    boolean attach() { owner(); return bay.retarget(this); }

    /**
     * Appends or replaces the receipt of {@code next.op()}. A new op needs a retained slot, and a pending phase needs a
     * pending slot; both are checked before either is charged. Released receipts keep their flag and never re-arm.
     * While the op or its satellite has a change in flight, the put refuses with BUSY (R37 item 2).
     */
    Admission put(BayReceipt next, Consumer<BayReceiptWriter.Outcome> completion) {
        owner(); Objects.requireNonNull(next); Objects.requireNonNull(completion);
        if (unavailable()) { return Admission.of(Status.STORE_UNAVAILABLE); }
        if (inFlight(next.op(), next.satellite())) { return Admission.of(Status.BUSY); }
        BayReceipt before = current.get(next.op());
        if (before != null && before.released() && (!next.released() || next.phase() == BayReceipt.Phase.INTENT && before.phase() != BayReceipt.Phase.INTENT)) {
            return Admission.of(Status.RELEASED_LOCKED);
        }
        // Unique key and satellite against every state, not RAM alone, so a cancelled change can return to its old state.
        BayReceiptRoot captured = writer.captured();
        for (Collection<BayReceipt> state : List.of(durable.receipts(), captured == null ? List.<BayReceipt>of() : captured.receipts(), current.values())) {
            for (BayReceipt other : state) {
                if (!other.op().equals(next.op()) && (other.key().equals(next.key()) || other.satellite().equals(next.satellite()))) {
                    return Admission.of(Status.INVALID);
                }
            }
        }
        if (before != null && (!before.owner().equals(next.owner()) || !before.satellite().equals(next.satellite()))) {
            return Admission.of(Status.INVALID);
        }
        var retention = authority.checkRetain(next.op(), next.owner());
        if (retention != EscrowAdmissionAuthority.Retention.RESERVED && retention != EscrowAdmissionAuthority.Retention.ALREADY_HELD) {
            return new Admission(Status.RETAIN_REFUSED, retention, null);
        }
        EscrowAdmissionAuthority.Result reservation = null;
        if (next.pending()) {
            reservation = authority.checkReserve(next.op(), next.owner(), next.rocket(), EscrowAdmissionAuthority.Consumer.BAY);
            if (reservation != EscrowAdmissionAuthority.Result.RESERVED && reservation != EscrowAdmissionAuthority.Result.ALREADY_HELD) {
                return new Admission(Status.RESERVE_REFUSED, retention, reservation);
            }
            // The checks reserve nothing, so each charge must answer exactly as its check did (W27C1-R8).
            var charged = authority.tryReserve(next.op(), next.owner(), next.rocket(), EscrowAdmissionAuthority.Consumer.BAY);
            EscrowTags.require(charged == reservation, "Bay pending charge " + charged + " differs from its check " + reservation);
        }
        var held = authority.tryRetain(next.op(), next.owner());
        EscrowTags.require(held == retention, "Bay retained charge " + held + " differs from its check " + retention);
        current.put(next.op(), next); issuedThrough = Math.max(issuedThrough, next.seq());
        queue(new Mutation(next.op(), before, next, completion));
        return new Admission(Status.QUEUED, retention, reservation);
    }

    /** Removes the receipt of {@code op}; its slots end only at the exact removal acknowledgement. */
    Admission remove(UUID op, Consumer<BayReceiptWriter.Outcome> completion) {
        owner(); Objects.requireNonNull(completion);
        if (unavailable()) { return Admission.of(Status.STORE_UNAVAILABLE); }
        BayReceipt before = current.get(op);
        if (inFlight(op, before == null ? null : before.satellite())) { return Admission.of(Status.BUSY); }
        if (before == null) { return Admission.of(Status.INVALID); }
        if (before.released()) { return Admission.of(Status.RELEASED_LOCKED); }
        current.remove(op);
        queue(new Mutation(op, before, null, completion));
        return Admission.of(Status.QUEUED);
    }

    /** A queued or captured (active or exact unknown) change of this op or of this satellite. */
    private boolean inFlight(UUID op, @Nullable UUID satellite) {
        for (List<Mutation> mutations : List.of(queued, capturedMutations)) {
            for (Mutation mutation : mutations) {
                if (mutation.op().equals(op) || mutation.satellite().equals(satellite)) { return true; }
            }
        }
        return false;
    }
    private void queue(Mutation mutation) {
        queued.add(mutation); changed();
        // The index now owns the reservations queued in the authority, so a later cancellation releases them.
        authority.refresh();
        requestWrite();
    }
    private void requestWrite() {
        if (queued.isEmpty() || closed || writer.hasSuccessor()) { return; } // A pending successor captures RAM later.
        writer.request(this::capture, this::completed);
    }
    private BayReceiptRoot capture() {
        var root = new BayReceiptRoot(storeId, issuedThrough, writer.lastGeneration() + 1, List.copyOf(current.values()));
        capturedMutations = List.copyOf(queued); queued.clear(); changed();
        return root;
    }
    private void completed(BayReceiptWriter.Completion completion) {
        var outcome = completion.outcome();
        if (outcome != BayReceiptWriter.Outcome.COMMITTED) { recoveryAt = lastTick + BayReceiptWriter.RETRY_TICKS; }
        if (outcome == BayReceiptWriter.Outcome.COMMITTED) {
            durable = completion.root();
        } else if (outcome != BayReceiptWriter.Outcome.UNKNOWN) {
            // A definite failure before replacement cancels the generation's mutations. Nothing later can supersede one,
            // because an op or satellite has at most one change in flight, so each returns exactly to what it replaced.
            for (Mutation mutation : capturedMutations) {
                if (mutation.before() == null) { current.remove(mutation.op()); } else { current.put(mutation.op(), mutation.before()); }
            }
        }
        var finished = capturedMutations;
        if (outcome != BayReceiptWriter.Outcome.UNKNOWN) { capturedMutations = List.of(); }
        changed(); authority.refresh();
        finished.forEach(mutation -> mutation.completion().accept(outcome));
    }

    public void tick(long now) {
        owner(); lastTick = now; writer.tick(now);
        if (closed || !writer.idle()) { return; }
        if (!queued.isEmpty()) {
            requestWrite();
        } else if (writer.recoverable() && now >= recoveryAt) {
            // R37 item 1 (W27C1-R1): unavailable with no write in flight, the store writes one generation with no new
            // change at most once per 200 ticks after its last failed write, and its exact commit makes it available
            // again. An idle writer means RAM holds exactly the acknowledged receipts (R37 item 2), so this publishes
            // no queued change; only the issued-sequence counter may be higher.
            recoveryAt = now + BayReceiptWriter.RETRY_TICKS;
            writer.request(this::capture, this::completed);
        }
    }
    /** ServerStoppingEvent: drain the writer, then refuse new work. */
    void close() { owner(); if (closed) { return; } writer.close(); closed = true; changed(); }
    /** Crash path: no callback is published; the target file stays authoritative. */
    void abort() { owner(); closed = true; writer.abort(); changed(); }

    @Override public EscrowAdmissionAuthority.Index index() { owner(); return view().index(); }
    @Override public boolean holdsWork() { owner(); return !queued.isEmpty() || !writer.idle(); }
    /** BAY_RECEIPTS_UNAVAILABLE: the writer is unavailable, or the store has stopped. */
    public boolean unavailable() { owner(); return closed || writer.unavailable(); }
    /** Whether a receipt blocks insertion of this satellite's package: INTENT, REGISTERED or REPAIR in any state. */
    public boolean blocks(UUID satellite) { owner(); return view().blockingSatellites().contains(satellite); }
    /** Whether an INTENT, REGISTERED, REPAIR or released receipt names this rocket in any state. */
    public boolean holdsRocket(UUID rocket) { owner(); return view().heldRockets().contains(rocket); }
    public BayReceiptRoot durable() { owner(); return durable; }
    public List<BayReceipt> receipts() { owner(); return List.copyOf(current.values()); }
    long issuedThrough() { owner(); return issuedThrough; }
    BayReceiptWriter writer() { owner(); return writer; }
    /** The receipt naming {@code satellite} in any state, newest first. */
    public Optional<BayReceipt> find(UUID satellite) {
        owner();
        for (BayReceiptRoot root : states(true)) {
            for (BayReceipt receipt : root.receipts()) { if (receipt.satellite().equals(satellite)) { return Optional.of(receipt); } }
        }
        return Optional.empty();
    }
    /** Exactly acknowledged: the op's receipt is identical in the durable, captured and RAM states, with no mutation pending. */
    public boolean acknowledged(UUID op) {
        owner(); BayReceipt ram = current.get(op);
        if (ram == null || !ram.equals(receipt(durable, op))) { return false; }
        BayReceiptRoot captured = writer.captured();
        return (captured == null || ram.equals(receipt(captured, op)))
                && queued.stream().noneMatch(m -> m.op().equals(op)) && capturedMutations.stream().noneMatch(m -> m.op().equals(op));
    }
    private static BayReceipt receipt(BayReceiptRoot root, UUID op) {
        for (BayReceipt receipt : root.receipts()) { if (receipt.op().equals(op)) { return receipt; } }
        return null;
    }
    /** Oldest first unless {@code newest}: durable, captured, then RAM. */
    private List<BayReceiptRoot> states(boolean newest) {
        var states = new ArrayList<BayReceiptRoot>(3); states.add(durable);
        BayReceiptRoot captured = writer.captured(); if (captured != null) { states.add(captured); }
        states.add(new BayReceiptRoot(storeId, issuedThrough, 0, List.copyOf(current.values())));
        if (newest) { java.util.Collections.reverse(states); }
        return states;
    }
    private View view() {
        if (view != null) { return view; }
        var pending = new LinkedHashMap<UUID, EscrowAdmissionAuthority.Pending>();
        var retained = new LinkedHashMap<UUID, EscrowAdmissionAuthority.Retained>();
        Set<UUID> blocking = new HashSet<>(), rockets = new HashSet<>();
        for (BayReceiptRoot root : states(false)) {
            for (BayReceipt receipt : root.receipts()) {
                retained.put(receipt.op(), new EscrowAdmissionAuthority.Retained(receipt.op(), receipt.owner()));
                if (receipt.pending()) {
                    // Newest pending state wins: a re-armed REJECTED receipt charges its new rocket.
                    pending.put(receipt.op(), new EscrowAdmissionAuthority.Pending(receipt.op(), receipt.owner(), receipt.rocket(),
                            EscrowAdmissionAuthority.Consumer.BAY));
                    rockets.add(receipt.rocket());
                }
                if (receipt.blocking()) { blocking.add(receipt.satellite()); }
            }
        }
        view = new View(new EscrowAdmissionAuthority.Index(true, pending.values(), retained.values()), Set.copyOf(blocking), Set.copyOf(rockets));
        return view;
    }
    private void changed() { view = null; }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Bay receipt store requires its owner thread"); }
}
