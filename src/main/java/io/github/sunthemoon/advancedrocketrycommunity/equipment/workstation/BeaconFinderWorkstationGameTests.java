package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-065 section 7 and ADR-066 section 5.1 through the actual suit workstation: the C17 finder installs into either
 * of the built-in helmet's two HEAD positions and competes for them with the C18 upgrades; a full helmet, a second
 * finder and a non-HEAD piece refuse without change; the module moves with its own tags; and the server equipment
 * summary, which alone decides the worn finder, follows wearing, a saved-and-reloaded helmet and unequipping.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeaconFinderWorkstationGameTests {
    private BeaconFinderWorkstationGameTests() {
    }

    private static ItemStack module(String id) {
        return SuitEquipmentContent.modules().get(id).get().getDefaultInstance();
    }

    private static void refused(EquipmentFixture f, String what) {
        CompoundTag armor = f.station.stack(0).serializeNBT();
        CompoundTag transfer = f.station.stack(1).serializeNBT();
        f.helper.assertTrue(!f.station.edit(f.player, -1, false), what + " was installed");
        f.helper.assertTrue(armor.equals(f.station.stack(0).serializeNBT())
                && transfer.equals(f.station.stack(1).serializeNBT()), what + ": the refusal changed a stack");
    }

    private static int position(ItemStack helmet, ModuleKind kind) {
        return ArmorEquipment.modules(helmet).stream().filter(module -> module.kind() == kind).findFirst()
                .orElseThrow().position();
    }

    @GameTest(template = "empty", batch = "beacon_finder_workstation", timeoutTicks = 100)
    public static void theFinderSharesTheTwoHeadPositionsThroughTheWorkstation(GameTestHelper helper) {
        try (EquipmentFixture f = new EquipmentFixture(helper)) {
            ItemStack finder = new ItemStack(ModItems.BEACON_FINDER.get());
            finder.getOrCreateTag().putString("foreign", "finder-owner");
            finder.enchant(Enchantments.UNBREAKING, 1);
            CompoundTag expected = finder.serializeNBT();

            // A non-HEAD piece refuses the finder.
            f.put(0, new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get()));
            f.put(1, finder.copy());
            refused(f, "A finder on a chestplate");
            f.clear();

            // The finder and a hover upgrade fill both HEAD positions; a third module and a second finder refuse.
            f.put(0, new ItemStack(ModItems.SPACE_SUIT_HELMET.get()));
            f.put(1, finder.copy());
            helper.assertTrue(f.station.edit(f.player, -1, false) && f.station.stack(1).isEmpty(),
                    "The finder did not install");
            f.put(1, module("hover_upgrade"));
            helper.assertTrue(f.station.edit(f.player, -1, false), "The hover upgrade did not share the helmet");
            helper.assertTrue(ArmorEquipment.modules(f.station.stack(0)).size() == 2, "Two HEAD positions not used");
            f.put(1, module("anti_fog_visor"));
            refused(f, "A third HEAD module");
            f.put(1, ItemStack.EMPTY);
            helper.assertTrue(f.station.edit(f.player, position(f.station.stack(0), ModuleKind.HOVER), false),
                    "The hover upgrade could not be removed");
            f.put(1, new ItemStack(ModItems.BEACON_FINDER.get()));
            refused(f, "A second finder");
            f.put(1, ItemStack.EMPTY);

            // The summary alone decides the worn finder: worn, saved and reloaded, and taken off.
            ItemStack helmet = f.station.stack(0).copy();
            helper.assertTrue(!BeaconFinderService.eligible(f.player), "The fixture player starts eligible");
            f.player.setItemSlot(EquipmentSlot.HEAD, helmet);
            helper.assertTrue(ArmorEquipment.summarize(f.player).beaconFinderEnabled()
                    && BeaconFinderService.eligible(f.player), "The worn finder is not enabled");
            f.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.of(helmet.save(new CompoundTag())));
            helper.assertTrue(ArmorEquipment.summarize(f.player).beaconFinderEnabled(), "A reloaded helmet lost it");
            f.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            helper.assertTrue(!ArmorEquipment.summarize(f.player).beaconFinderEnabled()
                    && !BeaconFinderService.eligible(f.player), "Unequipping kept the finder enabled");

            // Removal returns the same finder with its own tags and enchantment.
            helper.assertTrue(f.station.edit(f.player, position(f.station.stack(0), ModuleKind.BEACON_FINDER), false),
                    "The finder could not be removed");
            helper.assertTrue(expected.equals(f.station.stack(1).serializeNBT()), "The removed finder changed");
            helper.assertTrue(ArmorEquipment.modules(f.station.stack(0)).isEmpty(), "The helmet kept a finder copy");
        }
        helper.succeed();
    }
}
