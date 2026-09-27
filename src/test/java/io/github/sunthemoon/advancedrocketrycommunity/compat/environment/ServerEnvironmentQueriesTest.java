package io.github.sunthemoon.advancedrocketrycommunity.compat.environment;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServerEnvironmentQueriesTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void surfaceReadsCurrentDefinitionsWithoutWorldOrChunkAccess() {
        var catalogs = catalogs();
        var stations = new StationRegistrySavedData();
        try (var queries = queries(catalogs, stations)) {
            var earth = queries.at(Level.OVERWORLD, BlockPos.ZERO).orElseThrow();
            assertEquals(CelestialIds.EARTH_ID, earth.bodyId());
            assertEquals(1, earth.gravityMultiplier());
            assertFalse(earth.vacuum());
            assertTrue(earth.atmosphere().orElseThrow().breathable());
            var moon = queries.at(CelestialIds.MOON_LEVEL,
                    new BlockPos(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE)).orElseThrow();
            assertEquals(0.165, moon.gravityMultiplier());
            assertTrue(moon.vacuum());
            assertFalse(moon.atmosphere().orElseThrow().breathable());
            assertFalse(stations.isDirty());
        }
    }

    @Test
    void twoStationsInOneLevelUseTheirPersistedEnvironmentAndOrbitIdentity() {
        var stations = new StationRegistrySavedData();
        var earth = station(stations, CelestialIds.EARTH_ID);
        var moon = station(stations, CelestialIds.MOON_ID);
        CompoundTag saved = stations.save(new CompoundTag());
        for (Tag raw : saved.getList("stations", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            if (record.getUUID("station_id").equals(moon.stationId())) {
                record.getCompound("environment").putInt("gravity_milli", 7_250);
                record.getCompound("environment").putBoolean("vacuum", false);
            }
        }
        var restored = StationRegistrySavedData.load(saved);
        assertTrue(restored.operational());
        try (var queries = queries(catalogs(), restored)) {
            var first = queries.at(CelestialIds.SPACE_LEVEL, pad(earth)).orElseThrow();
            var second = queries.at(CelestialIds.SPACE_LEVEL, pad(moon)).orElseThrow();
            assertEquals(CelestialIds.EARTH_ID, first.bodyId());
            assertEquals(EnvironmentSnapshot.Locus.STATION_ORBIT, first.locus());
            assertEquals(earth.stationId(), first.instanceId().orElseThrow());
            assertEquals(0, first.gravityMultiplier());
            assertEquals(CelestialIds.MOON_ID, second.bodyId());
            assertEquals(7.25, second.gravityMultiplier());
            assertFalse(second.vacuum());
            assertTrue(second.atmosphere().isEmpty());
            assertEquals(second, queries.at(CelestialIds.SPACE_LEVEL,
                    new BlockPos(pad(moon).getX(), Integer.MIN_VALUE, pad(moon).getZ())).orElseThrow());
        }
    }

    @Test
    void queriesLeaveNativeSavedStateAndDirtyFlagUnchanged() {
        var stations = new StationRegistrySavedData();
        var station = station(stations, CelestialIds.EARTH_ID);
        stations.setDirty(false);
        CompoundTag before = stations.save(new CompoundTag());
        try (var queries = queries(catalogs(), stations)) {
            for (int index = 0; index < 32; index++) {
                assertTrue(queries.at(CelestialIds.SPACE_LEVEL, pad(station)).isPresent());
            }
        }
        assertEquals(before, stations.save(new CompoundTag()));
        assertFalse(stations.isDirty());
    }

    @Test
    void stationReservationCommitDeleteAndRegionEdgesAreImmediatelyVisible() {
        var stations = new StationRegistrySavedData();
        UUID id = UUID.randomUUID();
        var reserved = stations.reserve(id, UUID.randomUUID(), "query", CelestialIds.MOON_ID, 0);
        BlockPos position = new BlockPos(reserved.landingPad().x(), 0, reserved.landingPad().z());
        try (var queries = queries(catalogs(), stations)) {
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, position).isEmpty());
            var state = stations.commit(id);
            assertEquals(id, queries.at(CelestialIds.SPACE_LEVEL, position).orElseThrow().instanceId().orElseThrow());
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL,
                    new BlockPos(state.region().maximumX(), 0, state.region().maximumZ())).isPresent());
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL,
                    new BlockPos(state.region().maximumX() + 1, 0, state.region().maximumZ())).isEmpty());
            stations.delete(id);
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, position).isEmpty());
        }
    }

    @Test
    void blockedRegistryAndUnknownOrbitBodyNeverFallBackToSpace() {
        var stations = new StationRegistrySavedData();
        var unknown = station(stations, new ResourceLocation("test", "removed"));
        try (var queries = queries(catalogs(), stations)) {
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, pad(unknown)).isEmpty());
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL,
                    new BlockPos(Integer.MIN_VALUE, 0, Integer.MAX_VALUE)).isEmpty());
        }
        CompoundTag invalid = stations.save(new CompoundTag());
        invalid.putString("stations", "unreadable");
        var blocked = StationRegistrySavedData.load(invalid);
        assertFalse(blocked.operational());
        try (var queries = queries(catalogs(), blocked)) {
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, pad(unknown)).isEmpty());
            assertTrue(queries.at(Level.OVERWORLD, BlockPos.ZERO).isPresent());
        }
        assertEquals(invalid, blocked.save(new CompoundTag()));
    }

    @Test
    void acceptedCelestialReloadIsFreshAndRejectedCandidateKeepsDetachedValues() {
        var catalogs = catalogs();
        try (var queries = queries(catalogs, new StationRegistrySavedData())) {
            var original = queries.at(CelestialIds.MOON_LEVEL, BlockPos.ZERO).orElseThrow();
            assertTrue(catalogs.applyCandidate(CelestialCatalog.create(changedMoon(0.4))));
            var changed = queries.at(CelestialIds.MOON_LEVEL, BlockPos.ZERO).orElseThrow();
            assertEquals(0.165, original.gravityMultiplier());
            assertEquals(0.4, changed.gravityMultiplier());
            assertFalse(catalogs.applyCandidate(DataResult.error(() -> "Rejected query fixture")));
            assertEquals(changed, queries.at(CelestialIds.MOON_LEVEL, BlockPos.ZERO).orElseThrow());
            catalogs.clear();
            assertTrue(queries.at(CelestialIds.MOON_LEVEL, BlockPos.ZERO).isEmpty());
        }
    }

    @Test
    void missingBodyAfterReloadMakesItsPersistedStationUnresolved() {
        var catalogs = catalogs();
        var stations = new StationRegistrySavedData();
        var station = station(stations, CelestialIds.MOON_ID);
        try (var queries = queries(catalogs, stations)) {
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, pad(station)).isPresent());
            assertTrue(catalogs.applyCandidate(CelestialCatalog.create(
                    List.of(CelestialDefaults.definitions().get(0)))));
            assertTrue(queries.at(CelestialIds.SPACE_LEVEL, pad(station)).isEmpty());
            assertTrue(stations.find(station.stationId()).isPresent());
        }
    }

    @Test
    void unmappedUnavailableAndAmbiguousDimensionsAreEmpty() {
        var catalogs = catalogs();
        var definitions = new ArrayList<>(CelestialDefaults.definitions());
        var earth = definitions.get(0);
        definitions.add(new CelestialBodyDefinition(new ResourceLocation("test", "alias"), earth.parentId(),
                earth.levelKey(), earth.gravityMultiplier(), earth.atmosphere(), earth.orbit(), earth.visualProfile()));
        assertTrue(catalogs.applyCandidate(CelestialCatalog.create(definitions)));
        try (var queries = queries(catalogs, new StationRegistrySavedData())) {
            assertTrue(queries.at(Level.OVERWORLD, BlockPos.ZERO).isEmpty());
            assertTrue(queries.at(Level.NETHER, BlockPos.ZERO).isEmpty());
        }
        try (var queries = new ServerEnvironmentQueries(() -> true, ignored -> false,
                catalogs(), new StationRegistrySavedData())) {
            assertTrue(queries.at(Level.OVERWORLD, BlockPos.ZERO).isEmpty());
        }
    }

    @Test
    void offThreadAndExpiredHandlesFailBeforeWorldAccessAndNeverRebind() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicInteger levelChecks = new AtomicInteger();
        var queries = new ServerEnvironmentQueries(() -> Thread.currentThread() == owner,
                key -> { levelChecks.incrementAndGet(); return true; }, catalogs(), new StationRegistrySavedData());
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try { queries.at(Level.OVERWORLD, BlockPos.ZERO); }
            catch (Throwable caught) { failure.set(caught); }
        });
        worker.start();
        worker.join(5_000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertEquals(0, levelChecks.get());
        queries.close();
        queries.close();
        assertThrows(IllegalStateException.class, () -> queries.at(Level.OVERWORLD, BlockPos.ZERO));
        assertEquals(0, levelChecks.get());
        // Directly inspect the sole retained field, without a timing-dependent GC assertion.
        var binding = ServerEnvironmentQueries.class.getDeclaredField("binding");
        binding.setAccessible(true);
        assertNull(binding.get(queries));
        try (var next = queries(catalogs(), new StationRegistrySavedData())) {
            assertTrue(next.at(Level.OVERWORLD, BlockPos.ZERO).isPresent());
            assertThrows(IllegalStateException.class, () -> queries.at(Level.OVERWORLD, BlockPos.ZERO));
        }
    }

    @Test
    void nullInputsFailWithoutStateChanges() {
        try (var queries = queries(catalogs(), new StationRegistrySavedData())) {
            assertThrows(NullPointerException.class, () -> queries.at(null, BlockPos.ZERO));
            assertThrows(NullPointerException.class, () -> queries.at(Level.OVERWORLD, null));
        }
    }

    private static CelestialCatalogManager catalogs() {
        var catalogs = new CelestialCatalogManager();
        assertTrue(catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())));
        return catalogs;
    }

    private static ServerEnvironmentQueries queries(CelestialCatalogManager catalogs, StationRegistrySavedData stations) {
        return new ServerEnvironmentQueries(() -> true, ignored -> true, catalogs, stations);
    }

    private static StationState station(StationRegistrySavedData stations, ResourceLocation body) {
        UUID id = UUID.randomUUID();
        stations.reserve(id, UUID.randomUUID(), "query", body, 0);
        return stations.commit(id);
    }

    private static BlockPos pad(StationState station) {
        return new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
    }

    private static List<CelestialBodyDefinition> changedMoon(double gravity) {
        return CelestialDefaults.definitions().stream().map(body -> body.id().equals(CelestialIds.MOON_ID)
                ? new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), gravity,
                        body.atmosphere(), body.orbit(), body.visualProfile()) : body).toList();
    }
}
