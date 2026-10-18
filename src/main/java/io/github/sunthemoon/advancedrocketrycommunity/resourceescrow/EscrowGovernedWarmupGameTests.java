package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowWarmupFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMarginGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketWarmupGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W90b (Root decisions R21 item 4 and R22 amendments 1 to 4): the governed destination warm-up through the product's
 * player and admin entries. Each scenario has its own batch: they share the Moon's eight fixed pads and the global cap.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowGovernedWarmupGameTests {
    private EscrowGovernedWarmupGameTests() { }

    @GameTest(template = "empty", batch = "rocket_warmup_kept_pad", timeoutTicks = 2_400)
    public static void aWarmupKeepsItsPadThroughReservationChurnAndHandsOverAfterAPlayerRelaunch(GameTestHelper helper) {
        new KeptPad(helper).start();
    }

    @GameTest(template = "empty", batch = "rocket_warmup_capacity", timeoutTicks = 2_400)
    public static void theCapAndThePerPlayerLimitRefuseColdDestinationsAndEveryWarmupEnds(GameTestHelper helper) {
        new Capacity(helper).start();
    }

    /**
     * R22 amendment 1 and design 7.3, through the player entry. A decoy D warms the first Moon pad, so L's warm-up takes
     * the second. D's pad is then invalidated by a non-air block and D is replaced onto the third; with the first pad
     * free again, ordinary selection would move L there, but five fresh relaunches and a replay of L's starting request
     * all keep L's exact region, tickets and deadline while WARMING. A second decoy takes the first pad and is removed
     * after L is READY; L's relaunch then binds the same warm-up. The player leaves at once, the BOUND warm-up hands
     * over after T's first margin refresh, and the flight lands on the kept pad.
     */
    private static final class KeptPad {
        private final GameTestHelper helper;
        private final ServerLevel earth, moon;
        private final GameTestTickPacer pacer;
        private final UUID key = UUID.randomUUID(), start = UUID.randomUUID(), launch = UUID.randomUUID();
        private final List<BlockPos> sources = List.of(new BlockPos(652_008, 100, 652_008), new BlockPos(652_040, 100, 652_008),
                new BlockPos(652_072, 100, 652_008));
        private ResourceEscrowSavedData data;
        private RocketEntity rocket, decoy, second;
        private Player pilot;
        private List<RocketStructureSnapshot> pads;
        private RocketWarmupGameTestSupport.View warming;
        private RocketFlightData departure;
        private RocketTransferRecord prepared;
        private long churnedAt, observedAt = -1;
        private int step;

        KeptPad(GameTestHelper helper) {
            this.helper = helper; earth = helper.getLevel(); moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level is missing");
            pacer = new GameTestTickPacer(earth.getServer());
        }
        void start() { helper.onEachTick(this::tick); }

        private void tick() {
            if (step == 9) { return; }
            pacer.pace();
            if (pilot != null) { pilot.pump(); }
            var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            switch (step) {
                case 0 -> setup(server, journal);
                case 1 -> churnWhenCold(server, journal);
                case 2 -> awaitReady(server);
                case 3 -> relaunchAfterRemoval(server, journal);
                case 4 -> awaitF2(server, journal);
                case 5 -> handover(server, journal);
                case 6 -> landed(server, journal);
                case 7 -> cleanup(server);
                default -> { }
            }
        }

        private void setup(MinecraftServer server, RocketTransferSavedData journal) {
            if (!journal.retryChecked(server)) { return; }
            data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            sources.forEach(position -> earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(position), 2, key));
            rocket = governed(earth, sources.get(0), data.ledger().id());
            decoy = governed(earth, sources.get(1), data.ledger().id());
            second = governed(earth, sources.get(2), data.ledger().id());
            pilot = Player.connect(earth, sources.get(0), "WarmupPilot");
            step = 1;
        }

        private void churnWhenCold(MinecraftServer server, RocketTransferSavedData journal) {
            if (sources.stream().anyMatch(position -> !columnReady(earth, position))) { return; }
            if (!rocket.isAddedToWorld()) {
                for (var added : List.of(rocket, decoy, second)) { helper.assertTrue(earth.addFreshEntity(added), "Warm-up source publication failed"); }
                return;
            }
            // Earlier batches' warm-ups have ended, and the first three pads are cold.
            if (!journal.retryChecked(server) || !RocketWarmupGameTestSupport.live(server).isEmpty()) { return; }
            pads = predict(server, moon, rocket, 3);
            helper.assertTrue(pads != null, "Fewer than three admissible Moon pads");
            if (pads.stream().anyMatch(pad -> !cold(server, pad))) { return; }
            long now = server.getTickCount();
            departure = rocket.flightData().orElseThrow();

            var refused = RocketRuntime.requestAdminFlight(decoy, RocketDestination.MOON, UUID.randomUUID());
            helper.assertTrue(refused.code() == RocketFlightRequestCode.DESTINATION_LOADING, "The decoy did not warm: " + refused.code());
            helper.assertTrue(region(pads.get(0)).equals(warmup(server, decoy).region()), "The decoy did not take the first pad");

            RocketRuntime.requestFlightIntent(pilot, rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, start);
            helper.assertTrue(pilot.last().equals(LOADING), "The player's cold launch answered " + pilot.last());
            warming = warmup(server, rocket);
            helper.assertTrue(warming != null && warming.state().equals("WARMING") && warming.request().equals(start)
                    && warming.started() == now && pilot.getUUID().equals(warming.player())
                    && warming.region().equals(region(pads.get(1))), "The second rocket did not warm the next pad: " + warming);
            var columns = RocketWarmupGameTestSupport.columns(warming);
            helper.assertTrue(tickets(server, rocket).equals(columns) && columns.size() == 1, "L's warm-up tickets differ: " + columns);

            // A separately invalidated candidate: a non-air block that blocks no motion, so the surface height is unchanged.
            var blocked = new BlockPos(pads.get(0).sourceOrigin().x(), pads.get(0).sourceOrigin().y(), pads.get(0).sourceOrigin().z());
            var previous = moon.getBlockState(blocked);
            moon.setBlock(blocked, Blocks.STRUCTURE_VOID.defaultBlockState(), 3);
            var replacement = UUID.randomUUID();
            var replaced = RocketRuntime.requestAdminFlight(decoy, RocketDestination.MOON, replacement);
            moon.setBlock(blocked, previous, 3);
            var moved = warmup(server, decoy);
            helper.assertTrue(replaced.code() == RocketFlightRequestCode.DESTINATION_LOADING && moved != null
                    && moved.region().equals(region(pads.get(2))) && moved.request().equals(replacement) && moved.started() == now
                    && tickets(server, decoy).equals(RocketWarmupGameTestSupport.columns(moved)),
                    "The invalidated pad was not replaced after its release: " + moved);

            // Without its kept pad, L would now move to the free first pad.
            helper.assertTrue(region(pads.get(0)).equals(ordinary(server, moon, rocket)), "The first pad did not become free again");
            for (int i = 0; i < 5; i++) {
                RocketRuntime.requestFlightIntent(pilot, rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, UUID.randomUUID());
                helper.assertTrue(pilot.last().equals(LOADING), "A repeated request answered " + pilot.last());
                kept(server, columns);
            }
            RocketRuntime.requestFlightIntent(pilot, rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, start);
            helper.assertTrue(pilot.last().equals(REPLAYED), "The starting request was reused: " + pilot.last());
            kept(server, columns);

            // An earlier reservation that disappears while L is READY.
            var later = RocketRuntime.requestAdminFlight(second, RocketDestination.MOON, UUID.randomUUID());
            helper.assertTrue(later.code() == RocketFlightRequestCode.DESTINATION_LOADING
                    && region(pads.get(0)).equals(warmup(server, second).region()), "The second decoy did not take the first pad");
            churnedAt = now; step = 2;
        }

        /** L's warm-up keeps its region, tickets, starting request and deadline. */
        private void kept(MinecraftServer server, java.util.Set<String> columns) {
            var current = warmup(server, rocket);
            helper.assertTrue(current != null && current.started() == warming.started() && current.request().equals(start)
                    && current.region().equals(warming.region()) && tickets(server, rocket).equals(columns),
                    "L's warm-up moved, restarted or was renewed: " + current);
        }

        private void awaitReady(MinecraftServer server) {
            kept(server, RocketWarmupGameTestSupport.columns(warming));
            var held = warmup(server, second);
            helper.assertTrue(held != null && region(pads.get(0)).equals(held.region()), "The second decoy lost its pad early");
            if (!warmup(server, rocket).state().equals("READY") || server.getTickCount() - churnedAt <= 20) { return; }
            helper.assertTrue(pilot.count(READY) == 1, "The READY message was not sent exactly once: " + pilot.count(READY));
            var line = EscrowGovernedMarginGameTests.inspect(server, logical(rocket));
            helper.assertTrue(line.startsWith("Warm-up logical=" + logical(rocket) + " state=READY request=" + start + " transfer=none ")
                    && line.contains(" columns=1/1 ") && line.endsWith(" player=" + pilot.getUUID()), "Inspect lost the warm-up line: " + line);
            second.discard(); step = 3;
        }

        private void relaunchAfterRemoval(MinecraftServer server, RocketTransferSavedData journal) {
            kept(server, RocketWarmupGameTestSupport.columns(warming));
            if (warmup(server, second) != null) { return; }
            helper.assertTrue(tickets(server, second).isEmpty()
                    && RocketWarmupGameTestSupport.launchReservations(server, journal, logical(rocket)).stream().noneMatch(region(pads.get(0))::equals),
                    "The removed rocket's warm-up kept its tickets or reservation");
            if (!journal.retryChecked(server)) { return; }
            RocketRuntime.requestFlightIntent(pilot, rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, launch);
            helper.assertTrue(pilot.last().equals(PENDING), "The READY relaunch answered " + pilot.last());
            var bound = warmup(server, rocket);
            helper.assertTrue(bound != null && bound.state().equals("BOUND") && launch.equals(bound.transfer())
                    && bound.started() == warming.started() && bound.region().equals(warming.region())
                    && tickets(server, rocket).equals(RocketWarmupGameTestSupport.columns(warming)), "The relaunch did not bind the kept warm-up: " + bound);
            helper.assertTrue(pilot.count(READY) == 1, "The READY message repeated");
            // A BOUND warm-up no longer needs its player.
            pilot.leave(); pilot = null; step = 4;
        }

        private void awaitF2(MinecraftServer server, RocketTransferSavedData journal) {
            var current = warmup(server, rocket);
            helper.assertTrue(current == null || current.state().equals("BOUND") && launch.equals(current.transfer()),
                    "The player's disconnect ended a BOUND warm-up: " + current);
            var record = journal.find(launch).orElse(null);
            if (record == null || !journal.acknowledgedCurrent() || rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN) { return; }
            helper.assertTrue(record.nativeTransfer().isPresent() && region(record.destinationSnapshot()).equals(warming.region()),
                    "The flight left its kept pad or bypassed governed F2");
            prepared = record; step = 5;
        }

        private void handover(MinecraftServer server, RocketTransferSavedData journal) {
            var current = warmup(server, rocket);
            if (current != null) {
                helper.assertTrue(current.state().equals("BOUND"), "The bound warm-up changed before its handover: " + current);
                var line = EscrowGovernedMarginGameTests.inspect(server, launch);
                helper.assertTrue(line.matches("(?s)Transfer .* warmup=BOUND remaining=\\d+"), "Inspect lost the bound warm-up: " + line);
                return;
            }
            helper.assertTrue(journal.find(launch).isPresent() && server.getTickCount() - warming.started() <= warming.lifetime(),
                    "The warm-up ended without its handover");
            helper.assertTrue(tickets(server, rocket).isEmpty()
                    && RocketMarginGameTestSupport.flightTicketColumns(server, launch).equals(RocketMarginGameTestSupport.marginColumns(prepared)),
                    "The handover released the warm-up before T's margin tickets were in place");
            step = 6;
        }

        private void landed(MinecraftServer server, RocketTransferSavedData journal) {
            if (journal.find(launch).isPresent()) { return; }
            var column = new ChunkPos(prepared.destinationSnapshot().sourceOrigin().x() >> 4, prepared.destinationSnapshot().sourceOrigin().z() >> 4);
            if (observedAt < 0) { observedAt = server.getTickCount(); moon.getChunkSource().addRegionTicket(FIXTURE, column, 2, key); }
            if (data.ledger().armedFlights().stream().anyMatch(arm -> arm.transfer().equals(launch))) { return; }
            var entity = moon.getEntity(prepared.nativeTransfer().orElseThrow().armed().destination());
            if (entity == null) { helper.assertTrue(server.getTickCount() - observedAt < 400, "F8 lost the checked survivor"); return; }
            var arrival = (RocketEntity) entity; var flight = arrival.flightData().orElseThrow();
            var debited = departure.fuel().debit(prepared.sourceFlightData().plan().orElseThrow()).state();
            helper.assertTrue(arrival.operational() && RocketEscrowGate.idle(arrival) && flight.state() == RocketFlightState.LANDED
                    && flight.fuel().committedDebits().equals(List.of(launch)) && flight.fuel().totalAmount() == debited.totalAmount()
                    && region(arrival.snapshot().orElseThrow()).equals(warming.region()), "The landing changed resources or its pad");
            helper.assertTrue(tickets(server, rocket).isEmpty() && RocketMarginGameTestSupport.flightTicketColumns(server, launch).isEmpty(),
                    "F8 left a flight or warm-up ticket");
            arrival.discard(); moon.getChunkSource().removeRegionTicket(FIXTURE, column, 2, key);
            decoy.discard(); step = 7;
        }

        /** The removed decoy's warm-up ends at the next service tick, with every ticket. */
        private void cleanup(MinecraftServer server) {
            if (warmup(server, decoy) != null) { return; }
            helper.assertTrue(tickets(server, decoy).isEmpty() && tickets(server, second).isEmpty(), "A decoy's warm-up ticket survived");
            sources.forEach(position -> earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(position), 2, key));
            step = 9; helper.succeed();
        }
    }

    /**
     * R22 answer 2 and design 7.3 and 7.6. One player's two cold launches warm two pads and a third is refused with
     * DESTINATION_LOADING_LIMIT; six admin launches fill the global cap of eight; a ninth, to a cold station pad, is
     * refused without a ticket and starts once a rocket's removal ends one warm-up. The player's logout ends its two.
     * The warm-up part of an in-process runtime clear releases the rest with every ticket and reservation, a new
     * request starts a fresh warm-up, and that one expires at age 600 with no ticket left at age 601.
     */
    private static final class Capacity {
        private final GameTestHelper helper;
        private final ServerLevel earth, moon, space;
        private final GameTestTickPacer pacer;
        private final UUID key = UUID.randomUUID();
        private final List<BlockPos> owned = List.of(new BlockPos(654_017, 100, 654_008), new BlockPos(654_020, 100, 654_008),
                new BlockPos(654_023, 100, 654_008));
        private final List<RocketEntity> mine = new ArrayList<>(), admin = new ArrayList<>();
        private ResourceEscrowSavedData data;
        private StationState station;
        private Player pilot;
        private List<RocketStructureSnapshot> pads;
        private RocketWarmupGameTestSupport.View fresh;
        private int step;

        Capacity(GameTestHelper helper) {
            this.helper = helper; earth = helper.getLevel(); var server = earth.getServer();
            moon = server.getLevel(CelestialIds.MOON_LEVEL); space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(moon != null && space != null, "Moon or Space Level is missing");
            pacer = new GameTestTickPacer(server);
        }
        void start() { helper.onEachTick(this::tick); }
        private BlockPos adminSource(int i) { return new BlockPos(654_056 + 32 * i, 100, 654_008); }

        private void tick() {
            if (step == 9) { return; }
            pacer.pace();
            if (pilot != null) { pilot.pump(); }
            var server = earth.getServer(); var journal = RocketTransferSavedData.get(server);
            switch (step) {
                case 0 -> setup(server, journal);
                case 1 -> fillWhenCold(server, journal);
                case 2 -> afterRemoval(server);
                case 3 -> afterLogout(server, journal);
                case 4 -> freshWhenCold(server, journal);
                case 5 -> expiry(server);
                default -> { }
            }
        }

        private void setup(MinecraftServer server, RocketTransferSavedData journal) {
            if (!journal.retryChecked(server)) { return; }
            data = ResourceEscrowSavedData.forTests(server); data.bind(server);
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(server, UUID.randomUUID(), "Warm-up Cap Station", CelestialIds.EARTH_ID, false).station().orElseThrow();
            earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(owned.get(0)), 2, key);
            owned.forEach(position -> mine.add(governed(earth, position, data.ledger().id())));
            for (int i = 0; i < 7; i++) {
                earth.getChunkSource().addRegionTicket(FIXTURE, new ChunkPos(adminSource(i)), 2, key);
                admin.add(governed(earth, adminSource(i), data.ledger().id()));
            }
            pilot = Player.connect(earth, owned.get(1), "WarmupQuota");
            step = 1;
        }

        private void fillWhenCold(MinecraftServer server, RocketTransferSavedData journal) {
            if (!columnReady(earth, owned.get(0)) || java.util.stream.IntStream.range(0, 7).anyMatch(i -> !columnReady(earth, adminSource(i)))) { return; }
            if (!mine.get(0).isAddedToWorld()) {
                for (var rocket : all()) { helper.assertTrue(earth.addFreshEntity(rocket), "Warm-up source publication failed"); }
                return;
            }
            if (!journal.retryChecked(server) || !RocketWarmupGameTestSupport.live(server).isEmpty()) { return; }
            pads = predict(server, moon, mine.get(0), 8);
            helper.assertTrue(pads != null, "The Moon does not offer eight admissible pads");
            var stationPad = new RocketLandingPadSelector().selectStation(space, admin.get(6).snapshot().orElseThrow(), UUID.randomUUID(),
                    station, List.of(), space.getGameTime(), true);
            helper.assertTrue(stationPad.success(), "The station pad is not admissible: " + stationPad.detail());
            if (pads.stream().anyMatch(pad -> !cold(server, pad)) || !cold(server, stationPad.snapshot().orElseThrow())) { return; }

            for (int i = 0; i < 2; i++) {
                RocketRuntime.requestFlightIntent(pilot, mine.get(i).getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, UUID.randomUUID());
                helper.assertTrue(pilot.last().equals(LOADING) && region(pads.get(i)).equals(warmup(server, mine.get(i)).region()),
                        "The player's warm-up " + i + " answered " + pilot.last());
            }
            RocketRuntime.requestFlightIntent(pilot, mine.get(2).getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, UUID.randomUUID());
            helper.assertTrue(pilot.last().equals(LIMIT) && warmup(server, mine.get(2)) == null && tickets(server, mine.get(2)).isEmpty(),
                    "The per-player limit did not refuse a third cold destination: " + pilot.last());
            for (int i = 0; i < 6; i++) {
                var result = RocketRuntime.requestAdminFlight(admin.get(i), RocketDestination.MOON, UUID.randomUUID());
                helper.assertTrue(result.code() == RocketFlightRequestCode.DESTINATION_LOADING
                        && region(pads.get(i + 2)).equals(warmup(server, admin.get(i)).region()), "Admin warm-up " + i + " answered " + result.code());
            }
            helper.assertTrue(RocketWarmupGameTestSupport.live(server).size() == 8, "The global cap is not full");
            var ninth = RocketRuntime.requestAdminStationFlight(admin.get(6), station.stationId(), UUID.randomUUID());
            helper.assertTrue(ninth.code() == RocketFlightRequestCode.DESTINATION_LOADING_LIMIT && warmup(server, admin.get(6)) == null
                    && tickets(server, admin.get(6)).isEmpty(), "The ninth cold destination answered " + ninth.code());
            admin.get(0).discard(); step = 2;
        }

        private void afterRemoval(MinecraftServer server) {
            if (warmup(server, admin.get(0)) != null) { return; }
            helper.assertTrue(tickets(server, admin.get(0)).isEmpty(), "The removed rocket kept a warm-up ticket");
            var ninth = RocketRuntime.requestAdminStationFlight(admin.get(6), station.stationId(), UUID.randomUUID());
            var started = warmup(server, admin.get(6));
            helper.assertTrue(ninth.code() == RocketFlightRequestCode.DESTINATION_LOADING && started != null
                    && started.level().equals(space.dimension().location()) && tickets(server, admin.get(6)).size() == started.columns().size(),
                    "The ninth did not start after a release: " + ninth.code());
            helper.assertTrue(RocketWarmupGameTestSupport.live(server).size() == 8, "The cap count differs after the release");
            pilot.leave(); pilot = null; step = 3;
        }

        private void afterLogout(MinecraftServer server, RocketTransferSavedData journal) {
            if (warmup(server, mine.get(0)) != null || warmup(server, mine.get(1)) != null) { return; }
            helper.assertTrue(tickets(server, mine.get(0)).isEmpty() && tickets(server, mine.get(1)).isEmpty()
                    && RocketWarmupGameTestSupport.live(server).size() == 6, "The logout did not end exactly the player's warm-ups");
            // The warm-up part of an in-process runtime clear: the Levels stay loaded, so every warm-up is released first.
            RocketWarmupGameTestSupport.clearWarmups(server);
            helper.assertTrue(RocketWarmupGameTestSupport.live(server).isEmpty() && all().stream().allMatch(rocket -> tickets(server, rocket).isEmpty()),
                    "The runtime clear left a warm-up or a ticket");
            var reservations = RocketWarmupGameTestSupport.launchReservations(server, journal, UUID.randomUUID());
            helper.assertTrue(pads.stream().map(EscrowWarmupFixtures::region).noneMatch(reservations::contains),
                    "The runtime clear left a warm-up reservation");
            step = 4;
        }

        /** The cleared tickets demote their columns at the next ticket update; a new request then warms afresh. */
        private void freshWhenCold(MinecraftServer server, RocketTransferSavedData journal) {
            if (cold(server, pads.get(0)) && journal.retryChecked(server)) {
                var request = UUID.randomUUID();
                var result = RocketRuntime.requestAdminFlight(admin.get(1), RocketDestination.MOON, request);
                fresh = warmup(server, admin.get(1));
                helper.assertTrue(result.code() == RocketFlightRequestCode.DESTINATION_LOADING && fresh != null
                        && fresh.request().equals(request) && fresh.started() == server.getTickCount()
                        && fresh.region().equals(region(pads.get(0))), "A new request did not start a fresh warm-up: " + result.code());
                step = 5;
            }
        }

        /** No relaunch: released at age 600, so no warm-up ticket is left at age 601. */
        private void expiry(MinecraftServer server) {
            long age = server.getTickCount() - fresh.started();
            var current = warmup(server, admin.get(1));
            if (age <= fresh.lifetime()) {
                helper.assertTrue(current != null && current.started() == fresh.started(), "The warm-up ended before its deadline at age " + age);
                return;
            }
            helper.assertTrue(current == null && tickets(server, admin.get(1)).isEmpty(), "A warm-up ticket survived age " + age);
            all().forEach(RocketEntity::discard);
            earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(owned.get(0)), 2, key);
            for (int i = 0; i < 7; i++) { earth.getChunkSource().removeRegionTicket(FIXTURE, new ChunkPos(adminSource(i)), 2, key); }
            step = 9; helper.succeed();
        }

        private List<RocketEntity> all() {
            var rockets = new ArrayList<RocketEntity>(mine); rockets.addAll(admin); return rockets;
        }
    }
}
