package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/**
 * W98, P1-N3 leaf N3-A1 (Root decision R34; W96 sections 3 and 4 with amendments W96-R1 to W96-R5). An ordinary source
 * return (I1 fail-back, I2 recovery, I3 last reconnect) keeps its actual record T until the survivor's own entity slot,
 * and the absence of every discarded copy, are read back from disk. Only then is T removed, and the caller's effects run
 * on that removal's acknowledgement.
 *
 * <p>Server-service lifetime and RAM only: after a restart, T recovers through the four-case matrix, or the already
 * proved survivor stands alone. One entry per logical rocket, at most 64. The journal reservation is held only for one
 * attempt of at most {@link #ATTEMPT_TICKS} (R1); between attempts T stays settled, so launch, refuelling and disassembly
 * refuse L and recovery skips it. Proofs use the shared barrier's ordinary sub-quota and a silent sink (R5). No saved
 * format, ticket type, governed path, lease or replay rule changes. W116 (W98-R10, R12): this class is the state machine
 * only; it reaches the world through a {@link Port}, the guards are {@link RocketRetirementGuards}'s.
 */
final class RocketOrdinaryRetirement {
    static final int MAX_PENDING = RocketFlightLimits.MAX_ACTIVE_TRANSFERS;
    /** Proof attempts at once; each holds one reservation and its census's watches. */
    static final int MAX_ATTEMPTS = 4;
    /** One attempt's reservation: a force batch (20 ticks) and its readback, with margin (R1). */
    static final int ATTEMPT_TICKS = 100;
    /** After IO, force, bounds, veto or deadline failures (R11: never after a superseding save). */
    static final int RETRY_TICKS = 200;
    /** Cheap re-checks of a waiting crew, an unloaded slot or a save-off Level, with no reservation. */
    static final int POLL_TICKS = 20;
    /** The survivor's slot plus the discarded candidates' slots of both ends. */
    static final int MAX_SLOTS = 1 + 2 * RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES;

    enum Site { FAIL_BACK, RECOVERED_SOURCE, RECONNECT, BEFORE_FILL }

    /** Where the survivor is: loaded, absent from its loaded slot, or not visible because that slot is unloaded. */
    enum Presence { LOADED, GONE, UNLOADED }

    /** A census slot: its entity sections loaded, not loaded (or no Level), or loaded in a save-off Level. */
    enum SlotState { LOADED, UNLOADED, SAVE_OFF }

