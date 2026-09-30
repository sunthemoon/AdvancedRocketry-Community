package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

final class SatelliteMissionSavedDataTest {
    @Test
    void externalMissionSnapshotSurvivesWithoutRegistrationAndClaimsOnceAfterReload() {
        UUID satellite = UUID.randomUUID(), mission = UUID.randomUUID(), owner = UUID.randomUUID();
        var external = new SatelliteDefinition(1, net.minecraft.resources.ResourceLocation.tryParse("removed:mission"),
                400, 137, 11, List.of(ModIdentity.id("earth")));
        var data = SatelliteMissionSavedData.create(1000);
        assertTrue(data.launch(satellite, mission, owner, external, ModIdentity.id("earth"), 1000, false).success());
        var original = data.mission(mission).orElseThrow();
        var restored = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
        assertEquals(original, restored.mission(mission).orElseThrow());
        restored.completeDue(1400);
        assertEquals(SatelliteOperationCode.UNAUTHORIZED, restored.claim(mission, UUID.randomUUID(), 1400).code());
        assertTrue(restored.claim(mission, owner, 1400).success());
        assertEquals(137, restored.account(owner).balance());
        var claimed = SatelliteMissionSavedData.load(restored.save(new CompoundTag()));
        assertEquals(SatelliteOperationCode.ALREADY_CLAIMED, claimed.claim(mission, owner, 1401).code());
        assertEquals(137, claimed.account(owner).balance());
    }

    @Test
    void activeMissionClockAndAccountRoundTripAcrossRestart() {
        UUID satelliteId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(1_000L);
        data.launch(
                satelliteId, missionId, ownerId, definition(), ModIdentity.id("moon"), 1_000L, true
        );
        data.completeDue(1_200L);
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY,
                data.claim(missionId, ownerId, 1_201L).code());

        SatelliteMissionSavedData restored = SatelliteMissionSavedData.load(data.save(new CompoundTag()));

        assertTrue(restored.operational());
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY,
                restored.mission(missionId).orElseThrow().status());
        assertEquals(20, restored.account(ownerId).balance());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY,
                restored.claim(missionId, ownerId, 1_202L).code());
        assertEquals(20, restored.account(ownerId).balance());
        assertEquals(SatelliteOperationCode.SUCCESS, restored.finishDiscovery(missionId).code());

        SatelliteMissionSavedData finalReload = SatelliteMissionSavedData.load(
                restored.save(new CompoundTag())
        );
        assertEquals(MissionStatus.CLAIMED, finalReload.mission(missionId).orElseThrow().status());
        assertTrue(finalReload.satellite(satelliteId).orElseThrow().currentMissionId().isEmpty());
    }

    @Test
    void futureRootAndNestedSchemasArePreservedAndBlocked() {
        CompoundTag future = emptyRoot();
        future.putInt("schema_version", SatelliteLimits.REGISTRY_SCHEMA_VERSION + 1);
        future.putByteArray("opaque", new byte[]{4, 8, 15, 16, 23, 42});

        SatelliteMissionSavedData blocked = SatelliteMissionSavedData.load(future);

        assertFalse(blocked.operational());
        assertArrayEquals(future.getByteArray("opaque"),
                blocked.save(new CompoundTag()).getByteArray("opaque"));
        assertThrows(IllegalStateException.class, () -> blocked.account(UUID.randomUUID()));

        CompoundTag nested = emptyRoot();
        CompoundTag satellite = new CompoundTag();
        satellite.putInt("schema_version", SatelliteLimits.SATELLITE_SCHEMA_VERSION + 1);
        ListTag satellites = new ListTag();
        satellites.add(satellite);
        nested.put("satellites", satellites);
        SatelliteMissionSavedData nestedBlocked = SatelliteMissionSavedData.load(nested);
        assertFalse(nestedBlocked.operational());
        assertEquals(nested, nestedBlocked.save(new CompoundTag()));

        SatelliteMissionSavedData valid = SatelliteMissionSavedData.create(1_000L);
        valid.launch(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                definition(), ModIdentity.id("moon"), 1_000L, true
        );
        CompoundTag encoded = valid.save(new CompoundTag());
        CompoundTag futureMission = encoded.copy();
        futureMission.getList("missions", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putInt("schema_version", SatelliteLimits.MISSION_SCHEMA_VERSION + 1);
        assertFalse(SatelliteMissionSavedData.load(futureMission).operational());

        CompoundTag futureAccount = encoded.copy();
        futureAccount.getList("research_accounts", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .putInt("schema_version", SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION + 1);
        assertFalse(SatelliteMissionSavedData.load(futureAccount).operational());
    }

    @Test
    void malformedListsFailClosedAndBrokenReferencesAreQuarantined() {
        CompoundTag malformed = emptyRoot();
        malformed.putString("missions", "not-a-list");
        assertFalse(SatelliteMissionSavedData.load(malformed).operational());

        // ADR-050 section 9 (C8a): a broken reference no longer blocks the registry; the unfinished mission is
        // held for an operator, is not scheduled, and the change waits for the coalesced flush.
        SatelliteMissionSavedData valid = SatelliteMissionSavedData.create(1_000L);
        UUID owner = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        valid.launch(
                UUID.randomUUID(), missionId, owner,
                definition(), ModIdentity.id("moon"), 1_000L, true
        );
        CompoundTag brokenReference = valid.save(new CompoundTag());
        brokenReference.put("satellites", new ListTag());
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(brokenReference);
        assertTrue(loaded.operational());
        assertTrue(loaded.flushPending());
        var held = loaded.mission(missionId).orElseThrow();
        assertEquals(MissionStatus.QUARANTINED, held.status());
        assertEquals("MISSING_SATELLITE", held.quarantine().orElseThrow().reason());
        assertEquals(MissionStatus.ACTIVE, held.quarantine().orElseThrow().previousStatus());
        assertEquals(0, loaded.completeDue(1_000_000L).completed());
    }

    @Test
    void restoredActiveMissionCompletesAtOriginalDeadline() {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(10_000L);
        UUID missionId = UUID.randomUUID();
        data.launch(
                UUID.randomUUID(), missionId, UUID.randomUUID(), definition(),
                ModIdentity.id("moon"), 10_000L, true
        );

        SatelliteMissionSavedData restored = SatelliteMissionSavedData.load(
                data.save(new CompoundTag())
        );

        assertEquals(0, restored.completeDue(10_199L).completed());
        assertEquals(1, restored.completeDue(10_200L).completed());
        assertEquals(MissionStatus.READY, restored.mission(missionId).orElseThrow().status());
    }

    private static CompoundTag emptyRoot() {
        CompoundTag root = new CompoundTag();
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.SATELLITE_MISSIONS, root);
        CompoundTag clock = new CompoundTag();
        clock.putLong("logical_game_time", 0L);
        clock.putLong("last_observed_game_time", 0L);
        root.put("clock", clock);
        root.put("satellites", new ListTag());
        root.put("missions", new ListTag());
        root.put("research_accounts", new ListTag());
        return root;
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(
                SatelliteLimits.DEFINITION_SCHEMA_VERSION,
                SatelliteIds.DATA_SATELLITE,
                200,
                120,
                100,
                List.of(ModIdentity.id("earth"), ModIdentity.id("moon"), ModIdentity.id("space"))
        );
    }
}
