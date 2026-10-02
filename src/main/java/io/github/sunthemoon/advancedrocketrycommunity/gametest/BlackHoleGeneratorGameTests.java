package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-057 on a running server: a generator on a station that warps to and from Cygnus X-1. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlackHoleGeneratorGameTests {
    private static final String BATCH = "endgame_black_hole";
    private static final String IDLE_BATCH = "endgame_black_hole_idle";
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_black_hole",
            Comparator.comparing(UUID::toString));

    private BlackHoleGeneratorGameTests() {
    }

    @AfterBatch(batch = BATCH)
    public static void restoreSwitch(ServerLevel level) {
        CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR.set(true);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void aGeneratorBurnsOnlyAtASingularityPausesWhenFullAndPushes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        UUID owner = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, owner, "Singularity fixture", CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
        StationState station = stations.commit(stationId);
        BlockPos controller = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.landingPad().z() + 6);
        ChunkPos chunk = new ChunkPos(controller);
        space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
        // Test setup only: a forced chunk keeps the Space Level ticking block entities without a player (vanilla
        // stops them 300 ticks after a Level has neither players nor forced chunks).
        space.setChunkForced(chunk.x, chunk.z, true);
        space.getChunkAt(controller);
        build(space, controller);
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity) space.getBlockEntity(controller);
        helper.assertTrue(generator.assignOwner(owner), "Owner not assigned");
        ItemStack named = new ItemStack(Items.STICK);
        named.setHoverName(Component.literal("Precious"));
        helper.assertTrue(generator.fuel().insertItem(0, named, false).getCount() == 1,
                "A tagged item was accepted as fuel");
        generator.fuel().insertItem(0, new ItemStack(Items.STICK, 4), false);
        BlockPos receiverPos = controller.north();
        int[] pushed = new int[1];
        // ADR-057 A1: the generator never adds a chunk ticket (review C12R-I3).
        Map<String, Integer> tickets = TicketCounts.near(space, chunk, 3);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.NO_SINGULARITY,
                        "A generator orbiting Earth did not idle: " + generator.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(generator.energy() == 0 && count(generator) == 4, "Something burned at Earth");
                    warp(helper, server, stations, stationId, SingularityContent.CYGNUS_X1);
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.GENERATING
                        && generator.energy() >= 5_000, "No generation at Cygnus X-1: " + generator.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(count(generator) == 3 && generator.rate() == 500,
                            "One item, 500 FE per tick: " + generator.describe());
                    helper.assertTrue(TicketCounts.near(space, chunk, 3).equals(tickets),
                            "A generating generator changed the tickets: " + TicketCounts.near(space, chunk, 3));
                    warp(helper, server, stations, stationId, CelestialIds.EARTH_ID);
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.NO_SINGULARITY,
                        "A warp away did not pause the burn"))
                .thenExecute(() -> {
                    int remaining = generator.remaining();
                    helper.assertTrue(remaining > 0 && count(generator) == 3, "The burn state was not kept");
                    // Pushing: an adjacent energy receiver in front of the controller takes energy.
                    space.setBlockAndUpdate(receiverPos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
                    warp(helper, server, stations, stationId, SingularityContent.CYGNUS_X1);
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.GENERATING
                        && ((GravityFieldBlockEntity) space.getBlockEntity(receiverPos)).energy().energy() > 0,
                        "A warp back did not resume or nothing was pushed"))
                .thenExecute(() -> {
                    space.setBlockAndUpdate(receiverPos, Blocks.AIR.defaultBlockState());
                    generator.setEnergyForTest(BlackHoleGeneratorBlockEntity.ENERGY_CAPACITY);
                    pushed[0] = generator.remaining();
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.PAUSED_FULL,
                        "A full buffer did not pause"))
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(generator.remaining() == pushed[0] && count(generator) == 3
                                    && generator.energy() == BlackHoleGeneratorBlockEntity.ENERGY_CAPACITY,
                            "A paused burn consumed fuel or wasted energy: " + generator.describe());
                    // Review C12R-I3: with no burn in progress, a full buffer takes no new item either.
                    generator.endBurnForTest();
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertTrue(generator.remaining() == 0 && count(generator) == 3
                                    && generator.status() == EndgameCode.PAUSED_FULL,
                            "An empty burn with a full buffer took an item: " + generator.describe());
                    helper.assertTrue(TicketCounts.near(space, chunk, 3).equals(tickets),
                            "A paused generator changed the tickets");
                    generator.setEnergyForTest(0);
                    CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR.set(false);
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.SYSTEM_DISABLED,
                        "The switch did not pause the generator"))
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(generator.energy() == 0 && generator.remaining() == 0 && count(generator) == 3,
                            "A disabled generator burned");
                    CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR.set(true);
                    generator.fuel().setStackInSlot(0, ItemStack.EMPTY);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = 0; dz <= 2; dz++) {
                                space.setBlockAndUpdate(controller.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    space.setChunkForced(chunk.x, chunk.z, false);
                    stations.delete(stationId);
                    stations.flush(server);
                })
                .thenSucceed();
    }

    /**
     * Review C13-F7: an idle generator (nothing to burn or push) re-derives its state every 20 ticks, so a warp away
     * shows within 21 ticks; new fuel ends the idle state at once; and a facing change of a burning generator is
     * validated at once, because the structure's box is recomputed when the facing changes.
     */
    @GameTest(template = "empty", batch = IDLE_BATCH, timeoutTicks = 1200)
    public static void anIdleGeneratorRechecksWithinTwentyTicksAndSeesFuelAndFacingAtOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        UUID owner = UUID.randomUUID();
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, owner, "Idle singularity fixture", SingularityContent.CYGNUS_X1,
                helper.getLevel().getGameTime());
        StationState station = stations.commit(stationId);
        BlockPos controller = new BlockPos(station.landingPad().x() + 6, StationLimits.LANDING_Y + 6,
                station.landingPad().z() + 6);
        ChunkPos chunk = new ChunkPos(controller);
        space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
        space.setChunkForced(chunk.x, chunk.z, true);
        space.getChunkAt(controller);
        build(space, controller);
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity) space.getBlockEntity(controller);
        helper.assertTrue(generator.assignOwner(owner), "Owner not assigned");
        long[] at = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.NO_FUEL,
                        "An empty generator at Cygnus X-1 did not idle: " + generator.describe()))
                .thenExecute(() -> {
                    warp(helper, server, stations, stationId, CelestialIds.EARTH_ID);
                    at[0] = space.getGameTime();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(generator.status() == EndgameCode.NO_SINGULARITY, "The warp away is not seen");
                    at[1] = space.getGameTime();
                })
                .thenExecute(() -> {
                    helper.assertTrue(at[1] - at[0] <= 21, "An idle generator saw the warp after "
                            + (at[1] - at[0]) + " ticks, not within its 20-tick re-check");
                    warp(helper, server, stations, stationId, SingularityContent.CYGNUS_X1);
                })
                .thenWaitUntil(() -> helper.assertTrue(generator.status() == EndgameCode.NO_FUEL,
                        "The warp back is not seen"))
                .thenExecute(() -> {
                    generator.fuel().insertItem(0, new ItemStack(Items.STICK, 4), false);
                    at[0] = space.getGameTime();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(generator.status() == EndgameCode.GENERATING, "New fuel is not burned");
                    at[1] = space.getGameTime();
                })
                .thenExecute(() -> {
                    helper.assertTrue(at[1] - at[0] <= 2, "New fuel waited " + (at[1] - at[0])
                            + " ticks for the idle re-check");
                    space.setBlock(controller, space.getBlockState(controller)
                            .setValue(BlackHoleGeneratorBlock.FACING, Direction.EAST), 2);
                    helper.assertTrue(space.getBlockEntity(controller) == generator, "The facing change replaced "
                            + "the block entity");
                    at[0] = space.getGameTime();
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(generator.status() == EndgameCode.UNFORMED, "The turned controller still "
                            + "counts as formed: " + generator.describe());
                    at[1] = space.getGameTime();
                })
                .thenExecute(() -> {
                    helper.assertTrue(at[1] - at[0] <= 3, "A facing change waited " + (at[1] - at[0])
                            + " ticks: the structure box was not recomputed");
                    generator.fuel().setStackInSlot(0, ItemStack.EMPTY);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dz = 0; dz <= 2; dz++) {
                                space.setBlockAndUpdate(controller.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                            }
                        }
                    }
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    space.setChunkForced(chunk.x, chunk.z, false);
                    stations.delete(stationId);
                    stations.flush(server);
                })
                .thenSucceed();
    }

    /** The 3 × 3 × 3 pattern for a north-facing controller: casings, obsidian sides and back, crying obsidian core. */
    private static void build(ServerLevel level, BlockPos controller) {
        BlockState casing = ModBlocks.ENDGAME_CASING.get().defaultBlockState();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    BlockPos pos = controller.offset(x - 1, y - 1, z);
                    BlockState state = casing;
                    if (y == 1 && (x == 0 && z == 1 || x == 2 && z == 1 || x == 1 && z == 2)) {
                        state = Blocks.OBSIDIAN.defaultBlockState();
                    } else if (y == 1 && x == 1 && z == 1) {
                        state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    } else if (y == 1 && x == 1 && z == 0) {
                        state = ModBlocks.BLACK_HOLE_GENERATOR.get().defaultBlockState()
                                .setValue(BlackHoleGeneratorBlock.FACING, Direction.NORTH);
                    }
                    level.setBlockAndUpdate(pos, state);
                }
            }
        }
    }

    /** Test fixture: a funded, checked relocation, as a committed warp performs it (ADR-044). */
    private static void warp(GameTestHelper helper, MinecraftServer server, StationRegistrySavedData stations,
                             UUID stationId, ResourceLocation target) {
        stations.foldWarpCredits(Map.of(stationId, 8_000_000));
        helper.assertTrue(stations.checkedRelocation(server, stations.find(stationId).orElseThrow(), target, 8_000_000)
                == StationRegistrySavedData.CheckedUpdate.COMMITTED, "The relocation fixture failed");
    }

    private static int count(BlackHoleGeneratorBlockEntity generator) {
        int total = 0;
        for (int slot = 0; slot < BlackHoleGeneratorBlockEntity.FUEL_SLOTS; slot++) {
            total += generator.fuel().getStackInSlot(slot).getCount();
        }
        return total;
    }
}
