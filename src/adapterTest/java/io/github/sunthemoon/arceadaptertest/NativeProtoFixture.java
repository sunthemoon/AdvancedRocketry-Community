package io.github.sunthemoon.arceadaptertest;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in native diagnostic in the non-distributed adapter mod; never grants product authority. */
@Mod.EventBusSubscriber(modid = AdapterTestMod.MOD_ID)
public final class NativeProtoFixture {
    private static final ChunkPos GENERATED = new ChunkPos(8_192, 8_192);
    private static final ChunkPos DISK = new ChunkPos(8_256, 8_192);
    private static final TicketType<Long> TICKET = TicketType.create("arce_proto_fixture", Comparator.<Long>naturalOrder());
    private static final long TOKEN = 28;
    private static final String OWNER = "ARCE native Proto diagnostic 28\n";

    private NativeProtoFixture() { }

    @SubscribeEvent public static void started(ServerStartedEvent event) throws IOException {
        String phase = System.getProperty("arce_adapter_test.protoFixture", "");
        if (phase.isEmpty()) { return; }
        require(phase.equals("stage") || phase.equals("resume"), "Unknown native fixture phase");
        MinecraftServer server = event.getServer();
        Path expected = Path.of("native-proto-world").toAbsolutePath().normalize();
        Path marker = expected.resolve(".arce-proto-owner");
        require(server.isSameThread() && server.getPlayerList().getPlayerCount() == 0
                        && expected.equals(server.getWorldPath(LevelResource.ROOT).toRealPath())
                        && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                        && Files.size(marker) == OWNER.length() && Files.readString(marker).equals(OWNER),
                "Native fixture requires its marked, player-free, owned world");
        // INIT_LIGHT is level 34. SPAWN/LIGHT are level 33 and already schedule accessible FULL.
        require(ChunkLevel.byStatus(ChunkStatus.INITIALIZE_LIGHT) == 34
                        && ChunkLevel.byStatus(ChunkStatus.FULL) == 33 && ChunkLevel.MAX_LEVEL == 45
                        && ChunkStatus.maxDistance() == 12 && ChunkStatus.FULL.getParent() == ChunkStatus.SPAWN,
                "Pinned native ticket/status profile changed");
        Session session = new Session(server, phase.equals("stage"));
        // Register terminal cleanup before the first native request or ticket.
        session.lifetime.install(() -> {
            MinecraftForge.EVENT_BUS.register(session);
            session.begin();
        }, session::finish);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static BlockPos cell(ChunkPos pos, boolean pending) {
        return new BlockPos(pos.getMinBlockX() + (pending ? 8 : 4), 280, pos.getMinBlockZ() + 4);
    }

    /** Event-bus ownership lasts only for this exact server; no global session map. */
    public static final class Session {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final ServerChunkCache cache;
        private final boolean stage;
        private final NativeFixtureLifetime lifetime = new NativeFixtureLifetime(System::nanoTime, TimeUnit.SECONDS.toNanos(60));
        private final ExecutorService requester = Executors.newSingleThreadExecutor(action -> {
            Thread thread = new Thread(action, "arce-owned-proto-request");
            thread.setDaemon(true);
            return thread;
        });
        private final AtomicReference<Throwable> eventFailure = new AtomicReference<>();
        private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> request;
        private CompletableFuture<Optional<CompoundTag>> read;
        private ProtoChunk predecessor;
        private ChestBlockEntity live;
        private CompoundTag pending;
        private CompoundTag published;
        private volatile ProtoChunk rawProto;
        private LevelChunk loaded;
        private int rawEvents;
        private int fullEvents;
        private int generatedTickets;
        private int diskTickets;
        private int step;
        private int requests;

        private Session(MinecraftServer server, boolean stage) {
            this.server = server;
            this.level = server.overworld();
            this.cache = level.getChunkSource();
            this.stage = stage;
        }

        private void begin() {
            try {
                require(level.getChunkSource().getChunkNow(GENERATED.x, GENERATED.z) == null
                                && level.getChunkSource().getChunkNow(DISK.x, DISK.z) == null,
                        "Fixture centers are already FULL");
                read = level.getChunkSource().chunkMap.read(stage ? GENERATED : DISK);
            } catch (RuntimeException | Error failure) { finish(failure); }
        }

        @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
            if (lifetime.isClosed() || event.getServer() != server || event.phase != TickEvent.Phase.END) { return; }
            try {
                require(server.isSameThread(), "Fixture tick left its server thread");
                if (eventFailure.get() != null) { throw new IllegalStateException("Native observation failed", eventFailure.get()); }
                require(!lifetime.expired(), "Native fixture observation exceeded 60 seconds");
                if (read != null) {
                    if (!read.isDone()) { return; }
                    Optional<CompoundTag> record = read.getNow(null);
                    read = null;
                    if (stage && step == 7) {
                        CompoundTag actual = record.orElseThrow();
                        NativeFixtureNbt.requireBounded(actual);
                        require(actual.equals(published), "Native read-after-flush differs from the staged snapshot");
                        LogUtils.getLogger().info("NATIVE_PROTO_READBACK partial_values_equal=true disk_durability=NOT_PROVED");
                        finish(null);
                    } else if (stage) {
                        require(record.isEmpty(), "Stage cannot overwrite an existing center record");
                        if (step++ == 0) { read = level.getChunkSource().chunkMap.read(DISK); return; }
                        step = 2;
                        request(GENERATED, ChunkStatus.INITIALIZE_LIGHT);
                    } else {
                        CompoundTag root = record.orElseThrow();
                        NativeFixtureNbt.requireBounded(root);
                        require(root.getString("Status").equals(ChunkStatus.INITIALIZE_LIGHT.toString()),
                                "Cold fixture did not retain its partial status");
                        step = 5;
                        request(DISK, ChunkStatus.INITIALIZE_LIGHT);
                    }
                    return;
                }
                if (request == null || !request.isDone()) { return; }
                ChunkAccess chunk = request.getNow(null).left().orElseThrow(() ->
                        new IllegalStateException("Native request returned unloaded Either"));
                request = null;
                if (step == 2 || step == 4 || step == 5) {
                    require(chunk.getClass() == ProtoChunk.class && chunk.getStatus() == ChunkStatus.INITIALIZE_LIGHT,
                            "Partial request was promoted, substituted or incomplete");
                    observedPartial((ProtoChunk) chunk);
                    if (step == 5) {
                        require(rawEvents == 1 && rawProto == predecessor && predecessor.getBlockEntities().isEmpty(),
                                "Cold partial does not match the actual raw disk event");
                        require(predecessor.getBlockEntityNbts().containsKey(cell(DISK, false))
                                        && predecessor.getBlockEntityNbts().containsKey(cell(DISK, true)),
                                "Cold partial did not retain both saved owners as pending records");
                        pending = predecessor.getBlockEntityNbt(cell(DISK, true));
                        step = 6;
                        request(DISK, ChunkStatus.FULL);
                    } else {
                        seed(step == 2 ? GENERATED : DISK);
                        if (step == 2) { step = 3; request(GENERATED, ChunkStatus.FULL); }
                        else { publishPartial(); step = 7; read = cache.chunkMap.read(DISK); }
                    }
                } else {
                    ChunkPos pos = step == 3 ? GENERATED : DISK;
                    require(chunk instanceof LevelChunk && chunk == loaded && fullEvents == 1
                                    && level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk,
                            "Native FULL event, result and installed cache disagree");
                    LevelChunk full = (LevelChunk) chunk;
                    require(full.getBlockEntityNbt(cell(pos, true)) == pending,
                            "Pending record reference was not observed in native FULL");
                    if (step == 3) {
                        require(full.getBlockEntities().get(cell(pos, false)) == live,
                                "Generated live owner reference did not survive native promotion");
                    }
                    assertChest(full, pos, false);
                    assertChest(full, pos, true);
                    LogUtils.getLogger().info("NATIVE_PROTO_FULL phase={} x={} z={} raw_events={} full_events={} "
                                    + "content_verified=true exact_constructor_pair=NOT_OBSERVED",
                            stage ? "generated" : "cold_disk", pos.x, pos.z, rawEvents, fullEvents);
                    if (step == 3) {
                        fullEvents = 0; loaded = null; predecessor = null; live = null; pending = null;
                        step = 4; request(DISK, ChunkStatus.INITIALIZE_LIGHT);
                    } else { finish(null); }
                }
            } catch (RuntimeException | Error failure) { finish(failure); }
        }

        /** Read-only debugger rendezvous: its real argument is also retained before FULL is requested. */
        private void observedPartial(ProtoChunk proto) {
            predecessor = proto;
            LogUtils.getLogger().info("NATIVE_PROTO_PARTIAL x={} z={} status={} phase={}",
                    proto.getPos().x, proto.getPos().z, proto.getStatus().toString(), stage ? "stage" : "resume");
        }

        private void request(ChunkPos pos, ChunkStatus status) {
            require(!lifetime.isClosed() && request == null && ++requests <= 4, "Overlapping or excess native request");
            ticket(pos, ChunkLevel.byStatus(status));
            // The off-thread public branch queues native work; no main-thread managedBlock/join/get.
            request = CompletableFuture.supplyAsync(() -> lifetime.enqueue(() -> {
                require(!server.isSameThread(), "Main-thread requester");
                return cache.getChunkFuture(pos.x, pos.z, status, true);
            }), requester).thenCompose(future -> future);
        }

        private void ticket(ChunkPos pos, int ticketLevel) {
            require((pos.equals(GENERATED) || pos.equals(DISK)) && (ticketLevel == 33 || ticketLevel == 34),
                    "Ticket outside frozen centers or levels");
            int bit = ticketLevel == 34 ? 1 : 2;
            int old = pos.equals(GENERATED) ? generatedTickets : diskTickets;
            require((old & bit) == 0, "Repeated owned ticket");
            var manager = level.getChunkSource().chunkMap.getDistanceManager();
            // Retain both bounded tickets until terminal cleanup; lower native level wins.
            // Mark ownership before insertion so cleanup also covers a partially throwing insertion.
            if (pos.equals(GENERATED)) { generatedTickets |= bit; } else { diskTickets |= bit; }
            manager.addTicket(TICKET, pos, ticketLevel, TOKEN);
        }

        private void seed(ChunkPos pos) {
            require(predecessor.getBlockEntities().size() <= 64 && predecessor.getBlockEntityNbts().size() <= 64,
                    "Native partial owner maps exceed observation bound");
            for (boolean packed : new boolean[]{false, true}) {
                BlockPos at = cell(pos, packed);
                require(predecessor.getBlockState(at).isAir() && predecessor.getBlockEntity(at) == null
                                && predecessor.getBlockEntityNbt(at) == null, "Fixture cell is not vacant");
                predecessor.setBlockState(at, Blocks.CHEST.defaultBlockState(), false);
                ChestBlockEntity chest = new ChestBlockEntity(at, Blocks.CHEST.defaultBlockState());
                chest.setItem(0, new ItemStack(packed ? Items.EMERALD : Items.DIAMOND, packed ? 3 : 2));
                if (packed) {
                    pending = chest.saveWithFullMetadata();
                    NativeFixtureNbt.requireBounded(pending);
                    predecessor.setBlockEntityNbt(pending);
                } else { live = chest; predecessor.setBlockEntity(chest); }
            }
            predecessor.setUnsaved(true);
        }

        private void publishPartial() {
            CompoundTag serialized = ChunkSerializer.write(level, predecessor);
            NativeFixtureNbt.requireBounded(serialized);
            CompoundTag exclusive = serialized.copy();
            require(exclusive.getString("Status").equals(ChunkStatus.INITIALIZE_LIGHT.toString()),
                    "Stage serialization is not the selected native partial");
            // Test staging only, not an ordinary save event/final-writer qualification. Never mutate after write.
            published = exclusive;
            cache.chunkMap.write(DISK, exclusive);
            cache.chunkMap.flushWorker();
            LogUtils.getLogger().info("NATIVE_PROTO_STAGED x={} z={} status={} explicit_test_publication=true flush_returned=true",
                    DISK.x, DISK.z, predecessor.getStatus().toString());
        }

        @SubscribeEvent public void raw(ChunkDataEvent.Load event) {
            if (lifetime.isClosed() || stage || !event.getChunk().getPos().equals(DISK)) { return; }
            try {
                // Plain Proto's native event Level is null. Bind its exact object to our completed cache result later.
                require(step == 5 && server.isSameThread() && ++rawEvents == 1
                                && event.getChunk().getClass() == ProtoChunk.class,
                        "Unexpected raw disk event identity, thread or count");
                require(event.getData().getString("Status").equals(ChunkStatus.INITIALIZE_LIGHT.toString()),
                        "Raw disk event did not expose the staged partial status");
                rawProto = (ProtoChunk) event.getChunk();
            } catch (RuntimeException | Error failure) { eventFailure.compareAndSet(null, failure); }
        }

        @SubscribeEvent public void full(ChunkEvent.Load event) {
            if (lifetime.isClosed() || !event.getChunk().getPos().equals(stage && step == 3 ? GENERATED : DISK)) { return; }
            try {
                require(event.getLevel() == level && server.isSameThread() && (step == 3 || step == 6)
                                && ++fullEvents == 1 && event.getChunk() instanceof LevelChunk,
                        "Unexpected native FULL event identity, phase, thread or count");
                loaded = (LevelChunk) event.getChunk();
            } catch (RuntimeException | Error failure) { eventFailure.compareAndSet(null, failure); }
        }

        private void assertChest(LevelChunk full, ChunkPos pos, boolean packed) {
            require(full.getBlockEntity(cell(pos, packed)) instanceof ChestBlockEntity, "Native owner did not resolve");
            ChestBlockEntity chest = (ChestBlockEntity) full.getBlockEntity(cell(pos, packed));
            ItemStack stack = chest.getItem(0);
            require(stack.is(packed ? Items.EMERALD : Items.DIAMOND) && stack.getCount() == (packed ? 3 : 2),
                    "Native promotion changed controlled owner content");
        }

        @SubscribeEvent public void stopping(ServerStoppingEvent event) {
            if (event.getServer() == server && !lifetime.isClosed()) { finish(new IllegalStateException("Server stopped before fixture completion")); }
        }

        private void finish(Throwable primary) {
            if (!lifetime.close()) { return; }
            if (primary == null) { primary = eventFailure.get(); }
            if (primary == null && lifetime.expired()) {
                primary = new IllegalStateException("Native fixture terminal observation exceeded 60 seconds");
            }
            try {
                primary = cleanup(primary, () -> MinecraftForge.EVENT_BUS.unregister(this));
                primary = cleanup(primary, () -> { if (request != null) { request.cancel(false); } });
                primary = cleanup(primary, requester::shutdownNow);
                var manager = level.getChunkSource().chunkMap.getDistanceManager();
                for (int ticketLevel : new int[]{34, 33}) {
                    int bit = ticketLevel == 34 ? 1 : 2;
                    if ((generatedTickets & bit) != 0) {
                        primary = cleanup(primary, () -> manager.removeTicket(TICKET, GENERATED, ticketLevel, TOKEN));
                    }
                    if ((diskTickets & bit) != 0) {
                        primary = cleanup(primary, () -> manager.removeTicket(TICKET, DISK, ticketLevel, TOKEN));
                    }
                }
            } catch (RuntimeException | Error cleanup) {
                if (primary == null) { primary = cleanup; } else { primary.addSuppressed(cleanup); }
            } finally {
                predecessor = null; live = null; pending = null; published = null; rawProto = null; loaded = null; request = null; read = null;
                if (primary == null && lifetime.expired()) {
                    primary = new IllegalStateException("Native fixture terminal cleanup exceeded 60 seconds");
                }
                if (primary == null) {
                    LogUtils.getLogger().info("NATIVE_PROTO_FIXTURE_COMPLETE phase={} explicit_tickets_released=true "
                                    + "native_queued_retirement=PROCESS_STOP exact_constructor_pair=NOT_OBSERVED",
                            stage ? "stage" : "resume");
                } else { LogUtils.getLogger().error("NATIVE_PROTO_FIXTURE_FAILED phase={}", stage ? "stage" : "resume", primary); }
                server.halt(false);
            }
        }

        private Throwable cleanup(Throwable primary, Runnable action) {
            return NativeFixtureLifetime.cleanup(primary, action);
        }
    }
}
