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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
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
    private boolean updatesQuarantined;

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
        int balance = registry.warpEnergy(stationId);
        Optional<StationState> result = registry.delete(stationId);
        if (result.isPresent()) {
            setDirty();
            if (balance > 0) {
                AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_STATION_WARP_ENERGY_DROPPED station={} energy={}", stationId, balance);
            }
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

    /** Stored warp energy of a station (ADR-044); 0 when absent or while the registry is blocked. */
    public int warpEnergy(UUID stationId) {
        return operational() ? registry.warpEnergy(stationId) : 0;
    }

    public SortedMap<UUID, Integer> warpEnergyBalances() {
        return operational() ? registry.warpEnergyBalances() : new TreeMap<>();
    }

    /**
     * Folds pending warp credits into the balances as one ordinary mutation (dirty, never a flush).
     * Credits for missing stations, beyond the per-station cap, or that would add an entry while the
     * encoded registry is within the headroom of its bound are refused and reported.
     */
    public WarpCreditFold foldWarpCredits(Map<UUID, Integer> credits) {
        Objects.requireNonNull(credits, "credits");
        long offered = credits.values().stream().mapToLong(Integer::longValue).sum();
        if (!operational()) {
            return new WarpCreditFold(0L, offered);
        }
        long credited = 0L;
        Boolean entryHeadroom = null;
        for (Map.Entry<UUID, Integer> credit : new TreeMap<>(credits).entrySet()) {
            if (registry.needsWarpEnergyEntry(credit.getKey())) {
                if (entryHeadroom == null) {
                    entryHeadroom = hasWarpEntryHeadroom();
                }
                if (!entryHeadroom) {
                    continue;
                }
            }
            credited += registry.creditWarpEnergy(credit.getKey(), credit.getValue());
        }
        if (credited > 0L) {
            setDirty();
        }
        if (credited < offered) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_WARP_CREDIT_REFUSED offered={} credited={} entry_headroom={}",
                    offered, credited, entryHeadroom);
        }
        return new WarpCreditFold(credited, offered - credited);
    }

    /** True while the encoded registry leaves the ADR-044 headroom; encodes only when the cheap bound fails. */
    private boolean hasWarpEntryHeadroom() {
        long limit = (long) StationLimits.MAX_REGISTRY_NBT_BYTES - StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES;
        long upperBound = 1_024L
                + (long) (registry.stations().size() + registry.reservations().size())
                * StationLimits.MAX_STATION_RECORD_NBT_BYTES
                + (long) registry.warpEnergyBalances().size() * StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES;
        if (upperBound <= limit) {
            return true;
        }
        try {
            return StationNbtSize.uncompressedBytes(encode(new CompoundTag(), null, null)) <= limit;
        } catch (IllegalStateException oversized) {
            return false;
        }
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

    /** True after a checked update could not determine whether its replacement happened. */
    public boolean updatesQuarantined() {
        return updatesQuarantined;
    }

    /** Grows the observed station to its centered 768 region through a checked commit. */
    public CheckedUpdate checkedExpand(MinecraftServer server, StationState observed) {
        return checkedExpand(stationFile(server), observed, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedExpand(Path file, StationState observed, CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return checkedReplace(file, observed, observed.withExpandedRegion(), committer);
    }

    /** Sets the observed station's configured gravity through a checked commit. */
    public CheckedUpdate checkedSetGravity(MinecraftServer server, StationState observed, int gravityMilli) {
        return checkedSetGravity(stationFile(server), observed, gravityMilli, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetGravity(Path file, StationState observed, int gravityMilli,
                                    CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return checkedReplace(file, observed, observed.withGravityMilli(gravityMilli), committer);
    }

    /**
     * ADR-044 warp: moves the observed station's orbit to {@code target} and debits {@code cost} from
     * its balance in one checked replacement of the station file, then one publish.
     */
    public CheckedUpdate checkedRelocation(MinecraftServer server, StationState observed, ResourceLocation target,
                                           int cost) {
        return checkedRelocation(stationFile(server), observed, target, cost, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedRelocation(Path file, StationState observed, ResourceLocation target, int cost,
                                    CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(target, "target");
        if (cost <= 0 || cost > StationLimits.MAX_WARP_ENERGY) {
            throw new IllegalArgumentException("Warp cost is outside its bound");
        }
        return checked(file, observed, observed.withOrbitBody(target), cost, committer);
    }

    private static Path stationFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.STATIONS.fileName());
    }

    /** Package-private for tests; production callers go through the named transitions above. */
    CheckedUpdate checkedReplace(Path file, StationState observed, StationState replacement,
                                         CheckedSavedDataFile.Committer committer) {
        return checked(file, observed, replacement, 0, committer);
    }

    /**
     * Replaces the station file with the complete candidate registry, then publishes the update.
     * The live registry is not changed unless the candidate is known to be the replaced authority.
     * A positive {@code cost} makes this an orbit relocation charged to the station's balance;
     * otherwise it is growth or a gravity-only change, and every balance must stay as it is.
     */
    private CheckedUpdate checked(Path file, StationState observed, StationState replacement, int cost,
                                  CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(committer, "committer");
        if (!operational() || updatesQuarantined) {
            return CheckedUpdate.UNAVAILABLE;
        }
        if (!registry.find(observed.stationId()).filter(observed::equals).isPresent()) {
            return CheckedUpdate.STALE;
        }
        if (replacement.equals(observed)) {
            return CheckedUpdate.UNCHANGED;
        }
        boolean relocation = cost > 0;
        // Rejected before any write, so a disallowed transition can never reach disk.
        if (relocation && !replacement.isOrbitRelocationOf(observed)) {
            throw new IllegalArgumentException("A charged update must be exactly one orbit relocation");
        }
        if (!relocation && !replacement.isCheckedUpdateOf(observed)) {
            throw new IllegalArgumentException("Only station growth or a gravity-only change is a checked update");
        }
        int balance = registry.warpEnergy(observed.stationId());
        if (relocation && balance < cost) {
            return CheckedUpdate.INSUFFICIENT_ENERGY;
        }
        Integer debited = relocation ? balance - cost : null;
        CompoundTag candidate = encode(new CompoundTag(), replacement, debited);
        requireValidCandidate(candidate, replacement, debited);
        boolean[] replacementAttempted = {false};
        try {
            CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, candidate::copy, (staged, target) -> {
                replacementAttempted[0] = true;
                committer.commit(staged, target);
            });
        } catch (RuntimeException failure) {
            if (!replacementAttempted[0]) {
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_STATION_UPDATE_WRITE_FAILED station={} stage=before_replace",
                        observed.stationId(), failure);
                return CheckedUpdate.WRITE_FAILED;
            }
            CheckedUpdate resolved = resolveReplacement(file, candidate, observed, failure);
            if (resolved != CheckedUpdate.COMMITTED) {
                return resolved;
            }
        }
        try {
            if (relocation) {
                registry.relocateChecked(observed, replacement, balance, cost);
            } else {
                registry.replaceChecked(observed, replacement);
            }
        } catch (RuntimeException publishFailure) {
            // Unreachable on the owning server thread; never report a written update as a clean failure.
            updatesQuarantined = true;
            setDirty();
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_UPDATE_OUTCOME_UNKNOWN station={} stage=publish; updates disabled until restart",
                    observed.stationId(), publishFailure);
            return CheckedUpdate.OUTCOME_UNKNOWN;
        }
        return CheckedUpdate.COMMITTED;
    }

    private CheckedUpdate resolveReplacement(Path file, CompoundTag candidate, StationState observed,
                                             RuntimeException failure) {
        Optional<CompoundTag> onDisk;
        try {
            onDisk = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.STATIONS);
        } catch (RuntimeException unreadable) {
            failure.addSuppressed(unreadable);
            updatesQuarantined = true;
            // The acknowledged authority excludes the update; let ordinary saves reassert it.
            setDirty();
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_UPDATE_OUTCOME_UNKNOWN station={} file={}; updates disabled until restart",
                    observed.stationId(), file, failure);
            return CheckedUpdate.OUTCOME_UNKNOWN;
        }
        if (onDisk.filter(candidate::equals).isPresent()) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_UPDATE_REPLACED_DESPITE_ERROR station={}", observed.stationId(), failure);
            return CheckedUpdate.COMMITTED;
        }
        // Not the candidate; ordinary saves rewrite the acknowledged authority in case the file is stale.
        setDirty();
        AdvancedRocketryCommunity.LOGGER.error(
                "ARCE_STATION_UPDATE_WRITE_FAILED station={} stage=replace file_present={}",
                observed.stationId(), onDisk.isPresent(), failure);
        return CheckedUpdate.WRITE_FAILED;
    }

    /**
     * The candidate must decode to the live registry with exactly the replacement state and, for a
     * relocation, exactly the debited balance substituted: every other state, reservation and balance
     * is unchanged.
     */
    private void requireValidCandidate(CompoundTag candidate, StationState replacement, Integer debited) {
        UUID stationId = replacement.stationId();
        List<StationState> expectedStations = registry.stations().stream()
                .map(state -> state.stationId().equals(stationId) ? replacement : state)
                .toList();
        SortedMap<UUID, Integer> expectedBalances = registry.warpEnergyBalances();
        if (debited != null && debited > 0) {
            expectedBalances.put(stationId, debited);
        } else if (debited != null) {
            expectedBalances.remove(stationId);
        }
        SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                ManagedSavedDataType.STATIONS, candidate.copy());
        if (migration.status() != SavedDataSchemaMigrator.MigrationStatus.CURRENT) {
            throw new IllegalStateException("Candidate station registry failed validation");
        }
        StationRegistryModel decoded = StationRegistryPayload.decodeCurrent(migration.payload());
        if (!decoded.stations().equals(expectedStations)
                || !decoded.reservations().equals(registry.reservations())
                || !decoded.warpEnergyBalances().equals(expectedBalances)) {
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
        return encode(target, null, null);
    }

    /**
     * Encodes the registry, optionally substituting one station by UUID and, for a relocation, that
     * station's balance, without mutating the live registry.
     */
    private CompoundTag encode(CompoundTag target, StationState substitute, Integer substituteBalance) {
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
        SortedMap<UUID, Integer> balances = registry.warpEnergyBalances();
        if (substitute != null && substituteBalance != null) {
            balances.remove(substitute.stationId());
            if (substituteBalance > 0) {
                balances.put(substitute.stationId(), substituteBalance);
            }
        }
        ListTag warpEnergy = new ListTag();
        balances.forEach((stationId, energy) -> warpEnergy.add(StationNbtCodec.encodeWarpEnergy(stationId, energy)));
        target.put(StationRegistryPayload.WARP_ENERGY, warpEnergy);
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

    public enum CheckedUpdate {
        COMMITTED,
        UNCHANGED,
        STALE,
        UNAVAILABLE,
        WRITE_FAILED,
        OUTCOME_UNKNOWN,
        /** Relocation only: the live balance does not cover the cost; nothing was written. */
        INSUFFICIENT_ENERGY
    }

    /** Result of one fold: energy added to balances, and energy refused (lost from the pending credits). */
    public record WarpCreditFold(long credited, long refused) {
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
