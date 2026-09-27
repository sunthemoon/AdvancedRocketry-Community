package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real server/menu services with network-free actors; these are not rendered-client tests. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketNavigationGameTests {
    private static final TravelTarget MARS = new TravelTarget.BodySurface(PlanetaryContent.MARS);
    private RocketNavigationGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_navigation", timeoutTicks = 40)
    public static void navigationUsesAuthorityOneGenerationAndReadOnlyPlanning(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var catalogs = new PlanetaryCatalogManager();
            helper.assertTrue(catalogs.applyCandidate(pair(false)), "Catalog fixture rejected");
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var owner = actor(helper, "NavOwner");
            var outsider = actor(helper, "NavOutsider");
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner.getUUID());
            var stations = StationRegistrySavedData.get(helper.getLevel().getServer());
            UUID station = UUID.randomUUID();
            try {
                owner.setPos(rocket.position()); outsider.setPos(rocket.position());
                stations.reserve(station, UUID.randomUUID(), "Navigation Mars", PlanetaryContent.MARS, helper.getLevel().getGameTime());
                stations.commit(station); stations.addMember(station, owner.getUUID());
                var before = rocket.flightData().orElseThrow();
                var journal = RocketTransferSavedData.get(helper.getLevel().getServer()).save(new CompoundTag());
                var stationBefore = stations.save(new CompoundTag());
                var chunks = chunks(helper);
                var navigation = manager.navigation(owner, rocket);
                helper.assertTrue(navigation.generation() == catalogs.capture().orElseThrow().generation(), "Quote generation was not captured");
                helper.assertTrue(navigation.quotes().forTarget(MARS).status() == RocketNavigationStatus.READY, "Owned Mars route not ready");
                helper.assertTrue(navigation.quotes().forTarget(new TravelTarget.BodySurface(CelestialIds.EARTH_ID)).status()
                        == RocketNavigationStatus.CURRENT, "Current body status incorrect");
                helper.assertTrue(navigation.stations().size() == 1 && navigation.stations().get(0).stationId().equals(station)
                        && navigation.stations().get(0).orbitBody().equals(PlanetaryContent.MARS), "Accessible station summary missing");
                helper.assertTrue(navigation.quotes().entries().stream().noneMatch(q -> q.target().equals(
                        new TravelTarget.BodySurface(PlanetaryContent.GAS_GIANT))), "Gas giant acquired a surface quote");
                var denied = manager.navigation(outsider, rocket);
                helper.assertTrue(denied.stations().isEmpty() && denied.quotes().entries().stream()
                        .allMatch(q -> q.quote().status() == RocketNavigationStatus.UNAUTHORIZED), "Private station or control permission leaked");
                for (int i = 0; i < 20; i++) { manager.navigation(owner, rocket); }
                helper.assertTrue(chunks.equals(chunks(helper)), "Navigation loaded chunks");
                helper.assertTrue(before.equals(rocket.flightData().orElseThrow()) && stationBefore.equals(stations.save(new CompoundTag()))
                        && journal.equals(RocketTransferSavedData.get(helper.getLevel().getServer()).save(new CompoundTag())), "Read-only navigation mutated authority");
                rocket.updateFlightData(before.withFuel(RocketFuelState.restore(before.fuel().capacity(), 1, List.of()), helper.getLevel().getGameTime()));
                helper.assertTrue(manager.navigation(owner, rocket).quotes().forTarget(MARS).status() == RocketNavigationStatus.INSUFFICIENT_FUEL,
                        "Insufficient fuel status not explained");
                rocket.updateFlightData(before);
                helper.assertTrue(catalogs.applyCandidate(pair(true)), "Closed Mars candidate rejected");
                var closed = manager.navigation(owner, rocket);
                helper.assertTrue(closed.generation() > navigation.generation() && closed.stations().isEmpty()
                        && !closed.quotes().forTarget(MARS).canLaunch(), "Reload mixed generations or retained closed arrivals");
                manager.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, MARS, UUID.randomUUID());
                helper.assertTrue(before.equals(rocket.flightData().orElseThrow()), "Stale quote launched after catalog changed");
                helper.succeed();
            } finally { stations.delete(station); stations.release(station); manager.clear(); rocket.discard(); }
        });
    }

    @GameTest(template = "rocket_test", batch = "rocket_navigation_menu", timeoutTicks = 60)
    public static void menuRefreshesRevokedStationsAndChangedFuelWithoutReopening(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var owner = actor(helper, "NavMenu");
            var other = actor(helper, "NavWrongViewer");
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner.getUUID());
            var stations = StationRegistrySavedData.get(helper.getLevel().getServer());
            UUID station = UUID.randomUUID();
            Runnable cleanup = () -> { owner.containerMenu = owner.inventoryMenu; stations.delete(station); stations.release(station); rocket.discard(); };
            try {
                owner.setPos(rocket.position()); other.setPos(rocket.position());
                stations.reserve(station, UUID.randomUUID(), "Menu member", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
                stations.commit(station); stations.addMember(station, owner.getUUID());
                var menu = new RocketFlightMenu(31, owner.getInventory(), rocket);
                owner.containerMenu = menu;
                helper.assertTrue(menu.clickMenuButton(owner, 0) && !menu.clickMenuButton(owner, 1)
                        && !menu.clickMenuButton(other, 0), "Refresh intent viewer/ID validation failed");
                var original = rocket.flightData().orElseThrow();
                var before = menu.navigation();
                helper.assertTrue(before.stations().size() == 1, "Menu did not list its accessible station");
                rocket.updateFlightData(original.withFuel(RocketFuelState.restore(original.fuel().capacity(), 1, List.of()), helper.getLevel().getGameTime()));
                helper.assertTrue(menu.quotes().forTarget(MARS).status() == RocketNavigationStatus.INSUFFICIENT_FUEL,
                        "Changed flight data waited for periodic refresh");
                rocket.updateFlightData(original);
                helper.assertTrue(menu.quotes().forTarget(MARS).canLaunch(), "Restored fuel did not refresh immediately");
                stations.removeMember(station, owner.getUUID());
                RocketRuntime.requestFlightIntent(owner, rocket.getId(), RocketFlightAction.LAUNCH, new TravelTarget.Station(station), UUID.randomUUID());
                helper.assertTrue(original.equals(rocket.flightData().orElseThrow()), "Stale station view bypassed server access");
                helper.runAfterDelay(21, () -> {
                    try {
                        helper.assertTrue(menu.navigation().stations().isEmpty() && menu.accessibleStations().isEmpty(), "Open menu retained revoked station");
                        owner.setPos(rocket.getX() + 100, rocket.getY(), rocket.getZ());
                        helper.assertTrue(!menu.clickMenuButton(owner, 0) && menu.quotes().entries().isEmpty(), "Distant viewer retained access");
                        owner.setPos(rocket.position()); owner.containerMenu = owner.inventoryMenu;
                        helper.assertTrue(!menu.clickMenuButton(owner, 0), "Closed menu accepted refresh");
                        helper.succeed();
                    } finally { cleanup.run(); }
                });
            } catch (RuntimeException | Error exception) { cleanup.run(); throw exception; }
        });
    }

    @GameTest(template = "rocket_test", batch = "rocket_navigation_refresh", timeoutTicks = 130)
    public static void catalogServiceSharesReopenBudgetAndRecoversLatestGeneration(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var catalogs = new PlanetaryCatalogManager();
            helper.assertTrue(catalogs.applyCandidate(pair(false)), "Refresh fixture rejected");
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var owner = actor(helper, "NavRefresh");
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner.getUUID());
            Runnable cleanup = () -> { owner.containerMenu = owner.inventoryMenu; manager.clear(); rocket.discard(); };
            try {
                owner.setPos(rocket.position());
                helper.assertTrue(manager.navigationCatalog(owner, rocket).isEmpty(), "Closed menu received catalog");
                owner.containerMenu = new RocketFlightMenu(41, owner.getInventory(), rocket);
                var first = manager.navigationCatalog(owner, rocket).orElseThrow();
                helper.assertTrue(first.catalogGeneration() == catalogs.capture().orElseThrow().generation(), "Initial catalog generation incorrect");
                owner.containerMenu = new RocketFlightMenu(42, owner.getInventory(), rocket);
                helper.assertTrue(manager.navigationCatalog(owner, rocket).isEmpty(), "Reopening bypassed full-catalog budget");
                helper.assertTrue(catalogs.applyCandidate(pair(true)), "Reload fixture rejected");
                helper.assertTrue(manager.navigationCatalog(owner, rocket).isEmpty(), "Generation change bypassed budget");
                helper.runAfterDelay(101, () -> {
                    try {
                        var latest = manager.navigationCatalog(owner, rocket).orElseThrow();
                        helper.assertTrue(latest.catalogGeneration() > first.catalogGeneration()
                                && latest.catalogGeneration() == catalogs.capture().orElseThrow().generation(), "Recovery returned stale catalog");
                        helper.assertTrue(manager.navigationCatalog(owner, rocket).isEmpty(), "Repeated refresh not limited");
                        manager.onPlayerLoggedOut(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(owner));
                        helper.assertTrue(manager.navigationCatalog(owner, rocket).isPresent(), "Logout retained old budget");
                        owner.setPos(rocket.getX() + 100, rocket.getY(), rocket.getZ());
                        helper.assertTrue(manager.navigationCatalog(owner, rocket).isEmpty(), "Distant menu received catalog");
                        helper.succeed();
                    } finally { cleanup.run(); }
                });
            } catch (RuntimeException | Error exception) { cleanup.run(); throw exception; }
        });
    }

    private static FakePlayer actor(GameTestHelper helper, String name) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static java.util.Map<String, Integer> chunks(GameTestHelper helper) {
        var counts = new java.util.TreeMap<String, Integer>();
        for (var level : helper.getLevel().getServer().getAllLevels()) {
            counts.put(level.dimension().location().toString(), level.getChunkSource().getLoadedChunksCount());
        }
        return counts;
    }

    private static DataResult<PlanetaryCatalog> pair(boolean closeMars) {
        var bodies = new ArrayList<>(CelestialDefaults.definitions());
        for (var body : PlanetaryContent.definitions()) {
            bodies.add(closeMars && body.id().equals(PlanetaryContent.MARS)
                    ? new CelestialBodyDefinition(body.id(), body.parentId(), body.levelKey(), body.gravityMultiplier(), body.atmosphere(),
                    body.orbit(), body.visualProfile(), new CelestialCapabilities(false, false, false), body.solarIntensity(),
                    body.radiation(), body.environmentEffects()) : body);
        }
        return CelestialCatalog.create(bodies).flatMap(CelestialCatalog::requireFixedBaseline)
                .flatMap(c -> RouteCatalog.create(PlanetaryContent.routes(), bodies.stream().map(CelestialBodyDefinition::id).toList())
                        .flatMap(routes -> PlanetaryCatalog.create(c, routes)));
    }
}
