package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-owned mission authority; malformed or future data is preserved fail-closed. */
public final class SatelliteMissionSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_satellite_missions";

    private final SatelliteMissionRegistry registry;
    private CompoundTag preservedBlockedData;

    private SatelliteMissionSavedData(SatelliteMissionRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static SatelliteMissionSavedData create(long observedGameTime) {
        return new SatelliteMissionSavedData(SatelliteMissionRegistry.create(observedGameTime));
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
            CompoundTag payload = migration.payload();
            CompoundTag clock = requireCompound(payload, "clock");
            SatelliteMissionRegistry restored = SatelliteMissionRegistry.restore(
                    requireNonNegativeLong(clock, "logical_game_time"),
                    requireNonNegativeLong(clock, "last_observed_game_time")
            );
            ListTag satellites = requireList(payload, "satellites");
            ListTag missions = requireList(payload, "missions");
            ListTag accounts = requireList(payload, "research_accounts");
            if (satellites.size() > SatelliteLimits.MAX_SATELLITES
                    || missions.size() > SatelliteLimits.MAX_MISSIONS
                    || accounts.size() > SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
                throw new IllegalArgumentException("Satellite registry lists exceed fixed bounds");
            }
            for (Tag raw : satellites) {
                restored.restoreSatellite(SatelliteNbtCodec.decodeSatellite((CompoundTag) raw));
            }
            for (Tag raw : missions) {
                restored.restoreMission(SatelliteNbtCodec.decodeMission((CompoundTag) raw));
            }
            for (Tag raw : accounts) {
                restored.restoreAccount(SatelliteNbtCodec.decodeAccount((CompoundTag) raw));
            }
            restored.finishRestore();
            data = new SatelliteMissionSavedData(restored);
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.preservedBlockedData = preserved;
        }
        return data;
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
            setDirty();
        }
        return result;
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
            setDirty();
        }
        return result;
    }

    public SatelliteMissionRegistry.SchedulerPass completeDue(long observedGameTime) {
        requireOperational();
        SatelliteMissionRegistry.SchedulerPass result = registry.completeDue(observedGameTime);
        if (result.clockAdvanced() || result.completed() > 0) {
            setDirty();
        }
        return result;
    }

    public SatelliteOperationResult claim(UUID missionId, UUID ownerId, long observedGameTime) {
        requireOperational();
        SatelliteOperationResult result = registry.claim(missionId, ownerId, observedGameTime);
        if (result.changed()) {
            setDirty();
        }
        return result;
    }

    public SatelliteOperationResult finishDiscovery(UUID missionId) {
        requireOperational();
        SatelliteOperationResult result = registry.finishDiscovery(missionId);
        if (result.changed()) {
            setDirty();
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
            setDirty();
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

    public void flush(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (isDirty()) {
            server.overworld().getDataStorage().save();
        }
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.SATELLITE_MISSIONS, target);
        CompoundTag clock = new CompoundTag();
        clock.putLong("logical_game_time", registry.logicalGameTime());
        clock.putLong("last_observed_game_time", registry.lastObservedGameTime());
        target.put("clock", clock);
        ListTag satellites = new ListTag();
        registry.satellites().forEach(state -> satellites.add(SatelliteNbtCodec.encodeSatellite(state)));
        target.put("satellites", satellites);
        ListTag missions = new ListTag();
        registry.missions().forEach(state -> missions.add(SatelliteNbtCodec.encodeMission(state)));
        target.put("missions", missions);
        ListTag accounts = new ListTag();
        registry.accounts().forEach(account -> accounts.add(SatelliteNbtCodec.encodeAccount(account)));
        target.put("research_accounts", accounts);
        if (SatelliteNbtSize.uncompressedBytes(target) > SatelliteLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalStateException("Encoded satellite registry exceeds its fixed NBT bound");
        }
        return target;
    }

    private void requireOperational() {
        if (!operational()) {
            throw new IllegalStateException("Satellite registry is blocked by invalid or future data");
        }
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing satellite registry long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Satellite registry long " + key + " cannot be negative");
        }
        return value;
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing satellite registry compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing or invalid satellite registry list " + key);
        }
        return list;
    }
}
