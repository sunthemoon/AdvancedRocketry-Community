package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerMenuGameTests {
    private static final BlockPos BREAK_CASING = new BlockPos(2, 2, 1);
    private static final List<BlockPos> INPUTS = List.of(
            PrecisionAssemblerGameTests.INPUT_0,
            PrecisionAssemblerGameTests.INPUT_1,
            new BlockPos(1, 1, 2),
            new BlockPos(3, 1, 2),
            new BlockPos(1, 1, 3)
    );
    private static final List<Item> INPUT_ITEMS = List.of(
            Items.IRON_INGOT, Items.REDSTONE, Items.GOLD_INGOT, Items.QUARTZ, Items.COPPER_INGOT
    );

    private PrecisionAssemblerMenuGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 35)
    public static void menuUsesAllBoundInputSlotsAndEnergyPort(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            Player player = nearbyViewer(helper);
            PrecisionAssemblerMenu menu = menu(helper, player);
            helper.assertTrue(menu.stillValid(player), "Nearby Precision Assembler menu was invalid");
            helper.assertTrue(menu.formationState() == MultiblockFormationState.FORMED,
                    "Menu did not show the formed structure");
            helper.assertTrue(menu.slots.size() == 43, "Menu did not expose seven machine and 36 player slots");

            for (int index = 0; index < INPUTS.size(); index++) {
                Item item = INPUT_ITEMS.get(index);
                player.getInventory().setItem(9 + index, new ItemStack(item));
                helper.assertTrue(menu.quickMoveStack(player,
                                PrecisionAssemblerMenu.MACHINE_SLOT_COUNT + index).is(item),
                        "Player input did not enter menu channel " + index);
                helper.assertTrue(menu.getSlot(index).getItem().is(item),
                        "Menu channel did not show the authoritative port " + index);
                helper.assertTrue(input(helper, index).getStackInSlot(0).getCount() == 1,
                        "Input port did not receive one item at channel " + index);
            }
            helper.assertTrue(!menu.getSlot(PrecisionAssemblerMenu.SLOT_OUTPUT_FIRST)
                            .mayPlace(new ItemStack(Items.IRON_INGOT)),
                    "Menu output slot accepted manual insertion");

            IEnergyStorage energy = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(500, false) == 500, "Energy fixture was rejected");
            helper.assertTrue(menu.energyStored() == 500 && menu.energyCapacity() == 20_000,
                    "Menu energy did not match the eighth physical port");
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.SLOT_INPUT_FIRST)
                            .is(Items.IRON_INGOT),
                    "Idle input could not be returned to the player");
            helper.assertTrue(input(helper, 0).getStackInSlot(0).isEmpty(),
                    "Menu extraction did not clear the authoritative input port");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void menuRejectsDistantViewerAndStaleControllerGeneration(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        AtomicReference<PrecisionAssemblerMenu> first = new AtomicReference<>();
        AtomicReference<Player> viewer = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            Player player = nearbyViewer(helper);
            first.set(menu(helper, player));
            viewer.set(player);
            player.setPos(player.getX() + 100.0D, player.getY(), player.getZ());
            helper.assertTrue(!first.get().stillValid(player), "Distant viewer retained menu access");
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).createMenu(
                    33, player.getInventory(), player) == null,
                    "Server constructed a menu for a distant viewer");
            positionNearby(helper, player);
            helper.setBlock(BREAK_CASING, Blocks.AIR);
        });
        helper.runAtTickTime(16, () -> {
            PrecisionAssemblerMenu diagnostics = menu(helper, viewer.get());
            helper.assertTrue(diagnostics.formationState() == MultiblockFormationState.UNFORMED,
                    "Broken structure was reported as formed");
            helper.assertTrue(diagnostics.diagnosticReason().orElse(null)
                            == PatternDiagnosticReason.BLOCK_MISMATCH,
                    "Menu did not expose the first structural mismatch");
            helper.assertTrue(diagnostics.diagnosticWorldPosition().orElseThrow()
                            .equals(helper.absolutePos(BREAK_CASING)),
                    "Menu pointed at the wrong world cell");
            helper.assertTrue(!diagnostics.getSlot(0).mayPlace(new ItemStack(Items.IRON_INGOT)),
                    "Unformed menu retained an Item write path");
            helper.setBlock(BREAK_CASING, ModBlocks.MACHINE_CASING.get());
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).generation() == 2L,
                    "Rebuild did not advance controller generation");
            Player player = viewer.get();
            PrecisionAssemblerMenu stale = first.get();
            helper.assertTrue(!stale.stillValid(player), "Old menu survived generation replacement");
            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(stale.quickMoveStack(player, PrecisionAssemblerMenu.MACHINE_SLOT_COUNT).isEmpty(),
                    "Stale menu accepted a quick transfer");
            helper.assertTrue(input(helper, 0).getStackInSlot(0).isEmpty(),
                    "Stale menu mutated the new generation's port");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 55)
    public static void activeMenuCannotBypassInputLockAndCanTakeBothOutputs(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        AtomicReference<PrecisionAssemblerMenu> opened = new AtomicReference<>();
        AtomicReference<Player> viewer = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            Player player = nearbyViewer(helper);
            PrecisionAssemblerMenu menu = menu(helper, player);
            opened.set(menu);
            viewer.set(player);
            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 2));
            player.getInventory().setItem(10, new ItemStack(Items.REDSTONE, 2));
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.MACHINE_SLOT_COUNT)
                            .getCount() == 2,
                    "Iron did not enter the first menu channel");
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.MACHINE_SLOT_COUNT + 1)
                            .getCount() == 2,
                    "Redstone did not enter the second menu channel");
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(800, false) == 800, "Energy fixture was rejected");
        });
        helper.runAtTickTime(14, () -> {
            PrecisionAssemblerMenu menu = opened.get();
            Player player = viewer.get();
            helper.assertTrue(menu.processState() == ProcessMachineState.RUNNING,
                    "Menu did not show the active server process");
            helper.assertTrue(menu.progress() > 0 && menu.totalProcessingTicks() == 20,
                    "Menu progress or recipe duration did not match the server");
            helper.assertTrue(menu.energyStored() < 800, "Menu did not show consumed Energy");
            helper.assertTrue(!menu.getSlot(0).mayPickup(player),
                    "Active process allowed input extraction");
            helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_INGOT)),
                    "Active process allowed input insertion");
            helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(),
                    "Active process quick-moved a locked input into the player inventory");
            player.getInventory().setItem(11, new ItemStack(Items.IRON_INGOT));
            menu.quickMoveStack(player, PrecisionAssemblerMenu.MACHINE_SLOT_COUNT + 2);
            helper.assertTrue(input(helper, 0).getStackInSlot(0).getCount() == 2,
                    "Active menu inserted into the locked input port");
        });
        helper.runAtTickTime(40, () -> {
            PrecisionAssemblerMenu menu = opened.get();
            Player player = viewer.get();
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.SLOT_OUTPUT_FIRST)
                            .is(ModItems.ADVANCED_CIRCUIT.get()),
                    "First output could not be taken through the menu");
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            player.getInventory().setItem(8, new ItemStack(Items.REDSTONE_TORCH, 63));
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.SLOT_OUTPUT_FIRST + 1)
                            .getCount() == 2,
                    "Second output did not begin the bounded partial transfer");
            helper.assertTrue(player.getInventory().getItem(8).getCount() == 64
                            && PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.OUTPUT_1)
                            .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                            .getStackInSlot(0).getCount() == 1,
                    "Partial menu transfer duplicated or lost an output item");
            player.getInventory().setItem(7, ItemStack.EMPTY);
            helper.assertTrue(menu.quickMoveStack(player, PrecisionAssemblerMenu.SLOT_OUTPUT_FIRST + 1)
                            .getCount() == 1,
                    "Second output did not transfer its remaining item");
            helper.assertTrue(player.getInventory().getItem(7).is(Items.REDSTONE_TORCH)
                            && player.getInventory().getItem(7).getCount() == 1,
                    "Remaining output did not reach the free player slot");
            helper.assertTrue(PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.OUTPUT_0)
                            .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                            .getStackInSlot(0).isEmpty(),
                    "First authoritative output remained after menu transfer");
            helper.assertTrue(PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.OUTPUT_1)
                            .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                            .getStackInSlot(0).isEmpty(),
                    "Second authoritative output remained after menu transfer");
            helper.succeed();
        });
    }

    private static PrecisionAssemblerMenu menu(GameTestHelper helper, Player player) {
        PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
        PrecisionAssemblerMenu menu = (PrecisionAssemblerMenu) controller.createMenu(
                31, player.getInventory(), player);
        helper.assertTrue(menu != null, "Precision Assembler menu construction was rejected");
        return menu;
    }

    private static Player nearbyViewer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        positionNearby(helper, player);
        return player;
    }

    private static void positionNearby(GameTestHelper helper, Player player) {
        BlockPos position = helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER);
        player.setPos(position.getX() + 0.5D, position.getY() + 0.5D, position.getZ() + 0.5D);
    }

    private static IItemHandler input(GameTestHelper helper, int index) {
        return PrecisionAssemblerGameTests.port(helper, INPUTS.get(index))
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
    }
}
