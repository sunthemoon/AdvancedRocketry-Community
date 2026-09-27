package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Live authority checks with private catalogs; never replaces the server runtime. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryAdmissionGameTests {
    private static final TravelTarget EARTH = new TravelTarget.BodySurface(CelestialIds.EARTH_ID);
    private static final TravelTarget MOON = new TravelTarget.BodySurface(CelestialIds.MOON_ID);
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_planetary", Comparator.comparing(UUID::toString), 100);

    private PlanetaryAdmissionGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "planetary_admission", timeoutTicks = 100)
    public static void closedDestinationsRejectWithoutChangingFuelOrSavedAuthority(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            var catalogs = catalogs(true, false, false);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var player = player(helper.getLevel(), UUID.randomUUID());
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), player.getUUID());
            player.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
            var server = helper.getLevel().getServer();
            var stations = StationRegistrySavedData.get(server);
            UUID stationId = UUID.randomUUID();
            try {
                stations.reserve(stationId, player.getUUID(), "Admission fixture", CelestialIds.EARTH_ID,
                        helper.getLevel().getGameTime());
                stations.commit(stationId);
                var stationTarget = new TravelTarget.Station(stationId);
                var registryBefore = stations.save(new CompoundTag());
                var journal = RocketTransferSavedData.get(server);
                var journalBefore = journal.save(new CompoundTag());
                var flightBefore = rocket.flightData().orElseThrow();
                for (TravelTarget target : List.of(MOON, stationTarget)) {
                    helper.assertTrue(!manager.flightQuotes(player, rocket).forTarget(target).canLaunch(),
                            "Closed destination has a launchable quote");
                    var denied = manager.requestAdminFlight(rocket, target, UUID.randomUUID());
                    helper.assertTrue(denied.code() == RocketFlightRequestCode.INVALID_DESTINATION,
                            "Closed destination was not rejected by capability: " + denied.code());
                    manager.requestFlightIntent(player, rocket.getId(), RocketFlightAction.LAUNCH, target, UUID.randomUUID());
                    helper.assertTrue(rocket.flightData().orElseThrow().equals(flightBefore), "Denied arrival changed flight/fuel");
                    helper.assertTrue(journal.save(new CompoundTag()).equals(journalBefore), "Denied arrival changed journal");
                }
                var stationManager = new StationManager(catalogs.celestialView());
                helper.assertTrue(stationManager.createForPlayer(player).code() == StationCreationCode.UNKNOWN_ORBIT_BODY,
                        "Closed orbit accepted player station creation");
                helper.assertTrue(stationManager.createForOperator(server, player.getUUID(), "Denied", CelestialIds.EARTH_ID)
                        .code() == StationCreationCode.UNKNOWN_ORBIT_BODY, "Closed orbit accepted operator station creation");
                helper.assertTrue(stations.save(new CompoundTag()).equals(registryBefore),
                        "Denied creation changed reservations or an existing station");
                // Clearing configured services must not restore the legacy enum fallback.
                catalogs.clear();
                helper.assertTrue(manager.flightQuotes(player, rocket).entries().isEmpty(), "Unready catalog offered quotes");
                helper.assertTrue(manager.requestAdminFlight(rocket, MOON, UUID.randomUUID()).code()
                        == RocketFlightRequestCode.INVALID_DESTINATION, "Unready catalog allowed launch");
                helper.assertTrue(journal.save(new CompoundTag()).equals(journalBefore), "Unready launch changed journal");
            } finally {
                manager.clear();
                rocket.discard();
                stations.release(stationId);
                stations.delete(stationId);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "planetary_admission", timeoutTicks = 100)
    public static void closedMappedSourceCanLaunchAndCancelThroughPlayerAuthority(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            var catalogs = catalogs(false, true, true);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var owner = player(helper.getLevel(), UUID.randomUUID());
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner.getUUID());
            try {
                launchAndCancel(helper, manager, rocket, owner, MOON);
            } finally {
                manager.clear();
                rocket.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "planetary_admission", timeoutTicks = 100)
    public static void closedOrbitStationCanDepartButStillRequiresLiveMembership(GameTestHelper helper) {
        helper.runAtTickTime(1, () -> {
            var catalogs = catalogs(true, true, false);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var server = helper.getLevel().getServer();
            var space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space is unavailable");
            UUID ownerId = UUID.randomUUID();
            var stations = StationRegistrySavedData.get(server);
            UUID stationId = UUID.randomUUID();
            var rocket = new RocketEntity(ModEntities.ROCKET.get(), space);
            var ticketChunk = new AtomicReference<ChunkPos>();
            Runnable cleanup = () -> {
                manager.clear();
                rocket.discard();
                stations.release(stationId);
                stations.delete(stationId);
                if (ticketChunk.get() != null) {
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, ticketChunk.get(), 2, stationId);
                }
            };
            try {
                stations.reserve(stationId, ownerId, "Closed orbit fixture", CelestialIds.EARTH_ID,
                        helper.getLevel().getGameTime());
                var station = stations.commit(stationId);
                var original = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), ownerId);
                try {
                    var old = original.flightData().orElseThrow();
                    var pad = station.landingPad();
                    var origin = new RocketPosition(pad.x(), pad.y(), pad.z());
                    var snapshot = original.snapshot().orElseThrow().relocated(UUID.randomUUID(),
                            CelestialIds.SPACE_LEVEL.location(), origin, space.getGameTime());
                    // An already-saved station rocket fixture; no new arrival through a closed orbit.
                    var flight = RocketFlightData.restore(old.schemaVersion(), old.logicalRocketId(), old.state(), old.fuel(),
                            null, old.passengers(), CelestialIds.EARTH_ID, CelestialIds.SPACE_LEVEL.location(), origin,
                            space.getGameTime(), null, new TravelTarget.Station(stationId));
                    rocket.initializeTransferred(snapshot, old.logicalRocketId(), ownerId, flight);
                } finally {
                    original.discard();
                }
                ticketChunk.set(new ChunkPos(rocket.blockPosition()));
                space.getChunkSource().addRegionTicket(FIXTURE_TICKET, ticketChunk.get(), 2, stationId);
                space.getChunkAt(rocket.blockPosition());
                helper.assertTrue(space.addFreshEntity(rocket), "Station rocket was not installed");
                awaitVisible(helper, space, rocket, () -> {
                    var owner = player(space, ownerId);
                    owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
                    // Transferring ownership retains the old owner as a member; explicitly revoke both.
                    stations.transferOwnership(stationId, UUID.randomUUID());
                    stations.removeMember(stationId, ownerId);
                    var before = RocketTransferSavedData.get(server).save(new CompoundTag());
                    var flightBefore = rocket.flightData().orElseThrow();
                    helper.assertTrue(manager.navigation(owner, rocket).quotes().forTarget(EARTH).status()
                            == io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus.UNAUTHORIZED,
                            "Source station access denial advertised a ready departure");
                    manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, EARTH, UUID.randomUUID());
                    helper.assertTrue(rocket.flightData().orElseThrow().equals(flightBefore), "Station access denial changed flight");
                    helper.assertTrue(RocketTransferSavedData.get(server).save(new CompoundTag()).equals(before),
                            "Station access denial changed journal");
                    stations.transferOwnership(stationId, ownerId);
                    launchAndCancel(helper, manager, rocket, owner, EARTH);
                    helper.assertTrue(stations.find(stationId).orElseThrow().orbitBody().equals(CelestialIds.EARTH_ID),
                            "Departure rewrote the station orbit identity");
                }, cleanup);
            } catch (RuntimeException | Error exception) {
                cleanup.run();
                throw exception;
            }
        });
    }

    private static void awaitVisible(GameTestHelper helper, ServerLevel level, RocketEntity rocket,
            Runnable assertions, Runnable cleanup) {
        helper.runAfterDelay(1, () -> {
            boolean scheduled = false;
            try {
                if (level.getEntity(rocket.getId()) != rocket) {
                    // Fail and release our external resources before the enclosing 100-tick timeout.
                    helper.assertTrue(helper.getTick() < 80, "Station fixture entity never became visible");
                    awaitVisible(helper, level, rocket, assertions, cleanup);
                    scheduled = true;
                } else {
                    assertions.run();
                    helper.succeed();
                }
            } finally {
                if (!scheduled) {
                    cleanup.run();
                }
            }
        });
    }

    private static void launchAndCancel(GameTestHelper helper, RocketManager manager, RocketEntity rocket,
            FakePlayer owner, TravelTarget destination) {
        owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
        var journal = RocketTransferSavedData.get(helper.getLevel().getServer());
        var before = journal.save(new CompoundTag());
        var fuel = rocket.flightData().orElseThrow().fuel();
        UUID transfer = UUID.randomUUID();
        helper.assertTrue(manager.flightQuotes(owner, rocket).forTarget(destination).canLaunch(), "Departure quote is unavailable");
        try {
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, destination, transfer);
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Authorized closed-source departure did not enter countdown");
            helper.assertTrue(journal.find(transfer).isPresent(), "Accepted departure did not persist its journal");
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.CANCEL, destination, UUID.randomUUID());
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Cancellation did not restore fueled state");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(fuel), "Cancelled departure changed fuel");
            helper.assertTrue(journal.save(new CompoundTag()).equals(before), "Cancelled departure changed saved authority");
        } finally {
            // Only this test's request may be removed if an assertion fails.
            if (journal.find(transfer).isPresent()) {
                journal.remove(transfer);
                journal.flush(helper.getLevel().getServer());
            }
        }
    }

    private static FakePlayer player(ServerLevel level, UUID id) {
        return new FakePlayer(level, new GameProfile(id, "PlanetaryFixture"));
    }

    private static PlanetaryCatalogManager catalogs(boolean earthLandable, boolean moonLandable, boolean earthOrbitable) {
        var definitions = new ArrayList<>(CelestialDefaults.definitions());
        for (int index = 0; index < 2; index++) {
            var body = definitions.get(index);
            definitions.set(index, new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(),
                    body.gravityMultiplier(), body.atmosphere(), body.orbit(), body.visualProfile(),
                    new CelestialCapabilities(index == 0 ? earthLandable : moonLandable, index != 0 || earthOrbitable, false), 1, 0));
        }
        var manager = new PlanetaryCatalogManager();
        var routes = List.of(
                new RouteDefinition(1, ModIdentity.id("test_surface"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.bodySurface(CelestialIds.MOON_ID), 50, true),
                new RouteDefinition(1, ModIdentity.id("test_orbit"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.orbit(CelestialIds.EARTH_ID), 25, true));
        if (!manager.applyCandidate(CelestialCatalog.create(definitions).flatMap(CelestialCatalog::requireFixedBaseline)
                .flatMap(bodies -> RouteCatalog.create(routes, definitions.stream().map(CelestialBodyDefinition::id).toList())
                        .flatMap(graph -> PlanetaryCatalog.create(bodies, graph))))) {
            throw new IllegalStateException("Invalid admission fixture catalogs");
        }
        return manager;
    }
}
