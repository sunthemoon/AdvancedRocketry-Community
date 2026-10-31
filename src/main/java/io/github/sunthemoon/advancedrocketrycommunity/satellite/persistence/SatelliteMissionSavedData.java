package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SchedulerPass;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Overworld-owned mission authority; malformed or future data is preserved fail-closed. */
public final class SatelliteMissionSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_satellite_missions";

    private final SatelliteMissionRegistry registry;
    private final SatelliteRegistryWriter writer;
    // Detached codec/domain fixtures have no world. Every production get binds its server's live receipt index.
    private java.util.function.BiPredicate<UUID, Boolean> deploymentFence = (satellite, removal) -> false;
    private MinecraftServer ownerServer;
    private io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayRegistryPermit scopedPermit;
    private boolean registering;
    private CompoundTag preservedBlockedData;
    private SatelliteCanonicalBoundsException canonicalBoundsFailure;
    /** ADR-050 section 2: a state change waits here for the next coalesced flush (at most one per 100 ticks). */
    private boolean flushPending;

    private SatelliteMissionSavedData(SatelliteMissionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.writer = new SatelliteRegistryWriter(this, registry);
        io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RestoreReport report = registry.restoreReport();
        if (report.changed()) {
            // ADR-050 section 9: what the load invariants held is persisted with the next coalesced flush.
            setDirty();
            flushPending = true;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_SATELLITE_RESTORE quarantined_missions={} recovery_required={} quarantined_instances={} "
                            + "accounts_added={} quarantined_ground_surveys={}", report.quarantinedMissions(), report.recoveries(),
                    report.quarantinedInstances(), report.accountsAdded(), report.quarantinedGroundSurveys());
        }
    }

    public static SatelliteMissionSavedData create(long observedGameTime) {
        return new SatelliteMissionSavedData(SatelliteMissionRegistry.create(observedGameTime, CodecRecordSizer.INSTANCE));
    }

    public static SatelliteMissionSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        SatelliteMissionSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                SatelliteMissionSavedData::load,
                () -> create(server.overworld().getGameTime()),
                DATA_NAME
        );
        data.bind(server);
        return data;
    }

    /** The lifecycle hooks' view: the cached registry, or the one vanilla reads from its file, never a new empty one. */
    public static SatelliteMissionSavedData existing(MinecraftServer server) {
        if (server.overworld() == null) { return null; }
        var data = server.overworld().getDataStorage().get(SatelliteMissionSavedData::load, DATA_NAME);
        if (data != null) { data.bind(server); }
        return data;
    }

    private void bind(MinecraftServer server) {
        if (ownerServer != server) {
            if (ownerServer != null) { throw new IllegalStateException("Registry belongs to another server"); }
            ownerServer = server;
            deploymentFence = (satellite, removal) ->
                    io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts.blocksRegistry(server, satellite, removal);
        }
    }

    public static SatelliteMissionSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        CompoundTag preserved = source.copy();
        SatelliteMissionSavedData data = create(0L);
        try {
            if (SatelliteNbtSize.uncompressedBytes(source) > SatelliteLimits.MAX_REGISTRY_NBT_BYTES) {
                throw new IllegalArgumentException("Satellite registry exceeds its fixed NBT bound");
            }
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.SATELLITE_MISSIONS,
                    source
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                throw new IllegalArgumentException("Satellite registry uses a future root schema");
            }
            data = new SatelliteMissionSavedData(SatelliteRegistryPayload.decodeCurrent(migration.payload()));
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.preservedBlockedData = preserved;
            data.canonicalBoundsFailure = SatelliteCanonicalBoundsException.find(exception).orElse(null);
        }
        data.writer.loaded();
        return data;
    }

    /** Startup validation needs the precise bounded reason rather than only the blocked/operational bit. */
    public Optional<SatelliteCanonicalBoundsException> canonicalBoundsFailure() {
        return Optional.ofNullable(canonicalBoundsFailure);
    }

    private void changed() {
        setDirty();
        registry.markChanged();
        flushPending = true;
    }

    /** Clock observations can change even when a domain operation refuses or throws. */
    private <T> T observeClock(java.util.function.Supplier<T> operation) {
        long before = registry.lastObservedGameTime();
        try { return operation.get(); }
        finally {
            // Clock-only writes retain E and the existing ordinary-autosave cadence, not flushPending.
            if (before != registry.lastObservedGameTime()) { setDirty(); }
        }
    }

    @Override
    public void setDirty(boolean dirty) {
        if (dirty) { writer.mutated(); }
        super.setDirty(dirty);
    }

    /** Publication of a COMMITTED capture: dirty clears if nothing mutated since it, flushPending if no state changed. */
    void published(boolean clean, boolean settled) {
        if (clean) { super.setDirty(false); }
        if (settled) { flushPending = false; }
    }
    MinecraftServer server() { return ownerServer; }
    public long mutationSequence() { return writer.sequence(); }
    public SatelliteRegistryProof.Watch watch(UUID satellite) { requireOperational(); return writer.watch(satellite); }
    public Optional<SatelliteRegistryProof.Acknowledgement> acknowledgement() { return writer.acknowledgement(); }
    void writePolicy(CheckedSavedDataPolicy policy) { writer.policy(policy); }
    void deploymentFence(java.util.function.BiPredicate<UUID, Boolean> fence) { deploymentFence = Objects.requireNonNull(fence); }
    /** Validation and its previews: the receipt fence, except for the one satellite a scoped coordinator permit covers. */
    public boolean deploymentPending(UUID satellite) {
        if (scopedPermit != null && scopedPermit.covers(satellite)) { return false; }
        return deploymentFence.test(satellite, false);
    }
    /** Launch and launchIdle honour a permit only inside {@link #registerWithPermit} (W170 D2); decommission never does. */
    private boolean launchFenced(UUID satellite) {
        return !(registering && scopedPermit != null && scopedPermit.covers(satellite)) && deploymentFence.test(satellite, false);
    }

    /**
     * W128 D2: runs one validation with the coordinator's permit in scope, on the owner thread. Only {@link
     * #deploymentPending} (the validation check and the two previews) honours it; {@code launch}, {@code launchIdle}
     * and {@code decommission} ignore it, and the scope ends when the call returns or throws. Scopes do not nest.
     */
    public <T> T validateWithPermit(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayRegistryPermit permit,
            java.util.function.Supplier<T> validation) {
        return scoped(permit, false, validation);
    }

    /**
     * W170 D2: as {@link #validateWithPermit}, and {@code launch} and {@code launchIdle} of the permit's own satellite
     * also honour it, so the coordinator registers X while its INTENT fences every other caller. {@code decommission}
     * still ignores it.
     */
    public <T> T registerWithPermit(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayRegistryPermit permit,
            java.util.function.Supplier<T> registration) {
        return scoped(permit, true, registration);
    }

    private <T> T scoped(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayRegistryPermit permit, boolean register,
            java.util.function.Supplier<T> call) {
        Objects.requireNonNull(permit, "permit"); Objects.requireNonNull(call, "call");
        if (ownerServer != null && !ownerServer.isSameThread()) { throw new IllegalStateException("Permit scope requires the server thread"); }
        if (scopedPermit != null) { throw new IllegalStateException("Registry permit scopes do not nest"); }
        scopedPermit = permit; registering = register;
        try { return call.get(); } finally { scopedPermit = null; registering = false; }
    }
    private static SatelliteOperationResult deploymentPendingResult() {
        return new SatelliteOperationResult(SatelliteOperationCode.DEPLOYMENT_PENDING, false, Optional.empty(), Optional.empty(), 0);
    }

    /**
     * GameTest and release-test servers only (ADR-074 item 6): request, await publication on the owner thread and throw
     * unless the write covers RAM. A normal running server never waits; production callers {@link #request}.
     */
    public void flush(MinecraftServer server) { SatelliteRegistryTestAwait.require(server); flush(file(server)); }
    /** GameTest and release-test servers only: pump the writer on the owner thread until it is idle. */
    public void awaitForTests(MinecraftServer server) { SatelliteRegistryTestAwait.require(server); writer.await(); }
    /** The release-test cut seams (W129bc); inert unless armed on an empty release-test dedicated server. */
    public SatelliteRegistryWriterCuts cuts() { return writer.cuts(); }
    /**
     * GameTest and release-test servers only: one write on the bound target whose committer fails before replacement
     * (research's same-target failure seam); a fake target is never bound. Returns that write's outcome.
     */
    public SatelliteRegistryProof.Outcome failOneWriteForTests(MinecraftServer server) {
        SatelliteRegistryTestAwait.require(server);
        return writer.failOnce(file(server));
    }
    /** ADR-074 item 6: a prompt plain request; the write runs off-thread and is published on a later tick START. */
    public boolean request(MinecraftServer server) {
        if (ownerServer != server || !server.isSameThread()) {
            throw new IllegalStateException("Registry requests require their owning server thread");
        }
        return writer.request(file(server));
    }
    /** Detached registries: request, await publication and throw unless the write covers RAM. */
    public void flush(java.nio.file.Path file) { writer.flush(file); }
    /** ADR-074 item 9: running, a dirty root requests and returns; stopping, it drains to the final target. */
    @Override public void save(java.io.File file) { writer.save(file); }
    /** Tick START (SatelliteRegistryLifecycle): publish at most one completion, then start a pending request. */
    public void pollWriter() { writer.poll(); }
    public void latchShutdown() { writer.latch(); }
    public void close() { writer.close(); }
    public void abort() { writer.abort(); }

    /** ADR-074 item 6: the directory-forcing checkpoint as a request; its answer is published on a later tick. */
    public SatelliteRegistryProof.Checkpoint requestCheckpoint(MinecraftServer server, List<SatelliteRegistryProof.Watch> watches) {
        if (ownerServer != server || !server.isSameThread()) {
            throw new IllegalStateException("Registry checkpoint requires its owning server thread");
        }
        return writer.requestCheckpoint(file(server), List.copyOf(watches), CheckedSavedDataPolicy.DirectoryForce.PLATFORM);
    }
    SatelliteRegistryProof.Checkpoint requestCheckpoint(java.nio.file.Path file, List<SatelliteRegistryProof.Watch> watches,
            CheckedSavedDataPolicy.DirectoryForce force) {
        return writer.requestCheckpoint(file, List.copyOf(watches), force);
    }
    /** A removal token at the current sequence (decommission, data actions), covered by a later commit. */
    public SatelliteRegistryProof.Token token() { return writer.token(); }
    public boolean covers(SatelliteRegistryProof.Token token) { return writer.covers(token); }
    public boolean proves(SatelliteRegistryProof.Watch watch) { return writer.proves(watch); }

    SatelliteRegistryProof.Completion checkpoint(java.nio.file.Path file, List<SatelliteRegistryProof.Watch> watches,
            CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force) {
        return writer.checkpoint(file, List.copyOf(watches), policy, force);
    }

    static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
    }

    public GroundSurveys.Result groundSurveys(java.util.function.Function<GroundSurveys, GroundSurveys.Result> operation) {
        requireOperational();
        writer.requireOwnerThread();
        if (!writer.admitsChange()) {
            return new GroundSurveys.Result(GroundSurveys.Code.CAPACITY_REACHED, false, Optional.empty(),
                    SatelliteRegistryWriter.EPOCH_CAPACITY_EXHAUSTED);
        }
        GroundSurveys.Result result = observeClock(() -> operation.apply(registry.groundSurveys()));
        if (result.changed()) { changed(); }
        return result;
    }

    public List<GroundSurveyJob> groundSurveys() {
        return operational() ? registry.groundSurveys().all() : List.of();
    }

    public <T> T groundSurveyQuery(java.util.function.Function<GroundSurveys, T> query) {
        requireOperational();
        return query.apply(registry.groundSurveys());
    }

    /** Whether a state change waits for the coalesced flush (clock-only changes never set it). */
    public boolean flushPending() {
        return flushPending && operational();
    }

    public void applyLimits(io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits limits) {
        if (operational()) {
            registry.applyLimits(limits);
        }
    }

    /** One registry mutation; a changed result waits for the coalesced flush (ADR-050 section 2). */
    private SatelliteOperationResult mutation(java.util.function.Supplier<SatelliteOperationResult> operation) {
        requireOperational();
        writer.requireOwnerThread();
        if (!writer.admitsChange()) {
            return new SatelliteOperationResult(SatelliteOperationCode.CAPACITY_REACHED, false, Optional.empty(), Optional.empty(), 0);
        }
        SatelliteOperationResult result = observeClock(operation);
        if (result.changed()) { changed(); }
        return result;
    }

    public SatelliteOperationResult releaseQuarantine(UUID missionId) {
        return mutation(() -> registry.releaseQuarantine(missionId));
    }

    public SatelliteOperationResult recoverSatellite(UUID satelliteId) {
        return mutation(() -> registry.recoverSatellite(satelliteId));
    }

    /** ADR-051 operations: one registry mutation; a changed record waits for the coalesced flush (ADR-050 §2). */
    public SatelliteOperationResult resources(java.util.function.Function<ResourceMissions, SatelliteOperationResult> operation) {
        return mutation(() -> operation.apply(registry.resources()));
    }

    /** ADR-051 section 7 for one mission; a blocked registry changes nothing and refuses resource actions. */
    public ResourceMissions.Reconciled reconcile(UUID terminal, UUID missionId, DeliveryReconciliation.ReceiptView receipt,
            Optional<MissionPayload.TerminalLocation> display, long observedGameTime) {
        if (!operational()) { return ResourceMissions.Reconciled.blocked(); }
        writer.requireOwnerThread();
        if (!writer.admitsChange()) { return ResourceMissions.Reconciled.blocked(); }
        var reconciled = observeClock(() -> registry.resources().reconcile(terminal, missionId, receipt, display, observedGameTime));
        if (reconciled.changed()) { changed(); }
        return reconciled;
    }

    /** A read of the ADR-051 indexes (bound missions, instances); refused on a blocked registry. */
    public <T> T resourceQuery(java.util.function.Function<ResourceMissions, T> query) {
        requireOperational();
        return query.apply(registry.resources());
    }

    public Optional<AsteroidInstance> instance(UUID instanceId) {
        return operational() ? registry.instance(instanceId) : Optional.empty();
    }

    /** Diagnostics: counts, queue sizes and reserved bytes (ADR-050 sections 5 and 7). */
    public String diagnostics() {
        requireOperational();
        return "satellites=" + registry.satellites().size() + " missions=" + registry.missions().size()
                + " unfinished=" + registry.unfinishedMissionCount() + " finished=" + registry.finishedMissionCount()
                + " scheduled=" + registry.scheduledCount() + " instances=" + registry.instances().size()
                + " bytes_satellites=" + registry.reservedBytes("SATELLITES")
                + " bytes_missions=" + registry.reservedBytes("MISSIONS")
                + " bytes_instances=" + registry.reservedBytes("INSTANCES")
                + " bytes_accounts=" + registry.reservedBytes("ACCOUNTS")
                + " save_epoch=" + registry.saveEpoch() + " stamp_epoch=" + registry.stampEpoch()
                + " reserved_epoch=" + registry.epochs().reserved();
    }

    /** The registry writer's IO-thread figures: writes published, last and maximum wall time, last CPU time. */
    public String writerDiagnostics() { return writer.diagnostics(); }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    /** The capture seam only: the private preserved root, set once at load, shared read-only and never exposed. */
    CompoundTag blockedRoot() { return preservedBlockedData; }

    /** Tests: the capture a write would take now. */
    SatelliteRegistryCapture.Capture captureForTests() { return writer.capture(writer.sequence()); }
    /** Unit tests and benchmarks: a real but unstarted server supplies the tick and both shutdown flags. */
    void bindServerForTests(MinecraftServer server) { ownerServer = server; }
    boolean requestWrite(java.nio.file.Path file, CheckedSavedDataPolicy policy) {
        return writer.request(file, new SatelliteRegistryCapture.Attributes(policy, null));
    }
    boolean writerBusy() { return writer.busy(); }
    boolean writerInFlight() { return writer.inFlight(); }
    Optional<io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter.DrainReport> drainReport() {
        return writer.drainReport();
    }
    SatelliteRegistryWriter writerForTests() { return writer; }

    public SatelliteOperationResult launch(
            UUID satelliteId,
            UUID missionId,
            UUID ownerId,
            SatelliteDefinition definition,
            ResourceLocation targetBodyId,
            long observedGameTime,
            boolean discoveryRequired
    ) {
        requireOperational();
        if (launchFenced(satelliteId)) { return deploymentPendingResult(); }
        return mutation(() -> registry.launch(satelliteId, missionId, ownerId, definition, targetBodyId, observedGameTime,
                discoveryRequired));
    }

    public SatelliteOperationResult launchIdle(
            java.util.function.LongFunction<SatelliteState> factory,
            long observedGameTime
    ) {
        return mutation(() -> registry.launchIdle(factory, observedGameTime, this::launchFenced));
    }

    public SatelliteOperationResult previewLaunch(UUID satellite, UUID mission, UUID owner,
            SatelliteDefinition definition, ResourceLocation target, boolean discoveryRequired) {
        requireOperational();
        if (deploymentPending(satellite)) { return deploymentPendingResult(); }
        return registry.previewLaunch(satellite, mission, owner, definition, target, discoveryRequired);
    }
    public SatelliteOperationResult previewIdle(java.util.function.LongFunction<SatelliteState> factory) {
        requireOperational();
        SatelliteState candidate = factory.apply(registry.logicalGameTime());
        if (deploymentPending(candidate.satelliteId())) { return deploymentPendingResult(); }
        return registry.previewIdle(time -> candidate);
    }

    public SatelliteOperationResult decommission(
            UUID satelliteId,
            UUID requesterId,
            boolean operator,
            boolean receiverMissing
    ) {
        requireOperational();
        if (deploymentFence.test(satelliteId, true)) { return deploymentPendingResult(); }
        return mutation(() -> registry.decommission(satelliteId, requesterId, operator, receiverMissing));
    }

    public SatelliteOperationResult updateKindState(UUID satelliteId, SatelliteKindState next) {
        return mutation(() -> registry.updateKindState(satelliteId, next));
    }

    public int ownerSatellites(UUID ownerId) {
        requireOperational();
        return registry.ownerSatellites(ownerId);
    }

    /** ADR-049 section 8: a paid scan is a registry change; a refused one is not. */
    public SatelliteOperationResult payScan(UUID satelliteId, UUID requesterId, boolean operator, long observedGameTime) {
        return mutation(() -> registry.payScan(satelliteId, requesterId, operator, observedGameTime));
    }

    public List<UUID> linkedTo(UUID receiverId) {
        requireOperational();
        return registry.linkedTo(receiverId);
    }

    public SatelliteOperationResult startMission(
            UUID satelliteId,
            UUID missionId,
            UUID ownerId,
            SatelliteDefinition definition,
            ResourceLocation targetBodyId,
            long observedGameTime,
            boolean discoveryRequired
    ) {
        return mutation(() -> registry.startMission(satelliteId, missionId, ownerId, definition, targetBodyId,
                observedGameTime, discoveryRequired));
    }

    public SchedulerPass completeDue(long observedGameTime) {
        requireOperational();
        writer.requireOwnerThread();
        // ADR-074 item 5: without a representable epoch the pass only observes the clock, which keeps E.
        if (!writer.admitsChange()) { return observeClock(() -> registry.observeOnly(observedGameTime)); }
        SchedulerPass result = observeClock(() -> registry.completeDue(observedGameTime));
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult claim(UUID missionId, UUID ownerId, long observedGameTime) {
        return mutation(() -> registry.claim(missionId, ownerId, observedGameTime));
    }

    public SatelliteOperationResult finishDiscovery(UUID missionId) {
        return mutation(() -> registry.finishDiscovery(missionId));
    }

    public SatelliteOperationResult cancel(
            UUID missionId,
            UUID requesterId,
            boolean operator,
            long observedGameTime
    ) {
        return mutation(() -> registry.cancel(missionId, requesterId, operator, observedGameTime));
    }

    public Optional<SatelliteState> satellite(UUID satelliteId) {
        return operational() ? registry.satellite(satelliteId) : Optional.empty();
    }

    public Optional<MissionState> mission(UUID missionId) {
        return operational() ? registry.mission(missionId) : Optional.empty();
    }

    public ResearchAccount account(UUID ownerId) {
        requireOperational();
        return registry.account(ownerId);
    }

    public List<SatelliteState> satellites() {
        return operational() ? registry.satellites() : List.of();
    }

    public List<MissionState> missions() {
        return operational() ? registry.missions() : List.of();
    }

    public List<MissionState> pendingDiscoveries() {
        return operational() ? registry.pendingDiscoveries() : List.of();
    }

    public long logicalGameTime() {
        requireOperational();
        return registry.logicalGameTime();
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        SatelliteRegistryPayload.encodeCurrent(registry, target);
        target.putLong("save_epoch", writer.epochToWrite());
        return target;
    }

    public long saveEpoch() {
        requireOperational();
        return registry.saveEpoch();
    }

    public List<AsteroidInstance> instances() {
        return operational() ? registry.instances() : List.of();
    }

    private void requireOperational() {
        if (!operational()) {
            throw new IllegalStateException("Satellite registry is blocked by invalid or future data");
        }
    }
}
