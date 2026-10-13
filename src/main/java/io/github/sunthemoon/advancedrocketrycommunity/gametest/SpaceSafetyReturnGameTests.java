package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SafetyReturnArrival;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SafetyReturnPhase;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SafetyReturnQuery;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 on the running server: connected players adrift in the Space Level for 100 ticks return to the
 * nearest station arrival they may VISIT, never the farthest, an unauthorized one or an unloaded one (which stays
 * unloaded, with no ticket); with no candidate they return to the Overworld; entering a region cancels the job; a
 * vehicle or a held transaction keeps a player ineligible; a refused attempt repeats only every 200 ticks; and many
 * adrift players stay inside the global budgets. Mock players are not ticked by their connection, so each test posts
 * their real {@code PlayerTickEvent} once per tick, which drives the installed service exactly as a client's tick
 * would; the installed service's server tick does the rest. One {@code onEachTick} per test (W28).
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceSafetyReturnGameTests {
    private static final String BATCH = "space_safety_return";
    private static final String BUDGET_BATCH = "space_safety_return_budget";
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_safety_return",
            Comparator.comparing(UUID::toString));
    /** A cell boundary: every point with x and z at an odd multiple of half the grid spacing is outside all regions. */
    private static final int HALF_GRID = StationLimits.GRID_SPACING / 2;
    private static final List<Fixture> OPEN = new ArrayList<>();

    private SpaceSafetyReturnGameTests() {
    }

    /** A test that times out skips its own clean-up; its batch releases players, stations, blocks and tickets. */
    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        closeOpen();
    }

    @AfterBatch(batch = BUDGET_BATCH)
    public static void cleanUpBudget(ServerLevel level) {
        closeOpen();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void anAdriftPlayerReturnsToTheNearestAuthorizedStationNotTheFarthest(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        StationState near = f.station("Safety nearest", UUID.randomUUID(), true, playerId);
        StationState far = f.station("Safety farthest", UUID.randomUUID(), true, playerId);
        ServerPlayer player = f.join(playerId, "safetyNearest", f.gapBeside(near, far));
        run(helper, f, tick -> {
            f.tickPlayers();
            helper.assertTrue(!f.arrivedAt(player, far), "Returned to the farthest station: " + f.describe(player));
            if (!f.arrivedAt(player, near)) {
                helper.assertTrue(tick < 300, "No return: " + f.describe(player));
                return false;
            }
            helper.assertTrue(tick > SafetyReturnPhase.ADRIFT_TICKS, "Returned before 100 adrift ticks: " + tick);
            helper.assertTrue(f.tokens(player).contains(SpaceSafetyReturnService.ARRIVED_STATION + "(Safety nearest)"),
                    "The destination was not announced: " + f.tokens(player));
            helper.assertTrue(!f.service.pending(playerId) && f.service.adriftTicks(playerId) == 0,
                    "The return did not clear the phase: " + f.describe(player));
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void anUnauthorizedNearerStationIsSkipped(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        StationState stranger = f.station("Safety stranger", UUID.randomUUID(), true);
        StationState allowed = f.station("Safety allowed", UUID.randomUUID(), true, playerId);
        ServerPlayer player = f.join(playerId, "safetyStranger", f.gapBeside(stranger, allowed));
        run(helper, f, tick -> {
            f.tickPlayers();
            helper.assertTrue(!f.arrivedAt(player, stranger), "Returned to a station without VISIT access");
            if (!f.arrivedAt(player, allowed)) {
                helper.assertTrue(tick < 300, "No return: " + f.describe(player));
                return false;
            }
            helper.assertTrue(f.tokens(player).contains(SpaceSafetyReturnService.ARRIVED_STATION + "(Safety allowed)"),
                    "The destination was not announced: " + f.tokens(player));
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void anUnloadedNearerArrivalIsSkippedWithoutLoadingIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        StationState unloaded = f.unloadedStation("Safety unloaded", playerId);
        StationState loaded = f.station("Safety loaded", UUID.randomUUID(), true, playerId);
        ServerPlayer player = f.join(playerId, "safetyUnloaded", f.gapBeside(unloaded, loaded));
        ChunkPos chunk = new ChunkPos(Fixture.feet(unloaded));
        Map<String, Integer> ticketsBefore = TicketCounts.near(f.space, chunk, 2);
        run(helper, f, tick -> {
            f.tickPlayers();
            helper.assertTrue(f.space.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                    "The unloaded arrival's chunk was loaded at tick " + tick);
            helper.assertTrue(TicketCounts.near(f.space, chunk, 2).equals(ticketsBefore),
                    "A ticket was added near the unloaded arrival: " + TicketCounts.near(f.space, chunk, 2));
            if (!f.arrivedAt(player, loaded)) {
                helper.assertTrue(tick < 300, "No return: " + f.describe(player));
                return false;
            }
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void withNoCandidateThePlayerReturnsToTheOverworld(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        BlockPos gap = new BlockPos(HALF_GRID + 37 * StationLimits.GRID_SPACING, StationLimits.LANDING_Y,
                HALF_GRID + 37 * StationLimits.GRID_SPACING);
        helper.assertTrue(f.data.findAt(gap.getX(), gap.getZ()).isEmpty(), "The gap is inside a region");
        ServerPlayer player = f.join(playerId, "safetyOverworld", gap);
        run(helper, f, tick -> {
            f.tickPlayers();
            if (player.level() != f.server.overworld()) {
                helper.assertTrue(tick < 300, "No return: " + f.describe(player));
                return false;
            }
            SafeCelestialTravel.Destination expected = new SafeCelestialTravel().prepare(f.server.overworld(),
                    CelestialIds.EARTH_ID);
            helper.assertTrue(player.position().distanceToSqr(expected.x(), expected.y(), expected.z()) < 1.0D,
                    "Not at the safe Overworld arrival: " + f.describe(player));
            helper.assertTrue(f.tokens(player).contains(SpaceSafetyReturnService.ARRIVED_OVERWORLD),
                    "The Overworld return was not announced: " + f.tokens(player));
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
    public static void enteringARegionCancelsTheJob(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        StationState station = f.station("Safety entered", UUID.randomUUID(), true, playerId);
        StationState other = f.station("Safety other", UUID.randomUUID(), false);
        ServerPlayer player = f.join(playerId, "safetyEntered", f.gapBeside(station, other));
        BlockPos inside = Fixture.feet(station).offset(40, 4, 40);
        int[] cancelledAt = {-1};
        run(helper, f, tick -> {
            if (cancelledAt[0] < 0) {
                f.tickPlayers();
                if (!f.service.pending(playerId)) {
                    helper.assertTrue(tick < SafetyReturnPhase.ADRIFT_TICKS, "No job after 100 adrift ticks: "
                            + f.describe(player));
                    return false;
                }
                // The job exists until this tick's server END; the player enters the region first.
                player.teleportTo(f.space, inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D, 0.0F, 0.0F);
                f.tickPlayers();
                helper.assertTrue(!f.service.pending(playerId) && f.service.adriftTicks(playerId) == 0,
                        "Entering the region did not cancel the job: " + f.describe(player));
                cancelledAt[0] = tick;
                return false;
            }
            f.tickPlayers();
            helper.assertTrue(player.level() == f.space
                            && player.position().distanceToSqr(inside.getX() + 0.5D, inside.getY(), inside.getZ() + 0.5D)
                            < 0.01D, "The cancelled job still moved the player: " + f.describe(player));
            helper.assertTrue(f.tokens(player).stream().noneMatch(token -> token.contains("space_safety_return")),
                    "A cancelled job announced something: " + f.tokens(player));
            return tick >= cancelledAt[0] + 40;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 500)
    public static void aPlayerOnAVehicleOrInATransactionIsNotEligible(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID riderId = UUID.randomUUID();
        UUID heldId = UUID.randomUUID();
        StationState station = f.station("Safety held", UUID.randomUUID(), true, riderId, heldId);
        StationState other = f.station("Safety held other", UUID.randomUUID(), false);
        BlockPos gap = f.gapBeside(station, other);
        ServerPlayer rider = f.join(riderId, "safetyRider", gap);
        ServerPlayer held = f.join(heldId, "safetyHeld", gap.offset(0, 0, 3));
        Minecart cart = EntityType.MINECART.create(f.space);
        helper.assertTrue(cart != null, "No minecart");
        cart.setNoGravity(true);
        cart.moveTo(gap.getX() + 0.5D, gap.getY(), gap.getZ() + 0.5D);
        helper.assertTrue(f.space.addFreshEntity(cart), "The minecart was not added");
        f.cleanups.add(cart::discard);
        helper.assertTrue(rider.startRiding(cart, true), "The rider did not mount");
        // A rocket transfer or an elevator ride, as the installed transaction rule reports one.
        f.service.holdForTest(heldId, true);
        f.cleanups.add(() -> f.service.holdForTest(heldId, false));
        int released = 150;
        run(helper, f, tick -> {
            f.tickPlayers();
            if (tick <= released) {
                helper.assertTrue(rider.isPassenger() && f.service.adriftTicks(riderId) == 0
                        && !f.service.pending(riderId), "A riding player became adrift: " + f.describe(rider));
                helper.assertTrue(!f.service.pending(heldId) && held.level() == f.space
                        && !f.arrivedAt(held, station), "A held player was returned: " + f.describe(held));
                if (tick == released) {
                    rider.stopRiding();
                    cart.discard();
                    f.service.holdForTest(heldId, false);
                }
                return false;
            }
            boolean both = f.arrivedAt(rider, station) && f.arrivedAt(held, station);
            if (!both) {
                helper.assertTrue(tick < released + 200, "Released players were not returned: "
                        + f.describe(rider) + " / " + f.describe(held));
                return false;
            }
            helper.assertTrue(tick > released + SafetyReturnPhase.ADRIFT_TICKS, "Released players returned early");
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 700)
    public static void aRefusedAttemptRepeatsOnlyEveryTwoHundredTicks(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        UUID playerId = UUID.randomUUID();
        StationState station = f.station("Safety protected", UUID.randomUUID(), true, playerId);
        StationState other = f.station("Safety protected other", UUID.randomUUID(), false);
        BlockPos gap = f.gapBeside(station, other);
        ServerPlayer player = f.join(playerId, "safetyRefused", gap);
        Consumer<EndgameEffectEvent> protection = event -> {
            if (event.systemId().equals(SpaceSafetyReturnService.SYSTEM_ID)
                    && event.actorId().filter(playerId::equals).isPresent()) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EndgameEffectEvent.class, protection);
        f.cleanups.add(() -> MinecraftForge.EVENT_BUS.unregister(protection));
        String refused = SpaceSafetyReturnService.REFUSED + "(" + SpaceSafetyReturnService.Refusal.PROTECTED.key() + ")";
        List<Integer> refusals = new ArrayList<>();
        run(helper, f, tick -> {
            f.tickPlayers();
            long seen = f.tokens(player).stream().filter(refused::equals).count();
            if (seen > refusals.size()) {
                refusals.add(f.server.getTickCount());
            }
            helper.assertTrue(player.level() == f.space && !f.arrivedAt(player, station),
                    "A protected arrival was used: " + f.describe(player));
            if (tick < 560) {
                return false;
            }
            helper.assertTrue(refusals.size() == 3, "Expected attempts at 100, 300 and 500 ticks: " + refusals
                    + " tokens " + f.tokens(player));
            helper.assertTrue(refusals.get(1) - refusals.get(0) == SafetyReturnPhase.ATTEMPT_SPACING_TICKS
                            && refusals.get(2) - refusals.get(1) == SafetyReturnPhase.ATTEMPT_SPACING_TICKS,
                    "Attempts were not spaced by 200 ticks: " + refusals);
            return true;
        });
    }

    @GameTest(template = "empty", batch = BUDGET_BATCH, timeoutTicks = 500)
    public static void manyAdriftPlayersStayInsideTheGlobalBudgets(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            ids.add(UUID.randomUUID());
        }
        // Forty unloaded stations shared by all twenty players: 820 inspections in all, at most 128 a tick.
        StationState first = null;
        for (int index = 0; index < 40; index++) {
            StationState station = f.station("Safety budget " + index, UUID.randomUUID(), false,
                    ids.toArray(UUID[]::new));
            first = first == null ? station : first;
        }
        BlockPos gap = new BlockPos(first.landingPad().x() + HALF_GRID, StationLimits.LANDING_Y,
                first.landingPad().z() + HALF_GRID);
        List<ServerPlayer> players = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            players.add(f.join(ids.get(index), "safetyBudget" + index, gap));
        }
        int[] limits = new int[4];
        int[] busyTicks = {0};
        run(helper, f, tick -> {
            SafetyReturnQuery.TickUse use = f.service.lastTick();
            helper.assertTrue(use.inspections() <= SafetyReturnQuery.INSPECTIONS_PER_TICK
                    && use.probes() <= SafetyReturnQuery.PROBES_PER_TICK
                    && use.maxJobInspections() <= SafetyReturnQuery.INSPECTIONS_PER_JOB
                    && use.jobs() <= SafetyReturnQuery.MAX_JOBS && f.service.jobs() <= SafetyReturnQuery.MAX_JOBS,
                    "A budget was exceeded: " + use);
            limits[0] = Math.max(limits[0], use.inspections());
            limits[1] = Math.max(limits[1], use.probes());
            limits[2] = Math.max(limits[2], use.maxJobInspections());
            limits[3] = Math.max(limits[3], use.jobs());
            f.tickPlayers();
            // Jobs pending now are served at this tick's server END.
            busyTicks[0] += f.service.jobs() > 0 ? 1 : 0;
            if (players.stream().anyMatch(player -> player.level() != f.server.overworld())) {
                helper.assertTrue(tick < 400, "Not every player returned: " + players.stream()
                        .filter(player -> player.level() != f.server.overworld()).map(f::describe).toList());
                return false;
            }
            helper.assertTrue(limits[0] == SafetyReturnQuery.INSPECTIONS_PER_TICK
                            && limits[2] == SafetyReturnQuery.INSPECTIONS_PER_JOB && limits[3] == ids.size(),
                    "The load never reached the inspection budget: inspections/probes/job/jobs "
                            + java.util.Arrays.toString(limits));
            helper.assertTrue(busyTicks[0] >= (ids.size() * 41 + SafetyReturnQuery.INSPECTIONS_PER_TICK - 1)
                    / SafetyReturnQuery.INSPECTIONS_PER_TICK, "The jobs finished faster than the budget allows: "
                    + busyTicks[0]);
            for (ServerPlayer player : players) {
                helper.assertTrue(f.tokens(player).contains(SpaceSafetyReturnService.ARRIVED_OVERWORLD),
                        "Not announced: " + f.describe(player));
            }
            return true;
        });
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void theArrivalProbeReadsOnlyLoadedSafeCells(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        try {
            BlockPos feet = new BlockPos(HALF_GRID + 41 * StationLimits.GRID_SPACING, 140,
                    HALF_GRID + 41 * StationLimits.GRID_SPACING);
            f.load(UUID.randomUUID(), feet);
            f.cell(feet.below(), Blocks.STONE.defaultBlockState());
            f.cell(feet, Blocks.AIR.defaultBlockState());
            f.cell(feet.above(), Blocks.AIR.defaultBlockState());
            SafetyReturnQuery.Target target = new SafetyReturnQuery.Target(UUID.randomUUID(), feet.getX(), feet.getY(),
                    feet.getZ());
            for (int step = 0; step < SafetyReturnQuery.PROBE_STEPS; step++) {
                helper.assertTrue(SafetyReturnArrival.probe(f.space, target, step), "A safe cell failed step " + step);
            }
            expectUnsafe(helper, f, target, feet.below(), Blocks.AIR.defaultBlockState(), 0, "no floor");
            expectUnsafe(helper, f, target, feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 0, "a magma floor");
            expectUnsafe(helper, f, target, feet, Blocks.STONE.defaultBlockState(), 1, "a solid feet cell");
            expectUnsafe(helper, f, target, feet, Blocks.FIRE.defaultBlockState(), 1, "fire");
            expectUnsafe(helper, f, target, feet.above(), Blocks.WATER.defaultBlockState(), 2, "water at the head");
            expectUnsafe(helper, f, target, feet.above(), Blocks.COBWEB.defaultBlockState(), 2, "a cobweb");
            BlockPos far = new BlockPos(4_000_000, 128, 4_000_000);
            ChunkPos farChunk = new ChunkPos(far);
            helper.assertTrue(f.space.getChunkSource().getChunkNow(farChunk.x, farChunk.z) == null,
                    "The far chunk is already loaded");
            SafetyReturnQuery.Target unloaded = new SafetyReturnQuery.Target(UUID.randomUUID(), far.getX(),
                    far.getY(), far.getZ());
            for (int step = 0; step < SafetyReturnQuery.PROBE_STEPS; step++) {
                helper.assertTrue(!SafetyReturnArrival.probe(f.space, unloaded, step), "An unloaded cell was safe");
            }
            helper.assertTrue(f.space.getChunkSource().getChunkNow(farChunk.x, farChunk.z) == null,
                    "A probe loaded a chunk");
        } finally {
            f.close();
        }
        helper.succeed();
    }

    private static void expectUnsafe(GameTestHelper helper, Fixture f, SafetyReturnQuery.Target target, BlockPos at,
                                     BlockState state, int step, String what) {
        BlockState before = f.space.getBlockState(at);
        f.cell(at, state);
        helper.assertTrue(!SafetyReturnArrival.probe(f.space, target, step), "The probe accepted " + what);
        f.cell(at, before);
    }

    /** One {@code onEachTick}; {@code step} receives the test tick and returns true when the test is complete. */
    private static void run(GameTestHelper helper, Fixture fixture, IntPredicate step) {
        boolean[] finished = {false};
        int[] tick = {0};
        helper.onEachTick(() -> {
            if (finished[0]) {
                return;
            }
            try {
                tick[0]++;
                if (step.test(tick[0])) {
                    finished[0] = true;
                    fixture.close();
                    helper.succeed();
                }
            } catch (RuntimeException | Error failure) {
                finished[0] = true;
                try {
                    fixture.close();
                } catch (RuntimeException | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        });
    }

    private static void closeOpen() {
        List<Fixture> open = new ArrayList<>(OPEN);
        OPEN.clear();
        open.forEach(Fixture::close);
    }

    /** Stations written straight into the registry, safe arrivals kept loaded by a fixture ticket, and players. */
    private static final class Fixture {
        final MinecraftServer server;
        final ServerLevel space;
        final StationRegistrySavedData data;
        final SpaceSafetyReturnService service;
        final List<Runnable> cleanups = new ArrayList<>();
        private final GameTestHelper helper;
        private final List<UUID> stations = new ArrayList<>();
        private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final Map<UUID, ChunkPos> tickets = new LinkedHashMap<>();
        private final List<ServerPlayer> players = new ArrayList<>();
        private final Map<UUID, List<String>> tokens = new HashMap<>();
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            server = helper.getLevel().getServer();
            space = server.getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "The Space Level is missing");
            data = StationRegistrySavedData.get(server);
            helper.assertTrue(data.updatesAvailable(), "The station registry is unavailable");
            service = SpaceSafetyReturnRuntime.current().orElse(null);
            helper.assertTrue(service != null, "The safety return service is not installed");
            OPEN.add(this);
        }

        static BlockPos feet(StationState station) {
            return new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
        }

        /** A committed station; a safe one has a loaded arrival: a floor and two free cells above it. */
        StationState station(String name, UUID owner, boolean safe, UUID... members) {
            UUID id = UUID.randomUUID();
            data.reserve(id, owner, name, CelestialIds.EARTH_ID, space.getGameTime());
            data.commit(id);
            stations.add(id);
            for (UUID member : members) {
                data.addMember(id, member);
            }
            StationState state = data.find(id).orElseThrow();
            if (safe) {
                BlockPos feet = feet(state);
                load(id, feet);
                cell(feet.below(), Blocks.SMOOTH_STONE.defaultBlockState());
                cell(feet, Blocks.AIR.defaultBlockState());
                cell(feet.above(), Blocks.AIR.defaultBlockState());
            }
            return state;
        }

        /** A station whose arrival chunk is not loaded; a cell an earlier test left loaded is not used. */
        StationState unloadedStation(String name, UUID... members) {
            for (int attempt = 0; attempt < 8; attempt++) {
                StationState state = station(name, UUID.randomUUID(), false, members);
                BlockPos feet = feet(state);
                if (space.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4) == null) {
                    return state;
                }
                data.delete(state.stationId());
                stations.remove(state.stationId());
            }
            throw new IllegalStateException("No station cell with an unloaded arrival");
        }

        /** Test-owned loading only: a fixture ticket and a synchronous load. */
        void load(UUID key, BlockPos at) {
            ChunkPos chunk = new ChunkPos(at);
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, key);
            tickets.put(key, chunk);
            space.getChunkAt(at);
        }

        void cell(BlockPos at, BlockState state) {
            cells.putIfAbsent(at.immutable(), space.getBlockState(at));
            space.setBlock(at, state, Block.UPDATE_CLIENTS);
        }

        /** The cell boundary beside {@code near}, on the side away from {@code far}: outside every region. */
        BlockPos gapBeside(StationState near, StationState far) {
            int dx = Integer.signum(near.cell().x() - far.cell().x());
            int dz = Integer.signum(near.cell().z() - far.cell().z());
            helper.assertTrue(dx != 0 || dz != 0, "The two stations share a cell");
            BlockPos gap = new BlockPos(near.landingPad().x() + dx * HALF_GRID, StationLimits.LANDING_Y,
                    near.landingPad().z() + dz * HALF_GRID);
            helper.assertTrue(data.findAt(gap.getX(), gap.getZ()).isEmpty(), "The gap is inside a region");
            helper.assertTrue(distance(gap, near) < distance(gap, far), "The near station is not nearer");
            return gap;
        }

        ServerPlayer join(UUID id, String name, BlockPos at) {
            List<String> playerTokens = new ArrayList<>();
            tokens.put(id, playerTokens);
            ServerPlayer player = ConnectedTestPlayers.join(server, id, name, space, at, new ArrayList<>(), false,
                    playerTokens);
            players.add(player);
            return player;
        }

        /** Posts each player's real END player tick, as the connection tick of a client would. */
        void tickPlayers() {
            for (ServerPlayer player : players) {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
                }
            }
        }

        boolean arrivedAt(ServerPlayer player, StationState station) {
            BlockPos feet = feet(station);
            return player.level() == space
                    && player.position().distanceToSqr(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D) < 1.0D;
        }

        List<String> tokens(ServerPlayer player) {
            return tokens.getOrDefault(player.getUUID(), List.of());
        }

        String describe(ServerPlayer player) {
            return player.getGameProfile().getName() + " level=" + player.level().dimension().location() + " at="
                    + player.blockPosition().toShortString() + " adrift=" + service.adriftTicks(player.getUUID())
                    + " pending=" + service.pending(player.getUUID()) + " last=" + service.lastTick()
                    + " tokens=" + tokens(player);
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            OPEN.remove(this);
            RuntimeException failure = null;
            List<Runnable> steps = new ArrayList<>();
            players.forEach(player -> steps.add(() -> {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    server.getPlayerList().remove(player);
                }
            }));
            steps.addAll(cleanups);
            cells.forEach((at, state) -> steps.add(() -> space.setBlock(at, state, Block.UPDATE_CLIENTS)));
            tickets.forEach((key, chunk) -> steps.add(() -> space.getChunkSource()
                    .removeRegionTicket(FIXTURE_TICKET, chunk, 2, key)));
            stations.forEach(id -> steps.add(() -> data.delete(id)));
            steps.add(() -> data.flush(server));
            for (Runnable step : steps) {
                try {
                    step.run();
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private static double distance(BlockPos at, StationState station) {
            double dx = station.landingPad().x() + 0.5D - (at.getX() + 0.5D);
            double dz = station.landingPad().z() + 0.5D - (at.getZ() + 0.5D);
            return dx * dx + dz * dz;
        }
    }
}
