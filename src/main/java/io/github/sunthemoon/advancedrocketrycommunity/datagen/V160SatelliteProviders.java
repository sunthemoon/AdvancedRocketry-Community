package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentRole;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteComponentReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteDefinitionReloadListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-049 v1.6 DataGen: the Satellite Builder, the generic package and the component items. Models
 * reference existing project textures and vanilla models (no texture bytes are copied); the component
 * and kind data reuse the legacy numbers recorded in ADR-049 section 2, and every generated file is
 * decoded by the strict runtime decoder before it is written.
 */
public final class V160SatelliteProviders {
    private V160SatelliteProviders() {
    }

    public static final class Models extends BlockStateProvider {
        public Models(PackOutput output, ExistingFileHelper existingFiles) {
            super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void registerStatesAndModels() {
            ModelFile builder = models().orientable(
                    "satellite_builder",
                    modLoc("block/machine_casing_side"),
                    modLoc("block/machine_casing_front"),
                    mcLoc("block/smithing_table_top")
            );
            horizontalBlock(ModBlocks.SATELLITE_BUILDER.get(), builder);
            simpleBlockItem(ModBlocks.SATELLITE_BUILDER.get(), builder);
            item("satellite_package", "item/conduit");
            item("advanced_solar_panel", "item/light_blue_stained_glass_pane");
            item("satellite_battery", "item/redstone_block");
            item("large_satellite_battery", "item/respawn_anchor");
            item("satellite_cargo_hold", "item/barrel");
            item("survey_scanner_module", "item/spyglass");
            item("solar_transmitter_module", "item/lightning_rod");
            item("asteroid_drill_module", "item/iron_pickaxe");
            item("gas_intake_module", "item/hopper");
        }

        private void item(String name, String vanillaParent) {
            itemModels().withExistingParent(name, mcLoc(vanillaParent));
        }
    }

    public static LootTableProvider loot(PackOutput output) {
        return new LootTableProvider(output, Set.of(), List.of(
                new LootTableProvider.SubProviderEntry(BuilderLoot::new, LootContextParamSets.BLOCK)));
    }

