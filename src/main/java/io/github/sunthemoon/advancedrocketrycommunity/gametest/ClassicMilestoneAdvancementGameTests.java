package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ClassicMachineData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternJsonCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicEventTrigger;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.critereon.ChangeDimensionTrigger;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 §6.2 on the real server: each custom trigger fires at its completion point and not at the steps before
 * it; a world-first milestone needs the world's first event (W44), whose isolated coverage is the A1 batch.
 * Connected mock players earn through production paths only, never through grants or direct trigger calls.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicMilestoneAdvancementGameTests {
    private ClassicMilestoneAdvancementGameTests() {
    }

    @GameTest(template = "empty", batch = "classic_milestone_tree", timeoutTicks = 20)
    public static void theSeventeenAdvancementsLoadWithTheRegisteredEventTrigger(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(CriteriaTriggers.getCriterion(ClassicEventTrigger.ID) == (Object) ClassicAdvancementEvents.TRIGGER,
                "The classic event trigger is not the registered instance");
        for (Goal goal : Goal.values()) {
            Advancement advancement = advancement(server, goal.id());
            helper.assertTrue(goal.parentId() == null ? advancement.getParent() == null
                    : advancement.getParent() != null && advancement.getParent().getId().toString().equals(goal.parentId()),
                    "Parent differs for " + goal.id());
        }
        for (Milestone milestone : Milestone.values()) {
            Advancement advancement = advancement(server, milestone.id());
            helper.assertTrue(advancement.getParent() != null
                    && advancement.getParent().getId().toString().equals(milestone.parentId()), "Parent of " + milestone);
            var rewards = advancement.getRewards().serializeToJson();
            helper.assertTrue(rewards.isJsonNull() || rewards.getAsJsonObject().size() == 0, "Reward on " + milestone);
            CriterionTriggerInstance trigger = advancement.getCriteria().get(milestone.criterion()).getTrigger();
            boolean expected = switch (milestone.kind()) {
                case INVENTORY -> trigger instanceof InventoryChangeTrigger.TriggerInstance;
                case ARRIVAL -> trigger instanceof ChangeDimensionTrigger.TriggerInstance;
                case PENDING -> trigger instanceof ImpossibleTrigger.TriggerInstance;
                case EVENT -> trigger instanceof ClassicEventTrigger.Instance instance
                        && instance.event().serializedName().equals(milestone.target())
                        && instance.serializeToJson(SerializationContext.INSTANCE).get(ClassicEventTrigger.EVENT)
                                .getAsString().equals(milestone.target());
            };
            helper.assertTrue(expected, "Loaded criterion differs for " + milestone + ": " + trigger);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_seat_on_tnt", timeoutTicks = 40)
    public static void seatOnTntFiresOnlyForAServerValidatedUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tnt = helper.absolutePos(new BlockPos(3, 2, 1));
        BlockPos stone = helper.absolutePos(new BlockPos(1, 2, 3));
        BlockPos distant = helper.absolutePos(new BlockPos(1, 22, 1));
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            level.setBlockAndUpdate(tnt, Blocks.TNT.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(distant, Blocks.TNT.defaultBlockState());
            ServerPlayer player = join(helper, level, helper.absolutePos(new BlockPos(1, 2, 1)), "classicSeat", joined);
            acknowledgeTeleport(player);
            helper.assertTrue(!done(player, Milestone.SEAT_ON_TNT), "Fresh player already has the seat milestone");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_SEAT.get(), 4));
            use(player, distant);
            helper.assertTrue(!done(player, Milestone.SEAT_ON_TNT), "An out-of-reach claim reached the trigger");
            use(player, stone);
            helper.assertTrue(!done(player, Milestone.SEAT_ON_TNT), "A seat on stone fired the TNT event");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
            use(player, tnt);
            helper.assertTrue(!done(player, Milestone.SEAT_ON_TNT), "Another item on TNT fired the event");
            level.setBlockAndUpdate(tnt.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(stone.above(), Blocks.AIR.defaultBlockState());

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_SEAT.get(), 4));
            use(player, tnt);
            helper.assertTrue(done(player, Milestone.SEAT_ON_TNT), "The validated seat use on TNT did not fire");
            helper.assertTrue(level.getBlockState(tnt).is(Blocks.TNT), "The trigger changed the TNT");
            helper.assertTrue(level.getEntitiesOfClass(PrimedTnt.class, new AABB(tnt).inflate(8)).isEmpty(),
                    "The trigger primed the TNT");
            boolean placed = level.getBlockState(tnt.above()).is(ModBlocks.ROCKET_SEAT.get());
            helper.assertTrue(player.getMainHandItem().getCount() == (placed ? 3 : 4),
                    "Items changed beyond vanilla seat placement");
            long earned = obtained(player, Milestone.SEAT_ON_TNT);
            level.setBlockAndUpdate(tnt.above(), Blocks.AIR.defaultBlockState());
            use(player, tnt);
            helper.assertTrue(obtained(player, Milestone.SEAT_ON_TNT) == earned, "A replay changed the receipt");
        } finally {
            joined.forEach(player -> level.getServer().getPlayerList().remove(player));
            for (BlockPos position : List.of(tnt, tnt.above(), stone, stone.above(), distant, distant.above())) {
                level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "classic_formation_rolling", timeoutTicks = 60)
    public static void rollingFormationRewardsNearbyPlayersOnlyForANewGeneration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer near = join(helper, level, helper.absolutePos(new BlockPos(4, 2, 1)), "classicNear", joined);
        ServerPlayer far = join(helper, level, helper.absolutePos(new BlockPos(4, 2, 4)).east(48), "classicFar", joined);
        ServerPlayer late = join(helper, level, helper.absolutePos(new BlockPos(4, 2, 4)).west(64), "classicLate", joined);
        long[] firstReceipt = new long[1];
        guarded(level, joined, () -> {
            RollingMachineGameTestFixtures.placeStructure(helper);
            helper.assertTrue(!done(near, Milestone.FORMED_MULTIBLOCK), "Placement fired before formation");
        });
        helper.runAtTickTime(6, () -> guarded(level, joined, () -> {
            helper.assertTrue(RollingMachineGameTestFixtures.controller(helper).generation() == 1L,
                    "Rolling machine did not form generation one");
            helper.assertTrue(done(near, Milestone.FORMED_MULTIBLOCK), "A nearby player missed the formation");
            helper.assertTrue(!done(far, Milestone.FORMED_MULTIBLOCK), "A player 48 blocks away earned the formation");
            firstReceipt[0] = obtained(near, Milestone.FORMED_MULTIBLOCK);
            // Only now does the late player stand close; a revalidation of the same generation must not reward it.
            late.teleportTo(level, near.getX() + 1.0D, near.getY(), near.getZ(), 0.0F, 0.0F);
            helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
        }));
        helper.runAtTickTime(12, () -> guarded(level, joined, () -> {
            helper.assertTrue(RollingMachineGameTestFixtures.controller(helper).generation() == 1L
                    && RollingMachineGameTestFixtures.controller(helper).formationState()
                    == MultiblockFormationState.FORMED, "Revalidation changed the formation");
            helper.assertTrue(!done(late, Milestone.FORMED_MULTIBLOCK),
                    "Revalidating an existing formation fired the event");
            helper.setBlock(RollingMachineGameTestFixtures.FLUID_INPUT, Blocks.AIR);
        }));
        helper.runAtTickTime(18, () -> guarded(level, joined, () -> {
            helper.assertTrue(RollingMachineGameTestFixtures.controller(helper).formationState()
                    == MultiblockFormationState.UNFORMED, "Removing a port did not unform the machine");
            helper.assertTrue(!done(late, Milestone.FORMED_MULTIBLOCK), "Breaking the structure fired the event");
            helper.setBlock(RollingMachineGameTestFixtures.FLUID_INPUT, ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get());
        }));
        helper.runAtTickTime(26, () -> {
            try {
                helper.assertTrue(RollingMachineGameTestFixtures.controller(helper).generation() == 2L,
                        "Rebuild did not advance the generation");
                helper.assertTrue(done(late, Milestone.FORMED_MULTIBLOCK), "The rebuilt formation did not fire");
                helper.assertTrue(obtained(near, Milestone.FORMED_MULTIBLOCK) == firstReceipt[0],
                        "A second formation changed the first receipt");
                helper.assertTrue(!done(far, Milestone.FORMED_MULTIBLOCK), "A distant player earned the rebuild");
            } finally {
                removeAll(level, joined);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "classic_formation_lathe", timeoutTicks = 40)
    public static void latheFormationRewardsNearbyPlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> joined = new ArrayList<>();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 4, 4));
        ServerPlayer near = join(helper, level, helper.absolutePos(new BlockPos(1, 2, 1)), "classicLatheNear", joined);
        ServerPlayer far = join(helper, level, origin.south(40), "classicLatheFar", joined);
        List<BlockPos> placed = new ArrayList<>();
        Runnable cleanup = () -> {
            removeAll(level, joined);
            // The controller first, so its claims are released before the hatches go.
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            placed.forEach(position -> level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        };
        try {
            placed.addAll(placeLathe(level, origin));
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
        helper.runAtTickTime(6, () -> {
            try {
                helper.assertTrue(level.getBlockEntity(origin) instanceof ClassicControllerBlockEntity controller
                        && controller.formed(), "The lathe did not form");
                helper.assertTrue(done(near, Milestone.FORMED_MULTIBLOCK), "A nearby player missed the lathe formation");
                helper.assertTrue(!done(far, Milestone.FORMED_MULTIBLOCK), "A player 40 blocks away earned it");
            } finally {
                cleanup.run();
            }
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "classic_moon_landing", timeoutTicks = 800)
    public static void moonLandingRewardsOnlyPassengersAtTheLandingCompletion(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon unavailable");
        RocketFlightGameTestFixtures.clearTransferJournal(earth);
        // W44: an explicit non-electing state; the world-first positives are the isolated A1 batch.
        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport
                .occupyFirstMoonLanding(helper);
        RocketFlightGameTestFixtures.primePadChunks(moon);
        RocketFlightGameTestFixtures.clearPadRockets(moon);
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer passenger = join(helper, earth, helper.absolutePos(new BlockPos(2, 3, 2)), "classicPassenger",
                joined);
        ServerPlayer earthWatcher = join(helper, earth, helper.absolutePos(new BlockPos(6, 2, 6)), "classicEarthWatch",
                joined);
        // On the Moon but off the pads, so it never blocks the landing it watches.
        ServerPlayer moonWatcher = join(helper, moon, SafeCelestialTravel.FIXED_FEET_POSITION.east(20),
                "classicMoonWatch", joined);
        UUID transfer = UUID.randomUUID();
        UUID[] logical = new UUID[1];
        Runnable cleanup = () -> {
            for (ServerLevel level : List.of(earth, moon)) {
                RocketEntity rocket = logical[0] == null ? null
                        : RocketFlightGameTestFixtures.loadedLogicalRocket(level, logical[0]);
                if (rocket != null) {
                    rocket.ejectPassengers();
                    rocket.discard();
                }
            }
            RocketTransferSavedData journal = RocketTransferSavedData.get(earth.getServer());
            journal.remove(transfer);
            journal.flush(earth.getServer());
            removeAll(earth, joined);
        };
        try {
            RocketEntity source = RocketPassengerPersistenceGameTests.createRocket(helper, passenger.getUUID());
            logical[0] = source.assemblyTransactionId().orElseThrow();
            var initial = source.flightData().orElseThrow();
            source.updateFlightData(initial.withFuel(initial.fuel().fill(1000).state(), earth.getGameTime())
                    .withPassengers(initial.passengers().assign(passenger.getUUID()).orElseThrow()));
            helper.assertTrue(passenger.startRiding(source, true), "The passenger could not board");
            var launched = RocketRuntime.requestAdminFlight(source, RocketDestination.MOON, transfer);
            RocketFlightGameTestFixtures.assertPendingLaunch(helper, launched, source);
            helper.assertTrue(!done(passenger, Milestone.MOON_LANDING), "The launch fired the landing event");
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
        RocketFlightGameTestFixtures.awaitPaced(helper, driver, helper.getTick() + 400,
                () -> passenger.serverLevel() == moon && passenger.getVehicle() instanceof RocketEntity
                        && RocketFlightGameTestFixtures.hasFlightState(moon, logical[0], RocketFlightState.DESCENT),
                () -> {
            try {
                helper.assertTrue(passenger.serverLevel() == moon, "The passenger never reached the Moon descent");
                helper.assertTrue(done(passenger, Milestone.MOON_SURFACE_VISIT), "Arrival did not earn the surface visit");
                helper.assertTrue(!done(passenger, Milestone.MOON_LANDING), "The descent fired the landing event early");
            } catch (RuntimeException failure) {
                cleanup.run();
                throw failure;
            }
            RocketFlightGameTestFixtures.awaitFlightState(helper, driver, moon, logical[0], RocketFlightState.LANDED, 300, () -> {
                try {
                    RocketEntity landed = RocketFlightGameTestFixtures.loadedLogicalRocket(moon, logical[0]);
                    helper.assertTrue(landed != null && landed.flightData().orElseThrow().state()
                            == RocketFlightState.LANDED, "The rocket did not land");
                    helper.assertTrue(done(passenger, Milestone.MOON_LANDING), "The landed passenger missed the event");
                    helper.assertTrue(!done(moonWatcher, Milestone.MOON_LANDING)
                            && !done(earthWatcher, Milestone.MOON_LANDING), "A player not aboard earned the landing");
                    helper.assertTrue(!done(passenger, Milestone.ONE_SMALL_STEP)
                                    && !done(moonWatcher, Milestone.ONE_SMALL_STEP),
                            "A landing that is not the world's first earned the world-first milestone");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "classic_station_warp", timeoutTicks = 400)
    public static void stationWarpRewardsPresentVisitorsAtTheCommittedWarp(GameTestHelper helper) {
        // Exactly the in-system cost: the commit empties the balance, so deleting the fixture drops no energy.
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Classic warp",
                CelestialIds.EARTH_ID, 2_000_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<ServerPlayer> extra = new ArrayList<>();
        boolean scheduled = false;
        try {
            fixture.core(fixture.pad.east(2));
            fixture.join(fixture.ownerId, "classicWarpOwner");
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            fixture.join(memberId, "classicWarpMember");
            UUID outsiderId = UUID.randomUUID();
            fixture.join(outsiderId, "classicWarpOutsider");
            UUID remoteId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), remoteId);
            fixture.data.acceptInvitation(fixture.id(), remoteId);
            extra.add(ConnectedTestPlayers.join(fixture.server, remoteId, "classicWarpRemote", fixture.space,
                    fixture.pad.east(StationLimits.REGION_SIZE / 2 + 32), new ArrayList<>()));
            ServerPlayer owner = player(fixture.server, fixture.ownerId);
            ServerPlayer member = player(fixture.server, memberId);
            ServerPlayer outsider = player(fixture.server, outsiderId);
            ServerPlayer remote = player(fixture.server, remoteId);
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            // W44: the world's single first-warp slot is shared by every GameTest warp, so this test checks the state it
            // observes explicitly; the isolated positives and negatives are the A1 batch.
            boolean slotOpen = fixture.data.firstWarpRam().isEmpty();
            fixture.run(fixture.ownerId, "arce station warp " + StationWarpGameTests.MOON);
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            helper.assertTrue(!done(owner, Milestone.STATION_WARP) && !done(member, Milestone.STATION_WARP),
                    "The request or confirmation fired the warp event");
            helper.assertTrue(!done(owner, Milestone.FIRST_STATION_WARP) && !done(member, Milestone.FIRST_STATION_WARP),
                    "The request or confirmation fired the world-first milestone");
            scheduled = true;
            helper.runAfterDelay(StationWarpGameTests.COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(fixture.station().orbitBody().equals(StationWarpGameTests.MOON)
                            && fixture.data.warpEnergy(fixture.id()) == 0, "The warp did not commit");
                    helper.assertTrue(done(owner, Milestone.STATION_WARP) && done(member, Milestone.STATION_WARP),
                            "A present visitor missed the committed warp");
                    helper.assertTrue(!done(outsider, Milestone.STATION_WARP),
                            "A player without VISIT access earned the warp");
                    helper.assertTrue(!done(remote, Milestone.STATION_WARP),
                            "A member outside the station region earned the warp");
                    var slot = fixture.data.firstWarpRam().event();
                    boolean elected = slotOpen && slot instanceof io.github.sunthemoon.advancedrocketrycommunity
                            .progression.classic.firstevent.FirstEvent.StationWarp warp
                            && warp.stationId().equals(fixture.id());
                    helper.assertTrue(!done(outsider, Milestone.FIRST_STATION_WARP)
                                    && !done(remote, Milestone.FIRST_STATION_WARP),
                            "A player without VISIT access or outside the region earned the world-first");
                    helper.assertTrue(elected == (done(owner, Milestone.FIRST_STATION_WARP)
                                    && done(member, Milestone.FIRST_STATION_WARP)),
                            elected ? "The present participants of the world-first warp were not awarded"
                                    : "A warp that is not the world's first earned the world-first milestone");
                } finally {
                    extra.forEach(fixture.server.getPlayerList()::remove);
                    fixture.warp.installRocketAuthority(previous);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                extra.forEach(fixture.server.getPlayerList()::remove);
                fixture.warp.installRocketAuthority(previous);
                fixture.close();
            }
        }
    }

    /** The registered lathe pattern placed facing north through ordinary block updates. */
    private static List<BlockPos> placeLathe(ServerLevel level, BlockPos origin) {
        MultiblockPatternDefinition pattern = MultiblockPatternJsonCodec.decode(
                V180ClassicMachineData.lathePattern().toString().getBytes(StandardCharsets.UTF_8));
        PatternTransform transform = new PatternTransform(PatternRotation.ZERO, false);
        PatternPosition controller = new PatternPosition(origin.getX(), origin.getY(), origin.getZ());
        List<BlockPos> placed = new ArrayList<>();
        for (var entry : pattern.cells().entrySet()) {
            PatternPosition world = transform.localToWorld(entry.getKey(), pattern.controllerAnchor(), controller);
            BlockPos position = new BlockPos(world.x(), world.y(), world.z());
            BlockState state;
            if (entry.getValue() instanceof PatternMatcher.Controller) {
                state = ModBlocks.LATHE.get().defaultBlockState().setValue(ClassicControllerBlock.FACING, Direction.NORTH);
            } else if (entry.getValue() instanceof PatternMatcher.BlockTag) {
                state = MotorContent.block(MotorDefinition.MOTOR).get().defaultBlockState();
            } else if (entry.getValue() instanceof PatternMatcher.ExactBlock) {
                state = ModBlocks.MACHINE_CASING.get().defaultBlockState();
            } else if (entry.getValue() instanceof PatternMatcher.Port port) {
                state = switch (port.channel()) {
                    case "item_input" -> ModBlocks.ITEM_INPUT_HATCH.get().defaultBlockState();
                    case "item_output" -> ModBlocks.ITEM_OUTPUT_HATCH.get().defaultBlockState();
                    case "energy_input" -> ModBlocks.POWER_INPUT_PLUG.get().defaultBlockState();
                    default -> throw new IllegalArgumentException("Unexpected lathe port " + port.channel());
                };
            } else {
                state = Blocks.AIR.defaultBlockState();
            }
            level.setBlock(position, state, Block.UPDATE_ALL);
            placed.add(position);
        }
        return placed;
    }

    /** Vanilla ignores use packets until the client confirms the last teleport, so confirm it as a client would. */
    private static void acknowledgeTeleport(ServerPlayer player) {
        EmbeddedChannel channel = (EmbeddedChannel) player.connection.connection.channel();
        Integer id = null;
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ClientboundPlayerPositionPacket position) {
                id = position.getId();
            }
        }
        if (id == null) {
            throw new IllegalStateException("No teleport to acknowledge");
        }
        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id));
    }

    /** A client use packet through the server's own validation (reach, teleport, spawn protection). */
    private static void use(ServerPlayer player, BlockPos target) {
        player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(target).add(0.0D, 0.5D, 0.0D), Direction.UP, target, false), 0));
    }

    private static ServerPlayer join(GameTestHelper helper, ServerLevel level, BlockPos position, String name,
                                     List<ServerPlayer> joined) {
        ServerPlayer player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), name, level, position,
                new ArrayList<>());
        joined.add(player);
        return player;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            throw new IllegalStateException("Fixture player is not online");
        }
        return player;
    }

    private static Advancement advancement(MinecraftServer server, String id) {
        Advancement advancement = server.getAdvancements().getAdvancement(new ResourceLocation(id));
        if (advancement == null || advancement.getDisplay() == null) {
            throw new IllegalStateException("Missing visible generated advancement " + id);
        }
        return advancement;
    }

    private static boolean done(ServerPlayer player, Milestone milestone) {
        return progress(player, milestone).isDone();
    }

    private static AdvancementProgress progress(ServerPlayer player, Milestone milestone) {
        return player.getAdvancements().getOrStartProgress(advancement(player.getServer(), milestone.id()));
    }

    private static long obtained(ServerPlayer player, Milestone milestone) {
        Date date = progress(player, milestone).getCriterion(milestone.criterion()).getObtained();
        if (date == null) {
            throw new IllegalStateException("Missing criterion timestamp for " + milestone);
        }
        return date.getTime();
    }

    /** Runs one step of a multi-tick test; a failing step disconnects the fixture players before it fails. */
    private static void guarded(ServerLevel level, List<ServerPlayer> joined, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            removeAll(level, joined);
            throw failure;
        }
    }

    private static void removeAll(ServerLevel level, List<ServerPlayer> joined) {
        joined.forEach(player -> level.getServer().getPlayerList().remove(player));
        joined.clear();
    }
}
