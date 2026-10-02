package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.OverworldOres;
import io.github.sunthemoon.advancedrocketrycommunity.material.worldgen.OverworldOres.Vein;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-063 sections 2 and 4 (A1) on a running server: the material recipes and the Overworld ore features. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialRecipeGameTests {
    private static final String ORE_SWITCH_BATCH = "materials_ore_switch";
    /** Ore veins of at most 16 blocks stay within this Chebyshev distance of their origin. */
    private static final int VEIN_REACH = 5;

    private MaterialRecipeGameTests() {
    }

    @AfterBatch(batch = ORE_SWITCH_BATCH)
    public static void restoreSwitch(ServerLevel level) {
        CommonConfig.OVERWORLD_ORES_ENABLED.set(true);
    }

    /** Every rolling input, at a full stack, matches one recipe: the machine never refuses a material as ambiguous. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyRollingInputMatchesOneRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<RollingMachineRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipes.ROLLING_TYPE.get());
        for (RollingMachineRecipe recipe : recipes) {
            for (ItemStack alternative : recipe.ingredient().getItems()) {
                SimpleContainer full = new SimpleContainer(alternative.copyWithCount(alternative.getMaxStackSize()));
                long matches = recipes.stream().filter(other -> other.matches(full, level)).count();
                helper.assertTrue(matches == 1, alternative + " matches " + matches + " rolling recipes");
            }
        }
        RollingMachineRecipe plate = rolling(helper, "rolling_titanium_plate");
        helper.assertTrue(plate.inputCount() == 1 && plate.fluidAmount() == 100
                        && plate.processDefinition().durationTicks() == 300
                        && plate.processDefinition().energyPerTick() == 20
                        && plate.result().is(MaterialContent.item("titanium_plate")),
                "Ingot to plate: one ingot, 100 mB, 300 ticks at 20 FE/t");
        RollingMachineRecipe sheet = rolling(helper, "rolling_titanium_sheet");
        helper.assertTrue(sheet.processDefinition().energyPerTick() == 200
                        && sheet.ingredient().test(new ItemStack(MaterialContent.item("titanium_plate")))
                        && sheet.result().is(MaterialContent.item("titanium_sheet")),
                "Plate to sheet: 300 ticks at 200 FE/t");
        helper.assertTrue(level.getRecipeManager().byKey(ModIdentity.id("rolling_iron_plate")).isEmpty(),
                "Iron ingots already roll into bars (ADR-063 revision 4)");
        helper.assertTrue(level.getRecipeManager().byKey(ModIdentity.id("rolling_iron_bars")).isPresent(),
                "The v1.2 rolling recipe stays");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oresRawItemsAndDustSmeltIntoIngots(GameTestHelper helper) {
        cooks(helper, "tin_ore", MaterialContent.item("tin_ingot"));
        cooks(helper, "deepslate_tin_ore", MaterialContent.item("tin_ingot"));
        cooks(helper, "raw_tin", MaterialContent.item("tin_ingot"));
        cooks(helper, "tin_dust", MaterialContent.item("tin_ingot"));
        cooks(helper, "raw_aluminum", MaterialContent.item("aluminum_ingot"));
        cooks(helper, "iridium_ore", MaterialContent.item("iridium_ingot"));
        cooks(helper, "raw_iridium", MaterialContent.item("iridium_ingot"));
        cooks(helper, "titanium_dust", MaterialContent.item("titanium_ingot"));
        cooks(helper, "steel_dust", MaterialContent.item("steel_ingot"));
        cooks(helper, "dilithium_ore", MaterialContent.item("dilithium_dust"));
        cooks(helper, "deepslate_dilithium_ore", MaterialContent.item("dilithium_dust"));
        cooks(helper, "iron_dust", Items.IRON_INGOT);
        cooks(helper, "copper_dust", Items.COPPER_INGOT);
        cooks(helper, "gold_dust", Items.GOLD_INGOT);
        // Rutile waits for the electric arc furnace (C16b), as in the legacy game.
        for (String rutile : List.of("rutile_ore", "deepslate_rutile_ore", "raw_rutile")) {
            ItemStack input = new ItemStack(MaterialContent.item(rutile));
            helper.assertTrue(cook(helper, RecipeType.SMELTING, input).isEmpty()
                    && cook(helper, RecipeType.BLASTING, input).isEmpty(), rutile + " must not smelt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyCraftingShapesMakeTheProducts(GameTestHelper helper) {
        Item titanium = MaterialContent.item("titanium_ingot");
        crafts(helper, new ItemStack(MaterialContent.item("titanium_rod"), 4),
                titanium, null, null, null, titanium, null, null, null, titanium);
        Item copper = Items.COPPER_INGOT;
        crafts(helper, new ItemStack(MaterialContent.item("copper_coil")),
                copper, copper, copper, copper, null, copper, copper, copper, copper);
        Item rod = MaterialContent.item("steel_rod");
        Item plate = MaterialContent.item("steel_plate");
        crafts(helper, new ItemStack(MaterialContent.item("steel_gear")),
                rod, plate, rod, null, MaterialContent.item("steel_ingot"), null, rod, plate, rod);
        crafts(helper, new ItemStack(MaterialContent.item("steel_fan")),
                plate, null, plate, null, rod, null, plate, null, plate);
        Item tin = MaterialContent.item("tin_ingot");
        Item nugget = MaterialContent.item("tin_nugget");
        crafts(helper, new ItemStack(nugget, 9), tin, null, null, null, null, null, null, null, null);
        crafts(helper, new ItemStack(tin), nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget, nugget);
        crafts(helper, new ItemStack(MaterialContent.item("tin_block")), tin, tin, tin, tin, tin, tin, tin, tin, tin);
        crafts(helper, new ItemStack(tin, 9), MaterialContent.item("tin_block"),
                null, null, null, null, null, null, null, null);
        crafts(helper, new ItemStack(MaterialContent.item("copper_nugget"), 9),
                Items.COPPER_INGOT, null, null, null, null, null, null, null, null);
        helper.succeed();
    }

    /** Each Overworld vein replaces stone with the stone ore and deepslate with the deepslate ore, near its origin. */
    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void overworldVeinsReplaceStoneAndDeepslateNearTheirOrigin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(7, 7, 7));
        for (Vein vein : OverworldOres.VEINS) {
            fillCube(level, origin);
            Holder<ConfiguredFeature<?, ?>> feature = level.registryAccess()
                    .registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(vein.configured());
            Block stoneOre = MaterialContent.block(vein.material().stoneOreId().orElseThrow());
            Block deepslateOre = MaterialContent.block(vein.material().deepslateOreId().orElseThrow());
            int placed = 0;
            for (long seed = 1; seed <= 8 && placed == 0; seed++) {
                feature.value().place(level, level.getChunkSource().getGenerator(), RandomSource.create(seed), origin);
                placed = countOres(helper, level, origin, stoneOre, deepslateOre);
            }
            helper.assertTrue(placed > 0 && placed <= vein.size() * 2, vein.featureName() + " placed " + placed);
        }
        fillCube(level, origin);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = ORE_SWITCH_BATCH, timeoutTicks = 20)
    public static void overworldPlacementsFollowTheServerSwitch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = new ChunkPos(helper.absolutePos(BlockPos.ZERO)).getWorldPosition();
        for (Vein vein : OverworldOres.VEINS) {
            PlacedFeature placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                    .getOrThrow(vein.placed());
            List<BlockPos> positions = positions(level, placed, origin);
            helper.assertTrue(positions.size() == vein.count(),
                    vein.featureName() + " gave " + positions.size() + " positions, not " + vein.count());
            for (BlockPos position : positions) {
                helper.assertTrue(new ChunkPos(position).equals(new ChunkPos(origin))
                                && position.getY() >= OverworldOres.MIN_Y && position.getY() <= OverworldOres.MAX_Y,
                        vein.featureName() + " left its chunk or height range at " + position);
            }
        }
        CommonConfig.OVERWORLD_ORES_ENABLED.set(false);
        for (Vein vein : OverworldOres.VEINS) {
            PlacedFeature placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                    .getOrThrow(vein.placed());
            helper.assertTrue(positions(level, placed, origin).isEmpty(), vein.featureName() + " ignored the switch");
        }
        CommonConfig.OVERWORLD_ORES_ENABLED.set(true);
        helper.succeed();
    }

    /** The biome modifier adds the four ores to Overworld biomes only; no Overworld feature places iridium. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void overworldBiomesCarryTheOresWithoutIridium(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        var placedRegistry = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (var key : List.of(Biomes.PLAINS, Biomes.DESERT, Biomes.DEEP_DARK)) {
            Biome biome = biomes.getOrThrow(key);
            HolderSet<PlacedFeature> ores = biome.getGenerationSettings().features().get(step);
            for (Vein vein : OverworldOres.VEINS) {
                helper.assertTrue(ores.contains(placedRegistry.getHolderOrThrow(vein.placed())),
                        key.location() + " lacks " + vein.featureName());
            }
            for (HolderSet<PlacedFeature> features : biome.getGenerationSettings().features()) {
                for (Holder<PlacedFeature> feature : features) {
                    helper.assertFalse(placesIridium(feature.value()), key.location() + " places iridium");
                }
            }
        }
        Biome nether = biomes.getOrThrow(Biomes.NETHER_WASTES);
        for (Vein vein : OverworldOres.VEINS) {
            helper.assertFalse(nether.getGenerationSettings().hasFeature(placedRegistry.getOrThrow(vein.placed())),
                    "The Nether received " + vein.featureName());
        }
        helper.succeed();
    }

    private static RollingMachineRecipe rolling(GameTestHelper helper, String name) {
        return helper.getLevel().getRecipeManager().byKey(ModIdentity.id(name))
                .filter(RollingMachineRecipe.class::isInstance).map(RollingMachineRecipe.class::cast)
                .orElseThrow(() -> new IllegalStateException("Missing rolling recipe " + name));
    }

    private static void cooks(GameTestHelper helper, String input, Item output) {
        ItemStack stack = new ItemStack(MaterialContent.item(input));
        for (RecipeType<? extends AbstractCookingRecipe> type
                : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
            Optional<ItemStack> result = cook(helper, type, stack);
            helper.assertTrue(result.isPresent() && result.get().is(output),
                    input + " does not " + type + " into " + output + ": " + result);
        }
    }

    private static <T extends AbstractCookingRecipe> Optional<ItemStack> cook(
            GameTestHelper helper, RecipeType<T> type, ItemStack input) {
        ServerLevel level = helper.getLevel();
        return level.getRecipeManager().getRecipeFor(type, new SimpleContainer(input.copy()), level)
                .map(recipe -> recipe.getResultItem(level.registryAccess()));
    }

    private static void crafts(GameTestHelper helper, ItemStack expected, Item... grid) {
        ServerLevel level = helper.getLevel();
        TransientCraftingContainer container = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int slot = 0; slot < grid.length; slot++) {
            if (grid[slot] != null) {
                container.setItem(slot, new ItemStack(grid[slot]));
            }
        }
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level)
                .map(recipe -> recipe.assemble(container, level.registryAccess())).orElse(ItemStack.EMPTY);
        helper.assertTrue(ItemStack.matches(result, expected), "Expected " + expected + ", crafted " + result);
    }

    private static void fillCube(ServerLevel level, BlockPos origin) {
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            BlockState state = position.getY() < origin.getY() ? Blocks.DEEPSLATE.defaultBlockState()
                    : Blocks.STONE.defaultBlockState();
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }
    }

    private static int countOres(GameTestHelper helper, ServerLevel level, BlockPos origin, Block stoneOre,
                                 Block deepslateOre) {
        int count = 0;
        for (BlockPos position : BlockPos.betweenClosed(origin.offset(-6, -6, -6), origin.offset(6, 6, 6))) {
            BlockState state = level.getBlockState(position);
            boolean below = position.getY() < origin.getY();
            if (state.is(stoneOre) || state.is(deepslateOre)) {
                helper.assertTrue(state.is(below ? deepslateOre : stoneOre),
                        "The wrong ore variant replaced " + (below ? "deepslate" : "stone") + " at " + position);
                helper.assertTrue(Math.abs(position.getX() - origin.getX()) <= VEIN_REACH
                                && Math.abs(position.getY() - origin.getY()) <= VEIN_REACH
                                && Math.abs(position.getZ() - origin.getZ()) <= VEIN_REACH,
                        "A vein reached " + position + " from " + origin);
                count++;
            } else {
                helper.assertTrue(state.is(below ? Blocks.DEEPSLATE : Blocks.STONE),
                        "A vein wrote " + state + " at " + position);
            }
        }
        return count;
    }

    /** The positions a placed feature would use at an origin, through its modifiers in order (as vanilla does). */
    private static List<BlockPos> positions(ServerLevel level, PlacedFeature placed, BlockPos origin) {
        PlacementContext context = new PlacementContext(level, level.getChunkSource().getGenerator(),
                Optional.of(placed));
        RandomSource random = RandomSource.create(42L);
        Stream<BlockPos> stream = Stream.of(origin);
        for (PlacementModifier modifier : placed.placement()) {
            stream = stream.flatMap(position -> modifier.getPositions(context, random, position));
        }
        return stream.toList();
    }

    private static boolean placesIridium(PlacedFeature feature) {
        if (!(feature.feature().value().config() instanceof OreConfiguration ores)) {
            return false;
        }
        Block iridium = MaterialContent.block("iridium_ore");
        return ores.targetStates.stream().anyMatch(target -> target.state.is(iridium));
    }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return false;
        }
    }
}
