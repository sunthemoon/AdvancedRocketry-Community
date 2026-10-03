package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-043 star systems on the real server: packaged example system, knowledge and station orbit context. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarSystemGameTests {
    private StarSystemGameTests() {
    }

    @GameTest(template = "empty", batch = "star_systems", timeoutTicks = 100)
    public static void packagedExampleSystemIsLoadedKnownByDiscoveryAndGivesStationContext(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        // v1.8 (ADR-063 section 6): the star, Tau Ceti e and the classic worlds Tau Ceti f and g.
        String tauCeti = "system=" + StarSystemContent.TAU_CETI + " known=true bodies=4";
        List<String> console = new ArrayList<>();
        run(dispatcher, server, console, "arce celestial systems");
        helper.assertTrue(console.stream().anyMatch(line -> line.startsWith("system=" + CelestialIds.EARTH_ID)),
                "Home system missing: " + console);
        CelestialSavedData discovery = CelestialSavedData.get(server);
        boolean alreadyDiscovered = discovery.get(StarSystemContent.TAU_CETI_E).isPresent();
        // The star is known; each planet counts once discovered (other tests may discover f or g first).
        long known = 1 + Stream.of(StarSystemContent.TAU_CETI_E, ExoplanetContent.TAU_CETI_F,
                ExoplanetContent.TAU_CETI_G).filter(body -> discovery.get(body).isPresent()).count();
        helper.assertTrue(console.stream().anyMatch(line -> line.equals(tauCeti + " known_bodies=" + known)),
                "Example system missing or wrong: " + console);
        if (!alreadyDiscovered) {
            discovery.discover(StarSystemContent.TAU_CETI_E, helper.getLevel().getGameTime());
            console.clear();
            run(dispatcher, server, console, "arce celestial systems");
            helper.assertTrue(console.contains(tauCeti + " known_bodies=" + (known + 1)),
                    "Discovery not reflected: " + console);
        }

        // A station orbiting the example planet resolves the planet's (not Earth's) solar intensity.
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationState station = new StationCreationService(platforms, body -> true)
                .create(server, UUID.randomUUID(), "Tau Ceti Orbit", StarSystemContent.TAU_CETI_E, false)
                .station().orElseThrow();
        List<ServerPlayer> online = new ArrayList<>();
        try {
            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
            space.getChunkAt(pad);
            List<String> replies = new ArrayList<>();
            ServerPlayer visitor = ConnectedTestPlayers.join(server, UUID.randomUUID(), "starVisitor", space, pad, replies);
            online.add(visitor);
            try {
                dispatcher.execute("arce station environment", visitor.createCommandSourceStack());
            } catch (CommandSyntaxException exception) {
                throw new AssertionError(exception);
            }
            String shown = replies.isEmpty() ? "" : replies.get(replies.size() - 1);
            helper.assertTrue(shown.contains("orbit=" + StarSystemContent.TAU_CETI_E) && shown.contains("solar=0.50")
                    && !shown.contains("(unavailable)"), "Station context differs: " + shown);
        } finally {
            online.forEach(server.getPlayerList()::remove);
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
            data.flush(server);
        }
        helper.succeed();
    }

    private static void run(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                            MinecraftServer server, List<String> replies, String command) {
        try {
            dispatcher.execute(command, server.createCommandSourceStack().withSource(ConnectedTestPlayers.capture(replies)));
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Command syntax rejected: " + command, exception);
        }
    }
}
