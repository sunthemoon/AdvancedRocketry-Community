package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
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
    private CompoundTag preservedBlockedData;
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
        if (data.ownerServer != server) {
            if (data.ownerServer != null) { throw new IllegalStateException("Registry belongs to another server"); }
            data.ownerServer = server;
            data.deploymentFence = (satellite, removal) ->
                    io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts.blocksRegistry(server, satellite, removal);
        }
        return data;
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
        }
        data.writer.loaded();
        return data;
    }

    private void changed() {
        setDirty();
        registry.markChanged();
        flushPending = true;
    }

    @Override
    public void setDirty(boolean dirty) {
        if (dirty) { writer.mutated(); }
        super.setDirty(dirty);
    }

    void pendingWrite() { super.setDirty(true); }
    void persisted() { super.setDirty(false); flushPending = false; }
    public long mutationSequence() { return writer.sequence(); }
    public SatelliteRegistryProof.Watch watch(UUID satellite) { requireOperational(); return writer.watch(satellite); }
    public Optional<SatelliteRegistryProof.Acknowledgement> acknowledgement() { return writer.acknowledgement(); }
    void writePolicy(CheckedSavedDataPolicy policy) { writer.policy(policy); }
    void deploymentFence(java.util.function.BiPredicate<UUID, Boolean> fence) { deploymentFence = Objects.requireNonNull(fence); }
    public boolean deploymentPending(UUID satellite) { return deploymentFence.test(satellite, false); }
    private static SatelliteOperationResult deploymentPendingResult() {
        return new SatelliteOperationResult(
                io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.DEPLOYMENT_PENDING,
                false, Optional.empty(), Optional.empty(), 0);
    }

    public void flush(MinecraftServer server) { flush(file(server)); }
    public void flush(java.nio.file.Path file) { writer.flush(file); }
    @Override public void save(java.io.File file) { writer.save(file); }

    public SatelliteRegistryProof.Completion checkpoint(MinecraftServer server, List<SatelliteRegistryProof.Watch> watches) {
        if (!server.isSameThread()) { throw new IllegalStateException("Registry checkpoint requires the server thread"); }
        return checkpoint(file(server), watches, writer.policy(), directory -> { });
    }

    SatelliteRegistryProof.Completion checkpoint(java.nio.file.Path file, List<SatelliteRegistryProof.Watch> watches,
            CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force) {
        return writer.checkpoint(file, List.copyOf(watches), policy, force);
    }

    private static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
    }

    public GroundSurveys.Result groundSurveys(java.util.function.Function<GroundSurveys, GroundSurveys.Result> operation) {
        requireOperational();
        GroundSurveys.Result result = operation.apply(registry.groundSurveys());
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

    public SatelliteOperationResult releaseQuarantine(UUID missionId) {
        requireOperational();
        SatelliteOperationResult result = registry.releaseQuarantine(missionId);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult recoverSatellite(UUID satelliteId) {
        requireOperational();
        SatelliteOperationResult result = registry.recoverSatellite(satelliteId);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    /** ADR-051 operations: one registry mutation; a changed record waits for the coalesced flush (ADR-050 §2). */
    public SatelliteOperationResult resources(
            java.util.function.Function<io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions,
                    SatelliteOperationResult> operation) {
        requireOperational();
        SatelliteOperationResult result = operation.apply(registry.resources());
        if (result.changed()) {
            changed();
        }
        return result;
    }

    /** ADR-051 section 7 for one mission; a blocked registry changes nothing and refuses resource actions. */
    public io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions.Reconciled reconcile(
            UUID terminal, UUID missionId,
            io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation.ReceiptView receipt,
            Optional<io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload.TerminalLocation> display,
            long observedGameTime) {
        if (!operational()) {
            return io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions.Reconciled.blocked();
        }
        var reconciled = registry.resources().reconcile(terminal, missionId, receipt, display, observedGameTime);
        if (reconciled.changed()) {
            changed();
        }
        return reconciled;
    }

    /** A read of the ADR-051 indexes (bound missions, instances); refused on a blocked registry. */
    public <T> T resourceQuery(java.util.function.Function<
            io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions, T> query) {
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
                + " save_epoch=" + registry.saveEpoch();
    }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

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
        if (deploymentPending(satelliteId)) { return deploymentPendingResult(); }
        SatelliteOperationResult result = registry.launch(
                satelliteId,
                missionId,
                ownerId,
                definition,
                targetBodyId,
                observedGameTime,
                discoveryRequired
        );
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult launchIdle(
            java.util.function.LongFunction<SatelliteState> factory,
            long observedGameTime
    ) {
        requireOperational();
        SatelliteOperationResult result = registry.launchIdle(factory, observedGameTime, this::deploymentPending);
        if (result.changed()) {
            changed();
        }
        return result;
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
        SatelliteOperationResult result = registry.decommission(satelliteId, requesterId, operator, receiverMissing);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult updateKindState(UUID satelliteId, SatelliteKindState next) {
        requireOperational();
        SatelliteOperationResult result = registry.updateKindState(satelliteId, next);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public int ownerSatellites(UUID ownerId) {
        requireOperational();
        return registry.ownerSatellites(ownerId);
    }

    /** ADR-049 section 8: a paid scan is a registry change; a refused one is not. */
    public SatelliteOperationResult payScan(UUID satelliteId, UUID requesterId, boolean operator, long observedGameTime) {
        requireOperational();
        SatelliteOperationResult result = registry.payScan(satelliteId, requesterId, operator, observedGameTime);
        if (result.changed()) {
            changed();
        }
        return result;
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
        requireOperational();
        SatelliteOperationResult result = registry.startMission(
                satelliteId,
                missionId,
                ownerId,
                definition,
                targetBodyId,
                observedGameTime,
                discoveryRequired
        );
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SchedulerPass completeDue(long observedGameTime) {
        requireOperational();
        io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SchedulerPass result = registry.completeDue(observedGameTime);
        if (result.changed()) {
            changed();
        } else if (result.clockAdvanced()) {
            // ADR-050 section 2: clock-only changes set the ordinary dirty flag, never "flush pending".
            setDirty();
        }
        return result;
    }

    public SatelliteOperationResult claim(UUID missionId, UUID ownerId, long observedGameTime) {
        requireOperational();
        SatelliteOperationResult result = registry.claim(missionId, ownerId, observedGameTime);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult finishDiscovery(UUID missionId) {
        requireOperational();
        SatelliteOperationResult result = registry.finishDiscovery(missionId);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult cancel(
            UUID missionId,
            UUID requesterId,
            boolean operator,
            long observedGameTime
    ) {
        requireOperational();
        SatelliteOperationResult result = registry.cancel(
                missionId, requesterId, operator, observedGameTime
        );
        if (result.changed()) {
            changed();
        }
        return result;
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
