package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real mission/menu/launch authority; network-free actors are not V2 evidence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryDiscoveryGameTests {
    private static final TravelTarget MARS = new TravelTarget.BodySurface(PlanetaryContent.MARS);
    private PlanetaryDiscoveryGameTests() { }

    @GameTest(template = "rocket_test", batch = "planetary_discovery", timeoutTicks = 280)
    public static void researchUnlocksSharedTravelButNotAnotherPlayersBalanceOrStation(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var server = helper.getLevel().getServer();
            var catalogs = new PlanetaryCatalogManager();
            helper.assertTrue(catalogs.applyCandidate(pair(true, false)), "Discovery catalog rejected");
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var owner = actor(helper, "ResearchOwner"); var other = actor(helper, "ResearchOther");
            var ownedRockets = new ArrayList<io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity>(2);
            var progress = CelestialSavedData.get(server);
            var missions = SatelliteMissionSavedData.get(server);
            var stations = StationRegistrySavedData.get(server);
            UUID station = UUID.randomUUID();
            Runnable cleanup = () -> {
                owner.containerMenu = owner.inventoryMenu; other.containerMenu = other.inventoryMenu;
                stations.delete(station); stations.release(station); manager.clear();
                ownedRockets.forEach(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity::discard);
            };
            try {
                var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner.getUUID());
                ownedRockets.add(rocket);
                var secondRocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(9, 2, 3), other.getUUID());
                ownedRockets.add(secondRocket);
                owner.setPos(rocket.position()); other.setPos(secondRocket.position());
                stations.reserve(station, owner.getUUID(), "Research orbit", PlanetaryContent.MARS, server.overworld().getGameTime());
                stations.commit(station);
                var stationTarget = new TravelTarget.Station(station);
                var before = rocket.flightData().orElseThrow();
                var saved = progress.save(new CompoundTag());
                var journal = RocketTransferSavedData.get(server).save(new CompoundTag());
                var stationBefore = stations.save(new CompoundTag());
                var chunks = chunks(helper);
                var locked = manager.navigation(owner, rocket);
                helper.assertTrue(locked.quotes().forTarget(MARS).status() == RocketNavigationStatus.DISCOVERY_REQUIRED
                        && locked.quotes().forTarget(MARS).requiredFuel() == 0, "Undiscovered surface offered travel");
                helper.assertTrue(locked.quotes().forTarget(stationTarget).status() == RocketNavigationStatus.DISCOVERY_REQUIRED,
                        "Owned station bypassed orbit discovery");
                manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, MARS, UUID.randomUUID());
                helper.assertTrue(before.equals(rocket.flightData().orElseThrow()), "Player intent bypassed discovery");
                helper.assertTrue(manager.requestAdminFlight(rocket, stationTarget, UUID.randomUUID()).code()
                        == RocketFlightRequestCode.DISCOVERY_REQUIRED, "Operator flight bypassed discovery");
                helper.assertTrue(saved.equals(progress.save(new CompoundTag())) && before.equals(rocket.flightData().orElseThrow())
                        && journal.equals(RocketTransferSavedData.get(server).save(new CompoundTag()))
                        && stationBefore.equals(stations.save(new CompoundTag())) && chunks.equals(chunks(helper)),
                        "Denied planning mutated authority/resources or loaded chunks");
                helper.assertTrue(catalogs.applyCandidate(pair(false, true)), "Reload candidate rejected");
                var unrestricted = manager.navigation(owner, rocket);
                helper.assertTrue(unrestricted.quotes().forTarget(MARS).canLaunch(), "Undiscovered source could not evacuate");
                helper.assertTrue(unrestricted.quotes().forTarget(new TravelTarget.BodySurface(CelestialIds.EARTH_ID)).status()
                        == RocketNavigationStatus.CURRENT, "Undiscovered source lost CURRENT status");
                helper.assertTrue(catalogs.applyCandidate(pair(true, false)), "Discovery reload rejected");
                helper.assertTrue(manager.requestAdminFlight(rocket, MARS, UUID.randomUUID()).code()
                        == RocketFlightRequestCode.DISCOVERY_REQUIRED, "Stale ready quote authorized launch");
                var menu = new RocketFlightMenu(61, owner.getInventory(), rocket);
                owner.containerMenu = menu;
                helper.assertTrue(menu.quotes().forTarget(MARS).status() == RocketNavigationStatus.DISCOVERY_REQUIRED,
                        "Open console missed discovery lock");
                var identity = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), SatelliteIds.DATA_SATELLITE);
                var capacityIdentity = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), SatelliteIds.DATA_SATELLITE);
                helper.assertTrue(SatelliteRuntime.launch(owner, identity, PlanetaryContent.MARS).success(), "Mars research did not start");
                helper.assertTrue(SatelliteRuntime.launch(owner, capacityIdentity, PlanetaryContent.VENUS).success(), "Venus research did not start");
                helper.assertTrue(SatelliteRuntime.claim(other, identity).code() == SatelliteOperationCode.UNAUTHORIZED,
                        "Other player claimed private mission");
                helper.runAfterDelay(230, () -> {
                    try {
                        helper.assertTrue(menu.quotes().forTarget(MARS).status() == RocketNavigationStatus.DISCOVERY_REQUIRED,
                                "Pre-claim menu refresh lost discovery lock");
                        helper.assertTrue(SatelliteRuntime.claim(owner, identity).success(), "Owner research claim failed");
                        helper.assertTrue(missions.account(owner.getUUID()).balance() == 20 && missions.account(other.getUUID()).balance() == 0,
                                "Shared discovery transferred private research");
                        helper.assertTrue(!menu.quotes().forTarget(MARS).canLaunch(), "Menu did not retain its bounded refresh interval");
                        helper.runAfterDelay(21, () -> {
                            try {
                                helper.assertTrue(menu.quotes().forTarget(MARS).canLaunch()
                                        && manager.navigation(other, secondRocket).quotes().forTarget(MARS).canLaunch(),
                                        "Discovery did not unlock both players' navigation");
                                helper.assertTrue(manager.navigation(other, secondRocket).stations().stream().noneMatch(s -> s.stationId().equals(station)),
                                        "Discovery granted private station access");
                                SatelliteRuntime.claim(owner, identity);
                                helper.assertTrue(missions.account(owner.getUUID()).balance() == 20, "Repeated claim duplicated research");
                                for (int i = 0; i < 127; i++) { progress.discover(ModIdentity.id("removed_fixture_" + i), server.overworld().getGameTime()); }
                                var full = progress.save(new CompoundTag());
                                for (int i = 0; i < 3; i++) {
                                    helper.assertTrue(SatelliteRuntime.claim(owner, capacityIdentity).code() == SatelliteOperationCode.PENDING_DISCOVERY,
                                            "Full retained ledger was not reported pending");
                                }
                                helper.assertTrue(missions.account(owner.getUUID()).balance() == 40 && full.equals(progress.save(new CompoundTag()))
                                        && SatelliteRuntime.currentMission(server, capacityIdentity).orElseThrow().status() == MissionStatus.CLAIM_PENDING_DISCOVERY,
                                        "Capacity retry repeated payment, evicted history or completed falsely");
                                helper.assertTrue(manager.navigation(owner, rocket).quotes().forTarget(new TravelTarget.BodySurface(PlanetaryContent.VENUS))
                                        .status() == RocketNavigationStatus.DISCOVERY_REQUIRED, "Pending discovery unlocked Venus");
                                helper.succeed();
                            } finally { cleanup.run(); }
                        });
                    } catch (RuntimeException | Error exception) { cleanup.run(); throw exception; }
                });
            } catch (RuntimeException | Error exception) { cleanup.run(); throw exception; }
        });
    }

    private static FakePlayer actor(GameTestHelper helper, String name) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static java.util.Map<String, Integer> chunks(GameTestHelper helper) {
        var result = new java.util.TreeMap<String, Integer>();
        for (var level : helper.getLevel().getServer().getAllLevels()) {
            result.put(level.dimension().location().toString(), level.getChunkSource().getLoadedChunksCount());
        }
        return result;
    }

    private static com.mojang.serialization.DataResult<PlanetaryCatalog> pair(boolean marsRequired, boolean earthRequired) {
        var bodies = new ArrayList<CelestialBodyDefinition>();
        var inputs = new ArrayList<>(CelestialDefaults.definitions()); inputs.addAll(PlanetaryContent.definitions());
        for (var body : inputs) {
            boolean required = body.id().equals(PlanetaryContent.MARS) ? marsRequired
                    : body.id().equals(CelestialIds.EARTH_ID) ? earthRequired : body.discoveryRequired();
            bodies.add(new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), body.gravityMultiplier(), body.atmosphere(),
                    body.orbit(), body.visualProfile(), body.capabilities(), body.solarIntensity(), body.radiation(), body.environmentEffects(), required));
        }
        return CelestialCatalog.create(bodies).flatMap(c -> RouteCatalog.create(PlanetaryContent.routes(), bodies.stream()
                .map(CelestialBodyDefinition::id).toList()).flatMap(routes -> PlanetaryCatalog.create(c, routes)));
    }
}
