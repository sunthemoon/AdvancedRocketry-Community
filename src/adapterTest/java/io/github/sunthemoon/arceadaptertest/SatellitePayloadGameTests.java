package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatelliteMissionDefinition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatellitePayloadGameTests {
    private SatellitePayloadGameTests() { }

    @GameTest(templateNamespace = SatellitePayloadFixture.HOST, template = "empty", timeoutTicks = 20)
    public static void registrationIsDeliveredOnceAndThenExpires(GameTestHelper helper) {
        helper.assertTrue(SatellitePayloadFixture.events == 1 && SatellitePayloadFixture.retained != null, "Satellite event missing/repeated");
        boolean rejected = false;
        try {
            SatellitePayloadFixture.retained.register(AdapterTestMod.id("late"), SatellitePayloadFixture.host("data_storage_unit"),
                    new SatelliteMissionDefinition(20, 1, 1, List.of(SatellitePayloadFixture.host("earth"))));
        } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Retained satellite event remained writable"); helper.succeed();
    }

    @GameTest(templateNamespace = SatellitePayloadFixture.HOST, template = "empty", batch = "satellite_payload", timeoutTicks = 460)
    public static void registeredPayloadIsManufacturedLaunchedAndClaimedExactlyOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var terminal = SatellitePayloadFixture.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), owner);
        var inventory = terminal.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        var player = SatellitePayloadFixture.player(terminal, owner);
        var menu = ((MenuProvider) terminal).createMenu(1, player.getInventory(), player);
        var research = new ResearchView(menu);
        player.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(player, 2), "Assembly intent rejected");
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty()
                && inventory.getStackInSlot(2).getCount() == 1, "Components were not consumed exactly once");
        CompoundTag chip = inventory.getStackInSlot(3).getTag().getCompound("SatelliteIdentity");
        helper.assertTrue(chip.getString("definition_id").equals(SatellitePayloadFixture.ID.toString()), "Wrong payload identity");
        helper.assertTrue(chip.equals(inventory.getStackInSlot(4).getTag().getCompound("SatelliteIdentity")), "Package/chip mismatch");
        helper.assertTrue(SatellitePayloadFixture.root(terminal).getInt("energy") == 9000, "Assembly energy differs");
        // Discovery is world-wide; snapshot it before launch rather than depend on other test batches.
        int expectedReward = research.targetDiscovered() ? 137 : 126;
        // ADR-049 section 10 (C8a): a player's state-changing intents are spaced by 10 ticks.
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(menu.clickMenuButton(player, 3) && inventory.getStackInSlot(4).isEmpty(), "Actual launch did not consume package");
            int before = research.balance();
            var intruder = SatellitePayloadFixture.player(terminal, UUID.randomUUID());
            helper.assertTrue(!menu.clickMenuButton(intruder, 4), "Wrong owner could use menu");
            helper.runAtTickTime(430, () -> {
                helper.assertTrue(menu.clickMenuButton(player, 4), "Claim intent rejected");
                helper.assertTrue(research.balance() == before + expectedReward, "Registered research reward differs");
                helper.runAtTickTime(441, () -> {
                    helper.assertTrue(menu.clickMenuButton(player, 4), "Replayed claim intent was not processed");
                    helper.assertTrue(research.balance() == before + expectedReward, "Replay duplicated reward");
                    var saved = terminal.saveWithoutMetadata(); terminal.load(saved);
                    helper.assertTrue(saved.equals(terminal.saveWithoutMetadata()), "Native terminal save/load changed completed identity");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = SatellitePayloadFixture.HOST, template = "empty", timeoutTicks = 20)
    public static void taggedPayloadIsRejectedAndShiftClickUsesRegisteredSlot(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var terminal = SatellitePayloadFixture.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), owner);
        var handler = terminal.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        handler.extractItem(2, 64, false);
        ItemStack tagged = new ItemStack(Items.AMETHYST_SHARD); tagged.getOrCreateTag().putString("payload", "tagged");
        helper.assertTrue(!handler.insertItem(2, tagged, false).isEmpty(), "Tagged payload accepted");
        var player = SatellitePayloadFixture.player(terminal, owner);
        player.getInventory().setItem(9, new ItemStack(Items.AMETHYST_SHARD, 3));
        var menu = ((MenuProvider) terminal).createMenu(1, player.getInventory(), player);
        menu.quickMoveStack(player, 6);
        helper.assertTrue(handler.getStackInSlot(2).getCount() == 3 && player.getInventory().getItem(9).isEmpty(), "Shift-click ignored payload");
        helper.succeed();
    }

    private static final class ResearchView implements net.minecraft.world.inventory.ContainerListener {
        private final net.minecraft.world.inventory.AbstractContainerMenu menu;
        private final int[] values = new int[12];
        private ResearchView(net.minecraft.world.inventory.AbstractContainerMenu menu) {
            this.menu = menu; menu.addSlotListener(this);
        }
        @Override public void slotChanged(net.minecraft.world.inventory.AbstractContainerMenu ignored, int slot, ItemStack stack) { }
        @Override public void dataChanged(net.minecraft.world.inventory.AbstractContainerMenu ignored, int index, int value) { values[index] = value; }
        private boolean targetDiscovered() {
            menu.broadcastChanges();
            return values[8] == 1;
        }
        private int balance() {
            // Retain the same observer: unchanged values are not rebroadcast to newly added listeners.
            menu.broadcastChanges();
            return (values[5] & 0xffff) | (values[10] & 0xffff) << 16;
        }
    }
}
