package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationExpansionCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
        StationManager manager = new StationManager(new CelestialCatalogManager());
        try {
            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
            space.getChunkAt(pad); // Test setup only; production checks never load chunks.
            FakePlayer owner = player(space, ownerId, false, pad);

            for (UUID denied : new UUID[]{memberId, inviteeId, UUID.randomUUID()}) {
                expect(helper, StationExpansionCode.UNAUTHORIZED,
                        manager.requestExpansion(player(space, denied, false, pad)).code(), "non-manager request");
            }
            expect(helper, StationExpansionCode.NOT_IN_SPACE,
                    manager.requestExpansion(player(helper.getLevel(), ownerId, false,
                            helper.absolutePos(BlockPos.ZERO))).code(), "Overworld request");
            BlockPos gap = pad.east(StationLimits.REGION_SIZE / 2 + 32);
            boolean gapLoaded = space.hasChunkAt(gap);
            if (!gapLoaded) {
                expect(helper, StationExpansionCode.CHUNK_UNLOADED,
                        manager.requestExpansion(player(space, ownerId, false, gap)).code(), "unloaded chunk");
                helper.assertTrue(!space.hasChunkAt(gap), "Expansion request loaded the requester's chunk");
                space.getChunkAt(gap); // Test setup only, to reach the region check.
            }
            expect(helper, StationExpansionCode.NOT_IN_STATION,
                    manager.requestExpansion(player(space, ownerId, false, gap)).code(), "gap request");

            expect(helper, StationExpansionCode.NO_CONFIRMATION,
                    manager.confirmExpansion(owner, station.stationId()).code(), "unrequested confirm");
            expect(helper, StationExpansionCode.ISSUED, manager.requestExpansion(owner).code(), "owner request");
            expect(helper, StationExpansionCode.NO_CONFIRMATION,
                    manager.confirmExpansion(player(space, UUID.randomUUID(), true, pad), station.stationId()).code(),
                    "transferred confirmation");
            expect(helper, StationExpansionCode.CONFIRMATION_MISMATCH,
                    manager.confirmExpansion(owner, second.stationId()).code(), "other station confirmation");
            expect(helper, StationExpansionCode.NO_CONFIRMATION,
                    manager.confirmExpansion(owner, station.stationId()).code(), "consumed confirmation");

            expect(helper, StationExpansionCode.ISSUED, manager.requestExpansion(owner).code(), "owner request");
            manager.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(owner));
            expect(helper, StationExpansionCode.NO_CONFIRMATION,
                    manager.confirmExpansion(owner, station.stationId()).code(), "confirmation after logout");

            expect(helper, StationExpansionCode.ISSUED, manager.requestExpansion(owner).code(), "owner request");
            data.declineInvitation(station.stationId(), inviteeId);
            expect(helper, StationExpansionCode.STATION_CHANGED,
                    manager.confirmExpansion(owner, station.stationId()).code(), "stale confirmation");
            helper.assertTrue(data.find(station.stationId()).orElseThrow().region().width() == StationLimits.REGION_SIZE,
                    "Rejected confirmation changed the region");

            station = data.find(station.stationId()).orElseThrow();
            int loadedChunks = space.getChunkSource().getLoadedChunksCount();
            expect(helper, StationExpansionCode.ISSUED, manager.requestExpansion(owner).code(), "owner request");
            var expanded = manager.confirmExpansion(owner, station.stationId());
            expect(helper, StationExpansionCode.EXPANDED, expanded.code(), "owner confirm");
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
            expect(helper, StationExpansionCode.ALREADY_EXPANDED, manager.requestExpansion(owner).code(),
                    "repeated request");

            // Registered command path: console has no bypass; a local operator can expand another station.
            var dispatcher = server.getCommands().getDispatcher();
            boolean consoleRejected = false;
            try {
                dispatcher.execute("arce station expand", server.createCommandSourceStack());
            } catch (CommandSyntaxException expected) {
                consoleRejected = true;
            }
            helper.assertTrue(consoleRejected, "Console expansion was not rejected");
            BlockPos secondPad = new BlockPos(second.landingPad().x(), StationLimits.LANDING_Y, second.landingPad().z());
            space.getChunkAt(secondPad);
            var operatorSource = player(space, UUID.randomUUID(), true, secondPad).createCommandSourceStack();
            helper.assertTrue(dispatcher.execute("arce station expand", operatorSource) == 1,
                    "Operator request command failed");
            helper.assertTrue(dispatcher.execute("arce station expand confirm " + second.stationId(),
                    operatorSource) == 1, "Operator confirm command failed");
            helper.assertTrue(data.find(second.stationId()).orElseThrow().expanded(),
                    "Operator command did not expand the station");
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Station expansion command failed", exception);
        } finally {
            manager.clear();
            for (StationState created : new StationState[]{station, second}) {
                data.delete(created.stationId());
                platforms.removeTemplate(space, created.cell());
            }
            data.flush(server);
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, StationExpansionCode expected, StationExpansionCode actual,
                               String step) {
        helper.assertTrue(expected == actual, step + ": expected " + expected + " but was " + actual);
    }

    private static FakePlayer player(ServerLevel level, UUID id, boolean operator, BlockPos position) {
        FakePlayer player = new FakePlayer(level, new GameProfile(id, "StationExpander")) {
            @Override
            public boolean hasPermissions(int permissionLevel) {
                return operator;
            }
        };
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }
}
