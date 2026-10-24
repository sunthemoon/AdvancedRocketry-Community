package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Session-owned W80 seat track. No player placement, login guard, resource mutation or production admission. */
public final class RocketReleaseCoordinator implements AutoCloseable {
    public static final int B0_TICKS = 200, RETRY_TICKS = 200, TICKET_TICKS = 400;
    public static final int BATCHES_PER_TICK = 2, MAX_TICKETS = 6;
    public enum SlotState { READY, UNLOADED, MISSING, BLOCKED }
    private enum Stage { B0, S1, B1, J1, S2 }

    /** Native proof watches remain held until S2's acknowledgement, or until an invalidated attempt is replaced. */
    public interface Proof extends AutoCloseable {
        boolean valid(); int copies(); void close();
    }
    public interface Port {
        long now();
        /** Existing escrow/bay and authority-identity interlocks, not permission to bypass the release's own fence. */
        boolean boundary(Batch batch);
        boolean applySeats(Batch batch);
        boolean crewReady(Batch batch);
        SlotState slot(Slot slot);
        void ticket(UUID release, Slot slot);
        void unticket(UUID release, Slot slot);
        /** False admits nothing. A null proof is a failed native attempt; completion runs on the owner thread. */
        boolean capture(Batch batch, Slot slot, RocketPassengerManifest b0, Consumer<Proof> complete);
        void flushJournal();
        void completed(Batch batch);
        void notice(Batch batch, String state);
    }
    private static final class Work {
        final Batch batch;
        final RocketReleasePlan plan;
        final long admitted;
        final Map<Slot, Proof> proofs = new LinkedHashMap<>();
        final Set<Slot> tickets = new LinkedHashSet<>();
        Stage stage;
        boolean durable, submitted, journalInstalled, seatSubmitted, capturing, attemptStarted, goneReported;
        int attempt, cursor;
        long notBefore, started;
        String notice;
        // The IO thread reads this immutable lease, never the maps, entities, journal RAM or the port.
        volatile BooleanSupplier completionLease = () -> false;
        Work(Batch batch, RocketReleasePlan plan, long now) {
            this.batch = batch; this.plan = plan; admitted = now; durable = plan == null;
            stage = plan == null ? Stage.S1 : Stage.B0;
        }
    }
    private final Thread owner = Thread.currentThread();
    private final RocketRejoinSavedData sidecar;
    private final RocketTransferSavedData journal;
    private final Port port;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private int cursor;
    private boolean closed;

    public RocketReleaseCoordinator(RocketRejoinSavedData sidecar, RocketTransferSavedData journal, Port port) {
        this.sidecar = sidecar; this.journal = journal; this.port = port;
        // Loaded obligations classify the first join; they do not assert any session-local native or journal proof.
        for (var batch : sidecar.root().batches()) {
            if (pendingSeats(sidecar.root(), batch.release())) { work.put(batch.release(), new Work(batch, null, port.now())); }
        }
    }

    /** Internal only. The world adapter must check its test gate and admission witnesses before calling. */
    public boolean admit(RocketReleasePlan plan) {
        owner(); var batch = plan.batch();
        var existing = work.values().stream().filter(w -> w.batch.rocket().equals(batch.rocket())).findFirst().orElse(null);
        if (existing != null) { return existing.batch.transfer().equals(batch.transfer()); }
        // Reserve the next sequence before B0. Only one unpublished admission may own it.
        if (closed || work.size() >= MAX_BATCHES || work.values().stream().anyMatch(w -> !w.durable)
                || !sidecar.pendingRoots().isEmpty() || !journal.actionable(batch.rocket())
                || !port.boundary(batch)) { return false; }
        try { plan.admit(sidecar.root()); } catch (IllegalArgumentException stale) { return false; }
        var entry = new Work(batch, plan, port.now());
        if (!versionAllowed(entry)) { return false; }
        work.put(batch.release(), entry); notice(entry, "B0"); return true;
    }

    public boolean fenced(UUID logical) {
        owner(); return work.values().stream().anyMatch(w -> w.batch.rocket().equals(logical));
    }
    public RocketPassengerManifest filter(UUID logical, RocketPassengerManifest current) {
        owner();
        for (var entry : work.values()) {
            if (entry.durable && entry.batch.rocket().equals(logical)) { current = RocketReleasePlan.filter(current, entry.batch.released()); }
        }
        return current;
    }
    public boolean pending(UUID transfer) { owner(); return work.values().stream().anyMatch(w -> w.batch.transfer().equals(transfer)); }
    public java.util.Optional<UUID> release(UUID transfer) {
        owner(); return work.values().stream().filter(w -> w.batch.transfer().equals(transfer)).map(w -> w.batch.release()).findFirst();
    }
    public boolean released(UUID logical, UUID player) {
        owner(); return work.values().stream().anyMatch(w -> w.batch.rocket().equals(logical) && w.batch.released().contains(player));
    }
    public String stage(UUID release) { owner(); var entry = work.get(release); return entry == null ? "NONE" : entry.stage.name(); }
    public int tickets() { owner(); return work.values().stream().mapToInt(w -> w.tickets.size()).sum(); }

