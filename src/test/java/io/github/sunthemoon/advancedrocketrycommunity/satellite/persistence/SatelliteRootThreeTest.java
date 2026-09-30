package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionQuarantine;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-050 §2, §3 and §10: root-3 shapes, legacy upgrade, bounds and the save epoch. */
final class SatelliteRootThreeTest {
    @TempDir
    Path temporary;

    @Test
    void legacyRootTwoUpgradesRecordsWithTheLegacyLabelAndKeepsExtraTags() {
        CompoundTag current = populated().save(new CompoundTag());
        CompoundTag legacy = downgrade(current);
        legacy.getList("satellites", CompoundTag.TAG_COMPOUND).getCompound(0).putString("extra_satellite_tag", "kept");
        legacy.getList("missions", CompoundTag.TAG_COMPOUND).getCompound(0).putString("extra_mission_tag", "kept");

        var migration = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, legacy);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migration.status());
        CompoundTag payload = migration.payload();
        assertEquals(3, payload.getInt("schema_version"));
        assertEquals("v1.6.0-satellite-missions", payload.getString("format_epoch"));
        assertEquals(1L, payload.getLong("save_epoch"));
        assertTrue(payload.getList("instances", CompoundTag.TAG_COMPOUND).isEmpty());
        CompoundTag satellite = payload.getList("satellites", CompoundTag.TAG_COMPOUND).getCompound(0);
        assertEquals("kept", satellite.getString("extra_satellite_tag"));
        assertEquals("data", satellite.getString("kind"));
        assertTrue(satellite.getCompound("blueprint").getBoolean("legacy"));
        CompoundTag mission = payload.getList("missions", CompoundTag.TAG_COMPOUND).getCompound(0);
        assertEquals("kept", mission.getString("extra_mission_tag"));
        assertEquals("legacy-data-v1", mission.getString("reward_version"));
        assertEquals(0L, mission.getLong("start_epoch"));

        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(payload);
        assertTrue(loaded.operational());
        assertEquals(SatelliteBlueprint.LEGACY_DATA, loaded.satellites().get(0).blueprint());
        assertEquals(1L, loaded.saveEpoch());

        CompoundTag rootOne = legacy.copy();
        rootOne.putInt("schema_version", 1);
        rootOne.remove("format_epoch");
        CompoundTag fromOne = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, rootOne).payload();
        assertEquals(1, fromOne.getInt("migrated_from_schema"));
        assertEquals(3, fromOne.getInt("schema_version"));
    }

    @Test
    void rootShapesFailClosedAndStayPreserved() {
        CompoundTag legacyWithSections = downgrade(populated().save(new CompoundTag()));
        legacyWithSections.put("instances", new ListTag());
        assertThrows(SavedDataMigrationException.class,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, legacyWithSections));
        assertFalse(SatelliteMissionSavedData.load(legacyWithSections).operational());

        CompoundTag missingEpoch = populated().save(new CompoundTag());
        missingEpoch.remove("save_epoch");
        SatelliteMissionSavedData blocked = SatelliteMissionSavedData.load(missingEpoch);
        assertFalse(blocked.operational());
        assertEquals(missingEpoch, blocked.save(new CompoundTag()));

        CompoundTag unknownKind = populated().save(new CompoundTag());
        unknownKind.getList("satellites", CompoundTag.TAG_COMPOUND).getCompound(0).putString("kind", "orbital_laser");
        assertFalse(SatelliteMissionSavedData.load(unknownKind).operational());

        CompoundTag mixedPayload = populated().save(new CompoundTag());
        mixedPayload.getList("missions", CompoundTag.TAG_COMPOUND).getCompound(0).putBoolean("rebound", true);
        assertFalse(SatelliteMissionSavedData.load(mixedPayload).operational());
    }

    @Test
    void worstCaseRecordsFitTheirDerivedBounds() {
        List<ResourceLocation> components = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_BLUEPRINT_COMPONENTS; index++) {
            components.add(longId(index));
        }
        SatelliteState satellite = new SatelliteState(SatelliteLimits.SATELLITE_SCHEMA_VERSION, UUID.randomUUID(),
                longId(90), UUID.randomUUID(), Long.MAX_VALUE, io.github.sunthemoon.advancedrocketrycommunity.satellite
                .model.SatelliteStatus.OPERATIONAL, Optional.of(UUID.randomUUID()),
                io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind.SURVEY, Optional.of(longId(91)),
                new SatelliteBlueprint(components, false, new SatelliteStats(1_000, 1_000_000, 100_000, 27, 100)),
                new SatelliteKindState.Survey(1_000_000L, Long.MAX_VALUE, 100_000, 48, 16));
        int satelliteBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeSatellite(satellite));
        assertTrue(satelliteBytes <= SatelliteLimits.MAX_SATELLITE_RECORD_NBT_BYTES, "satellite " + satelliteBytes);
        assertEquals(satellite, SatelliteNbtCodec.decodeSatellite(SatelliteNbtCodec.encodeSatellite(satellite)));

        List<RewardEntry> reward = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_REWARD_ENTRIES; index++) {
            reward.add(new RewardEntry(longId(index), index == 0 ? 1_712 : 1));
        }
        MissionState resource = new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), longId(92), MissionKind.ASTEROID, longId(93),
                Optional.of(UUID.randomUUID()), Long.MIN_VALUE, 0L, Long.MAX_VALUE - 1, Long.MAX_VALUE,
                MissionStatus.QUARANTINED, OptionalLong.of(Long.MAX_VALUE - 1), OptionalLong.empty(),
                "asteroid-v1/" + longId(94) + "/0123456789abcdef".substring(0, 16),
                Optional.of(new MissionQuarantine("MISSING_SATELLITE_AND_A_LONGER_REASON_CODE_12345", MissionStatus.READY, true)),
                new MissionPayload.Resource(MissionKind.ASTEROID, reward, UUID.randomUUID(),
                        Optional.of(new MissionPayload.TerminalLocation(longId(95), new BlockPos(30_000_000, 319, -30_000_000))),
                        true, Optional.of(UUID.randomUUID()), true, OptionalLong.of(Long.MAX_VALUE)));
        int missionBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(resource));
        assertTrue(missionBytes <= SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES, "mission " + missionBytes);
        assertEquals(resource, SatelliteNbtCodec.decodeMission(SatelliteNbtCodec.encodeMission(resource)));

        List<RewardEntry> yield = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_REWARD_ENTRIES; index++) {
            yield.add(new RewardEntry(longId(index), index == 0 ? 4_080 : 1));
        }
        AsteroidInstance instance = new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, UUID.randomUUID(),
                UUID.randomUUID(), longId(96), longId(97), "0123456789abcdef", "fedcba9876543210", Long.MIN_VALUE,
                yield, 0L, OptionalLong.of(Long.MAX_VALUE), InstanceState.ALLOCATED, UUID.randomUUID(),
                Optional.of(UUID.randomUUID()));
        int instanceBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeInstance(instance));
        assertTrue(instanceBytes <= SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES, "instance " + instanceBytes);
        assertEquals(instance, SatelliteNbtCodec.decodeInstance(SatelliteNbtCodec.encodeInstance(instance)));
        System.out.printf("ARCE_V160_RECORD_BOUNDS satellite=%d mission=%d instance=%d%n",
                satelliteBytes, missionBytes, instanceBytes);
    }

    @Test
    void overCapLegacyRootLoadsWithItsCountAndFitsTheByteBudget() {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0L);
        UUID owner = UUID.randomUUID();
        List<UUID> satellites = new ArrayList<>();
        long time = 0L;
        int missions = 0;
        while (missions < SatelliteLimits.MAX_MISSIONS) {
            UUID mission = new UUID(7L, missions);
            boolean ok;
            if (satellites.size() < 64) {
                UUID satellite = new UUID(8L, satellites.size());
                ok = data.launch(satellite, mission, owner, definition(), ModIdentity.id("moon"), time, false).success();
                satellites.add(satellite);
            } else {
                ok = data.startMission(satellites.get(missions % 64), mission, owner, definition(),
                        ModIdentity.id("moon"), time, false).success();
            }
            assertTrue(ok, "start " + missions);
            assertTrue(data.claim(mission, owner, time + 200L).success(), "claim " + missions);
            // The server drains the deadline queue every 20 ticks; a claim leaves a stale entry (ADR-050 §5, C8a).
            data.completeDue(time + 200L);
            time += 200L;
            missions++;
        }
        CompoundTag legacy = downgrade(data.save(new CompoundTag()));
        int legacyBytes = SatelliteNbtSize.uncompressedBytes(legacy);
        assertTrue(legacyBytes <= 4 * 1024 * 1024, "a v1.5 root is at most 4 MiB, was " + legacyBytes);

        CompoundTag migrated = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, legacy).payload();
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(migrated);
        assertTrue(loaded.operational());
        assertEquals(SatelliteLimits.MAX_MISSIONS, loaded.missions().size());
        int migratedBytes = SatelliteNbtSize.uncompressedBytes(loaded.save(new CompoundTag()));
        assertTrue(migratedBytes <= 6 * 1024 * 1024, "missions section budget, was " + migratedBytes);
        System.out.printf("ARCE_V160_OVER_CAP_LEGACY missions=%d legacy_bytes=%d migrated_bytes=%d%n",
                loaded.missions().size(), legacyBytes, migratedBytes);
    }

    @Test
    void saveEpochAdvancesOnlyAfterAChangedWriteSucceeds() {
        MinecraftBootstrap.initialize();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(1_000L);
        assertEquals(1L, data.saveEpoch());
        assertEquals(1L, data.save(new CompoundTag()).getLong("save_epoch"));
        UUID mission = UUID.randomUUID();
        data.launch(UUID.randomUUID(), mission, UUID.randomUUID(), definition(), ModIdentity.id("moon"), 1_000L, false);
        assertEquals(1L, data.mission(mission).orElseThrow().startEpoch());
        CompoundTag pending = data.save(new CompoundTag());
        assertEquals(2L, pending.getLong("save_epoch"));
        assertEquals(pending, data.save(new CompoundTag()), "an unpersisted save is repeatable");
        assertEquals(1L, data.saveEpoch(), "an in-memory save does not advance the epoch");

        data.flush(temporary.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        assertEquals(2L, data.saveEpoch());
        CompoundTag persisted = data.save(new CompoundTag());
        assertEquals(2L, persisted.getLong("save_epoch"), "no change since the write, no new epoch");
        data.flush(temporary.resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName()));
        assertEquals(2L, data.saveEpoch());

        SatelliteMissionSavedData reloaded = SatelliteMissionSavedData.load(persisted);
        assertEquals(2L, reloaded.saveEpoch());
        assertTrue(reloaded.saveEpoch() > reloaded.mission(mission).orElseThrow().startEpoch(),
                "a mission present in a file has a start epoch below the file's epoch");
        assertEquals(persisted, reloaded.save(new CompoundTag()), "an unchanged reload writes the same bytes");
    }

    private static SatelliteMissionSavedData populated() {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.create(1_000L);
        UUID owner = UUID.randomUUID();
        data.launch(UUID.randomUUID(), UUID.randomUUID(), owner, definition(), ModIdentity.id("moon"), 1_000L, true);
        return data;
    }

    /** The same payload as a v1.5 root 2 would store it. */
    static CompoundTag downgrade(CompoundTag current) {
        CompoundTag legacy = current.copy();
        legacy.putInt("schema_version", 2);
        legacy.putString("format_epoch", SavedDataSchemaMigrator.FORMAT_EPOCH);
        legacy.remove("instances");
        legacy.remove("save_epoch");
        legacy.getList("satellites", CompoundTag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag satellite = (CompoundTag) raw;
            satellite.putInt("schema_version", 1);
            List.of("kind", "orbit_body", "blueprint", "kind_state").forEach(satellite::remove);
        });
        legacy.getList("missions", CompoundTag.TAG_COMPOUND).forEach(raw -> {
            CompoundTag mission = (CompoundTag) raw;
            mission.putInt("schema_version", 1);
            List.of("kind", "seed", "start_epoch", "reward_version", "instance_id", "quarantine").forEach(mission::remove);
        });
        return legacy;
    }

    private static SatelliteDefinition definition() {
        return new SatelliteDefinition(SatelliteLimits.DEFINITION_SCHEMA_VERSION, SatelliteIds.DATA_SATELLITE, 200, 120,
                100, List.of(ModIdentity.id("earth"), ModIdentity.id("moon")));
    }

    private static ResourceLocation longId(int index) {
        String namespace = "n" + "x".repeat(31);
        String path = String.format("p%03d", index) + "y".repeat(128 - namespace.length() - 1 - 4);
        ResourceLocation id = ResourceLocation.tryParse(namespace + ":" + path);
        assertEquals(128, id.toString().length());
        return id;
    }
}
