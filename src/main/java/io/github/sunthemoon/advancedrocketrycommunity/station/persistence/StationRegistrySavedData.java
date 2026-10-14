package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventSourceView;
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
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;

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
    /** W44 section 1.1: the first-warp slot; healthy RAM is the known committed authority. */
    private FirstEventOutbox firstWarp = FirstEventOutbox.empty();
    /** W44 section 3.6: how this root was first obtained; a detached root behaves as loaded. */
    private StationRootPresence root = new StationRootPresence(ManagedRootPresence.LOADED);
    /** W44 section 1.2: a known-committed candidate whose publish failed; saved and read instead of stale RAM. */
    private CompoundTag committedOverride;

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
        return ManagedRootSupplier.obtain(server, TYPE, tag -> {
            FirstEventCutSeams.beforeLoad(server, TYPE);
            return load(tag);
        }, StationRegistrySavedData::created);
    }

    /** The root-presence supplier's root: PROVEN_ABSENT or UNREADABLE (W44 section 3.6). */
    static StationRegistrySavedData created(ManagedRootPresence presence) {
        StationRegistrySavedData data = new StationRegistrySavedData();
        data.root = new StationRootPresence(presence);
        return data;
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
            StationRegistrySavedData data = new StationRegistrySavedData(decoded.registry(), decoded.logicalTick());
            data.firstWarp = decoded.firstWarp();
            return data;
        } catch (RuntimeException exception) {
            StationRegistrySavedData data = new StationRegistrySavedData();
            data.preservedBlockedData = preserved;
            return data;
        }
    }

    /** Not blocked and not unreadable; an unreadable root is never written and holds all station gameplay. */
    public boolean operational() {
        return preservedBlockedData == null && !root.unreadable();
    }

    public ManagedRootPresence presence() {
        return preservedBlockedData != null ? ManagedRootPresence.BLOCKED : root.presence();
    }

    /** Reads that could authorize a mutation report unavailable under a committed override (W44 section 1.2). */
    private boolean readable() {
        return operational() && committedOverride == null;
    }

    /** The RAM slot; equal to the durable view while the registry is healthy. */
    public FirstEventOutbox firstWarpRam() {
        return firstWarp;
    }

    /**
     * W44 terms: the durable view. Healthy RAM is known committed authority, a publish-failure override supplies its
     * committed candidate, and a quarantined unknown update, an unreadable or a blocked root is unavailable.
     */
    public FirstEventSourceView durableFirstWarp() {
        if (root.unreadable()) {
            return FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_UNREADABLE);
        }
        if (preservedBlockedData != null) {
            return FirstEventSourceView.unavailable(FirstEventSourceView.Reason.ROOT_BLOCKED);
        }
        if (committedOverride != null) {
            return FirstEventSourceView.known(StationRegistryPayload.decodeCurrentRoot(committedOverride).firstWarp());
        }
        return updatesQuarantined ? FirstEventSourceView.unavailable(FirstEventSourceView.Reason.QUARANTINED)
                : FirstEventSourceView.known(firstWarp);
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
        requireMutable();
        requireStorageRoom(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        StationReservation result = registry.reserve(
                stationId, ownerId, name, orbitBody, createdAtGameTime
        );
        budget.grew(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState commit(UUID stationId) {
        requireMutable();
        requireStorageRoom(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        StationState result = registry.commit(stationId);
        budget.grew(StationLimits.MAX_STATION_RECORD_NBT_BYTES);
        setDirty();
        return result;
    }

    /** Operational and not quarantined: checked updates are possible (ADR-041/044/045 rule 1). */
    public boolean updatesAvailable() {
        return readable() && !updatesQuarantined;
    }

    /**
     * ADR-044 §2: warp cores are credited only while the registry is operational and not quarantined
     * (a quarantined registry refuses every checked update, so it could never spend the energy).
     */
    public boolean acceptsWarpEnergy() {
        return updatesAvailable();
    }

    public boolean release(UUID stationId) {
        requireMutable();
        boolean changed = registry.release(stationId);
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Optional<StationState> delete(UUID stationId) {
        requireMutable();
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
        requireMutable();
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.addMember(stationId, memberId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState removeMember(UUID stationId, UUID memberId) {
        requireMutable();
        StationState result = registry.removeMember(stationId, memberId);
        setDirty();
        return result;
    }

    public StationState invite(UUID stationId, UUID playerId) {
        requireMutable();
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.invite(stationId, playerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState acceptInvitation(UUID stationId, UUID playerId) {
        requireMutable();
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.acceptInvitation(stationId, playerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        setDirty();
        return result;
    }

    public StationState declineInvitation(UUID stationId, UUID playerId) {
        requireMutable();
        StationState result = registry.declineInvitation(stationId, playerId);
        setDirty();
        return result;
    }

    public StationState transferOwnership(UUID stationId, UUID ownerId) {
        requireMutable();
        requireStorageRoom(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        StationState result = registry.transferOwnership(stationId, ownerId);
        budget.grew(StationLimits.MAX_TEAM_ENTRY_GROWTH_NBT_BYTES);
        setDirty();
        return result;
    }

    public Optional<StationState> find(UUID stationId) {
        return readable() ? registry.find(stationId) : Optional.empty();
    }

    public Optional<StationState> findAt(int x, int z) {
        return readable() ? registry.findAt(x, z) : Optional.empty();
    }

    public List<StationState> stations() {
        return readable() ? registry.stations() : List.of();
    }

    public List<StationReservation> reservations() {
        return readable() ? registry.reservations() : List.of();
    }

    public long ownedBy(UUID ownerId) {
        return readable() ? registry.ownedBy(ownerId) : Long.MAX_VALUE;
    }

    /** ADR-066 section 3.3: the access index generation; empty while the registry is blocked. */
    public java.util.OptionalLong accessGeneration() {
        return readable() ? java.util.OptionalLong.of(registry.accessGeneration()) : java.util.OptionalLong.empty();
    }

    /**
     * The next station after {@code after} (the first when null) that a player owns or belongs to, or any committed
     * station for an operator, in UUID order. Empty at the end and while the registry is blocked, so a caller checks
     * {@link #operational()} first: a blocked registry is not an empty candidate set.
     */
    public Optional<UUID> nextAccessCandidate(UUID playerId, boolean operator, UUID after) {
        return readable() ? registry.nextAccessCandidate(playerId, operator, after) : Optional.empty();
    }

    /** Stored warp energy of a station (ADR-044); 0 when absent or while the registry is blocked. */
    public int warpEnergy(UUID stationId) {
        return readable() ? registry.warpEnergy(stationId) : 0;
    }

    public SortedMap<UUID, Integer> warpEnergyBalances() {
        return readable() ? registry.warpEnergyBalances() : new TreeMap<>();
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
        // A committed override refuses every mutation, as a registry that is not operational does (W44 section 1.2).
        if (!readable()) {
            return new WarpCreditFold(0L, offered);
        }
        long credited = StationWarpEnergyFold.apply(registry, budget, credits, this::recordCount, this::encodedBytes,
                offered);
        if (credited > 0L) {
            setDirty();
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

    /**
     * W44 section 5 preflight of a potentially first warp: an elected outbox of {@code participants} fits the unchanged
     * 4 MiB root bound (an upper estimate; the checked call encodes the exact candidate).
     */
    public boolean admitsFirstWarp(int participants) {
        return readable() && budget.admitsOutbox(recordCount(), registry.warpEnergyBalances().size(),
                this::encodedBytes, 1_024L + 16L * participants);
    }

    /** Committed stations plus pending reservations (0 while blocked). */
    public int recordCount() {
        return registry.stations().size() + registry.reservations().size();
    }

    private long encodedBytes() {
        try {
            return StationNbtSize.uncompressedBytes(encode(new CompoundTag(), null, null, clock.current(), firstWarp));
        } catch (IllegalStateException oversized) {
            return StationLimits.MAX_REGISTRY_NBT_BYTES + 1L;
        }
    }

    /** Validates persisted orbit references without deleting recoverable station state. */
    public OrbitBodyValidation validateOrbitBodies(Predicate<ResourceLocation> bodyExists) {
        Objects.requireNonNull(bodyExists, "bodyExists");
        if (!readable()) {
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
        return named(file, observed, observed.withExpandedRegion(), StationState.Transition.GROWTH,
                clock.current(), committer);
    }

    /** Sets the observed station's configured gravity through a checked commit. */
    public CheckedUpdate checkedSetGravity(MinecraftServer server, StationState observed, int gravityMilli) {
        return checkedSetGravity(stationFile(server), observed, gravityMilli, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetGravity(Path file, StationState observed, int gravityMilli,
                                    CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return named(file, observed, observed.withGravityMilli(gravityMilli),
                StationState.Transition.GRAVITY_ONLY, clock.current(), committer);
    }

    /** ADR-065 ALTITUDE_ONLY: sets the observed station's logical orbital distance through a checked commit. */
    public CheckedUpdate checkedSetAltitude(MinecraftServer server, StationState observed, int distance) {
        return checkedSetAltitude(stationFile(server), observed, distance, CheckedSavedDataFile::atomicMove);
    }

    CheckedUpdate checkedSetAltitude(Path file, StationState observed, int distance,
                                     CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(observed, "observed");
        return named(file, observed, observed.withOrbitalControls(observed.orbitalControls().withDistance(distance)),
                StationState.Transition.ALTITUDE_ONLY, clock.current(), committer);
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
        return named(file, observed, observed.withOrbitalControls(controls),
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
        return checkedRelocation(server, observed, target, cost, FirstWarpRequest.NONE);
    }

    /**
     * W44 section 1.2: the electing relocation; {@code StationWarpService} is its only production caller. The
     * election is decided inside the one checked call and joins the relocation candidate.
     */
    public CheckedUpdate checkedRelocation(MinecraftServer server, StationState observed, ResourceLocation target,
                                           int cost, FirstWarpRequest request) {
        // FP8's gated committer changes the live winning station after the move, so the publish check fails.
        return relocation(stationFile(server), observed, target, cost, FirstEventCutSeams.stationPolicy(server,
                () -> addMember(observed.stationId(), FirstEventCutSeams.OBSERVER)), FirstEventCutSeams.directoryForce(server),
                request);
    }

    CheckedUpdate checkedRelocation(Path file, StationState observed, ResourceLocation target, int cost,
                                    CheckedSavedDataFile.Committer committer) {
        return relocation(file, observed, target, cost, new CheckedSavedDataPolicy(TYPE, committer),
                CheckedSavedDataPolicy.DirectoryForce.PLATFORM, FirstWarpRequest.NONE);
    }

    CheckedUpdate relocation(Path file, StationState observed, ResourceLocation target, int cost,
                             CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force,
                             FirstWarpRequest request) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(request, "request");
        if (cost <= 0 || cost > StationLimits.MAX_WARP_ENERGY) {
            throw new IllegalArgumentException("Warp cost is outside its bound");
        }
        return checked(file, observed, observed.withOrbitBody(target), cost, null, clock.current(), policy, force,
                request);
    }

    /**
     * W44 section 1.3: the root-only checked acknowledgement ELECTED(E) to DELIVERED(E). No station lookup: the whole
     * current registry is kept exactly, with only the outbox and the root clock sample substituted, through the same
     * writer as every named update. The caller has proven {@code receipt} durable and charges the write budget.
     */
    public CheckedUpdate checkedDeliverFirstWarp(MinecraftServer server, FirstEvent expected, FirstEvent receipt,
                                                 StationCheckedWriter.Diagnostics log) {
        if (!server.isSameThread()) {
            return CheckedUpdate.UNAVAILABLE;
        }
        return checkedDeliverFirstWarp(stationFile(server), expected, receipt,
                FirstEventCutSeams.stationPolicy(server, () -> { }), FirstEventCutSeams.directoryForce(server), log);
    }

    CheckedUpdate checkedDeliverFirstWarp(Path file, FirstEvent expected, FirstEvent receipt,
                                          CheckedSavedDataPolicy policy, CheckedSavedDataPolicy.DirectoryForce force,
                                          StationCheckedWriter.Diagnostics log) {
        Objects.requireNonNull(expected, "expected");
        if (!updatesAvailable()) {
            return CheckedUpdate.UNAVAILABLE;
        }
        if (firstWarp.state() == FirstEventOutbox.State.DELIVERED && expected.equals(firstWarp.event())) {
            return CheckedUpdate.UNCHANGED;
        }
        if (!expected.equals(receipt) || firstWarp.state() != FirstEventOutbox.State.ELECTED
                || !expected.equals(firstWarp.event())) {
            return CheckedUpdate.STALE;
        }
        long sample = clock.current();
        FirstEventOutbox delivered = firstWarp.delivered();
        CompoundTag candidate = candidateTransform.apply(encode(new CompoundTag(), null, null, sample, delivered));
        new StationCheckedCandidate(registry.stations(), registry.reservations(), registry.warpEnergyBalances(),
                delivered, sample).require(candidate);
        CheckedUpdate failed = written(StationCheckedWriter.write(file, candidate, policy, force, root.guard(), log));
        if (failed != CheckedUpdate.COMMITTED) {
            return failed;
        }
        // Only the outbox and the clock floor change, together, after the known commit.
        firstWarp = delivered;
        clockFloor = sample;
        return CheckedUpdate.COMMITTED;
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
        return named(file, observed, replacement, replacement.checkedTransitionFrom(observed).orElse(null),
                clock.current(), committer);
    }

    private CheckedUpdate named(Path file, StationState observed, StationState replacement,
                                StationState.Transition transition, long sample,
                                CheckedSavedDataFile.Committer committer) {
        Objects.requireNonNull(committer, "committer");
        return checked(file, observed, replacement, 0, transition, sample, new CheckedSavedDataPolicy(TYPE, committer),
                CheckedSavedDataPolicy.DirectoryForce.PLATFORM, FirstWarpRequest.NONE);
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
                                  StationState.Transition transition, long sample, CheckedSavedDataPolicy policy,
                                  CheckedSavedDataPolicy.DirectoryForce force, FirstWarpRequest request) {
        Objects.requireNonNull(file, "file");
        if (!updatesAvailable()) {
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
        // W44 section 1.2, step 2: a DEFER refuses before any write, debit or cooldown; ALLOW is rechecked here.
        FirstEventDisposition first = relocation ? request.current() : FirstEventDisposition.NOT_FIRST;
        if (first == FirstEventDisposition.DEFER) {
            return CheckedUpdate.FIRST_EVENT_UNAVAILABLE;
        }
        long growth = relocation ? Math.max(0, replacement.orbitBody().toString().length()
                - observed.orbitBody().toString().length()) : 0L;
        if (relocation) {
            requireStorageRoom(growth);
        }
        Integer debited = relocation ? balance - cost : null;
        FirstEventOutbox outbox = firstWarp;
        CompoundTag encoded;
        if (first == FirstEventDisposition.ALLOW && firstWarp.isEmpty()) {
            // Step 3: at most 4,096 participants and the exact candidate within the unchanged 4 MiB root bound.
            try {
                outbox = FirstEventOutbox.elected(FirstEvent.StationWarp.create(UUID.randomUUID(), observed.stationId(),
                        request.actor(), observed.orbitBody(), replacement.orbitBody(), cost, sample,
                        request.participants()));
                encoded = encode(new CompoundTag(), replacement, debited, sample, outbox);
            } catch (IllegalArgumentException | IllegalStateException overBudget) {
                return CheckedUpdate.FIRST_EVENT_OVER_BUDGET;
            }
        } else {
            encoded = encode(new CompoundTag(), replacement, debited, sample, outbox);
        }
        CompoundTag candidate = candidateTransform.apply(encoded);
        StationCheckedCandidate.named(registry.stations(), registry.reservations(), registry.warpEnergyBalances(),
                replacement, debited, outbox, sample).require(candidate);
        CheckedUpdate failed = written(StationCheckedWriter.write(file, candidate, policy, force, root.guard(),
                StationCheckedWriter.named(observed.stationId())));
        if (failed != CheckedUpdate.COMMITTED) {
            return failed;
        }
        try {
            if (relocation) {
                registry.relocateChecked(observed, replacement, balance, cost);
                budget.grew(growth);
            } else {
                registry.replaceChecked(observed, replacement);
            }
        } catch (RuntimeException publishFailure) {
            // Unreachable on the owning server thread. The candidate is a known commit: keep it, never reassert stale
            // RAM over it, and refuse every mutation until restart (W44 section 1.2, ADR-066 note).
            committedOverride = candidate.copy();
            updatesQuarantined = true;
            setDirty();
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_UPDATE_OUTCOME_UNKNOWN station={} stage=publish; updates disabled until restart",
                    observed.stationId(), publishFailure);
            return CheckedUpdate.OUTCOME_UNKNOWN;
        }
        // The record, its durable clock and the outbox are published together.
        firstWarp = outbox;
        clockFloor = sample;
        return CheckedUpdate.COMMITTED;
    }

    /** Applies a writer outcome: quarantine on UNKNOWN, reassert RAM after a failed replacement (ADR-041). */
    private CheckedUpdate written(StationCheckedWriter.Outcome outcome) {
        if (outcome == StationCheckedWriter.Outcome.COMMITTED) {
            root.committed();
            return CheckedUpdate.COMMITTED;
        }
        if (outcome != StationCheckedWriter.Outcome.WRITE_FAILED || root.afterFailedWrite()) {
            updatesQuarantined |= outcome == StationCheckedWriter.Outcome.OUTCOME_UNKNOWN;
            setDirty(!root.unreadable());
        }
        return outcome == StationCheckedWriter.Outcome.OUTCOME_UNKNOWN ? CheckedUpdate.OUTCOME_UNKNOWN
                : CheckedUpdate.WRITE_FAILED;
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
        if (committedOverride != null) {
            return committedOverride.copy();
        }
        if (root.unreadable()) {
            throw new IllegalStateException("An unreadable station registry is never encoded");
        }
        return encode(target, null, null, updatesQuarantined ? clockFloor : clock.current(), firstWarp);
    }

    /**
     * Vanilla's in-place ordinary save (W44 section 3.6): an unreadable root writes nothing, and the first ordinary
     * save of a proven-absent root re-checks that no entry has appeared.
     */
    @Override
    public void save(java.io.File file) {
        StationOrdinaryWriter.save(this, root, file);
    }

    /** One root with optional substitutions; the live registry is never mutated (see {@link StationRegistryPayload}). */
    private CompoundTag encode(CompoundTag target, StationState substitute, Integer substituteBalance,
                               long logicalTick, FirstEventOutbox outbox) {
        return StationRegistryPayload.encodeRoot(target, registry.stations(), registry.reservations(),
                registry.warpEnergyBalances(), substitute, substituteBalance, logicalTick, outbox);
    }

    /** Ordinary mutators also refuse under a committed override, not only while not operational. */
    private void requireMutable() {
        if (!readable()) {
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
        INSUFFICIENT_ENERGY,
        /** W44 section 1.2: a potentially first warp whose election would exceed a bound; nothing written or debited. */
        FIRST_EVENT_OVER_BUDGET,
        /** W44 section 1.2: a potentially first warp while the world-first authority is unavailable; nothing written. */
        FIRST_EVENT_UNAVAILABLE
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
