package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPairs;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-059 on a running server: an anchor in the Overworld (Earth) and a terminal on a station orbiting Earth. Bind,
 * cargo both ways with an unbind in flight, the warp and deletion guards; rides for a member, not for a stranger, an
 * obstructed arrival, and no {@code elevator_arrival} ticket left behind; validity re-derived and failing closed with
 * the pair kept; the disabled switch keeping unbind and arrivals; the busy break rule. One test per batch.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorGameTests {
    private static final String CARGO = "endgame_elevator_cargo";
    private static final String RIDES = "endgame_elevator_rides";
    private static final String VALIDITY = "endgame_elevator_validity";
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_elevator",
            Comparator.comparing(UUID::toString));
    private static final String ARRIVAL_TICKET = "advancedrocketrycommunity:elevator_arrival";

    private ElevatorGameTests() {
    }

    @AfterBatch(batch = VALIDITY)
    public static void restoreSwitch(ServerLevel level) {
        CommonConfig.ENDGAME_SPACE_ELEVATOR.set(true);
    }

    /** A bound anchor and terminal of one owner, with the station's chunk loaded and ticking. */
    private static final class Fixture {
        final MinecraftServer server;
        final ServerLevel level;
        final ServerLevel space;
        final StationRegistrySavedData stations;
        final UUID owner = UUID.randomUUID();
        final UUID stationId = UUID.randomUUID();
        final StationState station;
        final BlockPos anchorPos;
        final BlockPos terminalPos;
        final ChunkPos terminalChunk;

        Fixture(GameTestHelper helper) {
            level = helper.getLevel();
            server = level.getServer();
            space = server.getLevel(CelestialIds.SPACE_LEVEL);
            stations = StationRegistrySavedData.get(server);
            stations.reserve(stationId, owner, "Elevator fixture", CelestialIds.EARTH_ID, level.getGameTime());
            station = stations.commit(stationId);
            terminalPos = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                    station.landingPad().z() + 6);
            terminalChunk = new ChunkPos(terminalPos);
            // Test setup only: a ticket and a forced chunk keep the station's chunk loaded and ticking.
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, terminalChunk, 2, stationId);
            space.setChunkForced(terminalChunk.x, terminalChunk.z, true);
            space.getChunkAt(terminalPos);
            // High above the test origin, so no fluid near it reaches the platform.
            anchorPos = helper.absolutePos(new BlockPos(3, 14, 3));
            buildAnchor(level, anchorPos, owner);
            space.setBlockAndUpdate(terminalPos, ModBlocks.ELEVATOR_TERMINAL.get().defaultBlockState());
            clearAbove(space, terminalPos);
            terminal().assignOwner(owner);
        }

        ElevatorAnchorBlockEntity anchor() {
            return (ElevatorAnchorBlockEntity) level.getBlockEntity(anchorPos);
        }

        ElevatorTerminalBlockEntity terminal() {
            return (ElevatorTerminalBlockEntity) space.getBlockEntity(terminalPos);
        }

        void registered(GameTestHelper helper) {
            LaserTargetGameTests.chunkSaved(level, anchorPos);
            LaserTargetGameTests.chunkSaved(space, terminalPos);
            helper.assertTrue(anchor().endpointActive() && anchor().placement() == EndgameCode.OK,
                    "The anchor is not registered and formed: " + anchor().describe());
            helper.assertTrue(terminal().endpointActive() && terminal().placement() == EndgameCode.OK,
                    "The terminal is not registered and placed: " + terminal().describe());
        }

        ElevatorPair bind(GameTestHelper helper) {
            service().barrier(root -> null);
            EndgameCode code = ElevatorPairs.bind(server, service(), devices(), terminal(),
                    anchor().deviceId().orElseThrow(), owner, false, level.getGameTime()).code();
            helper.assertTrue(code == EndgameCode.OK, "The bind failed: " + code);
            return root().pairs().forStation(stationId).orElseThrow();
        }

        void close() {
            root().pairs().forStation(stationId).ifPresent(pair -> service().barrier(root -> root.pairs()
                    .remove(pair.pairId())));
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        level.setBlockAndUpdate(anchorPos.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            space.setBlockAndUpdate(terminalPos, Blocks.AIR.defaultBlockState());
            space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, terminalChunk, 2, stationId);
            space.setChunkForced(terminalChunk.x, terminalChunk.z, false);
            stations.delete(stationId);
            stations.flush(server);
        }
    }

    @GameTest(template = "empty", batch = CARGO, timeoutTicks = 2400)
    public static void cargoTravelsBothWaysAndAnUnbindLetsItArrive(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        TransitKey[] keys = new TransitKey[2];
        helper.startSequence()
                .thenWaitUntil(() -> fixture.registered(helper))
                .thenExecute(() -> {
                    fixture.bind(helper);
                    ElevatorStationGuard guard = ElevatorStationGuard.Installed.current();
                    helper.assertTrue(guard.warp(fixture.server, fixture.station) == ElevatorStationGuard.Decision.BOUND,
                            "A bound station may warp");
                    helper.assertTrue(command(fixture.server, "arce station admin delete " + fixture.stationId
                            + " confirm") == 0 && fixture.stations.find(fixture.stationId).isPresent(),
                            "A bound station was deleted");
                    fixture.anchor().storage().input().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
                    fixture.anchor().storage().energy().set(100_000);
                    fixture.anchor().launchForTest(fixture.owner);
                    keys[0] = new TransitKey(fixture.anchor().deviceId().orElseThrow(), 1L);
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.anchor().source().outbox().size() == 1
                        && fixture.anchor().storage().energy().energy() == 80_000, "No escrow up: "
                        + fixture.anchor().describe()))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.level, fixture.anchorPos);
                    helper.assertTrue(root().transits().record(keys[0]).isPresent(), "Not registered");
                })
                .thenExecute(() -> service().barrier(root -> null))
                .thenWaitUntil(() -> helper.assertTrue(fixture.terminal().destination().incoming()
                        .containsKey(keys[0]), "The terminal did not claim the cargo"))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
                    helper.assertTrue(fixture.terminal().storage().receive().getStackInSlot(0).getCount() == 3,
                            "The cargo did not reach the terminal");
                })
                .thenExecute(() -> {
                    fixture.terminal().storage().input().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 2));
                    fixture.terminal().storage().energy().set(100_000);
                    fixture.terminal().launchForTest(fixture.owner);
                    keys[1] = new TransitKey(fixture.terminal().deviceId().orElseThrow(), 1L);
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
                    helper.assertTrue(root().transits().record(keys[1]).isPresent(), "Not registered down");
                })
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    ElevatorPair pair = root().pairs().forStation(fixture.stationId).orElseThrow();
                    helper.assertTrue(ElevatorPairs.unbind(fixture.server, service(), devices(), pair, fixture.owner,
                            false, fixture.level.getGameTime()).ok(), "The owner could not unbind");
                    ElevatorStationGuard guard = ElevatorStationGuard.Installed.current();
                    helper.assertTrue(guard.warp(fixture.server, fixture.station)
                            == ElevatorStationGuard.Decision.ALLOWED, "The unbound station may not warp");
                    helper.assertTrue(guard.delete(fixture.server, fixture.station)
                            == ElevatorStationGuard.Decision.BOUND, "Cargo of the region did not pin the deletion");
                })
                .thenWaitUntil(() -> helper.assertTrue(fixture.anchor().destination().incoming()
                        .containsKey(keys[1]), "Cargo in flight did not arrive after the unbind"))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.level, fixture.anchorPos);
                    helper.assertTrue(fixture.anchor().storage().receive().getStackInSlot(0).getCount() == 2,
                            "The cargo did not reach the anchor");
                })
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    LaserTargetGameTests.chunkSaved(fixture.level, fixture.anchorPos);
                    LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
                    helper.assertTrue(root().transits().record(keys[0]).isEmpty()
                            && root().transits().record(keys[1]).isEmpty(), "Not pruned");
                })
                .thenExecute(() -> {
                    helper.assertTrue(ElevatorStationGuard.Installed.current().delete(fixture.server, fixture.station)
                            == ElevatorStationGuard.Decision.ALLOWED, "A settled station is still pinned");
                    fixture.anchor().storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    fixture.terminal().storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    fixture.close();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = RIDES, timeoutTicks = 2400)
    public static void membersRideUpAndDownStrangersDoNotAndNoTicketRemains(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        UUID memberId = UUID.randomUUID();
        fixture.stations.addMember(fixture.stationId, memberId);
        List<ServerPlayer> joined = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> fixture.registered(helper))
                .thenExecute(() -> {
                    fixture.bind(helper);
                    fixture.anchor().storage().energy().set(200_000);
                    fixture.terminal().storage().energy().set(200_000);
                    ServerPlayer member = ConnectedTestPlayers.join(fixture.server, memberId, "elevatorMember",
                            fixture.level, fixture.anchorPos.above(), new ArrayList<>());
                    ServerPlayer stranger = ConnectedTestPlayers.join(fixture.server, UUID.randomUUID(),
                            "elevatorStranger", fixture.level, fixture.anchorPos.above().east(), new ArrayList<>());
                    joined.add(member);
                    joined.add(stranger);
                    helper.assertTrue(rides().request(stranger, fixture.anchor(), service(), devices()).code()
                            == EndgameCode.UNAUTHORIZED, "A stranger may ride");
                    EndgameCode requested = rides().request(member, fixture.anchor(), service(), devices()).code();
                    helper.assertTrue(requested == EndgameCode.RIDE_COUNTDOWN, "The member's ride: " + requested);
                    helper.assertTrue(arrivalTickets(fixture.space) == 1, "No pre-load ticket at the arrival");
                })
                .thenWaitUntil(() -> helper.assertTrue(joined.get(0).level() == fixture.space
                        && fixture.terminal().onPlatform(joined.get(0)), "The member did not arrive up"))
                .thenExecute(() -> {
                    helper.assertTrue(fixture.anchor().storage().energy().energy() == 150_000,
                            "The ride up cost " + (200_000 - fixture.anchor().storage().energy().energy()));
                    helper.assertTrue(arrivalTickets(fixture.space) == 0 && arrivalTickets(fixture.level) == 0,
                            "A ticket remained after the commit");
                    // Obstruct the arrival platform below: the ride down is cancelled at its commit.
                    fixture.level.setBlockAndUpdate(fixture.anchorPos.above(2), Blocks.STONE.defaultBlockState());
                })
                .thenExecuteAfter(205, () -> helper.assertTrue(rides().request(joined.get(0), fixture.terminal(),
                        service(), devices()).code() == EndgameCode.RIDE_COUNTDOWN, "The ride down was refused"))
                .thenWaitUntil(() -> helper.assertTrue(rides().pending(joined.get(0).getUUID()).isEmpty()
                        && joined.get(0).level() == fixture.space, "An obstructed ride did not cancel"))
                .thenExecute(() -> {
                    helper.assertTrue(arrivalTickets(fixture.level) == 0 && fixture.terminal().storage().energy()
                            .energy() == 200_000, "A cancelled ride cost energy or left its ticket");
                    fixture.level.setBlockAndUpdate(fixture.anchorPos.above(2), Blocks.AIR.defaultBlockState());
                })
                .thenExecuteAfter(105, () -> helper.assertTrue(rides().request(joined.get(0), fixture.terminal(),
                        service(), devices()).code() == EndgameCode.RIDE_COUNTDOWN, "The second ride down was refused"))
                .thenWaitUntil(() -> helper.assertTrue(joined.get(0).level() == fixture.level
                        && fixture.anchor().onPlatform(joined.get(0)), "The member did not arrive down"))
                .thenExecute(() -> {
                    helper.assertTrue(fixture.terminal().storage().energy().energy() == 150_000
                                    && arrivalTickets(fixture.level) == 0 && arrivalTickets(fixture.space) == 0,
                            "The ride down cost the wrong energy or left a ticket");
                    joined.forEach(player -> fixture.server.getPlayerList().remove(player));
                    fixture.close();
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = VALIDITY, timeoutTicks = 1600)
    public static void anInvalidPairIsKeptAndTheSwitchKeepsUnbindAndArrivals(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        TransitKey key = new TransitKey(UUID.randomUUID(), 1L);
        TransitKey[] shipped = {key};
        helper.startSequence()
                .thenWaitUntil(() -> fixture.registered(helper))
                .thenExecute(() -> {
                    ElevatorPair pair = fixture.bind(helper);
                    // The anchor's body now maps to another Level (as a catalog change would): invalid, kept.
                    ElevatorPair remapped = new ElevatorPair(pair.pairId(), pair.stationId(), pair.terminalId(),
                            pair.anchorId(), pair.bodyId(), ResourceLocation.tryBuild("minecraft", "the_nether"),
                            pair.x(), pair.z(), pair.anchorY(), pair.boundAt(), pair.boundBy());
                    service().barrier(root -> {
                        root.pairs().remove(pair.pairId());
                        root.pairs().add(remapped);
                        return null;
                    });
                    fixture.anchor().storage().input().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 4));
                    fixture.anchor().storage().energy().set(100_000);
                    helper.assertTrue(fixture.anchor().launch(fixture.level, service(), devices(), fixture.owner,
                            false) == EndgameCode.PAIR_LEVEL_CHANGED, "An invalid pair shipped");
                    helper.assertTrue(root().pairs().forStation(fixture.stationId).isPresent(), "The pair was dropped");
                    // A player who is not an operator cannot break an endpoint a pair names.
                    Player player = helper.makeMockPlayer();
                    BlockState state = fixture.level.getBlockState(fixture.anchorPos);
                    helper.assertFalse(state.getBlock().onDestroyedByPlayer(state, fixture.level, fixture.anchorPos,
                            player, true, Fluids.EMPTY.defaultFluidState()), "A paired anchor was broken");
                    // Unbind is always possible; a fresh bind is valid again.
                    helper.assertTrue(ElevatorPairs.unbind(fixture.server, service(), devices(), remapped,
                            fixture.owner, false, fixture.level.getGameTime()).ok(), "An invalid pair could not unbind");
                    fixture.bind(helper);
                    fixture.anchor().launchForTest(fixture.owner);
                    shipped[0] = new TransitKey(fixture.anchor().deviceId().orElseThrow(), 1L);
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.level, fixture.anchorPos);
                    helper.assertTrue(root().transits().record(shipped[0]).isPresent(), "Not registered");
                })
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    CommonConfig.ENDGAME_SPACE_ELEVATOR.set(false);
                    fixture.terminal().storage().input().setStackInSlot(0, new ItemStack(Items.DIRT, 1));
                    fixture.terminal().storage().energy().set(100_000);
                    helper.assertTrue(fixture.terminal().launch(fixture.space, service(), devices(), fixture.owner,
                            false) == EndgameCode.SYSTEM_DISABLED, "A disabled elevator shipped");
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
                    helper.assertTrue(fixture.terminal().storage().receive().getStackInSlot(0).getCount() == 4,
                            "The disabled switch stopped an arrival");
                })
                .thenExecute(() -> {
                    ElevatorPair pair = root().pairs().forStation(fixture.stationId).orElseThrow();
                    helper.assertTrue(ElevatorPairs.unbind(fixture.server, service(), devices(), pair, fixture.owner,
                            false, fixture.level.getGameTime()).ok(), "The disabled switch stopped an unbind");
                    CommonConfig.ENDGAME_SPACE_ELEVATOR.set(true);
                    fixture.terminal().storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    fixture.terminal().storage().input().setStackInSlot(0, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
                    helper.assertTrue(root().transits().record(shipped[0]).isEmpty(), "Not pruned");
                })
                .thenExecute(fixture::close)
                .thenSucceed();
    }

    /**
     * The 5 × 2 × 5 anchor around its controller in the middle of the top layer: a casing base, an iron ring around the
     * controller and a casing rim (solid, so no fluid can enter), with the platform above it cleared.
     */
    static void buildAnchor(ServerLevel level, BlockPos controller, UUID owner) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlockAndUpdate(controller.offset(x, -1, z), casing);
                boolean ring = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                level.setBlockAndUpdate(controller.offset(x, 0, z), ring ? Blocks.IRON_BLOCK.defaultBlockState()
                        : casing);
            }
        }
        level.setBlockAndUpdate(controller, ModBlocks.ELEVATOR_ANCHOR.get().defaultBlockState());
        clearAbove(level, controller);
        ((ElevatorAnchorBlockEntity) level.getBlockEntity(controller)).assignOwner(owner);
    }

    /** The 3 × 3 platform space above an endpoint, two blocks high. */
    static void clearAbove(ServerLevel level, BlockPos endpoint) {
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(endpoint.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** {@code elevator_arrival} tickets anywhere in the Level. */
    private static int arrivalTickets(ServerLevel level) {
        return TicketCounts.near(level, new ChunkPos(0, 0), Integer.MAX_VALUE / 2).getOrDefault(ARRIVAL_TICKET, 0);
    }

    private static int command(MinecraftServer server, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command,
                    server.createCommandSourceStack().withSuppressedOutput());
        } catch (CommandSyntaxException exception) {
            return -1;
        }
    }

    private static ElevatorRides rides() {
        return devices().elevatorRides();
    }

    private static EndgameDevices devices() {
        return EndgameRuntime.devices().orElseThrow();
    }

    private static EndgameService service() {
        return EndgameRuntime.operational().orElseThrow();
    }

    private static EndgameRoot root() {
        return service().root().orElseThrow();
    }
}
