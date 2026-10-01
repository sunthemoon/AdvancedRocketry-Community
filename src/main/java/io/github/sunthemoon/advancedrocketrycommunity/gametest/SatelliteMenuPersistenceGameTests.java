package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The satellite terminal's and builder's redstone slots hold up to 64 items: a partial shift-click out of them and a
 * merge into them change the handler's live stack, and must mark the block entity changed (the C11R-C1 pattern).
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteMenuPersistenceGameTests {
    private SatelliteMenuPersistenceGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite_menu_save", timeoutTicks = 100)
    public static void partialShiftClicksAndMergesOfRedstoneMarkTheBlockEntityChanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos terminalPos = new BlockPos(1, 2, 1);
        BlockPos builderPos = new BlockPos(3, 2, 1);
        helper.setBlock(terminalPos, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH).setValue(SatelliteTerminalBlock.LIT, false));
        helper.setBlock(builderPos, ModBlocks.SATELLITE_BUILDER.get().defaultBlockState()
                .setValue(SatelliteBuilderBlock.FACING, Direction.NORTH));
        SatelliteTerminalBlockEntity terminal = (SatelliteTerminalBlockEntity) helper.getBlockEntity(terminalPos);
        SatelliteBuilderBlockEntity builder = (SatelliteBuilderBlockEntity) helper.getBlockEntity(builderPos);
        ServerPlayer player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "satelliteMenuSave",
                level, helper.absolutePos(new BlockPos(2, 2, 3)), new ArrayList<>());
        try {
            SatelliteTerminalMenu terminalMenu = new SatelliteTerminalMenu(61, player.getInventory(), terminal,
                    SatelliteTerminalTargets.current());
            check(helper, level, player, terminalMenu, terminal.menuInventory(),
                    SatelliteTerminalBlockEntity.SLOT_CHARGE, helper.absolutePos(terminalPos), "terminal");
            SatelliteBuilderMenu builderMenu = new SatelliteBuilderMenu(62, player.getInventory(), builder,
                    SatelliteRuntime.catalogGeneration());
            check(helper, level, player, builderMenu, builder.menuInventory(),
                    SatelliteBuilderBlockEntity.SLOT_CHARGE, helper.absolutePos(builderPos), "builder");
        } finally {
            player.containerMenu = player.inventoryMenu;
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The redstone slot's menu index equals its handler index in both menus. */
    private static void check(GameTestHelper helper, ServerLevel level, ServerPlayer player, AbstractContainerMenu menu,
                              IItemHandler handler, int slot, BlockPos pos, String name) {
        IItemHandlerModifiable modifiable = (IItemHandlerModifiable) handler;
        player.containerMenu = menu;
        LevelChunk chunk = level.getChunkAt(pos);
        // A partial move out of the slot: the inventory has room for 10 more redstone only.
        modifiable.setStackInSlot(slot, new ItemStack(Items.REDSTONE, 64));
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        player.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 54));
        chunk.setUnsaved(false);
        menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(handler.getStackInSlot(slot).getCount() == 54, "The " + name + " move was not partial");
        helper.assertTrue(chunk.isUnsaved(), "A partial shift-click out of the " + name + " left the chunk clean");
        // A merge into the non-empty slot.
        modifiable.setStackInSlot(slot, new ItemStack(Items.REDSTONE, 10));
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 5));
        chunk.setUnsaved(false);
        menu.clicked(menu.slots.size() - 36, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(handler.getStackInSlot(slot).getCount() == 15, "The redstone was not merged into the "
                + name + " (" + handler.getStackInSlot(slot) + ")");
        helper.assertTrue(chunk.isUnsaved(), "A merge into the " + name + " left the chunk clean");
        modifiable.setStackInSlot(slot, ItemStack.EMPTY);
    }
}
