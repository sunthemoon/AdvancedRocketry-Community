package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.PlanetaryDiscoveryPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanetaryDiscoveryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void flagRoundTripsAndOldAuthoringRemainsUnrestricted() {
        for (var body : PlanetaryContent.definitions()) {
            assertTrue(body.discoveryRequired());
            var json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body).result().orElseThrow();
            var nbt = CelestialBodyDefinition.CODEC.encodeStart(NbtOps.INSTANCE, body).result().orElseThrow();
            assertEquals(body, CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
            assertEquals(body, CelestialBodyDefinition.CODEC.parse(NbtOps.INSTANCE, nbt).result().orElseThrow());
            json.getAsJsonObject().remove("discovery_required");
            assertFalse(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow().discoveryRequired());
            var prior = new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), body.gravityMultiplier(),
                    body.atmosphere(), body.orbit(), body.visualProfile(), body.capabilities(), body.solarIntensity(),
                    body.radiation(), body.environmentEffects());
            assertFalse(prior.discoveryRequired());
        }
        for (var old : CelestialDefaults.definitions()) {
            assertFalse(old.discoveryRequired());
            assertFalse(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE,
                    old.encodeLegacy(JsonOps.INSTANCE).result().orElseThrow()).result().orElseThrow().discoveryRequired());
        }
    }

    @Test void malformedFlagsAndLossyLegacyExportReject() {
        var body = PlanetaryContent.definitions().get(0);
        for (String invalid : List.of("null", "0", "1", "\"false\"", "[]", "{}")) {
            var json = CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body).result().orElseThrow().getAsJsonObject();
            json.add("discovery_required", JsonParser.parseString(invalid));
            assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), invalid);
        }
        var old = CelestialDefaults.definitions().get(0);
        for (boolean flag : List.of(true, false)) {
            var json = old.encodeLegacy(JsonOps.INSTANCE).result().orElseThrow().getAsJsonObject();
            json.addProperty("discovery_required", flag);
            assertTrue(CelestialBodyDefinition.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        }
        var gated = new CelestialBodyDefinition(old.id(), old.parentId(), old.levelKey(), old.gravityMultiplier(),
                old.atmosphere(), old.orbit(), old.visualProfile(), old.capabilities(), old.solarIntensity(), old.radiation(), false, true);
        assertTrue(gated.encodeLegacy(JsonOps.INSTANCE).error().isPresent());
    }

    @Test void sharedRecordsAndVisitsSurviveRemovalRoundTripWithoutGrantingMissingBodies() {
        var progress = CelestialSavedData.create();
        var mars = PlanetaryContent.definitions().get(0);
        assertFalse(allows(mars, progress));
        assertEquals(CelestialSavedData.MutationResult.CHANGED, progress.recordVisit(mars.id(), 42));
        var raw = progress.save(new CompoundTag());
        assertFalse(allows(null, progress));
        assertEquals(raw, progress.save(new CompoundTag()));
        var restored = CelestialSavedData.load(raw);
        assertTrue(allows(mars, restored));
        assertEquals(42, restored.get(mars.id()).orElseThrow().firstVisitAt().orElseThrow());
        assertTrue(PlanetaryDiscoveryPolicy.allows(CelestialDefaults.definitions().get(0), id -> {
            fail("An unrestricted body consulted the discovery authority"); return false;
        }));
    }

    @Test void futureOrFullProgressDoesNotInventUnlocksOrEvictHistoricalIds() {
        var progress = CelestialSavedData.create();
        for (int i = 0; i < 128; i++) { progress.discover(ModIdentity.id("removed_" + i), i); }
        var before = progress.save(new CompoundTag());
        assertEquals(CelestialSavedData.MutationResult.CAPACITY_REACHED, progress.discover(PlanetaryContent.MARS, 200));
        assertFalse(allows(PlanetaryContent.definitions().get(0), progress));
        assertEquals(before, progress.save(new CompoundTag()));
        before.putInt("schema_version", 99);
        var future = CelestialSavedData.load(before);
        assertFalse(future.isWritableSchema());
        assertFalse(allows(PlanetaryContent.definitions().get(0), future));
        assertTrue(allows(CelestialDefaults.definitions().get(0), future));
        assertEquals(before, future.save(new CompoundTag()));
    }

    @Test void concurrentMissionFeesAreCapturedAndPrivateAndClaimsSurviveRestart() {
        var missions = SatelliteMissionSavedData.create(0);
        var definition = PlanetaryContent.surveySatellite();
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var firstMission = UUID.randomUUID(); var secondMission = UUID.randomUUID();
        assertTrue(missions.launch(UUID.randomUUID(), firstMission, first, definition, PlanetaryContent.MARS, 0, true).success());
        assertTrue(missions.launch(UUID.randomUUID(), secondMission, second, definition, PlanetaryContent.MARS, 0, true).success());
        assertEquals(SatelliteOperationCode.UNAUTHORIZED, missions.claim(firstMission, second, 200).code());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, missions.claim(firstMission, first, 200).code());
        assertEquals(20, missions.account(first).balance());
        assertEquals(0, missions.account(second).balance());
        assertTrue(missions.finishDiscovery(firstMission).success());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, missions.claim(secondMission, second, 200).code());
        assertEquals(20, missions.account(second).balance());
        var restored = SatelliteMissionSavedData.load(missions.save(new CompoundTag()));
        for (int i = 0; i < 5; i++) {
            assertEquals(SatelliteOperationCode.PENDING_DISCOVERY, restored.claim(secondMission, second, 201 + i).code());
            assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, restored.claim(firstMission, first, 201 + i).code());
        }
        assertEquals(20, restored.account(first).balance());
        assertEquals(20, restored.account(second).balance());
        assertEquals(120, restored.account(first).lifetimeEarned());
        assertEquals(100, restored.account(first).lifetimeSpent());
        assertTrue(restored.finishDiscovery(secondMission).success());
        var knownMission = UUID.randomUUID();
        assertTrue(restored.launch(UUID.randomUUID(), knownMission, second, definition, PlanetaryContent.MARS, 205, false).success());
        assertEquals(SatelliteOperationCode.SUCCESS, restored.claim(knownMission, second, 405).code());
        assertEquals(140, restored.account(second).balance());
    }

    @Test void packagedSatelliteKeepsHistoricalEconomyAndOffersNewWorlds() throws Exception {
        try (var input = getClass().getResourceAsStream("/data/advancedrocketrycommunity/satellite_definitions/data_satellite.json")) {
            assertNotNull(input);
            var definition = io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition.CODEC
                    .parse(JsonOps.INSTANCE, JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).result().orElseThrow();
            assertEquals(PlanetaryContent.surveySatellite(), definition);
            assertEquals(6, definition.allowedTargets().size());
            assertEquals(200, definition.missionDurationTicks());
            assertEquals(120, definition.researchYield());
            assertEquals(100, definition.discoveryCost());
        }
    }

    private static boolean allows(CelestialBodyDefinition body, CelestialSavedData data) {
        return PlanetaryDiscoveryPolicy.allows(body, id -> data.isWritableSchema() && data.get(id).isPresent());
    }
}
