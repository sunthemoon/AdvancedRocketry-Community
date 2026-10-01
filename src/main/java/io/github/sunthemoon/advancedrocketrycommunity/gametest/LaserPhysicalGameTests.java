package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserShaft;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-055 section 3 on a running server: a drill on an Earth station digs at a laser target in the Overworld. Every
 * stop (immune block, block entity, fluid, full buffer, zone, API veto, break-event veto, the switch) leaves the
 * layer untouched and takes no payment; each layer is paid once, and a marker reset loses the link.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserPhysicalGameTests {
    private static final String BATCH = "endgame_laser_physical";
    private static final String ATTACHED_BATCH = "endgame_laser_physical_attached";
    private static final AtomicInteger BREAK_EVENTS = new AtomicInteger();
    private static volatile UUID effectVeto;
    private static volatile UUID breakVeto;
    private static volatile UUID countBreaksFor;

    static {
        MinecraftForge.EVENT_BUS.addListener(LaserPhysicalGameTests::onEffect);
        MinecraftForge.EVENT_BUS.addListener(LaserPhysicalGameTests::onBreak);
    }

    private LaserPhysicalGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void enablePhysicalMining(ServerLevel level) {
        CommonConfig.ENDGAME_LASER_PHYSICAL.set(true);
    }

    @AfterBatch(batch = BATCH)
    public static void disablePhysicalMining(ServerLevel level) {
        CommonConfig.ENDGAME_LASER_PHYSICAL.set(false);
    }

    @BeforeBatch(batch = ATTACHED_BATCH)
    public static void enablePhysicalMiningForAttachedBlocks(ServerLevel level) {
        CommonConfig.ENDGAME_LASER_PHYSICAL.set(true);
    }

    @AfterBatch(batch = ATTACHED_BATCH)
    public static void disablePhysicalMiningForAttachedBlocks(ServerLevel level) {
        CommonConfig.ENDGAME_LASER_PHYSICAL.set(false);
    }

    private static void onEffect(EndgameEffectEvent event) {
        if (event.ownerId().equals(effectVeto)) {
            event.setCanceled(true);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof FakePlayer fake && fake.getUUID().equals(breakVeto)) {
            event.setCanceled(true);
        }
        if (event.getPlayer() instanceof FakePlayer fake && fake.getUUID().equals(countBreaksFor)) {
            BREAK_EVENTS.incrementAndGet();
        }
    }

    @GameTest(template = "atmosphere_test", batch = BATCH, timeoutTicks = 2400)
    public static void aPhysicalShaftDigsWholeLayersPaysOnceAndEveryStopLeavesTheLayer(GameTestHelper helper) {
        OrbitalLaserDrillGameTests.Fixture fixture = new OrbitalLaserDrillGameTests.Fixture(helper,
                io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().setEnergy(200_000);
        ServerLevel overworld = helper.getLevel();
        BlockPos marker = markerPosition(helper);
        overworld.setBlockAndUpdate(marker, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) overworld.getBlockEntity(marker);
        helper.assertTrue(target.assignOwner(fixture.owner), "The marker owner was not assigned");
        UUID markerId = target.deviceId().orElseThrow();
        List<BlockPos> layer1 = LaserShaft.layer(marker, marker.getY() - 1);
        List<BlockPos> layer2 = LaserShaft.layer(marker, marker.getY() - 2);
        fill(overworld, layer1, Blocks.STONE.defaultBlockState());
        fill(overworld, layer2, Blocks.STONE.defaultBlockState());
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "physicalOwner", fixture.space,
                fixture.controller.south(4).above(2), new ArrayList<>());
        joined.add(owner);
        OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(7, owner.getInventory(), drill);
        String zone = "gt_laser_" + markerId.toString().substring(0, 8);
        int[] chunks = new int[2];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(overworld, marker);
                    helper.assertTrue(target.endpointActive(), "The marker did not register: " + target.describe());
                })
                .thenExecute(() -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_MODE, "mode"))
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT, "select");
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_LINK, "link");
                    helper.assertTrue(drill.linkedMarker().filter(markerId::equals).isPresent(), "Not linked");
                })
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_START, "start");
                    helper.assertTrue(!drill.running() && drill.confirmationPendingFor(fixture.owner),
                            "A physical start ran without a confirmation");
                })
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_CONFIRM, "confirm");
                    helper.assertTrue(drill.running(), "The confirmation did not start the drill");
                })
                .thenWaitUntil(() -> helper.assertTrue(target.opsDone() == 1, "No first layer: " + drill.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(layer1.stream().allMatch(cell -> overworld.getBlockState(cell).isAir()),
                            "The first layer is not cleared");
                    helper.assertTrue(count(target, Items.COBBLESTONE) == 9, "The drops are not in the marker");
                    helper.assertTrue(drill.opsPaid() == 1 && drill.storage().energy() == 190_000,
                            "The first layer was not paid exactly once");
                    chunks[0] = overworld.getChunkSource().getLoadedChunksCount();
                    chunks[1] = fixture.space.getChunkSource().getLoadedChunksCount();
                    overworld.setBlockAndUpdate(layer2.get(4), Blocks.BEDROCK.defaultBlockState());
                })
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.BLOCKED_IMMUNE))
                .thenExecute(() -> overworld.setBlockAndUpdate(layer2.get(4), Blocks.WATER.defaultBlockState()))
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.BLOCKED_FLUID))
                .thenExecute(() -> overworld.setBlockAndUpdate(layer2.get(4), Blocks.CHEST.defaultBlockState()))
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.BLOCKED_IMMUNE))
                .thenExecute(() -> {
                    overworld.setBlockAndUpdate(layer2.get(4), Blocks.STONE.defaultBlockState());
                    for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
                        target.buffer().setStackInSlot(slot, new ItemStack(Items.WOODEN_SWORD));
                    }
                })
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.TARGET_BUFFER_FULL))
                .thenExecute(() -> {
                    for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
                        target.buffer().setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    target.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 9));
                    ProtectedZone box = ProtectedZone.of(zone, overworld.dimension().location(), marker.getX() - 1,
                            marker.getZ() - 1, marker.getX() + 1, marker.getZ() + 1, List.of());
                    helper.assertTrue(service().barrier(root -> root.addZone(box, 256)) == EndgameCode.OK,
                            "The zone was not added");
                })
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.TARGET_PROTECTED))
                .thenExecute(() -> {
                    service().barrier(root -> root.removeZone(zone));
                    effectVeto = fixture.owner;
                })
                .thenExecuteAfter(25, () -> expectStop(helper, drill, target, layer2, EndgameCode.TARGET_PROTECTED))
                .thenExecute(() -> {
                    effectVeto = null;
                    breakVeto = fixture.owner;
                })
                .thenExecuteAfter(25, () -> expectStop(helper, drill, target, layer2, EndgameCode.TARGET_PROTECTED))
                .thenExecute(() -> {
                    breakVeto = null;
                    CommonConfig.ENDGAME_LASER_PHYSICAL.set(false);
                })
                .thenWaitUntil(() -> expectStop(helper, drill, target, layer2, EndgameCode.PHYSICAL_DISABLED))
                .thenExecute(() -> CommonConfig.ENDGAME_LASER_PHYSICAL.set(true))
                .thenWaitUntil(() -> helper.assertTrue(target.opsDone() == 2, "No second layer: " + drill.describe()))
                .thenExecute(() -> {
                    helper.assertTrue(layer2.stream().allMatch(cell -> overworld.getBlockState(cell).isAir()),
                            "The second layer is not cleared");
                    helper.assertTrue(drill.opsPaid() == 2 && drill.storage().energy() == 180_000,
                            "Stops took payments or a layer was paid twice: " + drill.describe());
                    helper.assertTrue(target.nextLayer() == marker.getY() - 3, "The cursor did not move down");
                    helper.assertTrue(overworld.getChunkSource().getLoadedChunksCount() <= chunks[0]
                                    && fixture.space.getChunkSource().getLoadedChunksCount() <= chunks[1],
                            "A layer loaded a chunk");
                    // The marker's owner resets it: the drill loses the link and may then unlink.
                    owner.teleportTo(overworld, marker.getX() + 0.5D, marker.getY() + 1, marker.getZ() + 2.5D, 0.0F,
                            0.0F);
                    LaserTargetMenu markerMenu = new LaserTargetMenu(8, owner.getInventory(), target);
                    click(helper, markerMenu, owner, LaserTargetMenu.BUTTON_RESET, "marker reset");
                    helper.assertTrue(target.linkedController().isEmpty(), "The marker kept its link");
                    owner.teleportTo(fixture.space, fixture.controller.getX() + 0.5D, fixture.controller.getY() + 2,
                            fixture.controller.getZ() + 4.5D, 0.0F, 0.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(drill.status() == EndgameCode.LINK_LOST,
                        "The drill did not lose its link: " + drill.describe()))
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_UNLINK, "unlink");
                    helper.assertTrue(drill.linkedMarker().isEmpty() && !drill.running(), "Unlink failed");
                    cleanup(fixture, joined, overworld, marker, layer1, layer2, target);
                })
                .thenSucceed();
    }

    /**
     * Review C11R-H1: a wall torch, a ladder and a button that hang on other cells of the same layer reach the marker
     * once each and never also drop into the world.
     */
    @GameTest(template = "atmosphere_test", batch = ATTACHED_BATCH, timeoutTicks = 1200)
    public static void blocksHangingOnTheirLayerAreCollectedOnce(GameTestHelper helper) {
        OrbitalLaserDrillGameTests.Fixture fixture = new OrbitalLaserDrillGameTests.Fixture(helper,
                io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().setEnergy(200_000);
        ServerLevel overworld = helper.getLevel();
        BlockPos marker = markerPosition(helper);
        overworld.setBlockAndUpdate(marker, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) overworld.getBlockEntity(marker);
        helper.assertTrue(target.assignOwner(fixture.owner), "The marker owner was not assigned");
        List<BlockPos> layer1 = LaserShaft.layer(marker, marker.getY() - 1);
        List<BlockPos> layer2 = LaserShaft.layer(marker, marker.getY() - 2);
        fill(overworld, layer2, Blocks.STONE.defaultBlockState());
        fill(overworld, layer1, Blocks.STONE.defaultBlockState());
        // Cells are x = mx - 1 + i % 3, z = mz - 1 + i / 3. Each hanging block's support is an earlier cell.
        overworld.setBlockAndUpdate(layer1.get(4), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.SOUTH));
        overworld.setBlockAndUpdate(layer1.get(5), Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.SOUTH));
        overworld.setBlockAndUpdate(layer1.get(8), Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.EAST));
        helper.assertTrue(overworld.getBlockState(layer1.get(4)).is(Blocks.WALL_TORCH)
                && overworld.getBlockState(layer1.get(5)).is(Blocks.LADDER)
                && overworld.getBlockState(layer1.get(8)).is(Blocks.STONE_BUTTON), "The hanging blocks were not placed");
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "attachedOwner", fixture.space,
                fixture.controller.south(4).above(2), new ArrayList<>());
        joined.add(owner);
        OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(9, owner.getInventory(), drill);
        AABB around = new AABB(marker).inflate(4.0D);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    LaserTargetGameTests.chunkSaved(overworld, marker);
                    helper.assertTrue(target.endpointActive(), "The marker did not register: " + target.describe());
                })
                .thenExecute(() -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_MODE, "mode"))
                .thenExecuteAfter(11, () -> {
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_TARGET_NEXT, "select");
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_LINK, "link");
                })
                .thenExecuteAfter(11, () -> click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_START, "start"))
                .thenExecuteAfter(11, () -> {
                    BREAK_EVENTS.set(0);
                    countBreaksFor = fixture.owner;
                    click(helper, menu, owner, OrbitalLaserDrillMenu.BUTTON_CONFIRM, "confirm");
                })
                .thenWaitUntil(() -> helper.assertTrue(target.opsDone() == 1, "No first layer: " + drill.describe()))
                .thenExecute(() -> {
                    countBreaksFor = null;
                    int breakEvents = BREAK_EVENTS.get();
                    int entities = overworld.getEntitiesOfClass(ItemEntity.class, around).stream()
                            .mapToInt(entity -> entity.getItem().getCount()).sum();
                    int torches = count(target, Items.TORCH);
                    int ladders = count(target, Items.LADDER);
                    int buttons = count(target, Items.STONE_BUTTON);
                    int cobblestone = count(target, Items.COBBLESTONE);
                    overworld.getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
                    cleanup(fixture, joined, overworld, marker, layer1, layer2, target);
                    helper.assertTrue(entities == 0, entities + " items dropped into the world");
                    // Review C11R-L1: one break event per breakable cell of the layer, not one per planning.
                    helper.assertTrue(breakEvents == 9, breakEvents + " break events for 9 cells");
                    helper.assertTrue(torches == 1 && ladders == 1 && buttons == 1 && cobblestone == 6,
                            "Hanging blocks were not collected once: torches=" + torches + " ladders=" + ladders
                                    + " buttons=" + buttons + " cobblestone=" + cobblestone);
                })
                .thenSucceed();
    }

    private static void expectStop(GameTestHelper helper, OrbitalLaserDrillBlockEntity drill,
                                   LaserTargetBlockEntity target, List<BlockPos> layer, EndgameCode code) {
        helper.assertTrue(drill.status() == code, "Expected " + code + ": " + drill.describe());
        helper.assertTrue(target.opsDone() == 1 && drill.opsPaid() == 1 && drill.storage().energy() == 190_000,
                "A stopped layer took a payment: " + drill.describe());
        long solid = layer.stream().filter(cell -> !drill.getLevel().getServer().overworld().getBlockState(cell).isAir())
                .count();
        helper.assertTrue(solid == 9, "A stopped layer was changed: " + solid + " solid cells");
    }

    private static void click(GameTestHelper helper, net.minecraft.world.inventory.AbstractContainerMenu menu,
                              ServerPlayer player, int button, String what) {
        helper.assertTrue(menu.clickMenuButton(player, button), "The " + what + " intent was refused");
    }

    /** A marker inside the template whose 3 x 3 footprint is at local x and z 1..14 of its chunk. */
    private static BlockPos markerPosition(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int x = 1;
        while (((base.getX() + x) & 15) < LaserShaft.MIN_LOCAL || ((base.getX() + x) & 15) > LaserShaft.MAX_LOCAL) {
            x++;
        }
        int z = 1;
        while (((base.getZ() + z) & 15) < LaserShaft.MIN_LOCAL || ((base.getZ() + z) & 15) > LaserShaft.MAX_LOCAL) {
            z++;
        }
        return helper.absolutePos(new BlockPos(x, 6, z));
    }

    private static void fill(ServerLevel level, List<BlockPos> cells, BlockState state) {
        cells.forEach(cell -> level.setBlockAndUpdate(cell, state));
    }

    private static int count(LaserTargetBlockEntity target, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
            ItemStack stack = target.buffer().getStackInSlot(slot);
            total += stack.is(item) ? stack.getCount() : 0;
        }
        return total;
    }

    private static EndgameService service() {
        return EndgameRuntime.operational().orElseThrow();
    }

    private static void cleanup(OrbitalLaserDrillGameTests.Fixture fixture, List<ServerPlayer> joined,
                                ServerLevel overworld, BlockPos marker, List<BlockPos> layer1, List<BlockPos> layer2,
                                LaserTargetBlockEntity target) {
        joined.forEach(player -> fixture.server.getPlayerList().remove(player));
        for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
            target.buffer().setStackInSlot(slot, ItemStack.EMPTY);
        }
        overworld.setBlockAndUpdate(marker, Blocks.AIR.defaultBlockState());
        fill(overworld, layer1, Blocks.AIR.defaultBlockState());
        fill(overworld, layer2, Blocks.AIR.defaultBlockState());
        fixture.close();
    }
}
