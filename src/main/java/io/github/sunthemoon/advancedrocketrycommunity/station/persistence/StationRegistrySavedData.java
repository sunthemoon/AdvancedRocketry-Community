package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalClock;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
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
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Overworld-owned station registry; invalid/future data is preserved fail-closed. Confined to the
 * server thread: encoding reads stations, reservations and balances in separate calls, which is
 * consistent only because every mutation and publish runs on that thread.
 */
public final class StationRegistrySavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_stations";

    private final StationRegistryModel registry;
    private CompoundTag preservedBlockedData;
    private boolean updatesQuarantined;
    /** Test seam only: changes the encoded candidate before it is verified (review F5). */
    UnaryOperator<CompoundTag> candidateTransform = UnaryOperator.identity();
    private final StationStorageBudget budget = new StationStorageBudget();
    /** ADR-065 section 5.2.1: the runtime orbital clock, starting at the saved logical tick. */
    private final StationOrbitalClock clock;
    /**
     * The last checked-commit clock floor: the loaded root's clock, raised by each known checked commit.
     * Every live epoch is at or before it, and a quarantined ordinary save reasserts it.
     */
    private long clockFloor;

    public StationRegistrySavedData() {
        this(new StationRegistryModel(), 0L);
    }

    private StationRegistrySavedData(StationRegistryModel registry, long logicalTick) {
        this.registry = registry;
        this.clock = new StationOrbitalClock(logicalTick);
        this.clockFloor = logicalTick;
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
            StationRegistryPayload.Decoded decoded = StationRegistryPayload.decodeCurrentRoot(migration.payload());
            return new StationRegistrySavedData(decoded.registry(), decoded.logicalTick());
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
        requireStorageRoom(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        StationReservation result = registry.reserve(
                stationId, ownerId, name, orbitBody, createdAtGameTime
        );
        budget.grew(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState commit(UUID stationId) {
        requireOperational();
        requireStorageRoom(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        StationState result = registry.commit(stationId);
        budget.grew(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        setDirty();
        return result;
    }

    /** Operational and not quarantined: checked updates are possible (ADR-041/044/045 rule 1). */
    public boolean updatesAvailable() {
        return operational() && !updatesQuarantined;
    }

    /**
     * ADR-044 §2: warp cores are credited only while the registry is operational and not quarantined
     * (a quarantined registry refuses every checked update, so it could never spend the energy).
     */
    public boolean acceptsWarpEnergy() {
        return updatesAvailable();
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
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.addMember(stationId, memberId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
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
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.invite(stationId, playerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState acceptInvitation(UUID stationId, UUID playerId) {
        requireOperational();
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.acceptInvitation(stationId, playerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
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
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.transferOwnership(stationId, ownerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
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

    /** ADR-066 section 3.3: the access index generation; empty while the registry is blocked. */
    public java.util.OptionalLong accessGeneration() {
        return operational() ? java.util.OptionalLong.of(registry.accessGeneration()) : java.util.OptionalLong.empty();
    }

    /**
     * The next station after {@code after} (the first when null) that a player owns or belongs to, or any committed
     * station for an operator, in UUID order. Empty at the end and while the registry is blocked, so a caller checks
     * {@link #operational()} first: a blocked registry is not an empty candidate set.
     */
    public Optional<UUID> nextAccessCandidate(UUID playerId, boolean operator, UUID after) {
        return operational() ? registry.nextAccessCandidate(playerId, operator, after) : Optional.empty();
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
     * Credits for missing stations or beyond the per-station cap are refused and reported. A new
     * balance entry uses the reserved headroom, so it is refused only if it would cross the bound
     * itself, which a registry grown under growth admission cannot reach (WARP review R4).
     */
    public WarpCreditFold foldWarpCredits(Map<UUID, Integer> credits) {
        Objects.requireNonNull(credits, "credits");
        // Validated as a whole first, so an invalid entry can never leave a partial, unsaved fold.
        if (credits.size() > StationLimits.MAX_PENDING_WARP_CREDITS) {
            throw new IllegalArgumentException("Too many warp credits in one fold");
        }
        credits.forEach((stationId, amount) -> {
            if (stationId == null || amount == null || amount < 0 || amount > StationLimits.MAX_WARP_ENERGY) {
                throw new IllegalArgumentException("Invalid warp credit");
            }
        });
        long offered = credits.values().stream().mapToLong(Integer::longValue).sum();
        if (!operational()) {
            return new WarpCreditFold(0L, offered);
        }
        long credited = 0L;
        // After one refusal the rest of this fold is refused too, so a full registry is encoded once.
        boolean entryRoom = true;
        for (Map.Entry<UUID, Integer> credit : new TreeMap<>(credits).entrySet()) {
            boolean newEntry = registry.needsWarpEnergyEntry(credit.getKey());
            if (newEntry) {
                entryRoom = entryRoom && budget.admitsBalanceEntry(recordCount(),
                        registry.warpEnergyBalances().size(), this::encodedBytes);
                if (!entryRoom) {
                    continue;
                }
            }
            int accepted = registry.creditWarpEnergy(credit.getKey(), credit.getValue());
            credited += accepted;
            if (newEntry && accepted > 0) {
                budget.grew(StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES);
            }
        }
        if (credited > 0L) {
            setDirty();
        }
        if (credited < offered) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_STATION_WARP_CREDIT_REFUSED offered={} credited={} entry_room={}",
                    offered, credited, entryRoom);
        }
        return new WarpCreditFold(credited, offered - credited);
    }

    /**
     * Review F2: refuses growth that would leave less than the ADR-044 balance headroom of the 4 MiB
     * registry bound, so ordinary saves can never fail on size. The caller records the growth with
     * {@code budget.grew} once the mutation succeeded.
     */
    private void requireStorageRoom(long growthBytes) {
        if (!budget.admitsGrowth(recordCount(), registry.warpEnergyBalances().size(), this::encodedBytes,
                growthBytes)) {
            throw new IllegalStateException("Station registry is at its storage bound");
        }
    }

    /** Committed stations plus pending reservations (0 while blocked). */
    public int recordCount() {
        return registry.stations().size() + registry.reservations().size();
    }

    private long encodedBytes() {
        try {
            return StationNbtSize.uncompressedBytes(encode(new CompoundTag(), null, null, clock.current()));
        } catch (IllegalStateException oversized) {
            return StationLimits.MAX_REGISTRY_NBT_BYTES + 1L;
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

    /** The current logical tick of the orbital clock (ADR-065 section 5.2.1); sampled once per update. */
    public long orbitalTick() {
        return clock.current();
    }

    /** The clock reached {@link Long#MAX_VALUE}: new nonzero rates are refused, STOP still works. */
    public boolean orbitalClockExhausted() {
        return clock.exhausted();
    }

    /** The last checked-commit clock floor: the one an ordinary save of a quarantined registry reasserts. */
    long orbitalClockFloor() {
        return clockFloor;
    }

    /**
     * One logical-server END tick. The clock advances only while the registry is operational and not
     * quarantined (an unknown write suspends it until restart). While any station rotates, the registry is
     * marked dirty at most once per 20 ticks, so ordinary autosaves checkpoint the sample; nothing is
     * forced to disk.
     */
    public void advanceOrbitalClock() {
        if (!updatesAvailable()) {
            return;
        }
        clock.advance();
        if (clock.current() % StationLimits.ORBITAL_CHECKPOINT_TICKS == 0L && registry.anyRotating()) {
            setDirty();
        }
    }

    /** Grows the observed station to its centered 768 region through a checked commit. */
    public CheckedUpdate checkedExpand(MinecraftServer server, StationState observed) {
        return checkedExpand(stationFile(server), observed, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedExpand(Path file, StationState observed, CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return checked(file, observed, observed.withExpandedRegion(), 0, StationState.Transition.GROWTH,
                clock.current(), committer);
    }

    /** Sets the observed station's configured gravity through a checked commit. */
    public CheckedUpdate checkedSetGravity(MinecraftServer server, StationState observed, int gravityMilli) {
        return checkedSetGravity(stationFile(server), observed, gravityMilli, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetGravity(Path file, StationState observed, int gravityMilli,
                                    CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return checked(file, observed, observed.withGravityMilli(gravityMilli), 0,
                StationState.Transition.GRAVITY_ONLY, clock.current(), committer);
    }

    /** ADR-065 ALTITUDE_ONLY: sets the observed station's logical orbital distance through a checked commit. */
    public CheckedUpdate checkedSetAltitude(MinecraftServer server, StationState observed, int distance) {
        return checkedSetAltitude(stationFile(server), observed, distance, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetAltitude(Path file, StationState observed, int distance,
                                     CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return checked(file, observed, observed.withOrbitalControls(observed.orbitalControls().withDistance(distance)),
                0, StationState.Transition.ALTITUDE_ONLY, clock.current(), committer);
    }

    /**
     * ADR-065 ORIENTATION_ONLY: publishes orientation controls the caller computed at {@code sample}, the one
     * clock sample it took. Their epoch must equal the sample, which the candidate also carries as its root
     * clock; the sample must not be behind the acknowledged clock or ahead of the runtime clock.
     */
    public CheckedUpdate checkedSetOrientation(MinecraftServer server, StationState observed,
                                               StationOrbitalControls controls, long sample) {
        return checkedSetOrientation(stationFile(server), observed, controls, sample, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetOrientation(Path file, StationState observed, StationOrbitalControls controls, long sample,
                                        CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(controls, "controls");
        if (controls.epoch() != sample) {
            throw new IllegalArgumentException("Orientation controls must be sampled at the candidate clock");
        }
        return checked(file, observed, observed.withOrbitalControls(controls), 0,
                StationState.Transition.ORIENTATION_ONLY, sample, committer);
    }

    /**
     * ADR-044 warp: moves the observed station's orbit to {@code target} and debits {@code cost} from
     * its balance in one checked replacement of the station file, then one publish.
     *
     * <p>This checks authority, the observed state, an exact relocation and that the live balance
     * covers the cost. The caller (the WARP-03 commit step) must first fold pending credits and check
     * that the target is present, orbitable, known and not the current orbit, and that the cost and
     * cost class equal the confirmed ones; this method accepts any target identifier.
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
        return checked(file, observed, observed.withOrbitBody(target), cost, null, clock.current(), committer);
    }

    private static Path stationFile(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(ManagedSavedDataType.STATIONS.fileName());
    }

    /**
     * Package-private for tests; production callers go through the named transitions above. Any checked
     * transition is accepted here, with the current clock as the candidate's.
     */
    CheckedUpdate checkedReplace(Path file, StationState observed, StationState replacement,
                                         CheckedSavedDataFile.Committer committer) {
        return checked(file, observed, replacement, 0,
                replacement.checkedTransitionFrom(observed).orElse(null), clock.current(), committer);
    }

    /**
     * Replaces the station file with the complete candidate registry, then publishes the update.
     * The live registry is not changed unless the candidate is known to be the replaced authority.
     * A positive {@code cost} makes this an orbit relocation charged to the station's balance;
     * otherwise it is exactly the named checked {@code transition}, and every balance must stay as it is.
     * The candidate carries {@code sample} as its orbital clock (ADR-065 section 5.2.1); a known commit
     * publishes the record and raises the acknowledged clock to that sample together.
     */
    private CheckedUpdate checked(Path file, StationState observed, StationState replacement, int cost,
                                  StationState.Transition transition, long sample,
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
        if (!relocation && (transition == null
                || !replacement.checkedTransitionFrom(observed).filter(transition::equals).isPresent())) {
            throw new IllegalArgumentException("The replacement is not the named checked station transition");
        }
        if (sample < clockFloor || sample > clock.current()) {
            throw new IllegalArgumentException("The candidate clock is behind the acknowledged clock or ahead of time");
        }
        int balance = registry.warpEnergy(observed.stationId());
        if (relocation && balance < cost) {
            return CheckedUpdate.INSUFFICIENT_ENERGY;
        }
        long growth = relocation ? Math.max(0, replacement.orbitBody().toString().length()
                - observed.orbitBody().toString().length()) : 0L;
        if (relocation) {
            requireStorageRoom(growth);
        }
        Integer debited = relocation ? balance - cost : null;
        CompoundTag candidate = candidateTransform.apply(encode(new CompoundTag(), replacement, debited, sample));
        requireValidCandidate(candidate, replacement, debited, sample);
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
                budget.grew(growth);
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
        // The record and its durable clock are published together.
        clockFloor = sample;
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
     * is unchanged, and its orbital clock is exactly the sample.
     */
    private void requireValidCandidate(CompoundTag candidate, StationState replacement, Integer debited,
                                       long sample) {
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
        StationRegistryPayload.Decoded decoded = StationRegistryPayload.decodeCurrentRoot(migration.payload());
        if (!decoded.registry().stations().equals(expectedStations)
                || !decoded.registry().reservations().equals(registry.reservations())
                || !decoded.registry().warpEnergyBalances().equals(expectedBalances)
                || decoded.logicalTick() != sample) {
            throw new IllegalStateException("Candidate station registry failed validation");
        }
    }

    public void flush(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (isDirty()) {
            server.overworld().getDataStorage().save();
        }
    }

    /**
     * An ordinary save. A healthy registry captures the current clock once with the live record set; a
     * quarantined one reasserts the acknowledged records with the last checked-commit clock floor, never a
     * live uncertain sample (ADR-065 section 5.2.1). An ordinary save is not a disk barrier, so it does not
     * raise the floor.
     */
    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        return encode(target, null, null, updatesQuarantined ? clockFloor : clock.current());
    }

    /**
     * Encodes the registry with {@code logicalTick} as its orbital clock, optionally substituting one
     * station by UUID and, for a relocation, that station's balance, without mutating the live registry.
     * Every encoded epoch must be at or before that clock.
     */
    private CompoundTag encode(CompoundTag target, StationState substitute, Integer substituteBalance,
                               long logicalTick) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.STATIONS, target);
        ListTag stations = new ListTag();
        registry.stations().forEach(state -> {
            StationState encoded = substitute != null && substitute.stationId().equals(state.stationId())
                    ? substitute : state;
            if (encoded.orbitalControls().epoch() > logicalTick) {
                throw new IllegalStateException("A station orbital epoch is ahead of the encoded clock");
            }
            stations.add(StationNbtCodec.encodeState(encoded));
        });
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
        target.put(StationRegistryPayload.ORBITAL_CLOCK, StationRegistryPayload.encodeClock(logicalTick));
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
