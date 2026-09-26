package io.github.sunthemoon.arceadaptertest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuitEquipmentGameTests {
    private SuitEquipmentGameTests() { }

    @GameTest(templateNamespace = SuitEquipmentFixture.HOST, template = "atmosphere_test", timeoutTicks = 20)
    public static void equipmentEventIsDeliveredOnceAndClosedAfterLoading(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.suitEvents == 1 && AdapterTestMod.suitEvent != null,
                "Suit equipment MOD-bus event was not delivered exactly once");
        boolean rejected = false;
        try {
            AdapterTestMod.suitEvent.register(AdapterTestMod.id("late_suit"), FixtureSuitOxygen.ITEMS, 1, new FixtureSuitOxygen());
        } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Late equipment registration was accepted");
        helper.succeed();
    }

    @GameTest(templateNamespace = SuitEquipmentFixture.HOST, template = "atmosphere_test", timeoutTicks = 20)
    public static void externalSuitUsesProductionItemEventsAndNativeStackSerialization(GameTestHelper helper) {
        var moon = helper.getLevel().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.tryParse(SuitEquipmentFixture.HOST + ":moon")));
        helper.assertTrue(moon != null, "Moon is missing");
        var player = SuitEquipmentFixture.player(moon);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SuitEquipmentFixture.item("oxygen_canister"), 3));
            helper.assertTrue(SuitEquipmentFixture.refill(player), "Production canister use failed");
            SuitEquipmentFixture.ticks(player, 20);
            ItemStack armor = player.getItemBySlot(EquipmentSlot.CHEST);
            helper.assertTrue(SuitEquipmentFixture.units(armor) == 999 && player.getHealth() == 20,
                    "Production player listener did not debit exactly once and protect");
            CompoundTag saved = armor.save(new CompoundTag());
            ItemStack restored = ItemStack.of(saved.copy());
            helper.assertTrue(restored.save(new CompoundTag()).equals(saved), "Native item round-trip changed payload");
            player.setItemSlot(EquipmentSlot.CHEST, restored);
            SuitEquipmentFixture.ticks(player, 20);
            helper.assertTrue(SuitEquipmentFixture.units(restored) == 998, "Restored equipment did not continue consumption");
            helper.assertTrue(SuitEquipmentFixture.refill(player) && !SuitEquipmentFixture.refill(player)
                    && SuitEquipmentFixture.units(restored) == 1998 && SuitEquipmentFixture.canisters(player) == 1
                    && SuitEquipmentFixture.empties(player) == 2, "Capacity rejection or shell conservation failed");
        } finally {
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            player.getInventory().clearContent();
        }
        helper.succeed();
    }
}
