package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Overworld-owned station registry; invalid/future data is preserved fail-closed. */
public final class StationRegistrySavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_stations";

    private final StationRegistryModel registry;
    private CompoundTag preservedBlockedData;
    private boolean expansionQuarantined;

    public StationRegistrySavedData() {
        this(new StationRegistryModel());
    }

    private StationRegistrySavedData(StationRegistryModel registry) {
        this.registry = registry;
    }

    public static StationRegistrySavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                StationRegistrySavedData::load,
                StationRegistrySavedData::new,
                DATA_NAME
        );
    }

    public static StationRegistrySavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        CompoundTag preserved = source.copy();
        try {
            if (StationNbtSize.uncompressedBytes(source) > StationLimits.MAX_REGISTRY_NBT_BYTES) {
                throw new IllegalArgumentException("Station registry exceeds the fixed NBT bound");
            }
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.STATIONS,
                    source
            );
            if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
                throw new IllegalArgumentException("Station registry requires supported pre-start migration");
            }
            return new StationRegistrySavedData(StationRegistryPayload.decodeCurrent(migration.payload()));
        } catch (RuntimeException exception) {
            StationRegistrySavedData data = new StationRegistrySavedData();
            data.preservedBlockedData = preserved;
            return data;
        }
    }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    public StationReservation reserve(
            UUID stationId,
            UUID ownerId,
            String name,
            ResourceLocation orbitBody,
            long createdAtGameTime
    ) {
        requireOperational();
        StationReservation result = registry.reserve(
                stationId, ownerId, name, orbitBody, createdAtGameTime
        );
        setDirty();
        return result;
    }

    public StationState commit(UUID stationId) {
        requireOperational();
        StationState result = registry.commit(stationId);
        setDirty();
        return result;
    }

    public boolean release(UUID stationId) {
        requireOperational();
        boolean changed = registry.release(stationId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Optional<StationState> delete(UUID stationId) {
        requireOperational();
        Optional<StationState> result = registry.delete(stationId);
        if (result.isPresent()) {
            setDirty();
        }
        return result;
    }

    public StationState addMember(UUID stationId, UUID memberId) {
        requireOperational();
        StationState result = registry.addMember(stationId, memberId);
        setDirty();
        return result;
    }

    public StationState removeMember(UUID stationId, UUID memberId) {
        requireOperational();
        StationState result = registry.removeMember(stationId, memberId);
        setDirty();
        return result;
    }

    public StationState invite(UUID stationId, UUID playerId) {
        requireOperational();
        StationState result = registry.invite(stationId, playerId);
        setDirty();
        return result;
    }

    public StationState acceptInvitation(UUID stationId, UUID playerId) {
        requireOperational();
        StationState result = registry.acceptInvitation(stationId, playerId);
        setDirty();
        return result;
    }

    public StationState declineInvitation(UUID stationId, UUID playerId) {
        requireOperational();
        StationState result = registry.declineInvitation(stationId, playerId);
        setDirty();
        return result;
    }

    public StationState transferOwnership(UUID stationId, UUID ownerId) {
        requireOperational();
        StationState result = registry.transferOwnership(stationId, ownerId);
        setDirty();
        return result;
    }

    public Optional<StationState> find(UUID stationId) {
        return operational() ? registry.find(stationId) : Optional.empty();
    }

    public Optional<StationState> findAt(int x, int z) {
        return operational() ? registry.findAt(x, z) : Optional.empty();
    }

    public List<StationState> stations() {
        return operational() ? registry.stations() : List.of();
    }

    public List<StationReservation> reservations() {
        return operational() ? registry.reservations() : List.of();
    }

    public long ownedBy(UUID ownerId) {
        return operational() ? registry.ownedBy(ownerId) : Long.MAX_VALUE;
    }

    /** Validates persisted orbit references without deleting recoverable station state. */
    public OrbitBodyValidation validateOrbitBodies(Predicate<ResourceLocation> bodyExists) {
        Objects.requireNonNull(bodyExists, "bodyExists");
        if (!operational()) {
            return new OrbitBodyValidation(false, List.of(), List.of());
        }
        List<UUID> invalidStations = registry.stations().stream()
                .filter(station -> !bodyExists.test(station.orbitBody()))
                .map(StationState::stationId)
                .toList();
        List<UUID> invalidReservations = registry.reservations().stream()
                .filter(reservation -> !bodyExists.test(reservation.orbitBody()))
                .map(StationReservation::stationId)
                .toList();
        return new OrbitBodyValidation(true, invalidStations, invalidReservations);
    }

    /** True after a checked expansion could not determine whether its replacement happened. */
    public boolean expansionQuarantined() {
        return expansionQuarantined;
    }

    /**
     * Replaces the station file with the complete candidate registry, then publishes the expansion.
     * The live registry is not changed unless the candidate is known to be the replaced authority.
     */
    public CheckedExpansion checkedExpand(MinecraftServer server, StationState observed) {
        Objects.requireNonNull(server, "server");
        return checkedExpand(server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.STATIONS.fileName()), observed, CheckedSavedDataFile::atomicMove);
    }

    CheckedExpansion checkedExpand(Path file, StationState observed, CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(committer, "committer");
        if (!operational() || expansionQuarantined) {
            return CheckedExpansion.UNAVAILABLE;
        }
        if (!registry.find(observed.stationId()).filter(observed::equals).isPresent()) {
            return CheckedExpansion.STALE;
        }
        if (observed.expanded()) {
            return CheckedExpansion.ALREADY_EXPANDED;
        }
        StationState expanded = observed.withExpandedRegion();
        CompoundTag candidate = encode(new CompoundTag(), expanded);
        requireValidCandidate(candidate, expanded);
        boolean[] replacementAttempted = {false};
        try {
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, candidate::copy, (staged, target) -> {
                replacementAttempted[0] = true;
                committer.commit(staged, target);
            });
        } catch (RuntimeException failure) {
            if (!replacementAttempted[0]) {
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_STATION_EXPANSION_WRITE_FAILED station={} stage=before_replace",
                        observed.stationId(), failure);
                return CheckedExpansion.WRITE_FAILED;
            }
            CheckedExpansion resolved = resolveReplacement(file, candidate, observed, failure);
            if (resolved != CheckedExpansion.EXPANDED) {
                return resolved;
            }
        }
        try {
            registry.replaceExpanded(observed, expanded);
        } catch (RuntimeException publishFailure) {
            // Unreachable on the owning server thread; never report a written growth as a clean failure.
            expansionQuarantined = true;
            setDirty();
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_EXPANSION_OUTCOME_UNKNOWN station={} stage=publish; expansion disabled until restart",
                    observed.stationId(), publishFailure);
            return CheckedExpansion.OUTCOME_UNKNOWN;
        }
        return CheckedExpansion.EXPANDED;
    }

    private CheckedExpansion resolveReplacement(Path file, CompoundTag candidate, StationState observed,
                                                RuntimeException failure) {
        Optional<CompoundTag> onDisk;
        try {
            onDisk = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.STATIONS);
        } catch (RuntimeException unreadable) {
            failure.addSuppressed(unreadable);
            expansionQuarantined = true;
            // The acknowledged authority excludes the expansion; let ordinary saves reassert it.
            setDirty();
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_EXPANSION_OUTCOME_UNKNOWN station={} file={}; expansion disabled until restart",
                    observed.stationId(), file, failure);
            return CheckedExpansion.OUTCOME_UNKNOWN;
        }
        if (onDisk.filter(candidate::equals).isPresent()) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_EXPANSION_REPLACED_DESPITE_ERROR station={}", observed.stationId(), failure);
            return CheckedExpansion.EXPANDED;
        }
        // Not the candidate; ordinary saves rewrite the acknowledged authority in case the file is stale.
        setDirty();
        AdvancedRocketryCommunity.LOGGER.error(
                "ARCE_STATION_EXPANSION_WRITE_FAILED station={} stage=replace file_present={}",
                observed.stationId(), onDisk.isPresent(), failure);
        return CheckedExpansion.WRITE_FAILED;
    }

    private static void requireValidCandidate(CompoundTag candidate, StationState expanded) {
        SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                ManagedSavedDataType.STATIONS, candidate.copy());
        if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT
                || !StationRegistryPayload.decodeCurrent(migration.payload()).find(expanded.stationId())
                .filter(expanded::equals).isPresent()) {
            throw new IllegalStateException("Candidate station registry failed validation");
        }
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
        return encode(target, null);
    }

    /** Encodes the registry, optionally substituting one station by UUID without mutating it. */
    private CompoundTag encode(CompoundTag target, StationState substitute) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.STATIONS, target);
        ListTag stations = new ListTag();
        registry.stations().forEach(state -> stations.add(StationNbtCodec.encodeState(
                substitute != null && substitute.stationId().equals(state.stationId()) ? substitute : state)));
        target.put("stations", stations);
        ListTag reservations = new ListTag();
        registry.reservations().forEach(
                reservation -> reservations.add(StationNbtCodec.encodeReservation(reservation))
        );
        target.put("reservations", reservations);
        if (StationNbtSize.uncompressedBytes(target) > StationLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalStateException("Encoded station registry exceeds the fixed NBT bound");
        }
        return target;
    }

    private void requireOperational() {
        if (!operational()) {
            throw new IllegalStateException("Station registry is blocked by invalid or future data");
        }
    }

    public enum CheckedExpansion {
        EXPANDED,
        ALREADY_EXPANDED,
        STALE,
        UNAVAILABLE,
        WRITE_FAILED,
        OUTCOME_UNKNOWN
    }

    public record OrbitBodyValidation(
            boolean registryOperational,
            List<UUID> invalidStationIds,
            List<UUID> invalidReservationIds
    ) {
        public OrbitBodyValidation {
            invalidStationIds = List.copyOf(invalidStationIds);
            invalidReservationIds = List.copyOf(invalidReservationIds);
        }

        public boolean valid() {
            return registryOperational && invalidStationIds.isEmpty() && invalidReservationIds.isEmpty();
        }
    }
}