    public void tick() {
        owner(); if (closed || work.isEmpty()) { return; }
        var entries = new ArrayList<>(work.values()); int size = entries.size();
        for (int visited = 0; visited < Math.min(BATCHES_PER_TICK, size); visited++) {
            Work entry = entries.get(Math.floorMod(cursor++, size));
            if (work.get(entry.batch.release()) != entry) { continue; }
            try { step(entry); }
            catch (RuntimeException unavailable) { fail(entry, "UNAVAILABLE"); }
        }
        cursor = Math.floorMod(cursor, Math.max(1, work.size()));
    }

    private void step(Work entry) {
        if (entry.stage == Stage.B0 && port.now() - entry.admitted >= B0_TICKS) { drop(entry, "B0_FAILED"); return; }
        if (port.now() < entry.notBefore) { return; }
        if (!versionAllowed(entry)) { fail(entry, "CONFLICT"); return; }
        if (!port.boundary(entry.batch)) { fail(entry, "BOUNDARY_HELD"); return; }
        if (entry.stage == Stage.B0) { b0(entry); return; }
        if (entry.stage == Stage.S1) {
            if (sidecar.releasePermit(entry.batch.release()).isEmpty()) {
                if (!entry.submitted && sidecar.pendingRoots().isEmpty()) {
                    var candidate = entry.plan == null ? sidecar.root() : entry.plan.admit(sidecar.root());
                    entry.submitted = sidecar.submit(candidate);
                }
                return;
            }
            entry.durable = true;
            if (!port.applySeats(entry.batch)) { fail(entry, "ENTITY_HELD"); return; }
            entry.stage = Stage.B1; notice(entry, "E1");
        }
        if (entry.stage == Stage.B1) { b1(entry); }
        if (entry.stage == Stage.J1) { j1(entry); }
        if (entry.stage == Stage.S2) { s2(entry); }
    }

    private void b0(Work entry) {
        Slot slot = authoritySlot(entry.batch);
        if (port.slot(slot) != SlotState.READY) { drop(entry, "B0_FAILED"); return; }
        if (entry.capturing) { return; }
        if (entry.proofs.containsKey(slot)) {
            var proof = entry.proofs.get(slot);
            if (!proof.valid() || proof.copies() != 1) { drop(entry, "B0_FAILED"); return; }
            entry.stage = Stage.S1;
            notice(entry, "S1");
            entry.submitted = sidecar.submit(entry.plan.admit(sidecar.root()));
            if (!entry.submitted) { drop(entry, "B0_FAILED"); return; }
            releaseAttempt(entry); return;
        }
        capture(entry, slot, entry.plan.manifest());
        // An own-store failure can complete synchronously; never use the removed admission afterward.
        if (work.get(entry.batch.release()) == entry && !entry.capturing && entry.proofs.containsKey(slot)) { b0(entry); }
    }

    private void b1(Work entry) {
        if (!entry.attemptStarted) {
            if (work.values().stream().filter(w -> w.attemptStarted).count() >= BATCHES_PER_TICK) { return; }
            if (entry.batch.slots().stream().anyMatch(s -> port.slot(s) == SlotState.MISSING || port.slot(s) == SlotState.BLOCKED)) {
                fail(entry, "SLOT_HELD"); return;
            }
            var cold = entry.batch.slots().stream().filter(s -> port.slot(s) == SlotState.UNLOADED).toList();
            if (tickets() + cold.size() > MAX_TICKETS) { fail(entry, "TICKET_CAP"); return; }
            entry.attemptStarted = true; entry.started = port.now();
            for (Slot slot : cold) { port.ticket(entry.batch.release(), slot); entry.tickets.add(slot); }
        }
        // The attempt never refreshes its own 400-tick tickets past their original lifetime.
        if (port.now() - entry.started >= TICKET_TICKS) { fail(entry, "TICKET_EXPIRED"); return; }
        if (entry.proofs.values().stream().anyMatch(p -> !p.valid())) { fail(entry, "PROOF_EXPIRED"); return; }
        if (!entry.batch.slots().stream().allMatch(s -> port.slot(s) == SlotState.READY)) { return; }
        if (!port.applySeats(entry.batch)) { fail(entry, "ENTITY_HELD"); return; }
        if (entry.capturing) { return; }
        if (entry.cursor < entry.batch.slots().size()) {
            capture(entry, entry.batch.slots().get(entry.cursor), null);
            return;
        }
        if (entry.proofs.size() != entry.batch.slots().size() || !nativeValid(entry)) { fail(entry, "PROOF_HELD"); return; }
        int copies = entry.proofs.values().stream().mapToInt(Proof::copies).sum();
        if (copies < 0 || copies > 1) { fail(entry, "COPIES_CONFLICT"); return; }
        if (copies == 0) { if (!entry.goneReported) { notice(entry, "GONE"); entry.goneReported = true; } }
        else { notice(entry, "B1"); }
        entry.stage = entry.journalInstalled ? Stage.S2 : Stage.J1;
    }

