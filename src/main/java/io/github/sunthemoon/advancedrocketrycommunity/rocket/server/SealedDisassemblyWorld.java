package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedJournalRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Original-worker C0-C5 adapter; full terrain and entity slots share the ordinary capture/force/readback budgets. */
final class SealedDisassemblyWorld implements SealedDisassembly.Port, AutoCloseable {
    private record Slot(boolean entity, ChunkPos position) { }
    final RocketPersistedTransaction entry;
    final SealedTerrain terrain;
    final SealedEntityView entities;
    private final ServerLevel level;
    private final RocketTransactionSavedData data;
    private final SealedJournalEntry journal;
    private final SealedJournalRequests writes;
    private final BooleanSupplier currentRequest, attemptRoom, claimCurrent;
    private final boolean recovery;
    private SealedProofBatch batch;
    private long proofRetryAt = Long.MIN_VALUE;
    private SealedDisassembly.Goal goal;
    private String reason;
    private String proofReason;
    private volatile String validation;

    SealedDisassemblyWorld(ServerLevel level, RocketPersistedTransaction entry, SealedTerrain terrain,
            SealedJournalRequests writes, BooleanSupplier currentRequest, BooleanSupplier attemptRoom,
            BooleanSupplier claimCurrent, boolean recovery) {
        this.level = level; this.entry = entry; this.terrain = terrain; this.writes = writes;
        this.currentRequest = currentRequest; this.attemptRoom = attemptRoom; this.claimCurrent = claimCurrent; this.recovery = recovery;
        data = RocketTransactionSavedData.get(level.getServer()); journal = new SealedJournalEntry(data, entry);
        entities = new SealedEntityView(level, entry, terrain.chunks().size());
        if (terrain.chunks().size() + entities.columns().size() > RocketOrdinaryRetirement.MAX_SLOTS) {
            throw new IllegalArgumentException("Sealed native proof plan exceeds existing slot bound");
        }
    }
    @Override public long now() { return level.getServer().getTickCount(); }
    @Override public SealedDisassembly.Gate gate() {
        if (!data.operational() || !journal.compatible() || !claimCurrent.getAsBoolean()) { return SealedDisassembly.Gate.CONFLICT; }
        if (level.noSave()) { return SealedDisassembly.Gate.SAVE_OFF; }
        return entities.loaded() && terrain.chunks().stream().allMatch(chunk -> level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null)
                ? SealedDisassembly.Gate.READY : SealedDisassembly.Gate.UNLOADED;
    }
    @Override public boolean requestCurrent() {
        boolean current = currentRequest.getAsBoolean() && entities.observe() == SealedDisassembly.Entity.MATCHING;
        if (!current) { reason = "REQUEST_CHANGED:" + entities.diagnostic(); }
        return current;
    }
    @Override public boolean predecessorAbsent() {
        var transfers = RocketTransferSavedData.get(level.getServer());
        var logical = entities.logical();
        if (!transfers.operational() || (logical == null ? !transfers.acknowledgedCurrent() : !transfers.actionAuthorityAvailable(logical))) { return false; }
        if (transfers.entries().stream().anyMatch(record -> logical != null && record.logicalRocketId().equals(logical)
                || record.sourceSnapshot().snapshotId().equals(entry.snapshot().snapshotId())
                || record.destinationSnapshot().snapshotId().equals(entry.snapshot().snapshotId())
                || record.sourceEntityId().equals(entry.record().rocketEntityId())
                || record.destinationEntityId().filter(entry.record().rocketEntityId()::equals).isPresent())) { return false; }
        if (journal.owned()) { return true; }
        boolean absent = data.acknowledgedAbsent(logical == null ? entry.record().transactionId() : logical,
                entry.snapshot().snapshotId(), entry.record().rocketEntityId());
        if (!absent) { writes.request(); }
        return absent;
    }
    @Override public SealedDisassembly.Cell inspect(int index) { return terrain.inspect(index); }
    @Override public boolean stage(int index) { invalidateProof(); return terrain.stage(index); }
    @Override public boolean erase(int index) { invalidateProof(); return terrain.erase(index); }
    @Override public SealedDisassembly.Entity entity() { return entities.observe(); }
    @Override public boolean discard() { return predecessorAbsent() && entities.discard(); }
    @Override public void retain() { journal.retain(); }
    @Override public boolean retainedAcknowledged() { return journal.retainedAcknowledged(); }
    @Override public void elect() { journal.elect(); }
    @Override public boolean electedAcknowledged() { return journal.electedAcknowledged(); }
    @Override public void removeRecord() { journal.remove(); }
    @Override public boolean absentAcknowledged() { return journal.absentAcknowledged(); }
    @Override public void checkpoint() { writes.request(); }
    @Override public boolean proofCurrent(SealedDisassembly.Goal goal) {
        return this.goal == goal && batch != null && batch.current() && predecessorAbsent()
                && (goal == SealedDisassembly.Goal.SEALED || entity() == (goal == SealedDisassembly.Goal.COMPLETE
                ? SealedDisassembly.Entity.ABSENT : presentFor(goal) ? SealedDisassembly.Entity.MATCHING : SealedDisassembly.Entity.ABSENT));
    }
    private boolean presentFor(SealedDisassembly.Goal goal) {
        return goal == SealedDisassembly.Goal.ROLLBACK || goal == SealedDisassembly.Goal.PREPARE && entities.observe() == SealedDisassembly.Entity.MATCHING;
    }
    @Override public void requestProof(SealedDisassembly.Goal goal) {
        if (this.goal == goal && batch != null || !terrain.prepared()) { return; }
        if (batch != null) { invalidateProof(); }
        this.goal = goal;
        var slots = new ArrayList<Slot>();
        if (goal != SealedDisassembly.Goal.SEALED) { entities.columns().forEach(chunk -> slots.add(new Slot(true, chunk))); }
        terrain.chunks().forEach(chunk -> slots.add(new Slot(false, chunk)));
        boolean present = presentFor(goal);
        batch = new SealedProofBatch(slots.size(), new NativeProofs(List.copyOf(slots), goal, present));
        batch.deferUntil(proofRetryAt);
    }
    void tickProof() { if (batch != null) { batch.tick(); } }
    void tickProofDeadline() { if (batch != null) { batch.tickDeadline(); } }
    boolean activeAttempt() { return batch != null && batch.active(); }
    String reason() { return reason + ":proof=" + proofReason + ":validation=" + validation + ":" + (batch == null ? "no-batch" : batch.diagnostic()); }
    void invalidateProof() {
        if (batch != null) {
            // A changed cell invalidates authority, not an IO/veto/deadline retry delay already imposed.
            proofRetryAt = Math.max(proofRetryAt, batch.retryAt()); batch.close(); batch = null; goal = null;
        }
    }
    @Override public void held(SealedDisassembly.Phase phase, SealedDisassembly.Reason reason) { this.reason = phase + ":" + reason; }
    @Override public void close() { if (batch != null) { batch.close(); } terrain.close(); }

