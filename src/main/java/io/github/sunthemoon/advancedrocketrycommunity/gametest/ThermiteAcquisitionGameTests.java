package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Survival player's own InventoryMenu clicks through the loaded crafting result slot. Server menu path only:
 * not pure matches/assemble, the C2S click packet handler, real client behavior, packaged S1 or restart evidence.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermiteAcquisitionGameTests {
    private static final String MARK = "thermite_acquisition_witness";
    private static final int RESULT = InventoryMenu.RESULT_SLOT;
    private static final int GRID_A = InventoryMenu.CRAFT_SLOT_START, GRID_B = GRID_A + 1, GRID_C = GRID_A + 2;
    private static final int STORE_A = InventoryMenu.INV_SLOT_START, STORE_B = STORE_A + 1, STORE_C = STORE_A + 2, STORE_D = STORE_A + 3;

    private ThermiteAcquisitionGameTests() { }

    @GameTest(template = "empty", batch = "thermite_acquisition", timeoutTicks = 20)
    public static void survivalMenuTakesThermiteThenTorchFromLoadedResultSlot(GameTestHelper helper) {
        Item aluminum = item(helper, "aluminum_dust"), iron = item(helper, "iron_dust");
        Item thermite = item(helper, "thermite"), torch = item(helper, "thermite_torch");
        FakePlayer player = survival(helper);
        InventoryMenu menu = player.inventoryMenu;
        try {
            menu.getSlot(STORE_A).set(marked(aluminum, 2));
            menu.getSlot(STORE_B).set(marked(iron, 1));
            menu.getSlot(STORE_C).set(marked(Items.STICK, 3));
            move(helper, player, menu, STORE_A, GRID_A);
            move(helper, player, menu, STORE_B, GRID_B);
            ItemStack shown = menu.getSlot(RESULT).getItem();
            helper.assertTrue(shown.is(thermite) && shown.getCount() == 1 && !shown.hasTag()
                    && ModIdentity.id("thermite").equals(selected(menu)), "Loaded menu did not offer the thermite recipe: " + shown);
            helper.assertTrue(!player.getRecipeBook().contains(ModIdentity.id("thermite")), "Fresh player already knew thermite");

            menu.clicked(RESULT, 0, ClickType.PICKUP, player);
            helper.assertTrue(exact(menu.getCarried(), new ItemStack(thermite)), "Thermite take cursor differs: " + menu.getCarried());
            helper.assertTrue(exact(menu.getSlot(GRID_A).getItem(), marked(aluminum, 1)) && menu.getSlot(GRID_B).getItem().isEmpty()
                    && menu.getSlot(GRID_C).getItem().isEmpty() && menu.getSlot(RESULT).getItem().isEmpty(),
                    "Thermite take did not consume exactly one of each input or left an offered result");
            helper.assertTrue(player.getRecipeBook().contains(ModIdentity.id("thermite")), "Native take did not award the used recipe");
            helper.assertTrue(menu.getSlot(STORE_A).getItem().isEmpty() && menu.getSlot(STORE_B).getItem().isEmpty()
                    && exact(menu.getSlot(STORE_C).getItem(), marked(Items.STICK, 3)), "Thermite take changed unrelated inventory");

            // Shift-click the marked leftover home, then place the taken thermite and one stick through clicks.
            menu.clicked(GRID_A, 0, ClickType.QUICK_MOVE, player);
            helper.assertTrue(exact(menu.getSlot(STORE_A).getItem(), marked(aluminum, 1)) && menu.getSlot(GRID_A).getItem().isEmpty()
                    && exact(menu.getCarried(), new ItemStack(thermite)), "Leftover marked dust did not return intact");
            menu.clicked(GRID_A, 0, ClickType.PICKUP, player);
            menu.clicked(STORE_C, 0, ClickType.PICKUP, player);
            menu.clicked(GRID_B, 1, ClickType.PICKUP, player);
            menu.clicked(STORE_C, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && exact(menu.getSlot(GRID_A).getItem(), new ItemStack(thermite))
                    && exact(menu.getSlot(GRID_B).getItem(), marked(Items.STICK, 1))
                    && exact(menu.getSlot(STORE_C).getItem(), marked(Items.STICK, 2)), "Click placement of torch inputs differs");
            shown = menu.getSlot(RESULT).getItem();
            helper.assertTrue(shown.is(torch) && shown.getCount() == 4 && !shown.hasTag()
                    && ModIdentity.id("thermite_torch").equals(selected(menu)), "Loaded menu did not offer the torch recipe: " + shown);

            menu.clicked(RESULT, 0, ClickType.PICKUP, player);
            helper.assertTrue(exact(menu.getCarried(), new ItemStack(torch, 4)), "Torch take cursor differs: " + menu.getCarried());
            helper.assertTrue(gridEmpty(menu) && menu.getSlot(RESULT).getItem().isEmpty(),
                    "Torch take did not consume the thermite and stick or left an offered result");
            helper.assertTrue(player.getRecipeBook().contains(ModIdentity.id("thermite_torch")), "Native take did not award the torch recipe");

            menu.clicked(STORE_D, 0, ClickType.PICKUP, player);
            helper.assertTrue(menu.getCarried().isEmpty() && exact(menu.getSlot(STORE_D).getItem(), new ItemStack(torch, 4))
                    && exact(menu.getSlot(STORE_A).getItem(), marked(aluminum, 1))
                    && exact(menu.getSlot(STORE_C).getItem(), marked(Items.STICK, 2))
                    && player.getInventory().countItem(torch) == 4 && player.getInventory().countItem(thermite) == 0
                    && player.getInventory().countItem(iron) == 0 && player.getInventory().countItem(aluminum) == 1
                    && player.getInventory().countItem(Items.STICK) == 2, "Final survival inventory differs");
        } finally {
            release(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "thermite_acquisition", timeoutTicks = 20)
    public static void survivalMenuWrongInputsAndBlockedCursorTakeNothing(GameTestHelper helper) {
        Item aluminum = item(helper, "aluminum_dust"), iron = item(helper, "iron_dust");
        Item thermite = item(helper, "thermite"), torch = item(helper, "thermite_torch");
        FakePlayer player = survival(helper);
        InventoryMenu menu = player.inventoryMenu;
        try {
            List<List<ItemStack>> wrong = List.of(
                    List.of(marked(aluminum, 1), marked(aluminum, 1)),
                    List.of(marked(aluminum, 1), marked(iron, 1), marked(Items.STICK, 1)),
                    List.of(marked(thermite, 1), marked(iron, 1)),
                    List.of(marked(Items.STICK, 1)));
            for (List<ItemStack> inputs : wrong) {
                load(helper, player, menu, inputs);
                List<ItemStack> before = grid(menu);
                helper.assertTrue(menu.getSlot(RESULT).getItem().isEmpty(), "Wrong input offered a result: " + inputs);
                menu.clicked(RESULT, 0, ClickType.PICKUP, player);
                menu.clicked(RESULT, 1, ClickType.PICKUP, player);
                menu.clicked(RESULT, 0, ClickType.QUICK_MOVE, player);
                helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(RESULT).getItem().isEmpty() && same(before, grid(menu))
                        && player.getInventory().countItem(thermite) == 0 && player.getInventory().countItem(torch) == 0,
                        "Wrong-input take changed the cursor, marked inputs or inventory: " + inputs);
                menu.clearCraftingContent();
            }

            // A valid offered result cannot be taken onto a cursor holding a different stack.
            load(helper, player, menu, List.of(marked(aluminum, 1), marked(iron, 1)));
            menu.getSlot(STORE_D).set(marked(Items.STICK, 1));
            menu.clicked(STORE_D, 0, ClickType.PICKUP, player);
            List<ItemStack> before = grid(menu);
            helper.assertTrue(exact(menu.getSlot(RESULT).getItem(), new ItemStack(thermite)), "Valid inputs offered no thermite");
            menu.clicked(RESULT, 0, ClickType.PICKUP, player);
            helper.assertTrue(exact(menu.getCarried(), marked(Items.STICK, 1)) && same(before, grid(menu))
                    && exact(menu.getSlot(RESULT).getItem(), new ItemStack(thermite)),
                    "Blocked-cursor take consumed inputs or replaced the cursor");
            helper.assertTrue(!player.getRecipeBook().contains(ModIdentity.id("thermite"))
                    && !player.getRecipeBook().contains(ModIdentity.id("thermite_torch")), "Refused takes awarded a recipe");
        } finally {
            release(player);
        }
        helper.succeed();
    }

    /** Stocks inputs in storage slots, then moves each whole stack into the grid by two PICKUP clicks. */
    private static void load(GameTestHelper helper, FakePlayer player, InventoryMenu menu, List<ItemStack> inputs) {
        helper.assertTrue(inputs.size() <= 3 && gridEmpty(menu), "Grid fixture exceeds the three-input budget or is dirty");
        for (int index = 0; index < inputs.size(); index++) {
            menu.getSlot(STORE_A + index).set(inputs.get(index).copy());
            move(helper, player, menu, STORE_A + index, GRID_A + index);
        }
    }

    private static void move(GameTestHelper helper, FakePlayer player, InventoryMenu menu, int from, int to) {
        menu.clicked(from, 0, ClickType.PICKUP, player);
        menu.clicked(to, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && menu.getSlot(from).getItem().isEmpty()
                && !menu.getSlot(to).getItem().isEmpty(), "Click move left a cursor or source stack: " + from + "->" + to);
    }

    private static ResourceLocation selected(InventoryMenu menu) {
        return menu.getSlot(RESULT).container instanceof ResultContainer result && result.getRecipeUsed() != null
                ? result.getRecipeUsed().getId() : null;
    }

    private static List<ItemStack> grid(InventoryMenu menu) {
        return IntStream.range(GRID_A, InventoryMenu.CRAFT_SLOT_END).mapToObj(slot -> menu.getSlot(slot).getItem().copy()).toList();
    }

    private static boolean gridEmpty(InventoryMenu menu) { return grid(menu).stream().allMatch(ItemStack::isEmpty); }

    private static boolean same(List<ItemStack> first, List<ItemStack> second) {
        return first.size() == second.size() && IntStream.range(0, first.size()).allMatch(i -> exact(first.get(i), second.get(i)));
    }

    private static boolean exact(ItemStack actual, ItemStack expected) { return ItemStack.matches(expected, actual); }

    private static ItemStack marked(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString(MARK, "retained");
        return stack;
    }

    private static Item item(GameTestHelper helper, String name) {
        ResourceLocation id = ModIdentity.id(name);
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "Literal item ID absent: " + name);
        Item value = ForgeRegistries.ITEMS.getValue(id);
        helper.assertTrue(value != null && value != Items.AIR, "Literal item resolved to AIR: " + name);
        return value;
    }

    private static FakePlayer survival(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ThermiteCrafter"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !player.isCreative()
                && !player.isSpectator() && player.containerMenu == player.inventoryMenu && gridEmpty(player.inventoryMenu),
                "Fixture is not a fresh survival player on its own crafting menu");
        return player;
    }

    /** The fixture player is never added to the level; drop its menu/inventory contents and advancement listeners. */
    private static void release(FakePlayer player) {
        player.inventoryMenu.setCarried(ItemStack.EMPTY);
        player.inventoryMenu.clearCraftingContent();
        player.getInventory().clearContent();
        player.getAdvancements().stopListening();
    }
}