    /**
     * W116 (W98-R12): the coordinator's server side, so its state machine runs without a server in unit tests. The
     * production port is {@link RocketRetirementWorld}; every call is on the server thread.
     */
    interface Port {
        long now();
        Presence survivor(RocketTransferRecord record, UUID survivor, RocketRetirementProof.Slot slot);
        /** R3's crew rule against T's source manifest; each placement found is added to {@code delivered}. */
        boolean crewReady(RocketTransferRecord record, UUID survivor, boolean offlineAllowed, Set<UUID> delivered);
        SlotState slot(RocketRetirementProof.Slot slot);
        /** False only when the slot's Level exists and its entity storage cannot give an own-store proof (R5(c)). */
        boolean captureSupported(RocketRetirementProof.Slot slot);
        boolean captureAvailable(RocketRetirementProof.Slot slot, long now);
        /** The barrier's ordinary capture of one slot; false when the barrier admitted nothing. */
        boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now, Consumer<String> sink,
                        Consumer<EscrowBarrier.Result> callback);
        /** Runs {@code retry} once at the end of the next barrier tick; may throw while the escrow runtime stops. */
        void retryNextTick(Runnable retry);
        boolean bayAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferBayRecoveryGate.Action action);
        boolean bayClaimed(UUID transfer);
        /** The token-aware escrow rule (W96 section 4) with {@code own} recognized as this attempt's reservation. */
        boolean escrowAllows(RocketTransferSavedData journal, RocketTransferRecord record, RocketTransferSavedData.Reservation own);
        /** The survivor's frozen projection, or null unless it is still T's stationary source ({@link RocketRetirementGuards}). */
        CompoundTag projection(RocketTransferRecord record, UUID survivor);
        /** The ordinary origin tickets the flight held, on every census column that is an end's origin (W90, W98-R3). */
        void keepLoaded(RocketTransferRecord record, List<RocketRetirementProof.Slot> census);
        /** Destination-site scan before freezing; returns a hold reason, or adds every discarded slot. */
        default String prepare(RocketTransferRecord record, UUID survivor, List<RocketRetirementProof.Slot> census) { return null; }
        default void releaseTickets(RocketTransferRecord record) { }
        /** One attempt's sink: silent unless a gated release test selects a pause. */
        Consumer<String> sink();
        /** Queues T's removal continuation with the attempt's reservation, consuming it. */
        boolean afterAcknowledged(RocketTransferSavedData journal, RocketTransferSavedData.Reservation token,
                                  BooleanSupplier effect, Runnable discarded);
    }

    /** The caller's effect on T's acknowledged removal (false discards it) and its discard. */
    record Effects(BooleanSupplier acknowledged, Runnable discarded) {
        static Effects of(BooleanSupplier acknowledged) { return new Effects(acknowledged, () -> { }); }
    }

    /** A call site's reservation in try-with-resources: closed on return unless {@link #begin} took it (R1, N2). */
    static final class Held implements AutoCloseable {
        private RocketTransferSavedData.Reservation token;
        private Held(RocketTransferSavedData.Reservation token) { this.token = token; }
        RocketTransferSavedData.Reservation token() { return token; }
        private RocketTransferSavedData.Reservation take() { var taken = token; token = null; return taken; }
        @Override public void close() { if (token != null) { token.close(); token = null; } }
    }

    static Held hold(RocketTransferSavedData journal, UUID logical) { return new Held(journal.reserve(logical).orElse(null)); }

    private enum State { WAITING, PROVING, REMOVING }

    private static final class Pending {
        final Port port;
        final RocketTransferSavedData journal;
        final RocketTransferRecord record;
        final UUID survivor;
        final Site site;
        final boolean offlineAllowed;
        final List<RocketRetirementProof.Slot> census = new ArrayList<>();
        final Set<UUID> delivered = new HashSet<>();
        final Map<RocketRetirementProof.Slot, EscrowBarrier.Proof> proofs = new HashMap<>();
        Effects effects;
        RocketTransferSavedData.Reservation token;
        boolean censusFull;
        State state = State.WAITING;
        CompoundTag frozen;
        Consumer<String> sink = label -> { };
        int attempt, cursor, attempts;
        long created, started, notBefore;
        String reason;
        Runnable retry = () -> { };

        Pending(Port port, RocketTransferSavedData journal, RocketTransferRecord record, UUID survivor, Site site,
                boolean offlineAllowed, Effects effects) {
            this.port = port; this.journal = journal; this.record = record; this.survivor = survivor; this.site = site;
            this.offlineAllowed = offlineAllowed; this.effects = effects;
        }
        UUID logical() { return record.logicalRocketId(); }
        void releaseProofs() { proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        void releaseToken() { if (token != null) { token.close(); token = null; } }
    }

    private final Set<UUID> live;
    private final Set<UUID> settled;
    private final Supplier<Set<UUID>> fenced;
    private final Function<MinecraftServer, Port> ports;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private final Map<UUID, String> refusals = new LinkedHashMap<>();
    private final Set<String> warnedLevels = new HashSet<>();
    private int attemptIds;
    private long destinationTick = Long.MIN_VALUE;
    private int destinationStarts;

    RocketOrdinaryRetirement(Set<UUID> live, Set<UUID> settled, Supplier<Set<UUID>> fenced, Function<MinecraftServer, Port> ports) {
        this.live = live; this.settled = settled; this.fenced = fenced; this.ports = ports;
    }

    boolean pending(UUID logical) { return pending.containsKey(logical); }
    int size() { return pending.size(); }
    /** Read-only, for tests: the attempts an entry for L has started so far, or -1 without one. */
    int attempts(UUID logical) { var entry = pending.get(logical); return entry == null ? -1 : entry.attempts; }
    /** Read-only, for tests: the last wait or failure reason of the entry for L, or null. */
    String reason(UUID logical) { var entry = pending.get(logical); return entry == null ? null : entry.reason; }

    /**
     * Whether a return of T through {@code survivor} may begin. Nothing has changed yet, so a refusal changes nothing
     * (N2). Room for one more entry; the survivor and every other candidate on its end's origin column (R4).
     */
    boolean admits(RocketTransferRecord record, RocketEntity survivor, List<RocketEntity> sources, List<RocketEntity> destinations) {
        // An existing entry for L is nudged or replaced by begin; only a new one needs room.
        String reason = !pending.containsKey(record.logicalRocketId()) && pending.size() >= MAX_PENDING ? "PENDING_LIMIT"
                : RocketRetirementGuards.offColumn(record, survivor, sources, destinations);
        if (reason == null) { refusals.remove(record.logicalRocketId()); return true; }
        if (!reason.equals(refusals.get(record.logicalRocketId()))) {
            if (refusals.size() >= MAX_PENDING) { refusals.remove(refusals.keySet().iterator().next()); }
            refusals.put(record.logicalRocketId(), reason);
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_HELD transfer={} logical={} reason={} repair=move-copy-or-offline-whole-world",
                    record.transferId(), record.logicalRocketId(), reason);
        }
        return false;
    }

    /**
     * Registers a return after the caller's mutation and starts its first attempt at once under the caller's
     * reservation; T leaves forward dispatch and is settled. An existing entry for the same T is nudged and given the
     * newly discarded slots (a waiting one retries under the caller's reservation), and the caller's effects are
     * discarded; one for another journal or record is replaced.
     */
    void begin(MinecraftServer server, RocketTransferSavedData journal, Site site, RocketTransferRecord record, RocketEntity survivor,
               List<RocketEntity> discarded, boolean offlineAllowed, Held held, Effects effects) {
        begin(ports.apply(server), journal, site, record, survivor.getUUID(), discarded.stream()
                .map(candidate -> new RocketRetirementProof.Slot(candidate.level().dimension().location(), candidate.chunkPosition())).toList(),
                offlineAllowed, held, effects);
    }

    /** {@link #begin} through a port, with the survivor by UUID and the discarded candidates by their entity slots. */
    void begin(Port port, RocketTransferSavedData journal, Site site, RocketTransferRecord record, UUID survivor,
               List<RocketRetirementProof.Slot> discarded, boolean offlineAllowed, Held held, Effects effects) {
        long now = port.now();
        var existing = pending.get(record.logicalRocketId());
        if (existing != null && (existing.journal != journal || journal.find(record.transferId()).orElse(null) != existing.record)) {
            abandon(existing, "REPLACED");
            existing = null;
        }
        if (existing != null) {
            // A loader retry is demand, not a reconnect: it must never cancel the retirement's backoff.
            if (site == Site.BEFORE_FILL) { effects.discarded().run(); return; }
            var nudged = existing;
            discarded.forEach(slot -> addSlot(nudged, slot));
            nudged.notBefore = Math.min(nudged.notBefore, now);
            // A waiting return retries at once under the caller's reservation, for example on its last reconnect.
            if (nudged.state == State.WAITING && nudged.token == null && held.token() != null) {
                nudged.token = held.take(); nudged.created = now;
                guarded(nudged, now);
            }
            // W98-R8: the existing entry's effects follow its removal; the nudging caller's never run.
            effects.discarded().run();
            return;
        }
        live.remove(record.transferId()); settled.add(record.transferId());
        var entry = new Pending(port, journal, record, survivor, site, offlineAllowed, effects);
        entry.census.add(RocketRetirementProof.Slot.origin(site == Site.BEFORE_FILL ? record.destinationSnapshot() : record.sourceSnapshot()));
        if (site == Site.BEFORE_FILL) { addSlot(entry, RocketRetirementProof.Slot.origin(record.sourceSnapshot())); }
        discarded.forEach(slot -> addSlot(entry, slot));
        entry.token = held.take();
        entry.created = now; entry.notBefore = now;
        entry.retry = () -> {
            if (pending.get(entry.logical()) != entry || entry.state == State.REMOVING) { return; }
            // The barrier may tick later in the same server tick; a one-tick wait then retries on the next one.
            if (entry.state == State.WAITING && entry.notBefore == port.now() + 1L) { wake(entry); return; }
            guarded(entry, port.now());
        };
        pending.put(entry.logical(), entry);
        // The first attempt starts at once under the caller's reservation; later steps follow the barrier's callbacks.
        guarded(entry, now);
    }

    private static void addSlot(Pending entry, RocketRetirementProof.Slot slot) {
        if (RocketRetirementProof.addSlot(entry.census, slot, MAX_SLOTS) || entry.censusFull) { return; }
        // W98-R7: a slot the census cannot hold is never treated as proved absent; the return holds, with one WARN.
        entry.censusFull = true;
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_HELD transfer={} logical={} reason=CENSUS_LIMIT slots={} repair=offline-whole-world",
                entry.record.transferId(), entry.logical(), MAX_SLOTS);
    }

    void tick() {
        if (pending.isEmpty()) { return; }
        for (var entry : List.copyOf(pending.values())) {
            if (pending.get(entry.logical()) != entry || entry.state == State.REMOVING) { continue; }
            guarded(entry, entry.port.now());
        }
    }

    private void guarded(Pending entry, long now) {
        try { step(entry, now); }
        catch (RuntimeException failure) {
            // A stopping escrow runtime or a refused capture is a held attempt, never permission to remove T.
            if (pending.get(entry.logical()) == entry && entry.state != State.REMOVING) {
                fail(entry, "UNAVAILABLE " + failure.getClass().getSimpleName(), now, RETRY_TICKS, true);
            }
        }
    }

    private void step(Pending entry, long now) {
        String abandon = abandoned(entry);
        if (abandon != null) { abandon(entry, abandon); return; }
        if (entry.state == State.PROVING) { prove(entry, now); return; }
        if (now < entry.notBefore) {
            if (entry.created != now) { entry.releaseToken(); } // a fail-back's deferred release (see fail)
            return;
        }
        start(entry, now);
    }

    /** T no longer belongs to this return: removed or replaced, fenced, claimed by a bay, or reclassified. */
    private String abandoned(Pending entry) {
        var record = entry.record;
        if (!entry.journal.operational() || !entry.journal.logicalAcknowledged(entry.logical())) { return "JOURNAL_STOPPED"; }
        if (entry.journal.find(record.transferId()).orElse(null) != record) { return "RECORD_CHANGED"; }
        if (fenced.get().contains(record.transferId())) { return "FENCED"; }
        if (entry.port.bayClaimed(record.transferId())) { return "BAY_CLAIMED"; }
        if (live.contains(record.transferId())) { return "RECLASSIFIED"; }
        // A bay's RELEASED_TO_ORDINARY handoff unclassifies T on each classification; ordinary rules are this return's.
        settled.add(record.transferId());
        return null;
    }

    private void start(Pending entry, long now) {
        if (entry.censusFull) { fail(entry, "CENSUS_LIMIT", now, RETRY_TICKS, false); return; }
        if (entry.token == null && attempting() >= MAX_ATTEMPTS) { return; }
        var record = entry.record; var port = entry.port;
        var presence = port.survivor(record, entry.survivor, entry.census.get(0));
        if (presence != Presence.LOADED) {
            if (presence == Presence.GONE) { abandon(entry, "SURVIVOR_GONE"); }
            else { waiting(entry, "SURVIVOR_UNLOADED", now); }
            return;
        }
        if (entry.site == Site.BEFORE_FILL) {
            if (destinationTick != now) { destinationTick = now; destinationStarts = 0; }
            if (destinationStarts >= MAX_ATTEMPTS || attempting() > MAX_ATTEMPTS) { entry.releaseToken(); return; }
            destinationStarts++;
            entry.notBefore = now + RETRY_TICKS;
            if (!port.escrowAllows(entry.journal, record, entry.token)) { waiting(entry, "BUSY", now); return; }
            if (port.projection(record, entry.survivor) == null) { waiting(entry, "SURVIVOR_CONFLICT", now); return; }
            // On demand only; source-return sites retain their original loaded-only ordering below.
            port.keepLoaded(record, entry.census);
        }
        if (!port.crewReady(record, entry.survivor, entry.offlineAllowed, entry.delivered)) { waiting(entry, "CREW_WAITING", now); return; }
        for (var slot : entry.census) {
            var state = port.slot(slot);
            if (state == SlotState.UNLOADED) { waiting(entry, "SLOT_UNLOADED", now); return; }
            if (state == SlotState.SAVE_OFF) { saveOff(entry, slot, now); return; }
            warnedLevels.remove("SAVE_OFF " + slot.dimension());
        }
        boolean supported = supported(entry);
        if (!supported && entry.site == Site.BEFORE_FILL) { waiting(entry, "UNSUPPORTED_ENTITY_STORAGE", now); return; }
        // W98-R5: an attempt starts only when its first capture can run now, so no reservation waits for capture budget.
        if (supported && !port.captureAvailable(entry.census.get(0), now)) { fail(entry, "BUSY", now, 1, false); return; }
        if (!port.bayAllows(entry.journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { abandon(entry, "BAY_HELD"); return; }
        if (!port.escrowAllows(entry.journal, record, entry.token)) { fail(entry, "BUSY", now, 1, false); return; }
        String preparation = port.prepare(record, entry.survivor, entry.census);
        if (preparation != null) { waiting(entry, preparation, now); return; }
        var frozen = port.projection(record, entry.survivor);
        if (frozen == null) { fail(entry, "SURVIVOR_CONFLICT", now, RETRY_TICKS, true); return; }
        if (entry.token == null) {
            entry.token = entry.journal.reserve(entry.logical()).orElse(null);
            if (entry.token == null) { fail(entry, "BUSY", now, 1, false); return; }
        }
        if (!supported) { removeUnproved(entry); return; }
        if (entry.site != Site.BEFORE_FILL) { port.keepLoaded(record, entry.census); }
        entry.state = State.PROVING; entry.attempt = ++attemptIds; entry.attempts++;
        entry.started = now; entry.frozen = frozen; entry.cursor = 0; entry.sink = port.sink();
        prove(entry, now);
    }

    private void prove(Pending entry, long now) {
        // A nudge may add slots during an attempt; an overflow ends it before any removal (W98-R7).
        if (entry.censusFull) { fail(entry, "CENSUS_LIMIT", now, RETRY_TICKS, false); return; }
        if (now - entry.started > ATTEMPT_TICKS) { fail(entry, "DEADLINE", now, RETRY_TICKS, true); return; }
        if (entry.proofs.values().stream().anyMatch(proof -> !proof.valid())) { fail(entry, "SUPERSEDING_SAVE", now, 1, false); return; }
        if (entry.cursor < entry.census.size()) {
            var slot = entry.census.get(entry.cursor);
            var state = entry.port.slot(slot);
            if (state == SlotState.UNLOADED) { waiting(entry, "SLOT_UNLOADED", now); return; }
            // W98-R9: a /save-off issued during the attempt holds the next capture too, not only the attempt's start.
            if (state == SlotState.SAVE_OFF) { saveOff(entry, slot, now); return; }
            if (!entry.port.captureAvailable(slot, now)) {
                // W98-R5: before its first capture an attempt releases its reservation and waits; later slots keep it.
                if (entry.cursor == 0) { fail(entry, "BUSY", now, 1, false); } else { wake(entry); }
                return;
            }
            int attempt = entry.attempt;
            var frozen = entry.frozen;
            UUID logical = entry.logical(), survivor = entry.survivor;
            boolean own = entry.cursor == 0;
            if (entry.port.capture(slot,
                    root -> own ? RocketRetirementProof.survivorSlot(root, logical, frozen) : RocketRetirementProof.absentSlot(root, logical, survivor),
                    now, entry.sink, result -> proved(entry, attempt, slot, result))) {
                entry.cursor++;
            }
            return;
        }
        if (entry.proofs.size() == entry.census.size()) { finish(entry, now); }
    }

    private void proved(Pending entry, int attempt, RocketRetirementProof.Slot slot, EscrowBarrier.Result result) {
        if (pending.get(entry.logical()) != entry || entry.state != State.PROVING || entry.attempt != attempt) {
            if (result.proof() != null) { result.proof().release(); }
            return;
        }
        if (result.confirmed()) {
            entry.proofs.put(slot, result.proof());
            // The next slot, or the final check, follows at once on the owner thread.
            guarded(entry, entry.port.now());
            return;
        }
        if (result.proof() != null) { result.proof().release(); }
        boolean superseded = result.reason() == EscrowReason.SUPERSEDING_SAVE;
        fail(entry, result.reason().name(), entry.port.now(), superseded ? 1 : RETRY_TICKS, !superseded);
    }

    /** The final owner-thread check: identical T, unchanged survivor, crew, token-aware guards and every proof valid. */
    private void finish(Pending entry, long now) {
        var record = entry.record; var port = entry.port;
        var current = port.projection(record, entry.survivor);
        if (current == null || !RocketRetirementProof.matches(current, entry.frozen)) { fail(entry, "SURVIVOR_CHANGED", now, 1, false); return; }
        if (!port.crewReady(record, entry.survivor, entry.offlineAllowed, entry.delivered)) { waiting(entry, "CREW_WAITING", now); return; }
        if (!port.bayAllows(entry.journal, record, RocketTransferBayRecoveryGate.Action.SETTLE_SOURCE)
                || !port.escrowAllows(entry.journal, record, entry.token)) { fail(entry, "BUSY", now, 1, false); return; }
        if (!entry.proofs.values().stream().allMatch(EscrowBarrier.Proof::valid)) { fail(entry, "SUPERSEDING_SAVE", now, 1, false); return; }
        entry.sink.accept(RocketRetirementTestHook.REMOVAL_BEFORE);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_RETIREMENT_PROVED transfer={} logical={} site={} survivor={} slots={} attempts={}",
                record.transferId(), entry.logical(), entry.site, entry.survivor, entry.census.size(), entry.attempts);
        remove(entry);
    }

    /** R5(c), Root's choice: another mod's entity storage leaves no own-store proof, so T goes as before, with a WARN. */
    private void removeUnproved(Pending entry) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_RETIREMENT_UNPROVED transfer={} logical={} site={} reason=UNSUPPORTED_ENTITY_STORAGE",
                entry.record.transferId(), entry.logical(), entry.site);
        remove(entry);
    }

    private void remove(Pending entry) {
        var token = entry.token; entry.token = null;
        entry.releaseProofs(); entry.state = State.REMOVING;
        var record = entry.record;
        entry.journal.remove(record.transferId());
        boolean submitted = entry.port.afterAcknowledged(entry.journal, token, () -> {
            drop(entry);
            if (!entry.effects.acknowledged().getAsBoolean()) { return false; }
            live.remove(record.transferId()); settled.remove(record.transferId());
            return true;
        }, () -> discard(entry));
        if (!submitted) { token.close(); discard(entry); }
    }

    private void discard(Pending entry) {
        drop(entry);
        if (entry.journal.find(entry.record.transferId()).isEmpty()) {
            live.remove(entry.record.transferId()); settled.remove(entry.record.transferId());
        }
        entry.effects.discarded().run();
    }

    private void drop(Pending entry) { pending.remove(entry.logical(), entry); entry.port.releaseTickets(entry.record); }

    /**
     * T stays with its classification unchanged, as a waiting return's always was; the caller's request is discarded.
     * A survivor that vanished from a loaded slot is the exception: T is unclassified again, so recovery rebuilds it.
     */
    private void abandon(Pending entry, String reason) {
        entry.releaseProofs(); entry.releaseToken(); drop(entry);
        if (reason.equals("SURVIVOR_GONE")) { settled.remove(entry.record.transferId()); }
        // Another owner changed T (its own diagnostic says why), or the journal stopped: T was never removed here.
        String message = "ARCE_TRANSFER_RETIREMENT_ABANDONED transfer={} logical={} site={} reason={}";
        if (reason.equals("SURVIVOR_GONE")) {
            AdvancedRocketryCommunity.LOGGER.warn(message, entry.record.transferId(), entry.logical(), entry.site, reason);
        } else {
            AdvancedRocketryCommunity.LOGGER.info(message, entry.record.transferId(), entry.logical(), entry.site, reason);
        }
        entry.effects.discarded().run();
    }

    private void waiting(Pending entry, String reason, long now) { fail(entry, reason, now, POLL_TICKS, false); }

    /**
     * R1: any failure or wait releases the reservation and the proofs; T stays as the per-rocket interlock. A fail-back
     * keeps its own reservation until the next tick, so its request reports JOURNAL_PENDING and completes only once.
     */
    private void fail(Pending entry, String reason, long now, int delay, boolean fault) {
        entry.releaseProofs();
        if (entry.site != Site.FAIL_BACK || entry.created != now) { entry.releaseToken(); }
        entry.state = State.WAITING;
        entry.notBefore = entry.site == Site.BEFORE_FILL ? Math.max(entry.notBefore, now + delay) : now + delay;
        if (delay == 1 && entry.site != Site.BEFORE_FILL) { wake(entry); }
        if (!reason.equals(entry.reason)) {
            entry.reason = reason;
            if (fault) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_RETRY transfer={} logical={} site={} reason={} retry_ticks={}",
                        entry.record.transferId(), entry.logical(), entry.site, reason, delay);
            } else {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_RETIREMENT_WAIT transfer={} logical={} site={} reason={}",
                        entry.record.transferId(), entry.logical(), entry.site, reason);
            }
        }
    }

    /**
     * A one-tick wait (a busy capture budget, a superseding save) retries once at the end of the next barrier tick, so
     * it never depends on who ticks this coordinator; at most once per real tick. Longer waits use the service tick.
     */
    private void wake(Pending entry) {
        try { entry.port.retryNextTick(entry.retry); }
        catch (RuntimeException stopping) {
            // A stopping escrow runtime: the service tick or the stop's clear decides this entry.
        }
    }

    /** R5(d): a save-off Level holds every return there, with one WARN per Level until saving resumes. */
    private void saveOff(Pending entry, RocketRetirementProof.Slot slot, long now) {
        if (warnedLevels.add("SAVE_OFF " + slot.dimension())) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_SAVE_OFF level={}; ordinary returns there keep their records until saving resumes",
                    slot.dimension());
        }
        waiting(entry, "SAVE_OFF", now);
    }

    private boolean supported(Pending entry) {
        for (var slot : entry.census) {
            if (!entry.port.captureSupported(slot)) {
                if (entry.site == Site.BEFORE_FILL) {
                    if (warnedLevels.add("FILL_STORAGE " + slot.dimension())) {
                        AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FILL_RETIREMENT_UNSUPPORTED_STORAGE level={}; escrow fills retain the transfer until entity storage supports a checked proof", slot.dimension());
                    }
                    return false;
                }
                if (warnedLevels.add("STORAGE " + slot.dimension())) {
                    AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_UNSUPPORTED_STORAGE level={}; another entity storage replaced "
                            + "this Level's, so ordinary returns remove their records after the in-memory return (R-035 residual)", slot.dimension());
                }
                return false;
            }
        }
        return true;
    }

    /** W98-R5: attempts in progress, and entries that still carry a caller's reservation. */
    private int attempting() {
        int count = 0;
        for (var entry : pending.values()) { if (entry.state == State.PROVING || entry.token != null) { count++; } }
        return count;
    }

    /** Stop: release this coordinator's tokens and watches; T stays wherever its removal was not submitted. */
    void clear() {
        for (var entry : List.copyOf(pending.values())) {
            entry.releaseProofs();
            entry.port.releaseTickets(entry.record);
            try { entry.releaseToken(); } catch (RuntimeException stopped) { entry.token = null; }
            if (entry.state != State.REMOVING) { entry.effects.discarded().run(); }
        }
        pending.clear(); refusals.clear(); warnedLevels.clear(); destinationTick = Long.MIN_VALUE; destinationStarts = 0;
    }
}