    private final class NativeProofs implements SealedProofBatch.Port {
        private final List<Slot> slots;
        private final SealedDisassembly.Goal goal;
        private final boolean present;
        NativeProofs(List<Slot> slots, SealedDisassembly.Goal goal, boolean present) { this.slots = slots; this.goal = goal; this.present = present; }
        @Override public long now() { return SealedDisassemblyWorld.this.now(); }
        @Override public SealedProofBatch.Readiness readiness() { return SealedProofBatch.Readiness.valueOf(gate().name()); }
        @Override public boolean attemptRoom() { return attemptRoom.getAsBoolean(); }
        @Override public boolean available(int index) {
            var runtime = EscrowRuntime.of(level.getServer()); var slot = slots.get(index); var name = level.dimension().location().toString();
            return slot.entity ? runtime.barrier().availableOrdinary(runtime.store(level), name, slot.position, now())
                    : runtime.barrier().availableOrdinaryTerrain(runtime.store(level), name, slot.position, now());
        }
        @Override public boolean capture(int index, Consumer<EscrowBarrier.Result> callback) {
            var runtime = EscrowRuntime.of(level.getServer()); var slot = slots.get(index); var name = level.dimension().location().toString();
            validation = index + ":not-validated";
            Consumer<String> observed = value -> validation = index + ":" + value;
            // The reader runs off-thread. Capture immutable inputs now, not the coordinator's later live/closed state.
            java.util.function.Predicate<CompoundTag> validator = slot.entity ? entities.validator(slot.position, present, observed)
                    : terrain.validator(slot.position, goal, recovery, observed);
            return slot.entity ? runtime.barrier().beginOrdinary(runtime.store(level), name, slot.position, validator, now(), label -> { }, callback)
                    : runtime.barrier().beginOrdinaryTerrain(runtime.store(level), name, slot.position, validator, now(), label -> { }, callback);
        }
        @Override public void held(String reason) { SealedDisassemblyWorld.this.proofReason = goal + ":" + reason; }
    }
}
