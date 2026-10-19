package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

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
 * format, ticket type, governed path, lease or replay rule changes.
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
    /** Gated release-test pause at one boundary, for the W98 native cuts. Production never sets it. */
    static final String CUT_PROPERTY = "advancedrocketrycommunity.ordinaryRetirementCut";
    static final String REMOVAL_BEFORE = "ORDINARY_REMOVAL_BEFORE";

    enum Site { FAIL_BACK, RECOVERED_SOURCE, RECONNECT }

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
        final MinecraftServer server;
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
        State state = State.WAITING;
        CompoundTag frozen;
        Consumer<String> sink = label -> { };
        int attempt, cursor, attempts;
        long created, started, notBefore;
        String reason;
        Runnable retry = () -> { };

        Pending(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record, UUID survivor, Site site,
                boolean offlineAllowed, Effects effects) {
            this.server = server; this.journal = journal; this.record = record; this.survivor = survivor; this.site = site;
            this.offlineAllowed = offlineAllowed; this.effects = effects;
        }
        UUID logical() { return record.logicalRocketId(); }
        void releaseProofs() { proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
        void releaseToken() { if (token != null) { token.close(); token = null; } }
    }

    private final Set<UUID> live;
    private final Set<UUID> settled;
    private final RocketTransferBayDispatch bays;
    private final Supplier<Set<UUID>> fenced;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private final Map<UUID, String> refusals = new LinkedHashMap<>();
    private final Set<String> warnedLevels = new HashSet<>();
    private int attemptIds;

    RocketOrdinaryRetirement(Set<UUID> live, Set<UUID> settled, RocketTransferBayDispatch bays, Supplier<Set<UUID>> fenced) {
        this.live = live; this.settled = settled; this.bays = bays; this.fenced = fenced;
    }

    boolean pending(UUID logical) { return pending.containsKey(logical); }
    int size() { return pending.size(); }

    /**
     * Whether a return of T through {@code survivor} may begin. Nothing has changed yet, so a refusal changes nothing
     * (N2). Room for one more entry; the survivor and every other candidate on its end's origin column (R4).
     */
    boolean admits(RocketTransferRecord record, RocketEntity survivor, List<RocketEntity> sources, List<RocketEntity> destinations) {
        // An existing entry for L is nudged or replaced by begin; only a new one needs room.
        String reason = !pending.containsKey(record.logicalRocketId()) && pending.size() >= MAX_PENDING ? "PENDING_LIMIT" : null;
        if (reason == null) {
            var candidates = new ArrayList<RocketEntity>(sources);
            if (survivor != null) { candidates.add(survivor); }
            for (var candidate : candidates) {
                if (!onOriginColumn(candidate, record.sourceSnapshot())) { reason = "CANDIDATE_OFF_COLUMN entity=" + candidate.getUUID(); }
            }
            for (var candidate : destinations) {
                if (!onOriginColumn(candidate, record.destinationSnapshot())) { reason = "CANDIDATE_OFF_COLUMN entity=" + candidate.getUUID(); }
            }
        }
        if (reason == null) { refusals.remove(record.logicalRocketId()); return true; }
        if (!reason.equals(refusals.get(record.logicalRocketId()))) {
            if (refusals.size() >= MAX_PENDING) { refusals.remove(refusals.keySet().iterator().next()); }
            refusals.put(record.logicalRocketId(), reason);
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_HELD transfer={} logical={} reason={} repair=move-copy-or-offline-whole-world",
                    record.transferId(), record.logicalRocketId(), reason);
        }
        return false;
    }

    static boolean onOriginColumn(RocketEntity candidate, RocketStructureSnapshot snapshot) {
        var origin = snapshot.sourceOrigin();
        return candidate.level().dimension().location().equals(snapshot.sourceDimension())
                && candidate.chunkPosition().equals(new ChunkPos(origin.x() >> 4, origin.z() >> 4));
    }

    /** R3's crew rule against T's source manifest, without remembering any placement. */
    static boolean crewReady(MinecraftServer server, RocketTransferRecord record, RocketEntity survivor, boolean offlineAllowed) {
        return crewReady(server, record, survivor, offlineAllowed, new HashSet<>());
    }

    private static boolean crewReady(MinecraftServer server, RocketTransferRecord record, RocketEntity survivor,
                                     boolean offlineAllowed, Set<UUID> delivered) {
        var seats = record.sourceFlightData().passengers().assignments().stream().map(RocketPassengerSeat::passengerId).toList();
        return RocketRetirementProof.crewReady(seats, id -> {
            var player = server.getPlayerList().getPlayer(id);
            return RocketRetirementProof.seat(player != null, player != null && player.getServer() == server && !player.isRemoved()
                            && player.isAlive() && !player.isSpectator() && player.connection != null && player.connection.connection.isConnected(),
                    player != null && player.level() == survivor.level(), player != null && player.getVehicle() == survivor,
                    player != null && player.getVehicle() == null);
        }, delivered, offlineAllowed);
    }

    /**
     * Registers a return after the caller's mutation and starts its first attempt at once under the caller's
     * reservation; T leaves forward dispatch and is settled. An existing entry for the same T is nudged and given the
     * newly discarded slots (a waiting one retries under the caller's reservation); one for another journal or record
     * is replaced.
     */
    void begin(MinecraftServer server, RocketTransferSavedData journal, Site site, RocketTransferRecord record, RocketEntity survivor,
               List<RocketEntity> discarded, boolean offlineAllowed, Held held, Effects effects) {
        long now = server.getTickCount();
        var existing = pending.get(record.logicalRocketId());
        if (existing != null && (existing.journal != journal || journal.find(record.transferId()).orElse(null) != existing.record)) {
            abandon(existing, "REPLACED");
            existing = null;
        }
        if (existing != null) {
            var nudged = existing;
            discarded.forEach(candidate -> addSlot(nudged, candidate));
            nudged.notBefore = Math.min(nudged.notBefore, now);
            // A waiting return retries at once under the caller's reservation, for example on its last reconnect.
            if (nudged.state == State.WAITING && nudged.token == null && held.token() != null) {
                nudged.token = held.take(); nudged.created = now;
                guarded(server, nudged, now);
            }
            return;
        }
        live.remove(record.transferId()); settled.add(record.transferId());
        var entry = new Pending(server, journal, record, survivor.getUUID(), site, offlineAllowed, effects);
        var origin = record.sourceSnapshot().sourceOrigin();
        entry.census.add(new RocketRetirementProof.Slot(record.sourceSnapshot().sourceDimension(), new ChunkPos(origin.x() >> 4, origin.z() >> 4)));
        discarded.forEach(candidate -> addSlot(entry, candidate));
        entry.token = held.take();
        entry.created = now; entry.notBefore = now;
        entry.retry = () -> {
            if (pending.get(entry.logical()) != entry || entry.state == State.REMOVING) { return; }
            // The barrier may tick later in the same server tick; a one-tick wait then retries on the next one.
            if (entry.state == State.WAITING && entry.notBefore == server.getTickCount() + 1L) { wake(entry); return; }
            guarded(server, entry, server.getTickCount());
        };
        pending.put(entry.logical(), entry);
        // The first attempt starts at once under the caller's reservation; later steps follow the barrier's callbacks.
        guarded(server, entry, now);
    }

    private static void addSlot(Pending entry, RocketEntity candidate) {
        var slot = new RocketRetirementProof.Slot(candidate.level().dimension().location(), candidate.chunkPosition());
        if (!entry.census.contains(slot) && entry.census.size() < MAX_SLOTS) { entry.census.add(slot); }
    }

    void tick(MinecraftServer server) {
        if (pending.isEmpty()) { return; }
        long now = server.getTickCount();
        for (var entry : List.copyOf(pending.values())) {
            if (pending.get(entry.logical()) != entry || entry.state == State.REMOVING) { continue; }
            guarded(server, entry, now);
        }
    }

    private void guarded(MinecraftServer server, Pending entry, long now) {
        try { step(server, entry, now); }
        catch (RuntimeException failure) {
            // A stopping escrow runtime or a refused capture is a held attempt, never permission to remove T.
            if (pending.get(entry.logical()) == entry && entry.state != State.REMOVING) {
                fail(entry, "UNAVAILABLE " + failure.getClass().getSimpleName(), now, RETRY_TICKS, true);
            }
        }
    }

    private void step(MinecraftServer server, Pending entry, long now) {
        String abandon = abandoned(entry);
        if (abandon != null) { abandon(entry, abandon); return; }
        if (entry.state == State.PROVING) { prove(server, entry, now); return; }
        if (now < entry.notBefore) {
            if (entry.created != now) { entry.releaseToken(); } // a fail-back's deferred release (see fail)
            return;
        }
        start(server, entry, now);
    }

    /** T no longer belongs to this return: removed or replaced, fenced, claimed by a bay, or reclassified. */
    private String abandoned(Pending entry) {
        var record = entry.record;
        if (!entry.journal.operational() || !entry.journal.logicalAcknowledged(entry.logical())) { return "JOURNAL_STOPPED"; }
        if (entry.journal.find(record.transferId()).orElse(null) != record) { return "RECORD_CHANGED"; }
        if (fenced.get().contains(record.transferId())) { return "FENCED"; }
        if (bays.ids().contains(record.transferId())) { return "BAY_CLAIMED"; }
        if (live.contains(record.transferId())) { return "RECLASSIFIED"; }
        // A bay's RELEASED_TO_ORDINARY handoff unclassifies T on each classification; ordinary rules are this return's.
        settled.add(record.transferId());
        return null;
    }

    private void start(MinecraftServer server, Pending entry, long now) {
        if (entry.token == null && attempting() >= MAX_ATTEMPTS) { return; }
        var record = entry.record;
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        var survivor = level != null && level.getEntity(entry.survivor) instanceof RocketEntity rocket ? rocket : null;
        if (survivor == null) {
            if (level != null && level.areEntitiesLoaded(entry.census.get(0).chunk().toLong())) { abandon(entry, "SURVIVOR_GONE"); }
            else { waiting(entry, "SURVIVOR_UNLOADED", now); }
            return;
        }
        if (!crewReady(server, record, survivor, entry.offlineAllowed, entry.delivered)) { waiting(entry, "CREW_WAITING", now); return; }
        for (var slot : entry.census) {
            var slotLevel = RocketTransferEntities.level(server, slot.dimension());
            if (slotLevel == null || !slotLevel.areEntitiesLoaded(slot.chunk().toLong())) { waiting(entry, "SLOT_UNLOADED", now); return; }
            if (slotLevel.noSave()) { saveOff(entry, slotLevel, now); return; }
            warnedLevels.remove("SAVE_OFF " + slot.dimension());
        }
        boolean supported = supported(server, entry);
        // W98-R5: an attempt starts only when its first capture can run now, so no reservation waits for capture budget.
        if (supported && !firstCaptureAvailable(server, entry, now)) { fail(entry, "BUSY", now, 1, false); return; }
        if (!bays.allowed(server, entry.journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL)) { abandon(entry, "BAY_HELD"); return; }
        if (!escrowAllowed(server, entry.journal, record, entry.token)) { fail(entry, "BUSY", now, 1, false); return; }
        var frozen = projection(survivor, record);
        if (frozen == null) { fail(entry, "SURVIVOR_CONFLICT", now, RETRY_TICKS, true); return; }
        if (entry.token == null) {
            entry.token = entry.journal.reserve(entry.logical()).orElse(null);
            if (entry.token == null) { fail(entry, "BUSY", now, 1, false); return; }
        }
        if (!supported) { removeUnproved(server, entry); return; }
        // The ordinary origin ticket the flight held; never a governed or margin ticket (W90).
        RocketTransferEntities.keepLoaded(level, record.sourceSnapshot(), record.transferId());
        entry.state = State.PROVING; entry.attempt = ++attemptIds; entry.attempts++;
        entry.started = now; entry.frozen = frozen; entry.cursor = 0; entry.sink = sink(server);
        prove(server, entry, now);
    }

    private void prove(MinecraftServer server, Pending entry, long now) {
        if (now - entry.started > ATTEMPT_TICKS) { fail(entry, "DEADLINE", now, RETRY_TICKS, true); return; }
        if (entry.proofs.values().stream().anyMatch(proof -> !proof.valid())) { fail(entry, "SUPERSEDING_SAVE", now, 1, false); return; }
        if (entry.cursor < entry.census.size()) {
            var slot = entry.census.get(entry.cursor);
            var level = RocketTransferEntities.level(server, slot.dimension());
            if (level == null || !level.areEntitiesLoaded(slot.chunk().toLong())) { waiting(entry, "SLOT_UNLOADED", now); return; }
            var runtime = EscrowRuntime.of(server);
            var barrier = runtime.barrier();
            String id = slot.dimension().toString();
            if (!barrier.availableOrdinary(id, now)) {
                // W98-R5: before its first capture an attempt releases its reservation and waits; later slots keep it.
                if (entry.cursor == 0) { fail(entry, "BUSY", now, 1, false); } else { wake(entry); }
                return;
            }
            int attempt = entry.attempt;
            var frozen = entry.frozen;
            UUID logical = entry.logical(), survivor = entry.survivor;
            boolean own = entry.cursor == 0;
            if (barrier.beginOrdinary(runtime.store(level), id, slot.chunk(),
                    root -> own ? RocketRetirementProof.survivorSlot(root, logical, frozen) : RocketRetirementProof.absentSlot(root, logical, survivor),
                    now, entry.sink, result -> proved(server, entry, attempt, slot, result))) {
                entry.cursor++;
            }
            return;
        }
        if (entry.proofs.size() == entry.census.size()) { finish(server, entry, now); }
    }

    private void proved(MinecraftServer server, Pending entry, int attempt, RocketRetirementProof.Slot slot, EscrowBarrier.Result result) {
        if (pending.get(entry.logical()) != entry || entry.state != State.PROVING || entry.attempt != attempt) {
            if (result.proof() != null) { result.proof().release(); }
            return;
        }
        if (result.confirmed()) {
            entry.proofs.put(slot, result.proof());
            // The next slot, or the final check, follows at once on the owner thread.
            guarded(server, entry, server.getTickCount());
            return;
        }
        if (result.proof() != null) { result.proof().release(); }
        boolean superseded = result.reason() == EscrowReason.SUPERSEDING_SAVE;
        fail(entry, result.reason().name(), server.getTickCount(), superseded ? 1 : RETRY_TICKS, !superseded);
    }

    /** The final owner-thread check: identical T, unchanged survivor, crew, token-aware guards and every proof valid. */
    private void finish(MinecraftServer server, Pending entry, long now) {
        var record = entry.record;
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        var survivor = level != null && level.getEntity(entry.survivor) instanceof RocketEntity rocket ? rocket : null;
        var current = survivor == null ? null : projection(survivor, record);
        if (current == null || !RocketRetirementProof.matches(current, entry.frozen)) { fail(entry, "SURVIVOR_CHANGED", now, 1, false); return; }
        if (!crewReady(server, record, survivor, entry.offlineAllowed, entry.delivered)) { waiting(entry, "CREW_WAITING", now); return; }
        if (!bays.allowed(server, entry.journal, record, RocketTransferBayRecoveryGate.Action.SETTLE_SOURCE)
                || !escrowAllowed(server, entry.journal, record, entry.token)) { fail(entry, "BUSY", now, 1, false); return; }
        if (!entry.proofs.values().stream().allMatch(EscrowBarrier.Proof::valid)) { fail(entry, "SUPERSEDING_SAVE", now, 1, false); return; }
        entry.sink.accept(REMOVAL_BEFORE);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_RETIREMENT_PROVED transfer={} logical={} site={} survivor={} slots={} attempts={}",
                record.transferId(), entry.logical(), entry.site, entry.survivor, entry.census.size(), entry.attempts);
        remove(server, entry);
    }

    /** R5(c), Root's choice: another mod's entity storage leaves no own-store proof, so T goes as before, with a WARN. */
    private void removeUnproved(MinecraftServer server, Pending entry) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_TRANSFER_RETIREMENT_UNPROVED transfer={} logical={} site={} reason=UNSUPPORTED_ENTITY_STORAGE",
                entry.record.transferId(), entry.logical(), entry.site);
        remove(server, entry);
    }

    private void remove(MinecraftServer server, Pending entry) {
        var token = entry.token; entry.token = null;
        entry.releaseProofs(); entry.state = State.REMOVING;
        var record = entry.record;
        entry.journal.remove(record.transferId());
        boolean submitted = entry.journal.afterAcknowledged(server, token, () -> {
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

    private void drop(Pending entry) { pending.remove(entry.logical(), entry); }

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
        entry.state = State.WAITING; entry.notBefore = now + delay;
        if (delay == 1) { wake(entry); }
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
        try { EscrowRuntime.of(entry.server).barrier().retryOrdinaryNextTick(entry.retry); }
        catch (RuntimeException stopping) {
            // A stopping escrow runtime: the service tick or the stop's clear decides this entry.
        }
    }

    /** R5(d): a save-off Level holds every return there, with one WARN per Level until saving resumes. */
    private void saveOff(Pending entry, ServerLevel level, long now) {
        if (warnedLevels.add("SAVE_OFF " + level.dimension().location())) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_SAVE_OFF level={}; ordinary returns there keep their records until saving resumes",
                    level.dimension().location());
        }
        waiting(entry, "SAVE_OFF", now);
    }

    private boolean supported(MinecraftServer server, Pending entry) {
        var runtime = EscrowRuntime.of(server);
        for (var slot : entry.census) {
            var level = RocketTransferEntities.level(server, slot.dimension());
            if (level != null && !runtime.store(level).entityCaptureSupported()) {
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

    /** W98-R5: whether the survivor's slot, the census's first capture, has ordinary capture budget this tick. */
    private static boolean firstCaptureAvailable(MinecraftServer server, Pending entry, long now) {
        return EscrowRuntime.of(server).barrier().availableOrdinary(entry.census.get(0).dimension().toString(), now);
    }

    /**
     * The survivor's frozen projection, or null unless it is T's loaded source identity, stationary at its snapshot
     * origin with T's source fuel and debits, and saved as a world entity (not a passenger).
     */
    static CompoundTag projection(RocketEntity survivor, RocketTransferRecord record) {
        if (!RocketTransferEntities.loadedEntity(survivor) || !survivor.operational() || survivor.isPassenger()) { return null; }
        var snapshot = record.sourceSnapshot(); var origin = snapshot.sourceOrigin();
        var flight = survivor.flightData().orElse(null);
        if (!survivor.level().dimension().location().equals(snapshot.sourceDimension())
                || survivor.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || survivor.ownerId().filter(record.ownerId()::equals).isEmpty()
                || survivor.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId())
                        && value.contentHash().equals(snapshot.contentHash())).isEmpty()
                || !RocketTransferRecoveryService.recoveredStationarySource(true, false, flight,
                        survivor.getX() == origin.x() + 0.5D && survivor.getY() == origin.y() && survivor.getZ() == origin.z() + 0.5D)
                || !flight.fuel().equals(record.sourceFlightData().fuel())) { return null; }
        var tag = new CompoundTag();
        return survivor.save(tag) ? tag : null;
    }

    /** RocketTransferEscrowRules.flightAllowed, with the held reservation recognized as this return's own (W96 section 4). */
    private boolean escrowAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                  RocketTransferSavedData.Reservation token) {
        UUID logical = record.logicalRocketId();
        if (!(token == null ? journal.actionable(logical) : journal.actionable(logical, token)) || record.nativeTransfer().isPresent()) {
            return false;
        }
        var boundary = bays.before(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL);
        if (boundary == null) { return false; }
        boolean permitted = boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT;
        if (permitted ? RocketEscrowGate.escrowLogicalLocked(server, logical) : RocketEscrowGate.logicalLocked(server, logical)) {
            if (permitted) { bays.conflict(server, journal, record); }
            return false;
        }
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            ServerLevel level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (UUID identity : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(identity) instanceof RocketEntity rocket
                        && !(permitted ? RocketEscrowGate.escrowIdle(rocket) : RocketEscrowGate.idle(rocket))) {
                    if (permitted) { bays.conflict(server, journal, record); }
                    return false;
                }
            }
        }
        return true;
    }

    /** Silent unless a gated release-test run selects one boundary to pause at, once. */
    private static Consumer<String> sink(MinecraftServer server) {
        String cut = Boolean.getBoolean(EscrowTestEndpoints.HOOKS) ? System.getProperty(CUT_PROPERTY, "") : "";
        if (cut.isEmpty()) { return label -> { }; }
        EscrowTestEndpoints.requireEnabled(server);
        var once = new AtomicBoolean();
        return label -> {
            if (!label.equals(cut) || !once.compareAndSet(false, true)) { return; }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N3_RETIREMENT_PAUSED boundary={}", label);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            for (long remaining; (remaining = deadline - System.nanoTime()) > 0; ) {
                try { TimeUnit.NANOSECONDS.sleep(remaining); } catch (InterruptedException ignored) { }
            }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N3_RETIREMENT_PAUSE_EXPIRED boundary={}", label);
        };
    }

    /** Stop: release this coordinator's tokens and watches; T stays wherever its removal was not submitted. */
    void clear() {
        for (var entry : List.copyOf(pending.values())) {
            entry.releaseProofs();
            try { entry.releaseToken(); } catch (RuntimeException stopped) { entry.token = null; }
            if (entry.state != State.REMOVING) { entry.effects.discarded().run(); }
        }
        pending.clear(); refusals.clear(); warnedLevels.clear();
    }

    /** A publication refusal's DESTINATION_SPAWNED T may return only when no destination entity was admitted. */
    static boolean destinationAdmitted(MinecraftServer server, RocketTransferRecord record) {
        if (record.phase() == RocketTransferPhase.PREPARED) { return false; }
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        return level == null || record.destinationEntityId().map(level::getEntity).isPresent()
                || !RocketTransferEntities.loadedMatches(server, record, true).isEmpty();
    }
}
