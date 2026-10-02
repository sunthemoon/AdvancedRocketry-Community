package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressBlock;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressBlock.PressResult;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-063 section 3 and 9 (A1) on a running server: the small plate press. Each test builds obsidian, the block to
 * press and the press in a column, and powers the press with a redstone block beside it.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialGameTests {
    private static final String SWITCH_BATCH = "materials_switches";
    private static final BlockPos ANVIL = new BlockPos(2, 1, 2);
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    private static final BlockPos PRESS = new BlockPos(2, 3, 2);
    private static final BlockPos POWER = new BlockPos(3, 3, 2);

    private MaterialGameTests() {
    }

    @AfterBatch(batch = SWITCH_BATCH)
    public static void restoreSwitches(ServerLevel level) {
        CommonConfig.SMALL_PLATE_PRESS_ENABLED.set(true);
        CommonConfig.OVERWORLD_ORES_ENABLED.set(true);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void aPulsePressesAStorageBlockIntoFourPlates(GameTestHelper helper) {
        column(helper, 0, Blocks.OBSIDIAN, MaterialContent.block("titanium_block"));
        column(helper, 4, Blocks.OBSIDIAN, Blocks.IRON_BLOCK);
        column(helper, 8, Blocks.OBSIDIAN, Blocks.COPPER_BLOCK);
        power(helper, 0);
        power(helper, 4);
        power(helper, 8);
        helper.succeedWhen(() -> {
            assertPressed(helper, 0, MaterialContent.item("titanium_plate"), 4);
            assertPressed(helper, 4, MaterialContent.item("iron_plate"), 4);
            assertPressed(helper, 8, MaterialContent.item("copper_plate"), 4);
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void aPulsePressesAnOreIntoTwoDust(GameTestHelper helper) {
        column(helper, 0, Blocks.OBSIDIAN, MaterialContent.block("tin_ore"));
        column(helper, 3, Blocks.OBSIDIAN, MaterialContent.block("deepslate_aluminum_ore"));
        column(helper, 6, Blocks.OBSIDIAN, Blocks.IRON_ORE);
        column(helper, 9, Blocks.OBSIDIAN, MaterialContent.block("dilithium_ore"));
        column(helper, 12, Blocks.OBSIDIAN, MaterialContent.block("deepslate_rutile_ore"));
        // Rutile is a titanium ore, but titanium comes only from the electric arc furnace (ADR-063 section 1).
        helper.assertTrue(SmallPlatePressBlock.press(helper.getLevel(), at(helper, 12, PRESS)) == PressResult.NO_RECIPE,
                "Rutile pressed into dust");
        for (int x : new int[] {0, 3, 6, 9, 12}) {
            power(helper, x);
        }
        helper.succeedWhen(() -> {
            assertPressed(helper, 0, MaterialContent.item("tin_dust"), 2);
            assertPressed(helper, 3, MaterialContent.item("aluminum_dust"), 2);
            assertPressed(helper, 6, MaterialContent.item("iron_dust"), 2);
            assertPressed(helper, 9, MaterialContent.item("dilithium_dust"), 2);
            helper.assertBlockPresent(MaterialContent.block("deepslate_rutile_ore"), TARGET.offset(12, 0, 0));
            helper.assertTrue(items(helper, 12).isEmpty(), "Rutile was pressed");
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void withoutObsidianOrWithABlockEntityNothingIsPressed(GameTestHelper helper) {
        column(helper, 0, Blocks.STONE, MaterialContent.block("tin_block"));
        column(helper, 4, Blocks.OBSIDIAN, Blocks.CHEST);
        column(helper, 8, Blocks.OBSIDIAN, Blocks.BEDROCK);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(SmallPlatePressBlock.press(level, at(helper, 0, PRESS)) == PressResult.NO_OBSIDIAN,
                "A press over stone acted");
        helper.assertTrue(SmallPlatePressBlock.press(level, at(helper, 4, PRESS)) == PressResult.NO_INPUT,
                "A block with a block entity was taken as input");
        helper.assertTrue(SmallPlatePressBlock.press(level, at(helper, 8, PRESS)) == PressResult.NO_INPUT,
                "An unbreakable block was taken as input");
        power(helper, 0);
        power(helper, 4);
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(MaterialContent.block("tin_block"), TARGET);
            helper.assertBlockPresent(Blocks.CHEST, TARGET.offset(4, 0, 0));
            helper.assertTrue(items(helper, 0).isEmpty() && items(helper, 4).isEmpty(), "Something was pressed");
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 60)
    public static void onlyARisingEdgePresses(GameTestHelper helper) {
        column(helper, 0, Blocks.OBSIDIAN, MaterialContent.block("steel_block"));
        Block steel = MaterialContent.block("steel_block");
        Item plate = MaterialContent.item("steel_plate");
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    helper.assertBlockPresent(steel, TARGET);
                    helper.assertTrue(items(helper, 0).isEmpty(), "An unpowered press acted");
                    power(helper, 0);
                })
                .thenExecuteAfter(2, () -> {
                    assertPressed(helper, 0, plate, 4);
                    // Clear the plates (a block placed on them would push them away), then, still powered, a new
                    // block below waits for the next rising edge.
                    items(helper, 0).forEach(ItemEntity::discard);
                    helper.setBlock(TARGET, steel);
                })
                .thenExecuteAfter(10, () -> {
                    helper.assertBlockPresent(steel, TARGET);
                    helper.assertTrue(items(helper, 0).isEmpty(), "A held signal pressed again");
                    helper.setBlock(POWER, Blocks.AIR);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertBlockPresent(steel, TARGET);
                    power(helper, 0);
                })
                .thenExecuteAfter(2, () -> assertPressed(helper, 0, plate, 4))
                .thenSucceed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void anUnloadedTargetIsNeitherPressedNorLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos far = helper.absolutePos(PRESS).offset(16 * 4_000, 0, 16 * 4_000);
        ChunkPos chunk = new ChunkPos(far);
        helper.assertFalse(level.getChunkSource().hasChunk(chunk.x, chunk.z), "The far chunk was already loaded");
        helper.assertTrue(SmallPlatePressBlock.press(level, far) == PressResult.UNLOADED,
                "A press in an unloaded chunk acted");
        helper.assertFalse(level.getChunkSource().hasChunk(chunk.x, chunk.z), "The press loaded a chunk");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void aCancelledPistonEventLeavesTheBlock(GameTestHelper helper) {
        column(helper, 0, Blocks.OBSIDIAN, MaterialContent.block("aluminum_block"));
        BlockPos press = at(helper, 0, PRESS);
        Consumer<PistonEvent.Pre> claim = event -> {
            if (event.getPos().equals(press)) {
                event.setCanceled(true);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PistonEvent.Pre.class, claim);
        try {
            helper.assertTrue(SmallPlatePressBlock.press(helper.getLevel(), press) == PressResult.CANCELLED,
                    "A cancelled piston event did not stop the press");
            power(helper, 0);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(claim);
        }
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(MaterialContent.block("aluminum_block"), TARGET);
            helper.assertTrue(items(helper, 0).isEmpty(), "A cancelled press made plates");
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = SWITCH_BATCH, timeoutTicks = 40)
    public static void theServerSwitchTurnsThePressOff(GameTestHelper helper) {
        column(helper, 0, Blocks.OBSIDIAN, MaterialContent.block("iridium_block"));
        CommonConfig.SMALL_PLATE_PRESS_ENABLED.set(false);
        helper.assertTrue(SmallPlatePressBlock.press(helper.getLevel(), at(helper, 0, PRESS)) == PressResult.DISABLED,
                "A disabled press acted");
        power(helper, 0);
        helper.runAfterDelay(5, () -> {
            helper.assertBlockPresent(MaterialContent.block("iridium_block"), TARGET);
            helper.assertTrue(items(helper, 0).isEmpty(), "A disabled press made plates");
            CommonConfig.SMALL_PLATE_PRESS_ENABLED.set(true);
            helper.succeed();
        });
    }

    /** C15aR1-M1: the dilithium ores drop themselves, so breaking one must give no experience (no XP farm). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dilithiumOreDropsItselfWithoutExperience(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        for (String id : java.util.List.of("dilithium_ore", "deepslate_dilithium_ore")) {
            Block ore = MaterialContent.block(id);
            var state = ore.defaultBlockState();
            var drops = Block.getDrops(state, level, position, null, null, pickaxe);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(ore.asItem()) && drops.get(0).getCount() == 1,
                    id + " drops " + drops);
            for (int attempt = 0; attempt < 20; attempt++) {
                helper.assertTrue(state.getExpDrop(level, level.getRandom(), position, 0, 0) == 0,
                        id + " gives experience when broken");
            }
        }
        helper.succeed();
    }

    /** Each press recipe input matches exactly one press recipe, so the press never meets an ambiguous block. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyPressInputMatchesOneRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<SmallPlatePressRecipe> recipes = level.getRecipeManager().getAllRecipesFor(
                MaterialContent.SMALL_PLATE_PRESS_TYPE.get());
        helper.assertTrue(recipes.size() == 17, "Expected 17 press recipes, found " + recipes.size());
        for (SmallPlatePressRecipe recipe : recipes) {
            helper.assertFalse(recipe.ingredient().test(new ItemStack(MaterialContent.item("rutile_ore"))),
                    recipe.getId() + " presses rutile");
        }
        for (SmallPlatePressRecipe recipe : recipes) {
            ItemStack[] inputs = recipe.ingredient().getItems();
            helper.assertTrue(inputs.length > 0, recipe.getId() + " has no input after tags load");
            for (ItemStack input : inputs) {
                SimpleContainer container = new SimpleContainer(input.copy());
                long matches = recipes.stream().filter(other -> other.matches(container, level)).count();
                helper.assertTrue(matches == 1, input + " matches " + matches + " press recipes");
            }
        }
        helper.assertTrue(level.getRecipeManager().byKey(ModIdentity.id("pressing_iron_plate")).isPresent(),
                "Iron plates must come from the press (ADR-063 revision 4)");
        helper.succeed();
    }

    private static void column(GameTestHelper helper, int dx, Block anvil, Block input) {
        helper.setBlock(ANVIL.offset(dx, 0, 0), anvil);
        helper.setBlock(TARGET.offset(dx, 0, 0), input);
        helper.setBlock(PRESS.offset(dx, 0, 0), MaterialContent.SMALL_PLATE_PRESS.get());
    }

    private static void power(GameTestHelper helper, int dx) {
        helper.setBlock(POWER.offset(dx, 0, 0), Blocks.REDSTONE_BLOCK);
    }

    private static BlockPos at(GameTestHelper helper, int dx, BlockPos relative) {
        return helper.absolutePos(relative.offset(dx, 0, 0));
    }

    private static List<ItemEntity> items(GameTestHelper helper, int dx) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(at(helper, dx, TARGET)).inflate(0.75D));
    }

    private static void assertPressed(GameTestHelper helper, int dx, Item item, int count) {
        helper.assertBlockPresent(Blocks.AIR, TARGET.offset(dx, 0, 0));
        int found = 0;
        for (ItemEntity entity : items(helper, dx)) {
            helper.assertTrue(entity.getItem().is(item), "Unexpected output " + entity.getItem());
            found += entity.getItem().getCount();
        }
        helper.assertTrue(found == count, "Expected " + count + " " + item + ", found " + found);
    }
}
