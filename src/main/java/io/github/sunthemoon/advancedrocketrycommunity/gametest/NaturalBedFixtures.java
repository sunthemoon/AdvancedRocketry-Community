package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.eventbus.api.IEventListener;

/** Owns the connected player, supplied-room fixtures, time and chunk tickets for the W37B native checks. */
final class NaturalBedFixtures {
    private NaturalBedFixtures() { }

    /** Visits and save-all must affect only this test's copy, never the next batch's discovery authority. */
    static final class DiscoveryScope implements AutoCloseable {
        private final MinecraftServer server;
        private final CelestialSavedData original;
        private boolean closed;

        DiscoveryScope(MinecraftServer server) { this(server, false); }

        DiscoveryScope(MinecraftServer server, boolean empty) {
            this.server = server;
            original = CelestialSavedData.get(server);
            var copy = empty ? CelestialSavedData.create() : CelestialSavedData.load(original.save(new CompoundTag()));
            server.overworld().getDataStorage().set(CelestialSavedData.DATA_NAME, copy);
        }

        @Override
        public void close() {
            if (closed) { return; }
            closed = true;
            server.overworld().getDataStorage().set(CelestialSavedData.DATA_NAME, original);
            // A native save-all may already have persisted the copy; checked restoration must replace that too.
            original.flush(server);
        }
    }

    /** A previously registered listener cancels, and a terminal listener uncancels, the actual save-all command. */
    static void saveWithReversedCommandCancellation(MinecraftServer server) {
        var source = server.createCommandSourceStack();
        var probe = new CommandEvent(server.getCommands().getDispatcher().parse("save-all", source));
        try {
            var field = MinecraftForge.EVENT_BUS.getClass().getDeclaredField("busID");
            field.setAccessible(true);
            IEventListener[] listeners = probe.getListenerList().getListeners(field.getInt(MinecraftForge.EVENT_BUS));
            IEventListener first = listeners[0];
            IEventListener last = listeners[listeners.length - 1];
            listeners[0] = event -> { event.setCanceled(true); first.invoke(event); };
            listeners[listeners.length - 1] = event -> { last.invoke(event); event.setCanceled(false); };
            try {
                if (server.getCommands().performPrefixedCommand(source, "save-all") <= 0) {
                    throw new IllegalStateException("The uncancelled native save-all command did not execute");
                }
            }
            finally { listeners[0] = first; listeners[listeners.length - 1] = last; }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Pinned CommandEvent dispatch fixture no longer matches EventBus", failure);
        }
    }

    static final class Room {
        final ServerLevel level;
        final BlockPos vent;

        Room(ServerLevel level, BlockPos vent) { this.level = level; this.vent = vent; }

        BlockPos head() { return SleepRespawnGameTests.bedHead(vent); }

        /**
         * The vent's own block entity ticks. Vanilla ticks it only in a block-ticking chunk whose entity sections have
         * loaded, and a newly forced chunk loads them asynchronously. Until then the atmosphere service has seen only
         * the fixture's one manual vent tick; its next tick drops the unobserved vent and the room returns to vacuum.
         */
        boolean live() {
            LevelChunk chunk = level.getChunkSource().getChunkNow(vent.getX() >> 4, vent.getZ() >> 4);
            return chunk != null && chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                    && level.areEntitiesLoaded(ChunkPos.asLong(vent));
        }

        /** The vent's chunk has finished its block-ticking promotion: every neighbour is a full chunk. */
        boolean blockTicking() {
            return level.getChunkSource().isPositionTicking(ChunkPos.asLong(vent));
        }
    }

    static final class Fixture implements AutoCloseable {
        /** Well inside the server watchdog's 60 s tick limit. */
        private static final long SETUP_LIMIT_NANOS = 30_000_000_000L;
        private static final long IDLE_PARK_NANOS = 500_000L;

        final MinecraftServer server;
        final List<Room> rooms = new ArrayList<>();
        final List<Runnable> cleanup = new ArrayList<>();
        ServerPlayer player;
        final long originalTime;
        final DiscoveryScope discovery;
        private boolean closed;

        Fixture(GameTestHelper helper, String name, List<ResourceKey<Level>> hosts) {
            server = helper.getLevel().getServer();
            originalTime = server.overworld().getDayTime();
            discovery = new DiscoveryScope(server);
            try {
                player = ConnectedTestPlayers.join(server, UUID.randomUUID(), name, helper.getLevel(),
                        helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, new ArrayList<>(), false);
                server.overworld().setDayTime(18000);
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
                awaitBlockTicking();
            } catch (RuntimeException failure) { close(); throw failure; }
        }

        /**
         * Fixture setup, like the synchronous {@code getChunk} in {@code force}: each room chunk finishes its
         * block-ticking promotion before the first test tick. That needs the neighbouring terrain generated, which
         * in five noise Levels is seconds of worker time on a slow host; as a paced-tick wait it took most of the
         * 60 ticks (W74). Meanwhile the Levels' main-thread chunk tasks run, as they do during a synchronous chunk
         * load. The vents' entity sections finish loading in the next ticks, and readiness still waits for them.
         */
        private void awaitBlockTicking() {
            long deadline = System.nanoTime() + SETUP_LIMIT_NANOS;
            while (!rooms.stream().allMatch(Room::blockTicking)) {
                if (System.nanoTime() - deadline > 0) {
                    throw new IllegalStateException("The bed rooms' chunks did not become block-ticking: " + status());
                }
                boolean ran = false;
                for (Room room : rooms) {
                    ran |= room.level.getChunkSource().pollTask();
                }
                if (!ran) {
                    LockSupport.parkNanos(IDLE_PARK_NANOS);
                }
            }
        }

        boolean ready() {
            return rooms.stream().allMatch(this::ready);
        }

        /** The room is live and its bed cells are breathable, so a bed call now meets the running authority. */
        boolean ready(Room room) {
            if (!room.live()) {
                return false;
            }
            if (room.level.dimension().equals(ExoplanetContent.TAU_CETI_F_LEVEL)) {
                // The production authority idles vents in comfortable, breathable ambient air. There is no
                // supplied volume to wait for on f; both native bed cells must still report breathable air.
                return AtmosphereRuntime.breathabilityAt(room.level, room.head()).orElse(null)
                        == BreathabilityState.BREATHABLE
                        && AtmosphereRuntime.breathabilityAt(room.level, room.head().west()).orElse(null)
                        == BreathabilityState.BREATHABLE;
            }
            return SleepRespawnGameTests.suppliedBed(room.level, room.vent);
        }

        String status() {
            return rooms.stream().map(room -> room.level.dimension().location() + ":ticking=" + room.blockTicking()
                    + ",live=" + room.live() + ",air="
                    + AtmosphereRuntime.breathabilityAt(room.level, room.head()) + ",supplied="
                    + SleepRespawnGameTests.suppliedBed(room.level, room.vent) + ",metrics="
                    + AtmosphereRuntime.metrics(room.level)).toList().toString();
        }

        @Override
        public void close() {
            if (closed) { return; }
            closed = true;
            try {
                if (player != null) {
                    if (player.isSleeping()) { player.stopSleepInBed(true, true); }
                    server.getPlayerList().remove(player);
                }
            } finally {
                try { cleanup.forEach(Runnable::run); }
                finally {
                    server.overworld().setDayTime(originalTime);
                    discovery.close();
                }
            }
        }
    }
}
