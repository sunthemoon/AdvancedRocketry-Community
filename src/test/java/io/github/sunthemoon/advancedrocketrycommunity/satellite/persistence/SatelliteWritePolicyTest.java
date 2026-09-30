package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-050 section 2 and revision 4 item 1: every state change marks the registry "flush pending" and makes the
 * next write carry E + 1; clock-only passes set only the dirty flag; a successful write clears the pending flag.
 */
final class SatelliteWritePolicyTest {
    @TempDir
    Path temporary;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everyKindOfChangeIsMarkedAndAClockOnlyPassIsNot() {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0L);
        UUID owner = UUID.randomUUID();
        UUID dataSatellite = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID survey = UUID.randomUUID();
        UUID solar = UUID.randomUUID();

        expectMarked(data, "data launch", d -> d.launch(dataSatellite, first, owner, definition(), ModIdentity.id("moon"),
                0L, true));
        expectMarked(data, "idle launch", d -> d.launchIdle(time -> survey(survey, owner, time), 0L));
        expectMarked(data, "solar launch", d -> d.launchIdle(time -> solar(solar, owner, time), 0L));
        expectClockOnly(data, 100L);
        expectMarked(data, "completion", d -> {
            assertEquals(1, d.completeDue(200L).completed());
            return null;
        });
        expectMarked(data, "claim", d -> d.claim(first, owner, 201L));
        expectMarked(data, "discovery", d -> d.finishDiscovery(first));
        UUID second = UUID.randomUUID();
        expectMarked(data, "start", d -> d.startMission(dataSatellite, second, owner, definition(), ModIdentity.id("moon"),
                300L, false));
        expectMarked(data, "cancel", d -> d.cancel(second, owner, false, 301L));
        expectMarked(data, "link change", d -> d.updateKindState(solar,
                new SatelliteKindState.Solar(100, Optional.of(UUID.randomUUID()))));
        expectMarked(data, "scan payment", d -> d.payScan(survey, owner, false, 400L));
        expectMarked(data, "decommission", d -> d.decommission(survey, owner, false, false));
        expectClockOnly(data, 500L);
    }

    private void expectMarked(SatelliteMissionSavedData data, String what,
                              Function<SatelliteMissionSavedData, SatelliteOperationResult> change) {
        flush(data);
        long epoch = data.saveEpoch();
        assertFalse(data.flushPending(), what + ": the flush did not clear the pending flag");
        SatelliteOperationResult result = change.apply(data);
        if (result != null) {
            assertTrue(result.changed(), what + " did not change the registry: " + result.code());
        }
        assertTrue(data.flushPending(), what + " was not marked flush pending");
        assertEquals(epoch + 1L, data.save(new CompoundTag()).getLong("save_epoch"), what + " does not advance E");
    }

    private void expectClockOnly(SatelliteMissionSavedData data, long observedGameTime) {
        flush(data);
        long epoch = data.saveEpoch();
        assertFalse(data.completeDue(observedGameTime).changed());
        assertFalse(data.flushPending(), "a clock-only pass set flush pending");
        assertTrue(data.isDirty(), "a clock-only pass must still reach the ordinary save");
        assertEquals(epoch, data.save(new CompoundTag()).getLong("save_epoch"));
    }

    private void flush(SatelliteMissionSavedData data) {
        data.flush(temporary.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
    }

    private static SatelliteState survey(UUID id, UUID owner, long time) {
        return SatelliteState.launchIdle(id, ModIdentity.id("survey_satellite"), owner, time, ModIdentity.id("earth"),
                blueprint(), new SatelliteKindState.Survey(10_720L, time, 1_000, 16, 16));
    }

    private static SatelliteState solar(UUID id, UUID owner, long time) {
        return SatelliteState.launchIdle(id, ModIdentity.id("solar_satellite"), owner, time, ModIdentity.id("earth"),
                blueprint(), new SatelliteKindState.Solar(100, Optional.empty()));
    }

    private static SatelliteBlueprint blueprint() {
        return new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("primary")), false,
                new SatelliteStats(4, 10_720, 1_000, 0, 10));
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(SatelliteLimits.DEFINITION_SCHEMA_VERSION, SatelliteIds.DATA_SATELLITE, 200, 120,
                100, List.of(ModIdentity.id("earth"), ModIdentity.id("moon"), ModIdentity.id("space")));
    }
}
