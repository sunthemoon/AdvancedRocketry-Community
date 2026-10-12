package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Real inventory-menu changes, without grants or direct criterion-trigger calls. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicCrystallizerAdvancementGameTests {
    private ClassicCrystallizerAdvancementGameTests() { }

    @GameTest(template = "empty", batch = "classic_crystallizer_inventory", timeoutTicks = 20)
    public static void crystallizerRequiresTheRealControllerItem(GameTestHelper helper) {
        acquire(helper, Milestone.CRYSTALLIZER, "classicCrystal");
    }

    @GameTest(template = "empty", batch = "classic_dilithium_inventory", timeoutTicks = 20)
    public static void dilithiumRequiresTheRealCrystalItem(GameTestHelper helper) {
        acquire(helper, Milestone.DILITHIUM, "classicDilith");
    }

    private static void acquire(GameTestHelper helper, Milestone milestone, String name) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = ConnectedTestPlayers.join(server, UUID.randomUUID(), name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 2, 1)), new ArrayList<>());
        try {
            Advancement advancement = server.getAdvancements().getAdvancement(ResourceLocation.tryParse(milestone.id()));
            helper.assertTrue(advancement != null, "Missing " + milestone.id());
            var progress = player.getAdvancements().getOrStartProgress(advancement);
            helper.assertTrue(!progress.hasProgress(), "Fresh player already earned " + milestone.id());
            var wrong = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("advancedrocketrycommunity:silicon_boule"));
            var target = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(milestone.target()));
            helper.assertTrue(wrong != null && target != null, "Missing registered inventory fixture");
            inventory(player, new ItemStack(wrong));
            helper.assertTrue(!progress.isDone(), "Silicon boule earned " + milestone.id());
            inventory(player, new ItemStack(target));
            helper.assertTrue(progress.isDone(), "Real item did not earn " + milestone.id());
            helper.assertTrue(player.getInventory().countItem(target) == 1, "Advancement changed the target item");
            var obtained = progress.getCriterion(milestone.criterion()).getObtained();
            helper.assertTrue(obtained != null, "Missing acquisition timestamp");
            inventory(player, ItemStack.EMPTY);
            inventory(player, new ItemStack(target));
            helper.assertTrue(obtained.equals(progress.getCriterion(milestone.criterion()).getObtained()),
                    "Replay changed the acquisition timestamp");
            helper.assertTrue(!player.getAdvancements().getOrStartProgress(server.getAdvancements()
                    .getAdvancement(ResourceLocation.tryParse(Milestone.ONE_SMALL_STEP.id()))).isDone()
                    && !player.getAdvancements().getOrStartProgress(server.getAdvancements()
                    .getAdvancement(ResourceLocation.tryParse(Milestone.FIRST_STATION_WARP.id()))).isDone(),
                    "Inventory acquisition earned a pending world-first milestone");
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    private static void inventory(ServerPlayer player, ItemStack stack) {
        player.getInventory().clearContent();
        player.getInventory().setItem(0, stack);
        player.inventoryMenu.broadcastChanges();
    }
}
