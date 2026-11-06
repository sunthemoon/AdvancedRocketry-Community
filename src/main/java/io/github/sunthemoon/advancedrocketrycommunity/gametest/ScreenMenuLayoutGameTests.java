package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Coordinate-only changes preserve slot indices, eligibility and round-trip inventory conservation. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScreenMenuLayoutGameTests {
    @GameTest(template = "empty", batch = "screen_menu_layout", timeoutTicks = 20)
    public static void relocatedElectrolyzerChargePreservesInsertOnlyRedstonePolicy(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.ELECTROLYZER.get());
        var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        var player = helper.makeMockPlayer();
        var menu = (ElectrolyzerMenu) machine.createMenu(1, player.getInventory(), player);
        var charge = menu.getSlot(ElectrolyzerBlockEntity.SLOT_CHARGE);
        helper.assertTrue(charge.x == 20 && charge.y == 35, "Charge slot has the wrong coordinates");
        helper.assertTrue(charge.mayPlace(new ItemStack(Items.REDSTONE)) && !charge.mayPlace(new ItemStack(Items.STONE)),
                "The charge filter changed");
        helper.assertTrue(!menu.getSlot(2).mayPlace(new ItemStack(ModItems.HYDROGEN_CANISTER.get()))
                && !menu.getSlot(3).mayPlace(new ItemStack(ModItems.OXYGEN_CANISTER.get())), "Outputs became inputs");
        player.getInventory().setItem(9, new ItemStack(Items.REDSTONE, 13));
        menu.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_COUNT);
        helper.assertTrue(charge.getItem().is(Items.REDSTONE) && charge.getItem().getCount() == 13,
                "Shift-click did not insert into the same charge index");
        // The existing charge port is insert-only, including through the menu; moving it must not grant extraction.
        helper.assertTrue(!charge.mayPickup(player), "The insert-only charge slot became removable");
        helper.assertTrue(menu.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_CHARGE).isEmpty(),
                "Shift-click extracted from the insert-only charge slot");
        menu.clicked(ElectrolyzerBlockEntity.SLOT_CHARGE, 0, ClickType.PICKUP, player);
        helper.assertTrue(charge.getItem().is(Items.REDSTONE) && charge.getItem().getCount() == 13
                && player.getInventory().countItem(Items.REDSTONE) == 0 && menu.getCarried().isEmpty(),
                "Refused charge extraction lost or duplicated redstone");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "screen_menu_layout", timeoutTicks = 20)
    public static void relocatedBayPlayerSlotsKeepTheirInventoryIndicesAndPickupBehavior(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SatelliteBayMenu.writeOpenData(buffer);
            var menu = new SatelliteBayMenu(2, player.getInventory(), buffer);
            helper.assertTrue(menu.slots.size() == 37, "Bay menu slot count changed");
            helper.assertTrue(menu.getSlot(0).x == 80 && menu.getSlot(0).y == 35, "The bay slot moved");
            for (int index = 1; index < 37; index++) {
                int relative = index - 1;
                int inventoryIndex = relative < 27 ? relative + 9 : relative - 27;
                var slot = menu.getSlot(index);
                helper.assertTrue(slot.getContainerSlot() == inventoryIndex, "Player inventory index changed");
                helper.assertTrue(slot.x == 8 + relative % 9 * 18
                        && slot.y == (relative < 27 ? 96 + relative / 9 * 18 : 154), "Player slot coordinates drifted");
                player.getInventory().setItem(inventoryIndex, new ItemStack(Items.STONE, 3));
                menu.clicked(index, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().is(Items.STONE) && menu.getCarried().getCount() == 3
                        && player.getInventory().getItem(inventoryIndex).isEmpty(), "Moved slot did not pick up its item");
                menu.clicked(index, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().isEmpty() && slot.getItem().getCount() == 3,
                        "Moved slot did not return its item");
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    private ScreenMenuLayoutGameTests() { }
}
