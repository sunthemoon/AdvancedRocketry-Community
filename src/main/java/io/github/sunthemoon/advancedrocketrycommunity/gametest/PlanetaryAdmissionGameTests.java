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
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
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
    /**
     * W122: the closed-orbit departure's own batch. Its launch-and-cancel check compares the whole shared transfer
     * journal across ticks, so another test's launch or cancellation in the same window changes it.
     */
    static final String STATION_DEPARTURE_BATCH = "planetary_admission_station_departure";
    /** W122: the wall-clock bound for loading a station pad chunk's entities in setup, as W111's fixture areas use. */
    private static final long PAD_LOAD_NANOS = 600_000_000_000L;

    private PlanetaryAdmissionGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "planetary_admission", timeoutTicks = 100)
    public static void closedDestinationsRejectWithoutChangingFuelOrSavedAuthority(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        driver.at(1, () -> {
            var catalogs = catalogs(true, false, false);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var player = player(helper.getLevel(), UUID.randomUUID());
            RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), player.getUUID(), rocket -> {
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
        });
    }

    @GameTest(template = "rocket_test", batch = "planetary_admission", timeoutTicks = 100)
    public static void closedMappedSourceCanLaunchAndCancelThroughPlayerAuthority(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        driver.at(1, () -> {
            var catalogs = catalogs(false, true, true);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var owner = player(helper.getLevel(), UUID.randomUUID());
            RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), owner.getUUID(), rocket -> {
                Runnable cleanup = () -> {
                    manager.clear();
                    rocket.discard();
                };
                try {
                    launchAndCancel(helper, driver, manager, rocket, owner, MOON, () -> {
                        cleanup.run();
                        helper.succeed();
                    }, cleanup);
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
        });
    }

    @GameTest(template = "rocket_test", batch = STATION_DEPARTURE_BATCH, timeoutTicks = 100)
    public static void closedOrbitStationCanDepartButStillRequiresLiveMembership(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        driver.at(1, () -> {
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
                holdPadChunk(helper, space, station, stationId, ticketChunk);
                RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), ownerId, cleanup, original -> {
                    try {
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
                        helper.assertTrue(new ChunkPos(rocket.blockPosition()).equals(ticketChunk.get()),
                                "Station rocket is outside its held pad chunk");
                        helper.assertTrue(space.addFreshEntity(rocket), "Station rocket was not installed");
                        awaitVisible(helper, driver, space, rocket, finish -> {
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
                            launchAndCancel(helper, driver, manager, rocket, owner, EARTH, () -> {
                                helper.assertTrue(stations.find(stationId).orElseThrow().orbitBody().equals(CelestialIds.EARTH_ID),
                                        "Departure rewrote the station orbit identity");
                                finish.run();
                            }, cleanup);
                        }, cleanup);
                    } catch (RuntimeException | Error exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
            } catch (RuntimeException | Error exception) {
                cleanup.run();
                throw exception;
            }
        });
    }

    /**
     * ADR-044 §5: a docked rocket moves with its station. After the station's orbit is relocated (the
     * warp commit primitive), the rocket's saved current_body is stale, and launch admission must
     * take the source body from the station record instead.
     */
    @GameTest(template = "rocket_test", batch = "planetary_admission_warp", timeoutTicks = 100)
    public static void dockedRocketDepartsFromTheStationsNewOrbitAfterRelocation(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        driver.at(1, () -> {
            var catalogs = catalogs(true, true, true, List.of(new RouteDefinition(1, ModIdentity.id("test_moon_orbit"),
                    RouteAnchor.bodySurface(CelestialIds.MOON_ID), RouteAnchor.orbit(CelestialIds.MOON_ID), 25, true)));
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
                stations.flush(server);
                if (ticketChunk.get() != null) {
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, ticketChunk.get(), 2, stationId);
                }
            };
            try {
                stations.reserve(stationId, ownerId, "Warped dock fixture", CelestialIds.EARTH_ID,
                        helper.getLevel().getGameTime());
                var station = stations.commit(stationId);
                stations.foldWarpCredits(java.util.Map.of(stationId, 100_000));
                holdPadChunk(helper, space, station, stationId, ticketChunk);
                RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), ownerId, cleanup, original -> {
                    try {
                        try {
                            var old = original.flightData().orElseThrow();
                            var pad = station.landingPad();
                            var origin = new RocketPosition(pad.x(), pad.y(), pad.z());
                            var snapshot = original.snapshot().orElseThrow().relocated(UUID.randomUUID(),
                                    CelestialIds.SPACE_LEVEL.location(), origin, space.getGameTime());
                            // Docked while the station orbited Earth: the saved current_body is Earth.
                            var flight = RocketFlightData.restore(old.schemaVersion(), old.logicalRocketId(), old.state(), old.fuel(),
                                    null, old.passengers(), CelestialIds.EARTH_ID, CelestialIds.SPACE_LEVEL.location(), origin,
                                    space.getGameTime(), null, new TravelTarget.Station(stationId));
                            rocket.initializeTransferred(snapshot, old.logicalRocketId(), ownerId, flight);
                        } finally {
                            original.discard();
                        }
                        helper.assertTrue(new ChunkPos(rocket.blockPosition()).equals(ticketChunk.get()),
                                "Station rocket is outside its held pad chunk");
                        helper.assertTrue(space.addFreshEntity(rocket), "Station rocket was not installed");
                        awaitVisible(helper, driver, space, rocket, finish -> {
                            var owner = player(space, ownerId);
                            owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
                            var committed = stations.checkedRelocation(server, stations.find(stationId).orElseThrow(),
                                    CelestialIds.MOON_ID, 100_000);
                            helper.assertTrue(committed == StationRegistrySavedData.CheckedUpdate.COMMITTED,
                                    "Relocation did not commit: " + committed);
                            helper.assertTrue(rocket.flightData().orElseThrow().currentBody().equals(CelestialIds.EARTH_ID),
                                    "The fixture must carry the stale pre-warp body");
                            launchAndCancel(helper, driver, manager, rocket, owner, MOON, () -> {
                                helper.assertTrue(stations.find(stationId).orElseThrow().orbitBody().equals(CelestialIds.MOON_ID),
                                        "Departure rewrote the relocated orbit");
                                // ADR-044 §5 (WARP review R4): the departure recorded the station's current orbit.
                                helper.assertTrue(rocket.flightData().orElseThrow().currentBody().equals(CelestialIds.MOON_ID),
                                        "The departure kept the stale body: " + rocket.flightData().orElseThrow().currentBody());
                                finish.run();
                            }, cleanup);
                        }, cleanup);
                    } catch (RuntimeException | Error exception) {
                        cleanup.run();
                        throw exception;
                    }
                });
            } catch (RuntimeException | Error exception) {
                cleanup.run();
                throw exception;
            }
        });
    }

    /** ADR-044 §5 (WARP review R2, M12): a rocket built on a station records the station's orbit body. */
    @GameTest(template = "rocket_test", batch = "planetary_admission_station_built", timeoutTicks = 100)
    public static void rocketBuiltOnAStationRecordsItsOrbitBody(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        driver.at(1, () -> {
            var server = helper.getLevel().getServer();
            var space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space is unavailable");
            var stations = StationRegistrySavedData.get(server);
            UUID stationId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            var built = new RocketEntity(ModEntities.ROCKET.get(), space);
            RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), ownerId, original -> {
                try {
                    stations.reserve(stationId, ownerId, "Built-here fixture", CelestialIds.MOON_ID,
                            helper.getLevel().getGameTime());
                    var station = stations.commit(stationId);
                    var pad = station.landingPad();
                    var snapshot = original.snapshot().orElseThrow().relocated(UUID.randomUUID(),
                            CelestialIds.SPACE_LEVEL.location(), new RocketPosition(pad.x(), pad.y(), pad.z()),
                            space.getGameTime());
                    built.initialize(snapshot, UUID.randomUUID(), ownerId);
                    var flight = built.flightData().orElseThrow();
                    helper.assertTrue(flight.currentTarget().filter(new TravelTarget.Station(stationId)::equals).isPresent(),
                            "The rocket is not docked at the station: " + flight.currentTarget());
                    helper.assertTrue(flight.currentBody().equals(CelestialIds.MOON_ID),
                            "A station-built rocket must record the station's orbit, not " + flight.currentBody());
                } finally {
                    built.discard();
                    original.discard();
                    stations.delete(stationId);
                    stations.flush(server);
                }
                helper.succeed();
            });
        });
    }

    /**
     * Runs {@code assertions} once the rocket is visible and its chunk's entities are loaded. They receive the finish step
     * (succeed, then clean up), which W98's deferred cancellation check calls later; a synchronous failure still cleans
     * up here.
     */
    private static void awaitVisible(GameTestHelper helper, GameTestTickDriver driver, ServerLevel level, RocketEntity rocket,
            java.util.function.Consumer<Runnable> assertions, Runnable cleanup) {
        awaitVisible(helper, driver, level, rocket, assertions, cleanup, new GameTestTickPacer(level.getServer()));
    }

    private static void awaitVisible(GameTestHelper helper, GameTestTickDriver driver, ServerLevel level, RocketEntity rocket,
            java.util.function.Consumer<Runnable> assertions, Runnable cleanup, GameTestTickPacer pacer) {
        driver.after(1, () -> {
            boolean scheduled = false;
            try {
                if (!loaded(level, rocket)) {
                    if (helper.getTick() >= 80) {
                        trace(helper, rocket, "expired", null);
                    }
                    // Fail and release our external resources before the enclosing 100-tick timeout.
                    helper.assertTrue(helper.getTick() < 80, "Station fixture entity never became visible");
                    pacer.pace();
                    awaitVisible(helper, driver, level, rocket, assertions, cleanup, pacer);
                    scheduled = true;
                } else {
                    trace(helper, rocket, "visible", null);
                    assertions.accept(() -> {
                        try { helper.succeed(); } finally { cleanup.run(); }
                    });
                    scheduled = true;
                }
            } finally {
                if (!scheduled) {
                    cleanup.run();
                }
            }
        });
    }

    /**
     * W122: holds the station's pad chunk, where the fixture later installs its rocket (the pad origin plus half a block),
     * and loads its entities in setup, outside the timed ticks ({@link EntitySections#loadNow}). The cleanup removes the
     * ticket.
     */
    private static void holdPadChunk(GameTestHelper helper, ServerLevel space, StationState station, UUID stationId,
            AtomicReference<ChunkPos> held) {
        var pad = new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
        held.set(new ChunkPos(pad));
        space.getChunkSource().addRegionTicket(FIXTURE_TICKET, held.get(), 2, stationId);
        space.getChunkAt(pad);
        long started = System.nanoTime();
        boolean loaded = EntitySections.loadNow(space, held.get(), started + PAD_LOAD_NANOS);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_STATION_DEPARTURE stage=pad_loaded level={} test_tick={} chunk={}"
                + " loaded={} ms={}", space.dimension().location(), helper.getTick(), held.get(), loaded,
                (System.nanoTime() - started) / 1_000_000L);
        helper.assertTrue(loaded, "Station pad chunk entities did not load in setup");
    }

    /**
     * W122: the state the launch's acknowledged continuation requires of its entity (the transfer service's loaded-entity
     * check): visible, and in a chunk whose entities are loaded. Vanilla makes a new chunk's sections visible as soon as
     * the chunk is accessible and only then queues its entity storage load on the IO and background threads, so a busy
     * host can show the rocket a tick or more before {@code areEntitiesLoaded} turns true, and the continuation then
     * refuses the departure.
     */
    private static boolean loaded(ServerLevel level, RocketEntity rocket) {
        return level.getEntity(rocket.getId()) == rocket && level.areEntitiesLoaded(rocket.chunkPosition().toLong());
    }

    /**
     * Launches and cancels synchronously, then (W98, N3-A1) waits, bounded, until the cancelled record leaves once the
     * returned rocket's own entity slot is read back; the saved-authority check and {@code then} follow. A failure after
     * the wait began runs {@code failed} once; a synchronous failure is the caller's to clean up, as before.
     */
    private static void launchAndCancel(GameTestHelper helper, GameTestTickDriver driver, RocketManager manager, RocketEntity rocket,
            FakePlayer owner, TravelTarget destination, Runnable then, Runnable failed) {
        owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
        var journal = RocketTransferSavedData.get(helper.getLevel().getServer());
        var before = journal.save(new CompoundTag());
        var fuel = rocket.flightData().orElseThrow().fuel();
        UUID transfer = UUID.randomUUID();
        helper.assertTrue(manager.flightQuotes(owner, rocket).forTarget(destination).canLaunch(), "Departure quote is unavailable");
        try {
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, destination, transfer);
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED
                    && !journal.acknowledgedCurrent(), "Departure changed authority before acknowledgement");
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            trace(helper, rocket, "acknowledged", transfer);
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Authorized closed-source departure did not enter countdown");
            helper.assertTrue(journal.find(transfer).isPresent(), "Accepted departure did not persist its journal");
            manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.CANCEL, destination, UUID.randomUUID());
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Cancellation did not restore fueled state");
            helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(fuel), "Cancelled departure changed fuel");
        } catch (RuntimeException | Error failure) {
            removeOwnRequest(helper, journal, transfer);
            throw failure;
        }
        cancelRetired(helper, driver, new GameTestTickPacer(helper.getLevel().getServer()), journal, before, transfer,
                rocket.assemblyTransactionId().orElseThrow(), helper.getTick() + 40, then, failed);
    }

    private static void cancelRetired(GameTestHelper helper, GameTestTickDriver driver, GameTestTickPacer pacer,
            RocketTransferSavedData journal, CompoundTag before, UUID transfer, UUID logical, long deadline, Runnable then, Runnable failed) {
        try {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport.acknowledgeFixture(helper);
            if ((journal.find(transfer).isPresent() || !journal.actionable(logical)) && helper.getTick() < deadline) {
                pacer.pace();
                driver.after(1, () -> cancelRetired(helper, driver, pacer, journal, before, transfer, logical, deadline, then, failed));
                return;
            }
            helper.assertTrue(journal.save(new CompoundTag()).equals(before), "Cancelled departure changed saved authority");
            then.run();
        } catch (RuntimeException | Error failure) {
            removeOwnRequest(helper, journal, transfer);
            failed.run();
            throw failure;
        }
    }

    /**
     * W122 trace: what the launch's acknowledged continuation reads (its entity check and the journal's per-rocket
     * state) at the fixture's readiness and right after the acknowledgement. Information only; it decides nothing.
     */
    private static void trace(GameTestHelper helper, RocketEntity rocket, String stage, UUID transfer) {
        var level = (ServerLevel) rocket.level();
        var journal = RocketTransferSavedData.get(level.getServer());
        UUID logical = rocket.assemblyTransactionId().orElse(null);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_STATION_DEPARTURE stage={} level={} test_tick={} server_tick={}"
                        + " rocket={} state={} added={} visible={} entities_loaded={} entity_ticking={} record={} actionable={}"
                        + " acknowledged={} outstanding={} entries={}",
                stage, level.dimension().location(), helper.getTick(), level.getServer().getTickCount(), rocket.getUUID(),
                rocket.flightData().map(flight -> flight.state().name()).orElse("none"), rocket.isAddedToWorld(),
                level.getEntity(rocket.getUUID()) == rocket, level.areEntitiesLoaded(rocket.chunkPosition().toLong()),
                level.isPositionEntityTicking(rocket.blockPosition()), transfer != null && journal.find(transfer).isPresent(),
                logical != null && journal.actionable(logical), journal.acknowledgedCurrent(), journal.outstandingEffectCount(),
                journal.entries().size());
    }

    /** Only this test's request may be removed if an assertion fails. */
    private static void removeOwnRequest(GameTestHelper helper, RocketTransferSavedData journal, UUID transfer) {
        if (journal.find(transfer).isPresent()) {
            journal.remove(transfer);
            journal.flush(helper.getLevel().getServer());
        }
    }

    private static FakePlayer player(ServerLevel level, UUID id) {
        return new FakePlayer(level, new GameProfile(id, "PlanetaryFixture"));
    }

    private static PlanetaryCatalogManager catalogs(boolean earthLandable, boolean moonLandable, boolean earthOrbitable) {
        return catalogs(earthLandable, moonLandable, earthOrbitable, List.of());
    }

    private static PlanetaryCatalogManager catalogs(boolean earthLandable, boolean moonLandable, boolean earthOrbitable,
                                                    List<RouteDefinition> extraRoutes) {
        var definitions = new ArrayList<>(CelestialDefaults.definitions());
        for (int index = 0; index < 2; index++) {
            var body = definitions.get(index);
            definitions.set(index, new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(),
                    body.gravityMultiplier(), body.atmosphere(), body.orbit(), body.visualProfile(),
                    new CelestialCapabilities(index == 0 ? earthLandable : moonLandable, index != 0 || earthOrbitable, false), 1, 0));
        }
        var manager = new PlanetaryCatalogManager();
        var routes = new ArrayList<>(List.of(
                new RouteDefinition(1, ModIdentity.id("test_surface"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.bodySurface(CelestialIds.MOON_ID), 50, true),
                new RouteDefinition(1, ModIdentity.id("test_orbit"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                        RouteAnchor.orbit(CelestialIds.EARTH_ID), 25, true)));
        routes.addAll(extraRoutes);
        if (!manager.applyCandidate(CelestialCatalog.create(definitions).flatMap(CelestialCatalog::requireFixedBaseline)
                .flatMap(bodies -> RouteCatalog.create(routes, definitions.stream().map(CelestialBodyDefinition::id).toList())
                        .flatMap(graph -> PlanetaryCatalog.create(bodies, graph))))) {
            throw new IllegalStateException("Invalid admission fixture catalogs");
        }
        return manager;
    }
}
