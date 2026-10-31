package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasChargePadBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasPadState;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EnchantedSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FiniteCanisterGameTests {
    private static SuitEquipmentService service() { return new SuitEquipmentService(SuitEquipmentCatalog.empty()); }
    private static ItemStack chest(TankData.Gas gas, int amount) {
        ItemStack tank = new ItemStack(SuitEquipmentContent.modules().get("pressure_tank").get());
        tank.getOrCreateTag().put(TankData.KEY, new TankData(gas, amount, 2000).encode());
        return ArmorEquipment.install(new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()), tank).armor();
    }
    private static void held(EquipmentFixture f, TankData.Gas gas, int count) {
        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(gas == TankData.Gas.OXYGEN
                ? ModItems.OXYGEN_CANISTER.get() : ModItems.HYDROGEN_CANISTER.get(), count));
    }
    @GameTest(template = "empty", batch = "finite_canister", timeoutTicks = 100)
    public static void padFillsBothGasesAndPersistsBankAndOutputTogether(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            BlockPos pos = f.position.east(2); var original = f.level.getBlockState(pos);
            try {
                f.level.setBlock(pos, ModBlocks.GAS_CHARGE_PAD.get().defaultBlockState(), Block.UPDATE_ALL);
                var pad = (GasChargePadBlockEntity) f.level.getBlockEntity(pos);
                helper.assertTrue(pad.onlyOpCanSetNbt(), "Pad root must be operator-only");
                for (var gas : new TankData.Gas[]{TankData.Gas.OXYGEN, TankData.Gas.HYDROGEN}) {
                    pad.load(new CompoundTag());
                    var items = pad.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(AssertionError::new);
                    helper.assertTrue(items.insertItem(1, new ItemStack(ModItems.EMPTY_CANISTER.get()), false).isEmpty(), "Insert empty output");
                    helper.assertTrue(!pad.process() && pad.state().amount() == 0, "Empty bank created gas");
                    var fluids = pad.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(AssertionError::new);
                    var fluid = new FluidStack(gas == TankData.Gas.OXYGEN ? ClassicFluids.OXYGEN.get() : ClassicFluids.HYDROGEN.get(), 16000);
                    helper.assertTrue(fluids.fill(fluid, FluidAction.SIMULATE) == 16000 && pad.state().amount() == 0, "Simulation mutated bank");
                    helper.assertTrue(fluids.fill(fluid, FluidAction.EXECUTE) == 16000 && pad.process(), "Full bank refused fill");
                    helper.assertTrue(pad.state().amount() == 15000 && pad.state().output().name().equals(gas.name()), "Bank/output did not commit together");
                    var saved = pad.saveWithoutMetadata(); pad.load(saved);
                    helper.assertTrue(saved.equals(pad.saveWithoutMetadata()), "Pad round trip changed bank or output");
                    f.player.setItemSlot(EquipmentSlot.CHEST, chest(gas, 0)); var before = f.player.getItemBySlot(EquipmentSlot.CHEST).serializeNBT();
                    f.player.teleportTo(f.level, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
                    for (int i = 0; i < 40; i++) { GasChargePadBlockEntity.tick(f.level, pos, pad.getBlockState(), pad); }
                    helper.assertTrue(before.equals(f.player.getItemBySlot(EquipmentSlot.CHEST).serializeNBT()) && pad.state().amount() == 15000,
                            "Standing on the pad automatically charged a player");
                    pad.invalidateCaps(); helper.assertTrue(fluids.fill(fluid, FluidAction.EXECUTE) == 0, "Stale capability retained authority"); pad.reviveCaps();
                }
                var raw = new CompoundTag(); var future = pad.state().encode(); future.putInt("schema_version", 2); raw.put(GasChargePadBlockEntity.KEY, future);
                pad.load(raw); helper.assertTrue(pad.protectedData() && raw.equals(pad.saveWithoutMetadata()), "Unsupported pad root not preserved");
                pad.load(new CompoundTag());
            } finally { f.level.setBlock(pos, original, Block.UPDATE_ALL); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "finite_canister", timeoutTicks = 100)
    public static void playerRefillsEachGasAndEmptyTankFromStackedFiniteCanisters(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            var service = service();
            for (var gas : new TankData.Gas[]{TankData.Gas.OXYGEN, TankData.Gas.HYDROGEN}) {
                f.player.getInventory().clearContent(); f.player.setItemSlot(EquipmentSlot.CHEST, chest(TankData.Gas.EMPTY, 0)); held(f, gas, 3);
                f.player.getAbilities().instabuild = true;
                helper.assertTrue(service.fillTankCanister(f.player, InteractionHand.MAIN_HAND), "EMPTY tank refused actual gas");
                helper.assertTrue(service.fillTankCanister(f.player, InteractionHand.MAIN_HAND), "Exact capacity refused");
                helper.assertTrue(!service.fillTankCanister(f.player, InteractionHand.MAIN_HAND), "Full tank accepted excess");
                var tank = gas == TankData.Gas.OXYGEN ? ArmorEquipment.reserve(f.player.getItemBySlot(EquipmentSlot.CHEST))
                        : ArmorEquipment.hydrogen(f.player.getItemBySlot(EquipmentSlot.CHEST));
                helper.assertTrue(tank.stored() == 2000 && f.player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1
                        && f.player.getInventory().countItem(ModItems.EMPTY_CANISTER.get()) == 2, "Finite refill did not conserve even in creative");
                helper.assertTrue(SpaceSuitOxygen.read(f.player.getItemBySlot(EquipmentSlot.CHEST)).oxygenUnits() == 0, "Tank refill edited active buffer");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "finite_canister", timeoutTicks = 100)
    public static void fullInventoryAndStalePreparationsRefuseWithoutDebitOrDrops(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            var service = service(); f.player.setItemSlot(EquipmentSlot.CHEST, chest(TankData.Gas.OXYGEN, 0));
            for (int i = 0; i < 36; i++) { f.player.getInventory().setItem(i, new ItemStack(Items.STONE, 64)); }
            held(f, TankData.Gas.OXYGEN, 2); var before = f.player.getItemBySlot(EquipmentSlot.CHEST).serializeNBT();
            int drops = f.level.getEntitiesOfClass(ItemEntity.class, f.bounds).size();
            helper.assertTrue(!service.fillTankCanister(f.player, InteractionHand.MAIN_HAND), "Full return inventory accepted stacked canister");
            for (int change = 0; change < 5; change++) {
                f.player.getInventory().setItem(1, ItemStack.EMPTY); held(f, TankData.Gas.OXYGEN, 2);
                var prepared = service.prepareTankCanister(f.player, InteractionHand.MAIN_HAND); helper.assertTrue(prepared != null, "Prepare failed");
                switch (change) {
                    case 0 -> f.player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                    case 1 -> f.player.getItemInHand(InteractionHand.MAIN_HAND).getOrCreateTag().putString("changed", "tag");
                    case 2 -> f.player.getInventory().setItem(1, new ItemStack(Items.STONE));
                    case 3 -> f.player.getInventory().selected = 2;
                    case 4 -> f.player.getItemBySlot(EquipmentSlot.CHEST).getOrCreateTag().putString("changed", "chest");
                    default -> throw new AssertionError();
                }
                helper.assertTrue(!service.commitTankCanister(f.player, prepared), "Changed state committed");
                f.player.getInventory().selected = 0; f.player.setItemSlot(EquipmentSlot.CHEST, ItemStack.of(before));
            }
            f.player.getInventory().setItem(1, ItemStack.EMPTY); held(f, TankData.Gas.OXYGEN, 2);
            var prepared = service.prepareTankCanister(f.player, InteractionHand.MAIN_HAND);
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try { helper.assertTrue(!service.commitTankCanister(f.player, prepared), "Changed configuration committed"); }
            finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
            helper.assertTrue(before.equals(f.player.getItemBySlot(EquipmentSlot.CHEST).serializeNBT())
                    && f.level.getEntitiesOfClass(ItemEntity.class, f.bounds).size() == drops, "Refusal debited or dropped a return");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "finite_canister", timeoutTicks = 100)
    public static void enchantedChestAcceptsOnlyOxygenActiveBufferAndUnsupportedRootsStayIntact(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            var service = service(); ItemStack enchanted = new ItemStack(Items.IRON_CHESTPLATE);
            enchanted.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1); f.player.setItemSlot(EquipmentSlot.CHEST, enchanted);
            held(f, TankData.Gas.HYDROGEN, 1); var before = enchanted.serializeNBT();
            helper.assertTrue(!service.fillTankCanister(f.player, InteractionHand.MAIN_HAND) && before.equals(enchanted.serializeNBT()), "Hydrogen edited enchanted chest");
            held(f, TankData.Gas.OXYGEN, 1); helper.assertTrue(service.fillTankCanister(f.player, InteractionHand.MAIN_HAND)
                    && EnchantedSuitOxygen.read(f.player.getItemBySlot(EquipmentSlot.CHEST).getTag()) == 1000, "Enchanted oxygen active refill failed");
            helper.assertTrue(!f.player.getItemBySlot(EquipmentSlot.CHEST).getTag().contains(ArmorRootCodec.KEY), "Enchanted chest acquired tanks");
            ItemStack future = chest(TankData.Gas.OXYGEN, 0); future.getTag().getCompound(ArmorRootCodec.KEY).putInt("schema_version", 2);
            f.player.setItemSlot(EquipmentSlot.CHEST, future); held(f, TankData.Gas.OXYGEN, 1); before = future.serializeNBT();
            helper.assertTrue(!service.fillTankCanister(f.player, InteractionHand.MAIN_HAND) && before.equals(future.serializeNBT()), "Unsupported root changed");
        }
        helper.succeed();
    }
}