    private void j1(Work entry) {
        if (!nativeValid(entry)) { fail(entry, "PROOF_EXPIRED"); return; }
        var permit = sidecar.releasePermit(entry.batch.release()).orElse(null);
        if (permit == null || !journal.releasePassengers(permit, port.crewReady(entry.batch), () -> nativeValid(entry))) { return; }
        entry.journalInstalled = true; entry.stage = Stage.S2; notice(entry, "J1"); port.flushJournal();
    }

    private void s2(Work entry) {
        if (!nativeValid(entry)) { fail(entry, "PROOF_EXPIRED"); return; }
        port.flushJournal();
        // Acquire anew after J1, including removal. The pre-mutation lease can never be revived.
        var lease = journal.logicalLease(entry.batch.rocket(), entry.batch.transfer(), 0);
        if (!journal.actionable(entry.batch.rocket()) || !lease.getAsBoolean()) { entry.completionLease = () -> false; return; }
        var proofs = List.copyOf(entry.proofs.values());
        entry.completionLease = () -> lease.getAsBoolean() && proofs.stream().allMatch(Proof::valid);
        if (!entry.seatSubmitted) {
            var candidate = sidecar.root();
            for (var old : sidecar.root().entries()) {
                if (old.release().equals(entry.batch.release())) {
                    candidate = candidate.update(new Entry(old.release(), old.player(), old.seatIndex(), old.placement(), Seat.VERIFIED, old.attempt()));
                }
            }
            entry.seatSubmitted = sidecar.submit(candidate, () -> entry.completionLease.getAsBoolean());
            return;
        }
        var acknowledged = sidecar.acknowledgedRoot();
        if (acknowledged != null && !pendingSeats(acknowledged, entry.batch.release()) && entry.completionLease.getAsBoolean()) {
            notice(entry, "S2"); releaseAttempt(entry); work.remove(entry.batch.release()); port.completed(entry.batch);
        }
    }

    private void capture(Work entry, Slot slot, RocketPassengerManifest b0) {
        int attempt = entry.attempt; entry.capturing = true;
        boolean accepted = port.capture(entry.batch, slot, b0, proof -> {
            owner();
            if (closed || work.get(entry.batch.release()) != entry || entry.attempt != attempt) { if (proof != null) { proof.close(); } return; }
            entry.capturing = false;
            if (proof == null || !proof.valid() || proof.copies() < 0 || proof.copies() > 1) {
                if (proof != null) { proof.close(); } fail(entry, "CAPTURE_FAILED"); return;
            }
            entry.proofs.put(slot, proof); entry.cursor++;
        });
        if (!accepted && work.get(entry.batch.release()) == entry) { entry.capturing = false; fail(entry, "CAPTURE_REFUSED"); }
    }
    private boolean nativeValid(Work entry) {
        return entry.proofs.size() == entry.batch.slots().size() && entry.proofs.values().stream().allMatch(Proof::valid);
    }
    private boolean versionAllowed(Work entry) {
        if (!journal.operational()) { return false; }
        var record = journal.findByLogicalRocket(entry.batch.rocket()).orElse(null);
        if (record == null) { return (entry.durable || entry.submitted) && journal.find(entry.batch.transfer()).isEmpty(); }
        if (record.nativeTransfer().isPresent() || !record.transferId().equals(entry.batch.transfer())) { return false; }
        var version = RocketReleasePlan.version(record);
        return version.equals(entry.batch.scrubbed()) || !entry.journalInstalled && version.equals(entry.batch.before());
    }
    private void fail(Work entry, String reason) {
        if (entry.stage == Stage.B0) { drop(entry, "B0_FAILED"); return; }
        releaseAttempt(entry); entry.notBefore = port.now() + RETRY_TICKS;
        if (entry.durable && entry.stage != Stage.S1) { entry.stage = Stage.B1; }
        notice(entry, reason);
    }
    private void drop(Work entry, String reason) { notice(entry, reason); releaseAttempt(entry); work.remove(entry.batch.release()); }
    private void releaseAttempt(Work entry) {
        entry.completionLease = () -> false; entry.attempt++; entry.capturing = false; entry.cursor = 0;
        entry.proofs.values().forEach(Proof::close); entry.proofs.clear();
        for (Slot slot : entry.tickets) { port.unticket(entry.batch.release(), slot); }
        entry.tickets.clear(); entry.attemptStarted = false;
    }
    private void notice(Work entry, String reason) {
        if (!reason.equals(entry.notice)) { entry.notice = reason; port.notice(entry.batch, reason); }
    }
    private static boolean pendingSeats(RocketRejoinRoot root, UUID release) {
        return root.entries().stream().anyMatch(e -> e.release().equals(release) && e.seat() == Seat.PENDING);
    }
    private static Slot authoritySlot(Batch batch) { return new Slot(batch.dimension(), batch.pad().x() >> 4, batch.pad().z() >> 4); }
    @Override public void close() { owner(); closed = true; work.values().forEach(this::releaseAttempt); work.clear(); }
    private void owner() { if (Thread.currentThread() != owner) { throw new IllegalStateException("Release requires owner thread"); } }
}
