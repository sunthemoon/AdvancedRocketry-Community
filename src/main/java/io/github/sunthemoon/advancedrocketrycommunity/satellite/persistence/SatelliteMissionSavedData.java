package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
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

/** Overworld-owned mission authority; malformed or future data is preserved fail-closed. */
public final class SatelliteMissionSavedData extends AtomicSavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_satellite_missions";

    private final SatelliteMissionRegistry registry;
    private CompoundTag preservedBlockedData;
    /** ADR-050 section 2: a state change waits here for the next coalesced flush (at most one per 100 ticks). */
    private boolean flushPending;

    private SatelliteMissionSavedData(SatelliteMissionRegistry registry) {
        super(ManagedSavedDataType.SATELLITE_MISSIONS);
        this.registry = Objects.requireNonNull(registry, "registry");
        io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RestoreReport report = registry.restoreReport();
        if (report.changed()) {
            // ADR-050 section 9: what the load invariants held is persisted with the next coalesced flush.
            setDirty();
            flushPending = true;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_SATELLITE_RESTORE quarantined_missions={} recovery_required={} quarantined_instances={} "
                            + "accounts_added={}", report.quarantinedMissions(), report.recoveries(),
                    report.quarantinedInstances(), report.accountsAdded());
        }
    }

    public static SatelliteMissionSavedData create(long observedGameTime) {
        return new SatelliteMissionSavedData(SatelliteMissionRegistry.create(observedGameTime, CodecRecordSizer.INSTANCE));
    }

    public static SatelliteMissionSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                SatelliteMissionSavedData::load,
                () -> create(server.overworld().getGameTime()),
                DATA_NAME
        );
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
        return data;
    }

    private void changed() {
        setDirty();
        registry.markChanged();
        flushPending = true;
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
        SatelliteOperationResult result = registry.launchIdle(factory, observedGameTime);
        if (result.changed()) {
            changed();
        }
        return result;
    }

    public SatelliteOperationResult decommission(
            UUID satelliteId,
            UUID requesterId,
            boolean operator,
            boolean receiverMissing
    ) {
        requireOperational();
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
        return SatelliteRegistryPayload.encodeCurrent(registry, target);
    }

    @Override
    protected void onPersisted() {
        if (preservedBlockedData == null) {
            registry.markPersisted();
            flushPending = false;
        }
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
