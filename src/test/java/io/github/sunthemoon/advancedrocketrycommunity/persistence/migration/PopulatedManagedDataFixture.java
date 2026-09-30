package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.SavedData;

/** Constructed domain states; not a captured packaged-world or player fixture. */
final class PopulatedManagedDataFixture {
    static final UUID OWNER = id(1);
    static final UUID PENDING_MISSION = id(31);
    static final UUID ACTIVE_MISSION = id(33);
    /** ADR-044 balance of the occupied station; root 4 only (older roots carry no balances). */
    static final int WARP_ENERGY = 1_234_567;

    private PopulatedManagedDataFixture() {
    }

    static Map<ManagedSavedDataType, CompoundTag> currentPayloads() {
        Map<ManagedSavedDataType, CompoundTag> payloads = new EnumMap<>(ManagedSavedDataType.class);
        CelestialSavedData celestial = CelestialSavedData.create();
        celestial.discover(ModIdentity.id("earth"), 12L);
        celestial.discover(ModIdentity.id("moon"), 900L);
        celestial.recordVisit(ModIdentity.id("moon"), 1_000L);
        payloads.put(ManagedSavedDataType.CELESTIAL, celestial.save(new CompoundTag()));

        RocketStructureSnapshot assembly = snapshot(id(10), new RocketPosition(8, 64, -2));
        RocketTransactionSavedData transactions = new RocketTransactionSavedData();
        transactions.journalFor(assembly, OWNER).write(new RocketTransactionRecord(
                id(11), RocketTransactionType.ASSEMBLY, RocketTransactionPhase.EXTRACTING,
                assembly.snapshotId(), assembly.contentHash(), RocketRegion.fromSnapshot(assembly), 1, null
        ));
        payloads.put(ManagedSavedDataType.ROCKET_TRANSACTIONS, transactions.save(new CompoundTag()));

        RocketTransferSavedData transfers = new RocketTransferSavedData();
        transfers.put(transfer().destinationSpawned(id(24)));
        payloads.put(ManagedSavedDataType.ROCKET_TRANSFERS, transfers.save(new CompoundTag()));

        StationRegistrySavedData stations = new StationRegistrySavedData();
        stations.reserve(id(40), OWNER, "Occupied", ModIdentity.id("earth"), 42L);
        stations.commit(id(40));
        stations.invite(id(40), id(2));
        stations.acceptInvitation(id(40), id(2));
        stations.invite(id(40), id(3));
        stations.foldWarpCredits(java.util.Map.of(id(40), WARP_ENERGY));
        stations.reserve(id(41), OWNER, "Reserved", ModIdentity.id("moon"), 43L);
        payloads.put(ManagedSavedDataType.STATIONS, stations.save(new CompoundTag()));

        SatelliteDefinition definition = new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION, SatelliteIds.DATA_SATELLITE,
                200, 120, 100, List.of(ModIdentity.id("earth"), ModIdentity.id("moon"))
        );
        SatelliteMissionSavedData satellites = SatelliteMissionSavedData.create(1_000L);
        satellites.launch(id(30), PENDING_MISSION, OWNER, definition, ModIdentity.id("moon"), 1_000L, true);
        satellites.completeDue(1_200L);
        satellites.claim(PENDING_MISSION, OWNER, 1_201L);
        satellites.launch(id(32), ACTIVE_MISSION, OWNER, definition, ModIdentity.id("earth"), 1_201L, true);
        payloads.put(ManagedSavedDataType.SATELLITE_MISSIONS, satellites.save(new CompoundTag()));
        return payloads;
    }

    static SavedData load(ManagedSavedDataType type, CompoundTag payload) {
        return switch (type) {
            case CELESTIAL -> CelestialSavedData.load(payload);
            case ROCKET_TRANSACTIONS -> RocketTransactionSavedData.load(payload);
            case ROCKET_TRANSFERS -> RocketTransferSavedData.load(payload);
            case STATIONS -> StationRegistrySavedData.load(payload);
            case SATELLITE_MISSIONS -> SatelliteMissionSavedData.load(payload);
        };
    }

    private static RocketTransferRecord transfer() {
        UUID transferId = id(20);
        RocketStructureSnapshot source = snapshot(id(21), new RocketPosition(32, 72, 12));
        RocketPosition destinationPosition = new RocketPosition(72, 80, 8);
        long transitAt = RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS;
        long arrivalAt = transitAt + RocketFlightLimits.TRANSIT_TICKS;
        RocketStructureSnapshot destination = source.relocated(
                id(22), RocketFlightPlanner.MOON.dimensionId(), destinationPosition, arrivalAt
        );
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(
                source.stats(), fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transferId, 0L
        ).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(
                id(25), source.stats().fuelCapacity(), source.stats().seatCount(),
                RocketFlightPlanner.EARTH.bodyId(), source.sourceDimension(), source.sourceOrigin(), 0L
        ).withFuel(fuel, 0L)
                .withPassengers(RocketPassengerManifest.empty(1).assign(OWNER).orElseThrow())
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transferId, transitAt);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transferId, plan.requiredFuel()).state(),
                RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.MOON.dimensionId(),
                destinationPosition, arrivalAt
        );
        return RocketTransferRecord.create(
                transferId, id(25), OWNER, id(23), source, destination,
                sourceFlight, destinationFlight, plan.requiredFuel(), 0L
        );
    }

    private static RocketStructureSnapshot snapshot(UUID id, RocketPosition origin) {
        RocketBlockState iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), iron),
                new RocketBlock(new RocketPosition(0, 1, 0), iron),
                new RocketBlock(new RocketPosition(0, 2, 0), iron),
                new RocketBlock(new RocketPosition(1, 1, 0), iron)
        );
        return RocketStructureSnapshot.create(
                id, RocketFlightPlanner.EARTH.dimensionId(), origin, blocks,
                List.of(new RocketPosition(1, 1, 0)), new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0), 0L
        );
    }

    private static UUID id(long value) {
        return new UUID(0x100L, value);
    }
}
