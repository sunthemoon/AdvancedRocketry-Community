package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/** Session-owned state. Opening, inspecting and ordinary saves never create an unused file. */
public final class RocketRejoinSavedData extends SavedData implements AutoCloseable {
    public static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_REJOINS;
    private final Thread owner = Thread.currentThread();
    private final boolean present;
    private RocketRejoinRoot root;
    private Path file;
    private CheckedSavedDataPolicy policy;
    private CheckedSavedDataPolicy.DirectoryForce force;
    private Function<Path, CheckedSavedDataFile.Presence> presence;
    private RocketRejoinWriter writer;
    private CheckedJournalCheckpoint.Acknowledgement acknowledged;
    private RocketRejoinRoot acknowledgedRoot;
    private Outcome outcome = Outcome.UNACKNOWLEDGED;
    private long generation, completions;
    private boolean closed, failureReported;

    private RocketRejoinSavedData(RocketRejoinRoot root, boolean present) {
        this.root = root; this.present = present; this.acknowledgedRoot = present ? root : null;
    }
    public static RocketRejoinSavedData load(CompoundTag root) { return new RocketRejoinSavedData(RocketRejoinCodec.decode(root), true); }
    public static RocketRejoinSavedData open(Path file) {
        return open(file, CheckedSavedDataPolicy.atomic(TYPE), CheckedSavedDataFile::presence, CheckedSavedDataPolicy.DirectoryForce.PLATFORM);
    }
    static RocketRejoinSavedData open(Path file, CheckedSavedDataPolicy policy,
                                     Function<Path, CheckedSavedDataFile.Presence> probe, CheckedSavedDataPolicy.DirectoryForce force) {
        file = file.toAbsolutePath().normalize();
        if (policy.type() != TYPE || !file.getFileName().toString().equals(TYPE.fileName())) { throw new IllegalArgumentException("Wrong rejoin file identity"); }
        var state = probe.apply(file);
        if (state == CheckedSavedDataFile.Presence.UNKNOWN) { throw new IllegalStateException("Unknown rejoin presence"); }
        var data = state == CheckedSavedDataFile.Presence.ABSENT ? new RocketRejoinSavedData(RocketRejoinRoot.empty(), false)
                : load(policy.read(file).orElseThrow(() -> new IllegalStateException("Rejoin root disappeared during startup")));
        data.file = file; data.policy = policy; data.presence = probe; data.force = force; return data;
    }
    public RocketRejoinRoot root() { owner(); return root; }
    /** Last loaded or acknowledged root, retained for player names, not proof of a session write acknowledgement. */
    public RocketRejoinRoot acknowledgedRoot() { owner(); return acknowledgedRoot; }
    public java.util.List<RocketRejoinRoot> pendingRoots() {
        owner(); return writer == null ? java.util.List.of() : writer.pendingRoots();
    }
    public Outcome outcome() { owner(); return outcome; }
    public long completions() { owner(); return completions; }
    public boolean operational() { return true; } // Invalid roots refuse startup; never replace them with empty state.
    public boolean acknowledgedCurrent() { owner(); return acknowledged != null && acknowledged.sequence() == generation; }
    /** Loaded bytes classify seats immediately, but J1 needs this session's checked S1 acknowledgement. */
    public java.util.Optional<RocketReleasePermit> releasePermit(java.util.UUID release) {
        owner(); return root.batches().stream().filter(b -> b.release().equals(release) && releaseAcknowledged(b))
                .findFirst().map(b -> new RocketReleasePermit(this, b));
    }
    boolean releaseAcknowledged(RocketRejoinRoot.Batch batch) {
        owner(); return !closed && acknowledged != null && acknowledgedRoot != null
                && root.batches().contains(batch) && acknowledgedRoot.batches().contains(batch)
                && acknowledgedRoot.entries().stream().anyMatch(e -> e.release().equals(batch.release()) && e.seat() == RocketRejoinRoot.Seat.PENDING);
    }
    /** Storage only; no command calls this before the release and protected-rejoin leaves are integrated. */
    public boolean submit(RocketRejoinRoot candidate) {
        owner();
        if (closed || file == null || outcome == Outcome.UNKNOWN || outcome == Outcome.NOT_COMMITTED || generation == Long.MAX_VALUE) { return false; }
        if (candidate.nextSequence() < root.nextSequence()) { throw new IllegalArgumentException("Rejoin sequence moved backwards"); }
        if (!present && generation == 0 && candidate.batches().isEmpty()) { return false; }
        if (writer == null) { writer = new RocketRejoinWriter(file, policy, present, presence, force); }
        if (!writer.request(generation + 1, candidate)) { return false; }
        root = candidate; generation++; setDirty(); return true;
    }
    public void tick(long tick) { owner(); if (writer != null && !closed) { writer.tick(tick, this::publish); } }
    private void publish(RocketRejoinWriter.Completion result) {
        owner(); completions++; outcome = result.outcome(); acknowledged = result.acknowledged();
        if (outcome == Outcome.COMMITTED) {
            acknowledgedRoot = result.capture().root();
            if (acknowledgedCurrent()) { setDirty(false); }
            if (failureReported) { AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_WRITE_RECOVERED"); failureReported = false; }
        } else if (!failureReported) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_REJOIN_WRITE_HELD outcome={}; pending obligations retained", outcome);
            failureReported = true;
        }
    }
    @Override public CompoundTag save(CompoundTag unused) { owner(); return RocketRejoinCodec.encode(root); }
    @Override public void save(File target) {
        owner();
        if (file != null && !file.equals(target.toPath().toAbsolutePath().normalize())) { throw new IllegalArgumentException("Cannot rebind rejoin writer"); }
        // submit owns checked generations; vanilla must never write an unchecked or unused root.
    }
    @Override public void close() { owner(); if (!closed) { if (writer != null) { writer.close(this::publish); } closed = true; } }
    private void owner() { if (Thread.currentThread() != owner) { throw new IllegalStateException("Rejoin state requires its owner thread"); } }
}
