package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationExpansionCode;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Registered-command expansion with connected mock players, as vanilla GameTest creates them. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationExpansionGameTests {
    private StationExpansionGameTests() {
    }

    @GameTest(template = "empty", batch = "station_expansion", timeoutTicks = 100)
    public static void localOwnerAndOperatorExpandOnlyThroughConfirmedCheckedCommit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space is unavailable");
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        helper.assertTrue(data.operational() && !data.expansionQuarantined(), "Station authority is unavailable");
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationCreationService creation = new StationCreationService(platforms, body -> true);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        StationState station = creation.create(server, ownerId, "Expansion", CelestialIds.EARTH_ID, false)
                .station().orElseThrow();
        StationState second = creation.create(server, UUID.randomUUID(), "Operator Expansion",
                CelestialIds.MOON_ID, false).station().orElseThrow();
        data.invite(station.stationId(), memberId);
        data.acceptInvitation(station.stationId(), memberId);
        data.invite(station.stationId(), inviteeId);
        data.flush(server);
        station = data.find(station.stationId()).orElseThrow();
        List<Mock> online = new ArrayList<>();
        try {
            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
            space.getChunkAt(pad); // Test setup only; production checks never load chunks.
            for (UUID denied : new UUID[]{memberId, inviteeId, UUID.randomUUID()}) {
                Mock player = join(server, online, denied, false, space, pad);
                expect(helper, player, StationExpansionCode.UNAUTHORIZED, "arce station expand", "non-manager");
            }

            Mock owner = join(server, online, ownerId, false, helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
            expect(helper, owner, StationExpansionCode.NOT_IN_SPACE, "arce station expand", "Overworld request");
            BlockPos gap = pad.east(StationLimits.REGION_SIZE / 2 + 32);
            helper.assertTrue(!space.hasChunkAt(gap), "Gap chunk was already loaded before the player arrived");
            // A connected player's own chunk loads on arrival, so CHUNK_UNLOADED is defense in depth
            // for real players; its ordering is covered by StationExpansionServiceTest.
            owner.player().teleportTo(space, gap.getX() + 0.5D, gap.getY(), gap.getZ() + 0.5D, 0.0F, 0.0F);
            helper.assertTrue(space.hasChunkAt(gap), "The arriving player did not load their own chunk");
            expect(helper, owner, StationExpansionCode.NOT_IN_STATION, "arce station expand", "gap request");
            owner.player().teleportTo(space, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0.0F, 0.0F);

            // Only the player's own command source counts: no console, /execute, block or function bypass.
            var dispatcher = server.getCommands().getDispatcher();
            try {
                dispatcher.execute("arce station expand", server.createCommandSourceStack());
                helper.fail("Console expansion was accepted");
            } catch (CommandSyntaxException expected) {
                helper.assertTrue(expected.getType() == CommandSourceStack.ERROR_NOT_PLAYER,
                        "Console was rejected for the wrong reason: " + expected.getMessage());
            }
            // /execute keeps the console's output source; its replies arrive there, not at the owner.
            List<String> consoleReplies = new ArrayList<>();
            CommandSourceStack console = server.createCommandSourceStack().withSource(capture(consoleReplies));
            String notLocal = StationExpansionCode.NOT_LOCAL_PLAYER.description();
            run(dispatcher, console, "execute as " + ownerId + " run arce station expand");
            helper.assertTrue(consoleReplies.size() == 1 && consoleReplies.get(0).contains(notLocal),
                    "/execute as the owner was not rejected as non-local: " + consoleReplies);
            run(dispatcher, console, "execute as " + ownerId + " run arce station expand confirm " + station.stationId());
            helper.assertTrue(consoleReplies.size() == 2 && consoleReplies.get(1).contains(notLocal),
                    "/execute confirm was not rejected as non-local: " + consoleReplies);
            expect(helper, owner, StationExpansionCode.NO_CONFIRMATION,
                    "arce station expand confirm " + station.stationId(), "confirmation after /execute");

            expect(helper, owner, StationExpansionCode.ISSUED, "arce station expand", "owner request");
            helper.assertTrue(owner.last().contains("expand confirm " + station.stationId()),
                    "Warning does not name the confirm command");
            run(dispatcher, console, "execute as " + ownerId + " run arce station expand confirm " + station.stationId());
            helper.assertTrue(consoleReplies.size() == 3 && consoleReplies.get(2).contains(notLocal),
                    "/execute confirm with a pending confirmation was not rejected: " + consoleReplies);
            // The CONFIRMATION_MISMATCH below proves the owner's confirmation was still pending.
            Mock operator = join(server, online, UUID.randomUUID(), true, space, pad);
            expect(helper, operator, StationExpansionCode.NO_CONFIRMATION,
                    "arce station expand confirm " + station.stationId(), "transferred confirmation");
            expect(helper, owner, StationExpansionCode.CONFIRMATION_MISMATCH,
                    "arce station expand confirm " + second.stationId(), "other station confirmation");
            expect(helper, owner, StationExpansionCode.NO_CONFIRMATION,
                    "arce station expand confirm " + station.stationId(), "consumed confirmation");

            // Real logout through the registered listener, then a fresh connection for the same player.
            expect(helper, owner, StationExpansionCode.ISSUED, "arce station expand", "owner request");
            server.getPlayerList().remove(owner.player());
            online.remove(owner);
            owner = join(server, online, ownerId, false, space, pad);
            expect(helper, owner, StationExpansionCode.NO_CONFIRMATION,
                    "arce station expand confirm " + station.stationId(), "confirmation after logout");

            expect(helper, owner, StationExpansionCode.ISSUED, "arce station expand", "owner request");
            data.declineInvitation(station.stationId(), inviteeId);
            expect(helper, owner, StationExpansionCode.STATION_CHANGED,
                    "arce station expand confirm " + station.stationId(), "stale confirmation");
            helper.assertTrue(data.find(station.stationId()).orElseThrow().region().width() == StationLimits.REGION_SIZE,
                    "Rejected confirmation changed the region");

            station = data.find(station.stationId()).orElseThrow();
            int loadedChunks = space.getChunkSource().getLoadedChunksCount();
            expect(helper, owner, StationExpansionCode.ISSUED, "arce station expand", "owner request");
            expect(helper, owner, StationExpansionCode.EXPANDED,
                    "arce station expand confirm " + station.stationId(), "owner confirm");
            helper.assertTrue(space.getChunkSource().getLoadedChunksCount() == loadedChunks,
                    "Expansion changed the loaded chunk count");
            StationState published = data.find(station.stationId()).orElseThrow();
            helper.assertTrue(published.equals(station.withExpandedRegion()), "Published state is not the expansion");
            helper.assertTrue(data.findAt(gap.getX(), gap.getZ()).filter(published::equals).isPresent(),
                    "Expanded region does not resolve the former gap");
            helper.assertTrue(platforms.intact(space, published.cell()), "Expansion changed the platform");
            var onDisk = CheckedSavedDataFile.readPayload(server.getWorldPath(LevelResource.ROOT).resolve("data")
                    .resolve(ManagedSavedDataType.STATIONS.fileName()), ManagedSavedDataType.STATIONS).orElseThrow();
            helper.assertTrue(StationRegistrySavedData.load(onDisk).find(station.stationId())
                    .filter(published::equals).isPresent(), "Station file does not contain the checked expansion");
            expect(helper, owner, StationExpansionCode.ALREADY_EXPANDED, "arce station expand", "repeated request");

            // A connected operator standing in another station may expand it.
            BlockPos secondPad = new BlockPos(second.landingPad().x(), StationLimits.LANDING_Y, second.landingPad().z());
            space.getChunkAt(secondPad);
            operator.player().teleportTo(space, secondPad.getX() + 0.5D, secondPad.getY(), secondPad.getZ() + 0.5D,
                    0.0F, 0.0F);
            expect(helper, operator, StationExpansionCode.ISSUED, "arce station expand", "operator request");
            expect(helper, operator, StationExpansionCode.EXPANDED,
                    "arce station expand confirm " + second.stationId(), "operator confirm");
            helper.assertTrue(data.find(second.stationId()).orElseThrow().expanded(),
                    "Operator command did not expand the station");
        } finally {
            for (Mock player : online) {
                server.getPlayerList().remove(player.player());
            }
            for (StationState created : new StationState[]{station, second}) {
                data.delete(created.stationId());
                platforms.removeTemplate(space, created.cell());
            }
            data.flush(server);
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, Mock player, StationExpansionCode expected, String command,
                               String step) {
        int before = player.messages().size();
        int result = run(player.player().getServer().getCommands().getDispatcher(),
                player.player().createCommandSourceStack(), command);
        boolean success = expected == StationExpansionCode.ISSUED || expected == StationExpansionCode.EXPANDED;
        helper.assertTrue(result == (success ? 1 : 0), step + ": command result " + result);
        helper.assertTrue(player.messages().size() > before, step + ": no reply");
        String reply = player.last();
        String marker = success
                ? (expected == StationExpansionCode.ISSUED ? "To confirm within" : "Station expanded;")
                : expected.description();
        helper.assertTrue(reply.contains(marker), step + ": expected " + expected + " but replied " + reply);
    }

    private static int run(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
                           CommandSourceStack source, String command) {
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Command syntax rejected: " + command, exception);
        }
    }

    private static net.minecraft.commands.CommandSource capture(List<String> replies) {
        return new net.minecraft.commands.CommandSource() {
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

    /** Mirrors GameTestHelper.makeMockServerPlayerInLevel with a chosen UUID and captured replies. */
    private static Mock join(MinecraftServer server, List<Mock> online, UUID id, boolean operator, ServerLevel level,
                             BlockPos position) {
        List<String> messages = new ArrayList<>();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, "expander" + online.size())) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean hasPermissions(int permissionLevel) {
                return operator;
            }

            @Override
            public void sendSystemMessage(Component message) {
                messages.add(message.getString());
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        Mock mock = new Mock(player, messages);
        online.add(mock);
        return mock;
    }

    private record Mock(ServerPlayer player, List<String> messages) {
        String last() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1);
        }
    }
}
