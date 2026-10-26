package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryProof.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryCapture.Attributes;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryCapture.Capture;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryCapture.Result;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-074 items 4 to 6, 9 and 10: the satellite registry's checked writer. The owner thread captures
 * ({@link SatelliteRegistryCapture}) and publishes at tick START; one daemon IO thread, {@value #THREAD}, encodes,
 * checks and replaces. The epoch rules are {@code RegistryEpochs}'; this class holds the session, the mutation
 * sequence, the acknowledgement, the bounded checkpoint tickets and the stop path. W119's proof rule is unchanged.
 */
final class SatelliteRegistryWriter {
    static final String LABEL = "satellite_registry", THREAD = "ARCE-satellite-registry";
    static final String EPOCH_CAPACITY_EXHAUSTED = "EPOCH_CAPACITY_EXHAUSTED";
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final Thread owner = Thread.currentThread();
    private final Object session = new Object();
    private final long createdNanos = System.nanoTime();
    private final SatelliteMissionSavedData data;
    private final SatelliteMissionRegistry registry;
    private final List<Ticket> tickets = new ArrayList<>();
    private long mutationSequence, completedWrites, lastWriterNanos, lastWriterCpuNanos;
    private Acknowledgement acknowledged;
    private CheckedSavedDataPolicy policy = CheckedSavedDataPolicy.atomic(TYPE);
    private CheckedRootWriter<Capture, Attributes, Result> writer;
    private Path target;
    private Capture flight;
    private RuntimeException lastFailure;
    private boolean exhaustionReported;

    /** One checkpoint request: answered by the first published completion whose capture covers sequence m. */
    private static final class Ticket {
        private final long sequence;
        private Completion answer;

        private Ticket(long sequence) { this.sequence = sequence; }
    }

    SatelliteRegistryWriter(SatelliteMissionSavedData data, SatelliteMissionRegistry registry) {
        this.data = data; this.registry = registry;
    }

    void mutated() { requireOwner(); mutationSequence = Math.addExact(mutationSequence, 1L); }
    long sequence() { return mutationSequence; }
    long epochToWrite() { return registry.epochToWrite(); }
    Optional<Acknowledgement> acknowledgement() { requireOwner(); return Optional.ofNullable(acknowledged); }
    void policy(CheckedSavedDataPolicy policy) {
        requireOwner();
        if (policy.type() != TYPE) { throw new IllegalArgumentException("Wrong registry write policy"); }
        this.policy = policy;
    }
    CheckedSavedDataPolicy policy() { return policy; }

    Watch watch(UUID satellite) {
        requireOwner();
        return new Watch(session, mutationSequence, Identity.of(data.satellite(satellite).orElseThrow()));
    }

    /** A clean decoded current root is a disk observation in this session; restored roots need a new write. */
    void loaded() {
        requireOwner();
        if (data.operational() && !data.isDirty()) {
            var identities = new LinkedHashMap<UUID, Identity>();
            registry.satellites().forEach(state -> identities.put(state.satelliteId(), Identity.of(state)));
            acknowledged = new Acknowledgement(session, mutationSequence, registry.saveEpoch(), identities);
        }
    }

    /** The capture a write would take now, without putting it in flight (tests and the capture's own path). */
    Capture capture(long sequence) {
        if (!data.operational()) { return new Capture(sequence, 0L, 0L, null, data.blockedRoot(), null); }
        var epochs = registry.epochs();
        return new Capture(sequence, epochs.stateChanges(), epochs.toCarry(), registry.sections(), null, null);
    }

    /** A plain request with the write policy: autosave, the coalesced flush and prompt barrier requests. */
    boolean request(Path file) { return request(file, new Attributes(policy, null)); }

    /** Refused while stopping or closed, and when no epoch is representable. Never waits. */
    boolean request(Path file, Attributes attributes) {
        requireOwner();
        Path normalized = bind(file);
        if (shuttingDown()) { latch(); return false; }
        try {
            return writer(normalized).request(attributes, tick());
        } catch (CheckedRootWriter.CaptureRefused refused) {
            return false;
        }
    }

    /** Tick START: publish at most one completion, then start a pending request when the cadence allows. */
    void poll() {
        requireOwner();
        if (writer == null) { return; }
        if (shuttingDown()) { latch(); return; }
        try { writer.poll(tick()); } catch (CheckedRootWriter.CaptureRefused refused) { /* reported at capture */ }
    }

    boolean busy() { requireOwner(); return writer != null && writer.busy(); }
    boolean inFlight() { requireOwner(); return writer != null && writer.inFlight(); }
    boolean completionReady() { requireOwner(); return writer != null && writer.completionReady(); }
    long completedWrites() { return completedWrites; }
    long lastWriterNanos() { return lastWriterNanos; }
    long lastWriterCpuNanos() { return lastWriterCpuNanos; }

    /** Detached (unit tests and diagnostics): request one write, await its publication and throw unless it covers RAM. */
    void flush(Path file) {
        Completion completion = awaited(file, new Attributes(policy, null));
        if (completion.outcome() != Outcome.COMMITTED || data.isDirty()) {
            throw new IllegalStateException("Satellite registry write was not acknowledged: " + completion.outcome(), lastFailure);
        }
    }

    /** The checkpoint's fast path stays: a clean registry whose acknowledgement proves every watch writes nothing. */
    Completion checkpoint(Path file, List<Watch> watches, CheckedSavedDataPolicy policy,
                          CheckedSavedDataPolicy.DirectoryForce force) {
        requireOwner();
        var attributes = new Attributes(policy, force);
        if (data.operational() && !data.isDirty() && acknowledged != null && watches.stream().allMatch(acknowledged::proves)) {
            return new Completion(Outcome.COMMITTED, Optional.of(acknowledged), false);
        }
        return awaited(file, attributes);
    }

    /**
     * ADR-074 item 9. A running server's autosave and {@code /save-all} only request and never wait. A detached
     * registry has no server and awaits its write here, so its synchronous callers keep W119's results. A stopping
     * server drains to the final target (item 10).
     */
    void save(File file) {
        requireOwner();
        if (shuttingDown()) { stopSave(file.toPath()); return; }
        if (!data.isDirty()) { return; }
        if (data.server() == null) { awaited(file.toPath(), new Attributes(policy, null)); }
        else { request(file.toPath()); }
    }

    /** {@code ServerStoppingEvent} or the first observed shutdown flag: refuse requests and starts, discard tickets. */
    void latch() {
        requireOwner();
        if (writer != null) { writer.latchShutdown(); }
        tickets.clear();
    }

    /** Overworld unload, before storage closes: resume the same drain (or start a skipped one), then always join. */
    void close() {
        requireOwner();
        tickets.clear();
        if (writer == null) {
            if (!data.isDirty() || data.server() == null && target == null) { return; }
            writer(bind(target != null ? target : SatelliteMissionSavedData.file(data.server())));
        }
        writer.close(new Attributes(policy, null), System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    /** {@code ServerStoppedEvent} and exceptional cleanup: join without publishing; it never reports success. */
    void abort() {
        requireOwner();
        tickets.clear();
        if (writer != null) { writer.abort(); }
    }

    Optional<CheckedRootWriter.DrainReport> drainReport() {
        requireOwner();
        return writer == null ? Optional.empty() : Optional.ofNullable(writer.lastReport());
    }

    /**
     * Pumps publication on the calling thread until the writer is idle. Detached registries only, plus W129b's
     * development-only owner-thread await of the live barriers, which W129c replaces before any merge.
     */
    void await() {
        requireOwner();
        if (writer == null) { return; }
        try { writer.await(System::nanoTime, TimeUnit.NANOSECONDS::sleep); }
        catch (CheckedRootWriter.CaptureRefused refused) { /* reported at capture */ }
    }

    /** Request, await, and answer with the first published completion that covers the request's sequence. */
    private Completion awaited(Path file, Attributes attributes) {
        var ticket = new Ticket(mutationSequence);
        tickets.add(ticket);
        try {
            if (request(file, attributes)) { await(); }
            return ticket.answer != null ? ticket.answer : new Completion(Outcome.FAILED, Optional.empty(), false);
        } finally {
            tickets.remove(ticket);
        }
    }

    private void stopSave(Path file) {
        Path normalized = bind(file);
        tickets.clear();
        if (writer == null && !data.isDirty()) { return; }
        writer(normalized).drain(new Attributes(policy, null), System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    /** Owner thread: take the capture (rule 1). An unrepresentable epoch refuses it, so nothing retries. */
    private Capture take(long sequence) {
        if (flight != null) { throw new IllegalStateException("A registry capture is already in flight"); }
        Capture capture;
        try { capture = capture(sequence); }
        catch (ArithmeticException unrepresentable) {
            exhausted();
            throw new CheckedRootWriter.CaptureRefused(EPOCH_CAPACITY_EXHAUSTED);
        }
        if (capture.operational()) { registry.epochs().captured(capture.epoch()); }
        flight = capture;
        return capture;
    }

    /** Owner thread, tick START: rules 3 to 7 for the capture in flight, then the tickets it answers. */
    private void publish(CheckedRootWriter.Completion<Result> completion) {
        Capture capture = flight;
        if (capture == null || capture.sequence() != completion.sequence()) {
            throw new IllegalStateException("Foreign satellite registry completion");
        }
        flight = null;
        completedWrites++; lastWriterNanos = completion.writerNanos(); lastWriterCpuNanos = completion.writerCpuNanos();
        Result result = completion.payload();
        lastFailure = result == null ? null : result.failure();
        Outcome outcome = Outcome.valueOf(completion.status().name());
        boolean mutatedInFlight = mutationSequence != capture.sequence();
        if (capture.operational()) {
            switch (outcome) {
                case COMMITTED -> {
                    acknowledged = new Acknowledgement(session, capture.sequence(), capture.epoch(), result.identities());
                    registry.committed(capture.stateSequence());
                }
                case FAILED -> registry.epochs().failed(mutatedInFlight);
                case UNKNOWN -> { registry.epochs().unknown(); acknowledged = null; }
            }
        }
        if (outcome == Outcome.COMMITTED) {
            // Rule 7: a blocked root's commit only clears dirty; it never acknowledges a registration.
            data.published(!mutatedInFlight, !capture.operational()
                    || registry.epochs().stateChanges() == capture.stateSequence());
        }
        var answer = new Completion(outcome, outcome == Outcome.COMMITTED ? Optional.ofNullable(acknowledged)
                : Optional.empty(), result != null && result.directoryForceFailed());
        for (var ticket : tickets) {
            if (ticket.answer == null && ticket.sequence <= capture.sequence()) { ticket.answer = answer; }
        }
    }

    private Path bind(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (target != null && !target.equals(normalized)) {
            throw new IllegalArgumentException("Satellite registry writer cannot rebind its target");
        }
        target = normalized;
        return normalized;
    }

    private CheckedRootWriter<Capture, Attributes, Result> writer(Path file) {
        if (writer == null) { writer = new CheckedRootWriter<>(LABEL, THREAD, new Root(file)); }
        return writer;
    }

    private boolean shuttingDown() {
        MinecraftServer server = data.server();
        return server != null && (server.isStopped() || !server.isRunning());
    }

    /** The server tick; a detached registry counts one tick per monotonic millisecond, as the drain clock does. */
    private long tick() {
        MinecraftServer server = data.server();
        return server == null ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - createdNanos) : server.getTickCount();
    }

    /**
     * ADR-074 item 5, design section 4.3: a mutation needing a new epoch is admitted only while a successor of the
     * ceiling r = max(E, h, w) is representable; every stamp t is at most r. Checked before RAM, items or FE change.
     */
    boolean admitsChange() {
        requireOwner();
        if (registry.epochs().admitsChange()) { exhaustionReported = false; return true; }
        exhausted();
        return false;
    }

    /** One diagnostic per exhaustion streak, never per refused poll; the work stays dirty and unproved. */
    private void exhausted() {
        if (exhaustionReported) { return; }
        exhaustionReported = true;
        var epochs = registry.epochs();
        LOGGER.error("ARCE_SATELLITE_REGISTRY_EPOCH_EXHAUSTED durable={} reserved={} in_flight={} sequence={} dirty={}",
                epochs.durable(), epochs.reserved(), epochs.inFlight(), mutationSequence, data.isDirty());
    }

    /** Every registry mutation runs on the owner thread, never inside an IO callback (ADR-074 item 2). */
    void requireOwnerThread() { requireOwner(); }

    private void requireOwner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Registry writes require the owner thread"); }
    }

    /** The writer's view of this root. Only {@code write} runs on IO, and it reads the capture and its own file. */
    private final class Root implements CheckedRootWriter.Root<Capture, Attributes, Result> {
        private final Path file;

        private Root(Path file) { this.file = file; }

        @Override public long mutationSequence() { return mutationSequence; }
        @Override public Capture capture(long sequence) { return take(sequence); }
        @Override public CheckedRootWriter.Attempt<Result> write(Capture capture, Attributes attributes) {
            return SatelliteRegistryCapture.write(capture, attributes, file);
        }
        @Override public void publish(CheckedRootWriter.Completion<Result> completion) {
            SatelliteRegistryWriter.this.publish(completion);
        }
        @Override public boolean needsWrite() { return data.isDirty(); }
        @Override public Attributes merge(Attributes pending, Attributes later) { return pending.merge(later); }
        @Override public boolean serviced(Attributes attempted, Attributes requested) { return attempted.services(requested); }
    }
}
