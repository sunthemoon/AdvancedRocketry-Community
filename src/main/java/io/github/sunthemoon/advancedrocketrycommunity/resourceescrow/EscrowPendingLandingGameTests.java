package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Concurrent product-entry flights, not direct calls to the F1/F2 bridge. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowPendingLandingGameTests {
    private static final String BATCH = "escrow_benchmark_supply";
    /** The two-slot world root while the flights run; server thread only. */
    private static RocketTransferSavedData bounded;

    private EscrowPendingLandingGameTests() { }

    // Reuses the vent-only batch; it adds no batch name or journal-writing peer.
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void concurrentPlayerLaunchesReserveDistinctPadsAndLandAfterCallbackOverflow(GameTestHelper helper) {
        new Flights(helper).start();
    }

    /**
     * W91-R3: runs after the batch on every path, so that a failed or timed-out run leaves neither its two-slot world
     * root nor its terminal-cut hook to every later batch. After a success both are already restored.
     */
    @AfterBatch(batch = BATCH)
    public static void restoreTheWorldJournal(ServerLevel level) {
        var server = level.getServer(); var fixture = bounded; bounded = null;
        EscrowRuntime.of(server).terminalCutForTests(server, null);
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence
                .RocketJournalSchedulingGameTestSupport.restoreFullBound(server, fixture);
    }

    private static final class Flights {
        /**
         * W187: F1's source capture completes with its Level's next 20-tick force, and the ARMED ledger write must capture
         * while that proof is still valid. Three force periods bound both at paced ticks, as in W184's Moon landing rule.
         */
        private static final int F1_WINDOW_TICKS = 60;
        private final GameTestHelper helper;
        private final ServerLevel earth, moon;
        private final GameTestTickPacer pacer;
        private final List<RocketEntity> sources = new ArrayList<>(), arrivals = new ArrayList<>();
        private final List<UUID> requests = new ArrayList<>();
        private final Map<UUID, RocketTransferRecord> records = new HashMap<>();
        private final Map<String, Integer> checkpoints = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<ServerLevel, Map<BlockPos, BlockState>> terrain = new HashMap<>();
        private final List<ObservingPlayer> players = new ArrayList<>();
        private final List<io.netty.channel.embedded.EmbeddedChannel> channels = new ArrayList<>();
        private ResourceEscrowSavedData data;
        private RocketTransferSavedData fixtureJournal;
        private RocketEntity refusedSource;
        private int requested, peakPending;
        private boolean started, overflow, refusalChecked, done;

        Flights(GameTestHelper helper) {
            this.helper = helper; earth = helper.getLevel(); moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is missing");
            pacer = new GameTestTickPacer(earth.getServer());
        }
        private BlockPos source(int i) { return new BlockPos(700_008 + i * 32, 100, 700_008); }
        private BlockPos pad(int i) { return EscrowFlightBenchmarkLoad.destination(i); }
        void start() {
            for (int i = 0; i < 8; i++) { ticket(earth, source(i), true); ticket(moon, pad(i), true); }
            helper.onEachTick(this::tick);
        }
        private void tick() {
            if (done) { return; }
            pacer.pace();
            for (int i = 0; i < players.size(); i++) {
                players.get(i).connection.tick();
                Object message;
                while ((message = channels.get(i).readOutbound()) != null) {
                    if (message instanceof net.minecraft.network.protocol.game.ClientboundKeepAlivePacket keepAlive) {
                        players.get(i).connection.handleKeepAlive(new net.minecraft.network.protocol.game.ServerboundKeepAlivePacket(keepAlive.getId()));
                    }
                    io.netty.util.ReferenceCountUtil.release(message);
                }
            }
            for (int i = 0; i < 8; i++) {
                if (!loaded(earth, source(i)) || !loaded(moon, pad(i))) { return; }
            }
            var server = earth.getServer(); var runtime = EscrowRuntime.of(server);
            if (fixtureJournal == null) {
                if (!RocketTransferSavedData.get(server).retryChecked(server)) { return; }
                fixtureJournal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence
                        .RocketJournalSchedulingGameTestSupport.boundedFixture(helper, 2);
                bounded = fixtureJournal;
            }
            var journal = fixtureJournal;
            if (!started) {
                if (!journal.retryChecked(server)) { return; }
                RocketJournalGameTestSupport.occupyFirstMoonLanding(helper);
                data = ResourceEscrowSavedData.forTests(server); data.bind(server);
                players.add(connect(earth, source(0), "PadLauncher")); players.add(connect(moon, pad(0), "PadObserver"));
                for (int i = 0; i < 8; i++) {
                    clear(earth, source(i)); clear(moon, pad(i));
                    var rocket = EscrowFlightBenchmarkLoad.createFlightRocket(earth, UUID.randomUUID(), players.get(0).getUUID(), source(i));
                    var flight = rocket.flightData().orElseThrow();
                    rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1000), earth.getGameTime()));
                    var tag = rocket.saveWithoutId(new CompoundTag());
                    tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
                    rocket.load(tag); helper.assertTrue(earth.addFreshEntity(rocket), "Source publication failed");
                    sources.add(rocket); requests.add(UUID.randomUUID());
                }
                var extra = source(7).offset(4, 0, 0); clear(earth, extra);
                refusedSource = EscrowFlightBenchmarkLoad.createFlightRocket(earth, UUID.randomUUID(), players.get(1).getUUID(), extra);
                var extraFlight = refusedSource.flightData().orElseThrow();
                refusedSource.updateFlightData(extraFlight.withFuel(extraFlight.fuel().withFluidBank(false,
                        ModIdentity.id("rocket_fuel"), null, 1000), earth.getGameTime()));
                helper.assertTrue(earth.addFreshEntity(refusedSource), "Refusal fixture publication failed");
                EscrowGameTestTerminalCut.install(helper, cut -> {
                    checkpoints.merge(cut, 1, Integer::sum);
                    if (cut.equals("F1_ARM_ACK")) {
                        try (var reservation = journal.canQueueEffect(UUID.randomUUID()).orElse(null)) {
                            overflow |= reservation == null;
                        }
                        helper.assertTrue(!runtime.flightCheckpoint().pendingDestinations().isEmpty(),
                                "F1 acknowledgement released its pad before F2 publication");
                    }
                });
                started = true;
            }
            // W187: a periodic autosave stores a source's entity section and supersedes its pending F1 capture. F1 then
            // discards that launch, and the eight records never complete before the 1,200-tick timeout. Make each request
            // only when the next autosave falls after its F1 window; a pause leaves at least four launches concurrent.
            if (requested < 8 && runtime.barrier().available(earth.dimension().location().toString(), true, server.getTickCount())
                    && !GameTestAutosave.within(server, F1_WINDOW_TICKS)) {
                var rocket = sources.get(requested); var player = players.get(0);
                player.teleportTo(earth, rocket.getX(), rocket.getY() + 3, rocket.getZ(), 0, 0);
                helper.assertTrue(RocketRuntime.flightQuotes(player, rocket).moon().canLaunch(), "Product launch quote refused");
                // W182: from the player's open flight menu, as the flight screen sends it.
                UUID request = requests.get(requested);
                helper.assertTrue(RocketFlightConsoleFixtures.send(player, rocket, () -> RocketRuntime.requestFlightIntent(player,
                        rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, request)), "The flight menu did not open");
                helper.assertTrue(RocketEscrowGate.flightFrozen(rocket), "Player request did not begin F1");
                requested++;
                peakPending = Math.max(peakPending, runtime.flightCheckpoint().pendingDestinations().size());
            }
            if (requested == 8 && !refusalChecked) {
                var player = players.get(1);
                player.teleportTo(earth, refusedSource.getX(), refusedSource.getY() + 3, refusedSource.getZ(), 0, 0);
                player.messages.clear();
                helper.assertTrue(RocketFlightConsoleFixtures.send(player, refusedSource, () -> RocketRuntime.requestFlightIntent(player,
                        refusedSource.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, UUID.randomUUID())),
                        "The flight menu did not open");
                helper.assertTrue(player.messages.contains(RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE.translationKey()),
                        "All eight reservations did not return LANDING_PAD_UNAVAILABLE: " + player.messages);
                helper.assertTrue(RocketEscrowGate.idle(refusedSource), "Refused launch retained a preparation lock");
                player.teleportTo(moon, pad(0).getX() + 0.5, pad(0).getY() + 3, pad(0).getZ() + 0.5, 0, 0);
                refusalChecked = true;
            }
            for (UUID request : requests) {
                var record = journal.find(request).orElse(null);
                if (record == null) { continue; }
                var old = records.putIfAbsent(request, record);
                if (old == null) {
                    var region = RocketRegion.fromSnapshot(record.destinationSnapshot());
                    helper.assertTrue(records.entrySet().stream().filter(entry -> !entry.getKey().equals(request))
                            .noneMatch(entry -> region.overlaps(RocketRegion.fromSnapshot(entry.getValue().destinationSnapshot()))),
                            "Concurrent product launches selected overlapping destinations");
                    helper.assertTrue(record.nativeTransfer().isPresent(), "Flight bypassed governed F2");
                }
            }
            if (records.size() != 8 || requested != 8) { return; }
            arrivals.clear();
            for (int i = 0; i < 8; i++) {
                var record = records.get(requests.get(i));
                if (journal.find(record.transferId()).isPresent()
                        || data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(record.transferId()))) { return; }
                var entity = moon.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
                helper.assertTrue(entity instanceof RocketEntity, "Checked handoff lost its fixed survivor");
                var arrival = (RocketEntity) entity; var flight = arrival.flightData().orElseThrow();
                helper.assertTrue(arrival.operational() && RocketEscrowGate.idle(arrival) && flight.state() == RocketFlightState.LANDED
                        && flight.fuel().totalAmount() == EscrowFlightBenchmarkProbe.LANDED_FUEL
                        && flight.fuel().committedDebits().equals(List.of(requests.get(i)))
                        && earth.getEntity(sources.get(i).getUUID()) == null, "Landing changed resources, source absence or idle authority");
                arrivals.add(arrival);
            }
            helper.assertTrue(peakPending >= 3 && overflow && count("F2_PREPARED_ACK") < 8,
                    "Fixture did not overflow the two-slot callback queue: pending=" + peakPending + " callbacks=" + count("F2_PREPARED_ACK"));
            for (String cut : List.of("F1_ARM_ACK", "F7_ACK", "F8_JOURNAL_ACK", "F8_PRUNE_ACK")) {
                helper.assertTrue(count(cut) == 8, "Missing exact eight-flight checkpoint " + cut + ": " + count(cut));
            }
            helper.assertTrue(runtime.flightCheckpoint().pendingDestinations().isEmpty(), "Completed launches leaked pending pads");
            if (!journal.retryChecked(server)) { return; }
            runtime.terminalCutForTests(server, null);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence
                    .RocketJournalSchedulingGameTestSupport.boundedFixture(helper, 128);
            arrivals.forEach(RocketEntity::discard); refusedSource.discard();
            players.forEach(player -> server.getPlayerList().remove(player)); channels.forEach(channel -> channel.finishAndReleaseAll());
            terrain.forEach((level, blocks) -> blocks.forEach((pos, state) -> level.setBlock(pos, state, 3)));
            for (int i = 0; i < 8; i++) { ticket(earth, source(i), false); ticket(moon, pad(i), false); }
            done = true; helper.succeed();
        }
        private int count(String cut) { return checkpoints.getOrDefault(cut, 0); }
        private void clear(ServerLevel level, BlockPos pos) {
            for (int y = 0; y < 2; y++) {
                var cell = pos.above(y);
                helper.assertTrue(level.getBlockEntity(cell) == null, "Flight fixture overlaps a block entity");
                terrain.computeIfAbsent(level, ignored -> new HashMap<>()).put(cell, level.getBlockState(cell));
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        private ObservingPlayer connect(ServerLevel level, BlockPos pos, String name) {
            var player = new ObservingPlayer(level, name);
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            var channel = new io.netty.channel.embedded.EmbeddedChannel(connection); channels.add(channel);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5, 0, 0); return player;
        }
        private static boolean loaded(ServerLevel level, BlockPos pos) {
            return level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos);
        }
        private static void ticket(ServerLevel level, BlockPos pos, boolean add) {
            var chunk = new ChunkPos(pos);
            if (add) { level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); level.getChunk(chunk.x, chunk.z); }
            else { level.getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE); }
        }
    }

    private static final class ObservingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();
        ObservingPlayer(ServerLevel level, String name) {
            super(level.getServer(), level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), name));
        }
        @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean overlay) {
            if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text) { messages.add(text.getKey()); }
            super.displayClientMessage(message, overlay);
        }
    }
}
