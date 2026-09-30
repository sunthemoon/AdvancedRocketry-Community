package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-052 section 7: every {@code mission verify} outcome, for instances and each mission kind. */
final class RewardVerifierTest {
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation GAS_GIANT = ModIdentity.id("gas_giant");
    private static final AsteroidType SMALL = type("t:small", "000000000000000a");
    private static final AsteroidType LIGHT = type("t:light", "000000000000000b");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void instancesMatchOrReportWhyTheyCannot() {
        ResourceTables tables = tables(List.of(SMALL), List.of());
        AsteroidInstance instance = instance(SMALL, 42L, ResourceAlgorithms.asteroidYield(SMALL, 42L));
        assertEquals(RewardVerifier.Outcome.MATCH, RewardVerifier.verifyInstance(instance, tables).outcome());
        assertEquals(RewardVerifier.Outcome.MISMATCH, RewardVerifier.verifyInstance(
                instance(SMALL, 42L, List.of(new RewardEntry(ResourceLocation.tryParse("minecraft:dirt"), 1))), tables).outcome());
        assertEquals(RewardVerifier.Outcome.VERSION_CHANGED, RewardVerifier.verifyInstance(instance,
                tables(List.of(type("t:small", "00000000000000ff")), List.of())).outcome());
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE,
                RewardVerifier.verifyInstance(instance, tables(List.of(), List.of())).outcome());
    }

    @Test
    void surveysRecomputeEveryInstance() {
        ResourceTables tables = tables(List.of(SMALL, LIGHT), List.of());
        List<AsteroidType> candidates = ResourceAlgorithms.candidates(tables.asteroidTypes(), EARTH);
        List<ResourceAlgorithms.GeneratedInstance> generated = ResourceAlgorithms.survey(7L, candidates, 3);
        Map<UUID, AsteroidInstance> stored = new HashMap<>();
        List<UUID> ids = new ArrayList<>();
        for (ResourceAlgorithms.GeneratedInstance value : generated) {
            AsteroidInstance instance = instance(value.type(), value.seed(), value.yield());
            stored.put(instance.instanceId(), instance);
            ids.add(instance.instanceId());
        }
        MissionState survey = mission(MissionKind.SURVEY, EARTH, 7L, Optional.empty(), "survey-v1",
                new MissionPayload.Survey(ids, ResourceAlgorithms.fingerprint(candidates)));
        assertEquals(RewardVerifier.Outcome.MATCH, verify(survey, stored, Map.of(), tables));
        assertEquals(RewardVerifier.Outcome.VERSION_CHANGED, verify(survey, stored, Map.of(), tables(List.of(SMALL), List.of())));
        Map<UUID, AsteroidInstance> pruned = new HashMap<>(stored);
        pruned.remove(ids.get(1));
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE, verify(survey, pruned, Map.of(), tables));
        Map<UUID, AsteroidInstance> tampered = new HashMap<>(stored);
        AsteroidInstance first = stored.get(ids.get(0));
        tampered.put(ids.get(0), instance(ids.get(0), SMALL.id().equals(first.asteroidType()) ? SMALL : LIGHT,
                first.seed() + 1L, first.yield()));
        assertEquals(RewardVerifier.Outcome.MISMATCH, verify(survey, tampered, Map.of(), tables));
    }

    @Test
    void asteroidMissionsAreTheInstanceYieldTruncatedToCargo() {
        ResourceTables tables = tables(List.of(SMALL), List.of());
        AsteroidInstance instance = instance(SMALL, 9L, ResourceAlgorithms.asteroidYield(SMALL, 9L));
        SatelliteState miner = satellite(SatelliteKind.ASTEROID_MINER, 1, 10);
        List<RewardEntry> reward = ResourceAlgorithms.truncate(instance.yield(), 1);
        String version = "asteroid-v1/" + SMALL.id() + "/" + SMALL.tableVersion();
        MissionState mission = mission(MissionKind.ASTEROID, EARTH, 5L, Optional.of(instance.instanceId()), version,
                resource(MissionKind.ASTEROID, reward), miner.satelliteId());
        Map<UUID, AsteroidInstance> instances = Map.of(instance.instanceId(), instance);
        Map<UUID, SatelliteState> satellites = Map.of(miner.satelliteId(), miner);
        assertEquals(RewardVerifier.Outcome.MATCH, verify(mission, instances, satellites, tables));
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE, verify(mission, Map.of(), satellites, tables));
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE, verify(mission, instances, Map.of(), tables));
        MissionState inflated = mission(MissionKind.ASTEROID, EARTH, 5L, Optional.of(instance.instanceId()), version,
                resource(MissionKind.ASTEROID, instance.yield()), miner.satelliteId());
        assertEquals(RewardVerifier.Outcome.MISMATCH, verify(inflated, instances, satellites, tables));
        // C9-L6: a rebalanced or removed type cannot be recomputed, so the mission is not MATCH either.
        AsteroidType rebalanced = type("t:small", "00000000000000ff");
        assertEquals(RewardVerifier.Outcome.VERSION_CHANGED, verify(mission, instances, satellites,
                tables(List.of(rebalanced), List.of())));
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE, verify(mission, instances, satellites,
                tables(List.of(), List.of())));
    }

    @Test
    void gasMissionsRecomputeTheAmount() {
        GasTable table = new GasTable(ResourceLocation.tryParse("t:gas"), GAS_GIANT,
                List.of(new GasTable.Product(ResourceLocation.tryParse("t:hydrogen"), 8)), "0000000000000abc");
        ResourceTables tables = tables(List.of(), List.of(table));
        SatelliteState harvester = satellite(SatelliteKind.GAS_HARVESTER, 9, 10);
        int amount = ResourceAlgorithms.gas(8, 10, 9, 100).amount();
        MissionState mission = mission(MissionKind.GAS, GAS_GIANT, 3L, Optional.empty(), "gas-v1/t:gas/0000000000000abc",
                resource(MissionKind.GAS, List.of(new RewardEntry(ResourceLocation.tryParse("t:hydrogen"), amount))),
                harvester.satelliteId());
        Map<UUID, SatelliteState> satellites = Map.of(harvester.satelliteId(), harvester);
        assertEquals(576, amount);
        assertEquals(RewardVerifier.Outcome.MATCH, verify(mission, Map.of(), satellites, tables));
        assertEquals(RewardVerifier.Outcome.VERSION_CHANGED, verify(mission, Map.of(), satellites, tables(List.of(),
                List.of(new GasTable(table.id(), GAS_GIANT, table.products(), "0000000000000abd")))));
        assertEquals(RewardVerifier.Outcome.INPUTS_UNAVAILABLE, verify(mission, Map.of(), satellites, tables(List.of(), List.of())));
        MissionState wrong = mission(MissionKind.GAS, GAS_GIANT, 3L, Optional.empty(), "gas-v1/t:gas/0000000000000abc",
                resource(MissionKind.GAS, List.of(new RewardEntry(ResourceLocation.tryParse("t:hydrogen"), amount + 1))),
                harvester.satelliteId());
        assertEquals(RewardVerifier.Outcome.MISMATCH, verify(wrong, Map.of(), satellites, tables));
    }

    private static RewardVerifier.Outcome verify(MissionState mission, Map<UUID, AsteroidInstance> instances,
                                                 Map<UUID, SatelliteState> satellites, ResourceTables tables) {
        return RewardVerifier.verifyMission(mission, id -> Optional.ofNullable(instances.get(id)),
                id -> Optional.ofNullable(satellites.get(id)), tables).outcome();
    }

    private static ResourceTables tables(List<AsteroidType> types, List<GasTable> gas) {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        CelestialCatalog catalog = CelestialCatalog.create(bodies).getOrThrow(false, message -> { });
        return ResourceTables.create(types, gas, catalog).getOrThrow(false, message -> { });
    }

    private static AsteroidType type(String id, String version) {
        return new AsteroidType(ResourceLocation.tryParse(id), 3, List.of(), 120, 50, 40, 50,
                ResourceLocation.tryParse("minecraft:cobblestone"),
                List.of(new AsteroidType.Ore(ResourceLocation.tryParse("minecraft:iron_ore"), 3),
                        new AsteroidType.Ore(ResourceLocation.tryParse("minecraft:gold_ore"), 1)), 100, version);
    }

    private static AsteroidInstance instance(AsteroidType type, long seed, List<RewardEntry> yield) {
        return instance(UUID.randomUUID(), type, seed, yield);
    }

    private static AsteroidInstance instance(UUID id, AsteroidType type, long seed, List<RewardEntry> yield) {
        return new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, id, UUID.randomUUID(), EARTH, type.id(),
                type.tableVersion(), "0123456789abcdef", seed, yield, 0L, OptionalLong.of(100L), InstanceState.AVAILABLE,
                UUID.randomUUID(), Optional.empty());
    }

    private static SatelliteState satellite(SatelliteKind kind, int cargo, int rating) {
        return SatelliteState.launchIdle(UUID.randomUUID(), ModIdentity.id("craft"), UUID.randomUUID(), 0L, EARTH,
                new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis")), false,
                        new SatelliteStats(4, 720, 0, cargo, rating)), new SatelliteKindState.Plain(kind));
    }

    private static MissionPayload.Resource resource(MissionKind kind, List<RewardEntry> reward) {
        return new MissionPayload.Resource(kind, reward, UUID.randomUUID(), Optional.empty(), false, Optional.empty(),
                false, OptionalLong.empty());
    }

    private static MissionState mission(MissionKind kind, ResourceLocation target, long seed, Optional<UUID> instance,
                                        String version, MissionPayload payload) {
        return mission(kind, target, seed, instance, version, payload, UUID.randomUUID());
    }

    private static MissionState mission(MissionKind kind, ResourceLocation target, long seed, Optional<UUID> instance,
                                        String version, MissionPayload payload, UUID satelliteId) {
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, UUID.randomUUID(), satelliteId,
                UUID.randomUUID(), ModIdentity.id("craft"), kind, target, instance, seed, 0L, 100L, 1L,
                MissionStatus.ACTIVE, OptionalLong.empty(), OptionalLong.empty(), version, Optional.empty(), payload);
    }
}
