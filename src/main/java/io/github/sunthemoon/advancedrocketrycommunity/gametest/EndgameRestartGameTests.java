package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.SingularityContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillMode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTable;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserShaft;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Census F9-F11: current-format binary NBT, fresh BEs, reopened service root and real server ticks. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndgameRestartGameTests {
    private EndgameRestartGameTests() { }

    @BeforeBatch(batch = "w417_physical_restart")
    public static void enablePhysicalRestart(ServerLevel level) {
        SwitchOverrides.set(CommonConfig.ENDGAME_LASER_PHYSICAL, true);
    }

    @AfterBatch(batch = "w417_physical_restart")
    public static void restorePhysicalSwitch(ServerLevel level) {
        SwitchOverrides.clear(CommonConfig.ENDGAME_LASER_PHYSICAL);
    }

    /** F9: a real logical draw precedes the restart; only the next seeded draw may be charged and delivered. */
    @GameTest(template = "empty", batch = "w417_logical_restart", timeoutTicks = 400)
    public static void f9LogicalJobRestoresSeedIndexEnergyAndExactNextOutput(GameTestHelper helper) {
        var fixture = new OrbitalLaserDrillGameTests.Fixture(helper, CelestialIds.EARTH_ID);
        EndgameRestartFixtures.cleanup(helper, fixture::close);
        OrbitalLaserDrillBlockEntity[] drill = {fixture.drill()};
        drill[0].storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill[0].storage().setEnergy(200_000);
        // A fixed pre-start seed makes consecutive draws distinguishable, rather than both being cobblestone.
        CompoundTag prepared = drill[0].saveWithoutMetadata();
        prepared.getCompound("endgame").putLong("seed", 1L);
        drill[0].load(prepared);
        long seed = drill[0].seed();
        helper.assertTrue(!OrbitalLaserDrillGameTests.draw(CelestialIds.EARTH_ID, seed, 0).item()
                        .equals(OrbitalLaserDrillGameTests.draw(CelestialIds.EARTH_ID, seed, 1).item()),
                "The logical restart fixture needs distinct consecutive draws to detect replay");
        ItemStackHandler expected = new ItemStackHandler(18);
        addDraw(helper, expected, OrbitalLaserDrillGameTests.draw(CelestialIds.EARTH_ID, seed, 0));
        helper.startSequence()
                .thenWaitUntil(() -> fixture.ready(helper))
                .thenExecute(() -> fixture.startAsOwner(helper))
                .thenWaitUntil(() -> helper.assertTrue(drill[0].operationIndex() >= 1, "No pre-restart logical draw"))
                .thenExecute(() -> {
                    assertLogical(helper, drill[0], seed, 1, 190_000, expected);
                    drill[0] = (OrbitalLaserDrillBlockEntity) EndgameRestartFixtures.restart(helper, drill[0])[0];
                    assertLogical(helper, drill[0], seed, 1, 190_000, expected);
                    addDraw(helper, expected, OrbitalLaserDrillGameTests.draw(CelestialIds.EARTH_ID, seed, 1));
                })
                .thenWaitUntil(() -> helper.assertTrue(drill[0].operationIndex() >= 2,
                        "No post-restart logical draw: " + drill[0].describe()))
                .thenExecute(() -> {
                    assertLogical(helper, drill[0], seed, 2, 180_000, expected);
                    fixture.close();
                })
                .thenSucceed();
    }

    /** F9 + F10: reopen a paid physical job and its ACTIVE endpoint; keep the exact cursor, link and buffer. */
    @GameTest(template = "atmosphere_test", batch = "w417_physical_restart", timeoutTicks = 1200)
    public static void f9AndF10PhysicalJobAndActiveTargetResumeExactlyTheNextLayer(GameTestHelper helper) {
        var fixture = new OrbitalLaserDrillGameTests.Fixture(helper, CelestialIds.EARTH_ID);
        LaserPhysicalCleanup cleanup = new LaserPhysicalCleanup(helper, fixture);
        OrbitalLaserDrillBlockEntity[] drill = {fixture.drill()};
        drill[0].storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill[0].storage().setEnergy(200_000);
        long seed = drill[0].seed();
        ServerLevel ground = helper.getLevel();
        BlockPos marker = markerPosition(helper);
        cleanup.target(ground, marker);
        ground.setBlockAndUpdate(marker, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity[] target = {(LaserTargetBlockEntity) ground.getBlockEntity(marker)};
        helper.assertTrue(target[0].assignOwner(fixture.owner), "The marker owner was not assigned");
        List<BlockPos> first = LaserShaft.layer(marker, marker.getY() - 1);
        List<BlockPos> second = LaserShaft.layer(marker, marker.getY() - 2);
        List<BlockPos> floor = LaserShaft.layer(marker, marker.getY() - 3);
        cleanup.layer(first);
        cleanup.layer(second);
        cleanup.layer(floor);
        first.forEach(pos -> ground.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
        second.forEach(pos -> ground.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState()));
        floor.forEach(pos -> ground.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState()));
        ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "restartLaser", fixture.space,
                fixture.controller.south(4).above(2), new ArrayList<>());
        cleanup.joined.add(owner);
        OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(17, owner.getInventory(), drill[0]);
        CompoundTag[] savedDrill = new CompoundTag[1];
        CompoundTag[] savedTarget = new CompoundTag[1];
        EndpointRecord[] endpoint = new EndpointRecord[1];
        ItemStackHandler expectedBuffer = new ItemStackHandler(LaserTargetBlockEntity.BUFFER_SLOTS);
        expectedBuffer.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 9));
        helper.startSequence()
                .thenWaitUntil(() -> fixture.ready(helper))
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(ground, marker);
                    helper.assertTrue(target[0].endpointActive(), "The target did not register");
                })
                .thenExecute(() -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_MODE))
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT);
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_LINK);
                })
                .thenExecuteAfter(11, () -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_START))
                .thenExecuteAfter(11, () -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_CONFIRM))
                .thenWaitUntil(() -> helper.assertTrue(target[0].opsDone() >= 1,
                        "No pre-restart physical layer: " + drill[0].describe()))
                .thenExecute(() -> {
                    assertPhysical(helper, drill[0], target[0], seed, 1, marker.getY() - 2, 190_000);
                    assertInventory(helper, target[0].buffer(), expectedBuffer, "pre-restart target");
                    savedDrill[0] = drill[0].saveWithoutMetadata().getCompound("endgame").copy();
                    savedTarget[0] = target[0].saveWithoutMetadata().getCompound("endgame").copy();
                    endpoint[0] = endpoint(target[0]);
                    helper.assertTrue(endpoint[0].state() == EndpointRecord.State.ACTIVE, "The record was not ACTIVE");
                    // A replacement in the completed layer must survive: a replay would mine these gold blocks.
                    first.forEach(pos -> ground.setBlockAndUpdate(pos, Blocks.GOLD_BLOCK.defaultBlockState()));
                    var restored = EndgameRestartFixtures.restart(helper, drill[0], target[0]);
                    drill[0] = (OrbitalLaserDrillBlockEntity) restored[0];
                    target[0] = (LaserTargetBlockEntity) restored[1];
                    helper.assertTrue(endpoint(target[0]).equals(endpoint[0]), "The reopened endpoint record changed");
                    assertPhysical(helper, drill[0], target[0], seed, 1, marker.getY() - 2, 190_000);
                    assertInventory(helper, target[0].buffer(), expectedBuffer, "restored target");
                })
                .thenWaitUntil(() -> helper.assertTrue(target[0].endpointActive() && target[0].opsDone() >= 2,
                        "The ACTIVE restored target did not continue: " + drill[0].describe()
                                + " / " + target[0].describe()))
                .thenExecute(() -> {
                    assertPhysical(helper, drill[0], target[0], seed, 2, marker.getY() - 3, 180_000);
                    helper.assertTrue(endpoint(target[0]).equals(endpoint[0]), "Continuation changed the live record");
                    CompoundTag nextDrill = savedDrill[0].copy();
                    nextDrill.getCompound("link").putLong("ops_paid", 2L);
                    nextDrill.putInt("energy", 180_000);
                    helper.assertTrue(nextDrill.equals(drill[0].saveWithoutMetadata().getCompound("endgame")),
                            "The controller changed fields other than its exact payment and energy");
                })
                .thenExecute(() -> {
                    helper.assertTrue(ItemHandlerHelper.insertItemStacked(expectedBuffer,
                            new ItemStack(Items.DIRT, 9), false).isEmpty(), "The expected second layer did not fit");
                    assertInventory(helper, target[0].buffer(), expectedBuffer, "continued target");
                    CompoundTag nextTarget = savedTarget[0].copy();
                    nextTarget.putInt("next_layer", marker.getY() - 3);
                    nextTarget.getCompound("link").putLong("ops_done", 2L);
                    nextTarget.put("buffer", expectedBuffer.serializeNBT());
                    helper.assertTrue(nextTarget.equals(target[0].saveWithoutMetadata().getCompound("endgame")),
                            "The target lost its exact link, generation, cursor or buffer");
                    helper.assertTrue(first.stream().allMatch(pos -> ground.getBlockState(pos).is(Blocks.GOLD_BLOCK))
                                    && second.stream().allMatch(pos -> ground.getBlockState(pos).isAir())
                                    && floor.stream().allMatch(pos -> ground.getBlockState(pos).is(Blocks.BEDROCK)),
                            "Continuation repeated an old layer or changed more than the next layer");
                    cleanup.close();
                })
                .thenSucceed();
    }

    /** F11: preserve a genuinely consumed item's remaining burn, fuel inventory, fixed rate and FE, then burn on. */
    @GameTest(template = "empty", batch = "w417_black_hole_mid_burn", timeoutTicks = 1200)
    public static void f11MidBurnRestartKeepsExactFuelCreditRateAndEnergy(GameTestHelper helper) {
        blackHoleRestart(helper, false);
    }

    /** F11: reopening a full generator must pause the saved burn and resume only as buffer space becomes free. */
    @GameTest(template = "empty", batch = "w417_black_hole_full_buffer", timeoutTicks = 1200)
    public static void f11FullBufferRestartPausesAndContinuesWithoutLosingBurnCredit(GameTestHelper helper) {
        blackHoleRestart(helper, true);
    }

    private static void blackHoleRestart(GameTestHelper helper, boolean full) {
        var fixture = new OrbitalLaserDrillGameTests.Fixture(helper, SingularityContent.CYGNUS_X1);
        EndgameRestartFixtures.cleanup(helper, () -> EndgameRestartFixtures.closeGenerator(fixture));
        BlackHoleGeneratorBlockEntity[] generator = {EndgameRestartFixtures.generator(fixture)};
        generator[0].fuel().setStackInSlot(0, new ItemStack(Items.STICK, 4));
        generator[0].fuel().setStackInSlot(8, new ItemStack(Items.DIRT, 7));
        GameTestTickPacer pacer = new GameTestTickPacer(fixture.server);
        CompoundTag[] saved = new CompoundTag[1];
        int[] remaining = new int[1];
        int[] energy = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    pacer.pace();
                    helper.assertTrue(generator[0].status() == EndgameCode.GENERATING && generator[0].energy() >= 5_000,
                            "No real pre-restart burn: " + generator[0].describe());
                })
                .thenExecute(() -> {
                    helper.assertTrue(generator[0].fuel().getStackInSlot(0).is(Items.STICK)
                                    && generator[0].fuel().getStackInSlot(0).getCount() == 3
                                    && generator[0].remaining() > 10 && generator[0].remaining() < 500
                                    && generator[0].rate() == 500
                                    && generator[0].energy() == (500 - generator[0].remaining()) * 500,
                            "The fixture has not consumed exactly one 500-tick item");
                    if (full) {
                        generator[0].setEnergyForTest(BlackHoleGeneratorBlockEntity.ENERGY_CAPACITY);
                    }
                    saved[0] = generator[0].saveWithoutMetadata().getCompound("endgame").copy();
                    remaining[0] = generator[0].remaining();
                    energy[0] = generator[0].energy();
                    generator[0] = (BlackHoleGeneratorBlockEntity) EndgameRestartFixtures.restart(helper,
                            generator[0])[0];
                    assertBurn(helper, generator[0], saved[0], remaining[0], energy[0]);
                })
                .thenExecuteAfter(full ? 20 : 0, () -> {
                    if (full) {
                        assertBurn(helper, generator[0], saved[0], remaining[0], energy[0]);
                        helper.assertTrue(generator[0].status() == EndgameCode.PAUSED_FULL,
                                "The restored full generator did not pause");
                        var output = generator[0].getCapability(ForgeCapabilities.ENERGY)
                                .orElseThrow(IllegalStateException::new);
                        helper.assertTrue(output.extractEnergy(5_000, false) == 5_000,
                                "The restored generator could not release buffered FE");
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(generator[0].remaining() <= remaining[0] - 10,
                        "The restored burn did not continue: " + generator[0].describe()))
                .thenExecute(() -> assertBurn(helper, generator[0], saved[0], remaining[0] - 10,
                        full ? energy[0] : energy[0] + 5_000))
                .thenExecuteAfter(full ? 20 : 0, () -> {
                    if (full) {
                        assertBurn(helper, generator[0], saved[0], remaining[0] - 10, energy[0]);
                        helper.assertTrue(generator[0].status() == EndgameCode.PAUSED_FULL,
                                "A refilled generator did not pause again");
                    }
                    EndgameRestartFixtures.closeGenerator(fixture);
                })
                .thenSucceed();
    }

    private static void assertBurn(GameTestHelper helper, BlackHoleGeneratorBlockEntity generator,
                                   CompoundTag saved, int remaining, int energy) {
        CompoundTag expected = saved.copy();
        expected.putInt("remaining_ticks", remaining);
        expected.putInt("energy", energy);
        helper.assertTrue(generator.remaining() == remaining && generator.energy() == energy && generator.rate() == 500
                        && !generator.quarantined()
                        && expected.equals(generator.saveWithoutMetadata().getCompound("endgame")),
                "The exact fuel, burn credit, rate, FE or identity changed: " + generator.describe());
    }

    private static void assertLogical(GameTestHelper helper, OrbitalLaserDrillBlockEntity drill, long seed,
                                      long index, int energy, ItemStackHandler expected) {
        helper.assertTrue(!drill.quarantined() && drill.running() && drill.mode() == LaserDrillMode.LOGICAL
                        && drill.seed() == seed && drill.operationIndex() == index && drill.storage().energy() == energy
                        && drill.linkedMarker().isEmpty() && drill.storage().lens().getStackInSlot(0).is(ModItems.LASER_LENS.get())
                        && drill.storage().lens().getStackInSlot(0).getCount() == 1,
                "The logical restart changed its job or payment: " + drill.describe());
        assertInventory(helper, drill.storage().output(), expected, "logical output");
    }

    private static void assertPhysical(GameTestHelper helper, OrbitalLaserDrillBlockEntity drill,
                                       LaserTargetBlockEntity target, long seed, long operations, int layer, int energy) {
        helper.assertTrue(!drill.quarantined() && !target.quarantined() && !target.frozen() && drill.running()
                        && drill.mode() == LaserDrillMode.PHYSICAL && drill.seed() == seed && drill.operationIndex() == 0
                        && drill.opsPaid() == operations && target.opsDone() == operations && target.nextLayer() == layer
                        && drill.storage().energy() == energy
                        && drill.linkedMarker().equals(target.deviceId())
                        && target.linkedController().equals(drill.deviceId()),
                "The physical restart changed payment, cursor or link: " + drill.describe() + " / " + target.describe());
    }

    private static void assertInventory(GameTestHelper helper, ItemStackHandler actual, ItemStackHandler expected,
                                        String context) {
        helper.assertTrue(actual.serializeNBT().equals(expected.serializeNBT()), "Unexpected exact " + context
                + ": expected=" + expected.serializeNBT() + " actual=" + actual.serializeNBT());
    }

    private static void addDraw(GameTestHelper helper, ItemStackHandler output, LaserDrillTable.Entry entry) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(output,
                new ItemStack(ForgeRegistries.ITEMS.getValue(entry.item()), entry.count()), false);
        helper.assertTrue(remainder.isEmpty(), "The expected logical output did not fit");
    }

    private static EndpointRecord endpoint(LaserTargetBlockEntity target) {
        return EndgameRuntime.operational().orElseThrow().root().orElseThrow()
                .endpoint(target.deviceId().orElseThrow()).orElseThrow();
    }

    private static void click(GameTestHelper helper, OrbitalLaserDrillMenu menu, ServerPlayer owner, int button) {
        helper.assertTrue(menu.clickMenuButton(owner, button), "The physical setup intent was refused: " + button);
    }

    private static BlockPos markerPosition(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int x = 2;
        int z = 2;
        while (((base.getX() + x) & 15) < LaserShaft.MIN_LOCAL
                || ((base.getX() + x) & 15) > LaserShaft.MAX_LOCAL) { x++; }
        while (((base.getZ() + z) & 15) < LaserShaft.MIN_LOCAL
                || ((base.getZ() + z) & 15) > LaserShaft.MAX_LOCAL) { z++; }
        return helper.absolutePos(new BlockPos(x, 6, z));
    }
}
