package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-074 items 2 and 3: the capture's encoder is byte-identical to {@code save(CompoundTag)}, and nothing a caller or
 * the owner thread changes after a capture reaches it. The 16 MiB byte-identity check and the
 * over-bound refusal run in B1 under Xmx4G, as W129a's 64 MiB check runs in B2.
 */
final class SatelliteRegistryCaptureTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void captureEncodingIsByteIdenticalToSaveOnEveryFixture() throws Exception {
        var display = new BlockPos.MutableBlockPos(3, 70, -9);
        for (var data : List.of(SatelliteMissionSavedData.create(0), mixed(file("a"), display, GroundSurveyFixtures.YIELD),
                padded(640), blocked())) {
            CompoundTag saved = data.save(new CompoundTag());
            CompoundTag captured = SatelliteRegistryCapture.encode(data.captureForTests());
            assertEquals(saved, captured);
            assertArrayEquals(bytes(saved), bytes(captured));
        }
    }

    @Test void laterChangesOfRamSuppliedCollectionsAndPositionsNeverReachACapture() throws Exception {
        var display = new BlockPos.MutableBlockPos(3, 70, -9);
        var reward = new ArrayList<>(GroundSurveyFixtures.YIELD);
        var data = mixed(file("b"), display, reward);
        var capture = data.captureForTests();
        byte[] expected = bytes(data.save(new CompoundTag()));

        display.set(100, 1, 100);
        reward.add(new RewardEntry(ResourceLocation.tryParse("minecraft:gold_ore"), 3));
        var launched = SatelliteRegistryProofTest.launch(data);
        assertTrue(data.cancel(SatelliteManager.initialMissionId(launched),
                data.satellite(launched).orElseThrow().ownerId(), false, 5).success());
        long created = data.groundSurveyQuery(g -> g.previewTime(2000));
        assertEquals(GroundSurveys.Code.SUCCESS,
                data.groundSurveys(g -> g.start(RegistryWriterFixtures.ground(1, created), 2000)).code());
        data.completeDue(50_000);

        assertArrayEquals(expected, bytes(SatelliteRegistryCapture.encode(capture)));
        assertNotEquals(data.save(new CompoundTag()), SatelliteRegistryCapture.encode(capture));
        var mission = capture.sections().missions().stream()
                .filter(state -> state.missionId().equals(RegistryWriterFixtures.RESOURCE_MISSION)).findFirst().orElseThrow();
        var resource = (MissionPayload.Resource) mission.payload();
        assertEquals(new BlockPos(3, 70, -9), resource.boundTerminalDisplay().orElseThrow().pos());
        assertEquals(GroundSurveyFixtures.YIELD, resource.reward());
        assertThrows(UnsupportedOperationException.class, () -> capture.sections().missions().clear());
        assertThrows(UnsupportedOperationException.class, () -> capture.sections().satellites().clear());
        assertThrows(UnsupportedOperationException.class, () -> capture.sections().groundSurveys().clear());
        assertThrows(UnsupportedOperationException.class, () -> resource.reward().clear());
    }

    @Test void aBlockedCaptureSharesThePreservedRootReadOnlyAndEncodesACopy() {
        CompoundTag raw = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        raw.putInt("schema_version", 999);
        var blocked = SatelliteMissionSavedData.load(raw);
        raw.putString("caller_change", "after load");
        var capture = blocked.captureForTests();
        assertSame(capture.blocked(), blocked.captureForTests().blocked(), "The capture shares one preserved root");
        CompoundTag first = SatelliteRegistryCapture.encode(capture);
        first.putString("encoder_change", "after encode");
        assertEquals(blocked.save(new CompoundTag()), SatelliteRegistryCapture.encode(capture));
        assertFalse(SatelliteRegistryCapture.encode(capture).contains("caller_change"));
        assertFalse(blocked.preservedBlockedData().orElseThrow().contains("encoder_change"));
        assertNotSame(capture.blocked(), blocked.preservedBlockedData().orElseThrow(), "Public accessors return copies");
    }

    private java.nio.file.Path file(String directory) throws java.io.IOException {
        return java.nio.file.Files.createDirectories(temporary.resolve(directory)).resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
    }
}
