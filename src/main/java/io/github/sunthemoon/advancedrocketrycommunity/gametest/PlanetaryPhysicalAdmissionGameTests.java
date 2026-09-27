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
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Physical admission through public server services, with a private catalog pair. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryPhysicalAdmissionGameTests {
    private static final TravelTarget MARS = new TravelTarget.BodySurface(PlanetaryContent.MARS);
    private static final TravelTarget VENUS = new TravelTarget.BodySurface(PlanetaryContent.VENUS);

    private PlanetaryPhysicalAdmissionGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "planetary_physical_admission", timeoutTicks = 40)
    public static void repeatedQuotesDoNotLoadTerrainAndInvalidSourceFormsDoNotLaunch(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var catalogs = catalogs(false);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PhysicalAdmission"));
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), player.getUUID());
            try {
                player.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
                var server = helper.getLevel().getServer();
                var before = loadedChunks(server);
                for (int index = 0; index < 20; index++) {
                    var quotes = manager.flightQuotes(player, rocket);
                    helper.assertTrue(quotes.forTarget(MARS).canLaunch() && quotes.forTarget(VENUS).canLaunch(),
                            "Started planets have no launchable quote");
                }
                helper.assertTrue(loadedChunks(server).equals(before), "Quotes loaded or generated terrain");
                for (var target : List.of(new TravelTarget.BodySurface(PlanetaryContent.GAS_GIANT),
                        new TravelTarget.BodySurface(ModIdentity.id("absent")), new TravelTarget.Orbit(PlanetaryContent.MARS),
                        new TravelTarget.Mission(UUID.randomUUID()))) {
                    denied(helper, manager, player, rocket, target);
                }
                var original = rocket.flightData().orElseThrow();
                for (var target : List.of(new TravelTarget.BodySurface(PlanetaryContent.MARS),
                        new TravelTarget.Orbit(CelestialIds.EARTH_ID), new TravelTarget.Mission(UUID.randomUUID()))) {
                    ResourceLocation body = target instanceof TravelTarget.BodySurface ? PlanetaryContent.MARS : CelestialIds.EARTH_ID;
                    rocket.updateFlightData(RocketFlightData.restore(original.schemaVersion(), original.logicalRocketId(),
                            original.state(), original.fuel(), null, original.passengers(), body, original.currentDimension(),
                            original.currentOrigin(), original.stateStartedGameTime(), null, target));
                    denied(helper, manager, player, rocket, VENUS);
                }
                rocket.updateFlightData(original);
                helper.assertTrue(manager.flightQuotes(player, rocket).forTarget(MARS).canLaunch(),
                        "Invalid-source probe changed healthy admission");
                helper.succeed();
            } finally {
                manager.clear();
                rocket.discard();
            }
        });
    }

    @GameTest(template = "rocket_test", batch = "planetary_physical_admission", timeoutTicks = 40)
    public static void mappedBodyWithoutStartedLevelCannotQuoteOrDebit(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            var catalogs = catalogs(true);
            var manager = new RocketManager(catalogs.celestialView(), catalogs);
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MissingPlanet"));
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), player.getUUID());
            try {
                denied(helper, manager, player, rocket, VENUS);
                helper.succeed();
            } finally {
                manager.clear();
                rocket.discard();
            }
        });
    }

    private static void denied(GameTestHelper helper, RocketManager manager, FakePlayer player,
            RocketEntity rocket, TravelTarget target) {
        var journal = RocketTransferSavedData.get(helper.getLevel().getServer());
        var saved = journal.save(new CompoundTag());
        var flight = rocket.flightData().orElseThrow();
        helper.assertTrue(!manager.flightQuotes(player, rocket).forTarget(target).canLaunch(), "Invalid destination has a launchable quote");
        var result = manager.requestAdminFlight(rocket, target, UUID.randomUUID());
        helper.assertTrue(result.code() == RocketFlightRequestCode.INVALID_DESTINATION, "Unexpected physical denial: " + result.code());
        helper.assertTrue(rocket.flightData().orElseThrow().equals(flight), "Denied launch changed flight or fuel");
        helper.assertTrue(journal.save(new CompoundTag()).equals(saved), "Denied launch changed transfer authority");
    }

    private static java.util.Map<ResourceLocation, Integer> loadedChunks(MinecraftServer server) {
        var counts = new LinkedHashMap<ResourceLocation, Integer>();
        for (var level : server.getAllLevels()) {
            counts.put(level.dimension().location(), level.getChunkSource().getLoadedChunksCount());
        }
        return counts;
    }

    private static PlanetaryCatalogManager catalogs(boolean missingVenus) {
        var definitions = new ArrayList<>(CelestialDefaults.definitions());
        for (var body : PlanetaryContent.definitions()) {
            definitions.add(missingVenus && body.id().equals(PlanetaryContent.VENUS)
                    ? new CelestialBodyDefinition(body.id(), body.parentId(), Optional.of(PlanetaryContent.level(ModIdentity.id("unstarted"))),
                            body.gravityMultiplier(), body.atmosphere(), body.orbit(), body.visualProfile(), body.capabilities(),
                            body.solarIntensity(), body.radiation()) : body);
        }
        var manager = new PlanetaryCatalogManager();
        if (!manager.applyCandidate(CelestialCatalog.create(definitions).flatMap(CelestialCatalog::requireFixedBaseline)
                .flatMap(bodies -> RouteCatalog.create(PlanetaryContent.routes(), definitions.stream().map(CelestialBodyDefinition::id).toList())
                        .flatMap(graph -> PlanetaryCatalog.create(bodies, graph))))) {
            throw new IllegalStateException("Invalid physical-admission fixture catalogs");
        }
        return manager;
    }
}
