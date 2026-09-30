package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C9 review M1, M2, M3 and L7: every queue holds at most one entry per record it serves (ADR-050 section 5), and a
 * bound terminal's display level is bounded when the record is made (ADR-050 section 3).
 */
final class QueueBoundsTest {
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation SYSTEM = ModIdentity.id("sol");
    private static final ResourceLocation TYPE = ModIdentity.id("small_asteroid");
    private static final String VERSION = "0123456789abcdef";
    private static final List<RewardEntry> YIELD = List.of(new RewardEntry(new ResourceLocation("minecraft",
            "iron_ore"), 30), new RewardEntry(new ResourceLocation("minecraft", "cobblestone"), 34));
    private static final RecordSizer SMALL = new RecordSizer() {
        @Override
        public int satelliteBytes(SatelliteState satellite) {
            return 100;
        }

        @Override
        public int missionBytes(MissionState mission) {
            return 100;
        }

        @Override
        public int instanceBytes(AsteroidInstance instance) {
            return 100;
        }
    };

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void recordsPrunedThroughOneQueueLeaveNoEntryInTheOther() throws Exception {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        registry.applyLimits(new RegistryLimits(1_024, 64, 1, 3_072, 4_096, 256, 2_048, 16, 10, 2));
        UUID owner = UUID.randomUUID();
        UUID satellite = UUID.randomUUID();
        UUID mission = UUID.randomUUID();
        registry.launch(satellite, mission, owner, definition(), MOON, 0L, false);
        long time = 0L;
        for (int round = 0; round < 2_000; round++) {
            time += 200L;
            assertTrue(registry.claim(mission, owner, time).success());
            mission = UUID.randomUUID();
            assertTrue(registry.startMission(satellite, mission, owner, definition(), MOON, time, false).success());
            registry.completeDue(time);
        }
        registry.completeDue(time + 2_000L);
        MissionRetention retention = (MissionRetention) field(registry, "retention");
        assertTrue(registry.missions().size() <= 3, "the per-owner limit of 1 keeps pruning");
        assertTrue(retention.queued() <= registry.finishedMissionCount(),
                "queued " + retention.queued() + " > finished " + registry.finishedMissionCount());
    }

    @Test
    void startCancelChurnKeepsOneExpiryEntryPerInstance() throws Exception {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0L, SMALL);
        ResourceMissions resources = registry.resources();
        UUID owner = UUID.randomUUID();
        UUID survey = craft(registry, owner, SatelliteKind.SURVEY);
        UUID miner = craft(registry, owner, SatelliteKind.ASTEROID_MINER);
        UUID surveyMission = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        resources.startSurvey(new ResourceMissions.SurveyStart(survey, surveyMission, owner, SYSTEM, VERSION, 1, 100, 1L,
                (count, time) -> List.of(new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, instanceId, owner,
                        SYSTEM, TYPE, VERSION, VERSION, 5L, YIELD, time, OptionalLong.empty(), InstanceState.PENDING,
                        surveyMission, Optional.empty()))), 0L);
        resources.claimSurvey(surveyMission, owner, 1_728_000L, 200L);
        UUID terminal = UUID.randomUUID();
        long time = 300L;
        for (int round = 0; round < 2_000; round++) {
            UUID missionId = UUID.randomUUID();
            assertEquals(SatelliteOperationCode.SUCCESS, resources.startResource(new ResourceMissions.ResourceStart(
                    miner, missionId, owner, MissionKind.ASTEROID, SYSTEM, Optional.of(instanceId), YIELD,
                    "asteroid-v1/" + TYPE + "/" + VERSION, 1_000, 9L, terminal, Optional.empty()), time++).code());
            assertEquals(SatelliteOperationCode.SUCCESS, resources.cancel(missionId, owner, false,
                    Optional.of(terminal), time++).code());
        }
        assertEquals(1, registry.instances().size());
        assertEquals(1, registry.ledger().queued(), "one expiry entry for the one AVAILABLE instance");
        assertEquals(0, registry.scheduledCount(), "every cancelled deadline left the scheduler");
    }

    @Test
    void anOverLongTerminalLevelIsNotStoredAndTheRegistryReloads() {
        ResourceLocation longLevel = new ResourceLocation("probe", "d".repeat(200));
        assertThrows(IllegalArgumentException.class, () -> new MissionPayload.TerminalLocation(longLevel,
                BlockPos.ZERO));
        assertEquals(Optional.empty(), MissionPayload.TerminalLocation.of(longLevel, BlockPos.ZERO));
        assertTrue(MissionPayload.TerminalLocation.of(ModIdentity.id("space"), BlockPos.ZERO).isPresent());

        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0L);
        UUID owner = UUID.randomUUID();
        UUID harvester = UUID.randomUUID();
        SatelliteBlueprint blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id("gas_harvester_primary")), false, new SatelliteStats(4, 10_720, 1_000, 9, 10));
        data.launchIdle(time -> SatelliteState.launchIdle(harvester, ModIdentity.id("gas_harvester"), owner, time, EARTH,
                blueprint, new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER)), 0L);
        SatelliteOperationResult started = data.resources(missions -> missions.startResource(
                new ResourceMissions.ResourceStart(harvester, UUID.randomUUID(), owner, MissionKind.GAS, EARTH,
                        Optional.empty(), List.of(new RewardEntry(new ResourceLocation("minecraft", "iron_ore"), 5)),
                        "gas-v1/test/" + VERSION, 1_000, 3L, UUID.randomUUID(),
                        MissionPayload.TerminalLocation.of(longLevel, BlockPos.ZERO)), 10L));
        assertEquals(SatelliteOperationCode.SUCCESS, started.code());
        SatelliteMissionSavedData reloaded = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.operational(), "the registry reloads");
        assertEquals(started.mission().orElseThrow(), reloaded.mission(started.mission().orElseThrow().missionId())
                .orElseThrow());
    }

    @Test
    void rescheduledAndRemovedDeadlinesKeepOneEntryPerMission() {
        MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
        MissionState mission = MissionState.start(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SatelliteDefinitionSnapshot.from(definition()), MOON, 0L, false);
        scheduler.schedule(mission);
        scheduler.schedule(mission);
        assertEquals(1, scheduler.scheduledCount(), "a replayed schedule keeps one entry");
        scheduler.remove(mission.missionId());
        scheduler.remove(mission.missionId());
        assertEquals(0, scheduler.scheduledCount());
    }

    private static UUID craft(SatelliteMissionRegistry registry, UUID owner, SatelliteKind kind) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0L, 0L, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        SatelliteBlueprint blueprint = new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id(kind.id() + "_primary")), false, new SatelliteStats(4, 10_720, 1_000, 1, 10));
        assertEquals(SatelliteOperationCode.SUCCESS, registry.launchIdle(time -> SatelliteState.launchIdle(
                satelliteId, ModIdentity.id(kind.id()), owner, time, EARTH, blueprint, state), 0L).code());
        return satelliteId;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(SatelliteLimits.DEFINITION_SCHEMA_VERSION, SatelliteIds.DATA_SATELLITE, 200, 120,
                100, List.of(EARTH, MOON, ModIdentity.id("space")));
    }
}
