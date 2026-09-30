package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointService;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorEndpointValidator;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-045 on the live server: the operator check command reads only (no chunk in any Level, no dirty
 * registry), reports the first failing rule in one line, and follows orbit, catalog and border changes.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationElevatorGameTests {
    private static final String EARTH = CelestialIds.EARTH_ID.toString();
    private static final String MOON = CelestialIds.MOON_ID.toString();

    private StationElevatorGameTests() {
    }

    @GameTest(template = "empty", batch = "station_elevator", timeoutTicks = 100)
    public static void endpointChecksReadOnlyInRuleOrderAndFollowWarpCatalogAndBorder(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        helper.assertTrue(data.updatesAvailable(), "Station authority is unavailable");
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        UUID ownerId = UUID.randomUUID();
        StationState station = new StationCreationService(platforms, body -> true)
                .create(server, ownerId, "Elevator", CelestialIds.EARTH_ID, false).station().orElseThrow();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        WorldBorder border = server.overworld().getWorldBorder();
        double borderSize = border.getSize();
        double borderX = border.getCenterX();
        double borderZ = border.getCenterZ();
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        try {
            data.flush(server);
            Map<ResourceKey<Level>, Integer> chunks = loadedChunks(levels);
            String id = station.stationId().toString();

            List<String> replies = new ArrayList<>();
            helper.assertTrue(run(server, replies, check(id, EARTH, 100, -100)) == 1
                            && replies.equals(List.of("Elevator endpoint check; station=" + id + " body=" + EARTH
                            + " column=100,-100 result=valid")), "Valid check differs: " + replies);
            expectRule(helper, server, check(UUID.randomUUID().toString(), EARTH, 0, 0),
                    ElevatorEndpointCode.STATION_MISSING);
            expectRule(helper, server, check(id, MOON, 0, 0), ElevatorEndpointCode.NOT_CURRENT_ORBIT);
            replies.clear();
            helper.assertTrue(run(server, replies, check(id, EARTH, 30_000_001, 0)) == 0
                    && replies.stream().anyMatch(line -> line.contains("30000000")), "Parse bound missing: " + replies);

            // Border shrink: a previously valid column is outside, and nothing is loaded to find out.
            border.setCenter(0.0D, 0.0D);
            border.setSize(2_000.0D);
            expectRule(helper, server, check(id, EARTH, 5_000, 0), ElevatorEndpointCode.OUTSIDE_WORLD_BORDER);
            border.setSize(borderSize);
            border.setCenter(borderX, borderZ);
            expectRule(helper, server, check(id, EARTH, 5_000, 0), ElevatorEndpointCode.VALID);

            // Catalog changes through the adapter on private catalogs: a remapped Level and a lost surface.
            ElevatorEndpointValidator.Request earthColumn =
                    new ElevatorEndpointValidator.Request(station.stationId(), CelestialIds.EARTH_ID, 0, 0);
            ResourceKey<Level> absent = ResourceKey.create(Registries.DIMENSION, ModIdentity.id("absent_level"));
            expectAdapter(helper, server, earthBody(body -> new CelestialBodyDefinition(body.id(), body.parentId(),
                    Optional.of(absent), body.gravityMultiplier(), body.atmosphere(), body.orbit(),
                    body.visualProfile(), body.capabilities(), 1, 0)), ownerId, earthColumn,
                    ElevatorEndpointCode.LEVEL_ABSENT);
            expectAdapter(helper, server, earthBody(body -> new CelestialBodyDefinition(body.id(), body.parentId(),
                    body.levelKey(), body.gravityMultiplier(), body.atmosphere(), body.orbit(), body.visualProfile(),
                    new CelestialCapabilities(false, body.capabilities().orbitable(), false), 1, 0)), ownerId,
                    earthColumn, ElevatorEndpointCode.NO_SURFACE);
            expectAdapter(helper, server, earthBody(UnaryOperator.identity()), ownerId, earthColumn,
                    ElevatorEndpointCode.VALID);
            expectAdapter(helper, server, earthBody(UnaryOperator.identity()), UUID.randomUUID(), earthColumn,
                    ElevatorEndpointCode.UNAUTHORIZED);

            helper.assertTrue(!data.isDirty(), "An elevator check dirtied the station registry");
            helper.assertTrue(chunks.equals(loadedChunks(levels)), "An elevator check loaded or unloaded chunks: "
                    + chunks + " -> " + loadedChunks(levels));

            // A non-operator never reaches the command: today the shared "admin" node already refuses it,
            // and the "elevator" leaf carries its own requirement too (checked directly below, final
            // review A5). (Joining loads the player's chunks, so this runs after the chunk comparison.)
            List<String> playerReplies = new ArrayList<>();
            var player = ConnectedTestPlayers.join(server, ownerId, "elevatorOwner", space,
                    BlockPos.containing(station.landingPad().x(), 80, station.landingPad().z()), playerReplies);
            try {
                CommandSourceStack source = player.createCommandSourceStack();
                boolean unreachable = server.getCommands().getDispatcher().parse(check(id, EARTH, 0, 0), source)
                        .getReader().canRead();
                int result = server.getCommands().performPrefixedCommand(source, check(id, EARTH, 0, 0));
                helper.assertTrue(unreachable && result == 0 && !playerReplies.isEmpty()
                                && playerReplies.stream().noneMatch(line -> line.contains("Elevator endpoint check")),
                        "A non-operator reached the elevator check: " + playerReplies);
                // The leaf's own requirement, independent of the shared "admin" node (ADR-045).
                var root = server.getCommands().getDispatcher().getRoot();
                var leaf = root.getChild("arce").getChild("station").getChild("admin").getChild("elevator");
                helper.assertTrue(leaf != null && !leaf.getRequirement().test(source)
                                && leaf.getRequirement().test(server.createCommandSourceStack()),
                        "The elevator node does not carry its own permission-level-2 requirement");
            } finally {
                server.getPlayerList().remove(player);
            }

            // A warp to another body invalidates the previously valid pair (rule 3).
            data.foldWarpCredits(Map.of(station.stationId(), 2_000_000));
            helper.assertTrue(data.checkedRelocation(server, station, CelestialIds.MOON_ID, 2_000_000)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The relocation fixture failed");
            expectRule(helper, server, check(id, EARTH, 100, -100), ElevatorEndpointCode.NOT_CURRENT_ORBIT);
            expectRule(helper, server, check(id, MOON, 100, -100), ElevatorEndpointCode.VALID);
        } finally {
            border.setSize(borderSize);
            border.setCenter(borderX, borderZ);
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
            data.flush(server);
        }
        helper.succeed();
    }

    private static String check(String stationId, String body, int x, int z) {
        return "arce station admin elevator check " + stationId + " " + body + " " + x + " " + z;
    }

    private static int run(MinecraftServer server, List<String> replies, String command) {
        CommandSourceStack console = server.createCommandSourceStack()
                .withSource(ConnectedTestPlayers.capture(replies));
        return server.getCommands().performPrefixedCommand(console, command);
    }

    private static void expectRule(GameTestHelper helper, MinecraftServer server, String command,
                                   ElevatorEndpointCode code) {
        List<String> replies = new ArrayList<>();
        int result;
        List<String> audits;
        try (AuditLogCapture audit = new AuditLogCapture()) {
            result = run(server, replies, command);
            audits = audit.lines().stream().filter(line -> line.startsWith("ARCE_STATION_ELEVATOR_CHECK ")).toList();
        }
        helper.assertTrue(audits.size() == 1 && audits.get(0).endsWith(" result=" + code),
                command + ": expected one audit line with result " + code + " but got " + audits);
        String expected = code == ElevatorEndpointCode.VALID ? "result=valid"
                : "result=rule " + code.rule() + " " + code + ": ";
        helper.assertTrue(replies.size() == 1 && replies.get(0).contains(expected)
                        && result == (code == ElevatorEndpointCode.VALID ? 1 : 0),
                command + ": expected " + code + " in one line, got " + replies);
    }

    private static void expectAdapter(GameTestHelper helper, MinecraftServer server, List<CelestialBodyDefinition> bodies,
                                      UUID requester, ElevatorEndpointValidator.Request request,
                                      ElevatorEndpointCode code) {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies)), "Private catalog rejected");
        ElevatorEndpointCode actual = new ElevatorEndpointService(catalogs).check(server, requester, false, request)
                .code();
        helper.assertTrue(actual == code, "Adapter: expected " + code + " but got " + actual);
    }

    private static List<CelestialBodyDefinition> earthBody(UnaryOperator<CelestialBodyDefinition> change) {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.replaceAll(body -> body.id().equals(CelestialIds.EARTH_ID) ? change.apply(body) : body);
        return bodies;
    }

    private static Map<ResourceKey<Level>, Integer> loadedChunks(List<ServerLevel> levels) {
        Map<ResourceKey<Level>, Integer> counts = new LinkedHashMap<>();
        levels.forEach(level -> counts.put(level.dimension(), level.getChunkSource().getLoadedChunksCount()));
        return counts;
    }
}
