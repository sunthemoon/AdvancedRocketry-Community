package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowGameTestProof;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFenceGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketWarmupGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W90a (Root decisions R21 items 1 to 3, 5 and 6): margin tickets, the DISCOVERY_MARGIN hold and governed admission. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedMarginGameTests {
    /** Test-only tickets: a source margin until F2 (no source warm-up exists, R22 answer 4), hold fixtures and observation. */
    private static final TicketType<UUID> FIXTURE = TicketType.create("arce_w90a_margin_fixture", UUID::compareTo);
    private EscrowGovernedMarginGameTests() { }

    // This flight reloads the shared checked journal, and its destination Level must stay empty of players and forced
    // chunks. It therefore shares the read-only registry batch, which writes no journal and adds no batch name.
    @GameTest(template = "empty", batch = "machine_menu_channel", timeoutTicks = 3_000)
    public static void borderCrossingMarginsLandOnAnEmptyMoonAndARestartReticketsTheSameColumns(GameTestHelper helper) {
        new Flight(helper).start();
    }

    // The fence batch's fixtures use isolated journals; these write only their own records and assert nothing global.
    @GameTest(template = "empty", batch = "escrow_transfer_fence", timeoutTicks = 1_300)
    public static void aRecordedSeventeenColumnDestinationHoldsWithoutMarginTickets(GameTestHelper helper) {
        new Recorded(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_transfer_fence", timeoutTicks = 1_300)
    public static void aRecordedOutsideOriginDestinationHoldsWithoutMarginTickets(GameTestHelper helper) {
        new Recorded(helper, true).start();
    }

    @GameTest(template = "empty", batch = "escrow_transfer_fence", timeoutTicks = 1_200)
    public static void governedLaunchAreaRefusalsPrecedePadSelectionAndLegacySelectionIsUnchanged(GameTestHelper helper) {
        new Admission(helper).start();
    }

    /** A governed or legacy fueled rocket with blocks at the given relative x and z corners; the first two blocks form the seat column. */
    static RocketEntity rocket(ServerLevel level, BlockPos origin, UUID ledger, int[][] corners) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(new RocketPosition(corners[0][0], 0, corners[0][1]), iron));
        blocks.add(new RocketBlock(new RocketPosition(corners[0][0], 1, corners[0][1]), iron));
        for (int i = 1; i < corners.length; i++) { blocks.add(new RocketBlock(new RocketPosition(corners[i][0], 0, corners[i][1]), iron)); }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), blocks,
                List.of(new RocketPosition(corners[0][0], 1, corners[0][1])),
                new RocketStats(blocks.size(), 100, 1_000, 2_000, 1, 1, 1, 0), level.getGameTime(),
                new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS));
        var rocket = ModEntities.ROCKET.get().create(level);
        EscrowTags.require(rocket != null, "Missing margin fixture rocket type");
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000),
                level.getGameTime()));
        if (ledger != null) {
            var tag = rocket.saveWithoutId(new CompoundTag());
            tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(ledger, 0, List.of()).encode());
            rocket.load(tag);
        }
        return rocket;
    }

    static boolean ready(ServerLevel level, List<ChunkPos> columns, int y) {
        return columns.stream().allMatch(column -> level.areEntitiesLoaded(column.toLong())
                && level.isPositionEntityTicking(new BlockPos(column.getMinBlockX(), y, column.getMinBlockZ())));
    }

    /** The operator's {@code /arce rocket inspect} output. */
    static String inspect(MinecraftServer server, UUID transfer) {
        var lines = new ArrayList<String>();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture).withPermission(2),
                "arce rocket inspect " + transfer);
        return String.join("\n", lines);
    }

    /**
     * R21 items 1 and 4 end to end: 14-block footprints give 2 x 2 margins at both ends (the product's fixed Moon pads
     * are 64 blocks apart, so the source origin shares their x and z alignment). The destination Moon has no player
     * and no forced chunk, past its 300-tick idle threshold, and the predicted pad's margin is cold. W90b: the first
     * admin request answers DESTINATION_LOADING and the product's destination warm-up tickets exactly that margin; a
     * replay of its starting request is refused through the trusted admin entry (R22 amendment 2); once READY, a fresh
     * request binds the same warm-up and passes F1 and F2; the handover leaves no warm-up ticket and both flight sets
     * in place. A test-only ticket holds the source margin until F2, as no source warm-up exists (R22 answer 4). The
     * flight's own 8 tickets then carry it to F8, through an in-process restart (T's tickets removed and the checked
     * journal reloaded) that re-tickets the same 8 columns.
     */
    private static final class Flight {
        private static final int[][] FOOTPRINT = {{0, 0}, {13, 13}};
        /**
         * W187: F1's source capture completes with its Level's next 20-tick force, and the ARMED ledger write must capture
         * while that proof is still valid. Three force periods bound both at paced ticks, as in W184's Moon landing rule.
         */
        private static final int F1_WINDOW_TICKS = 60;
        private final GameTestHelper helper;
        private final ServerLevel earth;
        private final ServerLevel moon;
        private final GameTestTickPacer pacer;
        private final BlockPos position = new BlockPos(630_002, 100, 630_002);
        private final UUID first = UUID.randomUUID(), launch = UUID.randomUUID(), key = UUID.randomUUID();
        private final Map<ServerLevel, List<ChunkPos>> fixtures = new LinkedHashMap<>();
        private ResourceEscrowSavedData data;
        private RocketEntity rocket;
        private RocketStructureSnapshot destination;
        private RocketTransferRecord prepared;
        private RocketFlightData departure;
        private RocketWarmupGameTestSupport.View warming;
        private Set<String> ticketed;
        private long moonEmptySince = -1, restartedAt, observedAt = -1;
        private int step;

        Flight(GameTestHelper helper) {
            this.helper = helper; earth = helper.getLevel(); moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is missing");
            pacer = new GameTestTickPacer(earth.getServer());
        }
        void start() { helper.onEachTick(this::tick); }

        private void tick() {
            if (step == 11) { return; }
            pacer.pace();
            var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            boolean empty = moon.players().isEmpty() && moon.getForcedChunks().isEmpty()
                    && !net.minecraftforge.common.world.ForgeChunkManager.hasForcedChunks(moon);
            if (step >= 2) { helper.assertTrue(empty, "A player or forced chunk reached the destination Level during the flight"); }
            moonEmptySince = !empty ? -1 : moonEmptySince < 0 ? server.getTickCount() : moonEmptySince;
            switch (step) {
                case 0 -> setup(server, journal);
                case 1 -> launchWhenCold(server, journal);
                case 2 -> relaunchWhenReady(server, journal);
                case 3 -> awaitF2(journal);
                case 4 -> handover(server, journal);
                case 5 -> flightTicketsOnly(server, journal);
                case 6 -> restart(server, journal);
                case 7 -> reticketed(server);
                case 8 -> landed(server, journal);
                default -> { }
            }
        }

        private void setup(MinecraftServer server, RocketTransferSavedData journal) {
            if (!journal.retryChecked(server)) { return; }
            data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            rocket = rocket(earth, position, data.ledger().id(), FOOTPRINT);
            // The product's own reservation inputs, other rockets' destination warm-ups included (W90b).
            var reservations = RocketWarmupGameTestSupport.launchReservations(server, journal,
                    rocket.assemblyTransactionId().orElseThrow());
            var selected = new RocketLandingPadSelector().select(moon, rocket.snapshot().orElseThrow(), launch, reservations,
                    moon.getGameTime(), true);
            helper.assertTrue(selected.success(), "No admissible Moon pad for the margin fixture: " + selected.detail());
            destination = selected.snapshot().orElseThrow();
            for (var end : List.of(Map.entry(earth, rocket.snapshot().orElseThrow()), Map.entry(moon, destination))) {
                var columns = RocketDiscoveryColumns.of(end.getValue());
                helper.assertTrue(columns.admissible() && columns.count() == 4, "The fixture end is not a 2 x 2 margin: " + columns);
                fixtures.put(end.getKey(), columns.columns());
            }
            // Test-only source support until F2: there is no source warm-up (R22 answer 4).
            fixtures.get(earth).forEach(column -> earth.getChunkSource().addRegionTicket(FIXTURE, column, 2, key));
            step = 1;
        }

        /** The source is ready, the Moon has been empty past its idle threshold and the predicted pad is cold. */
        private void launchWhenCold(MinecraftServer server, RocketTransferSavedData journal) {
            EscrowWarmupFixtures.requireReleasable(helper, server, List.of(destination));
            if (!ready(earth, fixtures.get(earth), position.getY()) || ready(moon, fixtures.get(moon), destination.sourceOrigin().y())) { return; }
            if (moonEmptySince < 0 || server.getTickCount() - moonEmptySince <= 300) { return; }
            if (!rocket.isAddedToWorld()) { helper.assertTrue(earth.addFreshEntity(rocket), "Margin fixture publication failed"); return; }
            if (!journal.retryChecked(server)) { return; }
            departure = rocket.flightData().orElseThrow();
            var logical = rocket.assemblyTransactionId().orElseThrow();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, first);
            helper.assertTrue(result.code() == RocketFlightRequestCode.DESTINATION_LOADING,
                    "The cold governed launch was not refused for warming: " + result.code() + " " + result.detailKey());
            warming = RocketWarmupGameTestSupport.warmup(server, logical).orElse(null);
            helper.assertTrue(warming != null && warming.state().equals("WARMING") && warming.request().equals(first)
                    && warming.player() == null && warming.region().equals(RocketRegion.fromSnapshot(destination)),
                    "The warm-up did not start on the predicted pad: " + warming);
            var expected = RocketWarmupGameTestSupport.columns(warming);
            helper.assertTrue(expected.size() == 4 && RocketWarmupGameTestSupport.warmupTicketColumns(server, logical).equals(expected),
                    "The warm-up did not ticket exactly the destination margin: " + expected);
            unlaunched(journal, logical, first);
            // R22 amendment 2 through the trusted admin entry, which never consults the operation ledger.
            var replay = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, first);
            helper.assertTrue(replay.code() == RocketFlightRequestCode.REQUEST_REPLAYED, "The starting request was reused: " + replay.code());
            helper.assertTrue(RocketWarmupGameTestSupport.warmup(server, logical).filter(warming::equals).isPresent()
                    && RocketWarmupGameTestSupport.warmupTicketColumns(server, logical).equals(expected), "The replay changed the warm-up");
            step = 2;
        }

        private void relaunchWhenReady(MinecraftServer server, RocketTransferSavedData journal) {
            var logical = rocket.assemblyTransactionId().orElseThrow();
            var current = RocketWarmupGameTestSupport.warmup(server, logical).orElse(null);
            helper.assertTrue(current != null && current.started() == warming.started(), "The warm-up ended or restarted while warming");
            unlaunched(journal, logical, first);
            if (!current.state().equals("READY")) { return; }
            var line = inspect(server, logical);
            helper.assertTrue(line.startsWith("Warm-up logical=" + logical + " state=READY request=" + first + " transfer=none level=")
                    && line.contains(" columns=4/4 ") && line.endsWith(" player=none"), "Inspect lost the warm-up line: " + line);
            if (!journal.retryChecked(server)) { return; }
            // W187: a periodic autosave stores the source's entity section and supersedes F1's pending capture. F1 then
            // discards the launch and awaitF2 waits for a record that never comes, until the 3,000-tick timeout. Request
            // the flight only when the next autosave falls after F1's window; the 600-tick warm-up outlasts the wait.
            if (GameTestAutosave.within(server, F1_WINDOW_TICKS)) { return; }
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
            helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING,
                    "The governed border-crossing launch was refused: " + result.code() + " " + result.detailKey());
            var bound = RocketWarmupGameTestSupport.warmup(server, logical).orElse(null);
            helper.assertTrue(bound != null && bound.state().equals("BOUND") && launch.equals(bound.transfer())
                    && bound.started() == warming.started() && bound.region().equals(warming.region())
                    && RocketWarmupGameTestSupport.warmupTicketColumns(server, logical).equals(RocketWarmupGameTestSupport.columns(warming)),
                    "The relaunch did not bind the same warm-up: " + bound);
            step = 3;
        }

        private void unlaunched(RocketTransferSavedData journal, UUID logical, UUID request) {
            helper.assertTrue(journal.find(request).isEmpty() && journal.findByLogicalRocket(logical).isEmpty()
                    && data.ledger().armedFlights().stream().noneMatch(arm -> arm.logical().equals(logical))
                    && RocketEscrowGate.idle(rocket) && rocket.flightData().orElseThrow().equals(departure),
                    "A warming request armed, wrote a record or changed the rocket");
        }

        private void awaitF2(RocketTransferSavedData journal) {
            var record = journal.find(launch).orElse(null);
            if (record == null || !journal.acknowledgedCurrent()
                    || rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN) { return; }
            helper.assertTrue(record.nativeTransfer().isPresent(), "Flight bypassed governed F2");
            helper.assertTrue(RocketRegion.fromSnapshot(record.destinationSnapshot()).equals(RocketRegion.fromSnapshot(destination)),
                    "The product selected another pad than the fixture's prediction");
            prepared = record;
            fixtures.get(earth).forEach(column -> earth.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key));
            step = 4;
        }

        /** The first successful ACTIVE refresh releases the warm-up in the same visit: T's 8 columns are then in place. */
        private void handover(MinecraftServer server, RocketTransferSavedData journal) {
            var logical = prepared.logicalRocketId();
            var current = RocketWarmupGameTestSupport.warmup(server, logical).orElse(null);
            if (current != null) {
                helper.assertTrue(current.state().equals("BOUND") && server.getTickCount() - current.started() < current.lifetime(),
                        "The bound warm-up changed before its handover: " + current);
                var line = inspect(server, launch);
                helper.assertTrue(line.matches("(?s)Transfer .* warmup=BOUND remaining=\\d+"), "Inspect lost the bound warm-up: " + line);
                return;
            }
            helper.assertTrue(journal.find(launch).isPresent(), "The launch ended instead of handing its warm-up over");
            helper.assertTrue(server.getTickCount() - warming.started() <= warming.lifetime(), "The warm-up expired instead of handing over");
            helper.assertTrue(RocketWarmupGameTestSupport.warmupTicketColumns(server, logical).isEmpty(), "The handover left a warm-up ticket");
            helper.assertTrue(RocketMarginGameTestSupport.flightTicketColumns(server, launch).equals(RocketMarginGameTestSupport.marginColumns(prepared)),
                    "The handover released the warm-up before T's margin tickets were in place");
            step = 5;
        }

        private void flightTicketsOnly(MinecraftServer server, RocketTransferSavedData journal) {
            var expected = RocketMarginGameTestSupport.marginColumns(prepared);
            helper.assertTrue(expected.size() == 8, "The fixture margins are not 8 distinct columns: " + expected);
            var current = RocketMarginGameTestSupport.flightTicketColumns(server, launch);
            helper.assertTrue(expected.containsAll(current), "A flight ticket outside the checked margins: " + current);
            fixtures.forEach((level, columns) -> columns.forEach(column -> {
                for (var ticket : RocketMarginGameTestSupport.tickets(level, column)) {
                    helper.assertTrue(ticket.getTicketLevel() >= 33 || RocketMarginGameTestSupport.flightTicket(ticket, launch),
                            "Another ticket keeps the margin entity-ticking: " + ticket);
                }
            }));
            if (!current.equals(expected) || !ready(earth, fixtures.get(earth), position.getY())
                    || !ready(moon, fixtures.get(moon), destination.sourceOrigin().y())) { return; }
            step = 6;
        }

        private void restart(MinecraftServer server, RocketTransferSavedData journal) {
            var state = rocket.flightData().orElseThrow().state();
            if (state == RocketFlightState.COUNTDOWN) { return; }
            var record = journal.find(launch).orElse(null);
            helper.assertTrue(record != null && record.phase() == RocketTransferPhase.PREPARED,
                    "The flight passed its source phases before the restart point: " + (record == null ? "none" : record.phase()));
            if (!journal.acknowledgedCurrent() || !data.bind(server).writerIdle()) { return; }
            ticketed = RocketMarginGameTestSupport.flightTicketColumns(server, launch);
            helper.assertTrue(ticketed.equals(RocketMarginGameTestSupport.marginColumns(record)), "The live tickets differ from the checked margins");
            // Continuation 32's in-process restart: no ticket survives, and the checked roots are read back from disk.
            RocketMarginGameTestSupport.removeFlightTickets(server, record);
            helper.assertTrue(RocketMarginGameTestSupport.flightTicketColumns(server, launch).isEmpty(), "The restart fixture left a flight ticket");
            var file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var saved = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS).read(file).orElseThrow();
            journal.stop();
            var restored = RocketTransferSavedData.load(saved);
            helper.assertTrue(restored.operational() && saved.equals(restored.save(new CompoundTag()))
                    && restored.find(launch).filter(found -> found.checksum().equals(record.checksum())).isPresent(),
                    "The reloaded journal changed the flight record");
            server.overworld().getDataStorage().set(ManagedSavedDataType.ROCKET_TRANSFERS.dataName(), restored);
            restartedAt = server.getTickCount(); step = 7;
        }

        private void reticketed(MinecraftServer server) {
            helper.assertTrue(server.getTickCount() - restartedAt < 400, "The restarted flight did not re-ticket its margins");
            if (!RocketMarginGameTestSupport.flightTicketColumns(server, launch).equals(ticketed)) { return; }
            if (!ready(earth, fixtures.get(earth), position.getY()) || !ready(moon, fixtures.get(moon), destination.sourceOrigin().y())) { return; }
            var line = inspect(server, launch);
            if (!line.contains(" wait=NONE ")) { return; }
            helper.assertTrue(line.contains(" margin=source:4/4,destination:4/4 wait=NONE tickets=8 warmup=none"),
                    "Inspect lost the margin field: " + line);
            step = 8;
        }

        private void landed(MinecraftServer server, RocketTransferSavedData journal) {
            helper.assertTrue(RocketMarginGameTestSupport.flightTicketColumns(server, launch).size() <= 8, "The flight exceeded its ticket bound");
            if (journal.find(launch).isPresent()) { return; }
            if (observedAt < 0) {
                // F8 releases every flight ticket next; an observation ticket keeps the survivor's column visible afterwards.
                observedAt = server.getTickCount();
                moon.getChunkSource().addRegionTicket(FIXTURE, survivorColumn(), 2, key);
            }
            if (data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(launch))) { return; }
            var entity = moon.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (entity == null) { helper.assertTrue(server.getTickCount() - observedAt < 400, "F8 lost the checked survivor"); return; }
            helper.assertTrue(entity instanceof RocketEntity, "The survivor is not a rocket");
            var arrival = (RocketEntity) entity; var flight = arrival.flightData().orElseThrow();
            var debited = departure.fuel().debit(prepared.sourceFlightData().plan().orElseThrow()).state();
            helper.assertTrue(arrival.operational() && RocketEscrowGate.idle(arrival) && flight.state() == RocketFlightState.LANDED
                    && flight.fuel().committedDebits().equals(List.of(launch)) && flight.fuel().totalAmount() == debited.totalAmount()
                    && flight.fuel().totalAmount() < departure.fuel().totalAmount()
                    && earth.getEntity(rocket.getUUID()) == null, "The landing changed resources, the debit or source absence");
            var region = RocketRegion.fromSnapshot(prepared.destinationSnapshot());
            var copies = moon.getEntitiesOfClass(RocketEntity.class, new AABB(region.minimum().x() - 1, moon.getMinBuildHeight(),
                    region.minimum().z() - 1, region.maximum().x() + 2, moon.getMaxBuildHeight(), region.maximum().z() + 2)).stream()
                    .filter(found -> found.assemblyTransactionId().filter(prepared.logicalRocketId()::equals).isPresent()).toList();
            helper.assertTrue(copies.equals(List.of(arrival)), "The destination margin holds other copies: " + copies.size());
            helper.assertTrue(RocketMarginGameTestSupport.flightTicketColumns(server, launch).isEmpty()
                    && RocketTransferFenceGameTests.flightTickets(server, launch) == 0
                    && RocketWarmupGameTestSupport.warmupTicketColumns(server, prepared.logicalRocketId()).isEmpty(),
                    "F8 left a flight or warm-up ticket");
            arrival.discard(); moon.getChunkSource().removeRegionTicket(FIXTURE, survivorColumn(), 2, key);
            step = 11; helper.succeed();
        }

        private ChunkPos survivorColumn() {
            var origin = prepared.destinationSnapshot().sourceOrigin(); return new ChunkPos(origin.x() >> 4, origin.z() >> 4);
        }
    }

    /**
     * R21 item 2: a governed record whose destination set is invalid (17 columns) or lacks its origin column, decoded
     * through the journal codec, as a record written before this check would be. It is a DISCOVERY_MARGIN hold: one
     * WARN, no margin ticket, no ascent, debit, destination or discard, and the record and ARMED entry unchanged. The
     * origin tickets that this process's F2 callback would have added expire within 402 ticks.
     */
    private static final class Recorded {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final boolean outside;
        private final BlockPos position, destination;
        private final int[][] footprint;
        private final UUID transfer = UUID.randomUUID(), key = UUID.randomUUID();
        private RocketEntity source;
        private ResourceEscrowSavedData data;
        private EscrowGovernedLaunch prepared;
        private RocketTransferRecord record;
        private CompoundTag frozen;
        private EscrowFlightTables.Armed arm;
        private boolean stored, held;
        private String fault;
        private int originTickets;
        private EscrowGameTestProof<EscrowBarrier.Result> sourceProof;
        private long installedAt;
        private int step;

        Recorded(GameTestHelper helper, boolean outside) {
            this.helper = helper; this.outside = outside; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            // 1 x 17: blocks 0..252 from x = 641_026 span 641_025..641_280; the source from x = 640_001 spans 16 columns.
            // Outside: blocks 5..6 from x = 643_119 miss its origin column; from x = 642_008 they keep it.
            position = outside ? new BlockPos(642_008, 100, 640_008) : new BlockPos(640_001, 100, 640_008);
            destination = outside ? new BlockPos(643_119, 100, 640_008) : new BlockPos(641_026, 100, 640_008);
            footprint = outside ? new int[][] {{5, 0}, {6, 0}} : new int[][] {{0, 0}, {252, 0}};
        }
        void start() {
            level.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(position), 2, key);
            helper.onEachTick(this::tick);
        }
        private boolean ready() {
            return level.areEntitiesLoaded(new ChunkPos(position).toLong()) && level.isPositionEntityTicking(position);
        }
        private void tick() {
            if (step == 4) { return; }
            pacer.pace(); var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            if (step == 5) { return; }
            if (step == 0) {
                if (!ready() || !journal.retryChecked(server)) { return; }
                data = ResourceEscrowSavedData.forTests(server);
                if (!data.bind(server).writerIdle()) { return; }
                prepare(journal); step = 1; return;
            }
            if (step == 1) {
                if (!stored) { checkpoint(); return; }
                install(journal); return;
            }
            helper.assertTrue(fault == null, String.valueOf(fault));
            if (step == 6) {
                helper.assertTrue(originTickets == 2, "The F2 origin tickets were not added: " + originTickets);
                RocketJournalGameTestSupport.acknowledgeFixture(helper); step = 3;
            }
            observe(server, journal);
        }

        private void prepare(RocketTransferSavedData journal) {
            source = rocket(level, position, null, footprint);
            var tag = source.saveWithoutId(new CompoundTag());
            tag.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 7, List.of()).encode());
            source.load(tag); helper.assertTrue(level.addFreshEntity(source), "Margin hold source was not added");
            frozen = EscrowNativeEnvelope.rocket(source); var from = source.snapshot().orElseThrow(); long now = level.getGameTime();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(),
                    new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
            helper.assertTrue(RocketDiscoveryColumns.of(from).admissible() && !RocketDiscoveryColumns.of(to).admissible()
                    && (outside ? RocketDiscoveryColumns.of(to).valid() : RocketDiscoveryColumns.of(to).status()
                    == RocketDiscoveryColumns.Status.OVER_BUDGET), "The fixture geometry is not the intended invalid destination");
            var fuel = source.flightData().orElseThrow().fuel();
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), to.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W90a margin hold fixture")));
            var guard = journal.acknowledgedGuard(source.assemblyTransactionId().orElseThrow()).orElseThrow();
            prepared = EscrowGovernedLaunch.prepare(data.ledger(), frozen, from, source.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, UUID.randomUUID(), new EscrowGuard(guard.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        }

        private void checkpoint() {
            if (sourceProof == null) {
                var capture = new EscrowFlightSource(source); var runtime = EscrowRuntime.of(level.getServer());
                sourceProof = new EscrowGameTestProof<>(level.getServer()::getTickCount,
                        (tick, completed) -> runtime.barrier().begin(runtime.store(level), capture.endpoint,
                                capture.frontier, capture.validator(), tick, completed),
                        EscrowBarrier.Result::confirmed, EscrowBarrier.Result::reason, result -> {
                            helper.assertTrue(result.confirmed(), "Margin hold source checkpoint failed: " + result.reason());
                            result.proof().release(); stored = true;
                        });
            }
            sourceProof.tick();
        }

        private void install(RocketTransferSavedData journal) {
            var server = level.getServer();
            helper.assertTrue(data.bind(server).writerIdle(), "Margin hold fixture raced a checked writer");
            step = 5;
            helper.assertTrue(data.bind(server).flightWrite(source.assemblyTransactionId().orElseThrow(), prepared::preflight,
                    () -> true, committed -> {
                        // A callback must not throw into the writer; faults are asserted on the next tick.
                        if (!committed) { fault = "Margin hold fixture's ledger write was not acknowledged"; step = 3; return; }
                        arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(transfer)).findFirst().orElse(null);
                        if (arm == null) { fault = "Margin hold fixture's ARMED entry is missing"; step = 3; return; }
                        var written = prepared.record(arm);
                        // Through the journal codec, as a record written before W90 or a decoded fixture is read.
                        var isolated = new RocketTransferSavedData(); isolated.put(written);
                        record = RocketTransferSavedData.load(isolated.save(new CompoundTag())).find(transfer).orElse(null);
                        if (record == null || !record.checksum().equals(written.checksum())) { fault = "The journal codec changed the record"; step = 3; return; }
                        RocketMarginGameTestSupport.addOriginTickets(server, record);
                        originTickets = RocketTransferFenceGameTests.flightTickets(server, transfer);
                        journal.put(record);
                        installedAt = server.getTickCount(); step = 6;
                    }), "Margin hold fixture's ledger write was refused");
        }

        private void observe(MinecraftServer server, RocketTransferSavedData journal) {
            helper.assertTrue(data.ledger().armedFlights().contains(arm)
                    && journal.find(transfer).filter(found -> found.checksum().equals(record.checksum())).isPresent(),
                    "The hold changed the ARMED entry or the recorded flight");
            var entity = level.getEntity(record.sourceEntityId());
            helper.assertTrue(entity instanceof RocketEntity rocket && rocket.isAlive() && frozen.equals(EscrowNativeEnvelope.rocket(rocket)),
                    "The held source ascended, was debited or was discarded");
            helper.assertTrue(level.getEntity(arm.destination()) == null, "The held record published a destination");
            int tickets = RocketTransferFenceGameTests.flightTickets(server, transfer);
            helper.assertTrue(tickets <= 2, "The held record gained margin tickets: " + tickets);
            held |= EscrowRuntime.of(server).executorHolds().reason(transfer)
                    .filter(EscrowGovernedExecutorHolds.Reason.DISCOVERY_MARGIN::equals).isPresent();
            if (server.getTickCount() - installedAt < 402) { return; }
            helper.assertTrue(held, "The recorded invalid margin was not a DISCOVERY_MARGIN hold");
            helper.assertTrue(tickets == 0, "The F2 origin tickets survived 402 ticks: " + tickets);
            var line = inspect(server, transfer);
            helper.assertTrue(line.contains(" wait=HOLD:DISCOVERY_MARGIN tickets=0") && (outside
                    ? line.matches("(?s).*,destination:\\d/1 wait=.*") : line.contains(",destination:invalid wait=")),
                    "Inspect does not report the hold: " + line);
            journal.remove(transfer); RocketJournalGameTestSupport.acknowledgeFixture(helper);
            source.discard();
            level.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(position), 2, key);
            step = 4; helper.succeed();
        }
    }

    /**
     * R21 item 3 and R22 answer 4 through the product's admin entry. Governed sources: 1 x 17 and outside-origin
     * (LAUNCH_CHUNK_BUDGET), and two columns with only the origin column entity-ticking (LAUNCH_AREA_INACTIVE). A
     * governed 4 x 4 source whose Moon pad margin is 5 x 5 answers LANDING_PAD_UNAVAILABLE with chunk_budget. The same
     * 1 x 17 rocket, ungoverned, reaches its unchanged pad selection instead. None of them arms or writes a record.
     */
    private static final class Admission {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final GameTestTickPacer pacer;
        private final UUID key = UUID.randomUUID();
        private final List<ChunkPos> tickets = new ArrayList<>();
        private final List<RocketEntity> rockets = new ArrayList<>();
        private RocketEntity wide, outside, inactive, legacy, square;
        private int step;

        Admission(GameTestHelper helper) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
        }
        void start() {
            // Origin columns only, except the 4 x 4 source, which needs all 16 columns entity-ticking.
            for (var origin : List.of(new BlockPos(660_002, 100, 660_008), new BlockPos(661_023, 100, 660_008),
                    new BlockPos(662_015, 100, 660_008), new BlockPos(663_002, 100, 660_008))) { ticket(new ChunkPos(origin)); }
            for (int x = 0; x < 4; x++) { for (int z = 0; z < 4; z++) { ticket(new ChunkPos((664_001 >> 4) + x, (664_001 >> 4) + z)); } }
            helper.onEachTick(this::tick);
        }
        private void ticket(ChunkPos column) { tickets.add(column); level.getChunkSource().addRegionTicket(FIXTURE, column, 2, key); }
        private void tick() {
            if (step == 3) { return; }
            pacer.pace(); var server = level.getServer(); var journal = RocketTransferSavedData.get(server);
            if (!ready(level, tickets, 100) || !journal.retryChecked(server)) { return; }
            if (step == 0) {
                var ledger = ResourceEscrowSavedData.forTests(server).ledger().id();
                wide = publish(new BlockPos(660_002, 100, 660_008), ledger, new int[][] {{0, 0}, {252, 0}});
                outside = publish(new BlockPos(661_023, 100, 660_008), ledger, new int[][] {{5, 0}, {6, 0}});
                inactive = publish(new BlockPos(662_015, 100, 660_008), ledger, new int[][] {{0, 0}});
                legacy = publish(new BlockPos(663_002, 100, 660_008), null, new int[][] {{0, 0}, {252, 0}});
                square = publish(new BlockPos(664_001, 100, 664_001), ledger, new int[][] {{0, 0}, {49, 49}});
                step = 1; return;
            }
            if (step == 1) {
                refused(wide, RocketFlightRequestCode.LAUNCH_CHUNK_BUDGET, null, journal);
                refused(outside, RocketFlightRequestCode.LAUNCH_CHUNK_BUDGET, null, journal);
                refused(inactive, RocketFlightRequestCode.LAUNCH_AREA_INACTIVE, null, journal);
                refused(square, RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE, RocketLandingPadSelector.CHUNK_BUDGET, journal);
                // The ungoverned rocket keeps today's selection: its 17-column region fits no fixed pad.
                refused(legacy, RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE, null, journal);
                rockets.forEach(RocketEntity::discard);
                tickets.forEach(column -> level.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key));
                step = 3; helper.succeed();
            }
        }
        private RocketEntity publish(BlockPos origin, UUID ledger, int[][] footprint) {
            var rocket = rocket(level, origin, ledger, footprint);
            helper.assertTrue(level.addFreshEntity(rocket), "Admission fixture publication failed"); rockets.add(rocket);
            return rocket;
        }
        private void refused(RocketEntity rocket, RocketFlightRequestCode code, String detail, RocketTransferSavedData journal) {
            var before = rocket.flightData().orElseThrow(); var request = UUID.randomUUID();
            var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, request);
            helper.assertTrue(result.code() == code && java.util.Objects.equals(result.detailKey(), detail),
                    "Expected " + code + " " + detail + " but got " + result.code() + " " + result.detailKey());
            helper.assertTrue(RocketEscrowGate.idle(rocket) && journal.find(request).isEmpty()
                    && rocket.flightData().orElseThrow().equals(before), "A refused launch armed, wrote or changed the rocket");
        }
    }
}
