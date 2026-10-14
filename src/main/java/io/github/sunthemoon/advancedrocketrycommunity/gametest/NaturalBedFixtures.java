package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** Owns the connected player, supplied-room fixtures, time and chunk tickets for the W37B native checks. */
final class NaturalBedFixtures {
    private NaturalBedFixtures() { }

    static final class Room {
        final ServerLevel level;
        final BlockPos vent;

        Room(ServerLevel level, BlockPos vent) { this.level = level; this.vent = vent; }

        BlockPos head() { return SleepRespawnGameTests.bedHead(vent); }
    }

    static final class Fixture implements AutoCloseable {
        final MinecraftServer server;
        final List<Room> rooms = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        ServerPlayer player;
        final long originalTime;

        Fixture(GameTestHelper helper, String name, List<ResourceKey<Level>> hosts) {
            server = helper.getLevel().getServer();
            originalTime = server.overworld().getDayTime();
            player = ConnectedTestPlayers.join(server, UUID.randomUUID(), name, helper.getLevel(),
                    helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, new ArrayList<>(), false);
            server.overworld().setDayTime(18000);
            try {
                for (ResourceKey<Level> key : hosts) {
                    ServerLevel level = NonPlayerAtmosphereGameTests.level(helper, key);
                    BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
                    BlockPos vent = new BlockPos(column.getX(), 200, column.getZ());
                    if (key.equals(CelestialIds.SPACE_LEVEL)) {
                        BlockPos bed = SleepRespawnGameTests.commitStation(helper, StationRegistrySavedData.get(server),
                                UUID.randomUUID(), player.getUUID(), cleanup);
                        vent = bed.offset(-1, -1, -1);
                    }
                    BlockPos center = vent;
                    Set<ChunkPos> forced = new LinkedHashSet<>();
                    SleepRespawnGameTests.force(level, center, 3, forced);
                    cleanup.add(() -> {
                        SleepRespawnGameTests.clearBox(level, center.offset(-2, 0, -2), center.offset(2, 3, 2));
                        forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
                    });
                    SleepRespawnGameTests.roomWithBed(level, center, 2, 3);
                    rooms.add(new Room(level, center));
                }
            } catch (RuntimeException failure) { close(); throw failure; }
        }

        boolean ready() {
            return rooms.stream().allMatch(room -> {
                if (room.level.dimension().equals(ExoplanetContent.TAU_CETI_F_LEVEL)) {
                    // The production authority idles vents in comfortable, breathable ambient air. There is no
                    // supplied volume to wait for on f; both native bed cells must still report breathable air.
                    return AtmosphereRuntime.breathabilityAt(room.level, room.head()).orElse(null)
                            == BreathabilityState.BREATHABLE
                            && AtmosphereRuntime.breathabilityAt(room.level, room.head().west()).orElse(null)
                            == BreathabilityState.BREATHABLE;
                }
                return SleepRespawnGameTests.suppliedBed(room.level, room.vent);
            });
        }

        String status() {
            return rooms.stream().map(room -> room.level.dimension().location() + ":air="
                    + AtmosphereRuntime.breathabilityAt(room.level, room.head()) + ",supplied="
                    + SleepRespawnGameTests.suppliedBed(room.level, room.vent) + ",metrics="
                    + AtmosphereRuntime.metrics(room.level)).toList().toString();
        }

        @Override
        public void close() {
            if (player.isSleeping()) { player.stopSleepInBed(true, true); }
            server.getPlayerList().remove(player);
            cleanup.forEach(Runnable::run);
            server.overworld().setDayTime(originalTime);
        }
    }
}
