package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.compat.environment.ServerEnvironmentQueries;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-041 station gravity command, effective player gravity and environment display. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationOrbitGameTests {
    private static final double BASE_GRAVITY = 0.08D;

    private StationOrbitGameTests() {
    }

    @GameTest(template = "empty", batch = "station_orbit", timeoutTicks = 100)
    public static void localOwnerSetsStationGravityAppliedOnlyInsideItsRegion(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space is unavailable");
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        StationState station = new StationCreationService(platforms, body -> true)
                .create(server, ownerId, "Orbit", CelestialIds.MOON_ID, false).station().orElseThrow();
        data.invite(station.stationId(), memberId);
        data.acceptInvitation(station.stationId(), memberId);
        data.flush(server);
        station = data.find(station.stationId()).orElseThrow();
        CelestialCatalogManager catalogs = catalogs(2.5D);
        StationManager manager = new StationManager(catalogs);
        CelestialGravityController controller = new CelestialGravityController(
                new CelestialEnvironmentService(catalogs), manager::effectiveGravity);
        List<ServerPlayer> online = new ArrayList<>();
        try {
            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
            space.getChunkAt(pad);
            List<String> ownerReplies = new ArrayList<>();
            ServerPlayer owner = join(server, online, ownerId, space, pad, ownerReplies);
            List<String> memberReplies = new ArrayList<>();
            ServerPlayer member = join(server, online, memberId, space, pad, memberReplies);
            var dispatcher = server.getCommands().getDispatcher();

            run(dispatcher, member.createCommandSourceStack(), "arce station gravity 50");
            helper.assertTrue(last(memberReplies).contains(StationManagementCode.UNAUTHORIZED.description()),
                    "Member changed gravity: " + memberReplies);
            List<String> consoleReplies = new ArrayList<>();
            run(dispatcher, server.createCommandSourceStack().withSource(capture(consoleReplies)),
                    "execute as " + ownerId + " run arce station gravity 50");
            helper.assertTrue(last(consoleReplies).contains(StationManagementCode.NOT_LOCAL_PLAYER.description()),
                    "/execute changed gravity: " + consoleReplies);
            helper.assertTrue(data.find(station.stationId()).orElseThrow().environment().gravityMilli() == 0,
                    "Rejected commands changed gravity");

            int loadedChunks = space.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(run(dispatcher, owner.createCommandSourceStack(), "arce station gravity 35") == 1,
                    "Owner gravity command failed: " + ownerReplies);
            helper.assertTrue(last(ownerReplies).contains("Station gravity set"), "No confirmation: " + ownerReplies);
            helper.assertTrue(space.getChunkSource().getLoadedChunksCount() == loadedChunks,
                    "Gravity command changed the loaded chunk count");
            StationState published = data.find(station.stationId()).orElseThrow();
            helper.assertTrue(published.environment().gravityMilli() == 350 && published.sameAuthorityAs(station)
                    && published.region().equals(station.region()), "Published gravity update differs");
            var onDisk = CheckedSavedDataFile.readPayload(server.getWorldPath(LevelResource.ROOT).resolve("data")
                    .resolve(ManagedSavedDataType.STATIONS.fileName()), ManagedSavedDataType.STATIONS).orElseThrow();
            helper.assertTrue(StationRegistrySavedData.load(onDisk).find(station.stationId())
                    .filter(published::equals).isPresent(), "Station file lacks the checked gravity update");
            run(dispatcher, owner.createCommandSourceStack(), "arce station gravity 35");
            helper.assertTrue(last(ownerReplies).contains("unchanged"), "Repeat was not reported unchanged");
            run(dispatcher, owner.createCommandSourceStack(), "arce station gravity 40");
            helper.assertTrue(last(ownerReplies).contains(StationManagementCode.GRAVITY_COOLDOWN.description())
                    && data.find(station.stationId()).orElseThrow().environment().gravityMilli() == 350,
                    "A second change inside the cooldown was written: " + ownerReplies);

            // Effective player gravity: station value inside, shared Space outside, Level elsewhere.
            tick(controller, owner);
            helper.assertTrue(close(gravity(owner), BASE_GRAVITY * 0.35D), "Station gravity not applied: " + gravity(owner));
            BlockPos gap = pad.east(StationLimits.REGION_SIZE / 2 + 32);
            owner.teleportTo(space, gap.getX() + 0.5D, gap.getY(), gap.getZ() + 0.5D, 0.0F, 0.0F);
            tick(controller, owner);
            helper.assertTrue(close(gravity(owner), 0.0D), "Gap did not use Space gravity: " + gravity(owner));
            owner.teleportTo(helper.getLevel(), 0.5D, 100.0D, 0.5D, 0.0F, 0.0F);
            tick(controller, owner);
            helper.assertTrue(close(gravity(owner), BASE_GRAVITY), "Overworld gravity changed: " + gravity(owner));
            owner.teleportTo(space, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0.0F, 0.0F);
            // The production-registered controller (mod wiring) applies the same station gravity.
            MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(owner));
            helper.assertTrue(close(gravity(owner), BASE_GRAVITY * 0.35D),
                    "Registered controller did not apply station gravity: " + gravity(owner));

            // Display and catalog reload through the resolver.
            var environment = manager.environmentAt(space, pad).orElseThrow();
            helper.assertTrue(environment.orbitBodyAvailable() && close(environment.solarIntensity(), 2.5D)
                    && close(environment.effectiveGravity(), 0.35D), "Resolved environment differs");
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies(3.0D))), "Reload failed");
            helper.assertTrue(close(manager.environmentAt(space, pad).orElseThrow().solarIntensity(), 3.0D),
                    "Reload was not reflected");
            helper.assertTrue(manager.environmentAt(space, gap).isEmpty(), "Gap resolved a station");
            run(dispatcher, owner.createCommandSourceStack(), "arce station environment");
            String shown = last(ownerReplies);
            helper.assertTrue(shown.contains("id=" + station.stationId()) && shown.contains("orbit=" + CelestialIds.MOON_ID)
                    && shown.contains("gravity=35%") && shown.contains("vacuum=true") && shown.contains("sun_angle=270.0"),
                    "Environment display differs: " + shown);
            List<String> outsiderReplies = new ArrayList<>();
            ServerPlayer outsider = join(server, online, UUID.randomUUID(), space, pad, outsiderReplies);
            run(dispatcher, outsider.createCommandSourceStack(), "arce station environment");
            helper.assertTrue(last(outsiderReplies).contains("not a member")
                    && !last(outsiderReplies).contains(station.stationId().toString()),
                    "Station identity shown to an outsider: " + outsiderReplies);

            // Configured gravity above the physics bound: API reports it, physics clamps to 4.0.
            StationState current = data.find(station.stationId()).orElseThrow();
            helper.assertTrue(data.checkedSetGravity(server, current, 5_000)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Configured gravity write failed");
            var clamped = manager.environmentAt(space, pad).orElseThrow();
            helper.assertTrue(clamped.gravityClamped() && close(clamped.configuredGravity(), 5.0D)
                    && close(clamped.effectiveGravity(), CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER),
                    "Clamp differs");
            try (var queries = new ServerEnvironmentQueries(server::isSameThread, key -> server.getLevel(key) != null,
                    catalogs, data)) {
                var snapshot = queries.at(CelestialIds.SPACE_LEVEL, pad).orElseThrow();
                helper.assertTrue(close(snapshot.gravityMultiplier(), 5.0D),
                        "API did not report the configured gravity: " + snapshot.gravityMultiplier());
            }
            tick(controller, owner);
            helper.assertTrue(close(gravity(owner), BASE_GRAVITY * CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER),
                    "Physics did not clamp to 4: " + gravity(owner));
        } finally {
            for (ServerPlayer player : online) {
                server.getPlayerList().remove(player);
            }
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
            data.flush(server);
            manager.clear();
        }
        helper.succeed();
    }

    private static void tick(CelestialGravityController controller, ServerPlayer player) {
        controller.onLivingTick(new LivingEvent.LivingTickEvent(player));
    }

    private static double gravity(ServerPlayer player) {
        return player.getAttribute(ForgeMod.ENTITY_GRAVITY.get()).getValue();
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-9D;
    }

    private static String last(List<String> replies) {
        return replies.isEmpty() ? "" : replies.get(replies.size() - 1);
    }

    private static int run(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
                           CommandSourceStack source, String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Command syntax rejected: " + command, exception);
        }
    }

    private static CelestialCatalogManager catalogs(double moonSolar) {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        if (!catalogs.applyCandidate(CelestialCatalog.create(bodies(moonSolar)))) {
            throw new IllegalStateException("Test celestial catalog could not be prepared");
        }
        return catalogs;
    }

    private static List<CelestialBodyDefinition> bodies(double moonSolar) {
        List<CelestialBodyDefinition> bodies = new ArrayList<>();
        for (CelestialBodyDefinition body : CelestialDefaults.definitions()) {
            bodies.add(!body.id().equals(CelestialIds.MOON_ID) ? body : new CelestialBodyDefinition(body.id(),
                    body.parentId(), body.levelKey(), body.gravityMultiplier(), body.atmosphere(), body.orbit(),
                    body.visualProfile(), body.capabilities(), moonSolar, body.radiation(), body.environmentEffects()));
        }
        return bodies;
    }

    private static CommandSource capture(List<String> replies) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    /** Connected mock player with a chosen UUID, as GameTestHelper.makeMockServerPlayerInLevel builds one. */
    private static ServerPlayer join(MinecraftServer server, List<ServerPlayer> online, UUID id, ServerLevel level,
                                     BlockPos position, List<String> replies) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, "orbiter" + online.size())) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }

            @Override
            public boolean hasPermissions(int permissionLevel) {
                return false;
            }

            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        online.add(player);
        return player;
    }
}
