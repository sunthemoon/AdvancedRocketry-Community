package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 F5.5: a real Space source, station resolver, checked orbit relocation and both fresh registration branches. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayStationRowGameTests {
    private BayStationRowGameTests() { }

    @GameTest(template = TEMPLATE, batch = "w333_station_rows", timeoutTicks = 1200)
    public static void capturedOrbitSurvivesLaterStationChangeForBothKinds(GameTestHelper h) {
        var fixture = new Fixture(h);
        h.onEachTick(() -> {
            try { if (fixture.tick()) { fixture.close(); h.succeed(); } }
            catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final net.minecraft.server.MinecraftServer server;
        final ServerLevel space;
        final SatelliteMissionSavedData original, data;
        final StationRegistrySavedData originalStations, stations = new StationRegistrySavedData();
        final UUID owner = UUID.randomUUID(), stationId = UUID.randomUUID();
        final List<ItemStack> packages;
        final StationState station;
        final BlockPos origin;
        final Set<Long> forced = new HashSet<>();
        final Set<UUID> observed = new HashSet<>();
        final StationRegionBodyContextResolver contexts;
        Flight flight;
        BayClaim claim;
        int stage, waits;
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h; server = h.getLevel().getServer(); space = server.getLevel(CelestialIds.SPACE_LEVEL);
            h.assertTrue(space != null, "Station fixture has no Space Level");
            original = SatelliteMissionSavedData.get(server); original.awaitForTests(server);
            originalStations = StationRegistrySavedData.get(server);
            // Establish ownership of an initially absent root before the detached fixture writes that file.
            // Otherwise restoring the original session correctly rejects the fixture's file as an external arrival.
            originalStations.setDirty(); originalStations.flush(server);
            h.assertTrue(!originalStations.isDirty(), "Original station root was not saved before fixture replacement");
            data = SatelliteMissionSavedData.create(server.overworld().getGameTime());
            server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, data);
            SatelliteMissionSavedData.get(server);
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, stations);
            stations.reserve(stationId, owner, "W333 captured orbit", CelestialIds.EARTH_ID, 0);
            station = stations.commit(stationId);
            origin = new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
            contexts = new StationRegionBodyContextResolver(stations::findAt);
            packages = List.of(dataPackage(owner), surveyPackage(owner));
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    int cx = (origin.getX() >> 4) + x, cz = (origin.getZ() >> 4) + z;
                    long key = ChunkPos.asLong(cx, cz);
                    if (!space.getForcedChunks().contains(key)) { space.setChunkForced(cx, cz, true); forced.add(key); }
                }
            }
        }

        boolean tick() {
            h.assertTrue(++waits < 1000, "Station matrix exceeded bounded owner ticks: " + stage);
            if (stage == 0) {
                if (!space.isPositionEntityTicking(origin) || !storeIdle(server)) { return false; }
                var captured = contexts.resolve(new WorldLocation(CelestialIds.SPACE_LEVEL, origin)).context().orElseThrow();
                h.assertTrue(captured.bodyId().equals(CelestialIds.EARTH_ID), "Station did not resolve its initial orbit");
                flight = transitFromStation(captured.bodyId());
                h.assertTrue(!flight.record().sourceFlightData().plan().orElseThrow().destinationBody().equals(captured.bodyId()),
                        "Station orbit fixture equals its destination");
                stations.foldWarpCredits(Map.of(stationId, 10));
                h.assertTrue(stations.checkedRelocation(server, station, ModIdentity.id("mars"), 1)
                        == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Station orbit change was not checked/durable");
                h.assertTrue(contexts.resolve(new WorldLocation(CelestialIds.SPACE_LEVEL, origin)).context().orElseThrow().bodyId()
                        .equals(ModIdentity.id("mars")), "Station resolver did not change after capture");
                var journal = RocketTransferSavedData.get(server); journal.put(flight.record());
                bays(h).ascentComplete(server, journal, flight.record(), flight.source());
                claim = claim(h, flight.record().logicalRocketId()).orElseThrow(); stage = 1;
            }
            for (var pkg : packages) {
                bays(h).sessionForTests().find(satellite(pkg)).ifPresent(receipt -> {
                    h.assertTrue(receipt.orbit().equals(CelestialIds.EARTH_ID.toString()), "Receipt followed station's new orbit/destination");
                    observed.add(receipt.satellite());
                });
            }
            if (claim(h, flight.record().logicalRocketId()).isPresent()) { return false; }
            h.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "Station launch held/refused a branch: " + claim.reason());
            h.assertTrue(observed.size() == 2, "Did not observe both captured-body receipts");
            var initial = data.mission(SatelliteManager.initialMissionId(satellite(packages.get(0)))).orElseThrow();
            h.assertTrue(initial.targetBodyId().equals(CelestialIds.EARTH_ID)
                    && data.satellite(satellite(packages.get(1))).orElseThrow().orbitBody().orElseThrow().equals(CelestialIds.EARTH_ID),
                    "Fresh DATA/non-DATA registration did not use the captured station orbit");
            var record = RocketTransferSavedData.get(server).find(flight.record().transferId()).orElseThrow();
            h.assertTrue(consumedOps(record.sourceSnapshot()).containsAll(claim.group().orElseThrow().operations())
                    && consumedOps(record.destinationSnapshot()).containsAll(claim.group().orElseThrow().operations()),
                    "Both station packages were not consumed by their own operation");
            return true;
        }

        Flight transitFromStation(net.minecraft.resources.ResourceLocation captured) {
            long now = server.overworld().getGameTime();
            var blocks = new ArrayList<RocketBlock>();
            blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:iron_block")));
            for (int i = 0; i < packages.size(); i++) {
                blocks.add(new RocketBlock(new RocketPosition(0, i + 1, 0), state("advancedrocketrycommunity:rocket_satellite_bay"),
                        new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, root(owner, packages.get(i)))));
            }
            var stats = new RocketStats(3, 100, 1000, 1000, 1, 1, 1, 2);
            var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
            var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), space.dimension().location(), position(origin), blocks,
                    List.of(new RocketPosition(0, 0, 0)), stats, now, propulsion);
            var target = position(h.absolutePos(new BlockPos(14, 2, 3)));
            var destination = snapshot.relocated(UUID.randomUUID(), h.getLevel().dimension().location(), target, now);
            UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID();
            var fuel = RocketFuelState.empty(propulsion).fill(1000).state();
            var plan = new RocketFlightPlan(3, transfer, captured, CelestialIds.MOON_ID, space.dimension().location(),
                    h.getLevel().dimension().location(), 500, now);
            var idle = RocketFlightData.initial(logical, 1000, 1, captured, space.dimension().location(), position(origin), now,
                    new TravelTarget.Station(stationId));
            var outbound = idle.withFuel(fuel, now).withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = outbound.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), target, now);
            var source = ModEntities.ROCKET.get().create(space); h.assertTrue(source != null, "No station source entity");
            var record = RocketTransferRecord.create(transfer, logical, owner, source.getUUID(), snapshot, destination, outbound, arrival, 500, now);
            source.initializeTransferred(snapshot, logical, owner, outbound); RocketTransferEntities.positionAtAltitude(source, snapshot.sourceOrigin());
            h.assertTrue(space.addFreshEntity(source), "Station source entity was not published");
            return new Flight(record, source);
        }

        @Override public void close() {
            if (closed) { return; } closed = true;
            if (flight != null) {
                RocketTransferEntities.releaseFlightTickets(server, flight.record());
                flight.source().discard(); bays(h).forgetForTests(flight.record().logicalRocketId());
                var journal = RocketTransferSavedData.get(server);
                journal.find(flight.record().transferId()).flatMap(RocketTransferRecord::destinationEntityId)
                        .map(h.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                journal.remove(flight.record().transferId());
                packages.forEach(pkg -> bays(h).sessionForTests().find(satellite(pkg)).ifPresent(row -> BayReceiptTestHooks.remove(server, row.op(), o -> { })));
            }
            forced.forEach(key -> space.setChunkForced(ChunkPos.getX(key), ChunkPos.getZ(key), false));
            data.close();
            server.overworld().getDataStorage().set(SatelliteMissionSavedData.DATA_NAME, original); original.setDirty(); original.flush(server);
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, originalStations); originalStations.setDirty(); originalStations.flush(server);
        }
    }
}
