package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportInput;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorRootCodec;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.EnchantedSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuitWorkstationGameTests {
    private static ItemStack armor(SuitPart part) {
        return new ItemStack(switch (part) {
            case HEAD -> ModItems.SPACE_SUIT_HELMET.get(); case CHEST -> ModItems.SPACE_SUIT_CHESTPLATE.get();
            case LEGS -> ModItems.SPACE_SUIT_LEGGINGS.get(); case FEET -> ModItems.SPACE_SUIT_BOOTS.get();
        });
    }
    private static ItemStack module(String id) { return SuitEquipmentContent.modules().get(id).get().getDefaultInstance(); }
    private static void install(EquipmentFixture f, ItemStack component) {
        f.put(1, component); f.helper.assertTrue(f.station.edit(f.player, -1, false), "Installation refused");
        f.helper.assertTrue(f.station.stack(1).isEmpty(), "Installation must consume the sole transfer component");
    }
    private static void unchanged(EquipmentFixture f, CompoundTag armor, CompoundTag transfer) {
        f.helper.assertTrue(armor.equals(f.station.stack(0).serializeNBT()) && transfer.equals(f.station.stack(1).serializeNBT()), "Refusal changed a held stack");
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void everyRegisteredModuleInstallsAndRemovesLosslessly(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            for (ModuleKind kind : ModuleKind.values()) {
                if (kind == ModuleKind.BEACON_FINDER) { continue; }
                for (String id : kind.itemPaths()) {
                    f.clear(); ItemStack suit = armor(kind.part()); suit.setDamageValue(4);
                    suit.enchant(Enchantments.UNBREAKING, 1); suit.getOrCreateTag().putString("unrelated", "armor-owner");
                    if (kind.part() == SuitPart.CHEST) { helper.assertTrue(SpaceSuitOxygen.set(suit, 1234), "Active oxygen setup"); }
                    CompoundTag original = suit.getTag().copy();
                    ItemStack component = module(id); component.getOrCreateTag().putString("foreign", "module-owner");
                    component.getOrCreateTag().putInt("Damage", 13); component.enchant(Enchantments.UNBREAKING, 1);
                    if (kind == ModuleKind.PRESSURE_TANK) {
                        TankData tank = new TankData(TankData.Gas.HYDROGEN, 777,
                                TankData.baseCapacity(ModIdentity.MOD_ID + ":" + id) * CommonConfig.suitTankMultiplier());
                        component.getOrCreateTag().put(TankData.KEY, tank.encode());
                    }
                    CompoundTag expected = component.serializeNBT(); f.put(0, suit); install(f, component);
                    var installed = ArmorEquipment.modules(f.station.stack(0));
                    helper.assertTrue(installed.size() == 1 && installed.get(0).stack().equals(expected), "Installed module identity changed: " + id);
                    helper.assertTrue(f.station.edit(f.player, installed.get(0).position(), false), "Removal refused: " + id);
                    helper.assertTrue(expected.equals(f.station.stack(1).serializeNBT()), "Removed component changed: " + id);
                    for (String key : original.getAllKeys()) { helper.assertTrue(original.get(key).equals(f.station.stack(0).getTag().get(key)), "Armor metadata changed: " + key); }
                    helper.assertTrue(ArmorEquipment.modules(f.station.stack(0)).isEmpty(), "Removal retained a module copy");
                }
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void fullLayoutAndFullInventoryRefusalsAreAtomic(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.put(0, armor(SuitPart.HEAD)); install(f, module("hover_upgrade")); install(f, module("anti_fog_visor"));
            f.put(1, module("earthbright_visor")); var before = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Full HEAD accepted a third module"); unchanged(f, before, transfer);
            helper.assertTrue(!f.station.edit(f.player, 0, false), "Occupied transfer accepted extraction"); unchanged(f, before, transfer);
            f.put(1, ItemStack.EMPTY); transfer = f.station.stack(1).serializeNBT();
            for (int i = 0; i < 36; i++) { f.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); }
            ListTag inventory = f.player.getInventory().save(new ListTag());
            helper.assertTrue(!f.station.edit(f.player, 0, true), "Full player inventory accepted removal"); unchanged(f, before, transfer);
            helper.assertTrue(inventory.equals(f.player.getInventory().save(new ListTag())), "Full-inventory refusal changed inventory");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void duplicateTypesAndGasLimitsRefuseWithoutMutation(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.put(0, armor(SuitPart.LEGS)); install(f, module("flight_speed_upgrade")); f.put(1, module("flight_speed_upgrade"));
            var before = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Duplicate upgrade accepted"); unchanged(f, before, transfer);
            f.clear(); f.put(0, armor(SuitPart.CHEST)); ItemStack oxygen = module("pressure_tank");
            oxygen.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 1000, 2000).encode()); install(f, oxygen);
            f.put(1, oxygen); before = f.station.stack(0).serializeNBT(); transfer = f.station.stack(1).serializeNBT();
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Two oxygen reserves accepted"); unchanged(f, before, transfer);
            ItemStack hydrogen = module("pressure_tank"); hydrogen.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.HYDROGEN, 900, 2000).encode());
            install(f, hydrogen); install(f, module("jetpack")); f.put(1, module("low_pressure_tank"));
            before = f.station.stack(0).serializeNBT(); transfer = f.station.stack(1).serializeNBT();
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Third pressure tank accepted"); unchanged(f, before, transfer);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void vanillaShiftClickCommitsDetachedCopiesAndRefusesFullInventory(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            ItemStack suit = armor(SuitPart.HEAD); suit.setDamageValue(8); f.player.getInventory().setItem(9, suit);
            f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.player);
            helper.assertTrue(f.player.getInventory().getItem(9).isEmpty() && f.station.stack(0).getDamageValue() == 8, "Shift-click insertion failed");
            f.menu.getSlot(0).getItem().setDamageValue(25);
            helper.assertTrue(f.station.stack(0).getDamageValue() == 8, "Slot view leaked a live stack");
            f.player.getInventory().setItem(9, module("hover_upgrade")); f.menu.clicked(2, 0, ClickType.QUICK_MOVE, f.player);
            helper.assertTrue(f.station.stack(1).is(module("hover_upgrade").getItem()) && f.player.getInventory().getItem(9).isEmpty(), "Module shift-click failed");
            for (int i = 0; i < 36; i++) { f.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); }
            var before = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player); unchanged(f, before, transfer);
            f.player.getInventory().setItem(35, ItemStack.EMPTY); f.menu.clicked(0, 0, ClickType.QUICK_MOVE, f.player);
            helper.assertTrue(f.station.stack(0).isEmpty() && before.equals(f.player.getInventory().getItem(35).serializeNBT()), "Detached shift-click lost armor metadata");
            helper.assertTrue(!f.station.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Workstation exposes automation");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void ordinaryPickupAndSwapUseDetachedAuthoritativeSlots(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            ItemStack suit = armor(SuitPart.HEAD); f.menu.setCarried(suit); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            helper.assertTrue(f.menu.getCarried().isEmpty() && f.station.stack(0).is(suit.getItem()), "Ordinary insertion lost a carried stack");
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            helper.assertTrue(f.station.stack(0).isEmpty() && f.menu.getCarried().is(suit.getItem()), "Ordinary extraction duplicated armor");
            f.menu.setCarried(module("hover_upgrade")); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            helper.assertTrue(f.station.stack(0).isEmpty() && f.menu.getCarried().is(module("hover_upgrade").getItem()), "Incompatible click changed carried stack");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void breakingDropsEachHeldStackExactlyOnce(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.put(0, armor(SuitPart.CHEST)); install(f, module("jetpack")); f.put(1, module("hover_upgrade"));
            var before = f.station.stack(0).serializeNBT();
            helper.assertTrue(f.level.destroyBlock(f.position, true), "Workstation did not break"); f.station.dropContents();
            var drops = f.level.getEntitiesOfClass(ItemEntity.class, f.bounds).stream().filter(e -> !f.initialDrops.contains(e.getUUID())).toList();
            helper.assertTrue(drops.stream().filter(e -> e.getItem().is(ModItems.SPACE_SUIT_CHESTPLATE.get())).mapToInt(e -> e.getItem().getCount()).sum() == 1, "Armor drop count differs");
            helper.assertTrue(drops.stream().anyMatch(e -> before.equals(e.getItem().serializeNBT())), "Broken armor lost installed module");
            helper.assertTrue(drops.stream().filter(e -> e.getItem().is(module("hover_upgrade").getItem())).mapToInt(e -> e.getItem().getCount()).sum() == 1, "Module drop count differs");
            helper.assertTrue(drops.stream().filter(e -> e.getItem().is(SuitEquipmentContent.WORKSTATION.get())).mapToInt(e -> e.getItem().getCount()).sum() == 1, "Block drop count differs");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void equippedSuitAndWorkstationSurviveNativeSaveLoad(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.put(0, armor(SuitPart.CHEST)); ItemStack tank = module("super_high_pressure_tank");
            tank.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 7123, 8000).encode()); install(f, tank);
            f.put(1, module("hover_upgrade")); var held = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            var saved = f.station.saveWithoutMetadata(); f.station.load(saved); unchanged(f, held, transfer);
            f.player.setItemSlot(EquipmentSlot.CHEST, f.station.stack(0));
            CompoundTag playerSave = f.player.saveWithoutId(new CompoundTag());
            ServerPlayer loaded = new ServerPlayer(f.level.getServer(), f.level, f.player.getGameProfile()); loaded.load(playerSave);
            helper.assertTrue(held.equals(loaded.getItemBySlot(EquipmentSlot.CHEST).serializeNBT()), "Equipped modules or gas did not survive native player load");
            ItemStack helmet = ArmorEquipment.install(armor(SuitPart.HEAD), module("hover_upgrade")).armor();
            f.player.setItemSlot(EquipmentSlot.HEAD, helmet);
            helper.assertTrue(ArmorEquipment.summarize(f.player).installed().contains(ModuleKind.HOVER), "Worn module omitted from summary");
            f.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, helmet);
            helper.assertTrue(!ArmorEquipment.summarize(f.player).installed().contains(ModuleKind.HOVER), "Unequipped module retained in summary");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void reserveQueriesArePureAndScheduledDebitIsConservative(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            ItemStack chest = armor(SuitPart.CHEST); SpaceSuitOxygen.set(chest, 0);
            ItemStack tank = module("super_high_pressure_tank"); tank.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 7000, 8000).encode());
            chest = ArmorEquipment.install(chest, tank).armor(); f.player.setItemSlot(EquipmentSlot.CHEST, chest);
            var service = new SuitEquipmentService(SuitEquipmentCatalog.empty()); var before = chest.serializeNBT();
            var reading = service.readOxygen(f.player); ArmorEquipment.reserve(chest); ArmorEquipment.summarize(f.player);
            helper.assertTrue(before.equals(chest.serializeNBT()), "Query refilled the active oxygen buffer");
            var decision = service.decideAndCommit(f.player, reading, new PlayerLifeSupportInput(false, BreathabilityState.VACUUM, 4, 0, 19));
            helper.assertTrue(decision.oxygenUnits() == CommonConfig.suitOxygenTarget() - 1
                    && ArmorEquipment.reserve(chest).stored() == 7000 - CommonConfig.suitOxygenTarget()
                    && decision.damage() == 0, "Scheduled debit did not conserve oxygen minus one paid unit");
            service.clear();
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void enchantedNativeArmorSealsOnlyWhenWornAndHasFiniteOxygen(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            var service = new SuitEquipmentService(SuitEquipmentCatalog.empty());
            List<ItemStack> pieces = List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS));
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) { f.player.setItemSlot(slots[i], pieces.get(i)); }
            helper.assertTrue(service.countPieces(f.player) == 0, "Unenchanted native armor sealed suit");
            pieces.forEach(stack -> stack.enchant(SuitEquipmentContent.SPACE_BREATHING.get(), 1));
            helper.assertTrue(service.countPieces(f.player) == 4, "Correctly worn enchanted armor not recognized");
            helper.assertTrue(EnchantedSuitOxygen.set(pieces.get(1), 2), "Native oxygen setup refused");
            var paid = service.decideAndCommit(f.player, service.readOxygen(f.player), new PlayerLifeSupportInput(false, BreathabilityState.VACUUM, 4, 2, 19));
            helper.assertTrue(paid.oxygenUnits() == 1 && EnchantedSuitOxygen.read(pieces.get(1).getTag()) == 1, "Enchantment granted unbounded oxygen");
            var enchant = SuitEquipmentContent.SPACE_BREATHING.get();
            helper.assertTrue(enchant.getMaxLevel() == 1 && !enchant.isAllowedOnBooks() && !enchant.isDiscoverable()
                    && !enchant.isTradeable() && !enchant.canApplyAtEnchantingTable(pieces.get(1)), "Restricted enchantment acquisition changed");
            helper.assertTrue(ArmorEquipment.install(pieces.get(1), module("jetpack")) == null, "Native enchanted armor accepted modules");
            service.clear();
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void invalidPendingOrFutureRootsAndDisabledInstallRefuse(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            ItemStack suit = armor(SuitPart.HEAD); CompoundTag future = new CompoundTag(); future.putInt("schema_version", 2); future.putString("pending", "retain");
            suit.getOrCreateTag().put(ArmorRootCodec.KEY, future); f.put(0, suit); f.put(1, module("hover_upgrade"));
            var before = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Unsupported equipment root accepted an edit"); unchanged(f, before, transfer);
            f.put(0, armor(SuitPart.HEAD)); install(f, module("hover_upgrade"));
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                f.put(1, module("anti_fog_visor")); before = f.station.stack(0).serializeNBT(); transfer = f.station.stack(1).serializeNBT();
                helper.assertTrue(!f.station.edit(f.player, -1, false), "Disabled equipment accepted install"); unchanged(f, before, transfer);
                f.put(1, ItemStack.EMPTY); helper.assertTrue(f.station.edit(f.player, 0, false), "Disabled equipment blocked safe withdrawal");
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void distantStaleAndWrongMenuIntentsHaveNoAuthority(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            f.put(0, armor(SuitPart.HEAD)); f.put(1, module("hover_upgrade"));
            var before = f.station.stack(0).serializeNBT(); var transfer = f.station.stack(1).serializeNBT();
            f.player.containerMenu = f.player.inventoryMenu;
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Wrong container edited armor"); unchanged(f, before, transfer);
            f.player.containerMenu = f.menu; f.player.setPos(f.position.getX() + 20, f.position.getY(), f.position.getZ());
            helper.assertTrue(!f.station.edit(f.player, -1, false), "Distant player edited armor"); unchanged(f, before, transfer);
            f.player.setPos(f.position.getX() + .5, f.position.getY() + 1, f.position.getZ() + .5);
            helper.assertTrue(!f.menu.clickMenuButton(f.player, 7), "Unknown selection accepted"); unchanged(f, before, transfer);
            f.station.setRemoved(); helper.assertTrue(!f.station.edit(f.player, -1, false), "Removed station edited armor"); unchanged(f, before, transfer);
            f.station.clearRemoved();
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "equipment_workstation", timeoutTicks = 100)
    public static void unsupportedWorkstationRootIsPreservedAndOrdinaryBreakRefuses(GameTestHelper helper) {
        try (var f = new EquipmentFixture(helper)) {
            CompoundTag future = new CompoundTag(); future.putInt("schema_version", 2); future.putString("future_inventory", "retain");
            CompoundTag input = new CompoundTag(); input.put(SuitWorkstationSave.KEY, future); f.station.load(input);
            helper.assertTrue(f.station.repairRequired() && !f.station.edit(f.player, -1, false), "Future station granted authority");
            helper.assertTrue(future.equals(f.station.saveWithoutMetadata().get(SuitWorkstationSave.KEY)), "Unsupported station save erased raw data");
            helper.assertTrue(!SuitEquipmentContent.BLOCK.get().onDestroyedByPlayer(f.level.getBlockState(f.position), f.level,
                    f.position, f.player, true, f.level.getFluidState(f.position)), "Ordinary break destroyed quarantined root");
            // Restore only this disposable fixture, not a production repair mechanism.
            f.station.load(new CompoundTag());
        }
        helper.succeed();
    }
}
