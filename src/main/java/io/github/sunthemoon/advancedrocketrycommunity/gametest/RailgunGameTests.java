package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-056 on a running server, with the ADR-054 section 11 ledger underneath: escrow, registration after an aged save,
 * travel, a claim into incoming, the move to the receive buffer only after the destination's chunk was saved holding
 * it, acknowledgement and pruning; refusals that change nothing; a removed destination and an operator redirect; a
 * busy railgun a player cannot break; and no chunk ticket from any of it.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailgunGameTests {
    // One batch per test: tests of a batch stand side by side, closer than two 3 × 6 × 3 railguns allow.
    private static final String BATCH = "endgame_railgun";
    private static final String REFUSALS = "endgame_railgun_refusals";
    private static final String STATION = "endgame_railgun_station";
    private static final String MENU = "endgame_railgun_menu";
    private static final String TWO_SOURCES = "endgame_railgun_two_sources";
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_railgun",
            Comparator.comparing(UUID::toString));

    private RailgunGameTests() {
    }

    @AfterBatch(batch = REFUSALS)
    public static void restoreSwitch(ServerLevel level) {
        CommonConfig.ENDGAME_RAILGUN.set(true);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 900)
    public static void cargoTravelsAndMovesOnlyAfterTheDestinationSavedIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 2, 8));
        Map<String, Integer> tickets = TicketCounts.near(level, new ChunkPos(sourcePos), 2);
        RailgunBlockEntity source = build(level, sourcePos, owner);
        RailgunBlockEntity target = build(level, targetPos, owner);
        TransitKey key = new TransitKey(source.deviceId().orElseThrow(), 1L);
        // Vanilla also saves dirty chunks on its own, so every save of the two chunks is recorded and the 40-tick
        // rule is checked against the saves that really happened.
        ChunkSaveWatcher saves = ChunkSaveWatcher.start(helper, sourcePos, targetPos);
        long[] at = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, level, source, target))
                .thenExecute(() -> {
                    service().barrier(root -> null); // Both registrations become durable.
                    source.selectForTest(target.deviceId().orElseThrow(), false);
                    source.storage().input().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
                    source.storage().energy().set(100_000);
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> helper.assertTrue(source.source().outbox().size() == 1,
                        "No escrow: " + source.describe()))
                .thenExecute(() -> {
                    at[0] = level.getGameTime();
                    // Same Level and body, 7 blocks apart: LOCAL, 25,000 + 10 × 7 FE, 20 ticks.
                    helper.assertTrue(source.storage().input().getStackInSlot(0).isEmpty()
                                    && source.storage().energy().energy() == 100_000 - 25_070
                                    && source.source().outbox().get(0).travel() == 20,
                            "The escrow did not take the stack and the quoted cost: " + source.describe());
                    helper.assertTrue(root().transits().record(key).isEmpty(), "Registered before a save showed it");
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, sourcePos);
                    helper.assertTrue(root().transits().record(key).isPresent(), "Not registered after a save");
                })
                .thenExecute(() -> helper.assertTrue(saves.between(sourcePos, at[0], level.getGameTime() - 40),
                        "Registered without a save of the escrow at least 40 ticks old: " + saves))
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    helper.assertTrue(source.source().outbox().isEmpty(), "The durable record was not released");
                })
                .thenWaitUntil(() -> helper.assertTrue(record(key).state() == TransitRecord.State.CLAIMED
                        && target.destination().incoming().containsKey(key), "Not claimed into incoming"))
                .thenExecute(() -> {
                    at[1] = level.getGameTime();
                    helper.assertTrue(empty(target) && !record(key).acknowledged(),
                            "The claim went straight to the receive buffer");
                })
                .thenExecuteAfter(5, () -> LaserTargetGameTests.chunkSaved(level, targetPos))
                .thenWaitUntil(() -> helper.assertTrue(target.storage().receive().getStackInSlot(0).getCount() == 5
                        && record(key).acknowledged(), "Not moved after an aged save: " + target.describe()))
                .thenExecute(() -> helper.assertTrue(saves.between(targetPos, at[1], level.getGameTime() - 40),
                        "Moved without a save of the incoming payload at least 40 ticks old: " + saves))
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    LaserTargetGameTests.chunkSaved(level, targetPos);
                    helper.assertTrue(root().transits().record(key).isEmpty()
                            && target.destination().receipts().isEmpty(), "Not pruned after the saved move");
                })
                .thenExecute(() -> {
                    helper.assertTrue(TicketCounts.near(level, new ChunkPos(sourcePos), 2).equals(tickets),
                            "A railgun changed the chunk tickets: " + TicketCounts.near(level,
                                    new ChunkPos(sourcePos), 2));
                    saves.stop();
                    target.storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    demolish(level, sourcePos);
                    demolish(level, targetPos);
                    helper.assertTrue(root().retired(key.source()), "A removed railgun was not retired");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = REFUSALS, timeoutTicks = 1200)
    public static void refusalsChangeNothingAndMissingCargoIsRedirectedHome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID owner = UUID.randomUUID();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 2, 8));
        BlockPos foreignPos = helper.absolutePos(new BlockPos(8, 2, 1));
        RailgunBlockEntity source = build(level, sourcePos, owner);
        RailgunBlockEntity target = build(level, targetPos, owner);
        RailgunBlockEntity foreign = build(level, foreignPos, UUID.randomUUID());
        TransitKey key = new TransitKey(source.deviceId().orElseThrow(), 1L);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    registered(helper, level, source, target);
                    LaserTargetGameTests.chunkSaved(level, foreignPos);
                    helper.assertTrue(foreign.endpointActive(), "The foreign railgun is not registered");
                })
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    source.storage().input().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 5));
                    source.storage().energy().set(1_000);
                    source.selectForTest(foreign.deviceId().orElseThrow(), false);
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> refused(helper, source, EndgameCode.TARGET_FOREIGN))
                .thenExecute(() -> {
                    source.selectForTest(target.deviceId().orElseThrow(), false);
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> refused(helper, source, EndgameCode.INSUFFICIENT_ENERGY))
                .thenExecute(() -> {
                    source.storage().energy().set(100_000);
                    CommonConfig.ENDGAME_RAILGUN.set(false);
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> refused(helper, source, EndgameCode.SYSTEM_DISABLED))
                .thenExecute(() -> {
                    CommonConfig.ENDGAME_RAILGUN.set(true);
                    helper.assertTrue(source.storage().input().getStackInSlot(0).getCount() == 5
                                    && source.storage().energy().energy() == 100_000 && source.source().outbox().isEmpty(),
                            "A refused launch changed something: " + source.describe());
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, sourcePos);
                    helper.assertTrue(root().transits().record(key).isPresent(), "Not registered");
                })
                .thenExecute(() -> {
                    // A player who is not an operator cannot break a railgun that holds an outbox entry.
                    Player player = helper.makeMockPlayer();
                    BlockState state = level.getBlockState(sourcePos);
                    helper.assertFalse(state.getBlock().onDestroyedByPlayer(state, level, sourcePos, player, true,
                            Fluids.EMPTY.defaultFluidState()), "A busy railgun was broken by a player");
                    helper.assertTrue(level.getBlockState(sourcePos).is(ModBlocks.RAILGUN.get()),
                            "The busy railgun is gone");
                    // The disabled switch keeps arrivals going: the destination is removed while the cargo travels.
                    CommonConfig.ENDGAME_RAILGUN.set(false);
                    demolish(level, targetPos);
                    helper.assertTrue(root().endpoint(target.deviceId().orElseThrow()).isEmpty(),
                            "The removed destination is still indexed");
                })
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    helper.assertTrue(record(key).state() == TransitRecord.State.ARRIVED, "Not arrived");
                })
                .thenExecute(() -> {
                    helper.assertTrue(service().transitOperations().list(0).stream()
                            .anyMatch(line -> line.contains("DESTINATION_MISSING")), "Not listed as missing");
                    helper.assertTrue(service().transitOperations().redirect(key, foreign.deviceId().orElseThrow(),
                            CONSOLE, false, level.getGameTime()) == EndgameCode.UNAUTHORIZED,
                            "A stranger redirected the cargo");
                    helper.assertTrue(service().transitOperations().redirect(key, key.source(), CONSOLE, true,
                            level.getGameTime()) == EndgameCode.OK, "The operator redirect home was refused");
                    helper.assertTrue(record(key).redirected() && record(key).destination().equals(key.source()),
                            "The record was not redirected");
                })
                .thenWaitUntil(() -> helper.assertTrue(source.destination().incoming().containsKey(key),
                        "The source did not claim its returned cargo"))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, sourcePos);
                    helper.assertTrue(source.storage().receive().getStackInSlot(0).getCount() == 5,
                            "The returned cargo did not reach the receive buffer");
                })
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    LaserTargetGameTests.chunkSaved(level, sourcePos);
                    helper.assertTrue(root().transits().record(key).isEmpty(), "Not pruned");
                })
                .thenExecute(() -> {
                    CommonConfig.ENDGAME_RAILGUN.set(true);
                    source.storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    demolish(level, sourcePos);
                    demolish(level, foreignPos);
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(sourcePos).inflate(12.0D))
                            .forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    /**
     * A planet and a station orbiting it: the route is ORBITAL (250,000 FE, 600 ticks); cargo for a destination whose
     * chunk unloaded waits as {@code ARRIVED} and is claimed when the chunk loads again; once the station warped to
     * Cygnus X-1, a launch back to the planet is {@code ROUTE_OUT_OF_SYSTEM}.
     */
    @GameTest(template = "empty", batch = STATION, timeoutTicks = 2400)
    public static void aStationRouteIsOrbitalWaitsForAnUnloadedDestinationAndEndsAtTheSystem(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        UUID owner = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, owner, "Railgun fixture", CelestialIds.EARTH_ID, level.getGameTime());
        StationState station = stations.commit(stationId);
        BlockPos stationPos = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.landingPad().z() + 6);
        ChunkPos chunk = new ChunkPos(stationPos);
        // Test setup only: a ticket and a forced chunk keep the station's chunk loaded and ticking without a player.
        space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
        space.setChunkForced(chunk.x, chunk.z, true);
        space.getChunkAt(stationPos);
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        Map<String, Integer> tickets = TicketCounts.near(level, new ChunkPos(sourcePos), 2);
        RailgunBlockEntity source = build(level, sourcePos, owner);
        build(space, stationPos, owner);
        UUID targetId = station(space, stationPos).deviceId().orElseThrow();
        TransitKey key = new TransitKey(source.deviceId().orElseThrow(), 1L);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(space, stationPos);
                    registered(helper, level, source);
                    helper.assertTrue(station(space, stationPos).endpointActive(), "The station railgun is not "
                            + "registered: " + station(space, stationPos).describe());
                })
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    source.selectForTest(targetId, false);
                    source.storage().input().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
                    source.storage().energy().set(300_000);
                    source.launchForTest(owner);
                })
                .thenWaitUntil(() -> helper.assertTrue(source.source().outbox().size() == 1, "No escrow: "
                        + source.describe()))
                .thenExecute(() -> helper.assertTrue(source.source().outbox().get(0).paidFe() == 250_000
                                && source.source().outbox().get(0).travel() == 600
                                && source.storage().energy().energy() == 50_000,
                        "Not the ORBITAL quote: " + source.describe()))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, sourcePos);
                    helper.assertTrue(root().transits().record(key).isPresent(), "Not registered");
                })
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    space.setChunkForced(chunk.x, chunk.z, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(space.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                        "The destination chunk did not unload"))
                .thenWaitUntil(() -> helper.assertTrue(record(key).state() == TransitRecord.State.ARRIVED,
                        "Not arrived"))
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(record(key).state() == TransitRecord.State.ARRIVED,
                            "Claimed while the destination was unloaded");
                    helper.assertTrue(space.getChunkSource().getChunkNow(chunk.x, chunk.z) == null,
                            "The ledger loaded the destination chunk");
                    space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    space.setChunkForced(chunk.x, chunk.z, true);
                    space.getChunkAt(stationPos);
                })
                .thenWaitUntil(() -> helper.assertTrue(station(space, stationPos).destination().incoming()
                        .containsKey(key), "Not claimed after the destination loaded"))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(space, stationPos);
                    helper.assertTrue(station(space, stationPos).storage().receive().getStackInSlot(0).getCount() == 3,
                            "The cargo did not reach the receive buffer on the station");
                })
                .thenExecute(() -> {
                    helper.assertTrue(TicketCounts.near(level, new ChunkPos(sourcePos), 2).equals(tickets),
                            "A railgun changed the chunk tickets");
                    stations.foldWarpCredits(Map.of(stationId, 8_000_000));
                    helper.assertTrue(stations.checkedRelocation(server, stations.find(stationId).orElseThrow(),
                                    SingularityContent.CYGNUS_X1, 8_000_000)
                            == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The warp fixture failed");
                    RailgunBlockEntity back = station(space, stationPos);
                    back.storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    back.storage().input().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
                    back.storage().energy().set(300_000);
                    back.selectForTest(source.deviceId().orElseThrow(), false);
                    back.launchForTest(owner);
                })
                .thenWaitUntil(() -> refused(helper, station(space, stationPos), EndgameCode.ROUTE_OUT_OF_SYSTEM))
                .thenExecute(() -> {
                    service().transitOperations().purge(key, CONSOLE, level.getGameTime());
                    station(space, stationPos).storage().input().setStackInSlot(0, ItemStack.EMPTY);
                    demolish(level, sourcePos);
                    demolish(space, stationPos);
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    space.setChunkForced(chunk.x, chunk.z, false);
                    stations.delete(stationId);
                    stations.flush(server);
                })
                .thenSucceed();
    }

    /**
     * Two sources launch into one destination: both are claimed and both move once its chunk saved them; an
     * operator's {@code transfer list} from the console shows the records while they exist.
     */
    @GameTest(template = "empty", batch = TWO_SOURCES, timeoutTicks = 900)
    public static void twoSourcesShareOneDestination(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID owner = UUID.randomUUID();
        BlockPos firstPos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos secondPos = helper.absolutePos(new BlockPos(8, 2, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 2, 8));
        RailgunBlockEntity first = build(level, firstPos, owner);
        RailgunBlockEntity second = build(level, secondPos, owner);
        RailgunBlockEntity target = build(level, targetPos, owner);
        TransitKey firstKey = new TransitKey(first.deviceId().orElseThrow(), 1L);
        TransitKey secondKey = new TransitKey(second.deviceId().orElseThrow(), 1L);
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, level, first, second, target))
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    for (RailgunBlockEntity source : List.of(first, second)) {
                        source.selectForTest(target.deviceId().orElseThrow(), false);
                        source.storage().energy().set(100_000);
                        source.launchForTest(owner);
                    }
                    first.storage().input().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
                    second.storage().input().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
                })
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, firstPos);
                    LaserTargetGameTests.chunkSaved(level, secondPos);
                    helper.assertTrue(root().transits().record(firstKey).isPresent()
                            && root().transits().record(secondKey).isPresent(), "Not both registered");
                })
                .thenExecute(() -> {
                    int listed;
                    try {
                        listed = server.getCommands().getDispatcher().execute("arce endgame transfer list",
                                server.createCommandSourceStack().withSuppressedOutput());
                    } catch (CommandSyntaxException exception) {
                        listed = -1;
                    }
                    helper.assertTrue(listed >= 2, "The console transfer list did not count the records: " + listed);
                    service().barrier(root -> null);
                })
                .thenWaitUntil(() -> helper.assertTrue(target.destination().incoming().containsKey(firstKey)
                        && target.destination().incoming().containsKey(secondKey), "Not both claimed"))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(level, targetPos);
                    helper.assertTrue(target.storage().receive().getStackInSlot(0).getCount()
                            + target.storage().receive().getStackInSlot(1).getCount() == 8
                            && target.destination().incoming().isEmpty(), "Not both moved: " + target.describe());
                })
                .thenWaitUntil(() -> {
                    service().barrier(root -> null);
                    LaserTargetGameTests.chunkSaved(level, targetPos);
                    helper.assertTrue(root().transits().record(firstKey).isEmpty()
                            && root().transits().record(secondKey).isEmpty(), "Not both pruned");
                })
                .thenExecute(() -> {
                    target.storage().receive().setStackInSlot(0, ItemStack.EMPTY);
                    target.storage().receive().setStackInSlot(1, ItemStack.EMPTY);
                    demolish(level, firstPos);
                    demolish(level, secondPos);
                    demolish(level, targetPos);
                })
                .thenSucceed();
    }

    /**
     * The menu: the owner selects the other railgun and a launch without a payload is refused with its code; a
     * stranger sees the public view, presses nothing and takes nothing. Then a copy of a removed railgun comes back
     * holding an outbox entry: it is frozen, and its owner's {@code endpoint resolve} returns the never-registered
     * payload to the input (ADR-054 section 9).
     */
    @GameTest(template = "empty", batch = MENU, timeoutTicks = 600)
    public static void theMenuAndTheOwnersResolve(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID ownerId = UUID.randomUUID();
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos targetPos = helper.absolutePos(new BlockPos(1, 2, 8));
        RailgunBlockEntity source = build(level, sourcePos, ownerId);
        RailgunBlockEntity target = build(level, targetPos, ownerId);
        List<String> ownerReplies = new ArrayList<>();
        List<ServerPlayer> joined = new ArrayList<>();
        CompoundTag[] copy = new CompoundTag[1];
        helper.startSequence()
                .thenWaitUntil(() -> registered(helper, level, source, target))
                .thenExecute(() -> {
                    service().barrier(root -> null);
                    ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "railgunOwner", level,
                            sourcePos.west(2), ownerReplies);
                    joined.add(owner);
                    open(level, owner, sourcePos);
                    helper.assertTrue(owner.containerMenu instanceof RailgunMenu, "The owner's menu did not open");
                    helper.assertTrue(owner.containerMenu.clickMenuButton(owner, RailgunMenu.BUTTON_NEXT)
                            && source.target().filter(target.deviceId().orElseThrow()::equals).isPresent(),
                            "Next did not select the other railgun: " + source.describe());
                })
                .thenExecuteAfter(12, () -> {
                    ServerPlayer owner = joined.get(0);
                    owner.containerMenu.clickMenuButton(owner, RailgunMenu.BUTTON_LAUNCH);
                    helper.assertTrue(service().audit().page("railgun", 0).stream().anyMatch(line ->
                                    line.contains("NO_PAYLOAD") && line.contains(source.deviceId().orElseThrow()
                                            .toString())),
                            "A launch without a payload was not refused with its code: " + ownerReplies);
                    source.storage().input().setStackInSlot(0, new ItemStack(Items.EMERALD, 4));
                    ServerPlayer stranger = ConnectedTestPlayers.join(server, UUID.randomUUID(), "railgunStranger",
                            level, sourcePos.east(2), new ArrayList<>());
                    joined.add(stranger);
                    open(level, stranger, sourcePos);
                    helper.assertTrue(stranger.containerMenu instanceof RailgunMenu menu
                            && !menu.itemActionAllowed(EndgameAction.WITHDRAW)
                            && !menu.clickMenuButton(stranger, RailgunMenu.BUTTON_PREVIOUS),
                            "The stranger's view is not a public view");
                    stranger.containerMenu.clicked(0, 0, ClickType.QUICK_MOVE, stranger);
                    stranger.containerMenu.clicked(0, 0, ClickType.PICKUP, stranger);
                    helper.assertTrue(source.storage().input().getStackInSlot(0).getCount() == 4
                            && stranger.getInventory().countItem(Items.EMERALD) == 0,
                            "A stranger took from another player's railgun");
                    stranger.containerMenu = stranger.inventoryMenu;
                    // A copy of a removed railgun comes back holding a never-registered outbox entry.
                    source.storage().input().setStackInSlot(0, ItemStack.EMPTY);
                    copy[0] = target.saveWithoutMetadata();
                    TransitSourceState held = new TransitSourceState();
                    held.escrow(source.deviceId().orElseThrow(), TransitPayload.of(List.of(new ItemStack(
                            Items.EMERALD, 4))).orElseThrow(), 25_070, 20, EndgameSystem.RAILGUN);
                    held.write(copy[0].getCompound("endgame").getCompound("transit"));
                    demolish(level, targetPos);
                    helper.assertTrue(root().retired(target.deviceId().orElseThrow()), "Not retired");
                    build(level, targetPos, ownerId);
                    level.getBlockEntity(targetPos).load(copy[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(((RailgunBlockEntity) level.getBlockEntity(targetPos)).frozen(),
                        "The returning copy was not frozen"))
                .thenExecuteAfter(25, () -> {
                    RailgunBlockEntity back = (RailgunBlockEntity) level.getBlockEntity(targetPos);
                    helper.assertTrue(!back.busy() && back.source().outbox().size() == 1,
                            "A frozen copy reads as busy or lost its entry: " + back.describe());
                    ServerPlayer owner = joined.get(0);
                    owner.containerMenu = owner.inventoryMenu;
                    int result = command(server, owner, "arce endgame endpoint resolve "
                            + back.deviceId().orElseThrow());
                    helper.assertTrue(result == 1 && back.source().outbox().isEmpty()
                                    && back.storage().input().getStackInSlot(0).getCount() == 4,
                            "The owner's resolve did not return the payload: " + result + " " + back.describe());
                    back.storage().input().setStackInSlot(0, ItemStack.EMPTY);
                    joined.forEach(player -> server.getPlayerList().remove(player));
                    demolish(level, targetPos);
                    demolish(level, sourcePos);
                })
                .thenSucceed();
    }

    private static void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
        level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static int command(MinecraftServer server, ServerPlayer player, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        } catch (CommandSyntaxException exception) {
            return -1;
        }
    }

    private static RailgunBlockEntity station(ServerLevel space, BlockPos pos) {
        return (RailgunBlockEntity) space.getBlockEntity(pos);
    }

    private static void registered(GameTestHelper helper, ServerLevel level, RailgunBlockEntity... railguns) {
        for (RailgunBlockEntity railgun : railguns) {
            LaserTargetGameTests.chunkSaved(level, railgun.getBlockPos());
        }
        for (RailgunBlockEntity railgun : railguns) {
            helper.assertTrue(railgun.endpointActive() && railgun.structureCode() == EndgameCode.OK,
                    "Not registered and formed: " + railgun.describe() + " cells:"
                            + mismatches(level, railgun.getBlockPos()));
        }
    }

    private static void refused(GameTestHelper helper, RailgunBlockEntity railgun, EndgameCode code) {
        helper.assertTrue(railgun.lastCode() == code, "Expected " + code + ": " + railgun.describe());
    }

    private static boolean empty(RailgunBlockEntity railgun) {
        for (int slot = 0; slot < railgun.storage().receive().getSlots(); slot++) {
            if (!railgun.storage().receive().getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The 3 × 6 × 3 railgun for a north-facing controller in the middle of the base's front edge: a casing base, an
     * iron barrel four blocks high in the middle and a casing muzzle. The cells around the barrel, which the pattern
     * allows to be air or casing, are casing here: the volume is solid, so no fluid left near the shared test origin
     * by the terrain or earlier batches can flow into it.
     */
    static RailgunBlockEntity build(ServerLevel level, BlockPos controller, UUID owner) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z <= 2; z++) {
                    level.setBlockAndUpdate(controller.offset(x, y, z), casing);
                }
            }
        }
        for (int y = 1; y <= 4; y++) {
            level.setBlockAndUpdate(controller.offset(0, y, 1), Blocks.IRON_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(controller.offset(0, 5, 1), casing);
        level.setBlockAndUpdate(controller, ModBlocks.RAILGUN.get().defaultBlockState()
                .setValue(RailgunBlock.FACING, Direction.NORTH));
        RailgunBlockEntity railgun = (RailgunBlockEntity) level.getBlockEntity(controller);
        railgun.assignOwner(owner);
        return railgun;
    }

    /** Removes the whole structure, the controller first (an operator-free removal, as an explosion would). */
    static void demolish(ServerLevel level, BlockPos controller) {
        level.setBlockAndUpdate(controller, Blocks.AIR.defaultBlockState());
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z <= 2; z++) {
                    level.setBlockAndUpdate(controller.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static TransitRecord record(TransitKey key) {
        return root().transits().record(key).orElseThrow(() -> new IllegalStateException("No record " + key));
    }

    private static EndgameService service() {
        return EndgameRuntime.operational().orElseThrow();
    }

    private static EndgameRoot root() {
        return service().root().orElseThrow();
    }

    /** The cells of a railgun's volume that differ from the pattern, for a failure message. */
    private static String mismatches(ServerLevel level, BlockPos controller) {
        StringBuilder found = new StringBuilder();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = controller.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean expected;
                    if (y == 0) {
                        expected = x == 0 && z == 0 ? state.is(ModBlocks.RAILGUN.get())
                                : state.is(ModBlocks.ENDGAME_CASING.get());
                    } else if (x == 0 && z == 1) {
                        expected = y < 5 ? state.is(Blocks.IRON_BLOCK) : state.is(ModBlocks.ENDGAME_CASING.get());
                    } else {
                        expected = state.isAir() || state.is(ModBlocks.ENDGAME_CASING.get());
                    }
                    if (!expected) {
                        found.append(' ').append(x).append(',').append(y).append(',').append(z).append('=')
                                .append(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock()));
                    }
                }
            }
        }
        return found.length() == 0 ? " no mismatching cell" : found.toString();
    }
}
