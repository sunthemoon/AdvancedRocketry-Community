package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EnchantedSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActiveCanisterGameTests {
    private static ItemStack chest(boolean enchanted) {
        if (enchanted) {
            ItemStack armor = new ItemStack(Items.IRON_CHESTPLATE);
            armor.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1);
            return armor;
        }
        ItemStack tank = new ItemStack(SuitEquipmentContent.modules().get("pressure_tank").get());
        return ArmorEquipment.install(new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()), tank).armor();
    }

    private static boolean use(EquipmentFixture f, InteractionHand hand) {
        return f.player.gameMode.useItem(f.player, f.level, f.player.getItemInHand(hand), hand).consumesAction();
    }

    @GameTest(template = "empty", batch = "active_canister", timeoutTicks = 100)
    public static void singleMainHandCanisterReturnsToSelectedSlot(GameTestHelper helper) {
        singleReturnsToHand(helper, InteractionHand.MAIN_HAND);
    }

    @GameTest(template = "empty", batch = "active_canister", timeoutTicks = 100)
    public static void singleOffhandCanisterReturnsToOffhand(GameTestHelper helper) {
        singleReturnsToHand(helper, InteractionHand.OFF_HAND);
    }

    private static void singleReturnsToHand(GameTestHelper helper, InteractionHand hand) {
        try (var f = new EquipmentFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            f.player.setShiftKeyDown(false);
            f.player.getInventory().selected = 7;
            for (boolean enchanted : new boolean[]{false, true}) {
                // Free slot zero, an existing empty-canister stack, and completely full storage.
                for (int storage = 0; storage < 3; storage++) {
                    f.player.getInventory().clearContent();
                    if (storage == 1) { f.player.getInventory().setItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2)); }
                    if (storage == 2) {
                        for (int i = 0; i < 36; i++) { f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64)); }
                    }
                    f.player.setItemSlot(EquipmentSlot.CHEST, chest(enchanted));
                    InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    ItemStack untouched = new ItemStack(Items.DIAMOND, 3);
                    f.player.setItemInHand(other, untouched);
                    f.player.setItemInHand(hand, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
                    CompoundTag slotZero = f.player.getInventory().getItem(0).serializeNBT();
                    int drops = f.level.getEntitiesOfClass(ItemEntity.class, f.bounds).size();
                    helper.assertTrue(use(f, hand), "Single canister refill refused");
                    helper.assertTrue(f.player.getItemInHand(hand).is(ModItems.EMPTY_CANISTER.get())
                            && f.player.getItemInHand(hand).getCount() == 1, "Empty canister did not replace the used hand stack");
                    helper.assertTrue(slotZero.equals(f.player.getInventory().getItem(0).serializeNBT())
                            && f.player.getItemInHand(other) == untouched, "Return changed another slot or hand");
                    helper.assertTrue(f.player.getInventory().countItem(ModItems.OXYGEN_CANISTER.get()) == 0
                            && f.player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()) == (storage == 1 ? 3 : 1),
                            "Single refill changed canister totals");
                    ItemStack armor = f.player.getItemBySlot(EquipmentSlot.CHEST);
                    int oxygen = enchanted ? EnchantedSuitOxygen.read(armor.getTag()) : SpaceSuitOxygen.read(armor).oxygenUnits();
                    helper.assertTrue(oxygen == 1000 && f.level.getEntitiesOfClass(ItemEntity.class, f.bounds).size() == drops,
                            "Single refill lost oxygen or dropped its return");
                    CompoundTag after = armor.serializeNBT();
                    helper.assertTrue(!use(f, hand) && after.equals(armor.serializeNBT()), "Returned empty canister supplied oxygen again");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "active_canister", timeoutTicks = 100)
    public static void normalUseKeepsActiveOxygenSeparateFromEmptyInstalledTank(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            f.player.setShiftKeyDown(false);
            f.player.setItemSlot(EquipmentSlot.CHEST, chest(false));
            ItemStack armor = f.player.getItemBySlot(EquipmentSlot.CHEST);
            CompoundTag modules = armor.getTag().getCompound(ArmorRootCodec.KEY).copy();
            armor.getOrCreateTag().putString("marker", "preserve");
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
            helper.assertTrue(use(f, InteractionHand.MAIN_HAND), "Normal oxygen use refused");
            ItemStack loaded = ItemStack.of(armor.serializeNBT());
            helper.assertTrue(SpaceSuitOxygen.read(loaded).oxygenUnits() == 1000
                    && modules.equals(loaded.getTag().getCompound(ArmorRootCodec.KEY))
                    && loaded.getTag().getString("marker").equals("preserve"), "Normal refill moved or mirrored active oxygen");
            var module = ArmorEquipment.modules(loaded).get(0);
            var tank = TankData.read(module.stack().getCompound("tag").get(TankData.KEY), 2000, 1);
            helper.assertTrue(tank != null && tank.gas() == TankData.Gas.EMPTY && tank.stored() == 0 && tank.maximum() == 2000,
                    "Normal use charged or changed the auxiliary tank");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "active_canister", timeoutTicks = 100)
    public static void stackedUseKeepsRemainingCanistersAndReturnsOneIntoInventory(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            f.player.setShiftKeyDown(false);
            f.player.getInventory().selected = 7;
            for (InteractionHand hand : InteractionHand.values()) {
                for (boolean merge : new boolean[]{false, true}) {
                    f.player.getInventory().clearContent();
                    f.player.setItemSlot(EquipmentSlot.CHEST, chest(false));
                    if (merge) { f.player.getInventory().setItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2)); }
                    f.player.setItemInHand(hand, new ItemStack(ModItems.OXYGEN_CANISTER.get(), 3));
                    helper.assertTrue(use(f, hand), "Stacked refill refused");
                    helper.assertTrue(f.player.getItemInHand(hand).is(ModItems.OXYGEN_CANISTER.get())
                            && f.player.getItemInHand(hand).getCount() == 2
                            && f.player.getInventory().getItem(0).is(ModItems.EMPTY_CANISTER.get())
                            && f.player.getInventory().getItem(0).getCount() == (merge ? 3 : 1), "Stacked return convention changed");
                    helper.assertTrue(SpaceSuitOxygen.read(f.player.getItemBySlot(EquipmentSlot.CHEST)).oxygenUnits() == 1000,
                            "Stacked refill changed oxygen amount");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "active_canister", timeoutTicks = 100)
    public static void creativeNormalUseAndInsufficientCapacityKeepTheSource(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.player.setShiftKeyDown(false);
            for (InteractionHand hand : InteractionHand.values()) {
                f.player.getInventory().clearContent();
                f.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                f.player.setItemSlot(EquipmentSlot.CHEST, chest(false));
                f.player.setItemInHand(hand, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
                helper.assertTrue(use(f, hand) && f.player.getItemInHand(hand).is(ModItems.OXYGEN_CANISTER.get())
                        && f.player.getItemInHand(hand).getCount() == 1
                        && f.player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()) == 0, "Creative normal-use exemption changed");
                ItemStack armor = f.player.getItemBySlot(EquipmentSlot.CHEST);
                helper.assertTrue(SpaceSuitOxygen.read(armor).oxygenUnits() == 1000, "Creative refill did not credit active oxygen");
                f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                helper.assertTrue(SpaceSuitOxygen.set(armor, 1001), "Capacity fixture failed");
                CompoundTag before = armor.serializeNBT();
                helper.assertTrue(!use(f, hand) && before.equals(armor.serializeNBT())
                        && f.player.getItemInHand(hand).is(ModItems.OXYGEN_CANISTER.get())
                        && f.player.getItemInHand(hand).getCount() == 1
                        && f.player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()) == 0, "Rejected refill changed source or chest");
            }
        }
        helper.succeed();
    }

    private ActiveCanisterGameTests() { }
}
