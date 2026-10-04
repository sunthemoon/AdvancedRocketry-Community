package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicInventoryAdvancements.Goal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Connected server mocks exercise the real inventory menu listener, not grants or direct triggers. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicInventoryAdvancementGameTests {
    private ClassicInventoryAdvancementGameTests() { }

    @GameTest(template = "empty", batch = "classic_inventory", timeoutTicks = 100)
    public static void namedTargetsEarnThroughInventoryChangesAndReplayIsIdempotent(GameTestHelper helper) {
        ServerPlayer player = join(helper, "classicTargets");
        try {
            assertFresh(helper, player);
            inventory(player, List.of(new ItemStack(Items.COBBLESTONE)));
            assertFresh(helper, player);
            for (Goal goal : Goal.values()) {
                if (goal == Goal.SUITED_UP) {
                    continue;
                }
                Item target = item(goal.items().get(0));
                int experience = player.totalExperience;
                inventory(player, List.of(new ItemStack(target)));
                AdvancementProgress earned = progress(player, goal);
                helper.assertTrue(earned.isDone(), "Inventory listener did not earn " + goal.id());
                helper.assertTrue(player.getInventory().countItem(target) == 1,
                        "Tutorial consumed or rewarded its target " + goal.id());
                helper.assertTrue(player.totalExperience == experience, "Tutorial awarded experience " + goal.id());
                long timestamp = earnedAt(earned);
                inventory(player, List.of());
                helper.assertTrue(progress(player, goal).isDone(), "Removing items revoked tutorial progress");
                inventory(player, List.of(new ItemStack(target)));
                helper.assertTrue(earnedAt(progress(player, goal)) == timestamp,
                        "Replay changed the original criterion timestamp " + goal.id());
                helper.assertTrue(player.getInventory().countItem(target) == 1,
                        "Replay changed resources " + goal.id());
            }
            helper.assertTrue(!progress(player, Goal.SUITED_UP).isDone(), "Other targets earned the suit criterion");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_inventory", timeoutTicks = 100)
    public static void suitRequiresAllFourDistinctBuiltInPiecesInTheSameInventory(GameTestHelper helper) {
        ServerPlayer player = join(helper, "classicSuit");
        try {
            assertFresh(helper, player);
            for (int mask = 0; mask < 15; mask++) {
                List<ItemStack> partial = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    if ((mask & (1 << i)) != 0) {
                        partial.add(new ItemStack(item(Goal.SUITED_UP.items().get(i))));
                    }
                }
                inventory(player, List.of());
                inventory(player, partial);
                helper.assertTrue(!progress(player, Goal.SUITED_UP).isDone(),
                        "Partial or historical suit pieces earned the conjunction: " + mask);
            }
            Item helmet = item(Goal.SUITED_UP.items().get(0));
            inventory(player, List.of(new ItemStack(helmet), new ItemStack(helmet),
                    new ItemStack(helmet), new ItemStack(helmet)));
            helper.assertTrue(!progress(player, Goal.SUITED_UP).isDone(), "Duplicate helmets replaced distinct pieces");
            inventory(player, List.of(new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE),
                    new ItemStack(Items.LEATHER_LEGGINGS), new ItemStack(Items.LEATHER_BOOTS)));
            helper.assertTrue(!progress(player, Goal.SUITED_UP).isDone(), "Other armor matched built-in identities");

            List<ItemStack> complete = Goal.SUITED_UP.items().stream()
                    .map(id -> new ItemStack(item(id))).toList();
            int experience = player.totalExperience;
            inventory(player, complete);
            helper.assertTrue(progress(player, Goal.SUITED_UP).isDone(), "The complete current inventory did not earn suit");
            for (String id : Goal.SUITED_UP.items()) {
                helper.assertTrue(player.getInventory().countItem(item(id)) == 1, "Suit acquisition changed resources");
            }
            helper.assertTrue(player.totalExperience == experience, "Suit tutorial awarded experience");
            long timestamp = earnedAt(progress(player, Goal.SUITED_UP));
            inventory(player, List.of());
            helper.assertTrue(progress(player, Goal.SUITED_UP).isDone()
                    && earnedAt(progress(player, Goal.SUITED_UP)) == timestamp,
                    "Removing a completed suit changed the earned receipt");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_inventory", timeoutTicks = 100)
    public static void displayParentsDoNotGateAcquisitionAndPlayersRemainIndependent(GameTestHelper helper) {
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer first = join(helper, "classicFirst");
            joined.add(first);
            ServerPlayer other = join(helper, "classicOther");
            joined.add(other);
            assertFresh(helper, first);
            assertFresh(helper, other);
            Item warpCore = item(Goal.WARP_CORE.items().get(0));
            inventory(first, List.of(new ItemStack(warpCore)));
            helper.assertTrue(progress(first, Goal.WARP_CORE).isDone(), "Display ancestry blocked child acquisition");
            for (Goal goal : Goal.values()) {
                if (goal != Goal.WARP_CORE) {
                    helper.assertTrue(!progress(first, goal).isDone(), "Acquisition awarded a display parent");
                }
                helper.assertTrue(!progress(other, goal).isDone(), "Inventory progress leaked to another player");
            }
            inventory(other, List.of(new ItemStack(Items.CRAFTING_TABLE)));
            helper.assertTrue(progress(other, Goal.ROOT).isDone() && !progress(other, Goal.WARP_CORE).isDone(),
                    "The second player's criterion was not independent");
            helper.assertTrue(!progress(first, Goal.ROOT).isDone()
                    && first.getInventory().countItem(warpCore) == 1,
                    "Other player's event changed first player progress/resources");
        } finally {
            joined.forEach(player -> helper.getLevel().getServer().getPlayerList().remove(player));
        }
        helper.succeed();
    }

    private static ServerPlayer join(GameTestHelper helper, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name,
                helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)), new ArrayList<>());
    }

    private static void inventory(ServerPlayer player, List<ItemStack> stacks) {
        player.getInventory().clearContent();
        for (int i = 0; i < stacks.size(); i++) {
            player.getInventory().setItem(i, stacks.get(i));
        }
        // The normal menu diff calls ServerPlayer's registered vanilla ContainerListener.
        player.inventoryMenu.broadcastChanges();
    }

    private static void assertFresh(GameTestHelper helper, ServerPlayer player) {
        for (Goal goal : Goal.values()) {
            helper.assertTrue(!progress(player, goal).hasProgress(), "Fresh player already has " + goal.id());
        }
    }

    private static AdvancementProgress progress(ServerPlayer player, Goal goal) {
        Advancement advancement = player.getServer().getAdvancements().getAdvancement(new ResourceLocation(goal.id()));
        if (advancement == null || advancement.getDisplay() == null) {
            throw new IllegalStateException("Missing visible generated tutorial " + goal.id());
        }
        return player.getAdvancements().getOrStartProgress(advancement);
    }

    private static long earnedAt(AdvancementProgress progress) {
        Date obtained = progress.getCriterion(ClassicInventoryAdvancements.CRITERION).getObtained();
        if (obtained == null) {
            throw new IllegalStateException("Missing inventory criterion timestamp");
        }
        return obtained.getTime();
    }

    private static Item item(String id) {
        ResourceLocation key = new ResourceLocation(id);
        if (!ForgeRegistries.ITEMS.containsKey(key)) {
            throw new IllegalStateException("Missing registered inventory target " + id);
        }
        return ForgeRegistries.ITEMS.getValue(key);
    }
}
