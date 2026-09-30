package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.StationSkyContextPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationSkyContextService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-047 on the live server: each player's station sky context is derived from their own position,
 * sent only on change, follows a warp, an expansion, a deletion and the player's moves, is re-sent
 * after a respawn or Level change, is "none" while the registry is blocked, never loads a chunk, and
 * carries no station identity.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationSkyContextGameTests {
    private StationSkyContextGameTests() {
    }

    @GameTest(template = "empty", batch = "station_sky_context", timeoutTicks = 100)
    public static void theSkyContextIsServerDerivedSentOnChangeAndFollowsEveryChange(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        helper.assertTrue(space != null && data.updatesAvailable(), "Station authority or Space is unavailable");
        helper.assertTrue(StationSkyContextPacket.class.getRecordComponents().length == 1,
                "The sky context must carry only the orbit body");
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationCreationService creation = new StationCreationService(platforms, body -> true);
        StationState earthStation = creation.create(server, UUID.randomUUID(), "Sky", CelestialIds.EARTH_ID, false)
                .station().orElseThrow();
        StationState moonStation = creation.create(server, UUID.randomUUID(), "Sky two", CelestialIds.MOON_ID, false)
                .station().orElseThrow();
        UUID viewerId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Map<UUID, List<StationSkyContextPacket>> received = new HashMap<>();
        StationSkyContextService service = new StationSkyContextService((player, packet) ->
                received.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(packet));
        BlockPos pad = pad(earthStation);
        BlockPos otherPad = pad(moonStation);
        space.getChunkAt(pad); // Test setup only.
        space.getChunkAt(otherPad);
        ServerPlayer viewer = ConnectedTestPlayers.join(server, viewerId, "skyViewer", space, pad, new ArrayList<>());
        ServerPlayer other = ConnectedTestPlayers.join(server, otherId, "skyOther", space, otherPad, new ArrayList<>());
        boolean removed = false;
        try {
            List<ServerLevel> levels = new ArrayList<>();
            server.getAllLevels().forEach(levels::add);
            Map<ResourceKey<Level>, Integer> chunks = loadedChunks(levels);
            service.pass(server);
            // Two players in two stations at once: each receives only their own station's body.
            expect(helper, received, viewerId, Optional.of(CelestialIds.EARTH_ID), "viewer, first pass");
            expect(helper, received, otherId, Optional.of(CelestialIds.MOON_ID), "other player, first pass");
            service.pass(server);
            helper.assertTrue(received.values().stream().allMatch(List::isEmpty),
                    "An unchanged context was sent again: " + received);
            helper.assertTrue(chunks.equals(loadedChunks(levels)), "A pass loaded or unloaded chunks");

            // A warp changes the orbit.
            data.foldWarpCredits(Map.of(earthStation.stationId(), 2_000_000));
            helper.assertTrue(data.checkedRelocation(server, earthStation, CelestialIds.MOON_ID, 2_000_000)
                    == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The relocation fixture failed");
            service.pass(server);
            expect(helper, received, viewerId, Optional.of(CelestialIds.MOON_ID), "after the warp");
            expect(helper, received, otherId, null, "the other station did not change");
            StationState warped = data.find(earthStation.stationId()).orElseThrow();

            // The expansion ring: outside the original region, inside the expanded one.
            BlockPos ring = new BlockPos(warped.region().minimumX() - 64, StationLimits.LANDING_Y, pad.getZ());
            helper.assertTrue(data.findAt(ring.getX(), ring.getZ()).isEmpty(), "The ring is already claimed");
            viewer.teleportTo(space, ring.getX() + 0.5, ring.getY(), ring.getZ() + 0.5, 0, 0);
            service.pass(server);
            expect(helper, received, viewerId, Optional.empty(), "in the unclaimed ring");
            helper.assertTrue(data.checkedExpand(server, warped) == StationRegistrySavedData.CheckedUpdate.COMMITTED,
                    "The expansion fixture failed");
            service.pass(server);
            expect(helper, received, viewerId, Optional.of(CelestialIds.MOON_ID), "in the ring after the expansion");

            // A respawn and a Level change reset the client, so the next pass re-sends the context.
            service.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(viewer, false));
            service.pass(server);
            expect(helper, received, viewerId, Optional.of(CelestialIds.MOON_ID), "after a respawn");
            service.onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(viewer,
                    Level.OVERWORLD, CelestialIds.SPACE_LEVEL));
            service.pass(server);
            expect(helper, received, viewerId, Optional.of(CelestialIds.MOON_ID), "after a Level change");

            // A blocked registry gives no context; the real one restores it.
            CompoundTag future = new CompoundTag();
            future.putInt("schema_version", 99);
            StationRegistrySavedData blocked = StationRegistrySavedData.load(future);
            helper.assertTrue(!blocked.operational(), "The blocked registry fixture is operational");
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, blocked);
            try {
                service.pass(server);
                expect(helper, received, viewerId, Optional.empty(), "blocked registry, viewer");
                expect(helper, received, otherId, Optional.empty(), "blocked registry, other player");
            } finally {
                server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, data);
            }
            service.pass(server);
            expect(helper, received, viewerId, Optional.of(CelestialIds.MOON_ID), "registry restored, viewer");
            expect(helper, received, otherId, Optional.of(CelestialIds.MOON_ID), "registry restored, other player");

            // Deletion: the other player's station disappears.
            data.delete(moonStation.stationId());
            platforms.removeTemplate(space, moonStation.cell());
            removed = true;
            service.pass(server);
            expect(helper, received, otherId, Optional.empty(), "after the deletion");

            // Another Level: no station context.
            BlockPos ground = helper.absolutePos(BlockPos.ZERO).above(2);
            viewer.teleportTo(helper.getLevel(), ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0, 0);
            service.pass(server);
            expect(helper, received, viewerId, Optional.empty(), "outside Space");
        } finally {
            server.getPlayerList().remove(viewer);
            server.getPlayerList().remove(other);
            data.delete(earthStation.stationId());
            platforms.removeTemplate(space, earthStation.cell());
            if (!removed) {
                data.delete(moonStation.stationId());
                platforms.removeTemplate(space, moonStation.cell());
            }
            data.flush(server);
        }
        service.pass(server);
        helper.assertTrue(service.trackedPlayers() == server.getPlayerList().getPlayerCount(),
                "A logged-out player stayed in the last-sent map");
        service.onServerStopping(null);
        helper.assertTrue(service.trackedPlayers() == 0, "The server stop did not clear the last-sent map");
        helper.succeed();
    }

    /**
     * Final review B1: the production-registered service, driven by its registered listeners only: the
     * tick pass, a real respawn, a real Level round trip and a real logout.
     */
    @GameTest(template = "empty", batch = "station_sky_context_wiring", timeoutTicks = 200)
    public static void theRegisteredServiceFollowsARealRespawnLevelRoundTripAndLogout(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationSkyContextService service = io.github.sunthemoon.advancedrocketrycommunity.station.orbit
                .StationSkyContextRuntime.service().orElseThrow();
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationState station = new StationCreationService(platforms, body -> true)
                .create(server, UUID.randomUUID(), "Sky wiring", CelestialIds.EARTH_ID, false).station().orElseThrow();
        BlockPos pad = pad(station);
        space.getChunkAt(pad); // Test setup only.
        UUID viewerId = UUID.randomUUID();
        ServerPlayer viewer = ConnectedTestPlayers.join(server, viewerId, "skyWiring", space, pad, new ArrayList<>());
        java.util.concurrent.atomic.AtomicReference<ServerPlayer> current = new java.util.concurrent.atomic.AtomicReference<>(viewer);
        Runnable cleanup = () -> {
            ServerPlayer online = server.getPlayerList().getPlayer(viewerId);
            if (online != null) {
                server.getPlayerList().remove(online);
            }
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
            data.flush(server);
        };
        helper.runAfterDelay(2 * StationSkyContextService.PASS_TICKS + 1, () -> {
            try {
                helper.assertTrue(service.lastSent(viewerId).equals(Optional.of(CelestialIds.EARTH_ID)),
                        "The registered tick pass did not send the station's body");
                ServerPlayer respawned = server.getPlayerList().respawn(current.get(), false);
                current.set(respawned);
                helper.assertTrue(!service.tracked(viewerId), "The registered respawn listener did not forget the player");
                respawned.teleportTo(space, pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0, 0);
                helper.assertTrue(!service.tracked(viewerId),
                        "The registered Level-change listener did not forget the player");
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
            helper.runAfterDelay(2 * StationSkyContextService.PASS_TICKS + 1, () -> {
                try {
                    helper.assertTrue(service.lastSent(viewerId).equals(Optional.of(CelestialIds.EARTH_ID)),
                            "The context was not re-sent after the respawn and the Level round trip");
                    server.getPlayerList().remove(current.get());
                    helper.assertTrue(!service.tracked(viewerId), "The registered logout listener did not forget the player");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        });
    }

    private static BlockPos pad(StationState station) {
        return new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
    }

    /** {@code orbit == null} means that player must have received nothing. */
    private static void expect(GameTestHelper helper, Map<UUID, List<StationSkyContextPacket>> received, UUID player,
                               Optional<ResourceLocation> orbit, String label) {
        List<StationSkyContextPacket> packets = received.getOrDefault(player, List.of());
        if (orbit == null) {
            helper.assertTrue(packets.isEmpty(), label + ": expected nothing but got " + packets);
            return;
        }
        helper.assertTrue(packets.size() == 1 && packets.get(0).orbitBody().equals(orbit),
                label + ": expected one context " + orbit + " but got " + packets);
        packets.clear();
    }

    private static Map<ResourceKey<Level>, Integer> loadedChunks(List<ServerLevel> levels) {
        Map<ResourceKey<Level>, Integer> counts = new LinkedHashMap<>();
        levels.forEach(level -> counts.put(level.dimension(), level.getChunkSource().getLoadedChunksCount()));
        return counts;
    }
}
