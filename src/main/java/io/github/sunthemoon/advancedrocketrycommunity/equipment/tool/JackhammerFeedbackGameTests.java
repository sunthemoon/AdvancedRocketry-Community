package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JackhammerFeedbackGameTests {
    private JackhammerFeedbackGameTests() { }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void disabledHardTargetNotifiesOnStartNotProgressStopOrAbort(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.setTarget(Blocks.OBSIDIAN);
            helper.assertTrue(f.disabledStartFeedback() == 0, "Fixture unexpectedly emitted disabled feedback");
            CompoundTag before = f.tool().save(new CompoundTag());
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                f.start();
                helper.assertTrue(f.disabledStartFeedback() == 1, "Hard target did not immediately send one disabled action bar");
                f.unchanged(Blocks.OBSIDIAN, before);
                f.tickProgress(3);
                f.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
                f.action(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
                helper.assertTrue(f.disabledStartFeedback() == 0, "Progress/STOP/ABORT repeated start feedback");
                f.start();
                helper.assertTrue(f.disabledStartFeedback() == 1, "Second native START did not send exactly one notice");
                f.unchanged(Blocks.OBSIDIAN, before);
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void disabledCreativeNotifiesWithoutReplacingFinalRefusal(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            f.disabledStartFeedback();
            CompoundTag before = f.tool().save(new CompoundTag());
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                f.start();
                helper.assertTrue(f.disabledStartFeedback() == 1, "Disabled creative START did not send one action bar");
                f.unchanged(Blocks.STONE, before);
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void enabledNativeMiningHasNoDisabledNotification(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.disabledStartFeedback();
            f.start();
            f.stoneLootAndWear(1);
            helper.assertTrue(f.disabledStartFeedback() == 0, "Enabled tool emitted disabled feedback");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void ordinaryMainHandWithOffhandJackhammerIsUnaffected(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            f.player.setItemInHand(InteractionHand.OFF_HAND, f.tool().copy());
            f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            f.disabledStartFeedback();
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                f.start();
                helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 0
                        && f.player.getOffhandItem().getDamageValue() == 0 && f.drops().isEmpty(),
                        "Disabled offhand jackhammer changed ordinary creative mining");
                helper.assertTrue(f.disabledStartFeedback() == 0, "Ordinary main-hand tool emitted jackhammer feedback");
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void nativeCanceledStartKeepsEventResultsAndStillNotifies(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            var receipt = new AtomicReference<PlayerInteractEvent.LeftClickBlock>();
            Consumer<PlayerInteractEvent.LeftClickBlock> veto = event -> {
                if (event.getEntity() == f.player) {
                    event.setCanceled(true);
                    event.setResult(Event.Result.DENY);
                    receipt.set(event);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.LeftClickBlock.class, veto);
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                f.disabledStartFeedback();
                CompoundTag before = f.tool().save(new CompoundTag());
                f.start();
                var event = receipt.get();
                helper.assertTrue(event != null && event.isCanceled() && event.getResult() == Event.Result.DENY
                        && event.getUseItem() == Event.Result.DENY && event.getUseBlock() == Event.Result.DENY,
                        "Informational listener changed native event results");
                helper.assertTrue(f.disabledStartFeedback() == 1, "Canceled native START did not send one disabled notice");
                f.unchanged(Blocks.STONE, before);
            } finally {
                SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED);
                MinecraftForge.EVENT_BUS.unregister(veto);
            }
        }
        helper.succeed();
    }
}
