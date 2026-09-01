package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MissionDeadlineSchedulerTest {
    @Test
    void oneHundredFutureMissionsRequireOnlyOneHeadInspection() {
        Map<UUID, MissionState> missions = missions(100);
        MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
        scheduler.rebuild(missions.values());

        MissionDeadlineScheduler.DrainResult result = scheduler.drainDue(
                1_199L,
                SatelliteLimits.MAX_COMPLETIONS_PER_PASS,
                id -> java.util.Optional.ofNullable(missions.get(id)),
                ignored -> {
                    throw new AssertionError("No mission should be due");
                }
        );

        assertEquals(0, result.completed());
        assertEquals(1, result.inspectedEntries());
        assertEquals(100, result.remainingScheduled());
    }

    @Test
    void dueWorkIsCappedAndDrainedInDeadlineOrder() {
        Map<UUID, MissionState> missions = missions(100);
        MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
        scheduler.rebuild(missions.values());
        List<Long> deadlines = new ArrayList<>();

        MissionDeadlineScheduler.DrainResult first = scheduler.drainDue(
                2_000L,
                SatelliteLimits.MAX_COMPLETIONS_PER_PASS,
                id -> java.util.Optional.ofNullable(missions.get(id)),
                mission -> {
                    deadlines.add(mission.completesAtLogicalTime());
                    missions.put(mission.missionId(), mission.complete(2_000L));
                }
        );

        assertEquals(SatelliteLimits.MAX_COMPLETIONS_PER_PASS, first.completed());
        assertTrue(first.inspectedEntries() <= SatelliteLimits.MAX_QUEUE_INSPECTIONS_PER_PASS);
        assertEquals(68, first.remainingScheduled());
        for (int index = 1; index < deadlines.size(); index++) {
            assertTrue(deadlines.get(index - 1) <= deadlines.get(index));
        }
    }

    private static Map<UUID, MissionState> missions(int count) {
        SatelliteDefinition definition = new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("moon"))
        );
        SatelliteDefinitionSnapshot snapshot = SatelliteDefinitionSnapshot.from(definition);
        Map<UUID, MissionState> missions = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            UUID missionId = UUID.nameUUIDFromBytes(("mission-" + index).getBytes());
            MissionState mission = MissionState.start(
                    missionId,
                    UUID.nameUUIDFromBytes(("satellite-" + index).getBytes()),
                    UUID.nameUUIDFromBytes(("owner-" + index).getBytes()),
                    snapshot,
                    ModIdentity.id("moon"),
                    1_000L + index,
                    true
            );
            missions.put(missionId, mission);
        }
        return missions;
    }
}
