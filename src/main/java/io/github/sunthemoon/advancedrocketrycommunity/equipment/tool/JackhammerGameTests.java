package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JackhammerGameTests {
    private JackhammerGameTests() { }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void nativeTierTagsRepairAndSpeed(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            helper.assertTrue(f.item.getTier() == Tiers.DIAMOND && f.tool().getMaxDamage() == 1024
                    && f.tool().getMaxStackSize() == 1 && f.item.getEnchantmentValue() == 10, "Native tool identity differs");
            helper.assertTrue(f.tool().getDestroySpeed(Blocks.STONE.defaultBlockState()) == 50.0F
                    && f.tool().getDestroySpeed(Blocks.DIRT.defaultBlockState()) == 1.0F
                    && f.tool().canPerformAction(ToolActions.PICKAXE_DIG), "Tag speed/actions differ");
            helper.assertTrue(f.tool().isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
                    && !f.tool().isCorrectToolForDrops(Blocks.DIRT.defaultBlockState()), "Native qualification differs");
            helper.assertTrue(f.item.isValidRepairItem(f.tool(), new ItemStack(MaterialContent.item("titanium_rod")))
                    && !f.item.isValidRepairItem(f.tool(), new ItemStack(Items.DIAMOND))
                    && !f.item.isValidRepairItem(f.tool(), new ItemStack(MaterialContent.item("iron_rod"))), "Repair tag differs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void survivalNativeActionDropsOneTargetAndWearsOnce(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) { f.start(); f.stoneLootAndWear(1); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void nativeObsidianProgressIsNotInstant(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.setTarget(Blocks.OBSIDIAN);
            f.start(); f.tickProgress(2);
            f.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
            helper.assertTrue(f.level.getBlockState(f.target).is(Blocks.OBSIDIAN) && f.tool().getDamageValue() == 0
                    && f.drops().isEmpty(), "Hard target broke before native progress was sufficient");
            f.tickProgress(35);
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.tool().getDamageValue() == 1
                    && f.drops().size() == 1 && f.drops().get(0).getItem().is(Items.OBSIDIAN),
                    "Native delayed progress/diamond-qualified actual loot failed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void negativeHardnessAndOutsideReachNativeActionsRefuse(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.setTarget(Blocks.BEDROCK);
            CompoundTag before = f.tool().save(new CompoundTag());
            f.start(); f.tickProgress(5); f.action(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK);
            f.tickProgress(35); f.unchanged(Blocks.BEDROCK, before);
            f.action(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK);
            f.setTarget(Blocks.STONE);
            f.player.teleportTo(f.level, f.target.getX() - 20.0D, f.target.getY(), f.target.getZ(), 0, 0);
            f.start(); f.tickProgress(5); f.unchanged(Blocks.STONE, before);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void creativeNativeActionRemovesOnlyTargetWithoutWearOrLoot(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            CompoundTag before = f.tool().save(new CompoundTag());
            f.start();
            helper.assertTrue(f.level.getBlockState(f.target).isAir() && f.level.getBlockState(f.target.east()).is(Blocks.STONE)
                    && before.equals(f.tool().save(new CompoundTag())) && f.drops().isEmpty(), "Native creative semantics differ");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void finiteLastUseBreaksToolButRetainsQualifiedLoot(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            f.tool().setDamageValue(1023); f.start();
            helper.assertTrue(f.tool().isEmpty() && f.level.getBlockState(f.target).isAir()
                    && f.drops().size() == 1 && f.drops().get(0).getItem().is(Items.COBBLESTONE), "Native last-use boundary differs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void canceledForgeBreakPreservesToolAndTarget(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            Consumer<BlockEvent.BreakEvent> veto = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.target)) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, veto);
            try { CompoundTag before = f.tool().save(new CompoundTag()); f.start(); f.unchanged(Blocks.STONE, before); }
            finally { MinecraftForge.EVENT_BUS.unregister(veto); }
            f.start(); f.stoneLootAndWear(1); // Unregistered listener must not mask the admitted control.
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void installedUnavailableStationBuildDeniesActualToolBreak(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space level unavailable");
        var original = StationRegistrySavedData.get(server);
        CompoundTag bad = new CompoundTag(); bad.putInt("schema_version", 4);
        var blocked = StationRegistrySavedData.load(bad);
        helper.assertTrue(!blocked.operational(), "Invalid station fixture unexpectedly loaded");
        try (var f = new JackhammerFixture(helper, space)) {
            server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, blocked);
            try { CompoundTag before = f.tool().save(new CompoundTag()); f.start(); f.unchanged(Blocks.STONE, before); }
            finally { server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, original); }
            helper.assertTrue(blocked.save(new CompoundTag()).equals(bad), "Protection changed quarantined station data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void disabledFinalBreakHookSurvivesEventUncancelAndReenables(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            Consumer<BlockEvent.BreakEvent> uncancel = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.target)) { event.setCanceled(false); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, uncancel);
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                CompoundTag before = f.tool().save(new CompoundTag());
                helper.assertTrue(!f.player.gameMode.destroyBlock(f.target), "Disabled post-event hook admitted break");
                f.unchanged(Blocks.STONE, before);
                helper.assertTrue(!f.tool().canPerformAction(ToolActions.PICKAXE_DIG), "Disabled predicted tool action remained enabled");
                f.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                f.start(); f.unchanged(Blocks.STONE, before);
            } finally {
                SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED);
                MinecraftForge.EVENT_BUS.unregister(uncancel);
            }
            f.player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            f.start(); f.stoneLootAndWear(1);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void disabledNativeMeleeHasNoDamageWearAndReenables(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            var cow = EntityType.COW.create(f.level);
            helper.assertTrue(cow != null, "Cow fixture unavailable");
            cow.setNoAi(true); cow.setPos(f.target.getX() - 0.5D, f.target.getY(), f.target.getZ() + 1.5D);
            f.level.addFreshEntity(cow);
            try {
                float health = cow.getHealth(); CompoundTag before = f.tool().save(new CompoundTag());
                SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
                try {
                    f.player.attack(cow);
                    helper.assertTrue(cow.getHealth() == health && before.equals(f.tool().save(new CompoundTag())), "Disabled melee mutated target/tool");
                } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
                f.player.attack(cow);
                helper.assertTrue(cow.getHealth() < health && f.tool().getDamageValue() == 2, "Reenabled native melee/wear failed");
            } finally { cow.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void nativeAnvilRepairsConsumesRodAndXpEvenDisabled(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            var menu = new AnvilMenu(42, f.player.getInventory(), ContainerLevelAccess.NULL);
            f.player.experienceLevel = 30;
            ItemStack left = annotated(f.tool().copy(), 700);
            menu.getSlot(0).set(left); menu.getSlot(1).set(new ItemStack(Items.DIAMOND)); menu.createResult();
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Default diamond repair was admitted");
            menu.getSlot(1).set(new ItemStack(MaterialContent.item("iron_rod"))); menu.createResult();
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Wrong rod repair was admitted");
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
            try {
                menu.getSlot(1).set(new ItemStack(MaterialContent.item("titanium_rod"), 1)); menu.createResult();
                helper.assertTrue(menu.getSlot(2).getItem().getDamageValue() == 444, "One rod did not repair 256 damage");
                menu.clicked(2, 0, ClickType.PICKUP, f.player);
                ItemStack output = menu.getCarried();
                assertAnnotation(helper, output);
                helper.assertTrue(output.getDamageValue() == 444 && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(1).getItem().isEmpty() && f.player.experienceLevel == 29, "Native anvil take did not settle inputs/XP");
                ItemStack restored = ItemStack.of(output.save(new CompoundTag()));
                helper.assertTrue(restored.getItem() == f.item && restored.save(new CompoundTag()).equals(output.save(new CompoundTag())),
                        "Ordinary saved item data changed on decode");
            } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "jackhammer_native", timeoutTicks = 100)
    public static void nativeSameItemAndBookCombinationRetainLeftData(GameTestHelper helper) {
        try (var f = new JackhammerFixture(helper)) {
            var menu = new AnvilMenu(43, f.player.getInventory(), ContainerLevelAccess.NULL);
            menu.getSlot(0).set(annotated(f.tool().copy(), 800));
            ItemStack right = f.tool().copy(); right.setDamageValue(900);
            menu.getSlot(1).set(right); menu.createResult();
            ItemStack combined = menu.getSlot(2).getItem(); assertAnnotation(helper, combined);
            helper.assertTrue(combined.getDamageValue() < 800, "Same-item native repair failed");
            ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(Enchantments.BLOCK_EFFICIENCY, 3));
            menu.getSlot(1).set(book); menu.createResult();
            ItemStack enchanted = menu.getSlot(2).getItem();
            helper.assertTrue(!enchanted.isEmpty() && enchanted.getDamageValue() == 800
                    && "retain left".equals(enchanted.getTag().getString("fixture_note"))
                    && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, enchanted) == 3,
                    "Native book application or left-data semantics differ");
        }
        helper.succeed();
    }

    private static ItemStack annotated(ItemStack stack, int damage) {
        stack.setDamageValue(damage); stack.setHoverName(Component.literal("Owned tool"));
        stack.getOrCreateTag().putString("fixture_note", "retain left");
        EnchantmentHelper.setEnchantments(Map.of(Enchantments.BLOCK_EFFICIENCY, 2), stack);
        return stack;
    }

    private static void assertAnnotation(GameTestHelper helper, ItemStack stack) {
        helper.assertTrue(!stack.isEmpty() && "retain left".equals(stack.getTag().getString("fixture_note"))
                && stack.getHoverName().getString().equals("Owned tool")
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack) == 2,
                "Native anvil changed left name/enchantment/unrelated data");
    }
}
