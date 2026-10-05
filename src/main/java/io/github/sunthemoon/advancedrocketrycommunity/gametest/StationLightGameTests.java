package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Registered native lamp behavior; not survival-player, restart or visual evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationLightGameTests {
    private static final ResourceLocation ID = ModIdentity.id("station_light");

    private StationLightGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredLampIsAnOrdinaryOpaqueConstantLightWithPickaxeTools(GameTestHelper helper) {
        Block block = lamp(helper);
        Item item = lampItem(helper);
        BlockState state = block.defaultBlockState();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(helper.getLevel().hasChunkAt(pos), "Property fixture is not loaded");
        helper.assertTrue(block.getClass() == Block.class && item.getClass() == BlockItem.class
                && ((BlockItem) item).getBlock() == block && block.asItem() == item,
                "Lamp is not a matching ordinary Block and BlockItem");
        ItemStack stack = new ItemStack(item);
        helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.hasTag() && !stack.isDamageableItem(),
                "Lamp item defaults or payload changed");
        helper.assertTrue(state.getProperties().isEmpty() && !state.hasBlockEntity()
                && !state.isRandomlyTicking() && !state.isSignalSource(), "Lamp acquired custom state or ticking");
        helper.assertTrue(state.getLightEmission() == 15 && state.canOcclude()
                && state.isSolidRender(helper.getLevel(), pos)
                && state.isCollisionShapeFullBlock(helper.getLevel(), pos), "Lamp emission or solid cube changed");
        helper.assertTrue(state.getMapColor(helper.getLevel(), pos) == MapColor.COLOR_LIGHT_GRAY
                && state.getSoundType() == SoundType.METAL
                && state.getDestroySpeed(helper.getLevel(), pos) == 3.0F
                && block.getExplosionResistance() == 6.0F
                && state.getPistonPushReaction() == PushReaction.NORMAL, "Lamp material/default piston properties changed");
        helper.assertTrue(state.requiresCorrectToolForDrops() && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && !state.is(BlockTags.NEEDS_STONE_TOOL) && !state.is(BlockTags.NEEDS_IRON_TOOL)
                && !state.is(BlockTags.NEEDS_DIAMOND_TOOL), "Unexpected lamp tool tags");
        for (Item tool : List.of(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE,
                Items.GOLDEN_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE)) {
            helper.assertTrue(new ItemStack(tool).isCorrectToolForDrops(state), "Vanilla pickaxe was refused");
        }
        for (Item tool : List.of(Items.WOODEN_AXE, Items.IRON_SHOVEL, Items.SHEARS)) {
            helper.assertTrue(!new ItemStack(tool).isCorrectToolForDrops(state), "Unrelated tool was promoted");
        }
        helper.assertTrue(!ItemStack.EMPTY.isCorrectToolForDrops(state), "Empty hand became a correct tool");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualNineSlotRecipeProducesFourLampsAndPreservesQueries(GameTestHelper helper) {
        lamp(helper);
        var found = helper.getLevel().getRecipeManager().byKey(ID);
        helper.assertTrue(found.orElse(null) instanceof ShapedRecipe, "Registered lamp recipe is missing or not shaped");
        ShapedRecipe recipe = (ShapedRecipe) found.orElseThrow();
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3, "Lamp recipe is not full 3x3");
        checkRecipe(helper, recipe, grid(), true);
        TransientCraftingContainer mirror = grid();
        for (int row = 0; row < 3; row++) {
            ItemStack left = mirror.getItem(row * 3).copy();
            mirror.setItem(row * 3, mirror.getItem(row * 3 + 2).copy());
            mirror.setItem(row * 3 + 2, left);
        }
        checkRecipe(helper, recipe, mirror, true);
        for (int slot = 0; slot < 9; slot++) {
            TransientCraftingContainer missing = grid();
            missing.setItem(slot, ItemStack.EMPTY);
            checkRecipe(helper, recipe, missing, false);
            TransientCraftingContainer wrong = grid();
            wrong.setItem(slot, marked(Items.REDSTONE));
            checkRecipe(helper, recipe, wrong, false);
        }
        for (int slot : new int[] {1, 3, 5, 7}) {
            for (Item wrong : List.of(Items.GLASS_PANE, Items.TINTED_GLASS, Items.WHITE_STAINED_GLASS)) {
                TransientCraftingContainer changed = grid(); changed.setItem(slot, marked(wrong));
                checkRecipe(helper, recipe, changed, false);
            }
        }
        TransientCraftingContainer dust = grid(); dust.setItem(4, marked(Items.GLOWSTONE_DUST));
        checkRecipe(helper, recipe, dust, false);
        for (int slot : new int[] {0, 2, 6, 8}) {
            for (Item wrong : List.of(Items.IRON_NUGGET, Items.COPPER_INGOT, MaterialContent.item("iron_plate"))) {
                TransientCraftingContainer changed = grid(); changed.setItem(slot, marked(wrong));
                checkRecipe(helper, recipe, changed, false);
            }
        }
        var small = new TransientCraftingContainer(new NoMenu(), 2, 2);
        for (int slot = 0; slot < 4; slot++) { small.setItem(slot, marked(Items.IRON_INGOT)); }
        checkRecipe(helper, recipe, small, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualLootIsOneSelfItemWithSeededNativeExplosionDecay(GameTestHelper helper) {
        BlockState state = lamp(helper).defaultBlockState();
        Item item = lampItem(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.assertTrue(helper.getLevel().hasChunkAt(pos), "Loot fixture is not loaded");
        var table = helper.getLevel().getServer().getLootData().getLootTable(ModIdentity.id("blocks/station_light"));
        int survivors = 0;
        int decayed = 0;
        for (int sample = 1; sample <= 64; sample++) {
            long seed = 0x9E3779B97F4A7C15L * sample;
            int normal = drops(helper, item, table.getRandomItems(loot(helper, state, pos, null), seed));
            int unitRadius = drops(helper, item, table.getRandomItems(loot(helper, state, pos, 1.0F), seed));
            int radius = drops(helper, item, table.getRandomItems(loot(helper, state, pos, 4.0F), seed));
            helper.assertTrue(normal == 1 && unitRadius == 1, "Native ordinary/unit-radius self loot changed");
            helper.assertTrue(radius == drops(helper, item,
                    table.getRandomItems(loot(helper, state, pos, 4.0F), seed)), "Seeded decay was not reproducible");
            if (radius == 1) { survivors++; } else { decayed++; }
        }
        helper.assertTrue(survivors > 0 && decayed > 0, "Finite native seed controls did not exercise both decay outcomes");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void loadedLampPublishesBlockLightWithinTheFixedDeadline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos neighbour = centre.east();
        helper.assertTrue(level.hasChunkAt(centre) && level.hasChunkAt(neighbour), "Light fixture is not loaded");
        helper.assertTrue(level.getBlockEntity(centre) == null && level.getBlockEntity(neighbour) == null,
                "Light fixture contains unowned BlockEntity data");
        BlockState beforeCentre = level.getBlockState(centre), beforeNeighbour = level.getBlockState(neighbour);
        Runnable restore = () -> {
            level.setBlock(centre, beforeCentre, Block.UPDATE_ALL);
            level.setBlock(neighbour, beforeNeighbour, Block.UPDATE_ALL);
        };
        try {
            level.setBlock(neighbour, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(centre, lamp(helper).defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(centre).getLightEmission() == 15
                    && level.getBlockState(centre).isCollisionShapeFullBlock(level, centre)
                    && level.getBlockEntity(centre) == null, "Loaded placement lost lamp state/collision");
            awaitLight(helper, centre, neighbour, restore);
        } catch (RuntimeException | Error failure) { restore.run(); throw failure; }
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void lampPreservesBoundaryPriorityAndDoesNotLoadFarChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Block block = lamp(helper);
        BlockState state = block.defaultBlockState();
        helper.assertTrue(!state.is(ModBlockTags.ATMOSPHERE_SEALING)
                && !state.is(ModBlockTags.ATMOSPHERE_PERMEABLE), "Lamp unexpectedly acquired an explicit atmosphere tag");
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null, "Boundary fixture is not owned/loaded");
        BlockState original = level.getBlockState(pos);
        try {
            AtmosphereBoundaryCatalog overrides;
            try (var registry = new AtmosphereBoundaryRegistry(key -> ForgeRegistries.BLOCKS.containsKey(key)
                    ? ForgeRegistries.BLOCKS.getValue(key) : null)) {
                registry.forOwner(ModIdentity.MOD_ID).register(ModIdentity.id("station_light_test_open"),
                        Set.of(ID), ignored -> AtmosphereBoundary.PERMEABLE);
                overrides = registry.freeze();
            }
            var normal = new ServerLevelVolumeWorldView(level, false);
            var override = new ServerLevelVolumeWorldView(level, false, overrides);
            level.setBlock(pos, state, Block.UPDATE_ALL);
            helper.assertTrue(normal.observe(cell(pos)) == CellObservation.SEALED,
                    "Default full cube did not seal the boundary");
            helper.assertTrue(override.observe(cell(pos)) == CellObservation.TRAVERSABLE,
                    "Explicit local API override lost priority over collision");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(normal.observe(cell(pos)) == CellObservation.TRAVERSABLE, "AIR boundary changed");
            BlockPos far = new BlockPos(29_000_000, 100, 29_000_000);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(!level.hasChunkAt(far) && normal.observe(cell(far)) == CellObservation.UNLOADED
                    && override.observe(cell(far)) == CellObservation.UNLOADED && !level.hasChunkAt(far)
                    && loaded == level.getChunkSource().getLoadedChunksCount(), "Boundary observation loaded a far chunk");
            helper.succeed();
        } finally { level.setBlock(pos, original, Block.UPDATE_ALL); }
    }

    private static Block lamp(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(ID), "Literal station_light block ID is absent");
        Block block = ForgeRegistries.BLOCKS.getValue(ID);
        helper.assertTrue(block != null && block != Blocks.AIR, "Literal station_light resolved to missing/AIR fallback");
        return block;
    }

    private static Item lampItem(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(ID), "Literal station_light item ID is absent");
        Item item = ForgeRegistries.ITEMS.getValue(ID);
        helper.assertTrue(item != null && item != Items.AIR, "Literal station_light resolved to missing/AIR item");
        return item;
    }

    private static TransientCraftingContainer grid() {
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int slot = 0; slot < 9; slot++) {
            Item item = slot == 4 ? Items.GLOWSTONE : (slot % 2 == 0 ? Items.IRON_INGOT : Items.GLASS);
            grid.setItem(slot, marked(item));
        }
        return grid;
    }

    private static ItemStack marked(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("station_light_input_probe", "retained");
        return stack;
    }

    private static void checkRecipe(GameTestHelper helper, ShapedRecipe recipe,
            TransientCraftingContainer grid, boolean accepted) {
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) { before.add(grid.getItem(slot).copy()); }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()) == accepted, "Unexpected lamp recipe match");
        if (accepted) {
            var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
            helper.assertTrue(selected.isPresent() && selected.get().getId().equals(ID), "Crafting lookup selected another recipe");
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(lampItem(helper)) && result.getCount() == 4 && !result.hasTag(),
                    "Native lamp assembly identity/count/payload changed");
            var remainders = recipe.getRemainingItems(grid);
            helper.assertTrue(remainders.size() == 9 && remainders.stream().allMatch(ItemStack::isEmpty),
                    "Lamp recipe acquired a native crafting remainder");
        }
        for (int slot = 0; slot < before.size(); slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)), "Recipe query mutated an input");
        }
    }

    private static LootParams loot(GameTestHelper helper, BlockState state, BlockPos pos, Float radius) {
        var builder = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.WOODEN_PICKAXE))
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos));
        if (radius != null) { builder.withParameter(LootContextParams.EXPLOSION_RADIUS, radius); }
        return builder.create(LootContextParamSets.BLOCK);
    }

    private static int drops(GameTestHelper helper, Item item, List<ItemStack> stacks) {
        helper.assertTrue(stacks.size() <= 1, "Lamp loot produced multiple entries");
        int count = 0;
        for (ItemStack stack : stacks) {
            // Native explosion decay can return a zero-count stack, whose exposed item is AIR.
            if (stack.isEmpty()) {
                helper.assertTrue(stack.getCount() == 0 && stack.getTag() == null, "Malformed empty lamp loot");
                continue;
            }
            helper.assertTrue(stack.is(item) && !stack.hasTag(), "Lamp loot identity/payload changed");
            count += stack.getCount();
        }
        helper.assertTrue(count >= 0 && count <= 1, "Lamp loot count exceeded one");
        return count;
    }

    private static void awaitLight(GameTestHelper helper, BlockPos centre, BlockPos neighbour, Runnable restore) {
        helper.runAfterDelay(1, () -> {
            boolean pending = false;
            try {
                ServerLevel level = helper.getLevel();
                helper.assertTrue(level.hasChunkAt(centre) && level.hasChunkAt(neighbour), "Light fixture became unloaded");
                boolean published = level.getBrightness(LightLayer.BLOCK, centre) == 15
                        && level.getBrightness(LightLayer.BLOCK, neighbour) >= 14;
                if (!published && helper.getTick() < 39) {
                    awaitLight(helper, centre, neighbour, restore);
                    pending = true;
                    return;
                }
                helper.assertTrue(published, "Lamp block light was not published within the original 40-tick deadline");
                helper.succeed();
            } finally { if (!pending) { restore.run(); } }
        });
    }

    private static VolumePosition cell(BlockPos pos) { return new VolumePosition(pos.getX(), pos.getY(), pos.getZ()); }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