    /** A plain self-drop; the block adds a quarantined root to the drop itself, as the terminal does. */
    private static final class BuilderLoot extends BlockLootSubProvider {
        private BuilderLoot() {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags());
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.SATELLITE_BUILDER.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.SATELLITE_BUILDER.get());
        }
    }

    /** Community-authored recipes that follow the existing satellite and machine progression. */
    public static final class Recipes extends RecipeProvider {
        public Recipes(PackOutput output) {
            super(output);
        }

        @Override
        protected void buildRecipes(Consumer<FinishedRecipe> output) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModBlocks.SATELLITE_BUILDER.get())
                    .pattern("ACA").pattern("MTM").pattern("IRI")
                    .define('A', ModItems.ADVANCED_CIRCUIT.get())
                    .define('C', ModItems.SATELLITE_CHASSIS.get())
                    .define('M', ModItems.MACHINE_CASING.get())
                    .define('T', Items.SMITHING_TABLE)
                    .define('I', Tags.Items.INGOTS_IRON)
                    .define('R', Items.REDSTONE_BLOCK)
                    .unlockedBy("has_satellite_chassis", has(ModItems.SATELLITE_CHASSIS.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.ADVANCED_SOLAR_PANEL.get())
                    .pattern("SSS").pattern("GAG").pattern("GRG")
                    .define('S', ModItems.SATELLITE_SOLAR_MODULE.get())
                    .define('G', Tags.Items.INGOTS_GOLD)
                    .define('A', ModItems.ADVANCED_CIRCUIT.get())
                    .define('R', Items.REDSTONE_BLOCK)
                    .unlockedBy("has_satellite_solar_module", has(ModItems.SATELLITE_SOLAR_MODULE.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_BATTERY.get())
                    .pattern("IRI").pattern("RBR").pattern("IRI")
                    .define('I', Tags.Items.INGOTS_IRON)
                    .define('R', Items.REDSTONE)
                    .define('B', ModItems.BASIC_CIRCUIT.get())
                    .unlockedBy("has_basic_circuit", has(ModItems.BASIC_CIRCUIT.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.LARGE_SATELLITE_BATTERY.get())
                    .pattern("BRB").pattern("RAR").pattern("BRB")
                    .define('B', ModItems.SATELLITE_BATTERY.get())
                    .define('R', Items.REDSTONE_BLOCK)
                    .define('A', ModItems.ADVANCED_CIRCUIT.get())
                    .unlockedBy("has_satellite_battery", has(ModItems.SATELLITE_BATTERY.get()))
                    .save(output);
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SATELLITE_CARGO_HOLD.get())
                    .pattern("ICI").pattern("CMC").pattern("ICI")
                    .define('I', Tags.Items.INGOTS_IRON)
                    .define('C', Tags.Items.CHESTS_WOODEN)
                    .define('M', ModItems.MACHINE_CASING.get())
                    .unlockedBy("has_machine_casing", has(ModItems.MACHINE_CASING.get()))
                    .save(output);
            primary(output, ModItems.SURVEY_SCANNER_MODULE.get(), Items.SPYGLASS, Items.OBSERVER);
            primary(output, ModItems.SOLAR_TRANSMITTER_MODULE.get(), Items.LIGHTNING_ROD, Items.DAYLIGHT_DETECTOR);
            primary(output, ModItems.ASTEROID_DRILL_MODULE.get(), Items.DIAMOND_PICKAXE, Items.PISTON);
            primary(output, ModItems.GAS_INTAKE_MODULE.get(), Items.HOPPER, ModItems.EMPTY_CANISTER.get());
        }

        /** Every primary function module: its tool on top of two advanced circuits and a gold frame. */
        private static void primary(Consumer<FinishedRecipe> output, Item result, Item tool, Item core) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                    .pattern("GTG").pattern("ACA").pattern("GRG")
                    .define('G', Tags.Items.INGOTS_GOLD)
                    .define('T', tool)
                    .define('A', ModItems.ADVANCED_CIRCUIT.get())
                    .define('C', core)
                    .define('R', Items.REDSTONE)
                    .unlockedBy("has_advanced_circuit", has(ModItems.ADVANCED_CIRCUIT.get()))
                    .save(output);
        }
    }

    /**
     * Complete v1.6 copies of the two vanilla tool tags; they supersede the v1.5 copies (build.gradle
     * excludes those), so every earlier machine block keeps its entry.
     */
    public static final class ToolTags extends BlockTagsProvider {
        public ToolTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                        ExistingFileHelper existingFiles) {
            super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            Block[] blocks = {
                    ModBlocks.MACHINE_CASING.get(),
                    ModBlocks.SATELLITE_TERMINAL.get(),
                    ModBlocks.ROLLING_MACHINE.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                    ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                    ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get(),
                    ModBlocks.WARP_CORE.get(),
                    ModBlocks.SATELLITE_BUILDER.get()
            };
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(blocks);
            tag(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL).add(blocks);
        }
    }

    /** ADR-049 section 2 built-in components: the legacy numbers, one file per item. */
    public static final class Components implements DataProvider {
        private final PackOutput.PathProvider paths;

        public Components(PackOutput output) {
            paths = output.createPathProvider(PackOutput.Target.DATA_PACK, SatelliteComponentReloadListener.DIRECTORY);
        }

        static Map<String, JsonObject> files() {
            Map<String, JsonObject> files = new LinkedHashMap<>();
            files.put("satellite_chassis", component(ModItems.SATELLITE_CHASSIS.get(), SatelliteComponentRole.CHASSIS, null, 0));
            files.put("satellite_solar_module",
                    component(ModItems.SATELLITE_SOLAR_MODULE.get(), SatelliteComponentRole.POWER, null, 4));
            files.put("advanced_solar_panel",
                    component(ModItems.ADVANCED_SOLAR_PANEL.get(), SatelliteComponentRole.POWER, null, 40));
            files.put("satellite_battery",
                    component(ModItems.SATELLITE_BATTERY.get(), SatelliteComponentRole.BATTERY, null, 10_000));
            files.put("large_satellite_battery",
                    component(ModItems.LARGE_SATELLITE_BATTERY.get(), SatelliteComponentRole.BATTERY, null, 40_000));
            files.put("data_storage_unit",
                    component(ModItems.DATA_STORAGE_UNIT.get(), SatelliteComponentRole.DATA_STORAGE, null, 1_000));
            files.put("satellite_cargo_hold",
                    component(ModItems.SATELLITE_CARGO_HOLD.get(), SatelliteComponentRole.CARGO, null, 9));
            files.put("survey_scanner_module",
                    component(ModItems.SURVEY_SCANNER_MODULE.get(), SatelliteComponentRole.PRIMARY, SatelliteKind.SURVEY, 10));
            files.put("solar_transmitter_module",
                    component(ModItems.SOLAR_TRANSMITTER_MODULE.get(), SatelliteComponentRole.PRIMARY, SatelliteKind.SOLAR, 10));
            files.put("asteroid_drill_module", component(ModItems.ASTEROID_DRILL_MODULE.get(),
                    SatelliteComponentRole.PRIMARY, SatelliteKind.ASTEROID_MINER, 10));
            files.put("gas_intake_module", component(ModItems.GAS_INTAKE_MODULE.get(),
                    SatelliteComponentRole.PRIMARY, SatelliteKind.GAS_HARVESTER, 10));
            return files;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput output) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            files().forEach((name, json) -> {
                SatelliteComponentDefinition.decode(json);
                writes.add(DataProvider.saveStable(output, json, paths.json(ModIdentity.id(name))));
            });
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "ARCE v1.6 satellite components";
        }

        private static JsonObject component(Item item, SatelliteComponentRole role, SatelliteKind kind, int value) {
            JsonObject json = new JsonObject();
            json.addProperty("schema_version", 1);
            json.addProperty("item", ForgeRegistries.ITEMS.getKey(item).toString());
            json.addProperty("role", role.id());
            if (kind != null) {
                json.addProperty("kind", kind.id());
            }
            switch (role) {
                case POWER -> json.addProperty("power_generation", value);
                case BATTERY -> json.addProperty("battery_capacity", value);
                case DATA_STORAGE -> json.addProperty("data_capacity", value);
                case CARGO -> json.addProperty("cargo_stacks", value);
                case PRIMARY -> json.addProperty("primary_rating", value);
                case CHASSIS -> {
                }
            }
            return json;
        }
    }

    /**
     * ADR-049 section 3 schema-2 definitions for the four non-data kinds. The research thresholds and scan
     * values are v1.6 balance decisions recorded in the C7b verification record.
     */
    public static final class KindDefinitions implements DataProvider {
        private final PackOutput.PathProvider paths;

        public KindDefinitions(PackOutput output) {
            paths = output.createPathProvider(PackOutput.Target.DATA_PACK, SatelliteDefinitionReloadListener.DIRECTORY);
        }

        static Map<String, JsonObject> files() {
            ResourceLocation mars = ModIdentity.id("mars");
            ResourceLocation venus = ModIdentity.id("venus");
            ResourceLocation gasGiant = ModIdentity.id("gas_giant");
            ResourceLocation tauCetiE = ModIdentity.id("tau_ceti_e");
            Map<String, JsonObject> files = new LinkedHashMap<>();
            JsonObject survey = definition("survey_satellite", SatelliteKind.SURVEY, ModItems.SURVEY_SCANNER_MODULE.get(),
                    0, List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, mars, venus, gasGiant, tauCetiE));
            survey.addProperty("mission_duration_ticks", 6_000);
            survey.addProperty("instances_per_survey", 2);
            survey.addProperty("scan_energy", 1_000);
            survey.addProperty("scan_radius_blocks", 32);
            survey.addProperty("scan_cell_blocks", 8);
            files.put("survey_satellite", survey);
            JsonObject solar = definition("solar_satellite", SatelliteKind.SOLAR, ModItems.SOLAR_TRANSMITTER_MODULE.get(),
                    120, List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, mars, venus));
            solar.addProperty("output_multiplier_percent", 100);
            files.put("solar_satellite", solar);
            files.put("asteroid_miner", definition("asteroid_miner", SatelliteKind.ASTEROID_MINER,
                    ModItems.ASTEROID_DRILL_MODULE.get(), 240,
                    List.of(CelestialIds.EARTH_ID, CelestialIds.MOON_ID, mars, venus, tauCetiE)));
            files.put("gas_harvester", definition("gas_harvester", SatelliteKind.GAS_HARVESTER,
                    ModItems.GAS_INTAKE_MODULE.get(), 360, List.of(gasGiant)));
            return files;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput output) {
            List<CompletableFuture<?>> writes = new ArrayList<>();
            files().forEach((name, json) -> {
                SatelliteKindDefinition.decode(json);
                writes.add(DataProvider.saveStable(output, json, paths.json(ModIdentity.id(name))));
            });
            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        }

        @Override
        public String getName() {
            return "ARCE v1.6 satellite kind definitions";
        }

        private static JsonObject definition(String name, SatelliteKind kind, Item primary, int research,
                                             List<ResourceLocation> targets) {
            JsonObject json = new JsonObject();
            json.addProperty("schema_version", 2);
            json.addProperty("id", ModIdentity.id(name).toString());
            json.addProperty("kind", kind.id());
            json.addProperty("primary_component", ForgeRegistries.ITEMS.getKey(primary).toString());
            json.addProperty("required_lifetime_research", research);
            JsonArray launchTargets = new JsonArray();
            targets.forEach(target -> launchTargets.add(target.toString()));
            json.add("launch_targets", launchTargets);
            return json;
        }
    }
}
