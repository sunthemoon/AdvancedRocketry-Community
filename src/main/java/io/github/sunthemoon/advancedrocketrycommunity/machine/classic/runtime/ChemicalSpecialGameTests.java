package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.CarbonCartridge;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SuitEquipmentRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SpaceBreathingApplication;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.ClassicResourceBank;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W32: real chemical reactors run the special recipes through production hatches, kernel and manager. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalSpecialGameTests {
    private static final String REACTOR = "chemical_reactor";

    @GameTest(template = "empty", batch = "w32_seal_chest", timeoutTicks = 30)
    public static void reactorSealsAnEligibleChestplateAndKeepsItsData(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, REACTOR, Direction.NORTH)) {
            var recipe = fixture.recipe("chemical_reacting_space_breathing_chestplate");
            ItemStack chest = markedChest();
            insert(fixture, chest, new ItemStack(ModItems.PIPE_SEAL.get()), sheets(), emptyTank());
            power(fixture, recipe); fixture.tick(5);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.RUNNING
                    && fixture.controller.checkpoint().job().orElseThrow().recipe().equals(recipe.getId()), "Seal job did not start");
            fixture.tick(recipe.rows().processingTicks() - 4); fixture.assertCompleted(recipe);
            ItemStack sealed = fixture.output().getStackInSlot(0);
            ItemStack expected = chest.copy(); expected.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1);
            helper.assertTrue(ItemStack.matches(sealed, expected), "The sealed chestplate is not the input with one enchantment added");
            helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(SuitEquipmentContent.SPACE_BREATHING.get(), sealed) == 1
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, sealed) == 2 && sealed.getDamageValue() == 37
                    && sealed.getTag().getString("w32_marker").equals("kept") && sealed.getHoverName().getString().equals("W32 plate"),
                    "Damage, name, enchantments or unknown data changed");
            for (int slot = 1; slot < 4; slot++) { helper.assertTrue(fixture.output().getStackInSlot(slot).isEmpty(), "Extra output"); }
            var resources = fixture.resources(); fixture.tick(5);
            helper.assertTrue(resources.equals(fixture.resources()) && fixture.controller.checkpoint().batchOrdinal() == 1, "Completed seal repeated");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w32_seal_refuse", timeoutTicks = 30)
    public static void reactorRefusesIneligibleArmorAndGasBearingTanksWithNothingConsumed(GameTestHelper helper) {
        ItemStack sealed = markedChest(); sealed.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1);
        ItemStack oxygen = emptyTank(); oxygen.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 500, 8_000).encode());
        for (var inputs : List.of(List.of(sealed, oxygen.copy()), List.of(markedChest(), oxygen.copy()), List.of(sealed, emptyTank()))) {
            try (var fixture = new C16cMachineFixture(helper, REACTOR, Direction.NORTH)) {
                var recipe = fixture.recipe("chemical_reacting_space_breathing_chestplate");
                insert(fixture, inputs.get(0).copy(), new ItemStack(ModItems.PIPE_SEAL.get()), sheets(), inputs.get(1).copy());
                power(fixture, recipe); var resources = fixture.resources(); int energy = fixture.controller.storedEnergy();
                fixture.tick(20);
                helper.assertTrue(fixture.controller.reason() == ClassicWait.INELIGIBLE && fixture.controller.checkpoint().job().isEmpty()
                        && fixture.controller.checkpoint().batchOrdinal() == 0 && resources.equals(fixture.resources())
                        && fixture.controller.storedEnergy() == energy, "Ineligible input was consumed or not reported: " + fixture.controller.reason());
                if (inputs.get(0) != sealed) {
                    // The refusal is not sticky: an empty tank in the same slot lets the same chestplate seal.
                    var bank = fixture.controller.checkpoint().resources().bank(fixture.hatches("item_input").get(0).bankKey()).orElseThrow();
                    fixture.replaceBanks(List.of(bank.withItem(3, emptyTank()))); fixture.tick(recipe.rows().processingTicks() + 2);
                    helper.assertTrue(fixture.controller.checkpoint().batchOrdinal() == 1 && EnchantmentHelper.getItemEnchantmentLevel(
                            SuitEquipmentContent.SPACE_BREATHING.get(), fixture.output().getStackInSlot(0)) == 1, "Eligible retry did not seal");
                }
            }
        }
        // Built-in suits already seal and provider-owned armor keeps its own root (ADR-066 §5.1).
        var builtIn = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
        helper.assertTrue(SpaceBreathingApplication.refusal(builtIn, EquipmentSlot.CHEST, SuitEquipmentContent.SPACE_BREATHING.get(),
                SuitEquipmentRuntime::claimed).equals(Optional.of(SpaceBreathingApplication.Refusal.BUILT_IN_SUIT)), "Built-in suit accepted");
        helper.assertTrue(SpaceBreathingApplication.refusal(new ItemStack(Items.IRON_CHESTPLATE), EquipmentSlot.CHEST,
                SuitEquipmentContent.SPACE_BREATHING.get(), SuitEquipmentRuntime::claimed).isEmpty(), "Unclaimed vanilla armor refused");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w32_refresh", timeoutTicks = 30)
    public static void reactorRefreshesASpentCartridgeKeepingItsIdentityWithoutDuplicating(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, REACTOR, Direction.NORTH)) {
            var recipe = fixture.recipe("chemical_reacting_carbon_cartridge_refresh");
            ItemStack spent = cartridge(0);
            insert(fixture, spent); power(fixture, recipe); fixture.tick(recipe.rows().processingTicks() + 1);
            assertRefreshed(helper, fixture, recipe, spent);
            var resources = fixture.resources(); fixture.tick(5);
            helper.assertTrue(resources.equals(fixture.resources()) && fixture.controller.checkpoint().batchOrdinal() == 1, "Refresh repeated");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "w32_refresh_refuse", timeoutTicks = 30)
    public static void reactorRefusesAnUnspentCartridgeAndPausesWhenTheSubjectChanges(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, REACTOR, Direction.NORTH)) {
            var recipe = fixture.recipe("chemical_reacting_carbon_cartridge_refresh");
            insert(fixture, cartridge(5)); power(fixture, recipe);
            var resources = fixture.resources(); int energy = fixture.controller.storedEnergy(); fixture.tick(10);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.INELIGIBLE && fixture.controller.checkpoint().job().isEmpty()
                    && resources.equals(fixture.resources()) && fixture.controller.storedEnergy() == energy, "Unspent cartridge was consumed");
            var key = fixture.hatches("item_input").get(0).bankKey();
            ItemStack spent = cartridge(0);
            fixture.replaceBanks(List.of(fixture.controller.checkpoint().resources().bank(key).orElseThrow().withItem(0, spent.copy())));
            fixture.tick(20);
            int progress = fixture.controller.progressTicks();
            helper.assertTrue(fixture.controller.reason() == ClassicWait.RUNNING && progress > 0, "Spent cartridge did not start");
            // Swapping the subject mid-job pauses the job; it never refreshes or debits another cartridge.
            fixture.replaceBanks(List.of(fixture.controller.checkpoint().resources().bank(key).orElseThrow().withItem(0, cartridge(5))));
            var swapped = fixture.resources(); fixture.tick(40);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.INELIGIBLE && fixture.controller.progressTicks() == progress
                    && fixture.controller.checkpoint().job().isPresent() && swapped.equals(fixture.resources()), "Swapped subject was processed");
            SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
            fixture.replaceBanks(List.of(fixture.controller.checkpoint().resources().bank(key).orElseThrow().withItem(0, spent.copy())));
            fixture.tick(40);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.CONFIG_DISABLED && fixture.controller.checkpoint().job().isPresent()
                    && fixture.controller.checkpoint().batchOrdinal() == 0, "Disabled life support still refreshed");
            SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED);
            fixture.tick(recipe.rows().processingTicks());
            assertRefreshed(helper, fixture, recipe, spent);
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED); }
        helper.succeed();
    }

    private static void assertRefreshed(GameTestHelper helper, C16cMachineFixture fixture, ClassicMachineRecipe recipe, ItemStack spent) {
        helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.controller.checkpoint().batchOrdinal() == 1
                && fixture.controller.checkpoint().lastTransaction().isPresent(), "Refresh did not complete exactly once: " + fixture.controller.reason());
        helper.assertTrue(fixture.injected == recipe.rows().processingTicks() * recipe.rows().energyPerTick()
                && fixture.energyLeft == 0 && fixture.controller.storedEnergy() == 0, "Exact FE accounting differs");
        CompoundTag expected = spent.serializeNBT();
        expected.getCompound("tag").getCompound(CarbonCartridge.KEY).putInt("uses", CarbonCartridge.MAX_USES);
        helper.assertTrue(fixture.output().getStackInSlot(0).serializeNBT().equals(expected), "Refreshed cartridge lost or changed data");
        helper.assertTrue(ItemStack.matches(fixture.output().getStackInSlot(1), new ItemStack(Items.CHARCOAL)), "Legacy charcoal missing");
        int cartridges = 0;
        for (int slot = 0; slot < 4; slot++) {
            for (ItemStack stack : List.of(fixture.input().getStackInSlot(slot), fixture.output().getStackInSlot(slot))) {
                if (stack.is(ModItems.CARBON_SCRUBBER_CARTRIDGE.get())) { cartridges += stack.getCount(); }
            }
        }
        helper.assertTrue(cartridges == 1, "The refresh created or lost a cartridge: " + cartridges);
    }

    private static ItemStack markedChest() {
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.setDamageValue(37); chest.setHoverName(Component.literal("W32 plate"));
        chest.enchant(Enchantments.UNBREAKING, 2); chest.getOrCreateTag().putString("w32_marker", "kept");
        return chest;
    }
    private static ItemStack cartridge(int uses) {
        ItemStack stack = new ItemStack(ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
        CarbonCartridge.withUsesForDisplay(stack, uses);
        stack.setHoverName(Component.literal("Vent cartridge")); stack.getOrCreateTag().putString("w32_marker", "kept");
        return stack;
    }
    private static ItemStack sheets() { return new ItemStack(MaterialContent.item("titanium_aluminide_sheet"), 4); }
    private static ItemStack emptyTank() { return SuitEquipmentContent.modules().get("super_high_pressure_tank").get().getDefaultInstance(); }
    private static void insert(C16cMachineFixture fixture, ItemStack... stacks) {
        for (int slot = 0; slot < stacks.length; slot++) {
            fixture.helper.assertTrue(fixture.input().insertItem(slot, stacks[slot], false).isEmpty(), "Item input refused");
        }
    }
    private static void power(C16cMachineFixture fixture, ClassicMachineRecipe recipe) {
        fixture.energyLeft = recipe.rows().processingTicks() * recipe.rows().energyPerTick(); fixture.feed(); fixture.manager.wake(fixture.origin);
    }
    private ChemicalSpecialGameTests() { }
}
